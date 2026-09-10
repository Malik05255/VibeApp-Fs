# H WhatsApp Cloud Runtime

Official WhatsApp Business Platform runtime for the personal assistant **H**.

This path is intentionally **cloud-only**: once the WhatsApp number has been migrated/registered for the official Cloud API and the webhook test passes, H does not depend on the Android app or WhatsApp Business app being open to receive commands.

> Important: do **not** uninstall WhatsApp Business yet. First finish the Cloud API migration/registration and verify an end-to-end inbound + reply test. Removing the mobile app before that can break the current Coexistence connection.

## Architecture

`WhatsApp -> Meta Cloud API webhook -> H Cloud Runtime -> D1 + unified H cloud memory/tasks -> H model -> Meta Cloud API reply`

The Android app becomes a thin client. Peach can remain an optional management/MCP integration, but it is not the inbound trigger for this runtime.

## What the Worker does

- Verifies Meta webhook subscription (`GET /webhook`).
- Verifies `X-Hub-Signature-256` for every inbound webhook event.
- Deduplicates WhatsApp message IDs.
- Receives text, button/list replies, shared locations, voice/audio, supported images, and supported documents.
- Downloads official WhatsApp media using the Meta Cloud API access token.
- Transcribes voice through an OpenAI-compatible transcription endpoint (Groq Whisper by default when configured).
- Routes images/documents to the H Supabase media bridge, then feeds only extracted/understood text into unified H memory/tasks/reminders.
- Maintains isolated conversation context per WhatsApp user.
- Keeps durable H memory items per user so ideas/notes can be recalled later.
- Creates and lists cloud reminders.
- Runs a Cloudflare cron every minute to deliver due reminders.
- Keeps owner/friend permissions separate:
  - owner numbers (`CONTROL_WA_IDS`) may use third-party messaging actions;
  - friend numbers (`H_ALLOWED_WA_IDS`) may use H but cannot send to arbitrary WhatsApp numbers;
  - unknown numbers are denied unless `ALLOW_UNKNOWN_USERS=true` is explicitly set.
- Saves named contacts per owner, preventing one user's contacts/memory/reminders from leaking to another.
- Enforces WhatsApp's messaging window. Outside the service window it only sends through an approved template; it does not silently bypass Meta policy.

## Supported media

H currently accepts these inbound media types through the official Meta media path:

- Images: JPEG, PNG, WebP.
- Documents: PDF.
- Small text documents: plain text, CSV, Markdown, JSON, XML.

Safety/resource limits:

- Maximum media payload: **8 MiB**.
- Text-document analysis limit: **512 KiB**.
- Unsupported binary Office/ZIP formats fail closed until a real parser is integrated.
- SVG is not forwarded to a vision model.
- Raw image/document bytes are **not intentionally persisted** into H chat or memory; the unified H pipeline receives only the extracted description/text plus filename/caption context.

### Free-only media policy

Media understanding follows the same H free-service rule:

- The live OpenRouter model catalog is checked before analysis.
- A vision model is eligible only when its catalog metadata says it accepts image input and **every reported pricing field is zero**.
- If there is no strictly zero-priced compatible model, H refuses the analysis instead of silently using a paid model.
- PDF parsing explicitly requests OpenRouter's `file-parser` with the free `cloudflare-ai` PDF engine. It does not allow the default paid OCR parser to be selected silently.
- A failed/unsupported analysis never becomes an action or memory based on guessed content.

## Required GitHub Secrets

### Cloudflare

- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_D1_DATABASE_ID`

### Meta / WhatsApp Cloud API

- `WHATSAPP_VERIFY_TOKEN` — a long random value; configure the same value in Meta webhook settings.
- `WHATSAPP_APP_SECRET`
- `WHATSAPP_ACCESS_TOKEN`
- `WHATSAPP_PHONE_NUMBER_ID`
- `META_GRAPH_VERSION`
- `CONTROL_WA_IDS` — owner WhatsApp numbers, comma-separated, digits only with country code.

The deployment deliberately fails closed: without an owner allowlist, nobody can control H.

## H bridge / AI configuration

For unified voice/media processing the Worker also needs:

- `H_RUNTIME_SECRET`
- `H_SUPABASE_VOICE_URL` — normally the deployed `h-whatsapp-inbox` endpoint.
- `H_SUPABASE_MEDIA_URL` — optional explicit `h-whatsapp-media` endpoint. If omitted, the Worker derives it from a standard `H_SUPABASE_VOICE_URL` ending in `/h-whatsapp-inbox`.

The Supabase runtime needs its OpenRouter credential through H's encrypted OAuth configuration or the supported server-side legacy environment path. Media analysis never embeds an API key in the Android APK.

### Optional standby failover configuration

A separate, fully provisioned H Standby can be exposed to the Worker with both of these GitHub Actions secrets:

- `H_STANDBY_SUPABASE_VOICE_URL` — the Standby `h-whatsapp-inbox` endpoint.
- `H_STANDBY_RUNTIME_SECRET` — the Standby runtime authentication secret stored only server-side.

Do **not** create `H_STANDBY_FAILOVER_ENABLED` manually. The deploy workflow owns that Worker secret and rewrites it on every deployment: it becomes `true` only when both Standby values above are present; otherwise it is explicitly written as `false`. This prevents stale Worker configuration from continuing to route traffic to a removed or partially configured Standby.

Even when the flag is enabled, the router does not switch merely because a URL exists. It requires current Standby preflight health, rechecks Primary health after a short confirmation delay, performs request-only promotion before execution, verifies active promotion attestation, and never automatically fails back. Once an execution POST has started, the same message is never retried against another runtime.

## Optional Secrets

- `H_ALLOWED_WA_IDS` — friend/user numbers allowed to use H without external-send permission.
- `OPENROUTER_API_KEY`
- `H_MODEL`
- `H_SYSTEM_PROMPT`
- `GROQ_API_KEY` — default voice transcription provider.
- `TRANSCRIPTION_API_KEY` — generic alternative to `GROQ_API_KEY` for the configured OpenAI-compatible transcription endpoint.
- `WHATSAPP_REMINDER_TEMPLATE_NAME` — approved WhatsApp template used when a due reminder must be sent outside the customer-service window.

## Worker defaults

`wrangler.toml` sets:

- `DEFAULT_TIME_ZONE = Asia/Riyadh`
- `ALLOW_UNKNOWN_USERS = false`
- `TRANSCRIPTION_API_URL = https://api.groq.com/openai/v1/audio/transcriptions`
- `TRANSCRIPTION_MODEL = whisper-large-v3-turbo`
- reminder template language = Arabic

Model/provider credentials are secrets and are never committed.

## D1

Create a D1 database named `h-whatsapp` and save its ID in `CLOUDFLARE_D1_DATABASE_ID`.

`schema.sql` contains:

- inbound-message dedupe
- contact/service-window state
- scheduled jobs
- named contacts
- isolated conversation history
- durable memory items

The deploy workflow applies the schema automatically.

## Deployment

GitHub Actions -> **Deploy H WhatsApp Cloud Runtime** -> Run workflow.

The workflow also auto-runs after Cloud Runtime changes are merged to `main`.

After deployment:

1. Open `https://<worker-domain>/health`.
2. Do not continue until `runtimeReady=true`.
3. Confirm `voiceConfigured=true` before Voice Note E2E testing.
4. Confirm `mediaConfigured=true` before image/document E2E testing.
5. In Meta WhatsApp configuration, set callback URL to `https://<worker-domain>/webhook`.
6. Use the exact `WHATSAPP_VERIFY_TOKEN` value as the webhook verify token.
7. Subscribe the app to WhatsApp `messages` webhook events.
8. Send a text from the owner number to H and confirm the Worker receives it and replies while Android H is closed.
9. Send a voice note and verify transcription + unified H execution.
10. Send a supported image with a caption such as `وش في الصورة؟` and verify H answers from the actual media.
11. Send a PDF/text file and verify H extracts/understands only supported content.
12. Confirm an unsupported/oversized file is rejected without a paid fallback.
13. If a Standby has been configured, confirm `/health` reports `standbyConfigured=true` and `standbyFailoverEnabled=true`; otherwise both must remain false/disabled.
14. Only after the number is running as the intended official Cloud API channel and the above tests pass should the mobile WhatsApp Business app be removed.

## Cloud-only vs Coexistence

The earlier Peach Co-Pilot setup was useful for testing H's MCP access, but Coexistence is not the final dependency for a user who wants to remove the WhatsApp Business app.

The target production design is Cloud API webhook delivery directly to H's backend. Peach may still be kept as an optional MCP/operations layer if desired; H must not require it to wake up on an inbound WhatsApp message.

## Voice

For an inbound WhatsApp audio message H:

1. receives the official media ID from Meta;
2. downloads the media using the Cloud API access token;
3. sends the audio to the configured transcription endpoint;
4. feeds only the transcript into H's unified command pipeline.

The Worker does not intentionally persist raw audio files.

## Images and documents

For an inbound supported image/document H:

1. receives the official media ID from Meta;
2. obtains media metadata and downloads bytes with the Meta access token;
3. enforces size/MIME limits before forwarding;
4. sends the media to the authenticated `h-whatsapp-media` Supabase function;
5. chooses a strictly zero-priced compatible analysis path or fails closed;
6. passes only the resulting description/extracted text and user caption into unified H processing;
7. returns the unified H reply through Meta.

A real Meta image/PDF E2E test is still required after deployment Secrets are configured before production readiness can be claimed.

## Reminders and proactive messages

WhatsApp does not allow arbitrary free-form proactive messages at all times. If a reminder is due outside the allowed service window, H requires an approved WhatsApp template. Without one, the task moves to `WAITING_TEMPLATE` rather than pretending the message was sent.

## Security and privacy

- Meta webhook signatures are mandatory.
- Duplicate message IDs are ignored.
- Credentials stay in Worker/GitHub/Supabase secrets.
- Every memory item, chat context, reminder, and named contact is keyed by the originating WhatsApp user.
- Friend accounts cannot use H to message arbitrary third-party numbers.
- Unknown numbers are denied by default.
- Media input is size/MIME validated and raw bytes are not intentionally persisted in unified H history.
- H does not expose chain-of-thought; it stores operational conversation content and task state only.
