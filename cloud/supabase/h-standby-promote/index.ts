import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-standby-promote";
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;
const DIGEST_PATTERN = /^[0-9a-f]{64}$/i;

type DbClient = any;
type PromotionAttestation = {
  requestId: string;
  promotedAt: string | null;
};

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
  if (String(body?.mode || "") !== "request_only") {
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
    const canonicalPromotionRequestId = REQUEST_ID_PATTERN.test(String(result?.requestId || ""))
      ? String(result.requestId)
      : requestId;
    return reply({
      ok: true,
      service: FUNCTION_NAME,
      promoted: true,
      active: true,
      mode: "request_only",
      requestId,
      canonicalPromotionRequestId,
      idempotent: result?.idempotent === true,
      raceRecovered: false,
      promotedAt: String(result?.promotedAt || "") || null,
      replicaWritesFenced: result?.replicaWritesFenced !== false,
      schedulerActive: false,
      autonomousOutboundActive: false,
    });
  } catch (error) {
    // A second ingress request may lose the promotion race after another request has already
    // atomically promoted this standby. Recover only when the complete active attestation is
    // still internally consistent; every partial or ambiguous state remains fail-closed.
    const active = await loadActivePromotionAttestation(db).catch(() => null);
    if (active) {
      return reply({
        ok: true,
        service: FUNCTION_NAME,
        promoted: true,
        active: true,
        mode: "request_only",
        requestId,
        canonicalPromotionRequestId: active.requestId,
        idempotent: true,
        raceRecovered: active.requestId !== requestId,
        promotedAt: active.promotedAt,
        replicaWritesFenced: true,
        schedulerActive: false,
        autonomousOutboundActive: false,
      });
    }
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_promotion_rejected" }, 409);
  }
});

async function loadActivePromotionAttestation(db: DbClient): Promise<PromotionAttestation | null> {
  const { data, error } = await db.from("h_runtime_state")
    .select("key,value")
    .in("key", ["standby_runtime", "standby_execution", "standby_promotion"]);
  if (error) throw error;
  const rows = Array.isArray(data) ? data : [];
  const valueFor = (key: string): Record<string, unknown> => {
    const value = rows.find((row: any) => String(row?.key || "") === key)?.value;
    return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
  };
  const runtime = valueFor("standby_runtime");
  const execution = valueFor("standby_execution");
  const promotion = valueFor("standby_promotion");

  const canonicalRequestId = String(promotion.request_id || "").trim();
  const runtimeRequestId = String(runtime.promotion_request_id || "").trim();
  const digest = String(promotion.source_digest || "").trim();
  const promotedAt = validIsoOrNull(promotion.promoted_at);

  const valid = runtime.promoted === true &&
    runtime.allow_replica_writes === false &&
    runtime.execution_runtime_ready === true &&
    String(runtime.promotion_mode || "") === "request_only" &&
    REQUEST_ID_PATTERN.test(runtimeRequestId) &&
    runtimeRequestId === canonicalRequestId &&
    String(execution.mode || "") === "request_active" &&
    execution.promotion_controls_ready === true &&
    execution.execution_runtime_ready === true &&
    execution.scheduler_active === false &&
    execution.autonomous_outbound_active === false &&
    String(promotion.protocol || "") === "h_standby_promotion_v1" &&
    String(promotion.status || "") === "active" &&
    String(promotion.mode || "") === "request_only" &&
    REQUEST_ID_PATTERN.test(canonicalRequestId) &&
    DIGEST_PATTERN.test(digest) &&
    promotion.replica_writes_fenced === true &&
    promotion.scheduler_active === false &&
    promotion.autonomous_outbound_active === false &&
    promotedAt !== null;

  return valid ? { requestId: canonicalRequestId, promotedAt } : null;
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

function validIsoOrNull(value: unknown): string | null {
  const text = String(value || "").trim();
  if (!text) return null;
  const parsed = Date.parse(text);
  return Number.isFinite(parsed) && parsed <= Date.now() + 5_000 ? text : null;
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