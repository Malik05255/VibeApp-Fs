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
    'import { resolvePeachDeliveryContext } from "./owner-identity.ts";\nimport {\n  completeTask,',
    'import { resolvePeachDeliveryContext } from "./owner-identity.ts";\nimport { consumeOwnerPairingCommand } from "./owner-pairing.ts";\nimport {\n  completeTask,',
    "owner pairing import",
)

replace_once(
    '''      await appendChat(db, userKey, conversationId, "user", body, messageKey);\n      const delivery = await resolvePeachDeliveryContext(db, row.contact_phone);\n      const response = await decideResponse(db, userKey, conversationId, body, now, delivery);''',
    '''      await appendChat(db, userKey, conversationId, "user", body, messageKey);\n      const pairing = await consumeOwnerPairingCommand(db, row.contact_phone, body);\n      const response = pairing === "enrolled"\n        ? { reply: "تم ربط هذا الرقم كمالك H. صلاحيات المالك مفعلة من رسالتك القادمة." }\n        : pairing === "invalid_or_expired"\n          ? { reply: "رمز ربط المالك غير صالح أو انتهت صلاحيته. أنشئ رمز ربط جديد وحاول مرة أخرى." }\n          : await decideResponse(\n              db,\n              userKey,\n              conversationId,\n              body,\n              now,\n              await resolvePeachDeliveryContext(db, row.contact_phone),\n            );''',
    "Peach owner pairing command",
)

path.write_text(text)
