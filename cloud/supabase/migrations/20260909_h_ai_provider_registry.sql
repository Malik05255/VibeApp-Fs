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

-- Daily paid-helper claims are counted before a provider request is made. Failed provider
-- attempts still consume a claim, deliberately preventing retry storms from spending more.
create table if not exists public.h_runtime_ai_paid_usage_daily (
  route_id text not null references public.h_runtime_ai_provider_registry(id) on delete cascade,
  usage_date date not null default current_date,
  calls integer not null default 0 check (calls >= 0),
  prompt_tokens bigint not null default 0 check (prompt_tokens >= 0),
  completion_tokens bigint not null default 0 check (completion_tokens >= 0),
  last_used_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (route_id, usage_date)
);

alter table public.h_runtime_ai_paid_usage_daily enable row level security;
revoke all on table public.h_runtime_ai_paid_usage_daily from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_ai_paid_usage_daily to service_role;

-- Atomically reserves one paid-helper attempt. A future runtime must call this before any
-- paid provider request. There is intentionally no automatic refund on provider failure.
create or replace function public.h_claim_owner_paid_ai_call(p_route_id text)
returns table (
  allowed boolean,
  calls_used integer,
  daily_limit integer,
  provider text,
  credential_id text,
  selected_model text,
  allow_free_fallback boolean
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_route public.h_runtime_ai_provider_registry%rowtype;
  v_calls integer := 0;
begin
  select r.*
    into v_route
    from public.h_runtime_ai_provider_registry r
   where r.id = btrim(coalesce(p_route_id, ''))
   for update;

  if not found
     or v_route.route_class <> 'owner_paid'
     or not v_route.enabled
     or v_route.owner_enabled_at is null
     or v_route.credential_id is null
     or btrim(v_route.credential_id) = ''
     or v_route.selected_model is null
     or v_route.daily_call_limit is null then
    return query
    select false, 0, coalesce(v_route.daily_call_limit, 0),
           null::text, null::text, null::text, true;
    return;
  end if;

  insert into public.h_runtime_ai_paid_usage_daily (route_id, usage_date, calls)
  values (v_route.id, current_date, 0)
  on conflict (route_id, usage_date) do nothing;

  update public.h_runtime_ai_paid_usage_daily u
     set calls = u.calls + 1,
         last_used_at = now(),
         updated_at = now()
   where u.route_id = v_route.id
     and u.usage_date = current_date
     and u.calls < v_route.daily_call_limit
  returning u.calls into v_calls;

  if not found then
    select u.calls into v_calls
      from public.h_runtime_ai_paid_usage_daily u
     where u.route_id = v_route.id
       and u.usage_date = current_date;

    return query
    select false, coalesce(v_calls, 0), v_route.daily_call_limit,
           v_route.provider, v_route.credential_id, v_route.selected_model,
           v_route.allow_free_fallback;
    return;
  end if;

  return query
  select true, v_calls, v_route.daily_call_limit,
         v_route.provider, v_route.credential_id, v_route.selected_model,
         v_route.allow_free_fallback;
end;
$$;

revoke all on function public.h_claim_owner_paid_ai_call(text) from public, anon, authenticated;
grant execute on function public.h_claim_owner_paid_ai_call(text) to service_role;

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
comment on table public.h_runtime_ai_paid_usage_daily is
  'Atomic daily paid-helper call budget consumed before provider execution to prevent surprise retry spending.';
