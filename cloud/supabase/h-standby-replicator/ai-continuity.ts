import { rekeyAiCredentialRows, type AiCredentialRow } from "./ai-credential-rekey.ts";

const MAX_AI_CREDENTIALS = 16;
const MAX_AI_ROUTES = 32;
const MAX_AI_USAGE_ROWS = 32;
const MAX_AI_ROUTE_STATS = 1000;

export type AiContinuitySyncResult = {
  ok: true;
  credentialsRekeyed: boolean;
  freeAiRouteReady: boolean;
  paidAiBudgetContinuityReady: boolean;
  counts: Record<string, number>;
};

export async function syncStandbyAiContinuity(input: {
  db: any;
  endpoint: string;
  primaryServiceRole: string;
  standbyServiceRole: string;
}): Promise<AiContinuitySyncResult> {
  const snapshot = await buildAiContinuitySnapshot(
    input.db,
    input.primaryServiceRole,
    input.standbyServiceRole,
  );

  const response = await fetch(`${input.endpoint}/rest/v1/rpc/h_apply_standby_ai_continuity_v1`, {
    method: "POST",
    headers: {
      apikey: input.standbyServiceRole,
      Authorization: `Bearer ${input.standbyServiceRole}`,
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
    body: JSON.stringify({ p_snapshot: snapshot }),
  });
  const text = await response.text();
  let body: any = {};
  try { body = text ? JSON.parse(text) : {}; } catch (_) {}
  if (!response.ok || body?.ok !== true) {
    throw new Error(`standby_ai_continuity_apply_${response.status}`);
  }
  return {
    ok: true,
    credentialsRekeyed: body?.aiCredentialsRekeyReady === true,
    freeAiRouteReady: body?.freeAiRouteReady === true,
    paidAiBudgetContinuityReady: body?.paidAiBudgetContinuityReady === true,
    counts: snapshot.counts,
  };
}

async function buildAiContinuitySnapshot(
  db: any,
  primaryServiceRole: string,
  standbyServiceRole: string,
) {
  const generatedAt = new Date().toISOString();
  const usageDate = generatedAt.slice(0, 10);
  const [credentialsResult, routesResult, usageResult, statsResult] = await Promise.all([
    db.from("h_runtime_ai_credentials")
      .select("id,provider,secret_ciphertext,secret_iv,secret_version,selected_model,model_verified_at,oauth_metadata,connected_at,updated_at")
      .order("id")
      .limit(MAX_AI_CREDENTIALS + 1),
    db.from("h_runtime_ai_provider_registry")
      .select("id,provider,route_class,credential_id,selected_model,enabled,owner_enabled_at,hard_tasks_only,allow_free_fallback,daily_call_limit,priority,metadata,created_at,updated_at")
      .order("priority")
      .order("id")
      .limit(MAX_AI_ROUTES + 1),
    db.from("h_runtime_ai_paid_usage_daily")
      .select("route_id,usage_date,calls,prompt_tokens,completion_tokens,last_used_at,updated_at,cost_usd")
      .eq("usage_date", usageDate)
      .order("route_id")
      .limit(MAX_AI_USAGE_ROWS + 1),
    db.from("h_runtime_ai_route_stats")
      .select("provider,model,capability,attempts,successes,failures,rate_limits,consecutive_failures,avg_latency_ms,prompt_tokens,completion_tokens,last_http_status,last_rate_limit_remaining,last_rate_limit_reset_at,last_error,cooldown_until,last_used_at,last_success_at,updated_at")
      .order("provider")
      .order("model")
      .order("capability")
      .limit(MAX_AI_ROUTE_STATS + 1),
  ]);

  for (const [name, result] of [
    ["credentials", credentialsResult],
    ["providerRoutes", routesResult],
    ["paidUsageToday", usageResult],
    ["routeStats", statsResult],
  ] as const) {
    if (result?.error) throw result.error;
    if (!Array.isArray(result?.data)) throw new Error(`standby_ai_${name}_invalid`);
  }

  const credentials = credentialsResult.data as AiCredentialRow[];
  const providerRoutes = routesResult.data as any[];
  const paidUsageToday = usageResult.data as any[];
  const routeStats = statsResult.data as any[];
  if (credentials.length > MAX_AI_CREDENTIALS) throw new Error("standby_ai_credentials_requires_pagination");
  if (providerRoutes.length > MAX_AI_ROUTES) throw new Error("standby_ai_routes_requires_pagination");
  if (paidUsageToday.length > MAX_AI_USAGE_ROWS) throw new Error("standby_ai_usage_requires_pagination");
  if (routeStats.length > MAX_AI_ROUTE_STATS) throw new Error("standby_ai_stats_requires_pagination");

  const rekeyedCredentials = await rekeyAiCredentialRows(credentials, primaryServiceRole, standbyServiceRole);
  const usageByRoute = new Map(paidUsageToday.map((row: any) => [String(row.route_id || ""), row]));
  for (const route of providerRoutes) {
    if (route?.route_class !== "owner_paid" || route?.enabled !== true) continue;
    const routeId = String(route?.id || "");
    if (!routeId || usageByRoute.has(routeId)) continue;
    const synthetic = {
      route_id: routeId,
      usage_date: usageDate,
      calls: 0,
      prompt_tokens: 0,
      completion_tokens: 0,
      last_used_at: null,
      updated_at: generatedAt,
      cost_usd: 0,
    };
    paidUsageToday.push(synthetic);
    usageByRoute.set(routeId, synthetic);
  }
  if (paidUsageToday.length > MAX_AI_USAGE_ROWS) throw new Error("standby_ai_usage_requires_pagination");

  return {
    format: "h-standby-ai-continuity",
    version: 1,
    assistantIdentity: "H",
    generatedAt,
    usageDate,
    credentials: rekeyedCredentials,
    providerRoutes,
    paidUsageToday,
    routeStats,
    counts: {
      credentials: rekeyedCredentials.length,
      providerRoutes: providerRoutes.length,
      paidUsageToday: paidUsageToday.length,
      routeStats: routeStats.length,
    },
    rawProviderSecretsIncluded: false,
    sourceCiphertextsCopiedUnchanged: false,
    setupTokensIncluded: false,
    oauthPendingIncluded: false,
  };
}
