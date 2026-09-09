alter table public.h_runtime_knowledge_gaps
  drop constraint if exists h_runtime_knowledge_gaps_status_check;

alter table public.h_runtime_knowledge_gaps
  add constraint h_runtime_knowledge_gaps_status_check
  check (status in ('pending','researching','candidate','verified','failed','dismissed'));

alter table public.h_runtime_knowledge_gaps
  add column if not exists candidate_answer text,
  add column if not exists candidate_model text,
  add column if not exists research_error text,
  add column if not exists candidate_at timestamptz;

alter table public.h_runtime_knowledge_gaps
  drop constraint if exists h_runtime_knowledge_gaps_candidate_answer_check;
alter table public.h_runtime_knowledge_gaps
  add constraint h_runtime_knowledge_gaps_candidate_answer_check
  check (candidate_answer is null or char_length(candidate_answer) between 1 and 3000);

alter table public.h_runtime_knowledge_gaps
  drop constraint if exists h_runtime_knowledge_gaps_candidate_model_check;
alter table public.h_runtime_knowledge_gaps
  add constraint h_runtime_knowledge_gaps_candidate_model_check
  check (candidate_model is null or char_length(candidate_model) <= 200);

alter table public.h_runtime_knowledge_gaps
  drop constraint if exists h_runtime_knowledge_gaps_research_error_check;
alter table public.h_runtime_knowledge_gaps
  add constraint h_runtime_knowledge_gaps_research_error_check
  check (research_error is null or char_length(research_error) <= 500);

create or replace function public.h_claim_knowledge_gaps(p_limit integer default 3)
returns table (
  id uuid,
  user_key text,
  query_text text,
  priority text,
  research_attempts integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_limit integer := greatest(1, least(coalesce(p_limit, 3), 5));
begin
  return query
  with picked as (
    select g.id
    from public.h_runtime_knowledge_gaps g
    where (
      g.status in ('pending','failed')
      and (g.next_research_at is null or g.next_research_at <= now())
    ) or (
      g.status = 'researching'
      and g.updated_at < now() - interval '20 minutes'
    )
    order by
      case g.priority when 'important' then 0 when 'medium' then 1 else 2 end,
      g.occurrences desc,
      g.created_at asc
    for update skip locked
    limit v_limit
  ), claimed as (
    update public.h_runtime_knowledge_gaps g
    set status = 'researching',
        research_attempts = g.research_attempts + 1,
        last_researched_at = now(),
        updated_at = now(),
        research_error = null
    from picked
    where g.id = picked.id
    returning g.id, g.user_key, g.query_text, g.priority, g.research_attempts
  )
  select claimed.id, claimed.user_key, claimed.query_text, claimed.priority, claimed.research_attempts
  from claimed;
end;
$$;

create or replace function public.h_finish_knowledge_gap_research(
  p_id uuid,
  p_user_key text,
  p_candidate_answer text default null,
  p_candidate_model text default null,
  p_error text default null
)
returns table (status text, next_research_at timestamptz)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_answer text := nullif(left(regexp_replace(btrim(coalesce(p_candidate_answer, '')), '\s+', ' ', 'g'), 3000), '');
  v_model text := nullif(left(btrim(coalesce(p_candidate_model, '')), 200), '');
  v_error text := nullif(left(regexp_replace(btrim(coalesce(p_error, '')), '\s+', ' ', 'g'), 500), '');
  v_attempts integer;
begin
  if p_id is null or btrim(coalesce(p_user_key, '')) = '' then
    raise exception 'knowledge_gap_finish_invalid_identity';
  end if;

  select g.research_attempts into v_attempts
  from public.h_runtime_knowledge_gaps g
  where g.id = p_id
    and g.user_key = p_user_key
    and g.status = 'researching'
  for update;

  if not found then
    raise exception 'knowledge_gap_not_claimed';
  end if;

  if v_answer is not null then
    update public.h_runtime_knowledge_gaps g
    set status = 'candidate',
        candidate_answer = v_answer,
        candidate_model = v_model,
        candidate_at = now(),
        research_error = null,
        next_research_at = null,
        updated_at = now()
    where g.id = p_id and g.user_key = p_user_key;
  else
    update public.h_runtime_knowledge_gaps g
    set status = 'failed',
        candidate_answer = null,
        candidate_model = null,
        candidate_at = null,
        research_error = coalesce(v_error, 'learning_cycle_candidate_unavailable'),
        next_research_at = now() + make_interval(mins => case
          when v_attempts <= 1 then 60
          when v_attempts = 2 then 360
          when v_attempts = 3 then 1440
          else 4320
        end),
        updated_at = now()
    where g.id = p_id and g.user_key = p_user_key;
  end if;

  return query
  select g.status, g.next_research_at
  from public.h_runtime_knowledge_gaps g
  where g.id = p_id and g.user_key = p_user_key;
end;
$$;

revoke all on function public.h_claim_knowledge_gaps(integer) from public, anon, authenticated;
grant execute on function public.h_claim_knowledge_gaps(integer) to service_role;

revoke all on function public.h_finish_knowledge_gap_research(uuid,text,text,text,text) from public, anon, authenticated;
grant execute on function public.h_finish_knowledge_gap_research(uuid,text,text,text,text) to service_role;
