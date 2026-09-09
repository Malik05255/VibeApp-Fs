export type HRouteCapability = "text" | "image" | "file";

export type HRouteState = {
  model: string;
  capability: HRouteCapability;
  attempts?: number;
  successes?: number;
  failures?: number;
  consecutive_failures?: number;
  avg_latency_ms?: number;
  cooldown_until?: string | null;
};

export type RouteFailureDecision = {
  retryNext: boolean;
  providerFatal: boolean;
  reason: string;
  cooldownMs: number;
};

const DEFAULT_RATE_LIMIT_COOLDOWN_MS = 15 * 60_000;
const MAX_RATE_LIMIT_COOLDOWN_MS = 60 * 60_000;

export function isStrictlyZeroPriced(pricing: unknown): boolean {
  if (!pricing || typeof pricing !== "object" || Array.isArray(pricing)) return false;
  const values = pricing as Record<string, unknown>;
  for (const required of ["prompt", "completion"]) {
    const number = Number(values[required]);
    if (!Number.isFinite(number) || number !== 0) return false;
  }
  for (const value of Object.values(values)) {
    if (value == null || value === "") continue;
    const number = Number(value);
    if (!Number.isFinite(number) || number !== 0) return false;
  }
  return true;
}

export function modelSupportsInput(model: any, requiredInput: HRouteCapability): boolean {
  const modalities = Array.isArray(model?.architecture?.input_modalities)
    ? model.architecture.input_modalities.map((value: unknown) => String(value).toLowerCase())
    : [];
  if (modalities.length) return modalities.includes(requiredInput);
  return requiredInput === "text";
}

/**
 * Rank only catalog-proven zero-priced models. Routes under an active cooldown are omitted
 * entirely so repeated requests do not burn the same exhausted free quota.
 */
export function rankStrictlyFreeModelCandidates(
  models: any[],
  preferred: string | null,
  requiredInput: HRouteCapability,
  routeStates: HRouteState[] = [],
  preferDifferentFrom: string | null = null,
  nowMs = Date.now(),
): string[] {
  const stateByModel = new Map(routeStates
    .filter((state) => state.capability === requiredInput)
    .map((state) => [state.model, state]));

  const candidates = models
    .filter((model) =>
      typeof model?.id === "string" &&
      isStrictlyZeroPriced(model?.pricing) &&
      modelSupportsInput(model, requiredInput)
    )
    .map((model) => {
      const id = String(model.id);
      const state = stateByModel.get(id);
      const cooldownMs = state?.cooldown_until ? Date.parse(String(state.cooldown_until)) : 0;
      return {
        id,
        contextLength: Number(model?.context_length || 0),
        state,
        cooling: Number.isFinite(cooldownMs) && cooldownMs > nowMs,
      };
    })
    .filter((candidate) => !candidate.cooling);

  candidates.sort((a, b) => routeScore(b) - routeScore(a) || a.id.localeCompare(b.id));
  return candidates.map((candidate) => candidate.id);

  function routeScore(candidate: { id: string; contextLength: number; state?: HRouteState }) {
    const state = candidate.state;
    const attempts = Math.max(0, Number(state?.attempts || 0));
    const successes = Math.max(0, Number(state?.successes || 0));
    const successRate = attempts > 0 ? successes / attempts : 0.75;
    const consecutiveFailures = Math.max(0, Number(state?.consecutive_failures || 0));
    const latency = Math.max(0, Number(state?.avg_latency_ms || 0));
    let score = successRate * 1000;
    score -= consecutiveFailures * 250;
    score -= Math.min(500, latency / 40);
    score += Math.min(300, Math.log2(Math.max(2, candidate.contextLength)) * 12);
    if (candidate.id === "openrouter/free") score += 350;
    if (preferred && candidate.id === preferred) score += 700;
    if (preferDifferentFrom && candidate.id === preferDifferentFrom) score -= 900;
    return score;
  }
}

export function classifyRouteFailure(
  status: number | null,
  retryAfterHeader: string | null,
  message = "",
  nowMs = Date.now(),
): RouteFailureDecision {
  const normalized = String(message || "").toLowerCase();
  if (status === 401 || status === 403) {
    return { retryNext: false, providerFatal: true, reason: "credential_rejected", cooldownMs: 30 * 60_000 };
  }
  if (status === 402) {
    return { retryNext: false, providerFatal: true, reason: "billing_or_credit_guard", cooldownMs: 24 * 60 * 60_000 };
  }
  if (status === 429 || /rate.?limit|quota|too many requests/.test(normalized)) {
    return {
      retryNext: true,
      providerFatal: false,
      reason: "rate_limited",
      cooldownMs: retryAfterMs(retryAfterHeader, nowMs) ?? DEFAULT_RATE_LIMIT_COOLDOWN_MS,
    };
  }
  if (status === 404) {
    return { retryNext: true, providerFatal: false, reason: "model_unavailable", cooldownMs: 6 * 60 * 60_000 };
  }
  if (status === 400 || status === 422) {
    return { retryNext: true, providerFatal: false, reason: "model_request_rejected", cooldownMs: 60 * 60_000 };
  }
  if (status != null && status >= 500) {
    return { retryNext: true, providerFatal: false, reason: "provider_temporary_failure", cooldownMs: 5 * 60_000 };
  }
  if (/empty content|empty response/.test(normalized)) {
    return { retryNext: true, providerFatal: false, reason: "empty_content", cooldownMs: 30 * 60_000 };
  }
  return { retryNext: true, providerFatal: false, reason: "transport_or_model_failure", cooldownMs: 5 * 60_000 };
}

function retryAfterMs(value: string | null, nowMs: number): number | null {
  const raw = String(value || "").trim();
  if (!raw) return null;
  const seconds = Number(raw);
  if (Number.isFinite(seconds) && seconds >= 0) {
    return Math.max(1_000, Math.min(MAX_RATE_LIMIT_COOLDOWN_MS, seconds * 1000));
  }
  const absolute = Date.parse(raw);
  if (!Number.isFinite(absolute)) return null;
  return Math.max(1_000, Math.min(MAX_RATE_LIMIT_COOLDOWN_MS, absolute - nowMs));
}
