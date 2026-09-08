import {
  deliveryMetadata,
  parseVoiceTranscriptPayload,
  syntheticMetaConversationId,
} from "./voice-bridge.ts";

Deno.test("voice transcript payload accepts Arabic and normalizes wa id", () => {
  const value = parseVoiceTranscriptPayload({
    mode: "voice_transcript",
    wa_id: "+966 55 123 4567",
    message_id: "wamid.voice-1",
    transcript: "  ذكرني بعد ساعة أشرب ماء  ",
    received_at: "2026-09-08T18:00:00Z",
  });
  if (!value) throw new Error("expected valid voice input");
  if (value.waId !== "966551234567") throw new Error(`unexpected wa id ${value.waId}`);
  if (value.transcript !== "ذكرني بعد ساعة أشرب ماء") throw new Error("transcript was not trimmed");
});

Deno.test("voice transcript rejects empty oversized or malformed payloads", () => {
  const invalid = [
    null,
    { mode: "voice_transcript", wa_id: "123", message_id: "x", transcript: "hello" },
    { mode: "voice_transcript", wa_id: "966551234567", message_id: "x", transcript: "" },
    { mode: "voice_transcript", wa_id: "966551234567", message_id: "x", transcript: "x".repeat(12001) },
    { mode: "voice_transcript", wa_id: "966551234567", message_id: "x", transcript: "hi", received_at: "bad-date" },
  ];
  for (const value of invalid) {
    if (parseVoiceTranscriptPayload(value) !== null) throw new Error("invalid payload was accepted");
  }
});

Deno.test("meta conversation id is stable positive and owner specific", () => {
  const first = syntheticMetaConversationId("966551234567");
  const again = syntheticMetaConversationId("966551234567");
  const other = syntheticMetaConversationId("966551234568");
  if (first !== again || first <= 0) throw new Error("conversation id is not stable and positive");
  if (first === other) throw new Error("different users unexpectedly collided in test fixture");
});

Deno.test("delivery metadata records meta channel without credentials", () => {
  const metadata = deliveryMetadata(
    { channel: "meta", targetWaId: "+966 55 123 4567" },
    "whatsapp_voice_meta",
    "احفظ هذه الفكرة",
  );
  if (metadata.delivery_channel !== "meta") throw new Error("missing delivery channel");
  if (metadata.target_wa_id !== "966551234567") throw new Error("target was not normalized");
  if (Object.keys(metadata).some((key) => /secret|token|key/i.test(key))) throw new Error("credential-like metadata key found");
});


Deno.test("voice bridge accepts trusted owner external capability", () => {
  const value = parseVoiceTranscriptPayload({
    mode: "voice_transcript",
    wa_id: "966551234567",
    message_id: "wamid.owner-capability",
    transcript: "أرسل لمحمد وصلت",
    sender_role: "owner",
    can_send_external: true,
  });
  if (!value || value.senderRole !== "owner" || !value.canSendExternal) {
    throw new Error("owner capability was not preserved");
  }
});

Deno.test("voice bridge does not allow friend to forge external capability", () => {
  const value = parseVoiceTranscriptPayload({
    mode: "voice_transcript",
    wa_id: "966551234567",
    message_id: "wamid.friend-capability",
    transcript: "أرسل لمحمد وصلت",
    sender_role: "friend",
    can_send_external: true,
  });
  if (!value || value.senderRole !== "friend" || value.canSendExternal) {
    throw new Error("friend external capability was accepted");
  }
});

Deno.test("legacy voice payload defaults to no external capability", () => {
  const value = parseVoiceTranscriptPayload({
    mode: "voice_transcript",
    wa_id: "966551234567",
    message_id: "wamid.legacy-safe",
    transcript: "مرحبا",
  });
  if (!value || value.canSendExternal || value.senderRole !== "friend") {
    throw new Error("legacy payload did not default to safe capability");
  }
});
