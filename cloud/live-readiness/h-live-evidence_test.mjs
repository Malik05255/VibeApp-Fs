import test from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import {
  decryptAndVerifyBackupEnvelope,
  normalizeSupabaseEndpoint,
  runBackupProbe,
  runStandbyAttestation,
  runWhatsAppVoiceEvidence,
} from "./h-live-evidence.mjs";

const json = (value, status = 200) => new Response(JSON.stringify(value), {
  status,
  headers: { "Content-Type": "application/json" },
});

function base64Url(bytes) {
  return Buffer.from(bytes).toString("base64url");
}

async function encryptedEnvelope(endpoint, backupKey, portable) {
  const bytes = new TextEncoder().encode(JSON.stringify(portable));
  const checksum = Buffer.from(await crypto.webcrypto.subtle.digest("SHA-256", bytes)).toString("hex");
  const material = new TextEncoder().encode(`h-portable-backup-aes-v1:${endpoint}:${backupKey}`);
  const keyBytes = await crypto.webcrypto.subtle.digest("SHA-256", material);
  const key = await crypto.webcrypto.subtle.importKey("raw", keyBytes, { name: "AES-GCM" }, false, ["encrypt"]);
  const iv = new Uint8Array(12).fill(7);
  const ciphertext = await crypto.webcrypto.subtle.encrypt({ name: "AES-GCM", iv }, key, bytes);
  return {
    checksum,
    envelope: {
      format: "h-encrypted-portable-backup",
      version: 1,
      algorithm: "AES-256-GCM",
      createdAt: "2026-09-12T14:00:00Z",
      snapshotSchemaVersion: portable.schemaVersion,
      portableProtocol: "paged_core_v3",
      plaintextSha256: checksum,
      iv: base64Url(iv),
      ciphertext: base64Url(new Uint8Array(ciphertext)),
      restoreKeySource: "owner_backup_cloud_credential",
    },
  };
}

function nonceHashFromRequest(options) {
  const payload = JSON.parse(String(options?.body || "{}"));
  return crypto.createHash("sha256").update(String(payload.execution_probe_nonce || "")).digest("hex");
}

test("Supabase endpoint validation is strict", () => {
  assert.equal(normalizeSupabaseEndpoint("https://abc.supabase.co/"), "https://abc.supabase.co");
  assert.equal(normalizeSupabaseEndpoint("http://abc.supabase.co"), null);
  assert.equal(normalizeSupabaseEndpoint("https://example.com"), null);
});

test("backup evidence runs H backup, validates registry, decrypts object and verifies checksum", async () => {
  const backupEndpoint = "https://backup.supabase.co";
  const primaryEndpoint = "https://primary.supabase.co";
  const backupKey = "b".repeat(64);
  const primaryKey = "p".repeat(64);
  const objectPath = "snapshots/2026-09-12/live-proof.json";
  const portable = { format: "h-portable-bundle", schemaVersion: 3, manifest: {}, pages: [] };
  const encrypted = await encryptedEnvelope(backupEndpoint, backupKey, portable);
  const env = {
    H_BACKUP_SUPABASE_URL: backupEndpoint,
    H_PRIMARY_SUPABASE_URL: primaryEndpoint,
    H_BACKUP_SUPABASE_SERVICE_ROLE_KEY: backupKey,
    H_PRIMARY_SUPABASE_SERVICE_ROLE_KEY: primaryKey,
    H_BACKUP_RUNNER_URL: `${primaryEndpoint}/functions/v1/h-backup-runner`,
    H_RUNTIME_SECRET: "runtime-secret",
  };
  let probeBody = "";

  const fetchImpl = async (url, options = {}) => {
    const text = String(url);
    if (text === `${backupEndpoint}/storage/v1/bucket`) return json([{ id: "h-backups" }]);
    if (text.includes("/_h_live_evidence/") && options.method === "POST") {
      probeBody = String(options.body || "");
      return json({});
    }
    if (text.includes("/_h_live_evidence/") && (options.method || "GET") === "GET") {
      return new Response(probeBody, { status: 200 });
    }
    if (text.includes("/_h_live_evidence/") && options.method === "DELETE") return json({});
    if (text.endsWith("/functions/v1/h-backup-runner")) {
      assert.equal(options.headers?.["x-h-runtime-secret"], "runtime-secret");
      return json({ ok: true, skipped: false, encrypted: true, checksumPresent: true, rawMediaIncluded: false });
    }
    if (text.includes("/rest/v1/h_runtime_cloud_backup_runs")) {
      assert.equal(options.headers?.apikey, primaryKey);
      return json([{
        status: "succeeded",
        snapshot_version: 3,
        checksum_sha256: encrypted.checksum,
        item_counts: {},
        byte_estimate: 1024,
        finished_at: "2026-09-12T14:00:01Z",
        created_at: "2026-09-12T14:00:00Z",
        target_cloud_id: "h_backup_supabase_storage",
        metadata: { object_path: objectPath, format: "h-encrypted-portable-backup" },
      }]);
    }
    if (text.includes("/rest/v1/h_runtime_cloud_registry")) {
      return json([{
        enabled: true,
        ready: true,
        last_health_ok: true,
        last_error_code: null,
        endpoint: backupEndpoint,
        metadata: { storage_backup_ready: true, last_backup_object: objectPath },
      }]);
    }
    if (text === `${backupEndpoint}/storage/v1/object/h-backups/${objectPath}`) return json(encrypted.envelope);
    throw new Error(`unexpected request ${text}`);
  };

  const result = await runBackupProbe({ env, fetchImpl, now: () => new Date("2026-09-12T14:00:00Z") });
  assert.equal(result.writeVerified, true);
  assert.equal(result.readVerified, true);
  assert.equal(result.deleteVerified, true);
  assert.equal(result.encryptedBackupRunnerVerified, true);
  assert.equal(result.encryptedEnvelopeVerified, true);
  assert.equal(result.plaintextChecksumVerified, true);
  assert.equal(result.registryHealthy, true);
  assert.equal(result.snapshotSchemaVersion, 3);
  assert.equal(result.rawSecretExposed, false);
});

test("backup decrypt verification rejects a checksum mismatch", async () => {
  const endpoint = "https://backup.supabase.co";
  const key = "b".repeat(64);
  const encrypted = await encryptedEnvelope(endpoint, key, { schemaVersion: 2, counts: {} });
  await assert.rejects(
    () => decryptAndVerifyBackupEnvelope(encrypted.envelope, endpoint, key, "0".repeat(64)),
    /registry_checksum_mismatch/,
  );
});

test("backup probe rejects the primary project", async () => {
  const env = {
    H_BACKUP_SUPABASE_URL: "https://same.supabase.co",
    H_PRIMARY_SUPABASE_URL: "https://same.supabase.co",
    H_BACKUP_SUPABASE_SERVICE_ROLE_KEY: "b".repeat(64),
    H_PRIMARY_SUPABASE_SERVICE_ROLE_KEY: "p".repeat(64),
    H_BACKUP_RUNNER_URL: "https://same.supabase.co/functions/v1/h-backup-runner",
    H_RUNTIME_SECRET: "runtime-secret",
  };
  await assert.rejects(() => runBackupProbe({ env, fetchImpl: async () => json({}) }), /different_from_primary/);
});

const standby = {
  ok: true,
  service: "h-standby-health",
  hIdentity: "H",
  runtimeRole: "standby",
  replicationProtocol: "exact_mirror_v2",
  restoreVerified: true,
  replicationFresh: true,
  replicationLagSeconds: 3,
  aiContinuityFresh: true,
  rawProviderCredentialsReplicated: false,
  rawMediaReplicated: false,
};

function standbyWithProbe(options, overrides = {}) {
  return {
    ...standby,
    executionProbe: {
      requested: true,
      nonceSha256: nonceHashFromRequest(options),
      coreSchemaReadable: true,
      tablesChecked: ["h_runtime_state", "h_runtime_app_identities"],
      writesPerformed: false,
      userContentReturned: false,
    },
    ...overrides,
  };
}

test("standby preflight requires exact mirror plus fresh execution nonce proof", async () => {
  const env = { H_STANDBY_HEALTH_URL: "https://standby.example/health", H_RUNTIME_SECRET: "secret" };
  const fetchImpl = async (_url, options) => json(standbyWithProbe(options, {
    preflightReady: true,
    standbyReady: true,
    passivePreflightOnly: true,
    activeReady: false,
    promotionAttested: false,
  }));
  const result = await runStandbyAttestation({ mode: "preflight", env, fetchImpl });
  assert.equal(result.replicationProtocol, "exact_mirror_v2");
  assert.equal(result.restoreVerified, true);
  assert.equal(result.executionNonceVerified, true);
  assert.equal(result.executionWritesPerformed, false);
  assert.equal(result.userContentReturnedByProbe, false);
});

test("active failover proves request-only promotion, primary outage and standby execution", async () => {
  const env = {
    H_STANDBY_HEALTH_URL: "https://standby.example/health",
    H_RUNTIME_SECRET: "secret",
    H_EXPECT_PRIMARY_UNREACHABLE: "true",
    H_PRIMARY_HEALTH_URL: "https://primary.example/health",
  };
  const fetchImpl = async (url, options = {}) => {
    if (String(url).includes("standby")) return json(standbyWithProbe(options, {
      activeReady: true,
      requestOnlyActive: true,
      promotionAttested: true,
      promotionMode: "request_only",
      replicaWritesEnabled: false,
    }));
    throw new Error("primary unavailable");
  };
  const result = await runStandbyAttestation({ mode: "active", env, fetchImpl });
  assert.equal(result.promotionAttested, true);
  assert.equal(result.requestOnlyActive, true);
  assert.equal(result.executionNonceVerified, true);
  assert.equal(result.noAutomaticFailbackEvidence, true);
});

test("standby rejects a stale or forged nonce proof", async () => {
  const env = { H_STANDBY_HEALTH_URL: "https://standby.example/health", H_RUNTIME_SECRET: "secret" };
  const fetchImpl = async (_url, options) => json(standbyWithProbe(options, {
    preflightReady: true,
    standbyReady: true,
    passivePreflightOnly: true,
    executionProbe: {
      requested: true,
      nonceSha256: "0".repeat(64),
      coreSchemaReadable: true,
      writesPerformed: false,
      userContentReturned: false,
    },
  }));
  await assert.rejects(() => runStandbyAttestation({ mode: "preflight", env, fetchImpl }), /nonce_probe_failed/);
});

test("voice evidence verifies Meta media, STT and an isolated H bridge", async () => {
  const env = {
    WHATSAPP_ACCESS_TOKEN: "meta-token",
    META_GRAPH_VERSION: "v24.0",
    H_LIVE_VOICE_MEDIA_ID: "media-1",
    H_LIVE_VOICE_EXPECTED_PHRASE: "اختبار صوت h فقط",
    TRANSCRIPTION_API_KEY: "stt-token",
    H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
    H_RUNTIME_SECRET: "runtime-secret",
  };
  const fetchImpl = async (url, options = {}) => {
    const text = String(url);
    if (text.startsWith("https://graph.facebook.com/")) return json({ url: "https://cdn.example/audio", mime_type: "audio/ogg" });
    if (text === "https://cdn.example/audio") return new Response(new Blob(["voice"], { type: "audio/ogg" }), { status: 200 });
    if (text.includes("audio/transcriptions")) return json({ text: "هذا اختبار صوت H فقط" });
    if (text.includes("h-whatsapp-inbox")) {
      const payload = JSON.parse(String(options.body || "{}"));
      assert.equal(payload.wa_id, "990000000001");
      assert.equal(payload.sender_role, "friend");
      assert.equal(payload.can_send_external, false);
      return json({ ok: true, status: "processed", duplicate: false, reply: "تم" });
    }
    throw new Error(`unexpected request ${text}`);
  };
  const result = await runWhatsAppVoiceEvidence({ env, fetchImpl });
  assert.equal(result.metaDownloadVerified, true);
  assert.equal(result.transcriptionVerified, true);
  assert.equal(result.expectedProbePhraseDetected, true);
  assert.equal(result.isolatedSyntheticWaId, true);
  assert.equal(result.externalMessagingDisabledForProbe, true);
});
