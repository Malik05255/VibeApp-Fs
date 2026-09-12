# H Live External Evidence

This workflow closes the remaining external-only H readiness gates without weakening the fail-closed architecture.

Run **H Live External Evidence** manually from GitHub Actions. It never runs on a normal push. Every run writes a sanitized `h-live-evidence.json` artifact and never includes raw secrets.

## Targets

### `backup`

Required repository secrets:

- `H_BACKUP_SUPABASE_URL`
- `H_BACKUP_SUPABASE_SERVICE_ROLE_KEY`
- `H_PRIMARY_SUPABASE_URL` (recommended, to prove Backup is a different project)

Evidence required:

- Backup URL is a separate HTTPS Supabase project.
- `h-backups` bucket is available or can be created.
- A random probe object is written.
- The exact object is read back and compared byte-for-byte.
- The probe object is deleted.

A write-only check is not sufficient.

### `standby-preflight`

Required repository secrets:

- `H_STANDBY_HEALTH_URL`
- `H_RUNTIME_SECRET`

Evidence required:

- Standby identifies itself as H standby.
- `exact_mirror_v2` is active.
- Restore digest is verified.
- Replication is fresh.
- Passive preflight execution contract is ready.
- Raw provider credentials and raw media are not replicated.

### `failover-active`

Required repository secrets:

- `H_STANDBY_HEALTH_URL`
- `H_RUNTIME_SECRET`
- `H_PRIMARY_HEALTH_URL`

Before running this target, Primary must intentionally be unavailable for the controlled live test. The workflow is hard-wired with `H_EXPECT_PRIMARY_UNREACHABLE=true`.

Evidence required:

- Primary health endpoint is unreachable during the evidence run.
- Standby reports `activeReady=true`.
- Promotion is attested.
- Promotion mode is `request_only`.
- Standby remains on `exact_mirror_v2` with verified restore state.
- Active execution does not reopen replica writes.

The workflow does not kill Primary itself and does not implement automatic failback.

### `whatsapp-voice`

Required repository secrets:

- `WHATSAPP_ACCESS_TOKEN`
- `META_GRAPH_VERSION`
- `TRANSCRIPTION_API_KEY` or `GROQ_API_KEY`
- `H_SUPABASE_VOICE_URL`
- `H_RUNTIME_SECRET`
- Optional: `TRANSCRIPTION_API_URL`, `TRANSCRIPTION_MODEL`

Workflow input:

- `voice_media_id`: a real Meta media ID for a short voice note.
- `voice_expected_phrase`: defaults to `اختبار صوت H فقط`.

The live probe downloads the real Meta media, sends the audio to the configured STT provider, requires the expected phrase to appear in the transcript, then bridges the transcript through H.

Safety constraints:

- The bridge uses synthetic WA ID `990000000001`, not the owner's number.
- Sender role is `friend`.
- `can_send_external=false` is forced.
- The harness never stores raw audio.
- If the expected test phrase is not detected, the H bridge is not called.

## Production declaration

The deterministic 10-gate CI remains necessary but is not sufficient for 100/100 production status.

H may be declared fully live only after successful evidence artifacts exist for:

1. Backup Cloud write/read/delete.
2. Standby preflight.
3. Primary-down active failover.
4. Real WhatsApp Voice Meta -> STT -> H bridge.

Missing secrets or unavailable external resources must fail the manual workflow rather than be treated as success.
