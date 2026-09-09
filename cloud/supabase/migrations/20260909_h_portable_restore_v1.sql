-- Atomic merge-only restore for H portable snapshot schema v1.
-- Credentials, routing identities, conversations, media, execution metadata and cloud
-- secrets are intentionally outside this schema.

create table if not exists public.h_runtime_portable_restores (
  user_key text not null,
  snapshot_digest text not null,
  schema_version integer not null default 1,
  restored_at timestamptz not null default now(),
  result jsonb not null default '{}'::jsonb,
  primary key (user_key, snapshot_digest),
  constraint h_runtime_portable_restores_digest_check check (snapshot_digest ~ '^[0-9a-f]{64}$'),
  constraint h_runtime_portable_restores_schema_check check (schema_version = 1),
  constraint h_runtime_portable_restores_result_object_check check (jsonb_typeof(result) = 'object')
);

alter table public.h_runtime_portable_restores enable row level security;
revoke all on table public.h_runtime_portable_restores from public, anon, authenticated;
grant select, insert, update on table public.h_runtime_portable_restores to service_role;

create or replace function public.h_restore_portable_snapshot_v1(
  p_user_key text,
  p_snapshot_digest text,
  p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  existing_restore jsonb;
  memory_item jsonb;
  task_item jsonb;
  reminder_item jsonb;
  learning_item jsonb;
  existing_learning public.h_runtime_learning_state%rowtype;
  normalized_body text;
  normalized_original text;
  source_task_id text;
  mapped_task_id bigint;
  destination_task_id bigint;
  task_map jsonb := '{}'::jsonb;
  merged_tags jsonb := '{}'::jsonb;
  tag_key text;
  tag_value text;
  inserted_memories integer := 0;
  skipped_memories integer := 0;
  inserted_tasks integer := 0;
  skipped_tasks integer := 0;
  inserted_reminders integer := 0;
  skipped_reminders integer := 0;
  learning_merged boolean := false;
  final_result jsonb;
  imported_created_at timestamptz;
  imported_updated_at timestamptz;
  imported_due_at timestamptz;
  imported_paused_at timestamptz;
  imported_completed_at timestamptz;
  imported_cancelled_at timestamptz;
  imported_cooldown_until timestamptz;
begin
  p_user_key := btrim(coalesce(p_user_key, ''));
  p_snapshot_digest := lower(btrim(coalesce(p_snapshot_digest, '')));

  if p_user_key = '' then
    raise exception 'portable_restore_user_key_required' using errcode = '22023';
  end if;
  if p_snapshot_digest !~ '^[0-9a-f]{64}$' then
    raise exception 'portable_restore_digest_invalid' using errcode = '22023';
  end if;
  if jsonb_typeof(p_payload) <> 'object'
     or p_payload->>'assistantIdentity' <> 'H'
     or p_payload->>'scope' <> 'portable_core_v1'
     or jsonb_typeof(p_payload->'memories') <> 'array'
     or jsonb_typeof(p_payload->'tasks') <> 'array'
     or jsonb_typeof(p_payload->'reminders') <> 'array'
  then
    raise exception 'portable_restore_payload_invalid' using errcode = '22023';
  end if;
  if jsonb_array_length(p_payload->'memories') > 500
     or jsonb_array_length(p_payload->'tasks') > 500
     or jsonb_array_length(p_payload->'reminders') > 500
  then
    raise exception 'portable_restore_payload_too_large' using errcode = '22023';
  end if;

  -- Serialize restores for the same H identity to keep natural-key dedupe deterministic.
  perform pg_advisory_xact_lock(hashtextextended(p_user_key, 0));

  select r.result into existing_restore
  from public.h_runtime_portable_restores r
  where r.user_key = p_user_key and r.snapshot_digest = p_snapshot_digest;

  if existing_restore is not null then
    return existing_restore || jsonb_build_object('idempotentReplay', true);
  end if;

  -- H memories remain subject to the existing database privacy trigger.
  for memory_item in select value from jsonb_array_elements(p_payload->'memories') loop
    normalized_body := left(regexp_replace(btrim(coalesce(memory_item->>'body', '')), '\s+', ' ', 'g'), 280);
    normalized_original := nullif(left(regexp_replace(btrim(coalesce(memory_item->>'originalText', '')), '\s+', ' ', 'g'), 500), '');
    if normalized_body = '' then
      raise exception 'portable_restore_memory_invalid' using errcode = '22023';
    end if;

    if exists (
      select 1 from public.h_runtime_memories m
      where m.user_key = p_user_key
        and m.category = memory_item->>'category'
        and m.body = normalized_body
    ) then
      skipped_memories := skipped_memories + 1;
    else
      imported_created_at := coalesce(nullif(memory_item->>'createdAt', '')::timestamptz, now());
      imported_updated_at := coalesce(nullif(memory_item->>'updatedAt', '')::timestamptz, imported_created_at);
      insert into public.h_runtime_memories (user_key, category, body, original_text, created_at, updated_at)
      values (p_user_key, memory_item->>'category', normalized_body, normalized_original, imported_created_at, imported_updated_at);
      inserted_memories := inserted_memories + 1;
    end if;
  end loop;

  -- Source task IDs are portability references only; target IDs are resolved locally.
  for task_item in select value from jsonb_array_elements(p_payload->'tasks') loop
    source_task_id := task_item->>'id';
    imported_due_at := nullif(task_item->>'dueAt', '')::timestamptz;

    select t.id into destination_task_id
    from public.h_runtime_tasks t
    where t.user_key = p_user_key
      and coalesce(t.title, '') = coalesce(task_item->>'title', '')
      and t.body = task_item->>'body'
      and t.task_type = task_item->>'taskType'
      and t.due_at is not distinct from imported_due_at
    order by t.id
    limit 1;

    if destination_task_id is null then
      imported_created_at := coalesce(nullif(task_item->>'createdAt', '')::timestamptz, now());
      imported_updated_at := coalesce(nullif(task_item->>'updatedAt', '')::timestamptz, imported_created_at);
      imported_paused_at := nullif(task_item->>'pausedAt', '')::timestamptz;
      imported_completed_at := nullif(task_item->>'completedAt', '')::timestamptz;
      imported_cancelled_at := nullif(task_item->>'cancelledAt', '')::timestamptz;

      insert into public.h_runtime_tasks (
        user_key, conversation_id, title, body, task_type, priority, priority_source,
        status, due_at, execution_plan, metadata, result_text, paused_at, completed_at,
        cancelled_at, created_at, updated_at
      ) values (
        p_user_key,
        null,
        nullif(task_item->>'title', ''),
        task_item->>'body',
        task_item->>'taskType',
        task_item->>'priority',
        'auto',
        task_item->>'status',
        imported_due_at,
        case task_item->>'priority'
          when 'important' then jsonb_build_object(
            'effort','important','source_target',6,'max_fallbacks',3,
            'cross_verify',true,'require_specialized_source',true,
            'retry_with_rephrase',true,'allow_unverified_claims',false
          )
          when 'medium' then jsonb_build_object(
            'effort','medium','source_target',4,'max_fallbacks',2,
            'cross_verify',true,'require_specialized_source',false,
            'retry_with_rephrase',true,'allow_unverified_claims',false
          )
          else jsonb_build_object(
            'effort','simple','source_target',2,'max_fallbacks',1,
            'cross_verify',false,'require_specialized_source',false,
            'retry_with_rephrase',false,'allow_unverified_claims',false
          )
        end,
        '{}'::jsonb,
        null,
        imported_paused_at,
        imported_completed_at,
        imported_cancelled_at,
        imported_created_at,
        imported_updated_at
      ) returning id into destination_task_id;
      inserted_tasks := inserted_tasks + 1;
    else
      skipped_tasks := skipped_tasks + 1;
    end if;

    task_map := task_map || jsonb_build_object(source_task_id, destination_task_id);
    destination_task_id := null;
  end loop;

  -- Reminder foreign keys are remapped to target task IDs; existing reminders are kept.
  for reminder_item in select value from jsonb_array_elements(p_payload->'reminders') loop
    source_task_id := nullif(reminder_item->>'taskId', '');
    mapped_task_id := null;
    if source_task_id is not null then
      if not (task_map ? source_task_id) then
        raise exception 'portable_restore_orphan_reminder_task' using errcode = '22023';
      end if;
      mapped_task_id := (task_map->>source_task_id)::bigint;
    end if;
    imported_due_at := nullif(reminder_item->>'dueAt', '')::timestamptz;

    if exists (
      select 1 from public.h_runtime_reminders r
      where r.user_key = p_user_key
        and coalesce(r.title, '') = coalesce(reminder_item->>'title', '')
        and r.body = reminder_item->>'body'
        and r.due_at is not distinct from imported_due_at
        and r.reminder_type = reminder_item->>'reminderType'
        and r.recurrence_rule is not distinct from nullif(reminder_item->>'recurrenceRule', '')
        and r.task_id is not distinct from mapped_task_id
    ) then
      skipped_reminders := skipped_reminders + 1;
    else
      imported_created_at := coalesce(nullif(reminder_item->>'createdAt', '')::timestamptz, now());
      imported_updated_at := coalesce(nullif(reminder_item->>'updatedAt', '')::timestamptz, imported_created_at);
      imported_completed_at := nullif(reminder_item->>'completedAt', '')::timestamptz;
      imported_cooldown_until := nullif(reminder_item->>'cooldownUntil', '')::timestamptz;

      insert into public.h_runtime_reminders (
        user_key, conversation_id, body, due_at, status, attempts, last_error, created_at,
        updated_at, sent_at, priority_class, priority_source, classification_reason,
        paused_at, task_id, title, original_text, interpreted_text, reminder_type,
        lifecycle_status, source, domain, recurrence_rule, person_name, location,
        cooldown_until, completed_at, delivery_channel
      ) values (
        p_user_key,
        null,
        reminder_item->>'body',
        imported_due_at,
        reminder_item->>'status',
        0,
        null,
        imported_created_at,
        imported_updated_at,
        null,
        reminder_item->>'priorityClass',
        'auto',
        null,
        case when reminder_item->>'status' = 'paused' then imported_updated_at else null end,
        mapped_task_id,
        nullif(reminder_item->>'title', ''),
        nullif(reminder_item->>'originalText', ''),
        nullif(reminder_item->>'interpretedText', ''),
        reminder_item->>'reminderType',
        reminder_item->>'lifecycleStatus',
        'IMPORTED',
        reminder_item->>'domain',
        nullif(reminder_item->>'recurrenceRule', ''),
        nullif(reminder_item->>'personName', ''),
        case when reminder_item->'location' = 'null'::jsonb then null else reminder_item->'location' end,
        imported_cooldown_until,
        imported_completed_at,
        reminder_item->>'deliveryChannel'
      );
      inserted_reminders := inserted_reminders + 1;
    end if;
  end loop;

  -- Learning is monotonic/conservative: no imported aggregate may weaken target state.
  learning_item := p_payload->'learningState';
  if learning_item is not null and learning_item <> 'null'::jsonb then
    select * into existing_learning
    from public.h_runtime_learning_state
    where user_key = p_user_key
    for update;

    if not found then
      insert into public.h_runtime_learning_state (
        user_key, first_met_at, last_interaction_at, turn_count, directness_score,
        technical_depth_score, programming_interest_score, solution_breadth_score,
        arabic_preference_score, concise_preference_score,
        code_replacement_preference_score, interaction_samples, interest_tags, updated_at
      ) values (
        p_user_key,
        (learning_item->>'firstMetAt')::timestamptz,
        (learning_item->>'lastInteractionAt')::timestamptz,
        (learning_item->>'turnCount')::bigint,
        (learning_item->>'directnessScore')::smallint,
        (learning_item->>'technicalDepthScore')::smallint,
        (learning_item->>'programmingInterestScore')::smallint,
        (learning_item->>'solutionBreadthScore')::smallint,
        (learning_item->>'arabicPreferenceScore')::smallint,
        (learning_item->>'concisePreferenceScore')::smallint,
        (learning_item->>'codeReplacementPreferenceScore')::smallint,
        (learning_item->>'interactionSamples')::bigint,
        coalesce(learning_item->'interestTags', '{}'::jsonb),
        now()
      );
    else
      merged_tags := coalesce(existing_learning.interest_tags, '{}'::jsonb);
      for tag_key, tag_value in
        select key, value from jsonb_each_text(coalesce(learning_item->'interestTags', '{}'::jsonb))
      loop
        merged_tags := jsonb_set(
          merged_tags,
          array[tag_key],
          to_jsonb(greatest(coalesce((merged_tags->>tag_key)::integer, 0), tag_value::integer)),
          true
        );
      end loop;

      update public.h_runtime_learning_state
      set
        first_met_at = least(first_met_at, (learning_item->>'firstMetAt')::timestamptz),
        last_interaction_at = greatest(last_interaction_at, (learning_item->>'lastInteractionAt')::timestamptz),
        turn_count = greatest(turn_count, (learning_item->>'turnCount')::bigint),
        directness_score = greatest(directness_score, (learning_item->>'directnessScore')::smallint),
        technical_depth_score = greatest(technical_depth_score, (learning_item->>'technicalDepthScore')::smallint),
        programming_interest_score = greatest(programming_interest_score, (learning_item->>'programmingInterestScore')::smallint),
        solution_breadth_score = greatest(solution_breadth_score, (learning_item->>'solutionBreadthScore')::smallint),
        arabic_preference_score = greatest(arabic_preference_score, (learning_item->>'arabicPreferenceScore')::smallint),
        concise_preference_score = greatest(concise_preference_score, (learning_item->>'concisePreferenceScore')::smallint),
        code_replacement_preference_score = greatest(code_replacement_preference_score, (learning_item->>'codeReplacementPreferenceScore')::smallint),
        interaction_samples = greatest(interaction_samples, (learning_item->>'interactionSamples')::bigint),
        interest_tags = merged_tags,
        updated_at = now()
      where user_key = p_user_key;
    end if;
    learning_merged := true;
  end if;

  final_result := jsonb_build_object(
    'ok', true,
    'schemaVersion', 1,
    'snapshotDigest', p_snapshot_digest,
    'mergeOnly', true,
    'deletedExistingState', false,
    'providerCredentialsImported', false,
    'routingIdentityImported', false,
    'rawMediaImported', false,
    'idempotentReplay', false,
    'memories', jsonb_build_object('inserted', inserted_memories, 'skippedExisting', skipped_memories),
    'tasks', jsonb_build_object('inserted', inserted_tasks, 'skippedExisting', skipped_tasks),
    'reminders', jsonb_build_object('inserted', inserted_reminders, 'skippedExisting', skipped_reminders),
    'learningMerged', learning_merged
  );

  insert into public.h_runtime_portable_restores (user_key, snapshot_digest, schema_version, restored_at, result)
  values (p_user_key, p_snapshot_digest, 1, now(), final_result);

  return final_result;
end;
$$;

revoke all on function public.h_restore_portable_snapshot_v1(text, text, jsonb) from public, anon, authenticated;
grant execute on function public.h_restore_portable_snapshot_v1(text, text, jsonb) to service_role;

comment on table public.h_runtime_portable_restores is
  'Idempotency ledger for successful H portable restores. Stores digest/result metadata only, never raw snapshots.';
comment on function public.h_restore_portable_snapshot_v1(text, text, jsonb) is
  'Atomically merge-restores validated H portable core v1 state, remapping task links and never deleting existing H state.';
