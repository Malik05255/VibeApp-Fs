-- H cloud AI provider storage. Service-role only; no client policies.
-- Secrets are encrypted by Edge Functions with H_CREDENTIAL_ENCRYPTION_KEY before storage.

create table if not exists public.h_runtime_ai_setup_links (
  token_hash text primary key,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.h_runtime_ai_oauth_pending (
  state_hash text primary key,
  verifier_ciphertext text not null,
  verifier_iv text not null,
  redirect_uri text not null,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table if not exists public.h_runtime_ai_credentials (
  id text primary key,
  provider text not null check (provider in ('openrouter')),
  secret_ciphertext text not null,
  secret_iv text not null,
  secret_version integer not null default 1,
  selected_model text,
  model_verified_at timestamptz,
  oauth_metadata jsonb not null default '{}'::jsonb,
  connected_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.h_runtime_ai_setup_links enable row level security;
alter table public.h_runtime_ai_oauth_pending enable row level security;
alter table public.h_runtime_ai_credentials enable row level security;

-- No client RLS policies are created intentionally. Only service-role Edge Functions may access these rows.
create index if not exists h_runtime_ai_setup_links_expires_idx
  on public.h_runtime_ai_setup_links(expires_at);
create index if not exists h_runtime_ai_oauth_pending_expires_idx
  on public.h_runtime_ai_oauth_pending(expires_at);
