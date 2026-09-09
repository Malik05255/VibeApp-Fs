-- Prevent H standby replication dispatches from hitting an undeployed worker before a
-- real validated backup target exists. This is an operational no-op gate only; it never
-- marks a standby ready and never enables failover.

create or replace function public.h_runtime_dispatch_standby_replicator()
returns bigint
language plpgsql
security definer
set search_path = public, extensions, net
as $$
declare
  v_runtime_secret text;
  v_endpoint text;
  v_last_dispatch_at timestamptz;
  v_request_id bigint;
  v_backup_ready boolean := false;
begin
  perform pg_advisory_xact_lock(hashtext('h-runtime-standby-replication-scheduler'));

  select exists (
    select 1
      from public.h_runtime_cloud_registry c
     where c.id = 'h_backup_supabase_storage'
       and c.cloud_role = 'backup'
       and c.enabled = true
       and c.ready = true
       and c.last_health_ok = true
       and c.credential_id = 'h_backup_supabase_storage'
       and coalesce((c.metadata->>'storage_backup_ready')::boolean, false) = true
       and coalesce((c.metadata->>'connection_validated')::boolean, false) = true
  ) into v_backup_ready;

  if not v_backup_ready then
    insert into public.h_runtime_state (key, value, updated_at)
    values (
      'standby_replication_scheduler',
      jsonb_build_object(
        'worker', 'h-standby-replicator',
        'cadence', 'every_minute',
        'status', 'waiting_for_backup',
        'last_dispatch_at', null,
        'last_request_id', null,
        'overlap_guard_seconds', 45,
        'automatic_failover_enabled_by_scheduler', false,
        'raw_media_included', false,
        'runtime_secrets_included', false,
        'provider_credentials_included', false
      ),
      now()
    )
    on conflict (key) do update
    set value = excluded.value,
        updated_at = excluded.updated_at;
    return null;
  end if;

  select nullif(value->>'last_dispatch_at', '')::timestamptz
    into v_last_dispatch_at
    from public.h_runtime_state
   where key = 'standby_replication_scheduler'
   for update;

  if v_last_dispatch_at is not null
     and v_last_dispatch_at > now() - interval '45 seconds' then
    return null;
  end if;

  select secret_value into v_runtime_secret
    from public.h_runtime_config
   where key = 'poll_secret';
  if v_runtime_secret is null or btrim(v_runtime_secret) = '' then
    raise exception 'H runtime poll secret is not configured';
  end if;

  select secret_value into v_endpoint
    from public.h_runtime_config
   where key = 'standby_replicator_endpoint';
  if v_endpoint is null
     or v_endpoint !~ '^https://[a-z0-9-]+[.]supabase[.]co/functions/v1/h-standby-replicator$' then
    raise exception 'H standby replication worker endpoint is not configured safely';
  end if;

  select net.http_post(
    url := v_endpoint,
    body := '{}'::jsonb,
    params := '{}'::jsonb,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-h-runtime-secret', v_runtime_secret
    ),
    timeout_milliseconds := 50000
  ) into v_request_id;

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'standby_replication_scheduler',
    jsonb_build_object(
      'worker', 'h-standby-replicator',
      'cadence', 'every_minute',
      'status', 'dispatched',
      'last_dispatch_at', now(),
      'last_request_id', v_request_id,
      'overlap_guard_seconds', 45,
      'automatic_failover_enabled_by_scheduler', false,
      'raw_media_included', false,
      'runtime_secrets_included', false,
      'provider_credentials_included', false
    ),
    now()
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  return v_request_id;
end;
$$;

revoke all on function public.h_runtime_dispatch_standby_replicator() from public, anon, authenticated;
grant execute on function public.h_runtime_dispatch_standby_replicator() to service_role;

comment on function public.h_runtime_dispatch_standby_replicator() is
  'Dispatches H standby replication only after a real validated backup target exists; otherwise records waiting_for_backup and performs no network request.';
