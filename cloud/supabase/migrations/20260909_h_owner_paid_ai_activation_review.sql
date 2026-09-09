-- A paid helper is activated only after the owner sees the live price snapshot and
-- explicitly confirms it in a second step. Pending API keys stay encrypted and expire
-- with the one-time setup token.

alter table public.h_runtime_ai_owner_paid_setup
  add column if not exists pending_secret_ciphertext text,
  add column if not exists pending_secret_iv text,
  add column if not exists pricing_ceiling jsonb,
  add column if not exists pricing_verified_at timestamptz;

alter table public.h_runtime_ai_owner_paid_setup
  add constraint h_ai_owner_paid_setup_pending_secret_pair_check
  check (
    (pending_secret_ciphertext is null and pending_secret_iv is null)
    or (pending_secret_ciphertext is not null and pending_secret_iv is not null)
  ) not valid;

alter table public.h_runtime_ai_owner_paid_setup
  validate constraint h_ai_owner_paid_setup_pending_secret_pair_check;

alter table public.h_runtime_ai_paid_usage_daily
  add column if not exists cost_usd numeric(14,6) not null default 0
    check (cost_usd >= 0);

-- Replace the first-pass token-only telemetry RPC with a four-argument version that also
-- stores OpenRouter's exact usage.cost when the provider supplies it. This never changes
-- the already-claimed call count.
drop function if exists public.h_record_owner_paid_ai_usage(text, integer, integer);

create or replace function public.h_record_owner_paid_ai_usage(
  p_route_id text,
  p_prompt_tokens integer,
  p_completion_tokens integer,
  p_cost_usd numeric
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
         cost_usd = u.cost_usd + greatest(0::numeric, least(coalesce(p_cost_usd, 0), 1000000::numeric)),
         last_used_at = now(),
         updated_at = now()
   where u.route_id = btrim(coalesce(p_route_id, ''))
     and u.usage_date = current_date;
  get diagnostics v_updated = row_count;
  return v_updated = 1;
end;
$$;

revoke all on function public.h_record_owner_paid_ai_usage(text, integer, integer, numeric) from public, anon, authenticated;
grant execute on function public.h_record_owner_paid_ai_usage(text, integer, integer, numeric) to service_role;

comment on column public.h_runtime_ai_owner_paid_setup.pricing_ceiling is
  'Live OpenRouter pricing snapshot shown to the owner before the separate activation confirmation.';
comment on column public.h_runtime_ai_paid_usage_daily.cost_usd is
  'Best-effort exact OpenRouter usage cost reported after successful owner-paid calls; call-count limits are enforced before requests.';
