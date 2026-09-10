import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";

const FUNCTION_NAME = "h-standby-app-route";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;

type DbClient = any;

type StandbyState = {
  runtime: Record<string, unknown>;
  execution: Record<string, unknown>;
  promotion: Record<string, unknown>;
};

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
    // This is the only Google-authenticated endpoint allowed to inspect a passive standby.
    // It can only report readiness or perform the request-only promotion RPC below.
    const identitySecret = await loadIdentitySecret(db, { allowPassiveStandby: true });
    const subjectFingerprint = await secretFingerprint(identitySecret, GOOGLE_SUB_LABEL, google.subject);
    if (!await isLinkedOwner(db, subjectFingerprint, google.audience)) {
      return reply({ ok: false, error: "app_not_linked", linked: false }, 403);
    }

    const state = await loadStandbyState(db);
    if (!isDedicatedStandby(state.runtime)) {
      return reply({ ok: false, error: "not_dedicated_standby" }, 409);
    }

    const body = await req.json().catch(() => ({}));
    const action = String(body?.action || "status").trim().toLowerCase();

    if (action === "status") {
      if (isRequestOnlyActive(state)) {
        return reply(activeResponse(state, null, false));
      }

      const { data: prepared, error } = await db.rpc("h_prepare_standby_promotion_v1");
      if (error) throw error;
      const refreshed = await loadStandbyState(db);
      return reply({
        ok: true,
        service: FUNCTION_NAME,
        linked: true,
        preflightReady: prepared?.ready === true && isPassivePreflight(refreshed),
        activeReady: false,
        promoted: false,
        mode: "passive_preflight",
        schedulerActive: false,
        autonomousOutboundActive: false,
        automaticFailbackEnabled: false,
        runtimeSecretExposed: false,
      });
    }

    if (action === "promote_request_only") {
      const requestId = String(body?.request_id || "").trim();
      if (!REQUEST_ID_PATTERN.test(requestId)) {
        return reply({ ok: false, error: "invalid_request_id" }, 400);
      }

      if (isRequestOnlyActive(state)) {
        const existing = String(state.promotion.request_id || "").trim();
        if (existing && existing !== requestId) {
          return reply({ ok: false, error: "standby_already_promoted_with_different_request" }, 409);
        }
        return reply(activeResponse(state, requestId, true));
      }

      const { data: prepared, error: prepareError } = await db.rpc("h_prepare_standby_promotion_v1");
      if (prepareError) throw prepareError;
      if (prepared?.ready !== true) {
        return reply({
          ok: false,
          error: "standby_preflight_not_ready",
          preflightReady: false,
          activeReady: false,
        }, 409);
      }

      const { data: promoted, error: promotionError } = await db.rpc("h_promote_standby_request_only_v1", {
        p_request_id: requestId,
      });
      if (promotionError) throw promotionError;
      if (promoted?.ok !== true || promoted?.promoted !== true || promoted?.active !== true) {
        throw new Error("standby_promotion_rpc_contract_mismatch");
      }

      const after = await loadStandbyState(db);
      if (!isRequestOnlyActive(after)) throw new Error("standby_post_promotion_state_mismatch");
      if (String(after.promotion.request_id || "") !== requestId) {
        throw new Error("standby_promotion_request_attestation_mismatch");
      }
      return reply(activeResponse(after, requestId, false));
    }

    return reply({ ok: false, error: "unsupported_action" }, 400);
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_app_route_failed" }, 500);
  }
});

async function loadStandbyState(db: DbClient): Promise<StandbyState> {
  const { data, error } = await db.from("h_runtime_state")
    .select("key,value")
    .in("key", ["standby_runtime", "standby_execution", "standby_promotion"]);
  if (error) throw error;
  const rows = Array.isArray(data) ? data : [];
  const byKey = new Map(rows.map((row: any) => [String(row?.key || ""), objectOrEmpty(row?.value)]));
  return {
    runtime: byKey.get("standby_runtime") ?? {},
    execution: byKey.get("standby_execution") ?? {},
    promotion: byKey.get("standby_promotion") ?? {},
  };
}

function isDedicatedStandby(runtime: Record<string, unknown>): boolean {
  return runtime.runtime_role === "standby" &&
    runtime.h_identity === "H" &&
    runtime.dedicated_h_standby === true;
}

function isPassivePreflight(state: StandbyState): boolean {
  return isDedicatedStandby(state.runtime) &&
    state.runtime.promoted !== true &&
    state.runtime.allow_replica_writes === true &&
    state.runtime.execution_runtime_ready === true &&
    state.execution.contract === "h_standby_execution_v1" &&
    state.execution.mode === "passive_preflight" &&
    state.execution.promotion_controls_ready === true &&
    state.execution.scheduler_active !== true &&
    state.execution.autonomous_outbound_active !== true;
}

function isRequestOnlyActive(state: StandbyState): boolean {
  return isDedicatedStandby(state.runtime) &&
    state.runtime.promoted === true &&
    state.runtime.allow_replica_writes === false &&
    state.runtime.execution_runtime_ready === true &&
    state.runtime.promotion_mode === "request_only" &&
    state.execution.contract === "h_standby_execution_v1" &&
    state.execution.mode === "request_active" &&
    state.execution.execution_runtime_ready === true &&
    state.execution.scheduler_active !== true &&
    state.execution.autonomous_outbound_active !== true &&
    state.promotion.protocol === "h_standby_promotion_v1" &&
    state.promotion.status === "active" &&
    state.promotion.mode === "request_only" &&
    state.promotion.replica_writes_fenced === true;
}

function activeResponse(state: StandbyState, requestId: string | null, idempotent: boolean) {
  return {
    ok: true,
    service: FUNCTION_NAME,
    linked: true,
    preflightReady: false,
    activeReady: true,
    promoted: true,
    mode: "request_only",
    requestId: requestId ?? String(state.promotion.request_id || ""),
    idempotent,
    replicaWritesFenced: true,
    schedulerActive: false,
    autonomousOutboundActive: false,
    automaticFailbackEnabled: false,
    runtimeSecretExposed: false,
  };
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
  const raw = (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase().replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 140) || "standby_app_route_failed";
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
