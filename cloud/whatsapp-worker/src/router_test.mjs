import test from "node:test";
import assert from "node:assert/strict";
import {
  bridgeUnifiedMessage,
  buildUnifiedBridgePayload,
  looksLikeOwnerExternalMessagingIntent,
  normalizeUnifiedText,
  partitionWebhookPayload,
  profileNameForWaId,
  routeDecisionForMessage,
  selectUnifiedRuntime,
  standbyRuntimeConfigured,
} from "./router.js";

const baseEnv = {
  CONTROL_WA_IDS: "966500000001",
  H_ALLOWED_WA_IDS: "966500000002",
  ALLOW_UNKNOWN_USERS: "false",
  H_SUPABASE_VOICE_URL: "https://primary.supabase.co/functions/v1/h-whatsapp-inbox",
  H_RUNTIME_SECRET: "test-secret",
};

const standbyEnv = {
  ...baseEnv,
  H_STANDBY_FAILOVER_ENABLED: "true",
  H_STANDBY_SUPABASE_VOICE_URL: "https://standby.supabase.co/functions/v1/h-whatsapp-inbox",
  H_STANDBY_RUNTIME_SECRET: "standby-secret",
};

function jsonResponse(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function unifiedItem() {
  return {
    from: "966500000001",
    sourceType: "text",
    text: "ذكرني بعد ساعة",
    senderRole: "owner",
    canSendExternal: true,
    message: { id: "wamid.failover-1", timestamp: "1788900000" },
  };
}

test("blocked audio is rejected before legacy transcription path", () => {
  const decision = routeDecisionForMessage({ id: "wamid.blocked-audio", from: "966500009999", type: "audio", audio: { id: "media-1" } }, baseEnv);
  assert.equal(decision.kind, "blocked");
});

test("authorized text uses unified H bridge when configured", () => {
  const decision = routeDecisionForMessage({ id: "wamid.text-1", from: "966500000001", type: "text", text: { body: "احفظ هذه الفكرة" } }, baseEnv);
  assert.equal(decision.kind, "unified");
  assert.equal(decision.text, "احفظ هذه الفكرة");
});

test("owner external contact commands use unified H with trusted capability", () => {
  const samples = ["احفظ محمد 966551234567", "احفظ رقم محمد 966551234567", "أرسل رسالة إلى محمد", "ارسل لمحمد الموعد تغير"];
  for (const text of samples) {
    assert.equal(looksLikeOwnerExternalMessagingIntent(text), true, text);
    const decision = routeDecisionForMessage({ id: `wamid.owner-${samples.indexOf(text)}`, from: "966500000001", type: "text", text: { body: text } }, baseEnv);
    assert.equal(decision.kind, "unified", text);
    assert.equal(decision.senderRole, "owner", text);
    assert.equal(decision.canSendExternal, true, text);
  }
});

test("ordinary owner memory request is not mistaken for external messaging", () => {
  assert.equal(looksLikeOwnerExternalMessagingIntent("احفظ هذه الفكرة عن السباكة"), false);
  const decision = routeDecisionForMessage({ id: "wamid.owner-memory", from: "966500000001", type: "text", text: { body: "احفظ هذه الفكرة عن السباكة" } }, baseEnv);
  assert.equal(decision.kind, "unified");
});

test("friend cannot enter owner-only legacy external messaging path", () => {
  const decision = routeDecisionForMessage({ id: "wamid.friend-send", from: "966500000002", type: "text", text: { body: "أرسل رسالة إلى محمد" } }, baseEnv);
  assert.equal(decision.kind, "unified");
  assert.equal(decision.senderRole, "friend");
  assert.equal(decision.canSendExternal, false);
});

test("authorized audio remains delegated to the existing voice pipeline", () => {
  const decision = routeDecisionForMessage({ id: "wamid.voice-1", from: "966500000001", type: "audio", audio: { id: "media-voice" } }, baseEnv);
  assert.equal(decision.kind, "delegate");
});

test("text falls back to legacy D1 only when unified bridge is not configured", () => {
  const decision = routeDecisionForMessage({ id: "wamid.text-fallback", from: "966500000001", type: "text", text: { body: "ذكرني بعد ساعة" } }, { ...baseEnv, H_SUPABASE_VOICE_URL: "", H_RUNTIME_SECRET: "" });
  assert.equal(decision.kind, "delegate");
});

test("location is normalized without losing coordinates or label", () => {
  const text = normalizeUnifiedText({ type: "location", location: { latitude: 18.2164, longitude: 42.5053, name: "محايل عسير", address: "عسير" } });
  assert.match(text, /18\.2164/);
  assert.match(text, /42\.5053/);
  assert.match(text, /محايل عسير/);
});

test("unified text bridge emits channel_message rather than voice_transcript", () => {
  const payload = buildUnifiedBridgePayload({ from: "966500000001", sourceType: "location", text: "شارك المستخدم موقعه: 18.2164, 42.5053", senderRole: "owner", canSendExternal: true, message: { id: "wamid.location-1", timestamp: "1788900000" } });
  assert.equal(payload.mode, "channel_message");
  assert.equal(payload.wa_id, "966500000001");
  assert.equal(payload.message_id, "wamid.location-1");
  assert.equal(payload.source_type, "location");
  assert.equal(payload.text, "شارك المستخدم موقعه: 18.2164, 42.5053");
  assert.equal(payload.sender_role, "owner");
  assert.equal(payload.can_send_external, true);
  assert.equal(Object.hasOwn(payload, "transcript"), false);
});

test("profile name lookup matches the sender without exposing another contact", () => {
  const value = { contacts: [{ wa_id: "966500000002", profile: { name: "صديق H" } }, { wa_id: "966500000003", profile: { name: "شخص آخر" } }] };
  assert.equal(profileNameForWaId(value, "+966 50 000 0002"), "صديق H");
  assert.equal(profileNameForWaId(value, "966500000004"), null);
});

test("mixed webhook isolates blocked/unified messages and delegated contact metadata", () => {
  const payload = { entry: [{ changes: [{ value: { contacts: [{ wa_id: "966500000001", profile: { name: "مالك H" } }, { wa_id: "966500000002", profile: { name: "صديق H" } }, { wa_id: "966500009999", profile: { name: "محظور" } }], messages: [{ id: "blocked", from: "966500009999", type: "audio", audio: { id: "a" } }, { id: "text", from: "966500000001", type: "text", timestamp: "1788900000", text: { body: "مرحبا" } }, { id: "audio", from: "966500000002", type: "audio", audio: { id: "b" } }] } }] }] };
  const result = partitionWebhookPayload(payload, baseEnv);
  assert.equal(result.blocked.length, 1);
  assert.equal(result.unified.length, 1);
  assert.equal(result.unified[0].profileName, "مالك H");
  assert.equal(result.unified[0].message.timestamp, "1788900000");
  assert.deepEqual(result.delegatedPayload.entry[0].changes[0].value.messages.map((message) => message.id), ["audio"]);
  assert.deepEqual(result.delegatedPayload.entry[0].changes[0].value.contacts.map((contact) => contact.wa_id), ["966500000002"]);
});

test("standby is disabled unless all explicit failover settings are present", () => {
  assert.equal(standbyRuntimeConfigured(baseEnv), false);
  assert.equal(standbyRuntimeConfigured({ ...standbyEnv, H_STANDBY_FAILOVER_ENABLED: "false" }), false);
  assert.equal(standbyRuntimeConfigured({ ...standbyEnv, H_STANDBY_RUNTIME_SECRET: "" }), false);
  assert.equal(standbyRuntimeConfigured(standbyEnv), true);
});

test("primary-only mode does not add a health probe or latency", async () => {
  let calls = 0;
  const runtime = await selectUnifiedRuntime(baseEnv, async () => { calls += 1; throw new Error("should not probe"); });
  assert.equal(runtime.role, "primary");
  assert.equal(calls, 0);
});

test("healthy primary wins and standby is not probed", async () => {
  const urls = [];
  const runtime = await selectUnifiedRuntime(standbyEnv, async (url) => { urls.push(String(url)); return jsonResponse({ ok: true, service: "h-runtime-readiness" }); });
  assert.equal(runtime.role, "primary");
  assert.equal(urls.length, 1);
  assert.match(urls[0], /primary\.supabase\.co\/functions\/v1\/h-runtime-readiness$/);
});

test("unhealthy primary selects only a strictly validated continuously replicated standby", async () => {
  const urls = [];
  const runtime = await selectUnifiedRuntime(standbyEnv, async (url) => {
    const value = String(url); urls.push(value);
    if (value.includes("primary.supabase.co")) return jsonResponse({ ok: false }, 503);
    return jsonResponse({ ok: true, service: "h-standby-health", standbyReady: true, runtimeRole: "standby", hIdentity: "H", restoreVerified: true, replicationMode: "continuous", replicationLagSeconds: 20 });
  });
  assert.equal(runtime.role, "standby");
  assert.equal(urls.length, 2);
  assert.match(urls[1], /standby\.supabase\.co\/functions\/v1\/h-standby-health$/);
});

test("storage-only or stale standby is rejected instead of being promoted", async () => {
  await assert.rejects(() => selectUnifiedRuntime(standbyEnv, async (url) => {
    const value = String(url);
    if (value.includes("primary.supabase.co")) return jsonResponse({ ok: false }, 503);
    return jsonResponse({ ok: true, service: "h-standby-health", standbyReady: false, runtimeRole: "standby", hIdentity: "H", restoreVerified: true, replicationMode: "daily_backup", replicationLagSeconds: 3600 });
  }), /No validated H runtime/);
});

test("execution failure is never retried on the other runtime", async () => {
  const urls = [];
  await assert.rejects(() => bridgeUnifiedMessage(baseEnv, unifiedItem(), async (url) => { urls.push(String(url)); return jsonResponse({ ok: false, error: "runtime_failed" }, 500); }), /primary runtime rejected request/);
  assert.deepEqual(urls, [baseEnv.H_SUPABASE_VOICE_URL]);
});

test("standby routing probes before execution and posts the message only to standby", async () => {
  const urls = [];
  const result = await bridgeUnifiedMessage(standbyEnv, unifiedItem(), async (url) => {
    const value = String(url); urls.push(value);
    if (value.includes("primary.supabase.co") && value.endsWith("/h-runtime-readiness")) return jsonResponse({ ok: false }, 503);
    if (value.endsWith("/h-standby-health")) return jsonResponse({ ok: true, service: "h-standby-health", standbyReady: true, runtimeRole: "standby", hIdentity: "H", restoreVerified: true, replicationMode: "continuous", replicationLagSeconds: 15 });
    if (value === standbyEnv.H_STANDBY_SUPABASE_VOICE_URL) return jsonResponse({ ok: true, reply: "تم" });
    throw new Error(`unexpected URL ${value}`);
  });
  assert.equal(result.runtimeRoute, "standby");
  assert.equal(result.reply, "تم");
  assert.deepEqual(urls, ["https://primary.supabase.co/functions/v1/h-runtime-readiness", "https://standby.supabase.co/functions/v1/h-standby-health", standbyEnv.H_STANDBY_SUPABASE_VOICE_URL]);
  assert.equal(urls.includes(baseEnv.H_SUPABASE_VOICE_URL), false);
});
