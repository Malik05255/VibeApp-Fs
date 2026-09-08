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
    'import { sendFreePeachContactMessage } from "./peach-contact-delivery.ts";\nimport {\n  completeTask,',
    'import { sendFreePeachContactMessage } from "./peach-contact-delivery.ts";\nimport { resolvePeachDeliveryContext } from "./owner-identity.ts";\nimport {\n  completeTask,',
    "owner resolver import",
)

replace_once(
    '''      await appendChat(db, userKey, conversationId, "user", body, messageKey);\n      const response = await decideResponse(db, userKey, conversationId, body, now);''',
    '''      await appendChat(db, userKey, conversationId, "user", body, messageKey);\n      const delivery = await resolvePeachDeliveryContext(db, row.contact_phone);\n      const response = await decideResponse(db, userKey, conversationId, body, now, delivery);''',
    "Peach message delivery context",
)

replace_once(
    '''    canSendExternal\n      ? "This authenticated Meta sender may save contacts and send/schedule messages to saved contacts."\n      : "This sender may not save contacts for external delivery or message third-party WhatsApp numbers.",''',
    '''    canSendExternal\n      ? "This authenticated H owner channel may save contacts and send/schedule messages to saved contacts."\n      : "This sender may not save contacts for external delivery or message third-party WhatsApp numbers.",''',
    "AI owner capability wording",
)

path.write_text(text)
