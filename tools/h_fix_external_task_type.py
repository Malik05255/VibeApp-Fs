from pathlib import Path
p = Path('cloud/supabase/h-whatsapp-inbox/index.ts')
text = p.read_text()
old = '        taskType: "external_message",\n'
new = '        taskType: "reminder",\n'
if text.count(old) != 1:
    raise SystemExit(f'expected one external task type, found {text.count(old)}')
p.write_text(text.replace(old, new, 1))
