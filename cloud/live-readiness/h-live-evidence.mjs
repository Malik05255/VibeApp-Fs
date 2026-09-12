import { writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import crypto from "node:crypto";

const BACKUP_BUCKET = "h-backups";
const BACKUP_CLOUD_ID = "h_backup_supabase_storage";
const BACKUP_FORMAT = "h-encrypted-portable-backup";
const MAX_VOICE_BYTES = 8 * 1024 * 1024;
const LIVE_VOICE_SYNTHETIC_WA_ID = "990000000001";
const DEFAULT_LIVE_VOICE_PHRASE = "اختبار صوت h فقط";
const EVIDENCE_OUTPUT = process.env.H_LIVE_EVIDENCE_OUTPUT || "h-live-evidence.json";

export function normalizeSupabaseEndpoint(raw) {
  try {
    const url = new URL(String(raw || "").trim());
    if (url.protocol !== "https:" || !url.hostname.endsWith(".supabase.co")) return null;
    if (url.username || url.password || url.search || url.hash) return null;
    return `${url.protocol}//${url.host}`;
  } catch {
    return null;
  }
}

function requiredEnv(name, env = process.env) {
  const value = String(env[name] || "").trim();
  if (!value) throw new Error(`missing_required_secret:${name}`);
  return value;
}

async function fetchWithTimeout(fetchImpl, url, options = {}, timeoutMs = 15_000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetchImpl(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function jsonResponse(response, label) {
  const text = await response.text();
  let data = {};
  try { data = text ? JSON.parse(text) : {}; } catch {}
  if (!response.ok) {
    throw new Error(`${label}_http_${response.status}:${String(data?.error || text || "unknown").slice(0, 160)}`);
  }
  return data;
}

function base64UrlDecode(value) {
  const text = String(value || "");
  if (!/^[A-Za-z0-9_-]+$/.test(text)) throw new Error("backup_envelope_base64url_invalid");
  const normalized = text.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized + "=".repeat((4 - normalized.length % 4) % 4);
  return Uint8Array.from(Buffer.from(padded, "base64"));
}

async function sha256HexBytes(bytes) {
  const input = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  const digest = await crypto.webcrypto.subtle.digest("SHA-256", input);
  return Buffer.from(digest).toString("hex");
}

export async function decryptAndVerifyBackupEnvelope(envelope, endpoint, backupKey, expectedChecksum) {
  if (!envelope || typeof envelope !== "object") throw new Error("backup_envelope_invalid");
  if (envelope.format !== BACKUP_FORMAT || Number(envelope.version) !== 1 || envelope.algorithm !== "AES-256-GCM") {
    throw new Error("backup_envelope_contract_invalid");
  }
  if (!/^[0-9a-f]{64}$/i.test(String(envelope.plaintextSha256 || ""))) {
    throw new Error("backup_envelope_checksum_invalid");
  }
  if (expectedChecksum && String(envelope.plaintextSha256).toLowerCase() !== String(expectedChecksum).toLowerCase()) {
    throw new Error("backup_envelope_registry_checksum_mismatch");
  }

  const material = new TextEncoder().encode(`h-portable-backup-aes-v1:${endpoint}:${backupKey}`);
  const keyBytes = await crypto.webcrypto.subtle.digest("SHA-256", material);
  const key = await crypto.webcrypto.subtle.importKey("raw", keyBytes, { name: "AES-GCM" }, false, ["decrypt"]);
  let plaintext;
  try {
    plaintext = await crypto.webcrypto.subtle.decrypt(
      { name: "AES-GCM", iv: base64UrlDecode(envelope.iv) },
      key,
      base64UrlDecode(envelope.ciphertext),
    );
  } catch {
    throw new Error("backup_envelope_decryption_failed");
  }
  const bytes = new Uint8Array(plaintext);
  const checksum = await sha256HexBytes(bytes);
  if (checksum.toLowerCase() !== String(envelope.plaintextSha256).toLowerCase()) {
    throw new Error("backup_plaintext_checksum_mismatch");
  }

  let portable;
  try { portable = JSON.parse(new TextDecoder().decode(bytes)); } catch { throw new Error("backup_plaintext_json_invalid"); }
  const schemaVersion = Number(portable?.schemaVersion || 0);
  if (![1, 2, 3].includes(schemaVersion)) throw new Error("backup_portable_schema_invalid");
  return { checksum, schemaVersion };
}

function serviceHeaders(serviceRole) {
  return {
    apikey: serviceRole,
    Authorization: `Bearer ${serviceRole}`,
    Accept: "application/json",
  };
}

export async function runBackupProbe({ env = process.env, fetchImpl = fetch, now = () => new Date() } = {}) {
  const endpoint = normalizeSupabaseEndpoint(requiredEnv("H_BACKUP_SUPABASE_URL", env));
  if (!endpoint) throw new Error("backup_endpoint_invalid");
  const primary = normalizeSupabaseEndpoint(requiredEnv("H_PRIMARY_SUPABASE_URL", env));
  if (!primary) throw new Error("primary_endpoint_invalid");
  if (primary === endpoint) throw new Error("backup_must_be_different_from_primary");
  const backupKey = requiredEnv("H_BACKUP_SUPABASE_SERVICE_ROLE_KEY", env);
  const primaryKey = requiredEnv("H_PRIMARY_SUPABASE_SERVICE_ROLE_KEY", env);
  const runnerUrl = requiredEnv("H_BACKUP_RUNNER_URL", env);
  const runtimeSecret = requiredEnv("H_RUNTIME_SECRET", env);
  if (backupKey.length < 40) throw new Error("backup_service_role_key_invalid");
  if (primaryKey.length < 40) throw new Error("primary_service_role_key_invalid");

  const headers = serviceHeaders(backupKey);
  const bucketsResponse = await fetchWithTimeout(fetchImpl, `${endpoint}/storage/v1/bucket`, { headers });
  const buckets = await jsonResponse(bucketsResponse, "backup_bucket_list");
  const exists = Array.isArray(buckets) && buckets.some((item) => String(item?.id || item?.name || "") === BACKUP_BUCKET);
  let bucketCreated = false;
  if (!exists) {
    const createResponse = await fetchWithTimeout(fetchImpl, `${endpoint}/storage/v1/bucket`, {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ id: BACKUP_BUCKET, name: BACKUP_BUCKET, public: false }),
    });
    await jsonResponse(createResponse, "backup_bucket_create");
    bucketCreated = true;
  }

  const probeId = crypto.randomUUID();
  const probePath = `_h_live_evidence/${probeId}.txt`;
  const objectUrl = `${endpoint}/storage/v1/object/${BACKUP_BUCKET}/${probePath}`;
  const probeBody = `H live backup evidence ${probeId} ${now().toISOString()}`;
  const upload = await fetchWithTimeout(fetchImpl, objectUrl, {
    method: "POST",
    headers: { ...headers, "Content-Type": "text/plain; charset=utf-8", "x-upsert": "true" },
    body: probeBody,
  });
  if (!upload.ok) throw new Error(`backup_write_probe_http_${upload.status}`);

  let readVerified = false;
  try {
    const read = await fetchWithTimeout(fetchImpl, objectUrl, { headers });
    if (!read.ok) throw new Error(`backup_read_probe_http_${read.status}`);
    const readBody = await read.text();
    readVerified = readBody === probeBody;
    if (!readVerified) throw new Error("backup_read_probe_content_mismatch");
  } finally {
    const remove = await fetchWithTimeout(fetchImpl, objectUrl, { method: "DELETE", headers });
    if (!remove.ok) throw new Error(`backup_delete_probe_http_${remove.status}`);
  }

  const runnerResponse = await fetchWithTimeout(fetchImpl, runnerUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-h-runtime-secret": runtimeSecret },
    body: "{}",
  }, 90_000);
  const runner = await jsonResponse(runnerResponse, "backup_runner");
  if (runner?.ok !== true || runner?.skipped !== false || runner?.encrypted !== true || runner?.checksumPresent !== true) {
    throw new Error("backup_runner_live_run_not_verified");
  }
  if (runner?.rawMediaIncluded !== false) throw new Error("backup_runner_raw_media_contract_broken");

  const primaryHeaders = serviceHeaders(primaryKey);
  const runQuery = new URL(`${primary}/rest/v1/h_runtime_cloud_backup_runs`);
  runQuery.searchParams.set("select", "status,snapshot_version,checksum_sha256,item_counts,byte_estimate,finished_at,metadata,target_cloud_id,created_at");
  runQuery.searchParams.set("target_cloud_id", `eq.${BACKUP_CLOUD_ID}`);
  runQuery.searchParams.set("status", "eq.succeeded");
  runQuery.searchParams.set("order", "created_at.desc");
  runQuery.searchParams.set("limit", "1");
  const runRows = await jsonResponse(await fetchWithTimeout(fetchImpl, runQuery, { headers: primaryHeaders }), "backup_run_registry");
  const run = Array.isArray(runRows) ? runRows[0] : null;
  if (!run || !/^[0-9a-f]{64}$/i.test(String(run.checksum_sha256 || ""))) throw new Error("backup_run_registry_missing");
  const objectPath = String(run?.metadata?.object_path || "");
  if (!objectPath.startsWith("snapshots/") || run?.metadata?.format !== BACKUP_FORMAT) {
    throw new Error("backup_run_object_metadata_invalid");
  }

  const registryQuery = new URL(`${primary}/rest/v1/h_runtime_cloud_registry`);
  registryQuery.searchParams.set("select", "enabled,ready,last_health_ok,last_error_code,metadata,endpoint");
  registryQuery.searchParams.set("id", `eq.${BACKUP_CLOUD_ID}`);
  registryQuery.searchParams.set("limit", "1");
  const registryRows = await jsonResponse(await fetchWithTimeout(fetchImpl, registryQuery, { headers: primaryHeaders }), "backup_cloud_registry");
  const registry = Array.isArray(registryRows) ? registryRows[0] : null;
  if (!registry || registry.enabled !== true || registry.ready !== true || registry.last_health_ok !== true) {
    throw new Error("backup_registry_not_healthy");
  }
  if (String(registry.endpoint || "").replace(/\/$/, "") !== endpoint) throw new Error("backup_registry_endpoint_mismatch");
  if (registry?.metadata?.storage_backup_ready !== true || registry?.metadata?.last_backup_object !== objectPath) {
    throw new Error("backup_registry_last_object_mismatch");
  }
  if (registry.last_error_code) throw new Error("backup_registry_has_error");

  const backupObjectResponse = await fetchWithTimeout(
    fetchImpl,
    `${endpoint}/storage/v1/object/${BACKUP_BUCKET}/${objectPath}`,
    { headers },
  );
  if (!backupObjectResponse.ok) throw new Error(`backup_object_download_http_${backupObjectResponse.status}`);
  const envelopeText = await backupObjectResponse.text();
  let envelope;
  try { envelope = JSON.parse(envelopeText); } catch { throw new Error("backup_object_envelope_json_invalid"); }
  const decrypted = await decryptAndVerifyBackupEnvelope(envelope, endpoint, backupKey, run.checksum_sha256);
  if (Number(run.snapshot_version) !== decrypted.schemaVersion) throw new Error("backup_schema_registry_mismatch");

  return {
    gate: "backup_cloud",
    live: true,
    endpointHost: new URL(endpoint).host,
    bucket: BACKUP_BUCKET,
    bucketCreated,
    writeVerified: true,
    readVerified,
    deleteVerified: true,
    encryptedBackupRunnerVerified: true,
    encryptedObjectDownloaded: true,
    encryptedEnvelopeVerified: true,
    plaintextChecksumVerified: true,
    snapshotSchemaVersion: decrypted.schemaVersion,
    registryHealthy: true,
    registryLastBackupVerified: true,
    rawMediaIncluded: false,
    rawSecretExposed: false,
  };
}

async function sha256HexText(value) {
  return sha256HexBytes(new TextEncoder().encode(String(value)));
}

export async function runStandbyAttestation({ mode = "preflight", env = process.env, fetchImpl = fetch } = {}) {
  if (!new Set(["preflight", "active"]).has(mode)) throw new Error("standby_mode_invalid");
  const standbyHealthUrl = requiredEnv("H_STANDBY_HEALTH_URL", env);
  const runtimeSecret = requiredEnv("H_RUNTIME_SECRET", env);
  const executionNonce = crypto.randomBytes(24).toString("base64url");
  const response = await fetchWithTimeout(fetchImpl, standbyHealthUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-h-runtime-secret": runtimeSecret },
    body: JSON.stringify({ execution_probe_nonce: executionNonce }),
  });
  const health = await jsonResponse(response, "standby_health");
  if (health?.ok !== true || health?.service !== "h-standby-health") throw new Error("standby_health_contract_invalid");
  if (health?.hIdentity !== "H" || health?.runtimeRole !== "standby") throw new Error("standby_identity_invalid");
  if (health?.replicationProtocol !== "exact_mirror_v2" || health?.restoreVerified !== true) {
    throw new Error("standby_exact_mirror_not_verified");
  }
  if (health?.rawProviderCredentialsReplicated !== false || health?.rawMediaReplicated !== false) {
    throw new Error("standby_sensitive_replication_contract_broken");
  }
  const probe = health?.executionProbe;
  const expectedNonceHash = await sha256HexText(executionNonce);
  if (
    probe?.requested !== true ||
    probe?.coreSchemaReadable !== true ||
    probe?.writesPerformed !== false ||
    probe?.userContentReturned !== false ||
    probe?.nonceSha256 !== expectedNonceHash
  ) {
    throw new Error("standby_execution_nonce_probe_failed");
  }

  if (mode === "preflight") {
    if (health?.preflightReady !== true || health?.standbyReady !== true || health?.passivePreflightOnly !== true) {
      throw new Error("standby_preflight_not_ready");
    }
  } else {
    if (health?.activeReady !== true || health?.requestOnlyActive !== true || health?.promotionAttested !== true) {
      throw new Error("standby_active_promotion_not_attested");
    }
    if (health?.promotionMode !== "request_only" || health?.replicaWritesEnabled !== false) {
      throw new Error("standby_active_execution_contract_invalid");
    }
    if (String(env.H_EXPECT_PRIMARY_UNREACHABLE || "").toLowerCase() === "true") {
      const primaryHealthUrl = requiredEnv("H_PRIMARY_HEALTH_URL", env);
      let primaryReachable = false;
      try {
        const primaryResponse = await fetchWithTimeout(fetchImpl, primaryHealthUrl, { method: "GET" }, 6_000);
        primaryReachable = primaryResponse.ok;
      } catch {}
      if (primaryReachable) throw new Error("primary_still_reachable_during_live_failover_evidence");
    }
  }

  return {
    gate: mode === "active" ? "primary_to_standby_live_failover" : "standby_preflight",
    live: true,
    mode,
    replicationProtocol: health.replicationProtocol,
    replicationFresh: health.replicationFresh === true,
    replicationLagSeconds: health.replicationLagSeconds ?? null,
    restoreVerified: health.restoreVerified === true,
    aiContinuityFresh: health.aiContinuityFresh === true,
    promotionAttested: health.promotionAttested === true,
    promotionMode: health.promotionMode || null,
    requestOnlyActive: health.requestOnlyActive === true,
    executionNonceVerified: true,
    standbyCoreSchemaReadable: true,
    executionWritesPerformed: false,
    userContentReturnedByProbe: false,
    noAutomaticFailbackEvidence: mode === "active" && health.requestOnlyActive === true,
    rawProviderCredentialsReplicated: false,
    rawMediaReplicated: false,
  };
}

function extensionForMime(mime) {
  const value = String(mime || "").toLowerCase();
  if (value.includes("ogg")) return "ogg";
  if (value.includes("mpeg") || value.includes("mp3")) return "mp3";
  if (value.includes("mp4") || value.includes("m4a")) return "m4a";
  if (value.includes("wav")) return "wav";
  return "bin";
}

function normalizeProbeText(value) {
  return String(value || "").trim().toLocaleLowerCase("ar").replace(/\s+/g, " ");
}

export async function runWhatsAppVoiceEvidence({ env = process.env, fetchImpl = fetch } = {}) {
  const accessToken = requiredEnv("WHATSAPP_ACCESS_TOKEN", env);
  const graphVersion = requiredEnv("META_GRAPH_VERSION", env).replace(/^v?/i, "v");
  const mediaId = requiredEnv("H_LIVE_VOICE_MEDIA_ID", env);
  const expectedPhrase = normalizeProbeText(String(env.H_LIVE_VOICE_EXPECTED_PHRASE || DEFAULT_LIVE_VOICE_PHRASE));
  const transcriptionKey = String(env.TRANSCRIPTION_API_KEY || env.GROQ_API_KEY || "").trim();
  if (!transcriptionKey) throw new Error("missing_required_secret:TRANSCRIPTION_API_KEY_or_GROQ_API_KEY");
  const bridgeUrl = requiredEnv("H_SUPABASE_VOICE_URL", env);
  const runtimeSecret = requiredEnv("H_RUNTIME_SECRET", env);

  const metadataResponse = await fetchWithTimeout(
    fetchImpl,
    `https://graph.facebook.com/${graphVersion}/${encodeURIComponent(mediaId)}`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  const metadata = await jsonResponse(metadataResponse, "meta_media_metadata");
  if (!metadata?.url) throw new Error("meta_media_download_url_missing");

  const mediaResponse = await fetchWithTimeout(fetchImpl, metadata.url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!mediaResponse.ok) throw new Error(`meta_media_download_http_${mediaResponse.status}`);
  const blob = await mediaResponse.blob();
  if (!blob.size || blob.size > MAX_VOICE_BYTES) throw new Error(`live_voice_size_invalid:${blob.size}`);

  const transcriptionUrl = String(env.TRANSCRIPTION_API_URL || "https://api.groq.com/openai/v1/audio/transcriptions").trim();
  const transcriptionModel = String(env.TRANSCRIPTION_MODEL || "whisper-large-v3-turbo").trim();
  const form = new FormData();
  form.append("file", blob, `whatsapp-live.${extensionForMime(metadata.mime_type || blob.type)}`);
  form.append("model", transcriptionModel);
  form.append("response_format", "json");
  const transcriptionResponse = await fetchWithTimeout(fetchImpl, transcriptionUrl, {
    method: "POST",
    headers: { Authorization: `Bearer ${transcriptionKey}` },
    body: form,
  }, 45_000);
  const transcription = await jsonResponse(transcriptionResponse, "voice_transcription");
  const transcript = String(transcription?.text || "").trim();
  if (!transcript) throw new Error("voice_transcription_empty");
  if (!normalizeProbeText(transcript).includes(expectedPhrase)) throw new Error("voice_probe_phrase_not_detected");

  const messageId = `h-live-voice-${crypto.randomUUID()}`;
  const bridgeResponse = await fetchWithTimeout(fetchImpl, bridgeUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-h-runtime-secret": runtimeSecret },
    body: JSON.stringify({
      mode: "voice_transcript",
      wa_id: LIVE_VOICE_SYNTHETIC_WA_ID,
      message_id: messageId,
      transcript,
      received_at: new Date().toISOString(),
      sender_role: "friend",
      can_send_external: false,
    }),
  }, 45_000);
  const bridge = await jsonResponse(bridgeResponse, "h_voice_bridge");
  if (bridge?.ok !== true || bridge?.status !== "processed") throw new Error("h_voice_bridge_not_processed");

  return {
    gate: "whatsapp_voice_live",
    live: true,
    metaMetadataVerified: true,
    metaDownloadVerified: true,
    downloadedBytes: blob.size,
    mimeType: String(metadata.mime_type || blob.type || "").slice(0, 80),
    transcriptionVerified: true,
    expectedProbePhraseDetected: true,
    transcriptLength: transcript.length,
    bridgeProcessed: true,
    bridgeDuplicate: bridge?.duplicate === true,
    replyProduced: Boolean(String(bridge?.reply || "").trim()),
    isolatedSyntheticWaId: true,
    externalMessagingDisabledForProbe: true,
    rawMediaPersistedByHarness: false,
  };
}

export async function runTarget(target, options = {}) {
  if (target === "backup") return runBackupProbe(options);
  if (target === "standby-preflight") return runStandbyAttestation({ ...options, mode: "preflight" });
  if (target === "failover-active") return runStandbyAttestation({ ...options, mode: "active" });
  if (target === "whatsapp-voice") return runWhatsAppVoiceEvidence(options);
  throw new Error(`unsupported_live_evidence_target:${target}`);
}

function safeError(error) {
  return String(error instanceof Error ? error.message : error || "unknown_error")
    .replace(/Bearer\s+[A-Za-z0-9._~+\/-]+/gi, "Bearer [redacted]")
    .slice(0, 240);
}

async function main() {
  const target = String(process.argv[2] || "").trim();
  const startedAt = new Date().toISOString();
  let document;
  try {
    const evidence = await runTarget(target);
    document = {
      schemaVersion: 2,
      ok: true,
      target,
      startedAt,
      completedAt: new Date().toISOString(),
      gitSha: String(process.env.GITHUB_SHA || "").slice(0, 40) || null,
      evidence,
      secretsIncluded: false,
    };
  } catch (error) {
    document = {
      schemaVersion: 2,
      ok: false,
      target,
      startedAt,
      completedAt: new Date().toISOString(),
      gitSha: String(process.env.GITHUB_SHA || "").slice(0, 40) || null,
      error: safeError(error),
      secretsIncluded: false,
    };
    await writeFile(EVIDENCE_OUTPUT, `${JSON.stringify(document, null, 2)}\n`, "utf8");
    console.error(document.error);
    process.exitCode = 1;
    return;
  }
  await writeFile(EVIDENCE_OUTPUT, `${JSON.stringify(document, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(document, null, 2));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) await main();
