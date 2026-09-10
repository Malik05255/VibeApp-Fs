import {
  activeOwnerPaidHelper,
  type HProviderRoute,
} from "./provider-registry.ts";
import {
  modelSupportsOwnerPaidCapability,
  pricingWithinAuthorizedCeiling,
  type HOwnerPaidCapability,
  type HOwnerPaidTaskClass,
} from "./owner-paid-policy.ts";

const OPENROUTER_MODELS_URL = "https://openrouter.ai/api/v1/models";
const OPENROUTER_CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
const CREDENTIAL_VERSION = 1;
const MAX_PAID_TEXT_CHARS = 60_000;
const MAX_PAID_MEDIA_DATA_CHARS = 8_000_000;
const MAX_PAID_OUTPUT_TOKENS = 1_200;

type DbClient = any;
type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

type OwnerPaidRequest = {
  db: DbClient;
  messages: any[];
  temperature: number;
  stage: "candidate" | "verifier" | "media";
  taskClass: HOwnerPaidTaskClass;
  capability: HOwnerPaidCapability;
  plugins?: any[];
  fetchImpl?: FetchLike;
  decryptImpl?: (provider: string, ciphertext: string, iv: string) => Promise<string>;
};

export type HOwnerPaidCompletion =
  | { status: "not_configured" }
  | { status: "blocked"; routeId: string | null; allowFreeFallback: false; reason: string }
  | {
      status: "success";
      routeId: string;
      provider: string;
      model: string;
      content: string;
      callsUsed: number;
      dailyLimit: number;
      promptTokens: number;
      completionTokens: number;
      costUsd: number;
    };

type LoadedRoute = {
  route: HProviderRoute;
  metadata: Record<string, unknown>;
};

export async function completeWithOwnerPaidHelper(request: OwnerPaidRequest): Promise<HOwnerPaidCompletion> {
  const loaded = await loadActiveOwnerPaidRoute(request.db);
  if (loaded.kind === "none") return { status: "not_configured" };
  if (loaded.kind === "invalid") {
    return { status: "blocked", routeId: null, allowFreeFallback: false, reason: "owner_paid_registry_invalid" };
  }

  const { route, metadata } = loaded.value;
  // An enabled owner-paid/BYOK route is exclusive for every AI inference turn.
  // Legacy hard-task/fallback flags are intentionally ignored here so no caller can
  // re-enable task segmentation or silently escape to a free provider.
  if (route.provider !== "openrouter") return blocked(route, "unsupported_owner_paid_provider");

  const footprint = requestFootprint(request.messages);
  if (footprint.textChars > MAX_PAID_TEXT_CHARS) return blocked(route, "owner_paid_text_input_too_large");
  if (footprint.mediaDataChars > MAX_PAID_MEDIA_DATA_CHARS) return blocked(route, "owner_paid_media_input_too_large");

  const credential = await loadCredential(request.db, route);
  if (!credential.ok) return blocked(route, credential.reason);

  const fetchImpl = request.fetchImpl ?? fetch;
  const decryptImpl = request.decryptImpl ?? decryptOwnerPaidSecret;
  let apiKey = "";
  try {
    apiKey = (await decryptImpl(route.provider, credential.ciphertext, credential.iv)).trim();
  } catch (error) {
    return blocked(route, `credential_decrypt_failed:${errorMessage(error).slice(0, 120)}`);
  }
  if (!apiKey) return blocked(route, "owner_paid_credential_empty");

  const pricingCeiling = metadata.pricing_ceiling ?? credential.metadata.pricing_ceiling;
  let catalog: any[];
  try {
    catalog = await loadOpenRouterModels(fetchImpl, apiKey);
  } catch (error) {
    await recordState(request.db, route, {
      ready: false,
      reason: "catalog_unavailable",
      error: errorMessage(error).slice(0, 200),
    });
    return blocked(route, "owner_paid_catalog_unavailable");
  }

  const model = catalog.find((item) => String(item?.id || "") === route.selectedModel);
  if (!model) return blocked(route, "authorized_model_missing_from_live_catalog");
  if (!modelSupportsOwnerPaidCapability(model, request.capability)) {
    return blocked(route, `authorized_model_missing_${request.capability}_capability`);
  }
  const priceCheck = pricingWithinAuthorizedCeiling(model?.pricing, pricingCeiling);
  if (!priceCheck.ok) {
    await recordState(request.db, route, { ready: false, reason: priceCheck.reason, price_guard: true });
    return blocked(route, priceCheck.reason);
  }

  const claim = await claimPaidCall(request.db, route);
  if (!claim.allowed) {
    await recordState(request.db, route, {
      ready: false,
      reason: "daily_call_limit_reached",
      calls_used: claim.callsUsed,
      daily_limit: claim.dailyLimit,
    });
    return blocked(route, "daily_call_limit_reached");
  }

  let response: Response;
  let bodyText = "";
  try {
    response = await fetchImpl(OPENROUTER_CHAT_URL, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "HTTP-Referer": safeEnv("H_PUBLIC_BASE_URL") || safeEnv("SUPABASE_URL") || "https://supabase.com",
        "X-Title": `H Owner-Paid ${request.stage}`,
      },
      body: JSON.stringify({
        model: route.selectedModel,
        temperature: request.temperature,
        max_tokens: MAX_PAID_OUTPUT_TOKENS,
        usage: { include: true },
        messages: request.messages,
        ...(request.plugins?.length ? { plugins: request.plugins } : {}),
      }),
    });
    bodyText = await response.text();
  } catch (error) {
    await recordState(request.db, route, {
      ready: false,
      reason: "provider_transport_failure",
      error: errorMessage(error).slice(0, 200),
      calls_used: claim.callsUsed,
      daily_limit: claim.dailyLimit,
    });
    return blocked(route, "owner_paid_provider_transport_failure");
  }

  if (!response.ok) {
    const fatal = response.status === 401 || response.status === 402 || response.status === 403;
    if (fatal) await autoDisableRoute(request.db, loaded.value, `http_${response.status}`);
    await recordState(request.db, route, {
      ready: false,
      reason: `provider_http_${response.status}`,
      provider_fatal: fatal,
      calls_used: claim.callsUsed,
      daily_limit: claim.dailyLimit,
      error: bodyText.slice(0, 200),
    });
    // No paid retries and no free fallback. The already-reserved claim is deliberately not refunded.
    return blocked(route, `owner_paid_provider_http_${response.status}`);
  }

  let body: any;
  try {
    body = JSON.parse(bodyText);
  } catch (_) {
    return blocked(route, "owner_paid_provider_invalid_json");
  }
  const content = String(body?.choices?.[0]?.message?.content || "").trim();
  if (!content) return blocked(route, "owner_paid_provider_empty_content");

  const promptTokens = boundedTokenCount(body?.usage?.prompt_tokens);
  const completionTokens = boundedTokenCount(body?.usage?.completion_tokens);
  const costUsd = boundedCostUsd(body?.usage?.cost);
  await recordUsage(request.db, route.id, promptTokens, completionTokens, costUsd).catch(() => undefined);
  await recordState(request.db, route, {
    ready: true,
    last_success_at: new Date().toISOString(),
    selected_model: route.selectedModel,
    stage: request.stage,
    calls_used: claim.callsUsed,
    daily_limit: claim.dailyLimit,
    prompt_tokens: promptTokens,
    completion_tokens: completionTokens,
    cost_usd: costUsd,
    price_guard: true,
    paid_retries: 0,
    max_output_tokens: MAX_PAID_OUTPUT_TOKENS,
    max_text_chars: MAX_PAID_TEXT_CHARS,
    max_media_data_chars: MAX_PAID_MEDIA_DATA_CHARS,
  });

  return {
    status: "success",
    routeId: route.id,
    provider: route.provider,
    model: route.selectedModel!,
    content,
    callsUsed: claim.callsUsed,
    dailyLimit: claim.dailyLimit,
    promptTokens,
    completionTokens,
    costUsd,
  };
}

async function loadActiveOwnerPaidRoute(db: DbClient): Promise<
  | { kind: "none" }
  | { kind: "invalid" }
  | { kind: "ok"; value: LoadedRoute }
> {
  const { data, error } = await db.from("h_runtime_ai_provider_registry")
    .select("*")
    .eq("route_class", "owner_paid")
    .eq("enabled", true);
  if (error) return { kind: "invalid" };
  const rows = Array.isArray(data) ? data : [];
  if (!rows.length) return { kind: "none" };
  const route = activeOwnerPaidHelper(rows);
  if (!route) return { kind: "invalid" };
  const raw = rows.find((row: any) => String(row?.id || "") === route.id);
  const metadata = raw?.metadata && typeof raw.metadata === "object" && !Array.isArray(raw.metadata)
    ? raw.metadata as Record<string, unknown>
    : {};
  return { kind: "ok", value: { route, metadata } };
}

async function loadCredential(db: DbClient, route: HProviderRoute): Promise<
  | { ok: true; ciphertext: string; iv: string; metadata: Record<string, unknown> }
  | { ok: false; reason: string }
> {
  const { data, error } = await db.from("h_runtime_ai_credentials")
    .select("provider,secret_ciphertext,secret_iv,secret_version,selected_model,oauth_metadata")
    .eq("id", route.credentialId)
    .maybeSingle();
  if (error || !data) return { ok: false, reason: "owner_paid_credential_missing" };
  if (String(data.provider || "") !== route.provider) return { ok: false, reason: "owner_paid_credential_provider_mismatch" };
  if (Number(data.secret_version || 0) !== CREDENTIAL_VERSION) return { ok: false, reason: "owner_paid_credential_version_mismatch" };
  if (String(data.selected_model || "") !== route.selectedModel) return { ok: false, reason: "owner_paid_credential_model_mismatch" };
  const metadata = data.oauth_metadata && typeof data.oauth_metadata === "object" && !Array.isArray(data.oauth_metadata)
    ? data.oauth_metadata as Record<string, unknown>
    : {};
  if (metadata.owner_paid !== true || metadata.byok !== true) return { ok: false, reason: "owner_paid_credential_consent_metadata_missing" };
  const ciphertext = String(data.secret_ciphertext || "");
  const iv = String(data.secret_iv || "");
  if (!ciphertext || !iv) return { ok: false, reason: "owner_paid_credential_ciphertext_missing" };
  return { ok: true, ciphertext, iv, metadata };
}

async function loadOpenRouterModels(fetchImpl: FetchLike, apiKey: string): Promise<any[]> {
  const response = await fetchImpl(OPENROUTER_MODELS_URL, {
    headers: { Authorization: `Bearer ${apiKey}`, Accept: "application/json" },
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`catalog_http_${response.status}:${text.slice(0, 120)}`);
  const body = JSON.parse(text);
  if (!Array.isArray(body?.data)) throw new Error("catalog_shape_invalid");
  return body.data;
}

async function claimPaidCall(db: DbClient, route: HProviderRoute): Promise<{ allowed: boolean; callsUsed: number; dailyLimit: number }> {
  const { data, error } = await db.rpc("h_claim_owner_paid_ai_call", { p_route_id: route.id });
  if (error) return { allowed: false, callsUsed: 0, dailyLimit: route.dailyCallLimit || 0 };
  const row = Array.isArray(data) ? data[0] : data;
  const allowed = row?.allowed === true &&
    String(row?.provider || "") === route.provider &&
    String(row?.credential_id || "") === route.credentialId &&
    String(row?.selected_model || "") === route.selectedModel;
  return {
    allowed,
    callsUsed: Math.max(0, Math.floor(Number(row?.calls_used) || 0)),
    dailyLimit: Math.max(0, Math.floor(Number(row?.daily_limit) || route.dailyCallLimit || 0)),
  };
}

async function recordUsage(
  db: DbClient,
  routeId: string,
  promptTokens: number,
  completionTokens: number,
  costUsd: number,
) {
  const { error } = await db.rpc("h_record_owner_paid_ai_usage", {
    p_route_id: routeId,
    p_prompt_tokens: promptTokens,
    p_completion_tokens: completionTokens,
    p_cost_usd: costUsd,
  });
  if (error) throw error;
}

async function autoDisableRoute(db: DbClient, loaded: LoadedRoute, reason: string) {
  const now = new Date().toISOString();
  const metadata = {
    ...loaded.metadata,
    auto_disabled: true,
    auto_disabled_reason: reason,
    auto_disabled_at: now,
  };
  await db.from("h_runtime_ai_provider_registry").update({
    enabled: false,
    metadata,
    updated_at: now,
  }).eq("id", loaded.route.id).eq("route_class", "owner_paid");
}

async function recordState(db: DbClient, route: HProviderRoute, value: Record<string, unknown>) {
  try {
    await db.from("h_runtime_state").upsert({
      key: "owner_paid_ai",
      value: {
        route_id: route.id,
        provider: route.provider,
        model: route.selectedModel,
        owner_paid: true,
        exclusive_ai_routing: true,
        hard_tasks_only: false,
        allow_free_fallback: false,
        ...value,
      },
      updated_at: new Date().toISOString(),
    }, { onConflict: "key" });
  } catch (_) {
    // Observability must not override the paid safety decision.
  }
}

function blocked(route: HProviderRoute, reason: string): HOwnerPaidCompletion {
  return {
    status: "blocked",
    routeId: route.id,
    allowFreeFallback: false,
    reason,
  };
}

function requestFootprint(messages: any[]): { textChars: number; mediaDataChars: number } {
  let textChars = 0;
  let mediaDataChars = 0;
  for (const message of Array.isArray(messages) ? messages : []) {
    const content = message?.content;
    if (typeof content === "string") {
      textChars += content.length;
      continue;
    }
    if (!Array.isArray(content)) continue;
    for (const part of content) {
      if (part?.type === "text" && typeof part?.text === "string") textChars += part.text.length;
      const imageUrl = typeof part?.image_url?.url === "string" ? part.image_url.url : "";
      const fileData = typeof part?.file?.file_data === "string" ? part.file.file_data : "";
      if (imageUrl.startsWith("data:")) mediaDataChars += imageUrl.length;
      if (fileData.startsWith("data:")) mediaDataChars += fileData.length;
    }
  }
  return { textChars, mediaDataChars };
}

async function decryptOwnerPaidSecret(provider: string, ciphertext: string, iv: string): Promise<string> {
  const root = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!root) throw new Error("SUPABASE_SERVICE_ROLE_KEY is not configured");
  const digest = await crypto.subtle.digest(
    "SHA-256",
    toArrayBuffer(new TextEncoder().encode(`h-owner-paid-ai-aes-v1:${provider}:${root}`)),
  );
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["decrypt"]);
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: toArrayBuffer(decodeBase64Url(iv)) },
    key,
    toArrayBuffer(decodeBase64Url(ciphertext)),
  );
  return new TextDecoder().decode(decrypted);
}

function safeEnv(name: string): string {
  try { return String(Deno.env.get(name) || ""); } catch (_) { return ""; }
}

function decodeBase64Url(value: string): Uint8Array {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

function boundedTokenCount(value: unknown): number {
  const number = Math.floor(Number(value) || 0);
  return Number.isFinite(number) ? Math.max(0, Math.min(2_147_483_647, number)) : 0;
}

function boundedCostUsd(value: unknown): number {
  const number = Number(value);
  if (!Number.isFinite(number) || number <= 0) return 0;
  return Math.min(1_000_000, number);
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}