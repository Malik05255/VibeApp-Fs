# H External Readiness Doctor

The External Readiness Doctor answers one operational question: **is the repository configured with everything needed to run the real external H evidence gates?**

It does not create external resources, make purchases, expose secret values, or declare H live-ready.

Run **H External Readiness Doctor** manually from GitHub Actions.

## What it checks

### Backup

- Separate `H_BACKUP_SUPABASE_URL` and `H_PRIMARY_SUPABASE_URL`.
- Backup and Primary service-role credentials are present and structurally plausible.
- `H_BACKUP_RUNNER_URL` and `H_RUNTIME_SECRET` are configured.

### Standby preflight

- `H_STANDBY_HEALTH_URL` is configured as HTTPS.
- `H_RUNTIME_SECRET` is configured.

### Active failover

- Standby preflight configuration is present.
- `H_PRIMARY_HEALTH_URL` is configured for the controlled Primary-down proof.

### WhatsApp Voice

- Meta access token and Graph API version are configured.
- H voice bridge URL and runtime secret are configured.
- At least one STT credential exists: `TRANSCRIPTION_API_KEY` or `GROQ_API_KEY`.

The real Meta voice media ID remains a runtime input to **H Live External Evidence** and is not stored as a repository secret.

## Output

The sanitized report contains:

- `configurationReady`
- per-gate `configurationReady`
- exact missing secret/configuration names
- invalid configuration names
- whether Backup is a distinct project from Primary
- `liveReady=false`
- `liveEvidenceStillRequired=true`

No secret values are included.

## Hard rule

A green Doctor report means only that H is configured to attempt the live tests. It does **not** mean H is 100/100.

The sequence is:

1. Doctor: configuration is complete.
2. H Live External Evidence: real Backup, Standby, Failover, and Voice proofs succeed.
3. H Live Readiness Ledger: latest fresh evidence evaluates to `LIVE_READY` with `productionDeclarationAllowed=true`.
