create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron with schema extensions;

create table if not exists public.h_runtime_config (
  key text primary key,
  secret_value text not null,
  updated_at timestamptz not null default now()
);
alter table public.h_runtime_config enable row level security;

create table if not exists public.h_runtime_state (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.h_runtime_state enable row level security;

create table if not exists public.h_runtime_inbox (
  message_key text primary key,
  peach_message_id text,
  conversation_id bigint,
  contact_phone text,
  business_phone_number text,
  direction text,
  message_type text,
  body text,
  source_created_at timestamptz,
  raw jsonb not null default '{}'::jsonb,
  status text not null default 'new' check (status in ('new','processing','processed','ignored','failed')),
  error text,
  received_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.h_runtime_inbox enable row level security;
create index if not exists h_runtime_inbox_status_received_idx on public.h_runtime_inbox(status, received_at);
create index if not exists h_runtime_inbox_conversation_idx on public.h_runtime_inbox(conversation_id, source_created_at desc);

insert into public.h_runtime_config(key, secret_value)
values ('poll_secret', encode(gen_random_bytes(32), 'hex'))
on conflict (key) do nothing;

insert into public.h_runtime_state(key, value)
values ('inbox_poll', jsonb_build_object('last_poll_at', now() - interval '2 hours'))
on conflict (key) do nothing;

create or replace function public.h_runtime_trigger_inbox_poll()
returns bigint
language plpgsql
security definer
set search_path = public, extensions, net
as $$
declare
  v_secret text;
  v_request_id bigint;
begin
  select secret_value into v_secret from public.h_runtime_config where key = 'poll_secret';
  if v_secret is null then
    raise exception 'H poll secret missing';
  end if;

  select net.http_post(
    url := 'https://abavsspydbpkudhswmzp.supabase.co/functions/v1/h-whatsapp-inbox',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-h-runtime-secret', v_secret
    ),
    body := '{}'::jsonb
  ) into v_request_id;

  return v_request_id;
end;
$$;

revoke all on function public.h_runtime_trigger_inbox_poll() from public, anon, authenticated;

do $$
declare
  v_jobid bigint;
begin
  select jobid into v_jobid from cron.job where jobname = 'h-runtime-whatsapp-inbox' limit 1;
  if v_jobid is not null then
    perform cron.unschedule(v_jobid);
  end if;
  perform cron.schedule('h-runtime-whatsapp-inbox', '* * * * *', 'select public.h_runtime_trigger_inbox_poll();');
end $$;
