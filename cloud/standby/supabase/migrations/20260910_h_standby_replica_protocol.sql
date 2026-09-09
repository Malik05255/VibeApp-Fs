-- Exact H standby-replication protocol.
--
-- This function is intended to exist on a dedicated H standby project after the normal H
-- schema has been deployed there. It is inert until that project is explicitly marked as
-- a standby with replica writes enabled. Unlike Move H, this is an operational mirror:
-- current task/reminder state and deletions must be reflected exactly so failover cannot
-- resurrect completed/cancelled work or resend stale reminders.

create or replace function public.h_apply_standby_replica_v1(p_snapshot jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_runtime jsonb;
  v_generated_at timestamptz;
  v_digest text;
  v_counts jsonb;
  v_lag_seconds numeric;
  v_task_max bigint;
begin
  select value into v_runtime
    from public.h_runtime_state
   where key = 'standby_runtime'
   for update;

  if coalesce(v_runtime->>'runtime_role', '') <> 'standby'
     or coalesce((v_runtime->>'dedicated_h_standby')::boolean, false) is not true
     or coalesce((v_runtime->>'allow_replica_writes')::boolean, false) is not true
     or coalesce((v_runtime->>'promoted')::boolean, false) is true
  then
    raise exception 'h_standby_replica_not_enabled' using errcode = '42501';
  end if;

  if jsonb_typeof(p_snapshot) <> 'object'
     or p_snapshot->>'format' <> 'h-standby-replica'
     or (p_snapshot->>'version')::integer <> 1
     or p_snapshot->>'assistantIdentity' <> 'H'
  then
    raise exception 'h_standby_replica_snapshot_invalid' using errcode = '22023';
  end if;

  v_generated_at := nullif(p_snapshot->>'generatedAt', '')::timestamptz;
  v_digest := lower(btrim(coalesce(p_snapshot->>'digest', '')));
  v_counts := coalesce(p_snapshot->'counts', '{}'::jsonb);
  if v_generated_at is null
     or v_generated_at > now() + interval '5 minutes'
     or v_generated_at < now() - interval '15 minutes'
     or v_digest !~ '^[0-9a-f]{64}$'
  then
    raise exception 'h_standby_replica_snapshot_stale_or_invalid' using errcode = '22023';
  end if;

  if jsonb_typeof(p_snapshot->'memories') <> 'array'
     or jsonb_typeof(p_snapshot->'tasks') <> 'array'
     or jsonb_typeof(p_snapshot->'reminders') <> 'array'
     or jsonb_typeof(p_snapshot->'contacts') <> 'array'
     or jsonb_typeof(p_snapshot->'learningState') <> 'array'
     or jsonb_typeof(p_snapshot->'knowledgeGaps') <> 'array'
     or jsonb_typeof(p_snapshot->'verifiedKnowledge') <> 'array'
  then
    raise exception 'h_standby_replica_sections_invalid' using errcode = '22023';
  end if;

  if jsonb_array_length(p_snapshot->'memories') > 1000
     or jsonb_array_length(p_snapshot->'tasks') > 1000
     or jsonb_array_length(p_snapshot->'reminders') > 1000
     or jsonb_array_length(p_snapshot->'contacts') > 1000
     or jsonb_array_length(p_snapshot->'learningState') > 1000
     or jsonb_array_length(p_snapshot->'knowledgeGaps') > 1000
     or jsonb_array_length(p_snapshot->'verifiedKnowledge') > 1000
  then
    raise exception 'h_standby_replica_requires_pagination' using errcode = '54000';
  end if;

  perform pg_advisory_xact_lock(hashtext('h-standby-replica-v1'));

  delete from public.h_runtime_verified_knowledge;
  delete from public.h_runtime_knowledge_gaps;
  delete from public.h_runtime_reminders;
  delete from public.h_runtime_tasks;
  delete from public.h_runtime_contacts;
  delete from public.h_runtime_memories;
  delete from public.h_runtime_learning_state;

  insert into public.h_runtime_memories
  select * from jsonb_populate_recordset(null::public.h_runtime_memories, p_snapshot->'memories');

  insert into public.h_runtime_contacts
  select * from jsonb_populate_recordset(null::public.h_runtime_contacts, p_snapshot->'contacts');

  insert into public.h_runtime_tasks
  select * from jsonb_populate_recordset(null::public.h_runtime_tasks, p_snapshot->'tasks');

  insert into public.h_runtime_reminders
  select * from jsonb_populate_recordset(null::public.h_runtime_reminders, p_snapshot->'reminders');

  insert into public.h_runtime_learning_state
  select * from jsonb_populate_recordset(null::public.h_runtime_learning_state, p_snapshot->'learningState');

  insert into public.h_runtime_knowledge_gaps
  select * from jsonb_populate_recordset(null::public.h_runtime_knowledge_gaps, p_snapshot->'knowledgeGaps');

  insert into public.h_runtime_verified_knowledge
  select * from jsonb_populate_recordset(null::public.h_runtime_verified_knowledge, p_snapshot->'verifiedKnowledge');

  select max(id) into v_task_max from public.h_runtime_tasks;
  if v_task_max is not null then
    perform setval(pg_get_serial_sequence('public.h_runtime_tasks', 'id'), v_task_max, true);
  end if;

  v_lag_seconds := greatest(0, extract(epoch from (now() - v_generated_at)));

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'standby_replication',
    jsonb_build_object(
      'mode', 'continuous',
      'protocol', 'exact_mirror_v1',
      'last_replicated_at', now(),
      'source_generated_at', v_generated_at,
      'lag_seconds', v_lag_seconds,
      'last_digest', v_digest,
      'counts', v_counts,
      'exact_mirror', true,
      'provider_credentials_replicated', false,
      'runtime_secrets_replicated', false,
      'routing_identities_replicated', false,
      'raw_media_replicated', false
    ),
    now()
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  return jsonb_build_object(
    'ok', true,
    'protocol', 'exact_mirror_v1',
    'digest', v_digest,
    'lagSeconds', v_lag_seconds,
    'counts', v_counts,
    'exactMirror', true,
    'runtimeSecretsReplicated', false,
    'providerCredentialsReplicated', false
  );
end;
$$;

revoke all on function public.h_apply_standby_replica_v1(jsonb) from public, anon, authenticated;
grant execute on function public.h_apply_standby_replica_v1(jsonb) to service_role;

comment on function public.h_apply_standby_replica_v1(jsonb) is
  'Applies an exact bounded mirror of H operational/user state to an explicitly enabled dedicated standby. It is not Move H and does not copy runtime/provider secrets.';
