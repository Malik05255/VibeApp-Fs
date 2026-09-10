import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-standby-promote";
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;

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
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_promotion_rejected" }, 409);
  }
});

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
