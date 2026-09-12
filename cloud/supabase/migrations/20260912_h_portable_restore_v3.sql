-- Portable H v3 staged + atomic restore.
-- v1/v2 stay unchanged and backward compatible. v3 accepts bounded pages, validates the
-- complete staged set, then performs one merge-only PostgreSQL transaction.

alter table public.h_runtime_portable_restores
  drop constraint if exists h_runtime_portable_restores_schema_check;
alter table public.h_runtime_portable_restores
  add constraint h_runtime_portable_restores_schema_check
  check (schema_version in (1, 2, 3));

create table if not exists public.h_runtime_portable_import_sessions (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  export_session_id uuid not null,
  manifest_digest text not null check (manifest_digest ~ '^[0-9a-f]{64}$'),
  counts jsonb not null check (jsonb_typeof(counts) = 'object'),
  manifest_pages jsonb not null check (jsonb_typeof(manifest_pages) = 'array'),
  status text not null default 'staging' check (status in ('staging','restored')),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null
);

create table if not exists public.h_runtime_portable_import_pages (
  import_session_id uuid not null references public.h_runtime_portable_import_sessions(id) on delete cascade,
  section text not null check (section in ('memories','tasks','reminders','contacts','learning')),
  page_index integer not null check (page_index >= 0),
  page_digest text not null check (page_digest ~ '^[0-9a-f]{64}$'),
  item_count integer not null check (item_count between 1 and 500),
  items jsonb not null check (jsonb_typeof(items) = 'array'),
  primary key (import_session_id, section, page_index)
);

create index if not exists h_runtime_portable_import_sessions_expiry_idx
  on public.h_runtime_portable_import_sessions(expires_at);

alter table public.h_runtime_portable_import_sessions enable row level security;
alter table public.h_runtime_portable_import_pages enable row level security;
revoke all on table public.h_runtime_portable_import_sessions from public, anon, authenticated;
revoke all on table public.h_runtime_portable_import_pages from public, anon, authenticated;
grant select, insert, update, delete on table public.h_runtime_portable_import_sessions to service_role;
grant select, insert, update, delete on table public.h_runtime_portable_import_pages to service_role;

create or replace function public.h_begin_portable_restore_v3(
  p_user_key text,
  p_export_session_id uuid,
  p_manifest_digest text,
  p_counts jsonb,
  p_manifest_pages jsonb
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_id uuid := gen_random_uuid();
  v_now timestamptz := clock_timestamp();
  v_total bigint;
begin
  p_user_key := btrim(coalesce(p_user_key, ''));
  p_manifest_digest := lower(btrim(coalesce(p_manifest_digest, '')));
  if p_user_key = '' then raise exception 'portable_v3_restore_user_key_required'; end if;
  if p_manifest_digest !~ '^[0-9a-f]{64}$' then raise exception 'portable_v3_manifest_digest_invalid'; end if;
  if jsonb_typeof(p_counts) <> 'object' or jsonb_typeof(p_manifest_pages) <> 'array' then
    raise exception 'portable_v3_manifest_invalid';
  end if;
  if coalesce((p_counts->>'memories')::bigint, 0) > 20000
     or coalesce((p_counts->>'tasks')::bigint, 0) > 20000
     or coalesce((p_counts->>'reminders')::bigint, 0) > 20000
     or coalesce((p_counts->>'contacts')::bigint, 0) > 20000
     or coalesce((p_counts->>'learningState')::bigint, 0) > 1 then
    raise exception 'portable_v3_section_too_large';
  end if;
  v_total := coalesce((p_counts->>'memories')::bigint, 0)
           + coalesce((p_counts->>'tasks')::bigint, 0)
           + coalesce((p_counts->>'reminders')::bigint, 0)
           + coalesce((p_counts->>'contacts')::bigint, 0)
           + coalesce((p_counts->>'learningState')::bigint, 0);
  if v_total > 50000 then raise exception 'portable_v3_total_too_large'; end if;
  if jsonb_array_length(p_manifest_pages) > 1000 then raise exception 'portable_v3_manifest_pages_too_large'; end if;

  delete from public.h_runtime_portable_import_sessions where expires_at <= v_now;
  insert into public.h_runtime_portable_import_sessions(
    id,user_key,export_session_id,manifest_digest,counts,manifest_pages,status,created_at,expires_at
  ) values (
    v_id,p_user_key,p_export_session_id,p_manifest_digest,p_counts,p_manifest_pages,'staging',v_now,v_now + interval '30 minutes'
  );

  return jsonb_build_object(
    'importSessionId',v_id,
    'schemaVersion',3,
    'expiresAt',v_now + interval '30 minutes',
    'manifestDigest',p_manifest_digest
  );
end;
$$;

revoke all on function public.h_begin_portable_restore_v3(text,uuid,text,jsonb,jsonb) from public,anon,authenticated;
grant execute on function public.h_begin_portable_restore_v3(text,uuid,text,jsonb,jsonb) to service_role;

create or replace function public.h_stage_portable_restore_v3_page(
  p_user_key text,
  p_import_session_id uuid,
  p_section text,
  p_page_index integer,
  p_page_digest text,
  p_items jsonb
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_session public.h_runtime_portable_import_sessions%rowtype;
  v_descriptor jsonb;
  v_item_count integer;
  v_existing public.h_runtime_portable_import_pages%rowtype;
begin
  p_page_digest := lower(btrim(coalesce(p_page_digest,'')));
  if p_section not in ('memories','tasks','reminders','contacts','learning') then raise exception 'portable_v3_section_invalid'; end if;
  if p_page_index < 0 then raise exception 'portable_v3_page_index_invalid'; end if;
  if p_page_digest !~ '^[0-9a-f]{64}$' then raise exception 'portable_v3_page_digest_invalid'; end if;
  if jsonb_typeof(p_items) <> 'array' then raise exception 'portable_v3_page_items_invalid'; end if;
  v_item_count := jsonb_array_length(p_items);
  if v_item_count < 1 or v_item_count > 500 then raise exception 'portable_v3_page_count_invalid'; end if;
  if octet_length(p_items::text) > 4194304 then raise exception 'portable_v3_page_too_large'; end if;

  select * into v_session
    from public.h_runtime_portable_import_sessions
   where id = p_import_session_id and user_key = p_user_key;
  if not found then raise exception 'portable_v3_import_session_not_found'; end if;
  if v_session.expires_at <= clock_timestamp() then raise exception 'portable_v3_import_session_expired'; end if;
  if v_session.status <> 'staging' then raise exception 'portable_v3_import_session_closed'; end if;

  select value into v_descriptor
    from jsonb_array_elements(v_session.manifest_pages)
   where value->>'section' = p_section
     and (value->>'pageIndex')::integer = p_page_index
   limit 1;
  if v_descriptor is null then raise exception 'portable_v3_page_not_in_manifest'; end if;
  if lower(v_descriptor->>'digest') <> p_page_digest
     or (v_descriptor->>'itemCount')::integer <> v_item_count then
    raise exception 'portable_v3_page_manifest_mismatch';
  end if;

  select * into v_existing
    from public.h_runtime_portable_import_pages
   where import_session_id = p_import_session_id
     and section = p_section
     and page_index = p_page_index;
  if found then
    if v_existing.page_digest <> p_page_digest or v_existing.items <> p_items then
      raise exception 'portable_v3_page_conflict';
    end if;
    return jsonb_build_object('ok',true,'staged',true,'idempotentReplay',true);
  end if;

  insert into public.h_runtime_portable_import_pages(
    import_session_id,section,page_index,page_digest,item_count,items
  ) values (
    p_import_session_id,p_section,p_page_index,p_page_digest,v_item_count,p_items
  );
  return jsonb_build_object('ok',true,'staged',true,'idempotentReplay',false);
end;
$$;

revoke all on function public.h_stage_portable_restore_v3_page(text,uuid,text,integer,text,jsonb) from public,anon,authenticated;
grant execute on function public.h_stage_portable_restore_v3_page(text,uuid,text,integer,text,jsonb) to service_role;

create or replace function public.h_restore_portable_snapshot_v3(
  p_user_key text,
  p_import_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  s public.h_runtime_portable_import_sessions%rowtype;
  existing_restore jsonb;
  page_row record;
  item jsonb;
  source_task_id text;
  mapped_task_id bigint;
  destination_task_id bigint;
  task_map jsonb := '{}'::jsonb;
  normalized_body text;
  normalized_original text;
  existing_learning public.h_runtime_learning_state%rowtype;
  merged_tags jsonb := '{}'::jsonb;
  tag_key text;
  tag_value text;
  imported_created_at timestamptz;
  imported_updated_at timestamptz;
  imported_due_at timestamptz;
  imported_paused_at timestamptz;
  imported_completed_at timestamptz;
  imported_cancelled_at timestamptz;
  imported_cooldown_until timestamptz;
  inserted_memories integer := 0; skipped_memories integer := 0;
  inserted_tasks integer := 0; skipped_tasks integer := 0;
  inserted_reminders integer := 0; skipped_reminders integer := 0;
  inserted_contacts integer := 0; skipped_contacts integer := 0;
  learning_merged boolean := false;
  staged_pages integer;
  expected_pages integer;
  staged_counts jsonb;
  final_result jsonb;
begin
  p_user_key := btrim(coalesce(p_user_key,''));
  if p_user_key = '' then raise exception 'portable_v3_restore_user_key_required'; end if;

  select * into s from public.h_runtime_portable_import_sessions
   where id = p_import_session_id and user_key = p_user_key for update;
  if not found then raise exception 'portable_v3_import_session_not_found'; end if;
  if s.expires_at <= clock_timestamp() then raise exception 'portable_v3_import_session_expired'; end if;

  perform pg_advisory_xact_lock(hashtextextended(p_user_key, 3));
  select result into existing_restore from public.h_runtime_portable_restores
   where user_key = p_user_key and snapshot_digest = s.manifest_digest;
  if existing_restore is not null then return existing_restore || jsonb_build_object('idempotentReplay',true); end if;

  select count(*) into staged_pages from public.h_runtime_portable_import_pages where import_session_id = s.id;
  expected_pages := jsonb_array_length(s.manifest_pages);
  if staged_pages <> expected_pages then raise exception 'portable_v3_restore_pages_incomplete'; end if;

  select jsonb_build_object(
    'memories',coalesce(sum(item_count) filter(where section='memories'),0),
    'tasks',coalesce(sum(item_count) filter(where section='tasks'),0),
    'reminders',coalesce(sum(item_count) filter(where section='reminders'),0),
    'contacts',coalesce(sum(item_count) filter(where section='contacts'),0),
    'learningState',coalesce(sum(item_count) filter(where section='learning'),0)
  ) into staged_counts
  from public.h_runtime_portable_import_pages where import_session_id = s.id;
  if staged_counts <> s.counts then raise exception 'portable_v3_restore_counts_mismatch'; end if;

  -- Cross-page source identifiers must remain unique.
  if exists (
    select 1 from (
      select p.section, e.value->>'id' id, count(*) c
      from public.h_runtime_portable_import_pages p
      cross join lateral jsonb_array_elements(p.items) e(value)
      where p.import_session_id=s.id and p.section in ('memories','tasks','reminders','contacts')
      group by p.section,e.value->>'id' having count(*)>1
    ) d
  ) then raise exception 'portable_v3_duplicate_source_id'; end if;
  if exists (
    select 1 from (
      select e.value->>'nameKey' name_key,count(*) c
      from public.h_runtime_portable_import_pages p
      cross join lateral jsonb_array_elements(p.items) e(value)
      where p.import_session_id=s.id and p.section='contacts'
      group by e.value->>'nameKey' having count(*)>1
    ) d
  ) then raise exception 'portable_v3_duplicate_contact_name'; end if;

  -- Memories.
  for page_row in select items from public.h_runtime_portable_import_pages where import_session_id=s.id and section='memories' order by page_index loop
    for item in select value from jsonb_array_elements(page_row.items) loop
      normalized_body := left(regexp_replace(btrim(coalesce(item->>'body','')),'\s+',' ','g'),280);
      normalized_original := nullif(left(regexp_replace(btrim(coalesce(item->>'originalText','')),'\s+',' ','g'),500),'');
      if normalized_body='' then raise exception 'portable_v3_memory_invalid'; end if;
      if exists(select 1 from public.h_runtime_memories m where m.user_key=p_user_key and m.category=item->>'category' and m.body=normalized_body) then
        skipped_memories:=skipped_memories+1;
      else
        imported_created_at:=coalesce(nullif(item->>'createdAt','')::timestamptz,now());
        imported_updated_at:=coalesce(nullif(item->>'updatedAt','')::timestamptz,imported_created_at);
        insert into public.h_runtime_memories(user_key,category,body,original_text,created_at,updated_at)
        values(p_user_key,item->>'category',normalized_body,normalized_original,imported_created_at,imported_updated_at);
        inserted_memories:=inserted_memories+1;
      end if;
    end loop;
  end loop;

  -- Tasks first, building one source->destination map across every task page.
  for page_row in select items from public.h_runtime_portable_import_pages where import_session_id=s.id and section='tasks' order by page_index loop
    for item in select value from jsonb_array_elements(page_row.items) loop
      source_task_id:=item->>'id';
      imported_due_at:=nullif(item->>'dueAt','')::timestamptz;
      destination_task_id:=null;
      select t.id into destination_task_id from public.h_runtime_tasks t
       where t.user_key=p_user_key and coalesce(t.title,'')=coalesce(item->>'title','')
         and t.body=item->>'body' and t.task_type=item->>'taskType'
         and t.due_at is not distinct from imported_due_at
       order by t.id limit 1;
      if destination_task_id is null then
        imported_created_at:=coalesce(nullif(item->>'createdAt','')::timestamptz,now());
        imported_updated_at:=coalesce(nullif(item->>'updatedAt','')::timestamptz,imported_created_at);
        imported_paused_at:=nullif(item->>'pausedAt','')::timestamptz;
        imported_completed_at:=nullif(item->>'completedAt','')::timestamptz;
        imported_cancelled_at:=nullif(item->>'cancelledAt','')::timestamptz;
        insert into public.h_runtime_tasks(
          user_key,conversation_id,title,body,task_type,priority,priority_source,status,due_at,
          execution_plan,metadata,result_text,paused_at,completed_at,cancelled_at,created_at,updated_at
        ) values(
          p_user_key,null,nullif(item->>'title',''),item->>'body',item->>'taskType',item->>'priority','auto',item->>'status',imported_due_at,
          case item->>'priority'
            when 'important' then jsonb_build_object('effort','important','source_target',6,'max_fallbacks',3,'cross_verify',true,'require_specialized_source',true,'retry_with_rephrase',true,'allow_unverified_claims',false)
            when 'medium' then jsonb_build_object('effort','medium','source_target',4,'max_fallbacks',2,'cross_verify',true,'require_specialized_source',false,'retry_with_rephrase',true,'allow_unverified_claims',false)
            else jsonb_build_object('effort','simple','source_target',2,'max_fallbacks',1,'cross_verify',false,'require_specialized_source',false,'retry_with_rephrase',false,'allow_unverified_claims',false)
          end,
          '{}'::jsonb,null,imported_paused_at,imported_completed_at,imported_cancelled_at,imported_created_at,imported_updated_at
        ) returning id into destination_task_id;
        inserted_tasks:=inserted_tasks+1;
      else skipped_tasks:=skipped_tasks+1; end if;
      task_map:=task_map||jsonb_build_object(source_task_id,destination_task_id);
    end loop;
  end loop;

  -- Reminders after the complete task map exists, so references may cross page boundaries.
  for page_row in select items from public.h_runtime_portable_import_pages where import_session_id=s.id and section='reminders' order by page_index loop
    for item in select value from jsonb_array_elements(page_row.items) loop
      source_task_id:=nullif(item->>'taskId',''); mapped_task_id:=null;
      if source_task_id is not null then
        if not(task_map?source_task_id) then raise exception 'portable_v3_orphan_reminder_task'; end if;
        mapped_task_id:=(task_map->>source_task_id)::bigint;
      end if;
      imported_due_at:=nullif(item->>'dueAt','')::timestamptz;
      if exists(select 1 from public.h_runtime_reminders r where r.user_key=p_user_key
        and coalesce(r.title,'')=coalesce(item->>'title','') and r.body=item->>'body'
        and r.due_at is not distinct from imported_due_at and r.reminder_type=item->>'reminderType'
        and r.recurrence_rule is not distinct from nullif(item->>'recurrenceRule','')
        and r.task_id is not distinct from mapped_task_id) then
        skipped_reminders:=skipped_reminders+1;
      else
        imported_created_at:=coalesce(nullif(item->>'createdAt','')::timestamptz,now());
        imported_updated_at:=coalesce(nullif(item->>'updatedAt','')::timestamptz,imported_created_at);
        imported_completed_at:=nullif(item->>'completedAt','')::timestamptz;
        imported_cooldown_until:=nullif(item->>'cooldownUntil','')::timestamptz;
        insert into public.h_runtime_reminders(
          user_key,conversation_id,body,due_at,status,attempts,last_error,created_at,updated_at,sent_at,
          priority_class,priority_source,classification_reason,paused_at,task_id,title,original_text,interpreted_text,
          reminder_type,lifecycle_status,source,domain,recurrence_rule,person_name,location,cooldown_until,completed_at,delivery_channel
        ) values(
          p_user_key,null,item->>'body',imported_due_at,item->>'status',0,null,imported_created_at,imported_updated_at,null,
          item->>'priorityClass','auto',null,case when item->>'status'='paused' then imported_updated_at else null end,
          mapped_task_id,nullif(item->>'title',''),nullif(item->>'originalText',''),nullif(item->>'interpretedText',''),
          item->>'reminderType',item->>'lifecycleStatus','IMPORTED',item->>'domain',nullif(item->>'recurrenceRule',''),
          nullif(item->>'personName',''),case when item->'location'='null'::jsonb then null else item->'location' end,
          imported_cooldown_until,imported_completed_at,item->>'deliveryChannel'
        );
        inserted_reminders:=inserted_reminders+1;
      end if;
    end loop;
  end loop;

  -- Named contacts are merge-only; an existing normalized name wins.
  for page_row in select items from public.h_runtime_portable_import_pages where import_session_id=s.id and section='contacts' order by page_index loop
    for item in select value from jsonb_array_elements(page_row.items) loop
      if exists(select 1 from public.h_runtime_contacts c where c.user_key=p_user_key and c.name_key=item->>'nameKey') then
        skipped_contacts:=skipped_contacts+1;
      else
        imported_created_at:=coalesce(nullif(item->>'createdAt','')::timestamptz,now());
        imported_updated_at:=coalesce(nullif(item->>'updatedAt','')::timestamptz,imported_created_at);
        insert into public.h_runtime_contacts(user_key,name_key,display_name,target_wa_id,created_at,updated_at)
        values(p_user_key,item->>'nameKey',item->>'displayName',item->>'targetWaId',imported_created_at,imported_updated_at);
        inserted_contacts:=inserted_contacts+1;
      end if;
    end loop;
  end loop;

  -- Aggregate learning is monotonic/conservative, same semantics as v1/v2.
  select p.items->0 into item from public.h_runtime_portable_import_pages p
   where p.import_session_id=s.id and p.section='learning' and p.page_index=0;
  if item is not null and item<>'null'::jsonb then
    select * into existing_learning from public.h_runtime_learning_state where user_key=p_user_key for update;
    if not found then
      insert into public.h_runtime_learning_state(
        user_key,first_met_at,last_interaction_at,turn_count,directness_score,technical_depth_score,
        programming_interest_score,solution_breadth_score,arabic_preference_score,concise_preference_score,
        code_replacement_preference_score,interaction_samples,interest_tags,updated_at
      ) values(
        p_user_key,(item->>'firstMetAt')::timestamptz,(item->>'lastInteractionAt')::timestamptz,
        (item->>'turnCount')::bigint,(item->>'directnessScore')::smallint,(item->>'technicalDepthScore')::smallint,
        (item->>'programmingInterestScore')::smallint,(item->>'solutionBreadthScore')::smallint,
        (item->>'arabicPreferenceScore')::smallint,(item->>'concisePreferenceScore')::smallint,
        (item->>'codeReplacementPreferenceScore')::smallint,(item->>'interactionSamples')::bigint,
        coalesce(item->'interestTags','{}'::jsonb),now()
      );
    else
      merged_tags:=coalesce(existing_learning.interest_tags,'{}'::jsonb);
      for tag_key,tag_value in select key,value from jsonb_each_text(coalesce(item->'interestTags','{}'::jsonb)) loop
        merged_tags:=jsonb_set(merged_tags,array[tag_key],to_jsonb(greatest(coalesce((merged_tags->>tag_key)::integer,0),tag_value::integer)),true);
      end loop;
      update public.h_runtime_learning_state set
        first_met_at=least(first_met_at,(item->>'firstMetAt')::timestamptz),
        last_interaction_at=greatest(last_interaction_at,(item->>'lastInteractionAt')::timestamptz),
        turn_count=greatest(turn_count,(item->>'turnCount')::bigint),
        directness_score=greatest(directness_score,(item->>'directnessScore')::smallint),
        technical_depth_score=greatest(technical_depth_score,(item->>'technicalDepthScore')::smallint),
        programming_interest_score=greatest(programming_interest_score,(item->>'programmingInterestScore')::smallint),
        solution_breadth_score=greatest(solution_breadth_score,(item->>'solutionBreadthScore')::smallint),
        arabic_preference_score=greatest(arabic_preference_score,(item->>'arabicPreferenceScore')::smallint),
        concise_preference_score=greatest(concise_preference_score,(item->>'concisePreferenceScore')::smallint),
        code_replacement_preference_score=greatest(code_replacement_preference_score,(item->>'codeReplacementPreferenceScore')::smallint),
        interaction_samples=greatest(interaction_samples,(item->>'interactionSamples')::bigint),interest_tags=merged_tags,updated_at=now()
      where user_key=p_user_key;
    end if;
    learning_merged:=true;
  end if;

  final_result:=jsonb_build_object(
    'ok',true,'schemaVersion',3,'snapshotDigest',s.manifest_digest,'mergeOnly',true,'deletedExistingState',false,
    'providerCredentialsImported',false,'routingIdentityImported',false,'rawMediaImported',false,'idempotentReplay',false,
    'memories',jsonb_build_object('inserted',inserted_memories,'skippedExisting',skipped_memories),
    'tasks',jsonb_build_object('inserted',inserted_tasks,'skippedExisting',skipped_tasks),
    'reminders',jsonb_build_object('inserted',inserted_reminders,'skippedExisting',skipped_reminders),
    'contacts',jsonb_build_object('inserted',inserted_contacts,'skippedExisting',skipped_contacts),
    'learningMerged',learning_merged
  );

  insert into public.h_runtime_portable_restores(user_key,snapshot_digest,schema_version,restored_at,result)
  values(p_user_key,s.manifest_digest,3,now(),final_result);
  update public.h_runtime_portable_import_sessions set status='restored' where id=s.id;
  return final_result;
end;
$$;

revoke all on function public.h_restore_portable_snapshot_v3(text,uuid) from public,anon,authenticated;
grant execute on function public.h_restore_portable_snapshot_v3(text,uuid) to service_role;

comment on function public.h_restore_portable_snapshot_v3(text,uuid) is
  'Atomically merge-restores a complete validated staged H portable v3 session across bounded pages without deleting destination H state.';
