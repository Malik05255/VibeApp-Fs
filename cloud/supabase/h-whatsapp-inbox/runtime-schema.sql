-- H WhatsApp cloud brain storage. Service-role only; no client policies.

alter table public.h_runtime_inbox
  add column if not exists processed_at timestamptz,
  add column if not exists reply_text text;

create table if not exists public.h_runtime_chat (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  conversation_id bigint,
  role text not null check (role in ('user','assistant')),
  body text not null,
  source_message_key text,
  created_at timestamptz not null default now()
);
alter table public.h_runtime_chat enable row level security;
create index if not exists h_runtime_chat_user_created_idx
  on public.h_runtime_chat(user_key, created_at desc);

create table if not exists public.h_runtime_memories (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  category text not null default 'note',
  body text not null,
  original_text text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
alter table public.h_runtime_memories enable row level security;
create index if not exists h_runtime_memories_user_created_idx
  on public.h_runtime_memories(user_key, created_at desc);

create table if not exists public.h_runtime_reminders (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  conversation_id bigint not null,
  body text not null,
  due_at timestamptz not null,
  status text not null default 'pending'
    check (status in ('pending','sent','waiting_template','cancelled','failed')),
  attempts integer not null default 0,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  sent_at timestamptz
);
alter table public.h_runtime_reminders enable row level security;
create index if not exists h_runtime_reminders_due_idx
  on public.h_runtime_reminders(status, due_at);
create index if not exists h_runtime_reminders_user_created_idx
  on public.h_runtime_reminders(user_key, created_at desc);

-- Keep old messages from before automatic processing from suddenly generating replies.
update public.h_runtime_inbox
set status = 'ignored',
    error = coalesce(error, 'pre_auto_runtime_backlog'),
    processed_at = coalesce(processed_at, now()),
    updated_at = now()
where status = 'new'
  and received_at < now() - interval '5 minutes';
