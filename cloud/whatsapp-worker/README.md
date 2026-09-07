# H WhatsApp Cloud Worker

Official WhatsApp Business Platform integration for the personal assistant H.

## Intended setup

Use the existing WhatsApp Business app number through Meta's supported **Coexistence / Embedded Signup** flow. The same business number can stay active in the WhatsApp Business app while Cloud API mirrors supported messages.

Do not place the phone number, access token, app secret, or Cloudflare credentials in source code.

## What this worker does

- Meta webhook verification (`GET /webhook`).
- Verifies `X-Hub-Signature-256` on every webhook event.
- Deduplicates inbound WhatsApp messages.
- Accepts text/button/list replies from authorized controller numbers.
- Creates cloud reminders from simple Arabic/English relative-time commands.
- Optionally uses H through OpenRouter for broader natural-language reminder parsing.
- Runs a Cloudflare cron every minute to deliver due reminders.
- Sends normal text inside WhatsApp's customer-service window.
- Uses an approved reminder template outside the customer-service window.
- Stores contacts, inbound-message ids, and scheduled jobs in D1.

## Required GitHub Secrets for deployment

Cloudflare:

- `CLOUDFLARE_API_TOKEN`
- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_D1_DATABASE_ID`

Meta / WhatsApp:

- `WHATSAPP_VERIFY_TOKEN` — any long random secret you choose; use the same value when configuring the Meta webhook.
- `WHATSAPP_APP_SECRET`
- `WHATSAPP_ACCESS_TOKEN`
- `WHATSAPP_PHONE_NUMBER_ID`
- `META_GRAPH_VERSION` — for example the Graph API version currently shown by Meta for the WhatsApp app.

Recommended:

- `CONTROL_WA_IDS` — comma-separated personal WhatsApp numbers allowed to control H, digits only with country code.
- `WHATSAPP_REMINDER_TEMPLATE_NAME` — approved utility template for reminders outside the 24-hour service window.
- `OPENROUTER_API_KEY`
- `H_MODEL`

The Worker variable `WHATSAPP_REMINDER_TEMPLATE_LANGUAGE` defaults to `ar` and `DEFAULT_TIME_ZONE` defaults to `Asia/Riyadh`.

## D1

Create a D1 database named `h-whatsapp`, then save its id in the GitHub secret `CLOUDFLARE_D1_DATABASE_ID`.

The deployment workflow automatically applies `schema.sql` before deploying.

## Deploy

Run GitHub Actions → **Deploy H WhatsApp Worker** → Run workflow.

After deployment:

1. Open the Worker's `/health` endpoint and confirm `metaConfigured=true`.
2. In Meta WhatsApp configuration, set the callback URL to `https://<worker-domain>/webhook`.
3. Use the same value from `WHATSAPP_VERIFY_TOKEN` as the webhook verify token.
4. Subscribe the app to WhatsApp message webhook events.
5. Complete the WhatsApp Business app coexistence/embedded-signup flow for the number already active in the WhatsApp Business app.
6. Send a message from an authorized controller number to the H WhatsApp Business number.

## Reminder template

WhatsApp Cloud API cannot send arbitrary free-form messages outside the customer-service window. Create an approved template, preferably utility-category, whose body contains one variable, for example:

`تذكير من H: {{1}}`

Then store its exact template name in `WHATSAPP_REMINDER_TEMPLATE_NAME`.

## Security

- Webhook bodies are rejected unless the Meta signature is valid.
- Secrets are Worker/GitHub secrets, never committed.
- `CONTROL_WA_IDS` can restrict who is allowed to control H.
- Duplicate WhatsApp message ids are ignored.
