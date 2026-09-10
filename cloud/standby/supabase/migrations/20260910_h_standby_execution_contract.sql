-- H standby execution-readiness contract.
--
-- Standby preparation and failover eligibility are intentionally split. This file creates
-- a fail-closed attestation record plus explicit request-only promotion controls. It never
-- activates a scheduler or autonomous outbound execution.

insert into public.h_runtime_state (key, value, updated_at)
values (
  'standby_execution',
  jsonb_build_object(
    'contract', 'h_standby_execution_v1',
    'mode', 'passive_preflight',
    'core_schema_ready', false,
    'function_inventory_ready', false,
    'runtime_secret_ready', false,
    'app_identity_rekey_ready', false,
    'whatsapp_identity_rekey_ready', false,
    'ai_credentials_rekey_ready', false,
    'free_ai_route_ready', false,
    'paid_ai_budget_continuity_ready', false,
    'promotion_controls_ready', false,
    'scheduler_active', false,
    'autonomous_outbound_active', false,
    'validated_at', null,
    'execution_runtime_ready', false
  ),
  now()
)
on conflict (key) do update
set value = jsonb_build_object(
      'contract', 'h_standby_execution_v1',
      'mode', 'passive_preflight',
      'core_schema_ready', false,
      'function_inventory_ready', false,
      'runtime_secret_ready', false,
      'app_identity_rekey_ready', false,
      'whatsapp_identity_rekey_ready', false,
      'ai_credentials_rekey_ready', false,
      'free_ai_route_ready', false,
      'paid_ai_budget_continuity_ready', false,
      'promotion_controls_ready', false,
      'scheduler_active', false,
      'autonomous_outbound_active', false,
      'validated_at', null,
      'execution_runtime_ready', false
    ),
    updated_at = now();

create or replace function public.h_standby_execution_contract_status_v1()
returns jsonb
language sql
security definer
set search_path = public, pg_temp
as $$
  with state as (
    select coalesce(value, '{}'::jsonb) as value
      from public.h_runtime_state
     where key = 'standby_execution'
  ), normalized as (
    select
      value,
      coalesce(value->>'contract', '') = 'h_standby_execution_v1' as contract_ok,
      coalesce(value->>'mode', '') = 'passive_preflight' as mode_ok,
      coalesce((value->>'core_schema_ready')::boolean, false) as core_schema_ready,
      coalesce((value->>'function_inventory_ready')::boolean, false) as function_inventory_ready,
      coalesce((value->>'runtime_secret_ready')::boolean, false) as runtime_secret_ready,
      coalesce((value->>'app_identity_rekey_ready')::boolean, false) as app_identity_rekey_ready,
      coalesce((value->>'whatsapp_identity_rekey_ready')::boolean, false) as whatsapp_identity_rekey_ready,
      coalesce((value->>'ai_credentials_rekey_ready')::boolean, false) as ai_credentials_rekey_ready,
      coalesce((value->>'free_ai_route_ready')::boolean, false) as free_ai_route_ready,
      coalesce((value->>'paid_ai_budget_continuity_ready')::boolean, false) as paid_ai_budget_continuity_ready,
      coalesce((value->>'promotion_controls_ready')::boolean, false) as promotion_controls_ready,
      coalesce((value->>'scheduler_active')::boolean, false) as scheduler_active,
      coalesce((value->>'autonomous_outbound_active')::boolean, false) as autonomous_outbound_active
    from state
  )
  select jsonb_build_object(
    'ok', true,
    'contract', 'h_standby_execution_v1',
    'ready',
      contract_ok and mode_ok
      and core_schema_ready
      and function_inventory_ready
      and runtime_secret_ready
      and app_identity_rekey_ready
      and whatsapp_identity_rekey_ready
      and ai_credentials_rekey_ready
      and free_ai_route_ready
      and paid_ai_budget_continuity_ready
      and promotion_controls_ready
      and not scheduler_active
      and not autonomous_outbound_active,
    'mode', case when mode_ok then 'passive_preflight' else 'invalid' end,
    'coreSchemaReady', core_schema_ready,
    'functionInventoryReady', function_inventory_ready,
    'runtimeSecretReady', runtime_secret_ready,
    'appIdentityRekeyReady', app_identity_rekey_ready,
    'whatsappIdentityRekeyReady', whatsapp_identity_rekey_ready,
    'aiCredentialsRekeyReady', ai_credentials_rekey_ready,
    'freeAiRouteReady', free_ai_route_ready,
    'paidAiBudgetContinuityReady', paid_ai_budget_continuity_ready,
    'promotionControlsReady', promotion_controls_ready,
    'schedulerActive', scheduler_active,
    'autonomousOutboundActive', autonomous_outbound_active
  )
  from normalized;
$$;

revoke all on function public.h_standby_execution_contract_status_v1() from public, anon, authenticated;
grant execute on function public.h_standby_execution_contract_status_v1() to service_role;

comment on function public.h_standby_execution_contract_status_v1() is
  'Reports fail-closed H passive standby execution readiness. It never promotes the standby or activates scheduler/outbound execution.';

-- Recomputes passive preflight readiness from current replicated state. This is called by
-- the primary replicator after identity and AI continuity have been refreshed. Readiness is
-- revoked automatically when replication or AI continuity is stale.
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
  v_ready boolean := false;
  v_source_generated_at timestamptz;
  v_last_replicated_at timestamptz;
  v_ai_validated_at timestamptz;
  v_digest text;
begin
  select coalesce(value, '{}'::jsonb) into v_runtime
    from public.h_runtime_state where key = 'standby_runtime' for update;
  select coalesce(value, '{}'::jsonb) into v_execution
    from public.h_runtime_state where key = 'standby_execution' for update;
  select coalesce(value, '{}'::jsonb) into v_replication
    from public.h_runtime_state where key = 'standby_replication';

  if coalesce((v_runtime->>'promoted')::boolean, false) is true then
    return jsonb_build_object('ok', true, 'ready', false, 'reason', 'already_promoted');
  end if;

  begin v_source_generated_at := nullif(v_replication->>'source_generated_at', '')::timestamptz; exception when others then v_source_generated_at := null; end;
  begin v_last_replicated_at := nullif(v_replication->>'last_replicated_at', '')::timestamptz; exception when others then v_last_replicated_at := null; end;
  begin v_ai_validated_at := nullif(v_execution->>'ai_continuity_validated_at', '')::timestamptz; exception when others then v_ai_validated_at := null; end;
  v_digest := lower(btrim(coalesce(v_replication->>'last_digest', '')));

  v_ready :=
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

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'promotion_controls_ready', v_ready,
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

  return jsonb_build_object(
    'ok', true,
    'ready', v_ready,
    'mode', 'passive_preflight',
    'sourceDigest', case when v_ready then v_digest else null end,
    'sourceGeneratedAt', case when v_ready then v_source_generated_at else null end,
    'aiValidatedAt', case when v_ready then v_ai_validated_at else null end,
    'schedulerActive', false,
    'autonomousOutboundActive', false
  );
end;
$$;

revoke all on function public.h_prepare_standby_promotion_v1() from public, anon, authenticated;
grant execute on function public.h_prepare_standby_promotion_v1() to service_role;

comment on function public.h_prepare_standby_promotion_v1() is
  'Recomputes request-only promotion preflight from fresh exact_mirror_v2 and AI continuity state. It does not promote or activate autonomous work.';

-- Atomically promotes a fully prepared passive standby into request-active mode. Replica
-- writes are fenced off at the transition. Scheduler and autonomous outbound remain off,
-- preventing duplicate background execution when primary liveness is ambiguous.
create or replace function public.h_promote_standby_request_only_v1(p_request_id text)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_request_id text := btrim(coalesce(p_request_id, ''));
  v_preflight jsonb;
  v_runtime jsonb := '{}'::jsonb;
  v_execution jsonb := '{}'::jsonb;
  v_replication jsonb := '{}'::jsonb;
  v_existing_promotion jsonb := '{}'::jsonb;
  v_promoted_at timestamptz := now();
begin
  if v_request_id !~ '^[A-Za-z0-9_-]{16,128}$' then
    raise exception 'h_standby_promotion_request_id_invalid' using errcode = '22023';
  end if;

  perform pg_advisory_xact_lock(hashtext('h-standby-replica-v2'));
  perform pg_advisory_xact_lock(hashtext('h-standby-ai-continuity-v1'));
  perform pg_advisory_xact_lock(hashtext('h-standby-promotion-v1'));

  select coalesce(value, '{}'::jsonb) into v_runtime
    from public.h_runtime_state where key = 'standby_runtime' for update;
  select coalesce(value, '{}'::jsonb) into v_existing_promotion
    from public.h_runtime_state where key = 'standby_promotion' for update;

  if coalesce((v_runtime->>'promoted')::boolean, false) is true then
    if coalesce(v_existing_promotion->>'request_id', '') = v_request_id
       and coalesce(v_existing_promotion->>'status', '') = 'active'
       and coalesce(v_existing_promotion->>'mode', '') = 'request_only'
    then
      return jsonb_build_object(
        'ok', true,
        'promoted', true,
        'active', true,
        'idempotent', true,
        'mode', 'request_only',
        'requestId', v_request_id,
        'promotedAt', v_existing_promotion->>'promoted_at'
      );
    end if;
    raise exception 'h_standby_already_promoted' using errcode = '55000';
  end if;

  v_preflight := public.h_prepare_standby_promotion_v1();
  if coalesce((v_preflight->>'ready')::boolean, false) is not true then
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
       'promotion_mode', 'request_only',
       'promotion_request_id', v_request_id,
       'promoted_at', v_promoted_at
     ),
     updated_at = v_promoted_at
   where key = 'standby_runtime';

  update public.h_runtime_state
     set value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
       'mode', 'request_active',
       'promotion_controls_ready', true,
       'scheduler_active', false,
       'autonomous_outbound_active', false,
       'execution_runtime_ready', true,
       'validated_at', v_promoted_at
     ),
     updated_at = v_promoted_at
   where key = 'standby_execution';

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'standby_promotion',
    jsonb_build_object(
      'protocol', 'h_standby_promotion_v1',
      'status', 'active',
      'mode', 'request_only',
      'request_id', v_request_id,
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
    'mode', 'request_only',
    'requestId', v_request_id,
    'promotedAt', v_promoted_at,
    'replicaWritesFenced', true,
    'schedulerActive', false,
    'autonomousOutboundActive', false
  );
end;
$$;

revoke all on function public.h_promote_standby_request_only_v1(text) from public, anon, authenticated;
grant execute on function public.h_promote_standby_request_only_v1(text) to service_role;

comment on function public.h_promote_standby_request_only_v1(text) is
  'Promotes a fully prepared H standby into request-only active mode, fences replica writes, and keeps scheduler/autonomous outbound disabled.';
