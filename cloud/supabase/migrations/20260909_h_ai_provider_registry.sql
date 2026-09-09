-- H-owned AI provider registry.
--
-- The registry separates H identity from replaceable helper providers and separates
-- free/internal capacity from owner-authorized paid/BYOK capacity. No paid route is
-- inserted or enabled by this migration.

create table if not exists public.h_runtime_ai_provider_registry (
  id text primary key check (id ~ '^[a-z0-9][a-z0-9._:-]{1,95}$'),
  provider text not null check (provider ~ '^[a-z0-9][a-z0-9._-]{1,63}$'),
  route_class text not null check (route_class in ('internal_free', 'owner_paid')),
  credential_id text,
  selected_model text check (selected_model is null or char_length(selected_model) between 1 and 200),
  enabled boolean not null default false,
  owner_enabled_at timestamptz,
  hard_tasks_only boolean not null default true,
  allow_free_fallback boolean not null default true,
  daily_call_limit integer check (daily_call_limit is null or daily_call_limit between 1 and 10000),
  priority integer not null default 100 check (priority between 0 and 10000),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    route_class = 'internal_free'
    or not enabled
    or (
      owner_enabled_at is not null
      and credential_id is not null
      and btrim(credential_id) <> ''
      and selected_model is not null
      and daily_call_limit is not null
    )
  )
);

-- H may have many registered paid/BYOK providers, but only one can be active at once.
-- This prevents silent mixing of multiple paid helpers.
create unique index if not exists h_ai_provider_registry_one_paid_enabled_idx
  on public.h_runtime_ai_provider_registry ((route_class))
  where route_class = 'owner_paid' and enabled;

create index if not exists h_ai_provider_registry_enabled_priority_idx
  on public.h_runtime_ai_provider_registry (route_class, enabled, priority, updated_at desc);

alter table public.h_runtime_ai_provider_registry enable row level security;
revoke all on table public.h_runtime_ai_provider_registry from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_ai_provider_registry to service_role;

-- Register current hidden free OpenRouter capacity without changing its existing
-- credential record or exposing it as an owner-paid route.
insert into public.h_runtime_ai_provider_registry (
  id,
  provider,
  route_class,
  credential_id,
  selected_model,
  enabled,
  owner_enabled_at,
  hard_tasks_only,
  allow_free_fallback,
  daily_call_limit,
  priority,
  metadata
) values (
  'openrouter_free_hidden',
  'openrouter',
  'internal_free',
  'openrouter_default',
  null,
  true,
  null,
  false,
  true,
  null,
  100,
  jsonb_build_object(
    'hidden', true,
    'cost_policy', 'strict_zero_only',
    'managed_by', 'h_core'
  )
)
on conflict (id) do update
set provider = excluded.provider,
    route_class = excluded.route_class,
    credential_id = excluded.credential_id,
    enabled = true,
    hard_tasks_only = false,
    allow_free_fallback = true,
    metadata = excluded.metadata,
    updated_at = now();

comment on table public.h_runtime_ai_provider_registry is
  'H-owned registry of replaceable AI helpers. Owner-paid routes require explicit consent metadata and at most one may be enabled.';
