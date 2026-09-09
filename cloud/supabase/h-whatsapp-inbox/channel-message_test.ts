import {
  channelMessageKey,
  channelMessageType,
  parseChannelMessagePayload,
} from "./channel-message.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("channel message parser keeps source semantics and trusted owner capability", () => {
  const input = parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "+966 55 123 4567",
    message_id: "wamid.text-1",
    text: "ذكرني بعد ساعة",
    source_type: "text",
    sender_role: "owner",
    can_send_external: true,
    received_at: "2026-09-09T00:00:00+03:00",
  });
  assert(input?.waId === "966551234567");
  assert(input?.sourceType === "text");
  assert(input?.senderRole === "owner");
  assert(input?.canSendExternal === true);
  assert(input?.receivedAt === "2026-09-08T21:00:00.000Z");
  assert(channelMessageKey(input) === "meta:channel:text:wamid.text-1");
  assert(channelMessageType(input.sourceType) === "channel_text");
});

Deno.test("friend cannot forge external capability", () => {
  const input = parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "966551234567",
    message_id: "wamid.friend",
    text: "أرسل رسالة إلى محمد",
    source_type: "interactive",
    sender_role: "friend",
    can_send_external: true,
  });
  assert(input?.senderRole === "friend");
  assert(input?.canSendExternal === false);
});

Deno.test("media source types share the media idempotency namespace", () => {
  for (const sourceType of ["image", "document", "audio", "video"] as const) {
    const input = parseChannelMessagePayload({
      mode: "channel_message",
      wa_id: "966551234567",
      message_id: `media-${sourceType}`,
      text: `[${sourceType}] analyzed content`,
      source_type: sourceType,
    });
    assert(input?.sourceType === sourceType);
    assert(channelMessageType(sourceType) === `channel_${sourceType}`);
    assert(channelMessageKey(input) === `meta:media:media-${sourceType}`);
  }
});

Deno.test("long ids reproduce previous bridge truncation exactly", () => {
  const longId = "x".repeat(200);
  const oldTextBridgeId = `channel:location:${longId}`.slice(0, 200);
  const oldMediaBridgeId = `media:${longId}`.slice(0, 200);
  assert(channelMessageKey({ sourceType: "location", messageId: longId }) === `meta:${oldTextBridgeId}`);
  assert(channelMessageKey({ sourceType: "image", messageId: longId }) === `meta:${oldMediaBridgeId}`);
});

Deno.test("channel parser rejects voice mode, unknown sources, empty text and malformed ids", () => {
  assert(parseChannelMessagePayload({ mode: "voice_transcript" }) === null);
  assert(parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "966551234567",
    message_id: "x",
    text: "hello",
    source_type: "binary_blob",
  }) === null);
  assert(parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "966551234567",
    message_id: "x",
    text: "",
    source_type: "text",
  }) === null);
  assert(parseChannelMessagePayload({
    mode: "channel_message",
    wa_id: "123",
    message_id: "x",
    text: "hello",
    source_type: "text",
  }) === null);
});
