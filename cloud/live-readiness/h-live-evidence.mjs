import { writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import crypto from "node:crypto";

const BACKUP_BUCKET = "h-backups";
const MAX_VOICE_BYTES = 8 * 1024 * 1024;
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

export async function runBackupProbe({ env = process.env, fetchImpl = fetch, now = () => new Date() } = {}) {
  const endpoint = normalizeSupabaseEndpoint(requiredEnv("H_BACKUP_SUPABASE_URL", env));
  if (!endpoint) throw new Error("backup_endpoint_invalid");
  const primary = normalizeSupabaseEndpoint(String(env.H_PRIMARY_SUPABASE_URL || ""));
  if (primary && primary === endpoint) throw new Error("backup_must_be_different_from_primary");
  const serviceRole = requiredEnv("H_BACKUP_SUPABASE_SERVICE_ROLE_KEY", env);
  if (serviceRole.length < 40) throw new Error("backup_service_role_key_invalid");

  const headers = {
    apikey: serviceRole,
    Authorization: `Bearer ${serviceRole}`,
    Accept: "application/json",
  };

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

  return {
    gate: "backup_cloud",
    live: true,
    endpointHost: new URL(endpoint).host,
    bucket: BACKUP_BUCKET,
    bucketCreated,
    writeVerified: true,
    readVerified,
    deleteVerified: true,
    rawSecretExposed: false,
  };
}

export async function runStandbyAttestation({
  mode = "preflight",
  env = process.env,
  fetchImpl = fetch,
} = {}) {
  if (!new Set(["preflight", "active"]).has(mode)) throw new Error("standby_mode_invalid");
  const standbyHealthUrl = requiredEnv("H_STANDBY_HEALTH_URL", env);
  const runtimeSecret = requiredEnv("H_RUNTIME_SECRET", env);
  const response = await fetchWithTimeout(fetchImpl, standbyHealthUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-h-runtime-secret": runtimeSecret },
    body: "{}",
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
        const primary = await fetchWithTimeout(fetchImpl, primaryHealthUrl, { method: "GET" }, 6_000);
        primaryReachable = primary.ok;
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

export async function runWhatsAppVoiceEvidence({ env = process.env, fetchImpl = fetch } = {}) {
  const accessToken = requiredEnv("WHATSAPP_ACCESS_TOKEN", env);
  const graphVersion = requiredEnv("META_GRAPH_VERSION", env).replace(/^v?/i, "v");
  const mediaId = requiredEnv("H_LIVE_VOICE_MEDIA_ID", env);
  const waId = requiredEnv("H_LIVE_VOICE_WA_ID", env).replace(/\D/g, "");
  if (!/^\d{8,20}$/.test(waId)) throw new Error("live_voice_wa_id_invalid");
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

  const messageId = `h-live-voice-${crypto.randomUUID()}`;
  const bridgeResponse = await fetchWithTimeout(fetchImpl, bridgeUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-h-runtime-secret": runtimeSecret },
    body: JSON.stringify({
      mode: "voice_transcript",
      wa_id: waId,
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
    transcriptLength: transcript.length,
    bridgeProcessed: true,
    bridgeDuplicate: bridge?.duplicate === true,
    replyProduced: Boolean(String(bridge?.reply || "").trim()),
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
      schemaVersion: 1,
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
      schemaVersion: 1,
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

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
