-- Owner-paid/BYOK AI runtime support for H.
-- No paid route is created or enabled by this migration.

alter table public.h_runtime_ai_provider_registry
  alter column allow_free_fallback set default false;

create table if not exists public.h_runtime_ai_owner_paid_setup (
  token_hash text primary key check (char_length(token_hash) between 20 and 200),
  provider text not null check (provider = 'openrouter'),
  selected_model text not null check (char_length(selected_model) between 1 and 200),
  daily_call_limit integer not null check (daily_call_limit between 1 and 100),
  hard_tasks_only boolean not null default false,
  allow_free_fallback boolean not null default false,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_at timestamptz not null default now()
);

create index if not exists h_ai_owner_paid_setup_expires_idx
  on public.h_runtime_ai_owner_paid_setup (expires_at)
  where used_at is null;

alter table public.h_runtime_ai_owner_paid_setup enable row level security;
revoke all on table public.h_runtime_ai_owner_paid_setup from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_ai_owner_paid_setup to service_role;

-- The call claim is already consumed by h_claim_owner_paid_ai_call before contacting the
-- provider. This function records token usage for that already-claimed call only.
create or replace function public.h_record_owner_paid_ai_usage(
  p_route_id text,
  p_prompt_tokens integer,
  p_completion_tokens integer
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_updated integer := 0;
begin
  update public.h_runtime_ai_paid_usage_daily u
     set prompt_tokens = u.prompt_tokens + greatest(0, least(coalesce(p_prompt_tokens, 0), 2147483647))::bigint,
         completion_tokens = u.completion_tokens + greatest(0, least(coalesce(p_completion_tokens, 0), 2147483647))::bigint,
         last_used_at = now(),
         updated_at = now()
   where u.route_id = btrim(coalesce(p_route_id, ''))
     and u.usage_date = current_date;
  get diagnostics v_updated = row_count;
  return v_updated = 1;
end;
$$;

revoke all on function public.h_record_owner_paid_ai_usage(text, integer, integer) from public, anon, authenticated;
grant execute on function public.h_record_owner_paid_ai_usage(text, integer, integer) to service_role;

comment on table public.h_runtime_ai_owner_paid_setup is
  'One-time owner-authorized setup material for a BYOK paid AI helper. Raw API keys are never stored here.';
comment on function public.h_record_owner_paid_ai_usage(text, integer, integer) is
  'Records token totals only after a previously claimed owner-paid call; it never creates or refunds a paid call claim.';
