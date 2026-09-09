-- H multi-cloud foundation.
-- H identity/data semantics remain independent from any one cloud. This migration registers
-- the current H Cloud as primary and creates service-role-only registries for future backup
-- clouds, credentials, health/quota telemetry, and backup-run auditing.

create table if not exists public.h_runtime_cloud_credentials (
  id text primary key,
  provider text not null,
  secret_ciphertext text not null,
  secret_iv text not null,
  secret_version integer not null default 1 check (secret_version >= 1),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_cloud_credentials_id_format
    check (id ~ '^[a-z0-9][a-z0-9._-]{2,95}$'),
  constraint h_runtime_cloud_credentials_provider_format
    check (provider ~ '^[a-z0-9][a-z0-9._-]{1,63}$')
);

alter table public.h_runtime_cloud_credentials enable row level security;

create table if not exists public.h_runtime_cloud_registry (
  id text primary key,
  provider text not null,
  cloud_role text not null check (cloud_role in ('primary', 'backup')),
  endpoint text not null,
  credential_id text references public.h_runtime_cloud_credentials(id) on delete set null,
  enabled boolean not null default false,
  ready boolean not null default false,
  priority integer not null default 100 check (priority between 0 and 10000),
  quota_bytes bigint check (quota_bytes is null or quota_bytes >= 0),
  used_bytes bigint check (used_bytes is null or used_bytes >= 0),
  quota_requests bigint check (quota_requests is null or quota_requests >= 0),
  used_requests bigint check (used_requests is null or used_requests >= 0),
  quota_resets_at timestamptz,
  last_health_at timestamptz,
  last_health_ok boolean,
  last_error_code text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_cloud_registry_id_format
    check (id ~ '^[a-z0-9][a-z0-9._-]{2,95}$'),
  constraint h_runtime_cloud_registry_provider_format
    check (provider ~ '^[a-z0-9][a-z0-9._-]{1,63}$'),
  constraint h_runtime_cloud_registry_https_endpoint
    check (endpoint ~ '^https://[^[:space:]]+$')
);

alter table public.h_runtime_cloud_registry enable row level security;

create unique index if not exists h_runtime_cloud_one_enabled_primary
  on public.h_runtime_cloud_registry (cloud_role)
  where cloud_role = 'primary' and enabled = true;

create unique index if not exists h_runtime_cloud_one_enabled_backup
  on public.h_runtime_cloud_registry (cloud_role)
  where cloud_role = 'backup' and enabled = true;

create index if not exists h_runtime_cloud_health_lookup
  on public.h_runtime_cloud_registry (enabled, ready, priority, updated_at desc);

create table if not exists public.h_runtime_cloud_backup_runs (
  id uuid primary key default gen_random_uuid(),
  source_cloud_id text not null references public.h_runtime_cloud_registry(id) on delete restrict,
  target_cloud_id text references public.h_runtime_cloud_registry(id) on delete restrict,
  status text not null check (status in ('queued', 'running', 'succeeded', 'failed', 'skipped')),
  snapshot_version integer check (snapshot_version is null or snapshot_version >= 1),
  checksum_sha256 text,
  item_counts jsonb not null default '{}'::jsonb,
  byte_estimate bigint check (byte_estimate is null or byte_estimate >= 0),
  error_code text,
  metadata jsonb not null default '{}'::jsonb,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default now()
);

alter table public.h_runtime_cloud_backup_runs enable row level security;

create index if not exists h_runtime_cloud_backup_runs_recent
  on public.h_runtime_cloud_backup_runs (created_at desc);

create index if not exists h_runtime_cloud_backup_runs_target_recent
  on public.h_runtime_cloud_backup_runs (target_cloud_id, created_at desc)
  where target_cloud_id is not null;

-- Register the current H Cloud explicitly as the initial primary. No service credential is
-- duplicated into this registry; local Edge Functions continue using the platform-provided
-- SUPABASE_SERVICE_ROLE_KEY at runtime.
insert into public.h_runtime_cloud_registry (
  id,
  provider,
  cloud_role,
  endpoint,
  credential_id,
  enabled,
  ready,
  priority,
  last_health_at,
  last_health_ok,
  metadata,
  updated_at
) values (
  'h_primary_supabase',
  'supabase',
  'primary',
  'https://abavsspydbpkudhswmzp.supabase.co',
  null,
  true,
  true,
  0,
  now(),
  true,
  jsonb_build_object(
    'managed_by', 'h',
    'durable_state', 'current',
    'credential_source', 'platform_runtime',
    'auto_failover_eligible', false
  ),
  now()
)
on conflict (id) do update set
  provider = excluded.provider,
  cloud_role = excluded.cloud_role,
  endpoint = excluded.endpoint,
  enabled = true,
  ready = true,
  priority = 0,
  last_health_at = now(),
  last_health_ok = true,
  metadata = public.h_runtime_cloud_registry.metadata || excluded.metadata,
  updated_at = now();
