-- Link the Android H owner to the existing WhatsApp/cloud runtime without storing raw Google subjects or raw WhatsApp ids in the link table.

alter table if exists public.h_runtime_owner_pairing
  add column if not exists google_subject_fingerprint text,
  add column if not exists google_audience text,
  add column if not exists app_linked_at timestamptz;

create index if not exists h_runtime_owner_pairing_google_link_idx
  on public.h_runtime_owner_pairing (google_subject_fingerprint, google_audience, created_at desc)
  where google_subject_fingerprint is not null;

create table if not exists public.h_runtime_app_identities (
  google_subject_fingerprint text primary key,
  google_audience text not null,
  runtime_user_key_ciphertext text not null,
  active boolean not null default true,
  linked_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_app_identities_google_subject_fingerprint_check
    check (google_subject_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint h_runtime_app_identities_google_audience_check
    check (length(google_audience) between 1 and 255),
  constraint h_runtime_app_identities_runtime_user_key_ciphertext_check
    check (length(runtime_user_key_ciphertext) between 20 and 1024)
);

alter table public.h_runtime_app_identities enable row level security;

comment on table public.h_runtime_app_identities is
  'Server-only mapping from a verified Google subject fingerprint to an encrypted H runtime user key. No raw Google subject or WhatsApp id is stored here.';
