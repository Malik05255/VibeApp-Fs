-- H cloud runtime storage. Intentionally isolated from existing application tables.

create table if not exists public.h_runtime_setup_links (
  token text primary key,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.h_runtime_oauth_pending (
  state text primary key,
  verifier text not null,
  redirect_uri text not null,
  client_id text not null,
  token_endpoint text not null,
  resource_url text not null,
  scope text,
  expires_at timestamptz not null,
  created_at timestamptz not null default now()
);

create table if not exists public.h_runtime_credentials (
  id text primary key,
  access_token text not null,
  refresh_token text,
  token_type text not null default 'Bearer',
  scope text,
  expires_at timestamptz,
  client_id text not null,
  token_endpoint text not null,
  resource_url text not null,
  oauth_metadata jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table if not exists public.h_runtime_mcp_tools (
  name text primary key,
  description text not null default '',
  input_schema jsonb not null default '{}'::jsonb,
  discovered_at timestamptz not null default now()
);

alter table public.h_runtime_setup_links enable row level security;
alter table public.h_runtime_oauth_pending enable row level security;
alter table public.h_runtime_credentials enable row level security;
alter table public.h_runtime_mcp_tools enable row level security;

-- No client RLS policies are created intentionally. These tables are service-role only.
create index if not exists h_runtime_setup_links_expires_idx
  on public.h_runtime_setup_links(expires_at);
create index if not exists h_runtime_oauth_pending_expires_idx
  on public.h_runtime_oauth_pending(expires_at);
