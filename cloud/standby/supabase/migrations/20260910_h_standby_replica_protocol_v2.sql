-- H standby exact-mirror protocol v2.
--
-- v2 extends the operational mirror with the durable H identity mappings needed for
-- failover continuity. Only identity_secret-keyed HMAC fingerprints and encrypted runtime
-- user keys are mirrored. Raw Google subjects, raw owner/friend WhatsApp ids, provider
-- credentials, runtime polling secrets, and raw media are never included.

create table if not exists public.h_runtime_owner_identities (
  wa_fingerprint text primary key,
  label text null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_owner_identities_fingerprint_format check (wa_fingerprint ~ '^[0-9a-f]{64}$')
);

create table if not exists public.h_runtime_friend_identities (
  wa_fingerprint text primary key,
  label text null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_friend_identities_fingerprint_format check (wa_fingerprint ~ '^[0-9a-f]{64}$')
);

create table if not exists public.h_runtime_app_identities (
  google_subject_fingerprint text primary key,
  google_audience text not null,
  runtime_user_key_ciphertext text not null,
  active boolean not null default true,
  linked_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_app_identities_google_subject_fingerprint_check
    check (google_subject_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint h_runtime_app_identities_google_audience_check
    check (length(google_audience) between 1 and 255),
  constraint h_runtime_app_identities_runtime_user_key_ciphertext_check
    check (length(runtime_user_key_ciphertext) between 20 and 1024)
);

alter table public.h_runtime_owner_identities enable row level security;
alter table public.h_runtime_friend_identities enable row level security;
alter table public.h_runtime_app_identities enable row level security;

revoke all on table public.h_runtime_owner_identities from public, anon, authenticated;
revoke all on table public.h_runtime_friend_identities from public, anon, authenticated;
revoke all on table public.h_runtime_app_identities from public, anon, authenticated;

grant all on table public.h_runtime_owner_identities to service_role;
grant all on table public.h_runtime_friend_identities to service_role;
grant all on table public.h_runtime_app_identities to service_role;

comment on table public.h_runtime_owner_identities is
  'Standby mirror of H owner identities as identity_secret-keyed HMAC fingerprints only; raw WhatsApp ids are never stored.';
comment on table public.h_runtime_friend_identities is
  'Standby mirror of authorized H friend identities as identity_secret-keyed HMAC fingerprints only; raw WhatsApp ids are never stored.';
comment on table public.h_runtime_app_identities is
  'Standby mirror of verified Google-subject fingerprints and encrypted H runtime user keys; raw Google subjects and WhatsApp ids are never stored.';

create or replace function public.h_apply_standby_replica_v2(p_snapshot jsonb)
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

  if not exists (
    select 1 from public.h_runtime_config
     where key = 'identity_secret'
       and nullif(btrim(secret_value), '') is not null
  ) then
    raise exception 'h_standby_identity_secret_missing' using errcode = '42501';
  end if;

  if jsonb_typeof(p_snapshot) <> 'object'
     or p_snapshot->>'format' <> 'h-standby-replica'
     or (p_snapshot->>'version')::integer <> 2
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
     or jsonb_typeof(p_snapshot->'idempotency') <> 'array'
     or jsonb_typeof(p_snapshot->'appIdentities') <> 'array'
     or jsonb_typeof(p_snapshot->'ownerIdentities') <> 'array'
     or jsonb_typeof(p_snapshot->'friendIdentities') <> 'array'
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
     or jsonb_array_length(p_snapshot->'idempotency') > 2000
     or jsonb_array_length(p_snapshot->'appIdentities') > 1000
     or jsonb_array_length(p_snapshot->'ownerIdentities') > 1000
     or jsonb_array_length(p_snapshot->'friendIdentities') > 1000
  then
    raise exception 'h_standby_replica_requires_pagination' using errcode = '54000';
  end if;

  perform pg_advisory_xact_lock(hashtext('h-standby-replica-v2'));

  delete from public.h_runtime_inbox
   where raw->>'source' = 'standby_replicated_dedupe';

  insert into public.h_runtime_inbox (
    message_key,
    raw,
    status,
    error,
    received_at,
    updated_at,
    processed_at,
    reply_text
  )
  select
    d.message_key,
    jsonb_build_object('source', 'standby_replicated_dedupe'),
    d.status,
    d.error,
    d.received_at,
    d.updated_at,
    d.processed_at,
    d.reply_text
  from jsonb_to_recordset(p_snapshot->'idempotency') as d(
    message_key text,
    status text,
    error text,
    received_at timestamptz,
    updated_at timestamptz,
    processed_at timestamptz,
    reply_text text
  )
  where nullif(btrim(d.message_key), '') is not null
    and d.status in ('processing', 'processed', 'failed')
  on conflict (message_key) do nothing;

  delete from public.h_runtime_verified_knowledge;
  delete from public.h_runtime_knowledge_gaps;
  delete from public.h_runtime_reminders;
  delete from public.h_runtime_tasks;
  delete from public.h_runtime_contacts;
  delete from public.h_runtime_memories;
  delete from public.h_runtime_learning_state;
  delete from public.h_runtime_app_identities;
  delete from public.h_runtime_friend_identities;
  delete from public.h_runtime_owner_identities;

  insert into public.h_runtime_owner_identities
  select * from jsonb_populate_recordset(null::public.h_runtime_owner_identities, p_snapshot->'ownerIdentities');

  insert into public.h_runtime_friend_identities
  select * from jsonb_populate_recordset(null::public.h_runtime_friend_identities, p_snapshot->'friendIdentities');

  insert into public.h_runtime_app_identities
  select * from jsonb_populate_recordset(null::public.h_runtime_app_identities, p_snapshot->'appIdentities');

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
      'protocol', 'exact_mirror_v2',
      'last_replicated_at', now(),
      'source_generated_at', v_generated_at,
      'lag_seconds', v_lag_seconds,
      'last_digest', v_digest,
      'counts', v_counts,
      'exact_mirror', true,
      'idempotency_metadata_replicated', true,
      'identity_fingerprints_replicated', true,
      'encrypted_runtime_user_keys_replicated', true,
      'raw_routing_identities_replicated', false,
      'raw_message_bodies_replicated', false,
      'conversation_history_replicated', false,
      'provider_credentials_replicated', false,
      'runtime_secrets_replicated', false,
      'raw_media_replicated', false
    ),
    now()
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'identity_tables_ready', true,
       'app_identity_rekey_ready', true,
       'whatsapp_identity_rekey_ready', true,
       'scheduler_active', false,
       'autonomous_outbound_active', false,
       'execution_runtime_ready', false
     ),
     updated_at = now()
   where key = 'standby_execution'
     and value->>'contract' = 'h_standby_execution_v1'
     and value->>'mode' = 'passive_preflight';

  if not found then
    raise exception 'h_standby_execution_contract_missing' using errcode = '42501';
  end if;

  return jsonb_build_object(
    'ok', true,
    'protocol', 'exact_mirror_v2',
    'digest', v_digest,
    'lagSeconds', v_lag_seconds,
    'counts', v_counts,
    'exactMirror', true,
    'appIdentityReady', true,
    'whatsappIdentityReady', true,
    'identityFingerprintsReplicated', true,
    'encryptedRuntimeUserKeysReplicated', true,
    'rawRoutingIdentitiesReplicated', false,
    'idempotencyMetadataReplicated', true,
    'rawMessageBodiesReplicated', false,
    'runtimeSecretsReplicated', false,
    'providerCredentialsReplicated', false
  );
end;
$$;

revoke all on function public.h_apply_standby_replica_v2(jsonb) from public, anon, authenticated;
grant execute on function public.h_apply_standby_replica_v2(jsonb) to service_role;

comment on function public.h_apply_standby_replica_v2(jsonb) is
  'Applies H exact_mirror_v2 to an explicitly enabled passive standby, including stable-key identity fingerprints/ciphertext but excluding raw routing ids, runtime/provider secrets, raw chat bodies, and raw media.';
