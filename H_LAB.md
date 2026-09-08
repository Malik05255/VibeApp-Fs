# H Personal Assistant Runtime

This file keeps its historical name for compatibility, but **H / المساعد الشخصي H is now consolidated in `Malik05255/VibeApp-Fs`**.

- Active repository: `Malik05255/VibeApp-Fs`.
- Previous development repository: `Malik05255/vpn` (its `main` history was merged into this repository during the 2026-09-08 consolidation).
- Production Android applicationId remains `com.malik05255.lmai`.
- H is one assistant identity across in-app chat, memory, reminders, location context and the WhatsApp/cloud gateway.
- Personal reminders remain separate from programming/development reminders.
- WhatsApp, Maps, provider and backend credentials must never be committed or embedded in the APK.

## WhatsApp ingress

`cloud/whatsapp-worker/src/router.js` is the public Cloudflare Worker entrypoint. It verifies the Meta webhook signature and resolves the sender access policy before any legacy media normalization, download, transcription or message-body persistence.

- Unauthorized senders are rejected before audio transcription or media work. Only a minimal `[blocked]` idempotency envelope may be stored; the blocked message body is not persisted.
- When `H_SUPABASE_VOICE_URL` and `H_RUNTIME_SECRET` are configured, authorized text, location, button and interactive-reply content is routed into the shared Supabase H conversation/memory/task runtime.
- Audio and image/document messages continue through their existing H voice/media bridges.
- If the unified text bridge is not configured at all, authorized text uses the existing D1 fallback so the channel remains usable.
- Once a request has been attempted through unified H, the Worker does not execute it again through the local fallback. This prevents duplicate reminders/actions when the final network response is uncertain.
- Owner-only contact saving and third-party WhatsApp send/schedule commands currently remain on the guarded legacy execution path because that capability has not yet been moved into the Supabase H action runtime. Friend/unknown-user policy never gains access to that owner-only path.

This owner-only compatibility path is intentionally narrow; ordinary memory, reminder and chat requests still use unified H whenever the bridge is configured.

## H Supabase runtime deployment

The H Supabase Edge Functions are managed by `.github/workflows/h-supabase-runtime-deploy.yml`.

Required GitHub Actions secrets in this repository:

- `SUPABASE_ACCESS_TOKEN`: Supabase personal access token used only by GitHub Actions deployment.
- `SUPABASE_PROJECT_REF`: target Supabase project reference.

If these secrets are absent, deployment skips safely rather than placing credentials in source control. The workflow summary reports whether deployment was completed and verified, attempted but not verified, or skipped.

Current H Supabase functions:

- `h-openrouter-oauth`
- `h-provider-config`
- `h-tavily-config`
- `h-whatsapp-action`
- `h-whatsapp-inbox`
- `h-whatsapp-media`
- `h-whatsapp-peach`

After a configured deployment, the workflow verifies that each requested function appears in the Supabase function inventory. When `h-whatsapp-media` is part of the deployment, it also performs an unauthenticated probe that must reach the function and be rejected by H's own authorization boundary with HTTP 401. This verifies reachability without exposing or using `H_RUNTIME_SECRET` in GitHub Actions.

Secrets remain server-side. No Supabase credential, provider API key, WhatsApp credential or Maps credential belongs in the repository or Android APK.

The functions retain their application-level controls (runtime secret, setup token, OAuth state/callback validation, or provider-specific validation). Supabase gateway JWT verification is therefore disabled for these functions so external callbacks and custom-authenticated runtime calls can reach the functions' own authorization logic.
