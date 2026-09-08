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
    'import { consumeOwnerPairingCommand } from "./owner-pairing.ts";',
    '''import {
  consumeOwnerPairingCommand,
  consumeOwnerPairingFingerprint,
  redactOwnerPairingForStorage,
  storedOwnerPairingFingerprint,
} from "./owner-pairing.ts";''',
    "pairing imports",
)

replace_once(
    '    const poll = await pollPeachInbox(db, credentials.access_token, now);\n    const processed = await processNewMessages(db, credentials.access_token, now);',
    '    const runtimeSecret = String(config.secret_value);\n    const poll = await pollPeachInbox(db, credentials.access_token, now, runtimeSecret);\n    const processed = await processNewMessages(db, credentials.access_token, now, runtimeSecret);',
    "runtime secret handoff",
)

replace_once(
    'async function pollPeachInbox(db: any, accessToken: string, now: Date) {',
    'async function pollPeachInbox(db: any, accessToken: string, now: Date, runtimeSecret: string) {',
    "poll signature",
)

replace_once(
    '    const row = await normalizeMessage(message as Record<string, unknown>);',
    '    const row = await normalizeMessage(message as Record<string, unknown>, runtimeSecret);',
    "normalize call",
)

replace_once(
    'async function processNewMessages(db: any, accessToken: string, now: Date) {',
    'async function processNewMessages(db: any, accessToken: string, now: Date, runtimeSecret: string) {',
    "process signature",
)

replace_once(
    '''      await appendChat(db, userKey, conversationId, "user", body, messageKey);
      const pairing = await consumeOwnerPairingCommand(db, row.contact_phone, body);
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
              await resolvePeachDeliveryContext(db, row.contact_phone),
            );
      if (response.reply) {
        await sendConversationReply(accessToken, conversationId, response.reply);
        await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
      }''',
    '''      const storedPairingFingerprint = storedOwnerPairingFingerprint(row.raw);
      const pairing = storedPairingFingerprint
        ? await consumeOwnerPairingFingerprint(db, runtimeSecret, row.contact_phone, storedPairingFingerprint)
        : await consumeOwnerPairingCommand(db, row.contact_phone, body);
      if (pairing === "not_pairing") {
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
              await resolvePeachDeliveryContext(db, row.contact_phone),
            );
      if (response.reply) {
        await sendConversationReply(accessToken, conversationId, response.reply);
        if (pairing === "not_pairing") {
          await appendChat(db, userKey, conversationId, "assistant", response.reply, messageKey);
        }
      }''',
    "pairing chat exclusion",
)

replace_once(
    'async function normalizeMessage(message: Record<string, unknown>) {',
    'async function normalizeMessage(message: Record<string, unknown>, runtimeSecret: string) {',
    "normalize signature",
)

replace_once(
    '''  const created = firstString(message.created_at, message.timestamp, message.sent_at, message.received_at);
  return {
    message_key: messageKey,''',
    '''  const created = firstString(message.created_at, message.timestamp, message.sent_at, message.received_at);
  const originalBody = extractBody(message);
  const redactedPairing = await redactOwnerPairingForStorage(originalBody, runtimeSecret);
  return {
    message_key: messageKey,''',
    "prepare pairing redaction",
)

replace_once(
    '''    body: extractBody(message),
    source_created_at: parseDateOrNull(created),
    raw: message,''',
    '''    body: redactedPairing?.body ?? originalBody,
    source_created_at: parseDateOrNull(created),
    raw: redactedPairing?.raw ?? message,''',
    "persist redacted pairing envelope",
)

path.write_text(text)
