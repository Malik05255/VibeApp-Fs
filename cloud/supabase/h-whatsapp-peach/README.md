# H Supabase + Peach Cloud Bootstrap

This is the lightweight bootstrap path for connecting the personal assistant **H** to Peach from a cloud backend without putting Peach OAuth tokens in the Android APK.

## Why this exists

The Android H app already proves that Peach MCP OAuth + PKCE works. This runtime moves the long-lived Peach authorization into a server-side Supabase Edge Function so H can continue operating when the Android app is closed and the WhatsApp Business mobile app is later removed.

This does **not** touch existing application tables. All storage uses the `h_runtime_*` prefix and is service-role only behind RLS.

## Flow

`User opens one-time setup URL -> Peach OAuth DCR + PKCE -> Supabase Edge Function callback -> tokens stored server-side -> MCP tools discovered and cached`

After authorization, the runtime exposes a non-sensitive status endpoint with:

- whether Peach is connected
- token expiry time
- MCP resource URL
- MCP tool count
- discovered tool names

The next implementation stage should use the real discovered tool schemas rather than guessing Peach capabilities.

## Deployment

1. Apply `schema.sql` to the target Supabase project.
2. Deploy `index.ts` as Edge Function `h-whatsapp-peach` with `verify_jwt=false`.
3. Keep the endpoint public only because OAuth needs a browser callback. Sensitive operations remain protected by one-time setup state and service-role-only database access.
4. Insert a one-time setup token into `h_runtime_setup_links` with a short expiry.
5. Open:

   `https://<project>.supabase.co/functions/v1/h-whatsapp-peach/connect?setup=<one-time-token>`

6. Approve Peach access.
7. Verify `/status` reports `connected=true` and inspect the discovered tool names.

## Security

- OAuth uses dynamic client registration + PKCE + state validation.
- OAuth state expires after 10 minutes.
- Setup links expire and become single-use.
- Peach access/refresh tokens are never returned by the status endpoint.
- RLS is enabled on all `h_runtime_*` tables with no client policies; Edge Functions use the Supabase service role internally.
- Do not commit setup links, OAuth codes, access tokens, refresh tokens, or service-role keys.

## Important architectural note

Peach's documented managed AI agents can answer inbound WhatsApp conversations 24/7. H's goal is stricter: **H itself remains the brain**, with the same memory, task engine, permissions, and search capabilities across app and WhatsApp. Therefore the cloud runtime first discovers the exact Peach tools available to the connected account, then the inbound/outbound bridge is implemented against those verified capabilities rather than assuming unsupported APIs.
