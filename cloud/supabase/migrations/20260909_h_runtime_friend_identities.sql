create table if not exists public.h_runtime_friend_identities (
  wa_fingerprint text primary key,
  label text null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_friend_identities_fingerprint_format check (wa_fingerprint ~ '^[0-9a-f]{64}$')
);

alter table public.h_runtime_friend_identities enable row level security;

revoke all on table public.h_runtime_friend_identities from public;
revoke all on table public.h_runtime_friend_identities from anon;
revoke all on table public.h_runtime_friend_identities from authenticated;

comment on table public.h_runtime_friend_identities is
  'Authorized H friend WhatsApp identities stored only as poll_secret-keyed HMAC fingerprints; raw phone numbers are never persisted.';
