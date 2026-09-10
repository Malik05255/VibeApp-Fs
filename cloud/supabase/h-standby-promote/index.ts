import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { verifyFenceAssertion } from "./fence-assertion.ts";

const FUNCTION_NAME = "h-standby-promote";
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;
const REQUIRED_FENCING_KEYS = [
  "fencing_public_jwk",
  "fencing_issuer",
  "fencing_primary_project_ref",
  "fencing_standby_project_ref",
] as const;

type DbClient = any;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").trim();
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  const provided = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (!runtimeSecret || !provided || !constantTimeEqual(runtimeSecret, provided)) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  let body: any = {};
  try { body = await req.json(); } catch (_) {}
  if (String(body?.mode || "") !== "fenced_request_only") {
    return reply({ ok: false, error: "external_fence_required" }, 400);
  }
  const assertion = String(body?.fence_assertion || "").trim();
  if (!assertion) return reply({ ok: false, error: "fence_assertion_required" }, 400);

  try {
    const config = await loadFencingConfig(db);
    const fence = await verifyFenceAssertion(assertion, config);
    if (!REQUEST_ID_PATTERN.test(fence.requestId)) throw new Error("fence_request_id_invalid");

    const priorFence = await loadFencingState(db);
    const priorEpoch = finiteNonNegativeInteger(priorFence?.last_fence_epoch) ?? 0;
    if (fence.fenceEpoch <= priorEpoch) throw new Error("fence_epoch_replayed");

    await recordFencingState(db, {
      contract: "h_standby_fencing_v1",
      status: "verified_pending_promotion",
      authority_configured: true,
      request_id: fence.requestId,
      fence_epoch: fence.fenceEpoch,
      assertion_sha256: fence.assertionSha256,
      primary_project_ref: fence.primaryProjectRef,
      standby_project_ref: fence.standbyProjectRef,
      primary_write_fenced: true,
      fenced_at: fence.fencedAt,
      automatic_self_promotion_enabled: false,
    });

    // The existing SQL RPC remains the atomic state transition. The externally reachable
    // promotion path is this Edge Function, which requires both runtime auth and a signed
    // independent fence assertion before the RPC can be reached.
    const { data, error } = await db.rpc("h_promote_standby_request_only_v1", {
      p_request_id: fence.requestId,
    });
    if (error) throw error;
    const result = data && typeof data === "object" && !Array.isArray(data) ? data : {};
    if (result?.ok !== true || result?.promoted !== true || result?.active !== true || result?.mode !== "request_only") {
      throw new Error("promotion_rpc_contract_mismatch");
    }

    await recordFencingState(db, {
      contract: "h_standby_fencing_v1",
      status: "active",
      authority_configured: true,
      request_id: fence.requestId,
      fence_epoch: fence.fenceEpoch,
      last_fence_epoch: fence.fenceEpoch,
      assertion_sha256: fence.assertionSha256,
      primary_project_ref: fence.primaryProjectRef,
      standby_project_ref: fence.standbyProjectRef,
      primary_write_fenced: true,
      fenced_at: fence.fencedAt,
      promoted_at: String(result?.promotedAt || "") || new Date().toISOString(),
      automatic_self_promotion_enabled: false,
    });

    return reply({
      ok: true,
      service: FUNCTION_NAME,
      promoted: true,
      active: true,
      mode: "fenced_request_only",
      requestId: fence.requestId,
      fenceEpoch: fence.fenceEpoch,
      idempotent: result?.idempotent === true,
      promotedAt: String(result?.promotedAt || "") || null,
      primaryWriteFenced: true,
      replicaWritesFenced: result?.replicaWritesFenced !== false,
      schedulerActive: false,
      autonomousOutboundActive: false,
      automaticSelfPromotionEnabled: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_promotion_rejected" }, 409);
  }
});

async function loadFencingConfig(db: DbClient) {
  const { data, error } = await db.from("h_runtime_config")
    .select("key,secret_value")
    .in("key", [...REQUIRED_FENCING_KEYS]);
  if (error) throw error;
  const values = new Map<string, string>();
  for (const row of Array.isArray(data) ? data : []) {
    values.set(String(row?.key || ""), String(row?.secret_value || "").trim());
  }
  const publicJwk = values.get("fencing_public_jwk") || "";
  const issuer = values.get("fencing_issuer") || "";
  const primaryProjectRef = values.get("fencing_primary_project_ref") || "";
  const standbyProjectRef = values.get("fencing_standby_project_ref") || "";
  if (!publicJwk || !issuer || !primaryProjectRef || !standbyProjectRef) {
    throw new Error("fencing_authority_not_configured");
  }
  return { publicJwk, issuer, primaryProjectRef, standbyProjectRef };
}

async function loadFencingState(db: DbClient): Promise<Record<string, unknown>> {
  const { data, error } = await db.from("h_runtime_state")
    .select("value")
    .eq("key", "standby_fencing")
    .maybeSingle();
  if (error) throw error;
  return data?.value && typeof data.value === "object" && !Array.isArray(data.value)
    ? data.value as Record<string, unknown>
    : {};
}

async function recordFencingState(db: DbClient, value: Record<string, unknown>): Promise<void> {
  const { error } = await db.from("h_runtime_state").upsert({
    key: "standby_fencing",
    value,
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
  if (error) throw error;
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

function finiteNonNegativeInteger(value: unknown): number | null {
  const number = Number(value);
  return Number.isSafeInteger(number) && number >= 0 ? number : null;
}

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  return diff === 0;
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
