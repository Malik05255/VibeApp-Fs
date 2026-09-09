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
  redactFriendAccessForStorage,
  storedFriendAccessCommand,
} from "./friend-access.ts";
import {
  peachUnsupportedMediaEnvelope,
  peachUnsupportedMediaFallback,
} from "./peach-media-fallback.ts";
''',
    "Peach media fallback imports",
)

replace_once(
    '''        last_inserted_count: poll.inserted,
        last_processed_count: processed.processed,''',
    '''        last_inserted_count: poll.inserted,
        last_unsupported_media_count: poll.unsupportedMedia,
        last_processed_count: processed.processed,''',
    "runtime state unsupported media count",
)

replace_once(
    '''      inserted: poll.inserted,
      processed: processed.processed,''',
    '''      inserted: poll.inserted,
      unsupportedMedia: poll.unsupportedMedia,
      processed: processed.processed,''',
    "response unsupported media count",
)

replace_once(
    '''  let inserted = 0;
  let seen = 0;
  for (const message of messages) {''',
    '''  let inserted = 0;
  let seen = 0;
  let unsupportedMedia = 0;
  for (const message of messages) {''',
    "poll unsupported media counter",
)

replace_once(
    '''    if (friendAccessEnvelope) {
      row.body = friendAccessEnvelope.body;
      row.raw = friendAccessEnvelope.raw;
    }
    const blocked = !pairingFingerprint && access?.allowed !== true;
    if (blocked) {''',
    '''    if (friendAccessEnvelope) {
      row.body = friendAccessEnvelope.body;
      row.raw = friendAccessEnvelope.raw;
    }
    const mediaFallback = !pairingFingerprint && access?.allowed === true && !friendAccessEnvelope
      ? peachUnsupportedMediaFallback(row.message_type, row.body)
      : null;
    if (mediaFallback) {
      const envelope = peachUnsupportedMediaEnvelope(mediaFallback.kind, row.peach_message_id);
      row.body = envelope.body;
      row.raw = envelope.raw;
      row.status = "ignored";
      row.error = "peach_media_reference_unavailable";
      row.processed_at = new Date().toISOString();
    }
    const blocked = !pairingFingerprint && access?.allowed !== true;
    if (blocked) {''',
    "authorized unsupported media redaction",
)

replace_once(
    '''    if (Array.isArray(data) && data.length) {
      inserted += 1;
      if (blocked && Number.isInteger(Number(row.conversation_id)) && Number(row.conversation_id) > 0) {
        try {
          await sendConversationReply(
            accessToken,
            Number(row.conversation_id),
            "هذا الرقم غير مصرح له باستخدام H. اطلب من مالك H إضافتك أولاً.",
          );
        } catch (deliveryError) {
          console.error("Could not deliver blocked Peach access reply", deliveryError);
        }
      }
    }
  }
  return { seen, inserted, from: fromDate.toISOString(), to: now.toISOString() };''',
    '''    if (Array.isArray(data) && data.length) {
      inserted += 1;
      if (blocked && Number.isInteger(Number(row.conversation_id)) && Number(row.conversation_id) > 0) {
        try {
          await sendConversationReply(
            accessToken,
            Number(row.conversation_id),
            "هذا الرقم غير مصرح له باستخدام H. اطلب من مالك H إضافتك أولاً.",
          );
        } catch (deliveryError) {
          console.error("Could not deliver blocked Peach access reply", deliveryError);
        }
      } else if (mediaFallback && Number.isInteger(Number(row.conversation_id)) && Number(row.conversation_id) > 0) {
        unsupportedMedia += 1;
        try {
          await sendConversationReply(accessToken, Number(row.conversation_id), mediaFallback.reply);
        } catch (deliveryError) {
          console.error("Could not deliver Peach unsupported media reply", deliveryError);
        }
      }
    }
  }
  return { seen, inserted, unsupportedMedia, from: fromDate.toISOString(), to: now.toISOString() };''',
    "send one-time media fallback reply",
)

path.write_text(text)
