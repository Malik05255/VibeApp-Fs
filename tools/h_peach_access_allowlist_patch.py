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
    'const HISTORY_LIMIT = 14;\n',
    'const HISTORY_LIMIT = 14;\nconst BLOCKED_PEACH_BODY = "[blocked]";\n',
    "blocked body constant",
)

replace_once(
    '''    const row = await normalizeMessage(message as Record<string, unknown>, runtimeSecret);
    const { data, error } = await db.from("h_runtime_inbox")
      .upsert(row, { onConflict: "message_key", ignoreDuplicates: true })
      .select("message_key");
    if (error) throw error;
    if (Array.isArray(data) && data.length) inserted += 1;''',
    '''    const row = await normalizeMessage(message as Record<string, unknown>, runtimeSecret);
    const pairingFingerprint = storedOwnerPairingFingerprint(row.raw);
    const access = pairingFingerprint
      ? null
      : await resolvePeachDeliveryContext(db, row.contact_phone);
    const blocked = !pairingFingerprint && access?.allowed !== true;
    if (blocked) {
      row.body = BLOCKED_PEACH_BODY;
      row.raw = { source: "peach_blocked", redacted: true };
      row.status = "ignored";
      row.error = "unauthorized_sender";
      row.processed_at = new Date().toISOString();
    }

    const { data, error } = await db.from("h_runtime_inbox")
      .upsert(row, { onConflict: "message_key", ignoreDuplicates: true })
      .select("message_key");
    if (error) throw error;
    if (Array.isArray(data) && data.length) {
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
    }''',
    "block before persistence",
)

replace_once(
    '''      if (pairing === "not_pairing") {
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
            );''',
    '''      let delivery = null;
      if (pairing === "not_pairing") {
        delivery = await resolvePeachDeliveryContext(db, row.contact_phone);
        if (!delivery.allowed) {
          await db.from("h_runtime_inbox").update({
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
    "defense in depth before chat and AI",
)

path.write_text(text)
