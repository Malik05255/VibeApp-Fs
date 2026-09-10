-- H standby-only runtime bootstrap.
-- This file MUST NOT be applied to the primary project. It marks an explicitly prepared,
-- dedicated H project as a passive standby that accepts exact-mirror replication only.
-- Promotion/execution readiness is a separate operation and is intentionally not performed here.

insert into public.h_runtime_state (key, value, updated_at)
values (
  'standby_runtime',
  jsonb_build_object(
    'runtime_role', 'standby',
    'h_identity', 'H',
    'dedicated_h_standby', true,
    'allow_replica_writes', true,
    'execution_runtime_ready', false,
    'promoted', false,
    'bootstrap_state', 'awaiting_execution_runtime_and_first_replication',
    'auto_failover_enabled', false,
    'provider_credentials_replicated', false,
    'runtime_secrets_replicated', false,
    'raw_media_replicated', false,
    'configured_at', now()
  ),
  now()
)
on conflict (key) do update
set value = jsonb_build_object(
      'runtime_role', 'standby',
      'h_identity', 'H',
      'dedicated_h_standby', true,
      'allow_replica_writes', true,
      'execution_runtime_ready', false,
      'promoted', false,
      'bootstrap_state', 'awaiting_execution_runtime_and_first_replication',
      'auto_failover_enabled', false,
      'provider_credentials_replicated', false,
      'runtime_secrets_replicated', false,
      'raw_media_replicated', false,
      'configured_at', now()
    ),
    updated_at = now();

comment on table public.h_runtime_state is
  'H runtime state. On a dedicated standby, standby_runtime remains passive until executable runtime validation and a separate promotion workflow are complete.';
