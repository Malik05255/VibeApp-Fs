import {
  classifyRouteFailure,
  rankStrictlyFreeModelCandidates,
  type HRouteCapability,
  type HRouteState,
} from "./ai-router-policy.ts";

const OPENROUTER_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
const MAX_FREE_ATTEMPTS = 3;
const PROVIDER_GUARD_KEY = "openrouter_free_router_guard";
const MAX_PROVIDER_GUARD_MS = 60 * 60_000;

type RoutedRequest = {
  db: any;
  apiKey: string;
  models: any[];
  preferredModel: string | null;
  capability: HRouteCapability;
  messages: any[];
  temperature: number;
  stage: "candidate" | "verifier" | "media";
  preferDifferentFrom?: string | null;
  plugins?: any[];
  fetchImpl?: typeof fetch;
};

export type RoutedFreeCompletion = {
  content: string;
  model: string;
  attempts: number;
};

export async function completeWithFreeModelFailover(
  request: RoutedRequest,
): Promise<RoutedFreeCompletion | null> {
  if (await providerGuardActive(request.db)) return null;

  const initial = rankStrictlyFreeModelCandidates(
    request.models,
    request.preferredModel,
    request.capability,
    [],
    request.preferDifferentFrom ?? null,
  );
  if (!initial.length) return null;

  const routeStates = await loadRouteStates(request.db, request.capability, initial);
  const candidates = rankStrictlyFreeModelCandidates(
    request.models,
    request.preferredModel,
    request.capability,
    routeStates,
    request.preferDifferentFrom ?? null,
  );
  if (!candidates.length) return null;

  const fetcher = request.fetchImpl ?? fetch;
  let attempts = 0;
  for (const model of candidates.slice(0, MAX_FREE_ATTEMPTS)) {
    attempts += 1;
    const started = Date.now();
    let response: Response | null = null;
    let bodyText = "";
    try {
      response = await fetcher(OPENROUTER_CHAT_URL, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${request.apiKey}`,
          "Content-Type": "application/json",
          "HTTP-Referer": Deno.env.get("H_PUBLIC_BASE_URL") || Deno.env.get("SUPABASE_URL") || "https://supabase.com",
          "X-Title": request.stage === "media" ? "H WhatsApp Media Runtime" : "H WhatsApp Cloud Runtime",
        },
        body: JSON.stringify({
          model,
          temperature: request.temperature,
          messages: request.messages,
          ...(request.plugins?.length ? { plugins: request.plugins } : {}),
        }),
      });
      bodyText = await response.text();
      if (!response.ok) {
        const failure = classifyRouteFailure(
          response.status,
          response.headers.get("retry-after"),
          bodyText,
        );
        await recordRouteResult(request.db, model, request.capability, {
          ok: false,
          latencyMs: Date.now() - started,
          status: response.status,
          error: failure.reason,
          cooldownMs: failure.cooldownMs,
          remaining: parseIntegerHeader(response.headers.get("x-ratelimit-remaining")),
          resetAt: parseResetHeader(response.headers.get("x-ratelimit-reset")),
        });
        if (failure.providerFatal) {
          await setProviderGuard(request.db, failure.reason, failure.cooldownMs);
          return null;
        }
        if (!failure.retryNext) return null;
        continue;
      }

      const body = JSON.parse(bodyText);
      const content = String(body?.choices?.[0]?.message?.content || "").trim();
      if (!content) {
        const failure = classifyRouteFailure(response.status, null, "empty content");
        await recordRouteResult(request.db, model, request.capability, {
          ok: false,
          latencyMs: Date.now() - started,
          status: response.status,
          error: failure.reason,
          cooldownMs: failure.cooldownMs,
          remaining: parseIntegerHeader(response.headers.get("x-ratelimit-remaining")),
          resetAt: parseResetHeader(response.headers.get("x-ratelimit-reset")),
        });
        continue;
      }

      await recordRouteResult(request.db, model, request.capability, {
        ok: true,
        latencyMs: Date.now() - started,
        status: response.status,
        promptTokens: boundedTokenCount(body?.usage?.prompt_tokens),
        completionTokens: boundedTokenCount(body?.usage?.completion_tokens),
        remaining: parseIntegerHeader(response.headers.get("x-ratelimit-remaining")),
        resetAt: parseResetHeader(response.headers.get("x-ratelimit-reset")),
      });
      return { content, model, attempts };
    } catch (error) {
      const failure = classifyRouteFailure(
        response?.status ?? null,
        response?.headers.get("retry-after") ?? null,
        errorMessage(error),
      );
      await recordRouteResult(request.db, model, request.capability, {
        ok: false,
        latencyMs: Date.now() - started,
        status: response?.status ?? null,
        error: failure.reason,
        cooldownMs: failure.cooldownMs,
        remaining: response ? parseIntegerHeader(response.headers.get("x-ratelimit-remaining")) : null,
        resetAt: response ? parseResetHeader(response.headers.get("x-ratelimit-reset")) : null,
      });
      if (failure.providerFatal) {
        await setProviderGuard(request.db, failure.reason, failure.cooldownMs);
        return null;
      }
      if (!failure.retryNext) return null;
    }
  }
  return null;
}

async function providerGuardActive(db: any): Promise<boolean> {
  try {
    const { data, error } = await db.from("h_runtime_state")
      .select("value")
      .eq("key", PROVIDER_GUARD_KEY)
      .maybeSingle();
    if (error) return false;
    const blockedUntil = Date.parse(String(data?.value?.blocked_until || ""));
    return Number.isFinite(blockedUntil) && blockedUntil > Date.now();
  } catch (_) {
    return false;
  }
}

async function setProviderGuard(db: any, reason: string, cooldownMs: number) {
  try {
    const boundedCooldown = Math.max(60_000, Math.min(MAX_PROVIDER_GUARD_MS, Number(cooldownMs) || 5 * 60_000));
    const now = new Date();
    await db.from("h_runtime_state").upsert({
      key: PROVIDER_GUARD_KEY,
      value: {
        provider: "openrouter",
        free_only: true,
        reason: String(reason || "provider_unavailable").slice(0, 120),
        blocked_until: new Date(now.getTime() + boundedCooldown).toISOString(),
      },
      updated_at: now.toISOString(),
    }, { onConflict: "key" });
  } catch (_) {
    // Circuit-breaker persistence must not mask the original provider failure.
  }
}

async function loadRouteStates(db: any, capability: HRouteCapability, models: string[]): Promise<HRouteState[]> {
  try {
    const { data, error } = await db.from("h_runtime_ai_route_stats")
      .select("model,capability,attempts,successes,failures,consecutive_failures,avg_latency_ms,cooldown_until")
      .eq("provider", "openrouter")
      .eq("capability", capability)
      .in("model", models.slice(0, 50));
    if (error || !Array.isArray(data)) return [];
    return data as HRouteState[];
  } catch (_) {
    return [];
  }
}

type RouteRecord = {
  ok: boolean;
  latencyMs: number;
  status: number | null;
  error?: string | null;
  cooldownMs?: number;
  promptTokens?: number;
  completionTokens?: number;
  remaining?: number | null;
  resetAt?: string | null;
};

async function recordRouteResult(
  db: any,
  model: string,
  capability: HRouteCapability,
  result: RouteRecord,
) {
  try {
    const cooldownUntil = !result.ok && Number(result.cooldownMs || 0) > 0
      ? new Date(Date.now() + Number(result.cooldownMs)).toISOString()
      : null;
    await db.rpc("h_record_ai_route_result", {
      p_provider: "openrouter",
      p_model: model.slice(0, 200),
      p_capability: capability,
      p_ok: result.ok,
      p_latency_ms: Math.max(0, Math.min(2_147_483_647, Math.floor(result.latencyMs || 0))),
      p_http_status: result.status,
      p_error: result.error ? String(result.error).slice(0, 500) : null,
      p_cooldown_until: cooldownUntil,
      p_prompt_tokens: boundedTokenCount(result.promptTokens),
      p_completion_tokens: boundedTokenCount(result.completionTokens),
      p_rate_limit_remaining: result.remaining,
      p_rate_limit_reset_at: result.resetAt ?? null,
    });
  } catch (_) {
    // Telemetry must never make H fail when a free model result is otherwise usable.
  }
}

function boundedTokenCount(value: unknown): number {
  const number = Math.floor(Number(value) || 0);
  return Number.isFinite(number) ? Math.max(0, Math.min(2_147_483_647, number)) : 0;
}

function parseIntegerHeader(value: string | null): number | null {
  const number = Number(String(value || "").trim());
  if (!Number.isFinite(number)) return null;
  return Math.max(0, Math.floor(number));
}

function parseResetHeader(value: string | null): string | null {
  const raw = String(value || "").trim();
  if (!raw) return null;
  const numeric = Number(raw);
  if (Number.isFinite(numeric)) {
    const ms = numeric > 10_000_000_000 ? numeric : numeric * 1000;
    const date = new Date(ms);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }
  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}
