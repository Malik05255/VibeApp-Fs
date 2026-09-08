import test from "node:test";
import assert from "node:assert/strict";
import {
  looksLikeOwnerExternalMessagingIntent,
  normalizeUnifiedText,
  partitionWebhookPayload,
  routeDecisionForMessage,
} from "./router.js";

const baseEnv = {
  CONTROL_WA_IDS: "966500000001",
  H_ALLOWED_WA_IDS: "966500000002",
  ALLOW_UNKNOWN_USERS: "false",
  H_SUPABASE_VOICE_URL: "https://example.supabase.co/functions/v1/h-whatsapp-inbox",
  H_RUNTIME_SECRET: "test-secret",
};

test("blocked audio is rejected before legacy transcription path", () => {
  const decision = routeDecisionForMessage({
    id: "wamid.blocked-audio",
    from: "966500009999",
    type: "audio",
    audio: { id: "media-1" },
  }, baseEnv);
  assert.equal(decision.kind, "blocked");
});

test("authorized text uses unified H bridge when configured", () => {
  const decision = routeDecisionForMessage({
    id: "wamid.text-1",
    from: "966500000001",
    type: "text",
    text: { body: "احفظ هذه الفكرة" },
  }, baseEnv);
  assert.equal(decision.kind, "unified");
  assert.equal(decision.text, "احفظ هذه الفكرة");
});

test("owner external contact commands stay on guarded legacy execution path", () => {
  const samples = [
    "احفظ محمد 966551234567",
    "احفظ رقم محمد 966551234567",
    "أرسل رسالة إلى محمد",
    "ارسل لمحمد الموعد تغير",
  ];
  for (const text of samples) {
    assert.equal(looksLikeOwnerExternalMessagingIntent(text), true, text);
    const decision = routeDecisionForMessage({
      id: `wamid.owner-${samples.indexOf(text)}`,
      from: "966500000001",
      type: "text",
      text: { body: text },
    }, baseEnv);
    assert.equal(decision.kind, "delegate", text);
  }
});

test("ordinary owner memory request is not mistaken for external messaging", () => {
  assert.equal(looksLikeOwnerExternalMessagingIntent("احفظ هذه الفكرة عن السباكة"), false);
  const decision = routeDecisionForMessage({
    id: "wamid.owner-memory",
    from: "966500000001",
    type: "text",
    text: { body: "احفظ هذه الفكرة عن السباكة" },
  }, baseEnv);
  assert.equal(decision.kind, "unified");
});

test("friend cannot enter owner-only legacy external messaging path", () => {
  const decision = routeDecisionForMessage({
    id: "wamid.friend-send",
    from: "966500000002",
    type: "text",
    text: { body: "أرسل رسالة إلى محمد" },
  }, baseEnv);
  assert.equal(decision.kind, "unified");
});

test("authorized audio remains delegated to the existing voice pipeline", () => {
  const decision = routeDecisionForMessage({
    id: "wamid.voice-1",
    from: "966500000001",
    type: "audio",
    audio: { id: "media-voice" },
  }, baseEnv);
  assert.equal(decision.kind, "delegate");
});

test("text falls back to legacy D1 only when unified bridge is not configured", () => {
  const decision = routeDecisionForMessage({
    id: "wamid.text-fallback",
    from: "966500000001",
    type: "text",
    text: { body: "ذكرني بعد ساعة" },
  }, { ...baseEnv, H_SUPABASE_VOICE_URL: "", H_RUNTIME_SECRET: "" });
  assert.equal(decision.kind, "delegate");
});

test("location is normalized without losing coordinates or label", () => {
  const text = normalizeUnifiedText({
    type: "location",
    location: {
      latitude: 18.2164,
      longitude: 42.5053,
      name: "محايل عسير",
      address: "عسير",
    },
  });
  assert.match(text, /18\.2164/);
  assert.match(text, /42\.5053/);
  assert.match(text, /محايل عسير/);
});

test("mixed webhook removes blocked and unified text from legacy payload", () => {
  const payload = {
    entry: [{
      changes: [{
        value: {
          messages: [
            { id: "blocked", from: "966500009999", type: "audio", audio: { id: "a" } },
            { id: "text", from: "966500000001", type: "text", text: { body: "مرحبا" } },
            { id: "audio", from: "966500000002", type: "audio", audio: { id: "b" } },
          ],
        },
      }],
    }],
  };

  const result = partitionWebhookPayload(payload, baseEnv);
  assert.equal(result.blocked.length, 1);
  assert.equal(result.unified.length, 1);
  assert.deepEqual(
    result.delegatedPayload.entry[0].changes[0].value.messages.map((message) => message.id),
    ["audio"],
  );
});
