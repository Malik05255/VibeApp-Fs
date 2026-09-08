# H WhatsApp Voice — unified runtime

WhatsApp voice uses the same H identity and Supabase memory/task runtime as text, rather than creating a second assistant inside the Cloudflare Worker.

Flow:
1. Official Meta webhook receives a WhatsApp Voice Note and its media id.
2. Cloudflare Worker downloads the media with the official Meta access token.
3. The configured transcription endpoint converts audio to text. There is no automatic paid transcription fallback.
4. Worker sends only the transcript + WhatsApp user id + message id to `h-whatsapp-inbox` using `x-h-runtime-secret`.
5. Supabase runs the existing H memory/task/reminder/AI decision path and returns the reply.
6. Worker sends the reply through the official Meta WhatsApp API.
7. The Meta WhatsApp message id is used as an idempotency key so webhook retries do not execute the same voice request twice.

## Worker secrets

Required for the unified voice bridge; values never belong in Git:
- `H_SUPABASE_VOICE_URL` — deployed `h-whatsapp-inbox` Edge Function URL.
- `H_RUNTIME_SECRET` — must equal the existing `poll_secret` expected by the H Supabase runtime.
- `TRANSCRIPTION_API_KEY` or `GROQ_API_KEY` — one configured STT credential.
- Existing Meta secrets: `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `META_GRAPH_VERSION`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN`.

## Supabase Edge Function secrets for future reminders created by voice

The unified H reminder remains in Supabase. To deliver a reminder back through Meta, configure:
- `META_ACCESS_TOKEN` (or `WHATSAPP_ACCESS_TOKEN`)
- `WA_PHONE_NUMBER_ID` (or `WHATSAPP_PHONE_NUMBER_ID`)
- `WHATSAPP_API_VERSION` (or `META_GRAPH_VERSION`)
- Optional approved template: `WHATSAPP_REMINDER_TEMPLATE_NAME`, `WHATSAPP_REMINDER_TEMPLATE_LANGUAGE`

Paid/template fallback is disabled by default. It is used only when `H_ALLOW_PAID_WHATSAPP_TEMPLATE=true` is explicitly configured. Otherwise an out-of-window reminder remains `waiting_template` instead of silently causing a paid fallback.

## Honest capability boundary

If Meta media access, transcription credentials, the Supabase bridge URL, or the runtime secret is unavailable, H reports voice as unavailable and does not pretend to have understood or executed the Voice Note. Real end-to-end voice capability is not considered verified until a real Meta Voice Note passes webhook -> media download -> STT -> unified H -> WhatsApp reply.
