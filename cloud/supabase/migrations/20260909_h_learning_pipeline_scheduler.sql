-- Automatic H learning pipeline scheduler.
--
-- The queue is processed conservatively once per hour to protect free provider quotas.
-- Learning Cycle runs first; the Verification Engine follows 20 minutes later. Empty
-- queues result only in a small authenticated Edge Function call and no AI research.

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

insert into public.h_runtime_config (key, secret_value, updated_at)
values
  ('learning_cycle_endpoint', 'https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-learning-cycle', now()),
  ('knowledge_verifier_endpoint', 'https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-knowledge-verifier', now())
on conflict (key) do update
set secret_value = excluded.secret_value,
    updated_at = excluded.updated_at;

create or replace function public.h_runtime_dispatch_learning_worker(p_worker text)
returns bigint
language plpgsql
security definer
set search_path = public, extensions, net
as $$
declare
  v_worker text := btrim(coalesce(p_worker, ''));
  v_endpoint_key text;
  v_state_key text;
  v_expected_suffix text;
  v_runtime_secret text;
  v_endpoint text;
  v_last_dispatch_at timestamptz;
  v_request_id bigint;
begin
  if v_worker = 'learning_cycle' then
    v_endpoint_key := 'learning_cycle_endpoint';
    v_state_key := 'learning_cycle_scheduler';
    v_expected_suffix := '/h-learning-cycle';
  elsif v_worker = 'knowledge_verifier' then
    v_endpoint_key := 'knowledge_verifier_endpoint';
    v_state_key := 'knowledge_verifier_scheduler';
    v_expected_suffix := '/h-knowledge-verifier';
  else
    raise exception 'unsupported_h_learning_worker';
  end if;

  perform pg_advisory_xact_lock(hashtext('h-runtime-' || v_worker || '-scheduler'));

  select nullif(value->>'last_dispatch_at', '')::timestamptz
    into v_last_dispatch_at
    from public.h_runtime_state
   where key = v_state_key
   for update;

  -- Prevent accidental duplicate cron rows or repeated manual dispatches. Normal cadence
  -- is hourly, so a 45-minute lease still allows recovery after a missed invocation.
  if v_last_dispatch_at is not null
     and v_last_dispatch_at > now() - interval '45 minutes' then
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
   where key = v_endpoint_key;
  if v_endpoint is null
     or v_endpoint !~ '^https://[a-z0-9-]+[.]supabase[.]co/functions/v1/[a-z0-9-]+$'
     or right(v_endpoint, char_length(v_expected_suffix)) <> v_expected_suffix then
    raise exception 'H learning worker endpoint is not configured safely';
  end if;

  select net.http_post(
    url := v_endpoint,
    body := jsonb_build_object('limit', 2),
    params := '{}'::jsonb,
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-h-runtime-secret', v_runtime_secret
    ),
    timeout_milliseconds := 45000
  ) into v_request_id;

  insert into public.h_runtime_state (key, value, updated_at)
  values (
    v_state_key,
    jsonb_build_object(
      'worker', v_worker,
      'last_dispatch_at', now(),
      'last_request_id', v_request_id,
      'cadence', 'hourly',
      'batch_limit', 2,
      'overlap_guard_minutes', 45,
      'free_only', true
    ),
    now()
  )
  on conflict (key) do update
  set value = excluded.value,
      updated_at = excluded.updated_at;

  return v_request_id;
end;
$$;

revoke all on function public.h_runtime_dispatch_learning_worker(text) from public, anon, authenticated;
grant execute on function public.h_runtime_dispatch_learning_worker(text) to service_role;

-- Replace historical jobs with one canonical hourly pipeline.
do $do$
declare
  v_job_id bigint;
begin
  for v_job_id in
    select jobid from cron.job
    where jobname in ('h-runtime-learning-cycle', 'h-runtime-knowledge-verifier')
  loop
    perform cron.unschedule(v_job_id);
  end loop;

  perform cron.schedule(
    'h-runtime-learning-cycle',
    '5 * * * *',
    $$select public.h_runtime_dispatch_learning_worker('learning_cycle');$$
  );

  perform cron.schedule(
    'h-runtime-knowledge-verifier',
    '25 * * * *',
    $$select public.h_runtime_dispatch_learning_worker('knowledge_verifier');$$
  );
end;
$do$;

comment on function public.h_runtime_dispatch_learning_worker(text) is
  'Dispatches the internal free-only H Learning Cycle or Knowledge Verifier with an hourly overlap guard and private runtime-secret authorization.';
