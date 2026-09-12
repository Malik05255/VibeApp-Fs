-- Portable H v3 export staging.
-- A preparation call captures all portable sections in one SQL statement so the staged
-- pages belong to one database snapshot. Staging is short-lived, service-role only and
-- never exposes the runtime user key to the Android client.

create table if not exists public.h_runtime_portable_export_sessions (
  id uuid primary key,
  user_key text not null,
  page_size integer not null check (page_size between 1 and 500),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  counts jsonb not null default '{}'::jsonb
);

create table if not exists public.h_runtime_portable_export_pages (
  session_id uuid not null references public.h_runtime_portable_export_sessions(id) on delete cascade,
  section text not null check (section in ('memories','tasks','reminders','contacts','learning')),
  page_index integer not null check (page_index >= 0),
  item_count integer not null check (item_count >= 0 and item_count <= 500),
  items jsonb not null check (jsonb_typeof(items) = 'array'),
  primary key (session_id, section, page_index)
);

create index if not exists h_runtime_portable_export_sessions_expiry_idx
  on public.h_runtime_portable_export_sessions(expires_at);

alter table public.h_runtime_portable_export_sessions enable row level security;
alter table public.h_runtime_portable_export_pages enable row level security;

revoke all on table public.h_runtime_portable_export_sessions from public, anon, authenticated;
revoke all on table public.h_runtime_portable_export_pages from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_portable_export_sessions to service_role;
grant select, insert, update, delete on table public.h_runtime_portable_export_pages to service_role;

create or replace function public.h_prepare_portable_export_v3(
  p_user_key text,
  p_page_size integer default 200
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_session_id uuid := gen_random_uuid();
  v_now timestamptz := clock_timestamp();
  v_counts jsonb;
begin
  if coalesce(length(trim(p_user_key)), 0) = 0 then
    raise exception 'portable_export_user_key_required';
  end if;
  if p_page_size < 1 or p_page_size > 500 then
    raise exception 'portable_export_page_size_invalid';
  end if;

  -- Opportunistic cleanup. Sessions are intentionally short-lived and contain only
  -- already-sanitized portable owner data.
  delete from public.h_runtime_portable_export_sessions where expires_at <= v_now;

  insert into public.h_runtime_portable_export_sessions(
    id, user_key, page_size, created_at, expires_at
  ) values (
    v_session_id, p_user_key, p_page_size, v_now, v_now + interval '15 minutes'
  );

  -- IMPORTANT: all five portable sections are read by this single INSERT statement.
  -- Under PostgreSQL READ COMMITTED, one statement sees one snapshot, so memories,
  -- tasks, reminders, contacts and aggregate learning cannot be mixed across export
  -- moments while the session is being prepared.
  with portable_rows as (
    select 'memories'::text as section,
           row_number() over (order by created_at asc, id asc) - 1 as ordinal,
           jsonb_build_object(
             'id', id,
             'category', category,
             'body', body,
             'originalText', original_text,
             'createdAt', created_at,
             'updatedAt', updated_at
           ) as item
      from public.h_runtime_memories
     where user_key = p_user_key

    union all

    select 'tasks'::text,
           row_number() over (order by created_at asc, id asc) - 1,
           jsonb_build_object(
             'id', id::text,
             'title', title,
             'body', body,
             'taskType', task_type,
             'priority', priority,
             'status', status,
             'dueAt', due_at,
             'pausedAt', paused_at,
             'completedAt', completed_at,
             'cancelledAt', cancelled_at,
             'createdAt', created_at,
             'updatedAt', updated_at
           )
      from public.h_runtime_tasks
     where user_key = p_user_key

    union all

    select 'reminders'::text,
           row_number() over (order by created_at asc, id asc) - 1,
           jsonb_build_object(
             'id', id,
             'title', title,
             'body', body,
             'originalText', original_text,
             'interpretedText', interpreted_text,
             'dueAt', due_at,
             'status', status,
             'priorityClass', priority_class,
             'taskId', case when task_id is null then null else task_id::text end,
             'reminderType', reminder_type,
             'lifecycleStatus', lifecycle_status,
             'domain', domain,
             'recurrenceRule', recurrence_rule,
             'personName', person_name,
             'location', location,
             'cooldownUntil', cooldown_until,
             'completedAt', completed_at,
             'deliveryChannel', delivery_channel,
             'createdAt', created_at,
             'updatedAt', updated_at
           )
      from public.h_runtime_reminders
     where user_key = p_user_key

    union all

    select 'contacts'::text,
           row_number() over (order by created_at asc, id asc) - 1,
           jsonb_build_object(
             'id', id,
             'nameKey', name_key,
             'displayName', display_name,
             'targetWaId', target_wa_id,
             'createdAt', created_at,
             'updatedAt', updated_at
           )
      from public.h_runtime_contacts
     where user_key = p_user_key

    union all

    select 'learning'::text,
           0::bigint,
           jsonb_build_object(
             'firstMetAt', first_met_at,
             'lastInteractionAt', last_interaction_at,
             'turnCount', greatest(0, coalesce(turn_count, 0)),
             'directnessScore', least(20, greatest(0, coalesce(directness_score, 0))),
             'technicalDepthScore', least(20, greatest(0, coalesce(technical_depth_score, 0))),
             'programmingInterestScore', least(20, greatest(0, coalesce(programming_interest_score, 0))),
             'solutionBreadthScore', least(20, greatest(0, coalesce(solution_breadth_score, 0))),
             'arabicPreferenceScore', least(20, greatest(0, coalesce(arabic_preference_score, 0))),
             'concisePreferenceScore', least(20, greatest(0, coalesce(concise_preference_score, 0))),
             'codeReplacementPreferenceScore', least(20, greatest(0, coalesce(code_replacement_preference_score, 0))),
             'interactionSamples', greatest(0, coalesce(interaction_samples, 0)),
             'interestTags', coalesce(interest_tags, '{}'::jsonb),
             'updatedAt', updated_at
           )
      from public.h_runtime_learning_state
     where user_key = p_user_key
  ), grouped as (
    select section,
           floor(ordinal::numeric / p_page_size)::integer as page_index,
           count(*)::integer as item_count,
           jsonb_agg(item order by ordinal) as items
      from portable_rows
     group by section, floor(ordinal::numeric / p_page_size)::integer
  )
  insert into public.h_runtime_portable_export_pages(
    session_id, section, page_index, item_count, items
  )
  select v_session_id, section, page_index, item_count, items
    from grouped;

  select jsonb_build_object(
    'memories', coalesce(sum(item_count) filter (where section = 'memories'), 0),
    'tasks', coalesce(sum(item_count) filter (where section = 'tasks'), 0),
    'reminders', coalesce(sum(item_count) filter (where section = 'reminders'), 0),
    'contacts', coalesce(sum(item_count) filter (where section = 'contacts'), 0),
    'learningState', coalesce(sum(item_count) filter (where section = 'learning'), 0)
  ) into v_counts
    from public.h_runtime_portable_export_pages
   where session_id = v_session_id;

  update public.h_runtime_portable_export_sessions
     set counts = v_counts
   where id = v_session_id;

  return jsonb_build_object(
    'sessionId', v_session_id,
    'schemaVersion', 3,
    'pageSize', p_page_size,
    'createdAt', v_now,
    'expiresAt', v_now + interval '15 minutes',
    'counts', v_counts
  );
end;
$$;

revoke all on function public.h_prepare_portable_export_v3(text, integer) from public, anon, authenticated;
grant execute on function public.h_prepare_portable_export_v3(text, integer) to service_role;

create or replace function public.h_read_portable_export_v3_page(
  p_user_key text,
  p_session_id uuid,
  p_section text,
  p_page_index integer
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_session public.h_runtime_portable_export_sessions%rowtype;
  v_page public.h_runtime_portable_export_pages%rowtype;
begin
  select * into v_session
    from public.h_runtime_portable_export_sessions
   where id = p_session_id
     and user_key = p_user_key;

  if not found then raise exception 'portable_export_session_not_found'; end if;
  if v_session.expires_at <= clock_timestamp() then raise exception 'portable_export_session_expired'; end if;
  if p_section not in ('memories','tasks','reminders','contacts','learning') then
    raise exception 'portable_export_section_invalid';
  end if;
  if p_page_index < 0 then raise exception 'portable_export_page_invalid'; end if;

  select * into v_page
    from public.h_runtime_portable_export_pages
   where session_id = p_session_id
     and section = p_section
     and page_index = p_page_index;

  if not found then
    return jsonb_build_object(
      'found', false,
      'sessionId', p_session_id,
      'section', p_section,
      'pageIndex', p_page_index,
      'counts', v_session.counts,
      'expiresAt', v_session.expires_at
    );
  end if;

  return jsonb_build_object(
    'found', true,
    'sessionId', p_session_id,
    'section', p_section,
    'pageIndex', p_page_index,
    'itemCount', v_page.item_count,
    'items', v_page.items,
    'counts', v_session.counts,
    'expiresAt', v_session.expires_at
  );
end;
$$;

revoke all on function public.h_read_portable_export_v3_page(text, uuid, text, integer) from public, anon, authenticated;
grant execute on function public.h_read_portable_export_v3_page(text, uuid, text, integer) to service_role;
