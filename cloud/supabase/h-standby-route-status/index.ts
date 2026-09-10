import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";

const FUNCTION_NAME = "h-standby-route-status";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const PROMOTION_PROTOCOL = "h_standby_promotion_v1";
const EXECUTION_CONTRACT = "h_standby_execution_v1";
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;
const DIGEST_PATTERN = /^[0-9a-f]{64}$/i;

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

    const [runtime, execution, promotion] = await Promise.all([
      loadState(db, "standby_runtime"),
      loadState(db, "standby_execution"),
      loadState(db, "standby_promotion"),
    ]);

    const requestId = text(promotion.request_id);
    const runtimeRequestId = text(runtime.promotion_request_id);
    const sourceDigest = text(promotion.source_digest);
    const schedulerActive = execution.scheduler_active === true;
    const autonomousOutboundActive = execution.autonomous_outbound_active === true;

    const promotionAttested =
      text(promotion.protocol) === PROMOTION_PROTOCOL &&
      text(promotion.status) === "active" &&
      text(promotion.mode) === "request_only" &&
      Boolean(requestId && REQUEST_ID_PATTERN.test(requestId)) &&
      requestId === runtimeRequestId &&
      text(runtime.promotion_mode) === "request_only" &&
      Boolean(sourceDigest && DIGEST_PATTERN.test(sourceDigest));

    const activeReady =
      text(runtime.runtime_role) === "standby" &&
      text(runtime.h_identity) === "H" &&
      runtime.dedicated_h_standby === true &&
      runtime.promoted === true &&
      runtime.allow_replica_writes === false &&
      runtime.execution_runtime_ready === true &&
      text(execution.contract) === EXECUTION_CONTRACT &&
      text(execution.mode) === "request_active" &&
      execution.core_schema_ready === true &&
      execution.function_inventory_ready === true &&
      execution.runtime_secret_ready === true &&
      execution.app_identity_rekey_ready === true &&
      execution.whatsapp_identity_rekey_ready === true &&
      execution.ai_credentials_rekey_ready === true &&
      execution.free_ai_route_ready === true &&
      execution.paid_ai_budget_continuity_ready === true &&
      execution.promotion_controls_ready === true &&
      execution.execution_runtime_ready === true &&
      !schedulerActive &&
      !autonomousOutboundActive &&
      promotionAttested;

    return reply({
      ok: true,
      linked: true,
      service: FUNCTION_NAME,
      activeReady,
      mode: activeReady ? "request_only" : "inactive",
      promoted: runtime.promoted === true,
      promotionAttested,
      replicaWritesEnabled: runtime.allow_replica_writes === true,
      schedulerActive,
      autonomousOutboundActive,
      checkedAt: new Date().toISOString(),
      credentialsExposed: false,
      runtimeSecretExposed: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_route_status_failed" }, 500);
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

async function loadState(db: DbClient, key: string): Promise<Record<string, unknown>> {
  const { data, error } = await db.from("h_runtime_state").select("value").eq("key", key).maybeSingle();
  if (error) throw error;
  return data?.value && typeof data.value === "object" && !Array.isArray(data.value)
    ? data.value as Record<string, unknown>
    : {};
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

function text(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized || null;
}

function compactErrorCode(error: unknown): string {
  return (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_")
    .slice(0, 120) || "standby_route_status_failed";
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
