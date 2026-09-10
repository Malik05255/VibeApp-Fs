-- H standby execution-readiness contract.
--
-- Standby preparation and failover eligibility are intentionally split. This file creates
-- only a fail-closed attestation record. It does not deploy H functions, copy credentials,
-- activate a scheduler, enable outbound automation, or promote the standby.
--
-- A later validated provisioning controller may attest each readiness dimension only after
-- live verification. h-standby-health derives executable readiness from these dimensions;
-- flipping standby_runtime.execution_runtime_ready alone is never sufficient.

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
  'Reports fail-closed H standby execution readiness. Paid-AI daily budget continuity must be verified before readiness. It never promotes the standby or activates scheduler/outbound execution.';
