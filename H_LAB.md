# H Personal Assistant Runtime

This file keeps its historical name for compatibility, but **H / المساعد الشخصي H is now consolidated in `Malik05255/VibeApp-Fs`**.

- Active repository: `Malik05255/VibeApp-Fs`.
- Previous development repository: `Malik05255/vpn` (its `main` history was merged into this repository during the 2026-09-08 consolidation).
- Production Android applicationId remains `com.malik05255.lmai`.
- H is one assistant identity across in-app chat, memory, reminders, location context and the WhatsApp/cloud gateway.
- Personal reminders remain separate from programming/development reminders.
- WhatsApp, Maps, provider and backend credentials must never be committed or embedded in the APK.

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
