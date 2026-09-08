create table if not exists public.h_runtime_owner_pairing (
  code_fingerprint text primary key,
  expires_at timestamptz not null,
  consumed_at timestamptz null,
  created_at timestamptz not null default now(),
  constraint h_runtime_owner_pairing_fingerprint_format check (code_fingerprint ~ '^[0-9a-f]{64}$')
);

alter table public.h_runtime_owner_pairing enable row level security;

revoke all on table public.h_runtime_owner_pairing from public;
revoke all on table public.h_runtime_owner_pairing from anon;
revoke all on table public.h_runtime_owner_pairing from authenticated;

create index if not exists h_runtime_owner_pairing_active_idx
  on public.h_runtime_owner_pairing (expires_at)
  where consumed_at is null;

comment on table public.h_runtime_owner_pairing is
  'One-time H owner pairing challenges stored only as poll-secret-keyed HMAC fingerprints; raw pairing codes are never persisted.';
