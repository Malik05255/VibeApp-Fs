-- H WhatsApp inbox scheduler source of truth.
--
-- The public Edge Function URL is configuration, not a credential. Runtime authorization
-- remains in h_runtime_config.poll_secret and is read only inside this SECURITY DEFINER
-- function. No runtime secret is committed to source control.

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

insert into public.h_runtime_config (key, secret_value, updated_at)
values (
  'inbox_endpoint',
  'https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-whatsapp-inbox',
  now()
)
on conflict (key) do update
set secret_value = excluded.secret_value,
    updated_at = excluded.updated_at;

create or replace function public.h_runtime_trigger_inbox_poll()
returns bigint
language plpgsql
security definer
set search_path = public, extensions, net
as $$
declare
  runtime_secret text;
  inbox_endpoint text;
  last_dispatch_at timestamptz;
  last_completed_poll_at timestamptz;
  request_id bigint;
begin
  -- Serialize duplicate scheduler calls in the same transaction.
  perform pg_advisory_xact_lock(hashtext('h-runtime-whatsapp-inbox-scheduler'));

  select nullif(value->>'last_dispatch_at', '')::timestamptz
    into last_dispatch_at
    from public.h_runtime_state
   where key = 'inbox_scheduler'
   for update;

  select nullif(value->>'last_poll_at', '')::timestamptz
    into last_completed_poll_at
    from public.h_runtime_state
   where key = 'inbox_poll';

  -- Guard accidental duplicate cron entries or manual double-dispatches.
  if last_dispatch_at is not null
     and last_dispatch_at > now() - interval '45 seconds' then
    return null;
  end if;

  -- pg_net is asynchronous. If the previous Edge invocation has not yet completed,
  -- do not start another one. A ten-minute stale lease is allowed to recover so one
  -- failed network request cannot stop H permanently.
  if last_dispatch_at is not null
     and (last_completed_poll_at is null or last_completed_poll_at < last_dispatch_at)
     and last_dispatch_at > now() - interval '10 minutes' then
    return null;
  end if;

  select secret_value
    into runtime_secret
    from public.h_runtime_config
   where key = 'poll_secret';

  if runtime_secret is null or btrim(runtime_secret) = '' then
    raise exception 'H runtime poll secret is not configured';
  end if;

  select secret_value
    into inbox_endpoint
    from public.h_runtime_config
   where key = 'inbox_endpoint';

  if inbox_endpoint is null
     or inbox_endpoint !~ '^https://[a-z0-9-]+[.]supabase[.]co/functions/v1/h-whatsapp-inbox$' then
    raise exception 'H inbox endpoint is not configured safely';
  end if;

  select net.http_post(
    url := inbox_endpoint,
    body := '{}'::jsonb,
    params := '{}'::jsonb,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-h-runtime-secret', runtime_secret
    ),
    timeout_milliseconds := 45000
  ) into request_id;

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    'inbox_scheduler',
    jsonb_build_object(
      'last_dispatch_at', now(),
      'last_request_id', request_id,
      'cadence', 'every_minute',
      'overlap_guard', true,
      'free_only', true
    ),
    now()
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  return request_id;
end;
$$;

revoke all on function public.h_runtime_trigger_inbox_poll() from public;
revoke all on function public.h_runtime_trigger_inbox_poll() from anon;
revoke all on function public.h_runtime_trigger_inbox_poll() from authenticated;

-- Replace any historical job with one canonical minute scheduler.
do $$
declare
  existing_job_id bigint;
begin
  for existing_job_id in
    select jobid
      from cron.job
     where jobname = 'h-runtime-whatsapp-inbox'
  loop
    perform cron.unschedule(existing_job_id);
  end loop;

  perform cron.schedule(
    'h-runtime-whatsapp-inbox',
    '* * * * *',
    'select public.h_runtime_trigger_inbox_poll();'
  );
end;
$$;
