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
    '''import {
  executeStoredFriendAccess,
  maybeExecuteFriendAccessCommand,
  redactFriendAccessForStorage,
  storedFriendAccessCommand,
} from "./friend-access.ts";
''',
    '''import {
  executeStoredFriendAccess,
  maybeExecuteFriendAccessCommand,
  parseFriendAccessCommand,
  redactFriendAccessForStorage,
  storedFriendAccessCommand,
} from "./friend-access.ts";
import {
  consumeFriendPairingFingerprint,
  redactFriendPairingForStorage,
  storedFriendPairingFingerprint,
} from "./friend-pairing.ts";
''',
    "friend pairing imports",
)

replace_once(
    '      return reply(await processChannelMessage(db, requestPayload), 200);',
    '      return reply(await processChannelMessage(db, requestPayload, String(config.secret_value)), 200);',
    "channel runtime secret",
)

replace_once(
    'async function processChannelMessage(db: any, payload: unknown) {',
    'async function processChannelMessage(db: any, payload: unknown, runtimeSecret: string) {',
    "channel function signature",
)

replace_once(
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
''',
    '''  const now = new Date();
  const friendPairingEnvelope = await redactFriendPairingForStorage(input.text, runtimeSecret);
  const friendAccessEnvelope = friendPairingEnvelope ? null : await redactFriendAccessForStorage(db, input.text);
  const row = {
    message_key: messageKey,
    peach_message_id: null,
    conversation_id: conversationId,
    contact_phone: input.waId,
    business_phone_number: null,
    direction: "inbound",
    message_type: channelMessageType(input.sourceType),
    body: friendPairingEnvelope?.body ?? friendAccessEnvelope?.body ?? input.text,
    source_created_at: input.receivedAt ?? now.toISOString(),
    raw: friendPairingEnvelope?.raw ?? friendAccessEnvelope?.raw ?? {
''',
    "channel friend pairing redaction",
)

replace_once(
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
    }
    await db.from("h_runtime_inbox").update({
      status: "processed",
      error: null,
      reply_text: response.reply || null,
      processed_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("message_key", messageKey);
    return { ok: true, duplicate: false, status: "processed", reply: response.reply || null };
''',
    '''  try {
    const storedFriendPairing = storedFriendPairingFingerprint(row.raw);
    const friendPairing = storedFriendPairing
      ? await consumeFriendPairingFingerprint(db, runtimeSecret, input.waId, storedFriendPairing)
      : "not_pairing";
    const parsedFriendAccess = friendPairing === "not_pairing" ? parseFriendAccessCommand(input.text) : null;
    const sensitiveFriendInvite = parsedFriendAccess?.action === "create_invite";
    const storedFriendAccess = friendPairing === "not_pairing" ? storedFriendAccessCommand(row.raw) : null;
    const friendAccessReply = friendPairing === "not_pairing"
      ? storedFriendAccess
        ? await executeStoredFriendAccess(db, storedFriendAccess, delivery)
        : await maybeExecuteFriendAccessCommand(db, userKey, input.text, delivery)
      : null;
    if (friendPairing === "not_pairing" && !friendAccessReply) {
      await appendChat(db, userKey, conversationId, "user", input.text, messageKey);
    }
    const response = friendPairing === "enrolled"
      ? { reply: "تم ربط هذا الرقم كصديق في H. يمكنك استخدام H من رسالتك القادمة." }
      : friendPairing === "invalid_or_expired"
        ? { reply: "رمز ربط الصديق غير صالح أو انتهت صلاحيته. اطلب من مالك H إنشاء كود دعوة جديد." }
        : friendAccessReply
          ? { reply: friendAccessReply }
          : await decideResponse(db, userKey, conversationId, input.text, now, delivery);
    if (response.reply && friendPairing === "not_pairing" && !friendAccessReply) {
      await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
    }
    await db.from("h_runtime_inbox").update({
      status: "processed",
      error: null,
      reply_text: sensitiveFriendInvite ? null : response.reply || null,
      processed_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }).eq("message_key", messageKey);
    return { ok: true, duplicate: false, status: "processed", reply: response.reply || null };
''',
    "channel friend pairing execution",
)

replace_once(
    '''    const row: any = await normalizeMessage(message as Record<string, unknown>, runtimeSecret);
    const pairingFingerprint = storedOwnerPairingFingerprint(row.raw);
    const access = pairingFingerprint
      ? null
      : await resolvePeachDeliveryContext(db, row.contact_phone);
    const friendAccessEnvelope = pairingFingerprint ? null : await redactFriendAccessForStorage(db, row.body);
''',
    '''    const row: any = await normalizeMessage(message as Record<string, unknown>, runtimeSecret);
    const ownerPairingFingerprint = storedOwnerPairingFingerprint(row.raw);
    const friendPairingFingerprint = ownerPairingFingerprint ? null : storedFriendPairingFingerprint(row.raw);
    const pairingBypass = Boolean(ownerPairingFingerprint || friendPairingFingerprint);
    const access = pairingBypass
      ? null
      : await resolvePeachDeliveryContext(db, row.contact_phone);
    const friendAccessEnvelope = pairingBypass ? null : await redactFriendAccessForStorage(db, row.body);
''',
    "poll pairing bypass",
)

replace_once(
    '''    const mediaFallback = !pairingFingerprint && access?.allowed === true && !friendAccessEnvelope
      ? peachUnsupportedMediaFallback(row.message_type, row.body)
      : null;
''',
    '''    const mediaFallback = !pairingBypass && access?.allowed === true && !friendAccessEnvelope
      ? peachUnsupportedMediaFallback(row.message_type, row.body)
      : null;
''',
    "media fallback pairing bypass",
)

replace_once(
    '''    const blocked = !pairingFingerprint && access?.allowed !== true;
''',
    '''    const blocked = !pairingBypass && access?.allowed !== true;
''',
    "blocked pairing bypass",
)

replace_once(
    '''      const storedPairingFingerprint = storedOwnerPairingFingerprint(row.raw);
      const pairing = storedPairingFingerprint
        ? await consumeOwnerPairingFingerprint(db, runtimeSecret, row.contact_phone, storedPairingFingerprint)
        : await consumeOwnerPairingCommand(db, row.contact_phone, body);
      let delivery = null;
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
              );
      if (response.reply) {
        await sendConversationReply(accessToken, conversationId, response.reply);
        if (pairing === "not_pairing" && !friendAccessReply) {
          await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
        }
      }
      await db.from("h_runtime_inbox").update({
        status: "processed",
        error: null,
        reply_text: response.reply || null,
        processed_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      }).eq("message_key", messageKey);
''',
    '''      const storedOwnerPairing = storedOwnerPairingFingerprint(row.raw);
      const ownerPairing = storedOwnerPairing
        ? await consumeOwnerPairingFingerprint(db, runtimeSecret, row.contact_phone, storedOwnerPairing)
        : await consumeOwnerPairingCommand(db, row.contact_phone, body);
      const storedFriendPairing = ownerPairing === "not_pairing" ? storedFriendPairingFingerprint(row.raw) : null;
      const friendPairing = storedFriendPairing
        ? await consumeFriendPairingFingerprint(db, runtimeSecret, row.contact_phone, storedFriendPairing)
        : "not_pairing";
      let delivery = null;
      let friendAccessReply: string | null = null;
      let sensitiveFriendInvite = false;
      if (ownerPairing === "not_pairing" && friendPairing === "not_pairing") {
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
        const parsedFriendAccess = parseFriendAccessCommand(body);
        sensitiveFriendInvite = parsedFriendAccess?.action === "create_invite";
        const storedFriendAccess = storedFriendAccessCommand(row.raw);
        friendAccessReply = storedFriendAccess
          ? await executeStoredFriendAccess(db, storedFriendAccess, delivery)
          : await maybeExecuteFriendAccessCommand(db, userKey, body, delivery);
        if (!friendAccessReply) {
          await appendChat(db, userKey, conversationId, "user", body, messageKey);
        }
      }
      const response = ownerPairing === "enrolled"
        ? { reply: "تم ربط هذا الرقم كمالك H. صلاحيات المالك مفعلة من رسالتك القادمة." }
        : ownerPairing === "invalid_or_expired"
          ? { reply: "رمز ربط المالك غير صالح أو انتهت صلاحيته. أنشئ رمز ربط جديد وحاول مرة أخرى." }
          : friendPairing === "enrolled"
            ? { reply: "تم ربط هذا الرقم كصديق في H. يمكنك استخدام H من رسالتك القادمة." }
            : friendPairing === "invalid_or_expired"
              ? { reply: "رمز ربط الصديق غير صالح أو انتهت صلاحيته. اطلب من مالك H إنشاء كود دعوة جديد." }
              : friendAccessReply
                ? { reply: friendAccessReply }
                : await decideResponse(
                    db,
                    userKey,
                    conversationId,
                    body,
                    now,
                    delivery!,
                  );
      if (response.reply) {
        await sendConversationReply(accessToken, conversationId, response.reply);
        if (ownerPairing === "not_pairing" && friendPairing === "not_pairing" && !friendAccessReply) {
          await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
        }
      }
      await db.from("h_runtime_inbox").update({
        status: "processed",
        error: null,
        reply_text: sensitiveFriendInvite ? null : response.reply || null,
        processed_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
      }).eq("message_key", messageKey);
''',
    "Peach friend pairing execution",
)

replace_once(
    '''  const originalBody = extractBody(message);
  const redactedPairing = await redactOwnerPairingForStorage(originalBody, runtimeSecret);
  return {
''',
    '''  const originalBody = extractBody(message);
  const redactedOwnerPairing = await redactOwnerPairingForStorage(originalBody, runtimeSecret);
  const redactedFriendPairing = redactedOwnerPairing
    ? null
    : await redactFriendPairingForStorage(originalBody, runtimeSecret);
  return {
''',
    "normalize friend pairing redaction",
)

replace_once(
    '''    body: redactedPairing?.body ?? originalBody,
    source_created_at: parseDateOrNull(created),
    raw: redactedPairing?.raw ?? message,
''',
    '''    body: redactedOwnerPairing?.body ?? redactedFriendPairing?.body ?? originalBody,
    source_created_at: parseDateOrNull(created),
    raw: redactedOwnerPairing?.raw ?? redactedFriendPairing?.raw ?? message,
''',
    "normalize pairing envelope selection",
)

path.write_text(text)
