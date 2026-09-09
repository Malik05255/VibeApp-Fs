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
    '    const row = await normalizeMessage(message as Record<string, unknown>, runtimeSecret);',
    '    const row: any = await normalizeMessage(message as Record<string, unknown>, runtimeSecret);',
    'mutable normalized inbox row',
)

replace_once(
    '''          await db.from("h_runtime_inbox").update({
            status: "ignored",
            error: "unauthorized_sender",
            reply_text: null,
            processed_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
          }).eq("message_key", messageKey);''',
    '''          await db.from("h_runtime_inbox").update({
            body: BLOCKED_PEACH_BODY,
            raw: { source: "peach_blocked", redacted: true },
            status: "ignored",
            error: "unauthorized_sender",
            reply_text: null,
            processed_at: new Date().toISOString(),
            updated_at: new Date().toISOString(),
          }).eq("message_key", messageKey);''',
    'defense-in-depth redaction',
)

path.write_text(text)
