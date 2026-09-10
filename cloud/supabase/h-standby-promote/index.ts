import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";

const FUNCTION_NAME = "h-standby-promote";
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;
const DIGEST_PATTERN = /^[0-9a-f]{64}$/i;
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const PROMOTION_PROTOCOL = "h_standby_promotion_v1";

type DbClient = any;
type PromotionAuth = "runtime_secret" | "google_owner";

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").trim();
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const authorization = await authorizePromotion(req, db);
  if (!authorization.ok) return reply({ ok: false, error: authorization.error }, authorization.status);

  let body: any = {};
  try { body = await req.json(); } catch (_) {}
  const mode = String(body?.mode || "").trim().toLowerCase();

  if (mode === "status") {
    try {
      const status = await loadPromotionStatus(db);
      return reply({
        ok: true,
        service: FUNCTION_NAME,
        ...status,
        authenticatedBy: authorization.mode,
      });
    } catch (error) {
      console.error(`${FUNCTION_NAME} status failed`, compactErrorCode(error));
      return reply({ ok: false, error: "standby_promotion_status_failed" }, 500);
    }
  }

  if (mode !== "request_only") {
    return reply({ ok: false, error: "unsupported_promotion_mode" }, 400);
  }
  const requestId = String(body?.request_id || "").trim();
  if (!REQUEST_ID_PATTERN.test(requestId)) {
    return reply({ ok: false, error: "invalid_request_id" }, 400);
  }

  try {
    const { data, error } = await db.rpc("h_promote_standby_request_only_v1", { p_request_id: requestId });
    if (error) throw error;
    const result = data && typeof data === "object" && !Array.isArray(data) ? data : {};
    if (result?.ok !== true || result?.promoted !== true || result?.active !== true || result?.mode !== "request_only") {
      throw new Error("promotion_rpc_contract_mismatch");
    }
    return reply({
      ok: true,
      service: FUNCTION_NAME,
      promoted: true,
      active: true,
      mode: "request_only",
      requestId,
      idempotent: result?.idempotent === true,
      promotedAt: String(result?.promotedAt || "") || null,
      replicaWritesFenced: result?.replicaWritesFenced !== false,
      schedulerActive: false,
      autonomousOutboundActive: false,
      authenticatedBy: authorization.mode,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_promotion_rejected" }, 409);
  }
});

async function loadPromotionStatus(db: DbClient) {
  const { data, error } = await db.from("h_runtime_state")
    .select("key,value")
    .in("key", ["standby_runtime", "standby_execution", "standby_promotion", "standby_replication"]);
  if (error) throw error;
  const states = new Map<string, Record<string, unknown>>();
  for (const row of Array.isArray(data) ? data : []) {
    states.set(String(row?.key || ""), objectOrEmpty(row?.value));
  }
  const runtime = states.get("standby_runtime") ?? {};
  const execution = states.get("standby_execution") ?? {};
  const promotion = states.get("standby_promotion") ?? {};
  const replication = states.get("standby_replication") ?? {};
  const requestId = stringOrNull(promotion.request_id);
  const digest = stringOrNull(promotion.source_digest);
  const replicationDigest = stringOrNull(replication.last_digest);

  const active = runtime.promoted === true &&
    runtime.allow_replica_writes === false &&
    runtime.execution_runtime_ready === true &&
    runtime.promotion_mode === "request_only" &&
    execution.contract === "h_standby_execution_v1" &&
    execution.mode === "request_active" &&
    execution.execution_runtime_ready === true &&
    execution.scheduler_active === false &&
    execution.autonomous_outbound_active === false &&
    promotion.protocol === PROMOTION_PROTOCOL &&
    promotion.status === "active" &&
    promotion.mode === "request_only" &&
    Boolean(requestId && REQUEST_ID_PATTERN.test(requestId)) &&
    requestId === stringOrNull(runtime.promotion_request_id) &&
    Boolean(digest && DIGEST_PATTERN.test(digest)) &&
    digest === replicationDigest;

  return {
    promoted: runtime.promoted === true,
    active,
    mode: active ? "request_only" : "passive",
    requestId: active ? requestId : null,
    replicaWritesFenced: active && runtime.allow_replica_writes === false,
    schedulerActive: execution.scheduler_active === true,
    autonomousOutboundActive: execution.autonomous_outbound_active === true,
  };
}

async function authorizePromotion(
  req: Request,
  db: DbClient,
): Promise<{ ok: true; mode: PromotionAuth } | { ok: false; status: number; error: string }> {
  const [runtimeSecret, identitySecret] = await Promise.all([
    loadRuntimeSecret(db).catch(() => ""),
    loadIdentitySecret(db).catch(() => ""),
  ]);
  const providedRuntimeSecret = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (runtimeSecret && providedRuntimeSecret && constantTimeEqual(runtimeSecret, providedRuntimeSecret)) {
    return { ok: true, mode: "runtime_secret" };
  }

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return { ok: false, status: 401, error: "Unauthorized" };
  if (!identitySecret) return { ok: false, status: 500, error: "runtime_unavailable" };

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return { ok: false, status: 401, error: compactGoogleAuthError(error) };
  }

  const subjectFingerprint = await secretFingerprint(identitySecret, GOOGLE_SUB_LABEL, google.subject);
  const linked = await isLinkedOwner(db, subjectFingerprint, google.audience);
  if (!linked) return { ok: false, status: 403, error: "app_not_linked" };
  return { ok: true, mode: "google_owner" };
}

async function isLinkedOwner(db: DbClient, subjectFingerprint: string, audience: string): Promise<boolean> {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("google_audience,active")
    .eq("google_subject_fingerprint", subjectFingerprint)
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
  const signed = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${label}:${value}`),
  );
  return [...new Uint8Array(signed)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function loadRuntimeSecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

function bearerToken(header: string | null): string | null {
  const match = String(header || "").match(/^Bearer\s+(.+)$/i);
  const token = match?.[1]?.trim() || "";
  return token || null;
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function stringOrNull(value: unknown): string | null {
  const text = String(value ?? "").trim();
  return text || null;
}

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return diff === 0;
}

function compactGoogleAuthError(error: unknown): string {
  const code = compactErrorCode(error);
  return code.startsWith("invalid_google_") ||
      code.startsWith("expired_google_") ||
      code.startsWith("unsupported_google_") ||
      code.startsWith("unknown_google_") ||
      code.startsWith("unverified_google_") ||
      code === "google_keys_unavailable"
    ? code
    : "google_sign_in_required";
}

function compactErrorCode(error: unknown): string {
  const raw = (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 120) || "standby_promotion_failed";
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