import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { loadIdentitySecret } from "../_shared/h-identity-secret.ts";
import {
  buildPortableV3Manifest,
  buildPortableV3PageEnvelope,
  type PortableV3Section,
} from "../h-app-sync/portable-page.ts";
import {
  createPortableSnapshot,
  isPortableSnapshotLimitError,
} from "../h-app-sync/portable-snapshot.ts";
import { decryptRuntimeUserKey } from "../h-whatsapp-inbox/runtime-user-key.ts";

const FUNCTION_NAME = "h-backup-runner";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const BACKUP_CREDENTIAL_ID = "h_backup_supabase_storage";
const BUCKET_NAME = "h-backups";
const BACKUP_FORMAT = "h-encrypted-portable-backup";

type DbClient = any;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const supabaseUrl = safeEnv("SUPABASE_URL").replace(/\/$/, "");
  const serviceRole = safeEnv("SUPABASE_SERVICE_ROLE_KEY").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);
  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });

  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  const provided = req.headers.get("x-h-runtime-secret")?.trim() || "";
  if (!runtimeSecret || !provided || !constantTimeEquals(runtimeSecret, provided)) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  const startedAt = new Date();
  let runId: string | null = null;
  try {
    const backup = await loadReadyBackup(db);
    if (!backup) return reply({ ok: true, skipped: true, reason: "backup_not_ready" });

    const { data: run, error: runError } = await db.from("h_runtime_cloud_backup_runs")
      .insert({
        source_cloud_id: "h_primary_supabase",
        target_cloud_id: BACKUP_CLOUD_ID,
        status: "running",
        started_at: startedAt.toISOString(),
        metadata: { worker: FUNCTION_NAME, encrypted: true, raw_media_included: false },
      })
      .select("id")
      .single();
    if (runError) throw runError;
    runId = String(run.id);

    const backupKey = await decryptCloudCredential(
      "supabase",
      backup.secret_ciphertext,
      backup.secret_iv,
      serviceRole,
    );
    // Durable app identity encryption is keyed by identity_secret. poll_secret remains
    // only the runtime request authenticator and must not be reused for identity decryption.
    const identitySecret = await loadIdentitySecret(db);
    const userKey = await loadOwnerRuntimeUserKey(db, identitySecret);
    const portable = await createBackupPortablePayload(db, userKey);
    const plaintextBytes = new TextEncoder().encode(JSON.stringify(portable.payload));
    const checksum = await sha256Hex(plaintextBytes);
    const encrypted = await encryptBackupPayload(plaintextBytes, backupKey, backup.endpoint);
    const envelope = {
      format: BACKUP_FORMAT,
      version: 1,
      algorithm: "AES-256-GCM",
      createdAt: startedAt.toISOString(),
      snapshotSchemaVersion: portable.schemaVersion,
      portableProtocol: portable.schemaVersion === 3 ? "paged_core_v3" : "snapshot_v2",
      plaintextSha256: checksum,
      iv: encrypted.iv,
      ciphertext: encrypted.ciphertext,
      restoreKeySource: "owner_backup_cloud_credential",
    };
    const objectBytes = new TextEncoder().encode(JSON.stringify(envelope));
    const objectPath = backupObjectPath(startedAt);

    await uploadBackupObject(backup.endpoint, backupKey, objectPath, objectBytes);

    const finishedAt = new Date().toISOString();
    const { error: finishError } = await db.from("h_runtime_cloud_backup_runs")
      .update({
        status: "succeeded",
        snapshot_version: portable.schemaVersion,
        checksum_sha256: checksum,
        item_counts: portable.counts,
        byte_estimate: objectBytes.byteLength,
        finished_at: finishedAt,
        metadata: {
          worker: FUNCTION_NAME,
          encrypted: true,
          raw_media_included: false,
          object_path: objectPath,
          bucket: BUCKET_NAME,
          format: BACKUP_FORMAT,
          portable_protocol: portable.schemaVersion === 3 ? "paged_core_v3" : "snapshot_v2",
        },
      })
      .eq("id", runId);
    if (finishError) throw finishError;

    const { error: cloudUpdateError } = await db.from("h_runtime_cloud_registry").update({
      last_health_at: finishedAt,
      last_health_ok: true,
      last_error_code: null,
      metadata: {
        ...(backup.metadata || {}),
        storage_backup_ready: true,
        last_backup_at: finishedAt,
        last_backup_object: objectPath,
        auto_failover_eligible: false,
      },
      updated_at: finishedAt,
    }).eq("id", BACKUP_CLOUD_ID);
    if (cloudUpdateError) throw cloudUpdateError;

    return reply({
      ok: true,
      skipped: false,
      snapshotSchemaVersion: portable.schemaVersion,
      counts: portable.counts,
      checksumPresent: true,
      encrypted: true,
      rawMediaIncluded: false,
    });
  } catch (error) {
    const code = compactErrorCode(error);
    if (runId) {
      await db.from("h_runtime_cloud_backup_runs").update({
        status: "failed",
        error_code: code,
        finished_at: new Date().toISOString(),
      }).eq("id", runId);
    }
    await db.from("h_runtime_cloud_registry").update({
      last_health_at: new Date().toISOString(),
      last_health_ok: false,
      last_error_code: code,
      updated_at: new Date().toISOString(),
    }).eq("id", BACKUP_CLOUD_ID);
    console.error(`${FUNCTION_NAME} failed`, code);
    return reply({ ok: false, error: "backup_failed" }, 500);
  }
});

async function createBackupPortablePayload(db: DbClient, userKey: string) {
  try {
    const snapshot = await createPortableSnapshot(db, userKey);
    return {
      payload: snapshot,
      schemaVersion: Number(snapshot.schemaVersion || 2),
      counts: snapshot.counts ?? {},
    };
  } catch (error) {
    if (!isPortableSnapshotLimitError(error)) throw error;
  }

  const { data: session, error: prepareError } = await db.rpc("h_prepare_portable_export_v3", {
    p_user_key: userKey,
    p_page_size: 500,
  });
  if (prepareError) throw prepareError;
  const sessionId = String(session?.sessionId || "");
  const createdAt = String(session?.createdAt || "");
  const expiresAt = String(session?.expiresAt || "");
  const counts = session?.counts ?? {};
  if (!sessionId || !createdAt || !expiresAt) throw new Error("portable_v3_backup_session_invalid");

  try {
    const { data: storedPages, error: pageError } = await db.from("h_runtime_portable_export_pages")
      .select("section,page_index,items")
      .eq("session_id", sessionId)
      .order("section", { ascending: true })
      .order("page_index", { ascending: true });
    if (pageError) throw pageError;

    const pages = [];
    for (const stored of storedPages ?? []) {
      pages.push(await buildPortableV3PageEnvelope({
        sessionId,
        section: String(stored.section) as PortableV3Section,
        pageIndex: Number(stored.page_index),
        items: Array.isArray(stored.items) ? stored.items : [],
        counts,
        expiresAt,
      }));
    }
    const manifest = await buildPortableV3Manifest({
      sessionId,
      pages,
      counts,
      createdAt,
      expiresAt,
      restoreSupported: true,
    });
    return {
      payload: {
        format: "h-portable-bundle",
        schemaVersion: 3,
        manifest,
        pages,
      },
      schemaVersion: 3,
      counts,
    };
  } finally {
    // Backup already owns the complete in-memory encrypted payload; remove transient staging
    // immediately instead of retaining it until TTL cleanup.
    await db.from("h_runtime_portable_export_sessions")
      .delete()
      .eq("id", sessionId)
      .eq("user_key", userKey);
  }
}

async function loadReadyBackup(db: DbClient) {
  const { data: cloud, error } = await db.from("h_runtime_cloud_registry")
    .select("endpoint,credential_id,enabled,ready,last_health_ok,metadata")
    .eq("id", BACKUP_CLOUD_ID)
    .eq("cloud_role", "backup")
    .maybeSingle();
  if (error) throw error;
  if (!cloud?.enabled || !cloud?.ready || cloud?.last_health_ok !== true) return null;
  if (String(cloud.credential_id || "") !== BACKUP_CREDENTIAL_ID) return null;
  if (cloud?.metadata?.storage_backup_ready !== true) return null;

  const { data: credential, error: credentialError } = await db.from("h_runtime_cloud_credentials")
    .select("secret_ciphertext,secret_iv,provider")
    .eq("id", BACKUP_CREDENTIAL_ID)
    .maybeSingle();
  if (credentialError) throw credentialError;
  if (!credential || String(credential.provider || "") !== "supabase") return null;
  return {
    endpoint: String(cloud.endpoint || "").replace(/\/$/, ""),
    metadata: cloud.metadata && typeof cloud.metadata === "object" ? cloud.metadata : {},
    secret_ciphertext: String(credential.secret_ciphertext || ""),
    secret_iv: String(credential.secret_iv || ""),
  };
}

async function loadOwnerRuntimeUserKey(db: DbClient, identitySecret: string): Promise<string> {
  const { data, error } = await db.from("h_runtime_app_identities")
    .select("runtime_user_key_ciphertext,linked_at")
    .eq("active", true)
    .order("linked_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (error) throw error;
  if (!data?.runtime_user_key_ciphertext) throw new Error("owner_app_identity_missing");
  return decryptRuntimeUserKey(String(data.runtime_user_key_ciphertext), identitySecret);
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

async function decryptCloudCredential(
  provider: string,
  ciphertext: string,
  ivText: string,
  rootSecret: string,
): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`h-cloud-credential-aes-v1:${provider}:${rootSecret}`),
  );
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["decrypt"]);
  const decrypted = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: base64UrlDecode(ivText) },
    key,
    base64UrlDecode(ciphertext),
  );
  const value = new TextDecoder().decode(decrypted).trim();
  if (value.length < 40) throw new Error("backup_credential_invalid");
  return value;
}

async function encryptBackupPayload(payload: Uint8Array, backupKey: string, endpoint: string) {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(`h-portable-backup-aes-v1:${endpoint}:${backupKey}`),
  );
  const key = await crypto.subtle.importKey("raw", digest, { name: "AES-GCM" }, false, ["encrypt"]);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, toArrayBuffer(payload));
  return { iv: base64Url(iv), ciphertext: base64Url(new Uint8Array(encrypted)) };
}

async function uploadBackupObject(
  endpoint: string,
  backupKey: string,
  objectPath: string,
  bytes: Uint8Array,
): Promise<void> {
  const response = await fetch(`${endpoint}/storage/v1/object/${BUCKET_NAME}/${objectPath}`, {
    method: "POST",
    headers: {
      apikey: backupKey,
      Authorization: `Bearer ${backupKey}`,
      "Content-Type": "application/json",
      "x-upsert": "false",
      "Cache-Control": "no-store",
    },
    body: toArrayBuffer(bytes),
  });
  if (!response.ok) throw new Error(`backup_upload_${response.status}`);
}

function backupObjectPath(date: Date): string {
  const day = date.toISOString().slice(0, 10);
  const stamp = date.toISOString().replace(/[:.]/g, "-");
  return `snapshots/${day}/${stamp}-${crypto.randomUUID()}.json`;
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", toArrayBuffer(bytes));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  const copy = new Uint8Array(bytes.byteLength);
  copy.set(bytes);
  return copy.buffer;
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => binary += String.fromCharCode(byte));
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid_backup_ciphertext");
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function constantTimeEquals(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let mismatch = 0;
  for (let i = 0; i < a.length; i += 1) mismatch |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return mismatch === 0;
}

function compactErrorCode(error: unknown): string {
  const raw = (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 120) || "backup_failed";
}

function safeEnv(name: string): string {
  return String(Deno.env.get(name) || "");
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}
