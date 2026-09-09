-- H Verification Engine.
--
-- Candidates produced by the Learning Cycle are not durable knowledge until a second,
-- independent research pass verifies them. Failed verification returns the gap to the
-- research queue; only verified canonical answers enter h_runtime_verified_knowledge.

alter table public.h_runtime_knowledge_gaps
  drop constraint if exists h_runtime_knowledge_gaps_status_check;

alter table public.h_runtime_knowledge_gaps
  add constraint h_runtime_knowledge_gaps_status_check
  check (status in ('pending','researching','candidate','verifying','verified','failed','dismissed'));

alter table public.h_runtime_knowledge_gaps
  add column if not exists verification_attempts integer not null default 0,
  add column if not exists verification_error text,
  add column if not exists last_verification_attempt_at timestamptz;

alter table public.h_runtime_knowledge_gaps
  drop constraint if exists h_runtime_knowledge_gaps_verification_error_check;
alter table public.h_runtime_knowledge_gaps
  add constraint h_runtime_knowledge_gaps_verification_error_check
  check (verification_error is null or char_length(verification_error) <= 500);

create table if not exists public.h_runtime_verified_knowledge (
  id uuid primary key default gen_random_uuid(),
  user_key text not null check (char_length(user_key) between 1 and 256),
  query_key text not null check (query_key ~ '^[0-9a-f]{64}$'),
  query_text text not null check (char_length(query_text) between 1 and 600),
  answer_text text not null check (char_length(answer_text) between 1 and 3000),
  source_gap_id uuid references public.h_runtime_knowledge_gaps(id) on delete set null,
  verification_method text not null default 'independent_research_v1'
    check (verification_method = 'independent_research_v1'),
  verification_model text check (verification_model is null or char_length(verification_model) <= 200),
  verified_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_used_at timestamptz,
  use_count bigint not null default 0 check (use_count >= 0),
  unique (user_key, query_key)
);

create index if not exists h_runtime_verified_knowledge_user_idx
  on public.h_runtime_verified_knowledge (user_key, updated_at desc);

alter table public.h_runtime_verified_knowledge enable row level security;
revoke all on table public.h_runtime_verified_knowledge from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_verified_knowledge to service_role;

create or replace function public.h_claim_knowledge_candidates(p_limit integer default 2)
returns table (
  id uuid,
  user_key text,
  query_key text,
  query_text text,
  candidate_answer text,
  priority text,
  verification_attempts integer
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_limit integer := greatest(1, least(coalesce(p_limit, 2), 4));
begin
  return query
  with picked as (
    select g.id
    from public.h_runtime_knowledge_gaps g
    where g.status = 'candidate'
       or (g.status = 'verifying' and g.updated_at < now() - interval '20 minutes')
    order by
      case g.priority when 'important' then 0 when 'medium' then 1 else 2 end,
      g.occurrences desc,
      g.candidate_at asc nulls last,
      g.created_at asc
    for update skip locked
    limit v_limit
  ), claimed as (
    update public.h_runtime_knowledge_gaps g
    set status = 'verifying',
        verification_attempts = g.verification_attempts + 1,
        last_verification_attempt_at = now(),
        verification_error = null,
        updated_at = now()
    from picked
    where g.id = picked.id
    returning g.id, g.user_key, g.query_key, g.query_text, g.candidate_answer, g.priority, g.verification_attempts
  )
  select claimed.id, claimed.user_key, claimed.query_key, claimed.query_text,
         claimed.candidate_answer, claimed.priority, claimed.verification_attempts
  from claimed
  where claimed.candidate_answer is not null;
end;
$$;

create or replace function public.h_finish_knowledge_verification(
  p_id uuid,
  p_user_key text,
  p_verified boolean,
  p_canonical_answer text default null,
  p_verification_model text default null,
  p_error text default null
)
returns table (status text, verified_knowledge_id uuid)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_gap public.h_runtime_knowledge_gaps%rowtype;
  v_answer text := nullif(left(regexp_replace(btrim(coalesce(p_canonical_answer, '')), '\s+', ' ', 'g'), 3000), '');
  v_model text := nullif(left(btrim(coalesce(p_verification_model, '')), 200), '');
  v_error text := nullif(left(regexp_replace(btrim(coalesce(p_error, '')), '\s+', ' ', 'g'), 500), '');
  v_verified_id uuid;
begin
  if p_id is null or btrim(coalesce(p_user_key, '')) = '' then
    raise exception 'knowledge_verification_invalid_identity';
  end if;

  select * into v_gap
  from public.h_runtime_knowledge_gaps g
  where g.id = p_id
    and g.user_key = p_user_key
    and g.status = 'verifying'
  for update;

  if not found then
    raise exception 'knowledge_candidate_not_claimed';
  end if;

  if p_verified and v_answer is not null then
    insert into public.h_runtime_verified_knowledge (
      user_key, query_key, query_text, answer_text, source_gap_id,
      verification_method, verification_model, verified_at, updated_at
    ) values (
      v_gap.user_key, v_gap.query_key, v_gap.query_text, v_answer, v_gap.id,
      'independent_research_v1', v_model, now(), now()
    )
    on conflict (user_key, query_key) do update
      set query_text = excluded.query_text,
          answer_text = excluded.answer_text,
          source_gap_id = excluded.source_gap_id,
          verification_method = excluded.verification_method,
          verification_model = excluded.verification_model,
          verified_at = excluded.verified_at,
          updated_at = excluded.updated_at
    returning id into v_verified_id;

    update public.h_runtime_knowledge_gaps g
    set status = 'verified',
        verified_at = now(),
        verification_summary = 'independent_research_v1',
        verification_error = null,
        research_error = null,
        next_research_at = null,
        updated_at = now()
    where g.id = p_id and g.user_key = p_user_key;
  else
    -- A rejected or inconclusive candidate is not retained as knowledge. Return it to
    -- the research queue with bounded backoff so later provider improvements can retry.
    update public.h_runtime_knowledge_gaps g
    set status = 'failed',
        candidate_answer = null,
        candidate_model = null,
        candidate_at = null,
        verification_error = coalesce(v_error, 'independent_verification_rejected'),
        research_error = coalesce(v_error, 'independent_verification_rejected'),
        next_research_at = now() + make_interval(mins => case
          when g.verification_attempts <= 1 then 360
          when g.verification_attempts = 2 then 1440
          else 4320
        end),
        updated_at = now()
    where g.id = p_id and g.user_key = p_user_key;
  end if;

  return query
  select g.status, v_verified_id
  from public.h_runtime_knowledge_gaps g
  where g.id = p_id and g.user_key = p_user_key;
end;
$$;

revoke all on function public.h_claim_knowledge_candidates(integer) from public, anon, authenticated;
grant execute on function public.h_claim_knowledge_candidates(integer) to service_role;

revoke all on function public.h_finish_knowledge_verification(uuid,text,boolean,text,text,text)
  from public, anon, authenticated;
grant execute on function public.h_finish_knowledge_verification(uuid,text,boolean,text,text,text)
  to service_role;
