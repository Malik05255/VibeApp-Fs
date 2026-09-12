import test from "node:test";
import assert from "node:assert/strict";
import {
  normalizeSupabaseEndpoint,
  runBackupProbe,
  runStandbyAttestation,
  runWhatsAppVoiceEvidence,
} from "./h-live-evidence.mjs";

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

test("normalizes only clean Supabase HTTPS endpoints", () => {
  assert.equal(normalizeSupabaseEndpoint("https://abc.supabase.co/"), "https://abc.supabase.co");
  assert.equal(normalizeSupabaseEndpoint("http://abc.supabase.co"), null);
  assert.equal(normalizeSupabaseEndpoint("https://example.com"), null);
  assert.equal(normalizeSupabaseEndpoint("https://abc.supabase.co?x=1"), null);
});

test("backup live probe proves write read delete without exposing secret", async () => {
  const env = {
    H_BACKUP_SUPABASE_URL: "https://backup.supabase.co",
    H_PRIMARY_SUPABASE_URL: "https://primary.supabase.co",
    H_BACKUP_SUPABASE_SERVICE_ROLE_KEY: "x".repeat(64),
  };
  let uploaded = "";
  const calls = [];
  const fetchImpl = async (url, options = {}) => {
    calls.push([String(url), options.method || "GET"]);
    if (String(url).endsWith("/storage/v1/bucket") && !options.method) return json([{ id: "h-backups" }]);
    if (options.method === "POST") {
      uploaded = String(options.body || "");
      return json({ Key: "probe" });
    }
    if ((options.method || "GET") === "GET" && String(url).includes("/_h_live_evidence/")) {
      return new Response(uploaded, { status: 200 });
    }
    if (options.method === "DELETE") return json({});
    throw new Error(`unexpected fetch ${url} ${options.method || "GET"}`);
  };

  const evidence = await runBackupProbe({ env, fetchImpl, now: () => new Date("2026-09-12T14:00:00Z") });
  assert.equal(evidence.live, true);
  assert.equal(evidence.writeVerified, true);
  assert.equal(evidence.readVerified, true);
  assert.equal(evidence.deleteVerified, true);
  assert.equal(evidence.rawSecretExposed, false);
  assert.ok(calls.some(([, method]) => method === "DELETE"));
});

test("backup probe rejects reusing the primary project", async () => {
  const env = {
    H_BACKUP_SUPABASE_URL: "https://same.supabase.co",
    H_PRIMARY_SUPABASE_URL: "https://same.supabase.co",
    H_BACKUP_SUPABASE_SERVICE_ROLE_KEY: "x".repeat(64),
  };
  await assert.rejects(() => runBackupProbe({ env, fetchImpl: async () => json({}) }), /different_from_primary/);
});

const standbyBase = {
  ok: true,
  service: "h-standby-health",
  hIdentity: "H",
  runtimeRole: "standby",
  replicationProtocol: "exact_mirror_v2",
  restoreVerified: true,
  replicationFresh: true,
  replicationLagSeconds: 4,
  aiContinuityFresh: true,
  rawProviderCredentialsReplicated: false,
  rawMediaReplicated: false,
};

test("standby preflight requires fresh exact mirror and passive readiness", async () => {
  const env = { H_STANDBY_HEALTH_URL: "https://standby.example/health", H_RUNTIME_SECRET: "secret" };
  const fetchImpl = async () => json({
    ...standbyBase,
    preflightReady: true,
    standbyReady: true,
    passivePreflightOnly: true,
    activeReady: false,
    promotionAttested: false,
  });
  const evidence = await runStandbyAttestation({ mode: "preflight", env, fetchImpl });
  assert.equal(evidence.live, true);
  assert.equal(evidence.replicationProtocol, "exact_mirror_v2");
  assert.equal(evidence.restoreVerified, true);
});

test("active standby requires request-only attested promotion and can prove primary outage", async () => {
  const env = {
    H_STANDBY_HEALTH_URL: "https://standby.example/health",
    H_RUNTIME_SECRET: "secret",
    H_EXPECT_PRIMARY_UNREACHABLE: "true",
    H_PRIMARY_HEALTH_URL: "https://primary.example/health",
  };
  const fetchImpl = async (url) => {
    if (String(url).includes("standby")) return json({
      ...standbyBase,
      preflightReady: false,
      standbyReady: false,
      activeReady: true,
      requestOnlyActive: true,
      promotionAttested: true,
      promotionMode: "request_only",
      replicaWritesEnabled: false,
    });
    throw new Error("primary unreachable");
  };
  const evidence = await runStandbyAttestation({ mode: "active", env, fetchImpl });
  assert.equal(evidence.promotionAttested, true);
  assert.equal(evidence.requestOnlyActive, true);
  assert.equal(evidence.noAutomaticFailbackEvidence, true);
});

test("voice live evidence traverses Meta metadata, media, STT and H bridge without external actions", async () => {
  const env = {
    WHATSAPP_ACCESS_TOKEN: "meta-token",
    META_GRAPH_VERSION: "v24.0",
    H_LIVE_VOICE_MEDIA_ID: "media-1",
    H_LIVE_VOICE_WA_ID: "966500000001",
    TRANSCRIPTION_API_KEY: "stt-token",
    H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
    H_RUNTIME_SECRET: "runtime-secret",
  };
  const calls = [];
  const fetchImpl = async (url, options = {}) => {
    calls.push(String(url));
    if (String(url).startsWith("https://graph.facebook.com/")) {
      return json({ url: "https://cdn.example/audio", mime_type: "audio/ogg" });
    }
    if (String(url) === "https://cdn.example/audio") {
      return new Response(new Blob(["voice-bytes"], { type: "audio/ogg" }), { status: 200 });
    }
    if (String(url).includes("audio/transcriptions")) {
      assert.match(String(options.headers?.Authorization || ""), /^Bearer /);
      return json({ text: "اختبار صوت H" });
    }
    if (String(url).includes("h-whatsapp-inbox")) {
      const payload = JSON.parse(String(options.body || "{}"));
      assert.equal(payload.mode, "voice_transcript");
      assert.equal(payload.sender_role, "friend");
      assert.equal(payload.can_send_external, false);
      return json({ ok: true, status: "processed", duplicate: false, reply: "تم الاختبار" });
    }
    throw new Error(`unexpected fetch ${url}`);
  };

  const evidence = await runWhatsAppVoiceEvidence({ env, fetchImpl });
  assert.equal(evidence.live, true);
  assert.equal(evidence.metaDownloadVerified, true);
  assert.equal(evidence.transcriptionVerified, true);
  assert.equal(evidence.bridgeProcessed, true);
  assert.equal(evidence.externalMessagingDisabledForProbe, true);
  assert.equal(evidence.rawMediaPersistedByHarness, false);
  assert.equal(calls.length, 4);
});
