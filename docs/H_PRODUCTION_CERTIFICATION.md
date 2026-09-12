# H Production Certification

`H Production Certification` is the final combined H readiness workflow. It does not create external services, buy capacity, or expose secret values.

## What it proves

A successful `PRODUCTION_CERTIFIED` result requires all three layers to be true in the same run:

1. **Code readiness** — the reusable H Production Readiness Gate 1-10 suite passes on the checked-out `main` commit.
2. **External configuration readiness** — H External Readiness Doctor reports every required Backup, Standby, Failover, and WhatsApp Voice configuration item present and structurally valid, with Backup on a distinct Supabase project.
3. **Fresh live readiness** — H Live Readiness Ledger accepts the newest non-expired evidence for all four live targets: `backup`, `standby-preflight`, `failover-active`, and `whatsapp-voice`.

The default maximum live-evidence age is 14 days. A newer failed artifact always overrides an older success.

## Manual workflow

Run **H Production Certification** from GitHub Actions.

Inputs:

- `require_certified=false`: always produce a sanitized diagnostic certification document, even when H is blocked.
- `require_certified=true`: fail the workflow unless the final result is `PRODUCTION_CERTIFIED`.
- `max_age_days=14`: controls the maximum accepted age for live evidence.

The workflow emits a sanitized artifact bundle containing:

- `h-production-certification.json`
- `h-code-readiness.json`
- `h-external-readiness-doctor.json`
- `h-live-readiness-ledger.json`

## Fail-closed rules

Certification is blocked if any of these are true:

- one of the ten H code gates fails;
- required external configuration is missing or invalid;
- Backup points at the Primary project instead of a distinct project;
- any live target lacks evidence;
- the newest evidence for a target failed;
- accepted evidence is stale;
- any expected readiness document is missing or malformed.

`configurationReady` is not `LIVE_READY`, and `CODE_READY` is not `PRODUCTION_CERTIFIED`.

## External actions still required

The certification workflow can verify and combine evidence, but it intentionally does not create the real Backup Cloud, intentionally take Primary offline, or manufacture a real Meta voice media ID. Those actions must occur in the actual external environment before their live evidence can exist.
