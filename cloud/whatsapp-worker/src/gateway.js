import whatsappRouter, { selectUnifiedRuntime, standbyRuntimeConfigured } from "./router.js";

const APP_FAILOVER_ROUTE_PATH = "/h-app-failover-route";
const APP_ROUTE_STATUS_FUNCTION = "h-standby-route-status";
const OWNER_AUTH_TIMEOUT_MS = 2500;

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === APP_FAILOVER_ROUTE_PATH) {
      return handleAppFailoverRoute(request, env);
    }
    return whatsappRouter.fetch(request, env, ctx);
  },

  async scheduled(event, env, ctx) {
    return whatsappRouter.scheduled(event, env, ctx);
  },
};

/**
 * Neutral failover witness for Android.
 *
 * The phone never receives a runtime secret and never promotes a standby itself. The caller
 * must first prove it is the replicated Google owner against the standby control-plane status
 * endpoint. Only then may this Worker reuse the same server-side, double-primary-probe
 * request-only promotion path used by WhatsApp ingress.
 */
export async function handleAppFailoverRoute(
  request,
  env,
  fetchImpl = fetch,
  runtimeSelector = selectUnifiedRuntime,
) {
  if (request.method !== "POST") return json({ ok: false, error: "method_not_allowed" }, 405);
  if (!standbyRuntimeConfigured(env)) return json({ ok: false, error: "standby_not_configured" }, 503);

  const authorization = String(request.headers.get("authorization") || "").trim();
  if (!/^Bearer\s+\S+$/i.test(authorization)) {
    return json({ ok: false, error: "google_sign_in_required" }, 401);
  }

  const standbyVoiceUrl = String(env.H_STANDBY_SUPABASE_VOICE_URL || "").trim();
  const standbyBaseUrl = deriveSupabaseBaseUrl(standbyVoiceUrl);
  const routeStatusUrl = deriveSupabaseFunctionUrl(standbyVoiceUrl, APP_ROUTE_STATUS_FUNCTION);
  if (!standbyBaseUrl || !routeStatusUrl) {
    return json({ ok: false, error: "standby_route_invalid" }, 503);
  }

  const ownerCheck = await probeOwnerRouteStatus(routeStatusUrl, authorization, fetchImpl);
  if (!ownerCheck.ok) return json({ ok: false, error: ownerCheck.error }, ownerCheck.status);

  let selected;
  try {
    selected = await runtimeSelector(env, fetchImpl);
  } catch {
    return json({ ok: false, error: "no_validated_h_runtime" }, 503);
  }

  if (selected?.role !== "standby") {
    return json({
      ok: true,
      route: "primary",
      standbyActive: false,
      failoverPerformed: false,
      credentialsExposed: false,
      runtimeSecretExposed: false,
    });
  }

  // Do not trust the promotion call alone. Re-authenticate the owner against the promoted
  // standby and require the Android-facing active attestation before revealing the public route.
  const activeCheck = await probeOwnerRouteStatus(routeStatusUrl, authorization, fetchImpl);
  if (!activeCheck.ok || activeCheck.body?.activeReady !== true || activeCheck.body?.mode !== "request_only") {
    return json({ ok: false, error: "standby_active_attestation_failed" }, 503);
  }

  return json({
    ok: true,
    route: "standby",
    standbyActive: true,
    failoverPerformed: true,
    standbyBaseUrl,
    mode: "request_only",
    promotionAttested: activeCheck.body?.promotionAttested === true,
    replicaWritesEnabled: activeCheck.body?.replicaWritesEnabled === true,
    schedulerActive: activeCheck.body?.schedulerActive === true,
    autonomousOutboundActive: activeCheck.body?.autonomousOutboundActive === true,
    replicationProtocol: String(activeCheck.body?.replicationProtocol || ""),
    restoreVerified: activeCheck.body?.restoreVerified === true,
    credentialsExposed: false,
    runtimeSecretExposed: false,
  });
}

async function probeOwnerRouteStatus(url, authorization, fetchImpl) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), OWNER_AUTH_TIMEOUT_MS);
  try {
    const response = await fetchImpl(url, {
      method: "POST",
      headers: {
        Authorization: authorization,
        "Content-Type": "application/json",
        Accept: "application/json",
        "Cache-Control": "no-store",
      },
      body: "{}",
      signal: controller.signal,
    });
    const body = await response.json().catch(() => ({}));
    if (response.status === 401) return { ok: false, status: 401, error: "google_sign_in_required", body: {} };
    if (response.status === 403) return { ok: false, status: 403, error: "app_not_linked", body: {} };
    if (!response.ok || body?.ok !== true || body?.linked !== true) {
      return { ok: false, status: 503, error: "standby_owner_auth_unavailable", body: {} };
    }
    return { ok: true, status: 200, error: null, body };
  } catch {
    return { ok: false, status: 503, error: "standby_owner_auth_unavailable", body: {} };
  } finally {
    clearTimeout(timeout);
  }
}

export function deriveSupabaseBaseUrl(endpoint) {
  try {
    const url = new URL(String(endpoint || "").trim());
    if (url.protocol !== "https:" || !url.hostname.endsWith(".supabase.co")) return null;
    if (url.username || url.password || url.search || url.hash || !url.pathname.startsWith("/functions/v1/")) return null;
    return `https://${url.hostname}`;
  } catch {
    return null;
  }
}

function deriveSupabaseFunctionUrl(endpoint, functionName) {
  const base = deriveSupabaseBaseUrl(endpoint);
  return base ? `${base}/functions/v1/${functionName}` : null;
}

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
