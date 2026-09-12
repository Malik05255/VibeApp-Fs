import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeSupabaseEndpoint,
  runBackupProbe,
  runStandbyAttestation,
  runWhatsAppVoiceEvidence,
} from "./h-live-evidence.mjs";

const json = (value, status = 200) => new Response(JSON.stringify(value), {
  status,
  headers: { "Content-Type": "application/json" },
});

test("Supabase endpoint validation is strict", () => {
  assert.equal(normalizeSupabaseEndpoint("https://abc.supabase.co/"), "https://abc.supabase.co");
  assert.equal(normalizeSupabaseEndpoint("http://abc.supabase.co"), null);
  assert.equal(normalizeSupabaseEndpoint("https://example.com"), null);
});

test("backup probe verifies write read delete", async () => {
  const env = {
    H_BACKUP_SUPABASE_URL: "https://backup.supabase.co",
    H_PRIMARY_SUPABASE_URL: "https://primary.supabase.co",
    H_BACKUP_SUPABASE_SERVICE_ROLE_KEY: "x".repeat(64),
  };
  let body = "";
  const fetchImpl = async (url, options = {}) => {
    const text = String(url);
    if (text.endsWith("/storage/v1/bucket") && !options.method) return json([{ id: "h-backups" }]);
    if (options.method === "POST") { body = String(options.body || ""); return json({}); }
    if ((options.method || "GET") === "GET" && text.includes("/_h_live_evidence/")) return new Response(body, { status: 200 });
    if (options.method === "DELETE") return json({});
    throw new Error(`unexpected request ${text}`);
  };
  const result = await runBackupProbe({ env, fetchImpl, now: () => new Date("2026-09-12T14:00:00Z") });
  assert.equal(result.writeVerified, true);
  assert.equal(result.readVerified, true);
  assert.equal(result.deleteVerified, true);
  assert.equal(result.rawSecretExposed, false);
});

test("backup probe rejects the primary project", async () => {
  const env = {
    H_BACKUP_SUPABASE_URL: "https://same.supabase.co",
    H_PRIMARY_SUPABASE_URL: "https://same.supabase.co",
    H_BACKUP_SUPABASE_SERVICE_ROLE_KEY: "x".repeat(64),
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

test("standby preflight requires an exact fresh mirror", async () => {
  const env = { H_STANDBY_HEALTH_URL: "https://standby.example/health", H_RUNTIME_SECRET: "secret" };
  const fetchImpl = async () => json({
    ...standby,
    preflightReady: true,
    standbyReady: true,
    passivePreflightOnly: true,
    activeReady: false,
    promotionAttested: false,
  });
  const result = await runStandbyAttestation({ mode: "preflight", env, fetchImpl });
  assert.equal(result.replicationProtocol, "exact_mirror_v2");
  assert.equal(result.restoreVerified, true);
});

test("active failover requires request-only attestation and primary outage", async () => {
  const env = {
    H_STANDBY_HEALTH_URL: "https://standby.example/health",
    H_RUNTIME_SECRET: "secret",
    H_EXPECT_PRIMARY_UNREACHABLE: "true",
    H_PRIMARY_HEALTH_URL: "https://primary.example/health",
  };
  const fetchImpl = async (url) => {
    if (String(url).includes("standby")) return json({
      ...standby,
      activeReady: true,
      requestOnlyActive: true,
      promotionAttested: true,
      promotionMode: "request_only",
      replicaWritesEnabled: false,
    });
    throw new Error("primary unavailable");
  };
  const result = await runStandbyAttestation({ mode: "active", env, fetchImpl });
  assert.equal(result.promotionAttested, true);
  assert.equal(result.requestOnlyActive, true);
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
