-- H standby independent fencing contract.
--
-- Promotion must never be authorized by primary unreachability alone. A separate authority
-- signs a short-lived ES256 assertion that the old primary has been write-fenced. Signature
-- verification happens in h-standby-promote; this SQL enforces monotonic epochs, target
-- binding, replay resistance, and the existing fresh standby preflight before state changes.

insert into public.h_runtime_state (key, value, updated_at)
values (
  'standby_fencing',
  jsonb_build_object(
    'contract', 'h_standby_fencing_v1',
    'authority_configured', false,
    'last_fence_epoch', 0,
    'last_request_id', null,
    'last_assertion_sha256', null,
    'last_fenced_at', null,
    'automatic_self_promotion_enabled', false
  ),
  now()
)
on conflict (key) do update
set value = coalesce(public.h_runtime_state.value, '{}'::jsonb) || jsonb_build_object(
      'contract', 'h_standby_fencing_v1',
      'automatic_self_promotion_enabled', false
    ),
    updated_at = now();

-- Override passive preflight so transport/AI freshness can never advertise final promotion
-- readiness unless an independent fencing verification key and both project bindings exist.
create or replace function public.h_prepare_standby_promotion_v1()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_runtime jsonb := '{}'::jsonb;
  v_execution jsonb := '{}'::jsonb;
  v_replication jsonb := '{}'::jsonb;
  v_fencing jsonb := '{}'::jsonb;
  v_ready boolean := false;
  v_transport_ready boolean := false;
  v_fencing_ready boolean := false;
  v_source_generated_at timestamptz;
  v_last_replicated_at timestamptz;
  v_ai_validated_at timestamptz;
  v_digest text;
  v_fencing_issuer text;
  v_fencing_public_jwk text;
  v_primary_project_ref text;
  v_standby_project_ref text;
begin
  select coalesce(value, '{}'::jsonb) into v_runtime
    from public.h_runtime_state where key = 'standby_runtime' for update;
  select coalesce(value, '{}'::jsonb) into v_execution
    from public.h_runtime_state where key = 'standby_execution' for update;
  select coalesce(value, '{}'::jsonb) into v_replication
    from public.h_runtime_state where key = 'standby_replication';
  select coalesce(value, '{}'::jsonb) into v_fencing
    from public.h_runtime_state where key = 'standby_fencing' for update;

  select secret_value into v_fencing_issuer from public.h_runtime_config where key = 'fencing_issuer';
  select secret_value into v_fencing_public_jwk from public.h_runtime_config where key = 'fencing_public_jwk';
  select secret_value into v_primary_project_ref from public.h_runtime_config where key = 'fencing_primary_project_ref';
  select secret_value into v_standby_project_ref from public.h_runtime_config where key = 'fencing_standby_project_ref';

  if coalesce((v_runtime->>'promoted')::boolean, false) is true then
    return jsonb_build_object('ok', true, 'ready', false, 'reason', 'already_promoted');
  end if;

  begin v_source_generated_at := nullif(v_replication->>'source_generated_at', '')::timestamptz; exception when others then v_source_generated_at := null; end;
  begin v_last_replicated_at := nullif(v_replication->>'last_replicated_at', '')::timestamptz; exception when others then v_last_replicated_at := null; end;
  begin v_ai_validated_at := nullif(v_execution->>'ai_continuity_validated_at', '')::timestamptz; exception when others then v_ai_validated_at := null; end;
  v_digest := lower(btrim(coalesce(v_replication->>'last_digest', '')));

  v_fencing_ready :=
    coalesce(v_fencing->>'contract', '') = 'h_standby_fencing_v1'
    and nullif(btrim(coalesce(v_fencing_issuer, '')), '') is not null
    and char_length(v_fencing_issuer) between 3 and 200
    and nullif(btrim(coalesce(v_fencing_public_jwk, '')), '') is not null
    and char_length(v_fencing_public_jwk) between 40 and 4096
    and coalesce(v_fencing_public_jwk, '') not like '%"d"%'
    and coalesce(v_primary_project_ref, '') ~ '^[a-z0-9-]{8,64}$'
    and coalesce(v_standby_project_ref, '') ~ '^[a-z0-9-]{8,64}$'
    and v_primary_project_ref <> v_standby_project_ref;

  v_transport_ready :=
    coalesce(v_runtime->>'runtime_role', '') = 'standby'
    and coalesce(v_runtime->>'h_identity', '') = 'H'
    and coalesce((v_runtime->>'dedicated_h_standby')::boolean, false) = true
    and coalesce((v_runtime->>'allow_replica_writes')::boolean, false) = true
    and coalesce(v_execution->>'contract', '') = 'h_standby_execution_v1'
    and coalesce(v_execution->>'mode', '') = 'passive_preflight'
    and coalesce((v_execution->>'core_schema_ready')::boolean, false) = true
    and coalesce((v_execution->>'function_inventory_ready')::boolean, false) = true
    and coalesce((v_execution->>'runtime_secret_ready')::boolean, false) = true
    and coalesce((v_execution->>'app_identity_rekey_ready')::boolean, false) = true
    and coalesce((v_execution->>'whatsapp_identity_rekey_ready')::boolean, false) = true
    and coalesce((v_execution->>'ai_credentials_rekey_ready')::boolean, false) = true
    and coalesce((v_execution->>'free_ai_route_ready')::boolean, false) = true
    and coalesce((v_execution->>'paid_ai_budget_continuity_ready')::boolean, false) = true
    and coalesce((v_execution->>'scheduler_active')::boolean, false) = false
    and coalesce((v_execution->>'autonomous_outbound_active')::boolean, false) = false
    and coalesce(v_replication->>'mode', '') = 'continuous'
    and coalesce(v_replication->>'protocol', '') = 'exact_mirror_v2'
    and coalesce((v_replication->>'exact_mirror')::boolean, false) = true
    and v_digest ~ '^[0-9a-f]{64}$'
    and v_source_generated_at is not null
    and v_source_generated_at <= now() + interval '5 seconds'
    and v_source_generated_at >= now() - interval '120 seconds'
    and v_last_replicated_at is not null
    and v_last_replicated_at <= now() + interval '5 seconds'
    and v_last_replicated_at >= now() - interval '180 seconds'
    and v_ai_validated_at is not null
    and v_ai_validated_at <= now() + interval '5 seconds'
    and v_ai_validated_at >= now() - interval '180 seconds';

  v_ready := v_transport_ready and v_fencing_ready;

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'promotion_controls_ready', v_ready,
       'fencing_authority_ready', v_fencing_ready,
       'validated_at', case when v_ready then now() else null end,
       'execution_runtime_ready', v_ready,
       'scheduler_active', false,
       'autonomous_outbound_active', false
     ),
     updated_at = now()
   where key = 'standby_execution';

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'execution_runtime_ready', v_ready
     ),
     updated_at = now()
   where key = 'standby_runtime';

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'authority_configured', v_fencing_ready,
       'automatic_self_promotion_enabled', false
     ),
     updated_at = now()
   where key = 'standby_fencing';

  return jsonb_build_object(
    'ok', true,
    'ready', v_ready,
    'transportReady', v_transport_ready,
    'fencingAuthorityReady', v_fencing_ready,
    'mode', 'passive_preflight',
    'sourceDigest', case when v_transport_ready then v_digest else null end,
    'sourceGeneratedAt', case when v_transport_ready then v_source_generated_at else null end,
    'aiValidatedAt', case when v_transport_ready then v_ai_validated_at else null end,
    'automaticSelfPromotionEnabled', false,
    'schedulerActive', false,
    'autonomousOutboundActive', false
  );
end;
$$;

revoke all on function public.h_prepare_standby_promotion_v1() from public, anon, authenticated;
grant execute on function public.h_prepare_standby_promotion_v1() to service_role;

-- Compatibility endpoint is deliberately disabled: a runtime secret/request id is not a
-- primary write fence. All new promotions must pass through the signed fencing path.
create or replace function public.h_promote_standby_request_only_v1(p_request_id text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  raise exception 'h_standby_external_fence_required' using errcode = '42501';
end;
$$;

revoke all on function public.h_promote_standby_request_only_v1(text) from public, anon, authenticated;
grant execute on function public.h_promote_standby_request_only_v1(text) to service_role;

create or replace function public.h_promote_standby_fenced_v1(
  p_request_id text,
  p_fence_epoch bigint,
  p_assertion_sha256 text,
  p_primary_project_ref text,
  p_standby_project_ref text,
  p_fenced_at timestamptz
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_request_id text := btrim(coalesce(p_request_id, ''));
  v_assertion_sha256 text := lower(btrim(coalesce(p_assertion_sha256, '')));
  v_primary_project_ref text := btrim(coalesce(p_primary_project_ref, ''));
  v_standby_project_ref text := btrim(coalesce(p_standby_project_ref, ''));
  v_expected_primary_ref text;
  v_expected_standby_ref text;
  v_preflight jsonb;
  v_runtime jsonb := '{}'::jsonb;
  v_execution jsonb := '{}'::jsonb;
  v_replication jsonb := '{}'::jsonb;
  v_fencing jsonb := '{}'::jsonb;
  v_existing_promotion jsonb := '{}'::jsonb;
  v_last_epoch bigint := 0;
  v_promoted_at timestamptz := now();
begin
  if v_request_id !~ '^[A-Za-z0-9_-]{16,128}$'
     or p_fence_epoch is null or p_fence_epoch <= 0
     or v_assertion_sha256 !~ '^[0-9a-f]{64}$'
     or v_primary_project_ref !~ '^[a-z0-9-]{8,64}$'
     or v_standby_project_ref !~ '^[a-z0-9-]{8,64}$'
     or v_primary_project_ref = v_standby_project_ref
     or p_fenced_at is null
     or p_fenced_at > now() + interval '10 seconds'
     or p_fenced_at < now() - interval '130 seconds'
  then
    raise exception 'h_standby_fence_parameters_invalid' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('h-standby-replica-v2'));
  perform pg_advisory_xact_lock(hashtext('h-standby-ai-continuity-v1'));
  perform pg_advisory_xact_lock(hashtext('h-standby-promotion-v1'));
  perform pg_advisory_xact_lock(hashtext('h-standby-fencing-v1'));

  select secret_value into v_expected_primary_ref from public.h_runtime_config where key = 'fencing_primary_project_ref';
  select secret_value into v_expected_standby_ref from public.h_runtime_config where key = 'fencing_standby_project_ref';
  if v_primary_project_ref <> coalesce(v_expected_primary_ref, '')
     or v_standby_project_ref <> coalesce(v_expected_standby_ref, '')
  then
    raise exception 'h_standby_fence_target_mismatch' using errcode = '42501';
  end if;

  select coalesce(value, '{}'::jsonb) into v_runtime
    from public.h_runtime_state where key = 'standby_runtime' for update;
  select coalesce(value, '{}'::jsonb) into v_fencing
    from public.h_runtime_state where key = 'standby_fencing' for update;
  select coalesce(value, '{}'::jsonb) into v_existing_promotion
    from public.h_runtime_state where key = 'standby_promotion' for update;

  begin v_last_epoch := coalesce(nullif(v_fencing->>'last_fence_epoch', '')::bigint, 0); exception when others then v_last_epoch := 0; end;

  if coalesce((v_runtime->>'promoted')::boolean, false) is true then
    if coalesce(v_existing_promotion->>'request_id', '') = v_request_id
       and coalesce(v_existing_promotion->>'fence_epoch', '') = p_fence_epoch::text
       and coalesce(v_existing_promotion->>'assertion_sha256', '') = v_assertion_sha256
       and coalesce(v_existing_promotion->>'status', '') = 'active'
       and coalesce(v_existing_promotion->>'mode', '') = 'fenced_request_only'
    then
      return jsonb_build_object(
        'ok', true,
        'promoted', true,
        'active', true,
        'idempotent', true,
        'mode', 'fenced_request_only',
        'requestId', v_request_id,
        'fenceEpoch', p_fence_epoch,
        'promotedAt', v_existing_promotion->>'promoted_at'
      );
    end if;
    raise exception 'h_standby_already_promoted' using errcode = '55000';
  end if;

  if p_fence_epoch <= v_last_epoch then
    raise exception 'h_standby_fence_epoch_replayed' using errcode = '42501';
  end if;

  v_preflight := public.h_prepare_standby_promotion_v1();
  if coalesce((v_preflight->>'ready')::boolean, false) is not true
     or coalesce((v_preflight->>'fencingAuthorityReady')::boolean, false) is not true
  then
    raise exception 'h_standby_promotion_preflight_not_ready' using errcode = '55000';
  end if;

  select coalesce(value, '{}'::jsonb) into v_execution
    from public.h_runtime_state where key = 'standby_execution' for update;
  select coalesce(value, '{}'::jsonb) into v_replication
    from public.h_runtime_state where key = 'standby_replication';

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'promoted', true,
       'allow_replica_writes', false,
       'execution_runtime_ready', true,
       'promotion_mode', 'fenced_request_only',
       'promotion_request_id', v_request_id,
       'promotion_fence_epoch', p_fence_epoch,
       'promoted_at', v_promoted_at
     ),
     updated_at = v_promoted_at
   where key = 'standby_runtime';

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'mode', 'request_active',
       'promotion_controls_ready', true,
       'fencing_authority_ready', true,
       'scheduler_active', false,
       'autonomous_outbound_active', false,
       'execution_runtime_ready', true,
       'validated_at', v_promoted_at
     ),
     updated_at = v_promoted_at
   where key = 'standby_execution';

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'authority_configured', true,
       'last_fence_epoch', p_fence_epoch,
       'last_request_id', v_request_id,
       'last_assertion_sha256', v_assertion_sha256,
       'last_fenced_at', p_fenced_at,
       'automatic_self_promotion_enabled', false
     ),
     updated_at = v_promoted_at
   where key = 'standby_fencing';

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'standby_promotion',
    jsonb_build_object(
      'protocol', 'h_standby_promotion_v2',
      'status', 'active',
      'mode', 'fenced_request_only',
      'request_id', v_request_id,
      'fence_epoch', p_fence_epoch,
      'assertion_sha256', v_assertion_sha256,
      'primary_project_ref', v_primary_project_ref,
      'standby_project_ref', v_standby_project_ref,
      'primary_write_fenced', true,
      'fenced_at', p_fenced_at,
      'promoted_at', v_promoted_at,
      'source_digest', v_replication->>'last_digest',
      'source_generated_at', v_replication->>'source_generated_at',
      'replication_observed_at', v_replication->>'last_replicated_at',
      'ai_continuity_validated_at', v_execution->>'ai_continuity_validated_at',
      'replica_writes_fenced', true,
      'scheduler_active', false,
      'autonomous_outbound_active', false
    ),
    v_promoted_at
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  return jsonb_build_object(
    'ok', true,
    'promoted', true,
    'active', true,
    'idempotent', false,
    'mode', 'fenced_request_only',
    'requestId', v_request_id,
    'fenceEpoch', p_fence_epoch,
    'promotedAt', v_promoted_at,
    'primaryWriteFenced', true,
    'replicaWritesFenced', true,
    'schedulerActive', false,
    'autonomousOutboundActive', false
  );
end;
$$;

revoke all on function public.h_promote_standby_fenced_v1(text,bigint,text,text,text,timestamptz) from public, anon, authenticated;
grant execute on function public.h_promote_standby_fenced_v1(text,bigint,text,text,text,timestamptz) to service_role;

comment on function public.h_promote_standby_request_only_v1(text) is
  'Compatibility endpoint intentionally disabled. H standby promotion now requires an externally signed primary write-fence assertion.';
comment on function public.h_promote_standby_fenced_v1(text,bigint,text,text,text,timestamptz) is
  'Promotes H only after signed fencing was verified by h-standby-promote, with monotonic epoch/target/replay guards and fresh standby preflight.';
