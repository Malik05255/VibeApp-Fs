from pathlib import Path

path = Path("cloud/supabase/h-whatsapp-inbox/index.ts")
text = path.read_text()

def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    text = text.replace(old, new, 1)

replace_once(
    'import { resolvePeachDeliveryContext } from "./owner-identity.ts";\n',
    'import { resolvePeachDeliveryContext } from "./owner-identity.ts";\nimport {\n  executeStoredFriendAccess,\n  maybeExecuteFriendAccessCommand,\n  redactFriendAccessForStorage,\n  storedFriendAccessCommand,\n} from "./friend-access.ts";\n',
    "friend access imports",
)

replace_once(
    '''  const now = new Date();
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
  };''',
    '''  const now = new Date();
  const friendAccessEnvelope = await redactFriendAccessForStorage(db, input.text);
  const row = {
    message_key: messageKey,
    peach_message_id: null,
    conversation_id: conversationId,
    contact_phone: input.waId,
    business_phone_number: null,
    direction: "inbound",
    message_type: channelMessageType(input.sourceType),
    body: friendAccessEnvelope?.body ?? input.text,
    source_created_at: input.receivedAt ?? now.toISOString(),
    raw: friendAccessEnvelope?.raw ?? {
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
  };''',
    "meta friend access redaction",
)

replace_once(
    '''  try {
    await appendChat(db, userKey, conversationId, "user", input.text, messageKey);
    const response = await decideResponse(db, userKey, conversationId, input.text, now, delivery);
    if (response.reply) {
      await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
    }''',
    '''  try {
    const storedFriendAccess = storedFriendAccessCommand(row.raw);
    const friendAccessReply = storedFriendAccess
      ? await executeStoredFriendAccess(db, storedFriendAccess, delivery)
      : await maybeExecuteFriendAccessCommand(db, userKey, input.text, delivery);
    if (!friendAccessReply) {
      await appendChat(db, userKey, conversationId, "user", input.text, messageKey);
    }
    const response = friendAccessReply
      ? { reply: friendAccessReply }
      : await decideResponse(db, userKey, conversationId, input.text, now, delivery);
    if (response.reply && !friendAccessReply) {
      await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
    }''',
    "meta friend access execution",
)

replace_once(
    '''    const pairingFingerprint = storedOwnerPairingFingerprint(row.raw);
    const access = pairingFingerprint
      ? null
      : await resolvePeachDeliveryContext(db, row.contact_phone);
    const blocked = !pairingFingerprint && access?.allowed !== true;
    if (blocked) {''',
    '''    const pairingFingerprint = storedOwnerPairingFingerprint(row.raw);
    const access = pairingFingerprint
      ? null
      : await resolvePeachDeliveryContext(db, row.contact_phone);
    const friendAccessEnvelope = pairingFingerprint ? null : await redactFriendAccessForStorage(db, row.body);
    if (friendAccessEnvelope) {
      row.body = friendAccessEnvelope.body;
      row.raw = friendAccessEnvelope.raw;
    }
    const blocked = !pairingFingerprint && access?.allowed !== true;
    if (blocked) {''',
    "peach friend access redaction",
)

replace_once(
    '''      let delivery = null;
      if (pairing === "not_pairing") {
        delivery = await resolvePeachDeliveryContext(db, row.contact_phone);
        if (!delivery.allowed) {
          await db.from("h_runtime_inbox").update({
            body: BLOCKED_PEACH_BODY,
            raw: { source: "peach_blocked", redacted: true },
            status: "ignored",
            error: "unauthorized_sender",
            reply_text: null,
            processed_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
          }).eq("message_key", messageKey);
          ignored += 1;
          continue;
        }
        await appendChat(db, userKey, conversationId, "user", body, messageKey);
      }
      const response = pairing === "enrolled"
        ? { reply: "تم ربط هذا الرقم كمالك H. صلاحيات المالك مفعلة من رسالتك القادمة." }
        : pairing === "invalid_or_expired"
          ? { reply: "رمز ربط المالك غير صالح أو انتهت صلاحيته. أنشئ رمز ربط جديد وحاول مرة أخرى." }
          : await decideResponse(
              db,
              userKey,
              conversationId,
              body,
              now,
              delivery!,
            );''',
    '''      let delivery = null;
      let friendAccessReply: string | null = null;
      if (pairing === "not_pairing") {
        delivery = await resolvePeachDeliveryContext(db, row.contact_phone);
        if (!delivery.allowed) {
          await db.from("h_runtime_inbox").update({
            body: BLOCKED_PEACH_BODY,
            raw: { source: "peach_blocked", redacted: true },
            status: "ignored",
            error: "unauthorized_sender",
            reply_text: null,
            processed_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
          }).eq("message_key", messageKey);
          ignored += 1;
          continue;
        }
        const storedFriendAccess = storedFriendAccessCommand(row.raw);
        friendAccessReply = storedFriendAccess
          ? await executeStoredFriendAccess(db, storedFriendAccess, delivery)
          : await maybeExecuteFriendAccessCommand(db, userKey, body, delivery);
        if (!friendAccessReply) {
          await appendChat(db, userKey, conversationId, "user", body, messageKey);
        }
      }
      const response = pairing === "enrolled"
        ? { reply: "تم ربط هذا الرقم كمالك H. صلاحيات المالك مفعلة من رسالتك القادمة." }
        : pairing === "invalid_or_expired"
          ? { reply: "رمز ربط المالك غير صالح أو انتهت صلاحيته. أنشئ رمز ربط جديد وحاول مرة أخرى." }
          : friendAccessReply
            ? { reply: friendAccessReply }
            : await decideResponse(
                db,
                userKey,
                conversationId,
                body,
                now,
                delivery!,
              );''',
    "peach friend access execution",
)

replace_once(
    '''        if (pairing === "not_pairing") {
          await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
        }''',
    '''        if (pairing === "not_pairing" && !friendAccessReply) {
          await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
        }''',
    "peach friend access chat suppression",
)

path.write_text(text)
