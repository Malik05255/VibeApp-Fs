import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { verifyGoogleIdToken } from "../h-app-sync/google-id-token.ts";

const FUNCTION_NAME = "h-cloud-manager";
const BACKUP_CONFIG_FUNCTION = "h-cloud-backup-config";
const GOOGLE_SUB_LABEL = "h-app-google-subject-v1";
const PRIMARY_CLOUD_ID = "h_primary_supabase";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const BACKUP_CREDENTIAL_ID = "h_backup_supabase_storage";
const SETUP_TTL_MS = 10 * 60 * 1000;
const WARNING_RATIO = 0.85;
const CRITICAL_RATIO = 0.95;

type DbClient = any;

type CloudRow = {
  id: string;
  provider: string;
  cloud_role: "primary" | "backup";
  endpoint: string;
  credential_id: string | null;
  enabled: boolean;
  ready: boolean;
  priority: number;
  quota_bytes: number | null;
  used_bytes: number | null;
  quota_requests: number | null;
  used_requests: number | null;
  quota_resets_at: string | null;
  last_health_at: string | null;
  last_health_ok: boolean | null;
  last_error_code: string | null;
  metadata: Record<string, unknown> | null;
  updated_at: string;
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return json({ ok: false, error: "method_not_allowed" }, 405);

  const bearer = bearerToken(req.headers.get("authorization"));
  if (!bearer) return json({ ok: false, error: "google_sign_in_required" }, 401);

  let google;
  try {
    google = await verifyGoogleIdToken(bearer);
  } catch (error) {
    return json({ ok: false, error: errorMessage(error) }, 401);
  }

  const supabaseUrl = safeEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceRole = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!supabaseUrl || !serviceRole) return json({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  try {
    const runtimeSecret = await loadRuntimeSecret(db);
    const subjectFingerprint = await secretFingerprint(runtimeSecret, GOOGLE_SUB_LABEL, google.subject);
    if (!await isLinkedOwner(db, subjectFingerprint, google.audience)) {
      return json({ ok: false, error: "app_not_linked", linked: false }, 403);
    }

    const body = await req.json().catch(() => ({}));
    const action = String(body?.action || "status").trim().toLowerCase();

    if (action === "status") {
      return json({ ...(await cloudStatus(db)), linked: true });
    }

    if (action === "refresh_primary_health") {
      await refreshPrimaryHealth(db);
      return json({ ...(await cloudStatus(db)), linked: true, refreshed: true });
    }

    if (action === "create_backup_setup_link") {
      await invalidatePendingCloudSetup(db);
      const rawToken = randomUrlSafe(32);
      const tokenHash = await setupTokenHash(rawToken);
      const expiresAt = new Date(Date.now() + SETUP_TTL_MS).toISOString();
      const { error } = await db.from("h_runtime_cloud_setup").insert({
        token_hash: tokenHash,
        provider: "supabase",
        expires_at: expiresAt,
        metadata: { requested_by: "android_owner", purpose: "storage_backup" },
      });
      if (error) throw error;
      const connectUrl = new URL(`${supabaseUrl}/functions/v1/${BACKUP_CONFIG_FUNCTION}/connect`);
      connectUrl.searchParams.set("setup", rawToken);
      return json({
        ok: true,
        linked: true,
        expiresAt,
        connectUrl: connectUrl.toString(),
        provider: "supabase",
        purpose: "storage_backup",
        credentialInApk: false,
        readyOnlyAfterWriteProbe: true,
      });
    }

    if (action === "disconnect_backup") {
      const now = new Date().toISOString();
      await invalidatePendingCloudSetup(db);
      const { error: registryError } = await db.from("h_runtime_cloud_registry")
        .update({
          enabled: false,
          ready: false,
          credential_id: null,
          last_health_ok: false,
          last_error_code: "owner_disconnected",
          updated_at: now,
        })
        .eq("id", BACKUP_CLOUD_ID)
        .eq("cloud_role", "backup");
      if (registryError) throw registryError;
      const { error: credentialError } = await db.from("h_runtime_cloud_credentials")
        .delete()
        .eq("id", BACKUP_CREDENTIAL_ID);
      if (credentialError) throw credentialError;
      return json({ ...(await cloudStatus(db)), linked: true, backupDisconnected: true });
    }

    return json({ ok: false, error: "unsupported_action" }, 400);
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, errorMessage(error));
    return json({ ok: false, error: "cloud_manager_failed" }, 500);
  }
});

async function cloudStatus(db: DbClient) {
  const { data: clouds, error } = await db.from("h_runtime_cloud_registry")
    .select("id,provider,cloud_role,endpoint,credential_id,enabled,ready,priority,quota_bytes,used_bytes,quota_requests,used_requests,quota_resets_at,last_health_at,last_health_ok,last_error_code,metadata,updated_at")
    .order("cloud_role", { ascending: true })
    .order("priority", { ascending: true });
  if (error) throw error;

  const rows = (Array.isArray(clouds) ? clouds : []) as CloudRow[];
  const primary = rows.find((row) => row.cloud_role === "primary" && row.enabled) ?? null;
  const backup = rows.find((row) => row.cloud_role === "backup" && row.enabled) ?? null;

  const { data: lastBackup, error: backupError } = await db.from("h_runtime_cloud_backup_runs")
    .select("id,status,snapshot_version,checksum_sha256,item_counts,byte_estimate,error_code,started_at,finished_at,created_at,target_cloud_id")
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (backupError) throw backupError;

  const primaryView = primary ? safeCloudView(primary) : null;
  const backupView = backup ? safeCloudView(backup) : null;
  const backupReady = Boolean(backup?.ready && backup?.last_health_ok === true && backup?.credential_id);
  const backupAutoFailoverEligible = backupReady && backup?.metadata?.auto_failover_eligible === true;
  const primaryHealthy = Boolean(primary?.ready && primary?.last_health_ok === true);
  const capacityState = combinedCapacityState(primary, backupReady ? backup : null);

  return {
    ok: true,
    multiCloud: true,
    primary: primaryView,
    backup: backupView,
    primaryHealthy,
    backupConfigured: Boolean(backup),
    backupReady,
    backupAutoFailoverEligible,
    automaticFailoverReady: primaryHealthy && backupAutoFailoverEligible,
    capacityState,
    lastBackup: lastBackup ? {
      status: String(lastBackup.status || "unknown"),
      snapshotVersion: numberOrNull(lastBackup.snapshot_version),
      checksumPresent: Boolean(lastBackup.checksum_sha256),
      itemCounts: objectOrEmpty(lastBackup.item_counts),
      byteEstimate: numberOrNull(lastBackup.byte_estimate),
      errorCode: stringOrNull(lastBackup.error_code),
      startedAt: stringOrNull(lastBackup.started_at),
      finishedAt: stringOrNull(lastBackup.finished_at),
      createdAt: stringOrNull(lastBackup.created_at),
      targetConfigured: Boolean(lastBackup.target_cloud_id),
    } : null,
    backupDue: backupReady && !recentSuccessfulBackup(lastBackup, 24 * 60 * 60 * 1000),
    credentialsExposed: false,
  };
}

async function refreshPrimaryHealth(db: DbClient): Promise<void> {
  const now = new Date().toISOString();
  try {
    const { error: probeError } = await db.from("h_runtime_cloud_registry")
      .select("id")
      .eq("id", PRIMARY_CLOUD_ID)
      .limit(1);
    if (probeError) throw probeError;

    const { error } = await db.from("h_runtime_cloud_registry")
      .update({
        ready: true,
        last_health_at: now,
        last_health_ok: true,
        last_error_code: null,
        updated_at: now,
      })
      .eq("id", PRIMARY_CLOUD_ID)
      .eq("cloud_role", "primary")
      .eq("enabled", true);
    if (error) throw error;
  } catch (error) {
    await db.from("h_runtime_cloud_registry").update({
      last_health_at: now,
      last_health_ok: false,
      last_error_code: compactErrorCode(error),
      updated_at: now,
    }).eq("id", PRIMARY_CLOUD_ID).catch(() => undefined);
    throw error;
  }
}

async function invalidatePendingCloudSetup(db: DbClient): Promise<void> {
  const { error } = await db.from("h_runtime_cloud_setup").delete().is("used_at", null);
  if (error) throw error;
}

function safeCloudView(row: CloudRow) {
  const bytes = capacityMetric(row.used_bytes, row.quota_bytes);
  const requests = capacityMetric(row.used_requests, row.quota_requests);
  return {
    role: row.cloud_role,
    enabled: row.enabled === true,
    ready: row.ready === true,
    healthy: row.last_health_ok === true,
    priority: Number(row.priority || 0),
    hasCredential: Boolean(row.credential_id) || row.id === PRIMARY_CLOUD_ID,
    storageBackupReady: row.metadata?.storage_backup_ready === true,
    autoFailoverEligible: row.metadata?.auto_failover_eligible === true,
    capacity: {
      bytes,
      requests,
      state: worstCapacityState(bytes.state, requests.state),
      resetsAt: stringOrNull(row.quota_resets_at),
    },
    lastHealthAt: stringOrNull(row.last_health_at),
    lastErrorCode: stringOrNull(row.last_error_code),
    updatedAt: stringOrNull(row.updated_at),
  };
}

function capacityMetric(usedRaw: unknown, quotaRaw: unknown) {
  const used = finiteNonNegative(usedRaw);
  const quota = finitePositive(quotaRaw);
  if (used == null || quota == null) {
    return { measured: false, used: null, quota: null, ratio: null, state: "unknown" };
  }
  const ratio = Math.max(0, used / quota);
  return {
    measured: true,
    used,
    quota,
    ratio: Math.min(ratio, 999),
    state: ratio >= CRITICAL_RATIO ? "critical" : ratio >= WARNING_RATIO ? "warning" : "ok",
  };
}

function combinedCapacityState(primary: CloudRow | null, backup: CloudRow | null) {
  if (!primary) return "critical";
  const primaryState = safeCloudView(primary).capacity.state;
  if (primaryState !== "critical") return primaryState;
  if (!backup) return "last_cloud_capacity_at_risk";
  const backupState = safeCloudView(backup).capacity.state;
  return backupState === "critical" ? "last_cloud_capacity_at_risk" : "backup_available";
}

function worstCapacityState(a: string, b: string): string {
  const rank: Record<string, number> = { unknown: 0, ok: 1, warning: 2, critical: 3 };
  return (rank[a] ?? 0) >= (rank[b] ?? 0) ? a : b;
}

function recentSuccessfulBackup(row: any, maxAgeMs: number): boolean {
  if (!row || String(row.status || "") !== "succeeded") return false;
  const time = Date.parse(String(row.finished_at || row.created_at || ""));
  return Number.isFinite(time) && Date.now() - time <= maxAgeMs;
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

async function secretFingerprint(secret: string, label: string, value: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign(
    "HMAC",
    key,
    new TextEncoder().encode(`${label}:${value}`),
  );
  return [...new Uint8Array(signature)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function setupTokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`h-cloud-setup-v1:${token}`),
  );
  return base64Url(new Uint8Array(digest));
}

function randomUrlSafe(byteCount: number): string {
  const bytes = new Uint8Array(byteCount);
  crypto.getRandomValues(bytes);
  return base64Url(bytes);
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function bearerToken(value: string | null): string | null {
  const match = String(value || "").match(/^Bearer\s+(.+)$/i);
  return match?.[1]?.trim() || null;
}

function finiteNonNegative(value: unknown): number | null {
  if (value == null) return null;
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : null;
}

function finitePositive(value: unknown): number | null {
  if (value == null) return null;
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

function numberOrNull(value: unknown): number | null {
  const number = Number(value);
  return value != null && Number.isFinite(number) ? number : null;
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function stringOrNull(value: unknown): string | null {
  const text = String(value ?? "").trim();
  return text || null;
}

function compactErrorCode(error: unknown): string {
  const raw = errorMessage(error).toLowerCase().replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 120) || "health_probe_failed";
}

function safeEnv(name: string): string {
  return String(Deno.env.get(name) || "");
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error || "unknown_error");
}

function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
