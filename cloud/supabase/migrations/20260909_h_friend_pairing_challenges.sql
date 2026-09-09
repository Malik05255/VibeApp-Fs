create table if not exists public.h_runtime_friend_pairing (
  code_fingerprint text primary key,
  label text null,
  expires_at timestamptz not null,
  consumed_at timestamptz null,
  created_at timestamptz not null default now(),
  constraint h_runtime_friend_pairing_fingerprint_format check (code_fingerprint ~ '^[0-9a-f]{64}$')
);

alter table public.h_runtime_friend_pairing enable row level security;

revoke all on table public.h_runtime_friend_pairing from public;
revoke all on table public.h_runtime_friend_pairing from anon;
revoke all on table public.h_runtime_friend_pairing from authenticated;

create index if not exists h_runtime_friend_pairing_active_idx
  on public.h_runtime_friend_pairing (expires_at)
  where consumed_at is null;

comment on table public.h_runtime_friend_pairing is
  'One-time H friend pairing challenges stored only as poll-secret-keyed HMAC fingerprints; raw pairing codes and raw WhatsApp numbers are never persisted.';
