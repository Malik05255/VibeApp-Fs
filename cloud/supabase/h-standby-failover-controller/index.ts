import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-standby-failover-controller";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const BACKUP_CREDENTIAL_ID = "h_backup_supabase_storage";
const RUNTIME_SECRET_CREDENTIAL_ID = "h_backup_supabase_runtime_secret";
const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;

type DbClient = any;

type Target = {
  endpoint: string;
  serviceCiphertext: string;
  serviceIv: string;
  runtimeCiphertext: string;
  runtimeIv: string;
  metadata: Record<string, unknown>;
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const primaryUrl = String(Deno.env.get("SUPABASE_URL") || "").replace(/\/$/, "");
  const primaryServiceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!primaryUrl || !primaryServiceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(primaryUrl, primaryServiceRole, { auth: { persistSession: false } });
  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  const provided = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (!runtimeSecret || !provided || !constantTimeEqual(runtimeSecret, provided)) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  const body = await req.json().catch(() => ({}));
  if (String(body?.mode || "") !== "request_only") {
    return reply({ ok: false, error: "unsupported_failover_mode" }, 400);
  }
  const requestId = String(body?.request_id || "").trim();
  if (!REQUEST_ID_PATTERN.test(requestId)) return reply({ ok: false, error: "invalid_request_id" }, 400);

  try {
    const { data: gate, error: gateError } = await db.rpc("h_runtime_evaluate_cloud_failover_gate");
    if (gateError) throw gateError;
    if (gate?.decision !== "failover_eligible" || gate?.backupStandbyReady !== true) {
      return reply({
        ok: true,
        promoted: false,
        blocked: true,
        reason: String(gate?.reason || "failover_gate_not_eligible").slice(0, 120),
        decision: String(gate?.decision || "unknown").slice(0, 40),
      });
    }

    const target = await loadTarget(db);
    if (!target) throw new Error("standby_target_not_ready");
    const [standbyServiceRole, standbyRuntimeSecret] = await Promise.all([
      decryptCloudCredential("supabase", target.serviceCiphertext, target.serviceIv, primaryServiceRole),
      decryptCloudCredential("supabase_runtime", target.runtimeCiphertext, target.runtimeIv, primaryServiceRole),
    ]);

    const prepared = await standbyRpc(target.endpoint, standbyServiceRole, "h_prepare_standby_promotion_v1", {});
    if (prepared?.ok !== true || prepared?.ready !== true) throw new Error("standby_preflight_not_ready");

    const before = await probeHealth(target.endpoint, standbyRuntimeSecret);
    if (
      before?.ok !== true || before?.preflightReady !== true || before?.activeReady === true ||
      before?.promoted === true || before?.replicaWritesEnabled !== true ||
      before?.schedulerActive === true || before?.autonomousOutboundActive === true
    ) {
      throw new Error("standby_pre_promotion_health_mismatch");
    }

    const promoted = await standbyRpc(target.endpoint, standbyServiceRole, "h_promote_standby_request_only_v1", {
      p_request_id: requestId,
    });
    if (promoted?.ok !== true || promoted?.promoted !== true || promoted?.active !== true) {
      throw new Error("standby_promotion_rpc_mismatch");
    }

    const after = await probeHealth(target.endpoint, standbyRuntimeSecret);
    if (
      after?.ok !== true || after?.activeReady !== true || after?.promoted !== true ||
      after?.promotionAttested !== true || after?.promotionRequestId !== requestId ||
      after?.replicaWritesEnabled === true || after?.schedulerActive === true ||
      after?.autonomousOutboundActive === true
    ) {
      throw new Error("standby_post_promotion_health_mismatch");
    }

    const now = new Date().toISOString();
    const metadata = {
      ...target.metadata,
      standby_runtime_ready: true,
      runtime_health_ok: true,
      auto_failover_eligible: false,
      standby_promoted_request_only: true,
      standby_promotion_request_id: requestId,
      standby_promotion_protocol: "h_standby_promotion_v1",
      standby_promotion_verified_at: now,
      standby_replica_writes_fenced: true,
      standby_scheduler_active: false,
      standby_autonomous_outbound_active: false,
      automatic_traffic_switch_performed: false,
    };
    const { error: updateError } = await db.from("h_runtime_cloud_registry")
      .update({ metadata, updated_at: now })
      .eq("id", BACKUP_CLOUD_ID)
      .eq("cloud_role", "backup");
    if (updateError) throw updateError;

    await recordControllerState(db, {
      status: "promoted_request_only",
      request_id: requestId,
      promoted_at: now,
      automatic_traffic_switch_performed: false,
      scheduler_active: false,
      autonomous_outbound_active: false,
      replica_writes_fenced: true,
    });

    return reply({
      ok: true,
      promoted: true,
      active: true,
      mode: "request_only",
      requestId,
      replicaWritesFenced: true,
      schedulerActive: false,
      autonomousOutboundActive: false,
      automaticTrafficSwitchPerformed: false,
    });
  } catch (error) {
    const code = compactErrorCode(error);
    await recordControllerState(db, {
      status: "promotion_rejected",
      error: code,
      request_id: requestId,
      automatic_traffic_switch_performed: false,
    }).catch(() => undefined);
    console.error(`${FUNCTION_NAME} failed`, code);
    return reply({ ok: false, error: "standby_failover_rejected" }, 409);
  }
});

async function loadTarget(db: DbClient): Promise<Target | null> {
  const { data: cloud, error } = await db.from("h_runtime_cloud_registry")
    .select("endpoint,credential_id,enabled,ready,last_health_ok,metadata")
    .eq("id", BACKUP_CLOUD_ID)
    .eq("cloud_role", "backup")
    .maybeSingle();
  if (error) throw error;
  const metadata = objectOrEmpty(cloud?.metadata);
  if (!cloud?.enabled || !cloud?.ready || cloud?.last_health_ok !== true) return null;
  if (String(cloud.credential_id || "") !== BACKUP_CREDENTIAL_ID) return null;
  if (
    metadata.storage_backup_ready !== true || metadata.connection_validated !== true ||
    metadata.standby_replication_ready !== true || metadata.standby_runtime_ready !== true ||
    metadata.runtime_health_ok !== true || metadata.auto_failover_eligible !== true
  ) return null;
  const endpoint = normalizeSupabaseEndpoint(String(cloud.endpoint || ""));
  if (!endpoint) return null;

  const [{ data: service, error: serviceError }, { data: runtime, error: runtimeError }] = await Promise.all([
    db.from("h_runtime_cloud_credentials").select("provider,secret_ciphertext,secret_iv").eq("id", BACKUP_CREDENTIAL_ID).maybeSingle(),
    db.from("h_runtime_cloud_credentials").select("provider,secret_ciphertext,secret_iv").eq("id", RUNTIME_SECRET_CREDENTIAL_ID).maybeSingle(),
  ]);
  if (serviceError) throw serviceError;
  if (runtimeError) throw runtimeError;
  if (service?.provider !== "supabase" || runtime?.provider !== "supabase_runtime") return null;
  return {
    endpoint,
    serviceCiphertext: String(service.secret_ciphertext || ""),
    serviceIv: String(service.secret_iv || ""),
    runtimeCiphertext: String(runtime.secret_ciphertext || ""),
    runtimeIv: String(runtime.secret_iv || ""),
    metadata,
  };
}

async function standbyRpc(endpoint: string, serviceRole: string, rpc: string, body: Record<string, unknown>) {
  const response = await fetch(`${endpoint}/rest/v1/rpc/${rpc}`, {
    method: "POST",
    headers: {
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
      "Content-Type": "application/json",
      "Cache-Control": "no-store",
    },
    body: JSON.stringify(body),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`standby_rpc_${rpc}_${response.status}`);
  return payload;
}

async function probeHealth(endpoint: string, runtimeSecret: string) {
  const response = await fetch(`${endpoint}/functions/v1/h-standby-health`, {
    method: "POST",
    headers: { "x-h-runtime-secret": runtimeSecret, "Content-Type": "application/json", "Cache-Control": "no-store" },
    body: "{}",
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`standby_health_${response.status}`);
  return payload;
}

async function recordControllerState(db: DbClient, value: Record<string, unknown>) {
  const { error } = await db.from("h_runtime_state").upsert({
    key: "standby_failover_controller",
    value: { controller: FUNCTION_NAME, ...value },
    updated_at: new Date().toISOString(),
  }, { onConflict: "key" });
  if (error) throw error;
}

async function loadRuntimeSecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config").select("secret_value").eq("key", "poll_secret").maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

async function decryptCloudCredential(provider: string, ciphertext: string, ivText: string, rootSecret: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`h-cloud-credential-aes-v1:${provider}:${rootSecret}`));
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["decrypt"]);
  const decrypted = await crypto.subtle.decrypt({ name: "AES-GCM", iv: base64UrlDecode(ivText) }, key, base64UrlDecode(ciphertext));
  const value = new TextDecoder().decode(decrypted).trim();
  if (value.length < 32) throw new Error("standby_credential_invalid");
  return value;
}

function normalizeSupabaseEndpoint(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:" || !url.hostname.endsWith(".supabase.co") || url.username || url.password || url.search || url.hash) return null;
    return `${url.protocol}//${url.host}`;
  } catch { return null; }
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid_cloud_ciphertext");
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}
function constantTimeEqual(left: string, right: string): boolean { if (left.length !== right.length) return false; let diff = 0; for (let i = 0; i < left.length; i += 1) diff |= left.charCodeAt(i) ^ right.charCodeAt(i); return diff === 0; }
function compactErrorCode(error: unknown): string { return (error instanceof Error ? error.message : String(error || "unknown_error")).toLowerCase().replace(/[^a-z0-9_:-]+/g, "_").slice(0, 160) || "standby_failover_failed"; }
function reply(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }); }
