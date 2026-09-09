-- Daily encrypted H backup scheduler.
-- Dispatch is harmless when no validated backup cloud exists: the worker returns skipped.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

insert into public.h_runtime_config (key, secret_value, updated_at)
values (
  'backup_runner_endpoint',
  'https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-backup-runner',
  now()
)
on conflict (key) do update
set secret_value = excluded.secret_value,
    updated_at = excluded.updated_at;

create or replace function public.h_runtime_dispatch_backup_worker()
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
  perform pg_advisory_xact_lock(hashtext('h-runtime-backup-scheduler'));

  select nullif(value->>'last_dispatch_at', '')::timestamptz
    into v_last_dispatch_at
    from public.h_runtime_state
   where key = 'backup_scheduler'
   for update;

  -- Daily cadence with a 20-hour overlap guard prevents accidental duplicate cron rows
  -- or repeated manual dispatches from producing multiple snapshots in the same cycle.
  if v_last_dispatch_at is not null
     and v_last_dispatch_at > now() - interval '20 hours' then
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
   where key = 'backup_runner_endpoint';
  if v_endpoint is null
     or v_endpoint !~ '^https://[a-z0-9-]+[.]supabase[.]co/functions/v1/h-backup-runner$' then
    raise exception 'H backup worker endpoint is not configured safely';
  end if;

  select net.http_post(
    url := v_endpoint,
    body := '{}'::jsonb,
    params := '{}'::jsonb,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-h-runtime-secret', v_runtime_secret
    ),
    timeout_milliseconds := 60000
  ) into v_request_id;

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'backup_scheduler',
    jsonb_build_object(
      'worker', 'h-backup-runner',
      'last_dispatch_at', now(),
      'last_request_id', v_request_id,
      'cadence', 'daily',
      'overlap_guard_hours', 20,
      'encrypted', true,
      'raw_media_included', false
    ),
    now()
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  return v_request_id;
end;
$$;

revoke all on function public.h_runtime_dispatch_backup_worker() from public, anon, authenticated;
grant execute on function public.h_runtime_dispatch_backup_worker() to service_role;

do $do$
declare
  v_job_id bigint;
begin
  for v_job_id in
    select jobid from cron.job where jobname = 'h-runtime-daily-backup'
  loop
    perform cron.unschedule(v_job_id);
  end loop;

  perform cron.schedule(
    'h-runtime-daily-backup',
    '35 2 * * *',
    $$select public.h_runtime_dispatch_backup_worker();$$
  );
end;
$do$;

comment on function public.h_runtime_dispatch_backup_worker() is
  'Dispatches the encrypted H portable-backup worker once daily with a private runtime-secret header and a 20-hour overlap guard.';
