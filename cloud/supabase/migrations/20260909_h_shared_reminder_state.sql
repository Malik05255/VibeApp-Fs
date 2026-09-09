-- Shared H reminder state across Android and WhatsApp.
-- Cloud is the portable source of truth; the device remains the executor for
-- app-managed time/location reminders while WhatsApp keeps its own delivery queue.

alter table public.h_runtime_reminders
  add column if not exists title text,
  add column if not exists original_text text,
  add column if not exists interpreted_text text,
  add column if not exists reminder_type text not null default 'TIME',
  add column if not exists lifecycle_status text not null default 'ACTIVE',
  add column if not exists source text not null default 'WHATSAPP',
  add column if not exists domain text not null default 'PERSONAL',
  add column if not exists recurrence_rule text,
  add column if not exists person_name text,
  add column if not exists location jsonb,
  add column if not exists cooldown_until timestamptz,
  add column if not exists completed_at timestamptz,
  add column if not exists delivery_channel text not null default 'whatsapp';

-- Location/context reminders may not have a wall-clock due time and app-managed
-- reminders do not belong to a Peach/Meta conversation.
alter table public.h_runtime_reminders
  alter column conversation_id drop not null,
  alter column due_at drop not null;

update public.h_runtime_reminders
set
  title = coalesce(nullif(title, ''), left(body, 160)),
  original_text = coalesce(original_text, body),
  interpreted_text = coalesce(interpreted_text, body),
  reminder_type = coalesce(nullif(reminder_type, ''), 'TIME'),
  lifecycle_status = case
    when status = 'sent' then 'COMPLETED'
    when status = 'cancelled' then 'CANCELLED'
    else coalesce(nullif(lifecycle_status, ''), 'ACTIVE')
  end,
  source = coalesce(nullif(source, ''), 'WHATSAPP'),
  domain = coalesce(nullif(domain, ''), 'PERSONAL'),
  delivery_channel = coalesce(nullif(delivery_channel, ''), 'whatsapp'),
  completed_at = case
    when status = 'sent' then coalesce(completed_at, sent_at, updated_at)
    else completed_at
  end
where
  title is null
  or original_text is null
  or interpreted_text is null
  or reminder_type is null
  or lifecycle_status is null
  or source is null
  or domain is null
  or delivery_channel is null
  or status in ('sent', 'cancelled');

alter table public.h_runtime_reminders
  drop constraint if exists h_runtime_reminders_lifecycle_status_check,
  add constraint h_runtime_reminders_lifecycle_status_check
    check (lifecycle_status in ('ACTIVE', 'DEFERRED', 'COMPLETED', 'DISABLED', 'CANCELLED')),
  drop constraint if exists h_runtime_reminders_type_check,
  add constraint h_runtime_reminders_type_check
    check (reminder_type in ('TIME', 'LOCATION', 'PERSON', 'RECURRING', 'CONTEXTUAL')),
  drop constraint if exists h_runtime_reminders_source_check,
  add constraint h_runtime_reminders_source_check
    check (source in ('APP_CHAT', 'WHATSAPP', 'MANUAL', 'IMPORTED')),
  drop constraint if exists h_runtime_reminders_domain_check,
  add constraint h_runtime_reminders_domain_check
    check (domain in ('PERSONAL', 'PROGRAMMING')),
  drop constraint if exists h_runtime_reminders_delivery_channel_check,
  add constraint h_runtime_reminders_delivery_channel_check
    check (delivery_channel in ('app', 'whatsapp'));

-- Existing WhatsApp code owns its delivery status. Mirror terminal/paused delivery
-- changes into the portable lifecycle so Android sees them without coupling the inbox
-- implementation to the app sync API.
create or replace function public.h_sync_reminder_delivery_lifecycle()
returns trigger
language plpgsql
as $$
begin
  if new.delivery_channel = 'whatsapp' then
    if new.status = 'sent' then
      new.lifecycle_status := 'COMPLETED';
      new.completed_at := coalesce(new.completed_at, new.sent_at, now());
    elsif new.status = 'cancelled' then
      new.lifecycle_status := 'CANCELLED';
    elsif new.status = 'paused' and new.lifecycle_status not in ('COMPLETED', 'CANCELLED', 'DISABLED') then
      new.lifecycle_status := 'DEFERRED';
    elsif new.status = 'pending' and new.lifecycle_status = 'DEFERRED' then
      new.lifecycle_status := 'ACTIVE';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists h_sync_reminder_delivery_lifecycle_trg on public.h_runtime_reminders;
create trigger h_sync_reminder_delivery_lifecycle_trg
before insert or update of status, delivery_channel, sent_at on public.h_runtime_reminders
for each row execute function public.h_sync_reminder_delivery_lifecycle();

create index if not exists h_runtime_reminders_user_lifecycle_updated_idx
  on public.h_runtime_reminders (user_key, lifecycle_status, updated_at desc);

create index if not exists h_runtime_reminders_delivery_due_idx
  on public.h_runtime_reminders (delivery_channel, status, due_at)
  where due_at is not null;
