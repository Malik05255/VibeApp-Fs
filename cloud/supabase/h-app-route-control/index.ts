import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";

const FUNCTION_NAME = "h-app-route-control";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";

type DbClient = any;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return reply({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return reply({ ok: false, error: compactErrorCode(error) }, 401);
  }

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").trim();
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  try {
    const identitySecret = await loadIdentitySecret(db);
    const fingerprint = await secretFingerprint(identitySecret, GOOGLE_SUB_LABEL, google.subject);
    if (!await isLinkedIdentity(db, fingerprint, google.audience)) {
      return reply({ ok: false, error: "app_not_linked", linked: false }, 403);
    }

    const { data: backup, error } = await db.from("h_runtime_cloud_registry")
      .select("endpoint,enabled,ready,last_health_ok,credential_id,metadata")
      .eq("id", BACKUP_CLOUD_ID)
      .eq("cloud_role", "backup")
      .maybeSingle();
    if (error) throw error;

    const metadata = objectOrEmpty(backup?.metadata);
    const targetEndpoint = backup?.enabled === true ? normalizeSupabaseEndpoint(String(backup.endpoint || "")) : null;
    const standbyRequestOnlyActive = Boolean(
      targetEndpoint &&
      backup?.ready === true &&
      backup?.last_health_ok === true &&
      backup?.credential_id &&
      metadata.storage_backup_ready === true &&
      metadata.standby_runtime_ready === true &&
      metadata.runtime_health_ok === true &&
      metadata.standby_promoted_request_only === true &&
      metadata.standby_replica_writes_fenced === true &&
      metadata.standby_scheduler_active === false &&
      metadata.standby_autonomous_outbound_active === false &&
      metadata.automatic_traffic_switch_performed === false
    );

    return reply({
      ok: true,
      linked: true,
      service: FUNCTION_NAME,
      preferredRole: standbyRequestOnlyActive ? "standby" : "primary",
      standbyRequestOnlyActive,
      targetEndpoint,
      checkedAt: new Date().toISOString(),
      credentialsExposed: false,
      runtimeSecretExposed: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "app_route_control_failed" }, 500);
  }
});

async function isLinkedIdentity(db: DbClient, fingerprint: string, audience: string): Promise<boolean> {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("google_audience,active")
    .eq("google_subject_fingerprint", fingerprint)
    .eq("active", true)
    .maybeSingle();
  if (error) throw error;
  return data?.active === true && String(data.google_audience || "") === audience;
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

function normalizeSupabaseEndpoint(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:" || !url.hostname.endsWith(".supabase.co")) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    return `${url.protocol}//${url.host}`;
  } catch {
    return null;
  }
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function compactErrorCode(error: unknown): string {
  return (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_")
    .slice(0, 120) || "app_route_control_failed";
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
