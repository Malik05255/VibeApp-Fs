-- H standby replication scheduler on the current primary cloud.
--
-- The worker is safe to dispatch before a standby exists: it returns
-- `standby_replication_not_ready` and performs no remote write. The scheduler itself
-- never changes the primary role and never enables automatic failover.

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

insert into public.h_runtime_config (key, secret_value, updated_at)
values (
  'standby_replicator_endpoint',
  'https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-standby-replicator',
  now()
)
on conflict (key) do update
set secret_value = excluded.secret_value,
    updated_at = excluded.updated_at;

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
begin
  perform pg_advisory_xact_lock(hashtext('h-runtime-standby-replication-scheduler'));

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
      'last_dispatch_at', now(),
      'last_request_id', v_request_id,
      'cadence', 'every_minute',
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

do $do$
declare
  v_job_id bigint;
begin
  for v_job_id in
    select jobid from cron.job where jobname = 'h-runtime-standby-replication'
  loop
    perform cron.unschedule(v_job_id);
  end loop;

  perform cron.schedule(
    'h-runtime-standby-replication',
    '* * * * *',
    $$select public.h_runtime_dispatch_standby_replicator();$$
  );
end;
$do$;

comment on function public.h_runtime_dispatch_standby_replicator() is
  'Dispatches H exact-mirror standby replication once per minute with a 45-second overlap guard. It does not promote a standby or enable failover.';
