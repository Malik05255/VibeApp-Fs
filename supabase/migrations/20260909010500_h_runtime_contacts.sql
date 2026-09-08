create table if not exists public.h_runtime_contacts (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  name_key text not null,
  display_name text not null,
  target_wa_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_contacts_user_name_unique unique (user_key, name_key),
  constraint h_runtime_contacts_target_wa_id_check check (target_wa_id ~ '^[0-9]{8,20}$')
);

create index if not exists h_runtime_contacts_user_updated_idx
  on public.h_runtime_contacts (user_key, updated_at desc);

alter table public.h_runtime_contacts enable row level security;

comment on table public.h_runtime_contacts is
  'Server-side H named contacts. Access is restricted to trusted service-role runtime operations.';
