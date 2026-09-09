-- Durable, provider-independent H learning state.
-- No raw prompt, response, memory body, attachment, credential, or provider transcript is stored here.

create table if not exists public.h_runtime_learning_state (
  user_key text primary key,
  first_met_at timestamptz not null default now(),
  last_interaction_at timestamptz not null default now(),
  turn_count bigint not null default 0 check (turn_count >= 0),
  directness_score smallint not null default 0 check (directness_score between 0 and 20),
  technical_depth_score smallint not null default 0 check (technical_depth_score between 0 and 20),
  programming_interest_score smallint not null default 0 check (programming_interest_score between 0 and 20),
  solution_breadth_score smallint not null default 0 check (solution_breadth_score between 0 and 20),
  arabic_preference_score smallint not null default 0 check (arabic_preference_score between 0 and 20),
  concise_preference_score smallint not null default 0 check (concise_preference_score between 0 and 20),
  code_replacement_preference_score smallint not null default 0 check (code_replacement_preference_score between 0 and 20),
  interaction_samples bigint not null default 0 check (interaction_samples >= 0),
  interest_tags jsonb not null default '{}'::jsonb check (jsonb_typeof(interest_tags) = 'object'),
  updated_at timestamptz not null default now()
);

create table if not exists public.h_runtime_learning_events (
  user_key text not null,
  event_id uuid not null,
  occurred_at timestamptz not null,
  signal jsonb not null check (jsonb_typeof(signal) = 'object'),
  created_at timestamptz not null default now(),
  primary key (user_key, event_id)
);

create index if not exists h_runtime_learning_events_user_created_idx
  on public.h_runtime_learning_events (user_key, created_at desc);

alter table public.h_runtime_learning_state enable row level security;
alter table public.h_runtime_learning_events enable row level security;

-- Merge two tag->count maps without ever decreasing a learned count.
create or replace function public.h_merge_learning_tag_max(base_tags jsonb, incoming_tags jsonb)
returns jsonb
language plpgsql
immutable
as $$
declare
  result jsonb := coalesce(base_tags, '{}'::jsonb);
  pair record;
  incoming_count integer;
  existing_count integer;
begin
  if jsonb_typeof(coalesce(incoming_tags, '{}'::jsonb)) <> 'object' then
    return result;
  end if;

  for pair in select key, value from jsonb_each(incoming_tags)
  loop
    if pair.key not in ('programming','android','github','authentication','ai','ui-ux','cloud') then
      continue;
    end if;
    incoming_count := least(1000000, greatest(0, coalesce((pair.value #>> '{}')::integer, 0)));
    existing_count := least(1000000, greatest(0, coalesce((result ->> pair.key)::integer, 0)));
    result := jsonb_set(result, array[pair.key], to_jsonb(greatest(existing_count, incoming_count)), true);
  end loop;
  return result;
exception when others then
  return coalesce(base_tags, '{}'::jsonb);
end;
$$;

-- Increment tag counts for one newly accepted, de-duplicated learning event.
create or replace function public.h_increment_learning_tags(base_tags jsonb, event_tags jsonb)
returns jsonb
language plpgsql
immutable
as $$
declare
  result jsonb := coalesce(base_tags, '{}'::jsonb);
  tag_value text;
  existing_count integer;
begin
  if jsonb_typeof(coalesce(event_tags, '[]'::jsonb)) <> 'array' then
    return result;
  end if;

  for tag_value in select jsonb_array_elements_text(event_tags)
  loop
    if tag_value not in ('programming','android','github','authentication','ai','ui-ux','cloud') then
      continue;
    end if;
    existing_count := least(999999, greatest(0, coalesce((result ->> tag_value)::integer, 0)));
    result := jsonb_set(result, array[tag_value], to_jsonb(existing_count + 1), true);
  end loop;
  return result;
exception when others then
  return coalesce(base_tags, '{}'::jsonb);
end;
$$;

-- One atomic event application. The event row is the idempotency key: retries are safe.
create or replace function public.h_apply_learning_event(
  p_user_key text,
  p_event_id uuid,
  p_occurred_at timestamptz,
  p_signal jsonb
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  inserted_count integer := 0;
  s jsonb := coalesce(p_signal, '{}'::jsonb);
begin
  if nullif(trim(p_user_key), '') is null then
    raise exception 'invalid_user_key';
  end if;

  insert into public.h_runtime_learning_events(user_key, event_id, occurred_at, signal)
  values (p_user_key, p_event_id, p_occurred_at, s)
  on conflict (user_key, event_id) do nothing;
  get diagnostics inserted_count = row_count;

  if inserted_count = 0 then
    return false;
  end if;

  insert into public.h_runtime_learning_state(
    user_key,
    first_met_at,
    last_interaction_at,
    turn_count,
    directness_score,
    technical_depth_score,
    programming_interest_score,
    solution_breadth_score,
    arabic_preference_score,
    concise_preference_score,
    code_replacement_preference_score,
    interaction_samples,
    interest_tags,
    updated_at
  ) values (
    p_user_key,
    p_occurred_at,
    p_occurred_at,
    1,
    case when coalesce((s ->> 'directness')::boolean, false) then 1 else 0 end,
    case when coalesce((s ->> 'technical_depth')::boolean, false) then 1 else 0 end,
    case when coalesce((s ->> 'programming_interest')::boolean, false) then 1 else 0 end,
    case when coalesce((s ->> 'solution_breadth')::boolean, false) then 1 else 0 end,
    case when coalesce((s ->> 'arabic_preference')::boolean, false) then 1 else 0 end,
    case when coalesce((s ->> 'concise_preference')::boolean, false) then 1 else 0 end,
    case when coalesce((s ->> 'code_replacement_preference')::boolean, false) then 1 else 0 end,
    1,
    public.h_increment_learning_tags('{}'::jsonb, s -> 'interest_tags'),
    now()
  )
  on conflict (user_key) do update set
    first_met_at = least(h_runtime_learning_state.first_met_at, excluded.first_met_at),
    last_interaction_at = greatest(h_runtime_learning_state.last_interaction_at, excluded.last_interaction_at),
    turn_count = h_runtime_learning_state.turn_count + 1,
    directness_score = least(20, h_runtime_learning_state.directness_score + excluded.directness_score),
    technical_depth_score = least(20, h_runtime_learning_state.technical_depth_score + excluded.technical_depth_score),
    programming_interest_score = least(20, h_runtime_learning_state.programming_interest_score + excluded.programming_interest_score),
    solution_breadth_score = least(20, h_runtime_learning_state.solution_breadth_score + excluded.solution_breadth_score),
    arabic_preference_score = least(20, h_runtime_learning_state.arabic_preference_score + excluded.arabic_preference_score),
    concise_preference_score = least(20, h_runtime_learning_state.concise_preference_score + excluded.concise_preference_score),
    code_replacement_preference_score = least(20, h_runtime_learning_state.code_replacement_preference_score + excluded.code_replacement_preference_score),
    interaction_samples = h_runtime_learning_state.interaction_samples + 1,
    interest_tags = public.h_increment_learning_tags(h_runtime_learning_state.interest_tags, s -> 'interest_tags'),
    updated_at = now();

  return true;
end;
$$;

-- One-time migration bridge for learning accumulated before cloud learning existed.
-- This is monotonic: a stale device can never decrease the cloud profile.
create or replace function public.h_seed_learning_state(
  p_user_key text,
  p_baseline jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  b jsonb := coalesce(p_baseline, '{}'::jsonb);
  first_ms bigint := greatest(0, coalesce((b ->> 'first_met_at_ms')::bigint, 0));
  last_ms bigint := greatest(0, coalesce((b ->> 'last_interaction_at_ms')::bigint, 0));
begin
  if nullif(trim(p_user_key), '') is null then
    raise exception 'invalid_user_key';
  end if;

  insert into public.h_runtime_learning_state(
    user_key,
    first_met_at,
    last_interaction_at,
    turn_count,
    directness_score,
    technical_depth_score,
    programming_interest_score,
    solution_breadth_score,
    arabic_preference_score,
    concise_preference_score,
    code_replacement_preference_score,
    interaction_samples,
    interest_tags,
    updated_at
  ) values (
    p_user_key,
    case when first_ms > 0 then to_timestamp(first_ms / 1000.0) else now() end,
    case when last_ms > 0 then to_timestamp(last_ms / 1000.0) else now() end,
    least(1000000000, greatest(0, coalesce((b ->> 'turn_count')::bigint, 0))),
    least(20, greatest(0, coalesce((b ->> 'directness_score')::integer, 0))),
    least(20, greatest(0, coalesce((b ->> 'technical_depth_score')::integer, 0))),
    least(20, greatest(0, coalesce((b ->> 'programming_interest_score')::integer, 0))),
    least(20, greatest(0, coalesce((b ->> 'solution_breadth_score')::integer, 0))),
    least(20, greatest(0, coalesce((b ->> 'arabic_preference_score')::integer, 0))),
    least(20, greatest(0, coalesce((b ->> 'concise_preference_score')::integer, 0))),
    least(20, greatest(0, coalesce((b ->> 'code_replacement_preference_score')::integer, 0))),
    least(1000000000, greatest(0, coalesce((b ->> 'interaction_samples')::bigint, 0))),
    public.h_merge_learning_tag_max('{}'::jsonb, b -> 'interest_tags'),
    now()
  )
  on conflict (user_key) do update set
    first_met_at = least(h_runtime_learning_state.first_met_at, excluded.first_met_at),
    last_interaction_at = greatest(h_runtime_learning_state.last_interaction_at, excluded.last_interaction_at),
    turn_count = greatest(h_runtime_learning_state.turn_count, excluded.turn_count),
    directness_score = greatest(h_runtime_learning_state.directness_score, excluded.directness_score),
    technical_depth_score = greatest(h_runtime_learning_state.technical_depth_score, excluded.technical_depth_score),
    programming_interest_score = greatest(h_runtime_learning_state.programming_interest_score, excluded.programming_interest_score),
    solution_breadth_score = greatest(h_runtime_learning_state.solution_breadth_score, excluded.solution_breadth_score),
    arabic_preference_score = greatest(h_runtime_learning_state.arabic_preference_score, excluded.arabic_preference_score),
    concise_preference_score = greatest(h_runtime_learning_state.concise_preference_score, excluded.concise_preference_score),
    code_replacement_preference_score = greatest(h_runtime_learning_state.code_replacement_preference_score, excluded.code_replacement_preference_score),
    interaction_samples = greatest(h_runtime_learning_state.interaction_samples, excluded.interaction_samples),
    interest_tags = public.h_merge_learning_tag_max(h_runtime_learning_state.interest_tags, excluded.interest_tags),
    updated_at = now();

  return (
    select to_jsonb(s) from public.h_runtime_learning_state s where s.user_key = p_user_key
  );
end;
$$;

revoke all on table public.h_runtime_learning_state from anon, authenticated;
revoke all on table public.h_runtime_learning_events from anon, authenticated;
revoke all on function public.h_apply_learning_event(text, uuid, timestamptz, jsonb) from public, anon, authenticated;
revoke all on function public.h_seed_learning_state(text, jsonb) from public, anon, authenticated;
grant execute on function public.h_apply_learning_event(text, uuid, timestamptz, jsonb) to service_role;
grant execute on function public.h_seed_learning_state(text, jsonb) to service_role;
