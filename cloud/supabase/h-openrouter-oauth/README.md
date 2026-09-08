# H Cloud OpenRouter OAuth

This Edge Function gives the WhatsApp/cloud runtime its own OpenRouter authorization. It does not copy the Android credential and it never commits an API key to GitHub.

## Security model

- OpenRouter OAuth uses PKCE S256.
- Setup links are one-time and expire after 10 minutes.
- Only the SHA-256 hash of each setup token is stored.
- OAuth state is stored only as a SHA-256 hash.
- The PKCE verifier and final OpenRouter API key are encrypted with AES-256-GCM before they enter Postgres.
- The AES key exists only in the Edge Function environment as `H_CREDENTIAL_ENCRYPTION_KEY`.
- RLS is enabled with no client policies; tables are service-role only.
- `/status` never returns an API key or ciphertext.
- If encryption is not configured, connect/callback fail closed rather than storing plaintext.

## Free-only model policy

H fetches OpenRouter's live model catalog and accepts a model only when every advertised numeric pricing field is exactly zero, including required `prompt` and `completion` pricing. `H_MODEL` is only a preference: if it is not zero-priced it is ignored. `openrouter/free` is preferred when it is present and zero-priced.

The WhatsApp runtime must repeat this validation before making a model request. If the catalog cannot prove that a model is zero-priced, H does not call a paid model automatically.

This prevents automatic paid fallback. It does not promise unlimited usage; OpenRouter/provider free-tier rate limits can still apply.

## Deployment prerequisites

1. Apply `schema.sql` to the same Supabase project used by the H Peach runtime.
2. Configure a random 32-byte encryption key as a **base64url** Edge Function secret named `H_CREDENTIAL_ENCRYPTION_KEY`.
3. Deploy `index.ts` as Edge Function `h-openrouter-oauth` with `verify_jwt=false` because the browser callback must be public.
4. Do not put the encryption key, OpenRouter key, setup URL token, Supabase service role key, or OAuth code in GitHub or chat.

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are supplied by the Supabase Edge runtime.

## Creating the one-time connect URL

`POST /setup-link` requires the existing `x-h-runtime-secret` used by the H cloud runtime. The function returns a short-lived `connectUrl`. Open that URL, approve OpenRouter, and the callback stores only the encrypted credential.

The setup endpoint is intentionally admin-only. A future authenticated owner UI can request this URL without exposing the runtime secret to normal users.

## Status

`GET /status` returns only non-sensitive state:

- connected / disconnected
- selected zero-priced model
- last model verification time
- whether the encryption secret is configured
- `paidModelFallback=false`

## Disconnect

`POST /disconnect` also requires `x-h-runtime-secret`. It deletes the cloud OpenRouter credential and pending OAuth state. It does not touch the Android app's local OpenRouter authorization.
