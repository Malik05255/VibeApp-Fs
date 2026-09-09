-- H-owned free AI route health, quota and failover telemetry.
-- No paid route is represented or enabled by this schema.

create table if not exists public.h_runtime_ai_route_stats (
  provider text not null check (provider = 'openrouter'),
  model text not null check (char_length(model) between 1 and 200),
  capability text not null check (capability in ('text','image','file')),
  attempts bigint not null default 0 check (attempts >= 0),
  successes bigint not null default 0 check (successes >= 0),
  failures bigint not null default 0 check (failures >= 0),
  rate_limits bigint not null default 0 check (rate_limits >= 0),
  consecutive_failures integer not null default 0 check (consecutive_failures >= 0),
  avg_latency_ms double precision not null default 0 check (avg_latency_ms >= 0),
  prompt_tokens bigint not null default 0 check (prompt_tokens >= 0),
  completion_tokens bigint not null default 0 check (completion_tokens >= 0),
  last_http_status integer,
  last_rate_limit_remaining bigint,
  last_rate_limit_reset_at timestamptz,
  last_error text check (last_error is null or char_length(last_error) <= 500),
  cooldown_until timestamptz,
  last_used_at timestamptz,
  last_success_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (provider, model, capability)
);

create index if not exists h_runtime_ai_route_stats_cooldown_idx
  on public.h_runtime_ai_route_stats (provider, capability, cooldown_until);

alter table public.h_runtime_ai_route_stats enable row level security;
revoke all on table public.h_runtime_ai_route_stats from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_ai_route_stats to service_role;

create or replace function public.h_record_ai_route_result(
  p_provider text,
  p_model text,
  p_capability text,
  p_ok boolean,
  p_latency_ms integer default 0,
  p_http_status integer default null,
  p_error text default null,
  p_cooldown_until timestamptz default null,
  p_prompt_tokens integer default 0,
  p_completion_tokens integer default 0,
  p_rate_limit_remaining bigint default null,
  p_rate_limit_reset_at timestamptz default null
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_provider text := lower(btrim(coalesce(p_provider, '')));
  v_model text := left(btrim(coalesce(p_model, '')), 200);
  v_capability text := lower(btrim(coalesce(p_capability, '')));
  v_latency integer := greatest(0, coalesce(p_latency_ms, 0));
  v_prompt integer := greatest(0, coalesce(p_prompt_tokens, 0));
  v_completion integer := greatest(0, coalesce(p_completion_tokens, 0));
  v_error text := nullif(left(regexp_replace(btrim(coalesce(p_error, '')), E'\s+', ' ', 'g'), 500), '');
begin
  if v_provider <> 'openrouter'
     or v_model = ''
     or v_capability not in ('text','image','file') then
    raise exception 'invalid_h_ai_route_result';
  end if;

  insert into public.h_runtime_ai_route_stats (
    provider, model, capability,
    attempts, successes, failures, rate_limits, consecutive_failures,
    avg_latency_ms, prompt_tokens, completion_tokens,
    last_http_status, last_rate_limit_remaining, last_rate_limit_reset_at,
    last_error, cooldown_until, last_used_at, last_success_at, updated_at
  ) values (
    v_provider, v_model, v_capability,
    1,
    case when p_ok then 1 else 0 end,
    case when p_ok then 0 else 1 end,
    case when p_http_status = 429 then 1 else 0 end,
    case when p_ok then 0 else 1 end,
    v_latency, v_prompt, v_completion,
    p_http_status, p_rate_limit_remaining, p_rate_limit_reset_at,
    case when p_ok then null else v_error end,
    case when p_ok then null else p_cooldown_until end,
    now(), case when p_ok then now() else null end, now()
  )
  on conflict (provider, model, capability) do update
  set attempts = public.h_runtime_ai_route_stats.attempts + 1,
      successes = public.h_runtime_ai_route_stats.successes + case when p_ok then 1 else 0 end,
      failures = public.h_runtime_ai_route_stats.failures + case when p_ok then 0 else 1 end,
      rate_limits = public.h_runtime_ai_route_stats.rate_limits + case when p_http_status = 429 then 1 else 0 end,
      consecutive_failures = case
        when p_ok then 0
        else public.h_runtime_ai_route_stats.consecutive_failures + 1
      end,
      avg_latency_ms = case
        when public.h_runtime_ai_route_stats.attempts <= 0 then v_latency
        else ((public.h_runtime_ai_route_stats.avg_latency_ms * public.h_runtime_ai_route_stats.attempts) + v_latency)
          / (public.h_runtime_ai_route_stats.attempts + 1)
      end,
      prompt_tokens = public.h_runtime_ai_route_stats.prompt_tokens + v_prompt,
      completion_tokens = public.h_runtime_ai_route_stats.completion_tokens + v_completion,
      last_http_status = p_http_status,
      last_rate_limit_remaining = p_rate_limit_remaining,
      last_rate_limit_reset_at = p_rate_limit_reset_at,
      last_error = case when p_ok then null else v_error end,
      cooldown_until = case
        when p_ok then null
        when p_cooldown_until is null then public.h_runtime_ai_route_stats.cooldown_until
        when public.h_runtime_ai_route_stats.cooldown_until is null then p_cooldown_until
        else greatest(public.h_runtime_ai_route_stats.cooldown_until, p_cooldown_until)
      end,
      last_used_at = now(),
      last_success_at = case when p_ok then now() else public.h_runtime_ai_route_stats.last_success_at end,
      updated_at = now();
end;
$$;

revoke all on function public.h_record_ai_route_result(text,text,text,boolean,integer,integer,text,timestamptz,integer,integer,bigint,timestamptz)
  from public, anon, authenticated;
grant execute on function public.h_record_ai_route_result(text,text,text,boolean,integer,integer,text,timestamptz,integer,integer,bigint,timestamptz)
  to service_role;

comment on table public.h_runtime_ai_route_stats is
  'H-owned zero-price AI route health/quota telemetry used for free-model selection and cooldown failover.';
