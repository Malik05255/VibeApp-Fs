#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

say() { printf '\n==> %s\n' "$1"; }

say "Gate 1 - Backup Cloud stays fail-closed until a real standby exists"
manager=cloud/supabase/h-cloud-manager/index.ts
backup=cloud/supabase/h-cloud-backup-config/index.ts
grep -Fq 'backupAutoFailoverEligible' "$manager"
grep -Fq 'automaticFailoverReady: backupAutoFailoverEligible' "$manager"
grep -Fq 'readyOnlyAfterWriteProbe: true' "$manager"
grep -Fq 'auto_failover_eligible: false' "$backup"
grep -Fq 'standby_runtime_ready: false' "$backup"

say "Gate 2 - Failover promotion and split-brain stress contract"
node --test cloud/whatsapp-worker/src/router_failover_contract_test.mjs
node --test cloud/whatsapp-worker/src/router_scheduled_test.mjs

say "Gate 3 and 4 - Reinstall and brand-new-device continuity"
deno test cloud/supabase/h-app-sync/owner-continuity_test.ts

say "Gate 5 - App and WhatsApp share one H owner scope"
deno test cloud/supabase/h-app-sync/owner-continuity_test.ts
grep -Fq 'sameRuntimeAsWhatsApp: true' cloud/supabase/h-owner-continuity/index.ts
grep -Fq 'resumeExistingH: true' cloud/supabase/h-owner-continuity/index.ts

say "Gate 6 - WhatsApp Voice Meta to STT to H bridge E2E contract"
node --test cloud/whatsapp-worker/src/voice_e2e_test.mjs
deno test cloud/supabase/h-whatsapp-inbox/voice-bridge_test.ts

say "Gate 7 - Exhaust all free routes without paid fallback"
deno test --allow-env cloud/supabase/h-whatsapp-inbox/ai-router-policy_test.ts cloud/supabase/h-whatsapp-inbox/ai-router-runtime_test.ts
grep -Fq 'const MAX_FREE_ATTEMPTS = 3;' cloud/supabase/h-whatsapp-inbox/ai-router-runtime.ts

say "Gate 8 - Learning unknown to verified to re-ask loop"
deno test cloud/supabase/h-learning-cycle/learning-cycle-policy_test.ts
deno test cloud/supabase/h-knowledge-verifier/verification-policy_test.ts
deno test cloud/supabase/h-learning-cycle/learning-pipeline_e2e_test.ts
grep -Fq "'5 * * * *'" cloud/supabase/migrations/20260909_h_learning_pipeline_scheduler.sql
grep -Fq "'25 * * * *'" cloud/supabase/migrations/20260909_h_learning_pipeline_scheduler.sql

say "Gate 9 - Outage idempotency and concurrency guards"
node --test cloud/whatsapp-worker/src/router_test.mjs
node --test cloud/whatsapp-worker/src/router_failover_contract_test.mjs
grep -Fq 'pg_advisory_xact_lock' cloud/supabase/migrations/20260912_h_portable_restore_v3.sql
grep -Fq 'idempotentReplay' cloud/supabase/migrations/20260912_h_portable_restore_v3.sql
grep -Fq 'INSERT OR IGNORE INTO inbound_messages' cloud/whatsapp-worker/src/index.js
grep -Fq 'if (!firstSeen) continue;' cloud/whatsapp-worker/src/index.js

say "Gate 10 - Production declaration is controlled by fresh live evidence ledger"
doc=docs/H_PRODUCTION_READINESS.md
ledger=cloud/live-readiness/h-live-readiness-ledger.mjs
ledger_workflow=.github/workflows/h-live-readiness-ledger.yml
grep -Fq 'CODE_READY' "$doc"
grep -Fq 'LIVE_EXTERNAL_REQUIRED' "$doc"
grep -Fq 'Do not declare H 100/100' "$doc"
grep -Fq 'Backup Cloud' "$doc"
grep -Fq 'WhatsApp Voice' "$doc"
node --test cloud/live-readiness/h-live-readiness-ledger_test.mjs
grep -Fq 'productionDeclarationAllowed' "$ledger"
grep -Fq 'LIVE_READY' "$ledger"
grep -Fq 'LIVE_EXTERNAL_REQUIRED' "$ledger"
grep -Fq 'evidence_stale' "$ledger"
grep -Fq 'workflow_run:' "$ledger_workflow"
grep -Fq 'H Live External Evidence' "$ledger_workflow"
grep -Fq -- '--require-ready' "$ledger_workflow"

say "H Production Readiness: CODE_READY"
