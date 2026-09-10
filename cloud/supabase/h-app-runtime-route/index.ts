import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";
import { evaluateAppRuntimeRoute } from "../h-app-sync/runtime-route-policy.ts";

const FUNCTION_NAME = "h-app-runtime-route";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return reply({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return reply({ ok: false, error: errorMessage(error) }, 401);
  }

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  try {
    const identitySecret = await loadIdentitySecret(db);
    const subjectFingerprint = await secretFingerprint(identitySecret, GOOGLE_SUB_LABEL, google.subject);
    const { data: identity, error: identityError } = await db.from("h_runtime_app_identities")
      .select("google_audience,active")
      .eq("google_subject_fingerprint", subjectFingerprint)
      .eq("active", true)
      .maybeSingle();
    if (identityError) throw identityError;
    if (!identity || String(identity.google_audience || "") !== google.audience) {
      return reply({ ok: false, error: "app_not_linked", linked: false }, 403);
    }

    const { data: states, error: stateError } = await db.from("h_runtime_state")
      .select("key,value")
      .in("key", ["standby_runtime", "standby_execution", "standby_replication", "standby_promotion"]);
    if (stateError) throw stateError;
    const state = stateMap(states);
    const decision = evaluateAppRuntimeRoute({
      runtime: state.standby_runtime,
      execution: state.standby_execution,
      replication: state.standby_replication,
      promotion: state.standby_promotion,
    });

    let backupEndpoint: string | null = null;
    let backupConfigured = false;
    if (decision.runtimeRole === "primary") {
      const backup = await loadTrustedBackupCandidate(db);
      backupEndpoint = backup?.endpoint ?? null;
      backupConfigured = Boolean(backupEndpoint);
    }

    return reply({
      ok: true,
      service: FUNCTION_NAME,
      routeProtocol: "h_app_runtime_route_v1",
      linked: true,
      runtimeRole: decision.runtimeRole,
      requestReady: decision.requestReady,
      requestOnlyActive: decision.requestOnlyActive,
      promoted: decision.promoted,
      replicaWritesFenced: decision.replicaWritesFenced,
      promotionAttested: decision.promotionAttested,
      backupConfigured,
      backupEndpoint,
      endpointIsPublicRoutingMetadata: true,
      runtimeSecretExposed: false,
      serviceRoleExposed: false,
      checkedAt: new Date().toISOString(),
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "app_runtime_route_failed" }, 500);
  }
});

async function loadTrustedBackupCandidate(db: any): Promise<{ endpoint: string } | null> {
  const { data, error } = await db.from("h_runtime_cloud_registry")
    .select("endpoint,enabled,ready,last_health_ok,metadata")
    .eq("id", BACKUP_CLOUD_ID)
    .eq("cloud_role", "backup")
    .maybeSingle();
  if (error) throw error;
  const metadata = objectOrEmpty(data?.metadata);
  if (
    data?.enabled !== true ||
    data?.ready !== true ||
    data?.last_health_ok !== true ||
    metadata.storage_backup_ready !== true ||
    metadata.standby_runtime_provisioned !== true ||
    metadata.standby_health_service_deployed !== true
  ) return null;
  const endpoint = normalizeSupabaseEndpoint(String(data.endpoint || ""));
  return endpoint ? { endpoint } : null;
}

function stateMap(rows: unknown): Record<string, Record<string, unknown>> {
  const result: Record<string, Record<string, unknown>> = {};
  for (const row of Array.isArray(rows) ? rows : []) {
    const key = String(row?.key || "");
    if (!key) continue;
    result[key] = objectOrEmpty(row?.value);
  }
  return result;
}

function normalizeSupabaseEndpoint(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:" || !/^[a-z0-9-]{8,64}[.]supabase[.]co$/i.test(url.hostname)) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    if (url.pathname && url.pathname !== "/") return null;
    return `https://${url.hostname.toLowerCase()}`;
  } catch {
    return null;
  }
}

async function secretFingerprint(secret: string, label: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(`${label}:${value}`));
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function compactErrorCode(error: unknown): string {
  return errorMessage(error).toLowerCase().replace(/[^a-z0-9_:-]+/g, "_").slice(0, 120) || "app_runtime_route_failed";
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
