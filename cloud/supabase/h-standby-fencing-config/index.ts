import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { parseVerificationJwk } from "../h-standby-promote/fence-assertion.ts";

const FUNCTION_NAME = "h-standby-fencing-config";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const BACKUP_CREDENTIAL_ID = "h_backup_supabase_storage";
const RUNTIME_SECRET_CREDENTIAL_ID = "h_backup_supabase_runtime_secret";

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
  const issuer = normalizeHttpsIssuer(String(body?.issuer || ""));
  const publicJwkRaw = typeof body?.public_jwk === "string"
    ? body.public_jwk.trim()
    : JSON.stringify(body?.public_jwk ?? {});
  if (!issuer) return reply({ ok: false, error: "invalid_fencing_issuer" }, 400);

  let publicJwk: JsonWebKey;
  try {
    publicJwk = parseVerificationJwk(publicJwkRaw);
  } catch (error) {
    return reply({ ok: false, error: compactErrorCode(error) }, 400);
  }

  try {
    const target = await loadTarget(db);
    if (!target) return reply({ ok: false, error: "standby_target_not_ready" }, 409);
    const primaryProjectRef = projectRefFromEndpoint(primaryUrl);
    const standbyProjectRef = projectRefFromEndpoint(target.endpoint);
    if (!primaryProjectRef || !standbyProjectRef || primaryProjectRef === standbyProjectRef) {
      throw new Error("fencing_project_binding_invalid");
    }

    const [standbyServiceRole, standbyRuntimeSecret] = await Promise.all([
      decryptCloudCredential("supabase", target.serviceCiphertext, target.serviceIv, primaryServiceRole),
      decryptCloudCredential("supabase_runtime", target.runtimeCiphertext, target.runtimeIv, primaryServiceRole),
    ]);

    const before = await probeHealth(target.endpoint, standbyRuntimeSecret);
    if (before?.ok !== true || before?.promoted === true || before?.activeReady === true) {
      throw new Error("standby_fencing_reconfiguration_forbidden_after_promotion");
    }

    const normalizedJwk = JSON.stringify(publicJwk);
    await upsertStandbyConfig(target.endpoint, standbyServiceRole, [
      { key: "fencing_public_jwk", secret_value: normalizedJwk },
      { key: "fencing_issuer", secret_value: issuer },
      { key: "fencing_primary_project_ref", secret_value: primaryProjectRef },
      { key: "fencing_standby_project_ref", secret_value: standbyProjectRef },
    ]);
    await upsertStandbyState(target.endpoint, standbyServiceRole, {
      contract: "h_standby_fencing_v1",
      status: "configured",
      authority_configured: true,
      last_fence_epoch: 0,
      automatic_self_promotion_enabled: false,
    });

    const health = await probeHealth(target.endpoint, standbyRuntimeSecret);
    if (health?.ok !== true || health?.fencingAuthorityReady !== true || health?.promoted === true) {
      throw new Error("standby_fencing_health_mismatch");
    }

    const now = new Date().toISOString();
    const metadata = {
      ...target.metadata,
      standby_fencing_contract: "h_standby_fencing_v1",
      standby_fencing_authority_configured: true,
      standby_fencing_issuer: issuer,
      standby_fencing_primary_project_ref: primaryProjectRef,
      standby_fencing_project_ref: standbyProjectRef,
      standby_fencing_private_key_stored: false,
      standby_automatic_self_promotion_enabled: false,
      standby_fencing_configured_at: now,
      auto_failover_eligible: health?.standbyReady === true,
    };
    const { error: updateError } = await db.from("h_runtime_cloud_registry")
      .update({ metadata, updated_at: now })
      .eq("id", BACKUP_CLOUD_ID)
      .eq("cloud_role", "backup");
    if (updateError) throw updateError;

    return reply({
      ok: true,
      configured: true,
      protocol: "h_standby_fencing_v1",
      issuer,
      primaryProjectRef,
      standbyProjectRef,
      privateKeyStored: false,
      automaticSelfPromotionEnabled: false,
      standbyReady: health?.standbyReady === true,
    });
  } catch (error) {
    const code = compactErrorCode(error);
    console.error(`${FUNCTION_NAME} failed`, code);
    return reply({ ok: false, error: "standby_fencing_config_failed" }, 409);
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
    metadata.standby_runtime_provisioned !== true || metadata.standby_health_service_deployed !== true
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

async function upsertStandbyConfig(
  endpoint: string,
  serviceRole: string,
  rows: Array<{ key: string; secret_value: string }>,
): Promise<void> {
  const now = new Date().toISOString();
  const response = await fetch(`${endpoint}/rest/v1/h_runtime_config?on_conflict=key`, {
    method: "POST",
    headers: {
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=minimal",
      "Cache-Control": "no-store",
    },
    body: JSON.stringify(rows.map((row) => ({ ...row, updated_at: now }))),
  });
  if (!response.ok) throw new Error(`standby_fencing_config_write_${response.status}`);
}

async function upsertStandbyState(endpoint: string, serviceRole: string, value: Record<string, unknown>): Promise<void> {
  const now = new Date().toISOString();
  const response = await fetch(`${endpoint}/rest/v1/h_runtime_state?on_conflict=key`, {
    method: "POST",
    headers: {
      apikey: serviceRole,
      Authorization: `Bearer ${serviceRole}`,
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=minimal",
      "Cache-Control": "no-store",
    },
    body: JSON.stringify([{ key: "standby_fencing", value, updated_at: now }]),
  });
  if (!response.ok) throw new Error(`standby_fencing_state_write_${response.status}`);
}

async function probeHealth(endpoint: string, runtimeSecret: string): Promise<any> {
  const response = await fetch(`${endpoint}/functions/v1/h-standby-health`, {
    method: "POST",
    headers: { "x-h-runtime-secret": runtimeSecret, "Content-Type": "application/json", "Cache-Control": "no-store" },
    body: "{}",
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(`standby_health_${response.status}`);
  return payload;
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

function normalizeHttpsIssuer(raw: string): string | null {
  try {
    const url = new URL(raw.trim());
    if (url.protocol !== "https:" || url.username || url.password || url.hash || url.search) return null;
    const normalized = url.toString().replace(/\/$/, "");
    return normalized.length >= 8 && normalized.length <= 200 ? normalized : null;
  } catch { return null; }
}

function projectRefFromEndpoint(endpoint: string): string | null {
  try {
    const url = new URL(endpoint);
    const match = url.hostname.match(/^([a-z0-9-]{8,64})[.]supabase[.]co$/);
    return match?.[1] || null;
  } catch { return null; }
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
function compactErrorCode(error: unknown): string { return (error instanceof Error ? error.message : String(error || "unknown_error")).toLowerCase().replace(/[^a-z0-9_:-]+/g, "_").slice(0, 120) || "standby_fencing_config_failed"; }
function reply(value: unknown, status = 200) { return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } }); }
