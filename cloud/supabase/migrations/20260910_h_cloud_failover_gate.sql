-- H cloud failover gate and last-free-capacity warning.
--
-- This migration deliberately does NOT switch the durable primary by itself. It evaluates
-- whether an automatic failover would be safe and records the decision. A real switch is
-- allowed only after a separate standby runtime has been validated and explicitly marked
-- auto_failover_eligible. This prevents a storage-only backup from being mistaken for a
-- runnable H Cloud.

create extension if not exists pg_cron with schema pg_catalog;

create table if not exists public.h_runtime_cloud_failover_events (
  id uuid primary key default gen_random_uuid(),
  decision text not null check (decision in ('stay_primary', 'warn_last_cloud', 'failover_blocked', 'failover_eligible')),
  reason text not null,
  primary_cloud_id text references public.h_runtime_cloud_registry(id) on delete set null,
  backup_cloud_id text references public.h_runtime_cloud_registry(id) on delete set null,
  primary_health_ok boolean,
  primary_capacity_state text not null check (primary_capacity_state in ('unknown', 'ok', 'warning', 'critical')),
  backup_standby_ready boolean not null default false,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

alter table public.h_runtime_cloud_failover_events enable row level security;

create index if not exists h_runtime_cloud_failover_events_recent
  on public.h_runtime_cloud_failover_events (created_at desc);

create or replace function public.h_runtime_evaluate_cloud_failover_gate()
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_primary public.h_runtime_cloud_registry%rowtype;
  v_backup public.h_runtime_cloud_registry%rowtype;
  v_primary_found boolean := false;
  v_backup_found boolean := false;
  v_bytes_ratio numeric := null;
  v_requests_ratio numeric := null;
  v_capacity_state text := 'unknown';
  v_primary_health_ok boolean := false;
  v_needs_failover boolean := false;
  v_backup_standby_ready boolean := false;
  v_decision text;
  v_reason text;
  v_previous_decision text := null;
  v_now timestamptz := now();
begin
  select * into v_primary
    from public.h_runtime_cloud_registry
   where cloud_role = 'primary' and enabled = true
   order by priority asc, updated_at desc
   limit 1;
  v_primary_found := found;

  select * into v_backup
    from public.h_runtime_cloud_registry
   where cloud_role = 'backup' and enabled = true
   order by priority asc, updated_at desc
   limit 1;
  v_backup_found := found;

  if v_primary_found then
    if v_primary.quota_bytes is not null and v_primary.quota_bytes > 0 and v_primary.used_bytes is not null then
      v_bytes_ratio := greatest(0, v_primary.used_bytes::numeric / v_primary.quota_bytes::numeric);
    end if;
    if v_primary.quota_requests is not null and v_primary.quota_requests > 0 and v_primary.used_requests is not null then
      v_requests_ratio := greatest(0, v_primary.used_requests::numeric / v_primary.quota_requests::numeric);
    end if;

    if coalesce(v_bytes_ratio, 0) >= 0.95 or coalesce(v_requests_ratio, 0) >= 0.95 then
      v_capacity_state := 'critical';
    elsif coalesce(v_bytes_ratio, 0) >= 0.85 or coalesce(v_requests_ratio, 0) >= 0.85 then
      v_capacity_state := 'warning';
    elsif v_bytes_ratio is not null or v_requests_ratio is not null then
      v_capacity_state := 'ok';
    end if;

    v_primary_health_ok := v_primary.ready = true and v_primary.last_health_ok = true;
    v_needs_failover := not v_primary_health_ok or v_capacity_state = 'critical';
  else
    v_primary_health_ok := false;
    v_capacity_state := 'critical';
    v_needs_failover := true;
  end if;

  if v_backup_found then
    v_backup_standby_ready :=
      v_backup.enabled = true
      and v_backup.ready = true
      and v_backup.last_health_ok = true
      and v_backup.credential_id is not null
      and coalesce(v_backup.metadata->>'storage_backup_ready', 'false') = 'true'
      and coalesce(v_backup.metadata->>'standby_runtime_ready', 'false') = 'true'
      and coalesce(v_backup.metadata->>'runtime_health_ok', 'false') = 'true'
      and coalesce(v_backup.metadata->>'auto_failover_eligible', 'false') = 'true';
  end if;

  if v_needs_failover and v_backup_standby_ready then
    v_decision := 'failover_eligible';
    v_reason := case
      when not v_primary_found then 'primary_missing'
      when not v_primary_health_ok then 'primary_unhealthy'
      else 'primary_capacity_critical'
    end;
  elsif v_needs_failover then
    v_decision := 'failover_blocked';
    v_reason := case
      when not v_primary_found then 'primary_missing_no_validated_standby'
      when not v_primary_health_ok then 'primary_unhealthy_no_validated_standby'
      else 'primary_capacity_critical_no_validated_standby'
    end;
  elsif v_capacity_state = 'warning' and not v_backup_standby_ready then
    v_decision := 'warn_last_cloud';
    v_reason := 'primary_capacity_warning_no_validated_standby';
  else
    v_decision := 'stay_primary';
    v_reason := case
      when v_backup_standby_ready then 'primary_healthy_standby_ready'
      when v_backup_found then 'primary_healthy_backup_storage_only'
      else 'primary_healthy_no_backup'
    end;
  end if;

  select value->>'decision' into v_previous_decision
    from public.h_runtime_state
   where key = 'cloud_failover_gate';

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'cloud_failover_gate',
    jsonb_build_object(
      'decision', v_decision,
      'reason', v_reason,
      'evaluated_at', v_now,
      'primary_health_ok', v_primary_health_ok,
      'primary_capacity_state', v_capacity_state,
      'primary_bytes_ratio', v_bytes_ratio,
      'primary_requests_ratio', v_requests_ratio,
      'backup_configured', v_backup_found,
      'backup_standby_ready', v_backup_standby_ready,
      'automatic_switch_performed', false,
      'requires_external_standby_controller', v_decision = 'failover_eligible'
    ),
    v_now
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  if v_previous_decision is distinct from v_decision then
    insert into public.h_runtime_cloud_failover_events (
      decision,
      reason,
      primary_cloud_id,
      backup_cloud_id,
      primary_health_ok,
      primary_capacity_state,
      backup_standby_ready,
      metadata,
      created_at
    ) values (
      v_decision,
      v_reason,
      case when v_primary_found then v_primary.id else null end,
      case when v_backup_found then v_backup.id else null end,
      v_primary_health_ok,
      v_capacity_state,
      v_backup_standby_ready,
      jsonb_build_object(
        'automatic_switch_performed', false,
        'storage_only_backup_is_not_standby', true
      ),
      v_now
    );
  end if;

  return jsonb_build_object(
    'ok', true,
    'decision', v_decision,
    'reason', v_reason,
    'primaryHealthy', v_primary_health_ok,
    'primaryCapacityState', v_capacity_state,
    'backupConfigured', v_backup_found,
    'backupStandbyReady', v_backup_standby_ready,
    'automaticSwitchPerformed', false
  );
end;
$$;

revoke all on function public.h_runtime_evaluate_cloud_failover_gate() from public, anon, authenticated;
grant execute on function public.h_runtime_evaluate_cloud_failover_gate() to service_role;

-- Re-evaluate every 15 minutes. This is local state evaluation only and consumes no AI quota.
do $do$
declare
  v_job_id bigint;
begin
  for v_job_id in
    select jobid from cron.job where jobname = 'h-runtime-cloud-failover-gate'
  loop
    perform cron.unschedule(v_job_id);
  end loop;

  perform cron.schedule(
    'h-runtime-cloud-failover-gate',
    '*/15 * * * *',
    $$select public.h_runtime_evaluate_cloud_failover_gate();$$
  );
end;
$do$;

select public.h_runtime_evaluate_cloud_failover_gate();

comment on function public.h_runtime_evaluate_cloud_failover_gate() is
  'Evaluates H primary-cloud health/capacity and whether a validated standby runtime makes failover safe. It never promotes a storage-only backup.';
