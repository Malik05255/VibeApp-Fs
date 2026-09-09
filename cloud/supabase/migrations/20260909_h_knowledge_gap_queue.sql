create table if not exists public.h_runtime_knowledge_gaps (
  id uuid primary key default gen_random_uuid(),
  user_key text not null check (char_length(user_key) between 1 and 256),
  query_key text not null check (query_key ~ '^[0-9a-f]{64}$'),
  query_text text not null check (char_length(query_text) between 1 and 600),
  status text not null default 'pending' check (status in ('pending','researching','verified','failed','dismissed')),
  first_reason text not null check (first_reason in ('explicit_uncertainty','research_no_evidence','verifier_rejected','tool_unavailable')),
  last_reason text not null check (last_reason in ('explicit_uncertainty','research_no_evidence','verifier_rejected','tool_unavailable')),
  priority text not null default 'medium' check (priority in ('simple','medium','important')),
  occurrences integer not null default 1 check (occurrences >= 1),
  research_attempts integer not null default 0 check (research_attempts >= 0),
  next_research_at timestamptz,
  last_researched_at timestamptz,
  verified_at timestamptz,
  verification_summary text check (verification_summary is null or char_length(verification_summary) <= 1200),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  unique (user_key, query_key)
);

create index if not exists h_runtime_knowledge_gaps_pending_idx
  on public.h_runtime_knowledge_gaps (status, priority, next_research_at, created_at)
  where status in ('pending','failed');

create index if not exists h_runtime_knowledge_gaps_user_idx
  on public.h_runtime_knowledge_gaps (user_key, updated_at desc);

alter table public.h_runtime_knowledge_gaps enable row level security;
revoke all on table public.h_runtime_knowledge_gaps from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_knowledge_gaps to service_role;

create or replace function public.h_enqueue_knowledge_gap(
  p_user_key text,
  p_query_key text,
  p_query_text text,
  p_reason text,
  p_priority text default 'medium'
)
returns table (id uuid, status text, occurrences integer)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_query text := regexp_replace(trim(coalesce(p_query_text, '')), '\s+', ' ', 'g');
  v_priority text := case when p_priority in ('simple','medium','important') then p_priority else 'medium' end;
begin
  if coalesce(p_user_key, '') = '' or char_length(p_user_key) > 256 then
    raise exception 'knowledge_gap_invalid_user';
  end if;
  if coalesce(p_query_key, '') !~ '^[0-9a-f]{64}$' then
    raise exception 'knowledge_gap_invalid_key';
  end if;
  if char_length(v_query) < 1 or char_length(v_query) > 600 then
    raise exception 'knowledge_gap_invalid_query';
  end if;
  if p_reason not in ('explicit_uncertainty','research_no_evidence','verifier_rejected','tool_unavailable') then
    raise exception 'knowledge_gap_invalid_reason';
  end if;

  insert into public.h_runtime_knowledge_gaps (
    user_key,
    query_key,
    query_text,
    status,
    first_reason,
    last_reason,
    priority,
    occurrences,
    last_seen_at,
    updated_at
  ) values (
    p_user_key,
    p_query_key,
    v_query,
    'pending',
    p_reason,
    p_reason,
    v_priority,
    1,
    now(),
    now()
  )
  on conflict (user_key, query_key) do update
    set query_text = excluded.query_text,
        last_reason = excluded.last_reason,
        priority = case
          when public.h_runtime_knowledge_gaps.priority = 'important' or excluded.priority = 'important' then 'important'
          when public.h_runtime_knowledge_gaps.priority = 'medium' or excluded.priority = 'medium' then 'medium'
          else 'simple'
        end,
        occurrences = public.h_runtime_knowledge_gaps.occurrences + 1,
        last_seen_at = now(),
        updated_at = now(),
        status = case
          when public.h_runtime_knowledge_gaps.status in ('verified','dismissed') then public.h_runtime_knowledge_gaps.status
          else 'pending'
        end
  returning h_runtime_knowledge_gaps.id, h_runtime_knowledge_gaps.status, h_runtime_knowledge_gaps.occurrences
  into id, status, occurrences;

  return next;
end;
$$;

revoke all on function public.h_enqueue_knowledge_gap(text,text,text,text,text) from public, anon, authenticated;
grant execute on function public.h_enqueue_knowledge_gap(text,text,text,text,text) to service_role;
