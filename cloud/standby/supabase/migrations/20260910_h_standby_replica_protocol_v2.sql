-- H standby exact-mirror protocol v2.
--
-- v2 extends the operational mirror with the durable H identity mappings needed for
-- failover continuity. Only identity_secret-keyed HMAC fingerprints and encrypted runtime
-- user keys are mirrored. Raw Google subjects, raw owner/friend WhatsApp ids, provider
-- credentials, runtime polling secrets, and raw media are never included in exact_mirror_v2.
-- Provider credentials use the separate fail-closed AI continuity RPC below, where secrets
-- are re-encrypted for the standby service-role root before they reach this database.

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

-- AI continuity is intentionally separate from the ordinary exact mirror. The primary worker
-- decrypts each supported credential only in memory and re-encrypts it with the standby
-- service-role-derived runtime key before calling this RPC. No raw provider secret, setup
-- token, OAuth pending state, or primary ciphertext is accepted here.
create table if not exists public.h_runtime_ai_credentials (
  id text primary key check (id in ('openrouter_default','openrouter_owner_paid','tavily_default')),
  provider text not null check (provider in ('openrouter','tavily')),
  secret_ciphertext text not null check (secret_ciphertext ~ '^[A-Za-z0-9_-]{16,8192}$'),
  secret_iv text not null check (secret_iv ~ '^[A-Za-z0-9_-]{16,128}$'),
  secret_version integer not null default 1 check (secret_version = 1),
  selected_model text check (selected_model is null or char_length(selected_model) between 1 and 200),
  model_verified_at timestamptz,
  oauth_metadata jsonb not null default '{}'::jsonb,
  connected_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_standby_ai_credential_provider_pair check (
    (id = 'openrouter_default' and provider = 'openrouter')
    or (id = 'openrouter_owner_paid' and provider = 'openrouter')
    or (id = 'tavily_default' and provider = 'tavily')
  )
);

create table if not exists public.h_runtime_ai_provider_registry (
  id text primary key check (id ~ '^[a-z0-9][a-z0-9._:-]{1,95}$'),
  provider text not null check (provider ~ '^[a-z0-9][a-z0-9._-]{1,63}$'),
  route_class text not null check (route_class in ('internal_free', 'owner_paid')),
  credential_id text,
  selected_model text check (selected_model is null or char_length(selected_model) between 1 and 200),
  enabled boolean not null default false,
  owner_enabled_at timestamptz,
  hard_tasks_only boolean not null default true,
  allow_free_fallback boolean not null default false,
  daily_call_limit integer check (daily_call_limit is null or daily_call_limit between 1 and 10000),
  priority integer not null default 100 check (priority between 0 and 10000),
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (
    route_class = 'internal_free'
    or not enabled
    or (
      owner_enabled_at is not null
      and credential_id is not null
      and btrim(credential_id) <> ''
      and selected_model is not null
      and daily_call_limit is not null
    )
  )
);

create unique index if not exists h_standby_ai_provider_registry_one_paid_enabled_idx
  on public.h_runtime_ai_provider_registry ((route_class))
  where route_class = 'owner_paid' and enabled;

create table if not exists public.h_runtime_ai_paid_usage_daily (
  route_id text not null references public.h_runtime_ai_provider_registry(id) on delete cascade,
  usage_date date not null,
  calls integer not null default 0 check (calls >= 0),
  prompt_tokens bigint not null default 0 check (prompt_tokens >= 0),
  completion_tokens bigint not null default 0 check (completion_tokens >= 0),
  last_used_at timestamptz,
  updated_at timestamptz not null default now(),
  cost_usd numeric(14,6) not null default 0 check (cost_usd >= 0),
  primary key (route_id, usage_date)
);

create table if not exists public.h_runtime_ai_route_stats (
  provider text not null check (provider = 'openrouter'),
  model text not null check (char_length(model) between 1 and 200),
  capability text not null check (capability in ('text','image','file')),
  attempts bigint not null default 0 check (attempts >= 0),
  successes bigint not null default 0 check (successes >= 0),
  failures bigint not null default 0 check (failures >= 0),
  rate_limits bigint not null default 0 check (rate_limits >= 0),
  consecutive_failures integer not null default 0 check (consecutive_failures >= 0),
  avg_latency_ms double precision not null default 0 check (avg_latency_ms >= 0),
  prompt_tokens bigint not null default 0 check (prompt_tokens >= 0),
  completion_tokens bigint not null default 0 check (completion_tokens >= 0),
  last_http_status integer,
  last_rate_limit_remaining bigint,
  last_rate_limit_reset_at timestamptz,
  last_error text check (last_error is null or char_length(last_error) <= 500),
  cooldown_until timestamptz,
  last_used_at timestamptz,
  last_success_at timestamptz,
  updated_at timestamptz not null default now(),
  primary key (provider, model, capability)
);

alter table public.h_runtime_ai_credentials enable row level security;
alter table public.h_runtime_ai_provider_registry enable row level security;
alter table public.h_runtime_ai_paid_usage_daily enable row level security;
alter table public.h_runtime_ai_route_stats enable row level security;

revoke all on table public.h_runtime_ai_credentials from public, anon, authenticated;
revoke all on table public.h_runtime_ai_provider_registry from public, anon, authenticated;
revoke all on table public.h_runtime_ai_paid_usage_daily from public, anon, authenticated;
revoke all on table public.h_runtime_ai_route_stats from public, anon, authenticated;

grant all on table public.h_runtime_ai_credentials to service_role;
grant all on table public.h_runtime_ai_provider_registry to service_role;
grant all on table public.h_runtime_ai_paid_usage_daily to service_role;
grant all on table public.h_runtime_ai_route_stats to service_role;

create or replace function public.h_apply_standby_ai_continuity_v1(p_snapshot jsonb)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_runtime jsonb;
  v_generated_at timestamptz;
  v_usage_date date;
  v_ai_ready boolean := false;
  v_free_ready boolean := false;
  v_paid_budget_ready boolean := false;
  v_all_ready boolean := false;
  v_enabled_paid integer := 0;
  v_counts jsonb := '{}'::jsonb;
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
    raise exception 'h_standby_ai_continuity_not_enabled' using errcode = '42501';
  end if;

  if jsonb_typeof(p_snapshot) <> 'object'
     or p_snapshot->>'format' <> 'h-standby-ai-continuity'
     or (p_snapshot->>'version')::integer <> 1
     or p_snapshot->>'assistantIdentity' <> 'H'
     or coalesce((p_snapshot->>'rawProviderSecretsIncluded')::boolean, true) is true
     or coalesce((p_snapshot->>'sourceCiphertextsCopiedUnchanged')::boolean, true) is true
     or coalesce((p_snapshot->>'setupTokensIncluded')::boolean, true) is true
     or coalesce((p_snapshot->>'oauthPendingIncluded')::boolean, true) is true
  then
    raise exception 'h_standby_ai_continuity_snapshot_invalid' using errcode = '22023';
  end if;

  v_generated_at := nullif(p_snapshot->>'generatedAt', '')::timestamptz;
  v_usage_date := nullif(p_snapshot->>'usageDate', '')::date;
  v_counts := coalesce(p_snapshot->'counts', '{}'::jsonb);
  if v_generated_at is null
     or v_generated_at > now() + interval '5 minutes'
     or v_generated_at < now() - interval '15 minutes'
     or v_usage_date is null
     or v_usage_date <> (v_generated_at at time zone 'UTC')::date
  then
    raise exception 'h_standby_ai_continuity_snapshot_stale' using errcode = '22023';
  end if;

  if jsonb_typeof(p_snapshot->'credentials') <> 'array'
     or jsonb_typeof(p_snapshot->'providerRoutes') <> 'array'
     or jsonb_typeof(p_snapshot->'paidUsageToday') <> 'array'
     or jsonb_typeof(p_snapshot->'routeStats') <> 'array'
     or jsonb_array_length(p_snapshot->'credentials') > 16
     or jsonb_array_length(p_snapshot->'providerRoutes') > 32
     or jsonb_array_length(p_snapshot->'paidUsageToday') > 32
     or jsonb_array_length(p_snapshot->'routeStats') > 1000
  then
    raise exception 'h_standby_ai_continuity_sections_invalid' using errcode = '22023';
  end if;

  if exists (
    select 1
      from jsonb_to_recordset(p_snapshot->'credentials') as c(id text, provider text, secret_version integer)
     where not (
       (c.id = 'openrouter_default' and c.provider = 'openrouter' and c.secret_version = 1)
       or (c.id = 'openrouter_owner_paid' and c.provider = 'openrouter' and c.secret_version = 1)
       or (c.id = 'tavily_default' and c.provider = 'tavily' and c.secret_version = 1)
     )
  ) then
    raise exception 'h_standby_ai_continuity_unsupported_credential' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('h-standby-ai-continuity-v1'));

  delete from public.h_runtime_ai_paid_usage_daily;
  delete from public.h_runtime_ai_provider_registry;
  delete from public.h_runtime_ai_route_stats;
  delete from public.h_runtime_ai_credentials;

  insert into public.h_runtime_ai_credentials
  select * from jsonb_populate_recordset(null::public.h_runtime_ai_credentials, p_snapshot->'credentials');

  insert into public.h_runtime_ai_provider_registry
  select * from jsonb_populate_recordset(null::public.h_runtime_ai_provider_registry, p_snapshot->'providerRoutes');

  insert into public.h_runtime_ai_paid_usage_daily
  select * from jsonb_populate_recordset(null::public.h_runtime_ai_paid_usage_daily, p_snapshot->'paidUsageToday');

  insert into public.h_runtime_ai_route_stats
  select * from jsonb_populate_recordset(null::public.h_runtime_ai_route_stats, p_snapshot->'routeStats');

  v_ai_ready :=
    (select count(*) from public.h_runtime_ai_credentials) = jsonb_array_length(p_snapshot->'credentials')
    and not exists (
      select 1
        from public.h_runtime_ai_provider_registry r
       where r.enabled = true
         and (r.credential_id is null or not exists (
           select 1 from public.h_runtime_ai_credentials c where c.id = r.credential_id
         ))
    );

  v_free_ready := exists (
    select 1
      from public.h_runtime_ai_provider_registry r
      join public.h_runtime_ai_credentials c on c.id = r.credential_id
     where r.route_class = 'internal_free'
       and r.enabled = true
       and r.provider = 'openrouter'
       and c.id = 'openrouter_default'
       and c.provider = 'openrouter'
  );

  select count(*) into v_enabled_paid
    from public.h_runtime_ai_provider_registry
   where route_class = 'owner_paid' and enabled = true;

  v_paid_budget_ready := v_enabled_paid = 0 or (
    v_enabled_paid = 1
    and not exists (
      select 1
        from public.h_runtime_ai_provider_registry r
       where r.route_class = 'owner_paid'
         and r.enabled = true
         and (
           r.provider <> 'openrouter'
           or r.credential_id <> 'openrouter_owner_paid'
           or r.selected_model is null
           or r.daily_call_limit is null
           or not exists (
             select 1
               from public.h_runtime_ai_credentials c
              where c.id = 'openrouter_owner_paid' and c.provider = 'openrouter'
           )
           or not exists (
             select 1
               from public.h_runtime_ai_paid_usage_daily u
              where u.route_id = r.id
                and u.usage_date = v_usage_date
                and u.calls between 0 and r.daily_call_limit
           )
         )
    )
  );

  v_all_ready := v_ai_ready and v_free_ready and v_paid_budget_ready;

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'standby_ai_continuity',
    jsonb_build_object(
      'mode', 'rekeyed_mirror_v1',
      'source_generated_at', v_generated_at,
      'usage_date', v_usage_date,
      'last_synced_at', now(),
      'counts', v_counts,
      'ai_credentials_rekey_ready', v_ai_ready,
      'free_ai_route_ready', v_free_ready,
      'paid_ai_budget_continuity_ready', v_paid_budget_ready,
      'raw_provider_secrets_replicated', false,
      'source_ciphertexts_copied_unchanged', false,
      'setup_tokens_replicated', false,
      'oauth_pending_replicated', false
    ),
    now()
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'ai_credentials_rekey_ready', v_ai_ready,
       'free_ai_route_ready', v_free_ready,
       'paid_ai_budget_continuity_ready', v_paid_budget_ready,
       'ai_continuity_validated_at', case when v_all_ready then now() else null end,
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
    'protocol', 'ai_continuity_v1',
    'aiCredentialsRekeyReady', v_ai_ready,
    'freeAiRouteReady', v_free_ready,
    'paidAiBudgetContinuityReady', v_paid_budget_ready,
    'aiContinuityReady', v_all_ready,
    'rawProviderSecretsReplicated', false,
    'sourceCiphertextsCopiedUnchanged', false,
    'setupTokensReplicated', false,
    'oauthPendingReplicated', false,
    'counts', v_counts
  );
end;
$$;

revoke all on function public.h_apply_standby_ai_continuity_v1(jsonb) from public, anon, authenticated;
grant execute on function public.h_apply_standby_ai_continuity_v1(jsonb) to service_role;

comment on function public.h_apply_standby_ai_continuity_v1(jsonb) is
  'Applies a re-keyed, server-only AI continuity snapshot to passive H standby state. Raw provider secrets, primary ciphertext, setup tokens, OAuth pending state, scheduler activation, outbound activation, and promotion are excluded.';
