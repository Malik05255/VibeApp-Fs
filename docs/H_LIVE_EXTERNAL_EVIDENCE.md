# H Live External Evidence

This workflow closes the remaining external-only H readiness gates without weakening fail-closed behavior. Run **H Live External Evidence** manually from GitHub Actions. Normal pushes never touch live resources. Every run writes a sanitized `h-live-evidence.json` artifact with no raw secrets or user content.

## `backup`

Required repository secrets:

- `H_BACKUP_SUPABASE_URL`
- `H_BACKUP_SUPABASE_SERVICE_ROLE_KEY`
- `H_PRIMARY_SUPABASE_URL`
- `H_PRIMARY_SUPABASE_SERVICE_ROLE_KEY`
- `H_BACKUP_RUNNER_URL`
- `H_RUNTIME_SECRET`

Evidence required:

- Backup is a different HTTPS Supabase project.
- `h-backups` supports write, byte-for-byte read, and delete.
- H's real `h-backup-runner` succeeds with encryption enabled, checksum present, and raw media excluded.
- Latest successful `h_runtime_cloud_backup_runs` points to the same backup object.
- `h_runtime_cloud_registry` reports the backup enabled, ready, healthy, and `storage_backup_ready=true` with the same latest object.
- The real encrypted object is downloaded and its AES-256-GCM envelope is decrypted using H's backup-key derivation contract.
- Decrypted plaintext SHA-256 matches both the envelope checksum and the H Cloud backup-run registry checksum.
- Portable snapshot schema version matches the registry.

Storage write/read/delete alone is not sufficient.

## `standby-preflight`

Required repository secrets:

- `H_STANDBY_HEALTH_URL`
- `H_RUNTIME_SECRET`

Evidence required:

- Standby identifies as H standby.
- `exact_mirror_v2` is active, restore digest is verified, and replication is fresh.
- Passive preflight execution contract is ready.
- A fresh random execution nonce is sent to the real standby runtime.
- Standby reads required core H schema and returns only SHA-256 of that nonce.
- Returned nonce hash must match the current run; a stale/static response fails.
- Probe performs no writes and returns no user content.
- Raw provider credentials and raw media are not replicated.

## `failover-active`

Required repository secrets:

- `H_STANDBY_HEALTH_URL`
- `H_RUNTIME_SECRET`
- `H_PRIMARY_HEALTH_URL`

Primary must intentionally be unavailable for the controlled live test. The workflow forces `H_EXPECT_PRIMARY_UNREACHABLE=true`.

Evidence required:

- Primary health endpoint is unreachable.
- Standby reports `activeReady=true`.
- Promotion is attested and mode is `request_only`.
- `exact_mirror_v2` and restore verification remain valid.
- Active execution keeps replica writes fenced.
- A fresh execution nonce is processed by promoted Standby and correct nonce hash is returned.
- Core H schema is readable on promoted Standby without writes or user-content disclosure.

The workflow does not kill Primary and does not implement automatic failback.

## `whatsapp-voice`

Required repository secrets:

- `WHATSAPP_ACCESS_TOKEN`
- `META_GRAPH_VERSION`
- `TRANSCRIPTION_API_KEY` or `GROQ_API_KEY`
- `H_SUPABASE_VOICE_URL`
- `H_RUNTIME_SECRET`
- Optional: `TRANSCRIPTION_API_URL`, `TRANSCRIPTION_MODEL`

Workflow input:

- `voice_media_id`: real Meta media ID for a short voice note.
- `voice_expected_phrase`: defaults to `اختبار صوت H فقط`.

The probe downloads real Meta media, transcribes it with the configured STT provider, requires the expected phrase, then bridges the transcript through H.

Safety constraints:

- Uses synthetic WA ID `990000000001`.
- Sender role is `friend`.
- `can_send_external=false` is forced.
- Harness never stores raw audio.
- If expected phrase is not detected, H bridge is not called.

## Production declaration

The deterministic 10-gate CI remains necessary but is not sufficient for 100/100 production status.

H may be declared fully live only after successful artifacts exist for:

1. Real encrypted Backup Cloud backup: storage probe + H Backup Runner + registry + decrypt + checksum verification.
2. Standby preflight with a fresh execution nonce proof.
3. Primary-down active failover with a fresh promoted-Standby execution nonce proof.
4. Real WhatsApp Voice Meta -> STT -> H bridge.

Missing secrets, no second cloud, stale standby responses, checksum mismatch, failed decryption, or unavailable external resources must fail rather than be treated as success.
