# H Live Readiness Ledger

The Live Readiness Ledger is the final fail-closed production declaration layer for H.

Engineering CI proves that H's contracts are implemented. Live External Evidence proves the real external systems. The ledger combines the latest external evidence and is the only automated source allowed to emit `LIVE_READY`.

## Required gates

All four must be fresh and valid:

1. `backup` — real encrypted H Backup Runner execution, registry consistency, AES-256-GCM decrypt, and plaintext checksum verification.
2. `standby-preflight` — fresh `exact_mirror_v2` standby plus execution nonce proof without writes or user-content disclosure.
3. `failover-active` — Primary unavailable, request-only promoted Standby, and fresh execution nonce proof on the active Standby.
4. `whatsapp-voice` — real Meta media download, STT, expected phrase verification, and isolated H bridge processing.

## Latest evidence wins

For each target, only the newest available evidence artifact is evaluated.

An older successful artifact cannot hide a newer failed run. If the latest evidence is failed, malformed, incomplete, stale, or future-dated, that gate is not ready.

## Freshness

Default maximum evidence age is **14 days**. Evidence older than that becomes `evidence_stale` and H returns to `LIVE_EXTERNAL_REQUIRED` until the live gate is exercised again.

The manual ledger workflow can use a different `max_age_days`, but it cannot bypass any required field or deep-proof contract.

## Status values

- `LIVE_READY`: every required external gate has current deep proof.
- `LIVE_EXTERNAL_REQUIRED`: at least one external gate is missing, failed, invalid, or stale.

`productionDeclarationAllowed=true` is emitted only with `LIVE_READY`.

## Automation

`H Live Readiness Ledger` runs automatically after **H Live External Evidence** completes. It collects the newest non-expired GitHub Actions artifact for each target and writes `h-live-readiness-ledger.json`.

A manual run with `require_ready=true` is the authoritative release check. It exits non-zero unless every live gate is ready.

The ledger never reads application secrets or user content. It consumes only sanitized evidence JSON artifacts.

## Production rule

Do not declare H 100/100 from a passing unit test, a simulated failover, a historical artifact, or a single successful external gate.

H is 100/100 live only when the current ledger says:

```text
status = LIVE_READY
productionDeclarationAllowed = true
blockers = []
```
