# H WhatsApp Cloud Runtime

Official WhatsApp Business Platform runtime for the personal assistant **H**.

This path is intentionally **cloud-only**: once the WhatsApp number has been migrated/registered for the official Cloud API and the webhook test passes, H does not depend on the Android app or WhatsApp Business app being open to receive commands.

> Important: do **not** uninstall WhatsApp Business yet. First finish the Cloud API migration/registration and verify an end-to-end inbound + reply test. Removing the mobile app before that can break the current Coexistence connection.

## Architecture

`WhatsApp -> Meta Cloud API webhook -> H Cloud Runtime -> D1 memory/tasks -> H model -> Meta Cloud API reply`

The Android app becomes a thin client. Peach can remain an optional management/MCP integration, but it is not the inbound trigger for this runtime.

## What the Worker does

- Verifies Meta webhook subscription (`GET /webhook`).
- Verifies `X-Hub-Signature-256` for every inbound webhook event.
- Deduplicates WhatsApp message IDs.
- Receives text, button/list replies, shared locations, and voice/audio.
- Downloads official WhatsApp media and can transcribe voice through an OpenAI-compatible transcription endpoint (Groq Whisper by default when configured).
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
3. In Meta WhatsApp configuration, set callback URL to `https://<worker-domain>/webhook`.
4. Use the exact `WHATSAPP_VERIFY_TOKEN` value as the webhook verify token.
5. Subscribe the app to WhatsApp `messages` webhook events.
6. Send a text from the owner number to H.
7. Confirm the Worker receives it and H replies while the Android H app is closed.
8. Send a voice note and confirm transcription if a transcription key is configured.
9. Only after the number is running as the intended official Cloud API channel and the above tests pass should the mobile WhatsApp Business app be removed.

## Cloud-only vs Coexistence

The earlier Peach Co-Pilot setup was useful for testing H's MCP access, but Coexistence is not the final dependency for a user who wants to remove the WhatsApp Business app.

The target production design is Cloud API webhook delivery directly to H's backend. Peach may still be kept as an optional MCP/operations layer if desired; H must not require it to wake up on an inbound WhatsApp message.

## Voice

For an inbound WhatsApp audio message H:

1. receives the official media ID from Meta;
2. downloads the media using the Cloud API access token;
3. sends the audio to the configured transcription endpoint;
4. feeds only the transcript into H's command pipeline.

The Worker does not intentionally persist raw audio files.

## Reminders and proactive messages

WhatsApp does not allow arbitrary free-form proactive messages at all times. If a reminder is due outside the allowed service window, H requires an approved WhatsApp template. Without one, the task moves to `WAITING_TEMPLATE` rather than pretending the message was sent.

## Security and privacy

- Meta webhook signatures are mandatory.
- Duplicate message IDs are ignored.
- Credentials stay in Worker/GitHub secrets.
- Every memory item, chat context, reminder, and named contact is keyed by the originating WhatsApp user.
- Friend accounts cannot use H to message arbitrary third-party numbers.
- Unknown numbers are denied by default.
- H does not expose chain-of-thought; it stores operational conversation content and task state only.
