-- Short-lived owner setup tokens for adding a validated backup cloud.
-- Raw cloud credentials are never stored here; only a SHA-256 setup token fingerprint.
create table if not exists public.h_runtime_cloud_setup (
  token_hash text primary key,
  provider text not null default 'supabase',
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now(),
  metadata jsonb not null default '{}'::jsonb,
  constraint h_runtime_cloud_setup_provider_format
    check (provider ~ '^[a-z0-9][a-z0-9._-]{1,63}$')
);

alter table public.h_runtime_cloud_setup enable row level security;

create index if not exists h_runtime_cloud_setup_expiry
  on public.h_runtime_cloud_setup (expires_at)
  where used_at is null;
