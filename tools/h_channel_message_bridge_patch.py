from pathlib import Path


def patch(path_name: str, replacements: list[tuple[str, str, str]]) -> None:
    path = Path(path_name)
    text = path.read_text()
    for old, new, label in replacements:
        count = text.count(old)
        if count != 1:
            raise SystemExit(f"{path_name} / {label}: expected exactly one match, found {count}")
        text = text.replace(old, new, 1)
    path.write_text(text)


channel_processor = r'''async function processChannelMessage(db: any, payload: unknown) {
  const input = parseChannelMessagePayload(payload);
  if (!input) return { ok: false, error: "invalid_channel_message_payload" };

  const messageKey = channelMessageKey(input);
  const conversationId = syntheticMetaConversationId(input.waId);
  const { data: existing, error: existingError } = await db.from("h_runtime_inbox")
    .select("status,reply_text,error")
    .eq("message_key", messageKey)
    .maybeSingle();
  if (existingError) throw existingError;
  if (existing) {
    return {
      ok: existing.status === "processed",
      duplicate: true,
      status: existing.status,
      reply: existing.status === "processed" ? existing.reply_text || null : null,
      error: existing.status === "failed" ? existing.error || "previous_channel_attempt_failed" : null,
    };
  }

  const now = new Date();
  const row = {
    message_key: messageKey,
    peach_message_id: null,
    conversation_id: conversationId,
    contact_phone: input.waId,
    business_phone_number: null,
    direction: "inbound",
    message_type: channelMessageType(input.sourceType),
    body: input.text,
    source_created_at: input.receivedAt ?? now.toISOString(),
    raw: {
      source: "meta_channel_bridge",
      message_id: input.messageId,
      wa_id: input.waId,
      source_type: input.sourceType,
      text_length: input.text.length,
      sender_role: input.senderRole,
      can_send_external: input.canSendExternal,
    },
    status: "processing",
    updated_at: now.toISOString(),
  };
  const { error: insertError } = await db.from("h_runtime_inbox").insert(row);
  if (insertError) {
    if (String((insertError as any)?.code || "") === "23505") {
      const { data: raced } = await db.from("h_runtime_inbox")
        .select("status,reply_text,error")
        .eq("message_key", messageKey)
        .maybeSingle();
      return {
        ok: raced?.status === "processed",
        duplicate: true,
        status: raced?.status || "processing",
        reply: raced?.status === "processed" ? raced.reply_text || null : null,
        error: raced?.status === "failed" ? raced.error || "previous_channel_attempt_failed" : null,
      };
    }
    throw insertError;
  }

  const userKey = normalizeUserKey(input.waId, conversationId);
  const delivery: VoiceDeliveryContext = {
    channel: "meta",
    targetWaId: input.waId,
    senderRole: input.senderRole,
    canSendExternal: input.canSendExternal,
  };
  try {
    await appendChat(db, userKey, conversationId, "user", input.text, messageKey);
    const response = await decideResponse(db, userKey, conversationId, input.text, now, delivery);
    if (response.reply) {
      await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
    }
    await db.from("h_runtime_inbox").update({
      status: "processed",
      error: null,
      reply_text: response.reply || null,
      processed_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("message_key", messageKey);
    return { ok: true, duplicate: false, status: "processed", reply: response.reply || null };
  } catch (error) {
    const message = errorMessage(error);
    await db.from("h_runtime_inbox").update({
      status: "failed",
      error: message.slice(0, 1000),
      processed_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("message_key", messageKey);
    throw error;
  }
}

'''

patch("cloud/supabase/h-whatsapp-inbox/index.ts", [
    (
        '} from "./voice-bridge.ts";\nimport {\n  canUseExternalMessaging,',
        '} from "./voice-bridge.ts";\nimport { channelMessageKey, channelMessageType, parseChannelMessagePayload } from "./channel-message.ts";\nimport {\n  canUseExternalMessaging,',
        "channel parser import",
    ),
    (
        '''  let requestPayload: unknown = null;
  try { requestPayload = await req.json(); } catch (_) {}
  if (requestPayload && typeof requestPayload === "object" && (requestPayload as any).mode === "voice_transcript") {''',
        '''  let requestPayload: unknown = null;
  try { requestPayload = await req.json(); } catch (_) {}
  if (requestPayload && typeof requestPayload === "object" && (requestPayload as any).mode === "channel_message") {
    try {
      return reply(await processChannelMessage(db, requestPayload), 200);
    } catch (error) {
      console.error("H channel bridge failed", error);
      return reply({ ok: false, error: errorMessage(error) }, 500);
    }
  }
  if (requestPayload && typeof requestPayload === "object" && (requestPayload as any).mode === "voice_transcript") {''',
        "channel request dispatch",
    ),
    (
        'async function processVoiceTranscript(db: any, payload: unknown) {',
        channel_processor + 'async function processVoiceTranscript(db: any, payload: unknown) {',
        "channel processor",
    ),
])

bridge_payload = r'''export function buildUnifiedBridgePayload(item) {
  const receivedAtMs = Number(item.message?.timestamp) * 1000;
  return {
    mode: "channel_message",
    wa_id: item.from,
    message_id: String(item.message?.id || "").slice(0, 200),
    text: String(item.text || "").slice(0, 12000),
    source_type: item.sourceType,
    sender_role: item.senderRole === "owner" ? "owner" : "friend",
    can_send_external: item.canSendExternal === true,
    received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0
      ? new Date(receivedAtMs).toISOString()
      : new Date().toISOString(),
  };
}

'''

patch("cloud/whatsapp-worker/src/router.js", [
    (
        'async function bridgeUnifiedMessage(env, item) {',
        bridge_payload + 'async function bridgeUnifiedMessage(env, item) {',
        "bridge payload helper",
    ),
    (
        '''  const receivedAtMs = Number(item.message?.timestamp) * 1000;
  const messageId = `channel:${item.sourceType}:${String(item.message?.id || "")}`.slice(0, 200);
  const response = await fetch(endpoint, {''',
        '''  const bridgePayload = buildUnifiedBridgePayload(item);
  const response = await fetch(endpoint, {''',
        "bridge payload construction",
    ),
    (
        '''    body: JSON.stringify({
      mode: "voice_transcript",
      wa_id: item.from,
      message_id: messageId,
      transcript: String(item.text || "").slice(0, 12000),
      sender_role: item.senderRole === "owner" ? "owner" : "friend",
      can_send_external: item.canSendExternal === true,
      received_at: Number.isFinite(receivedAtMs) && receivedAtMs > 0
        ? new Date(receivedAtMs).toISOString()
        : new Date().toISOString(),
    }),''',
        '''    body: JSON.stringify(bridgePayload),''',
        "bridge payload body",
    ),
])
