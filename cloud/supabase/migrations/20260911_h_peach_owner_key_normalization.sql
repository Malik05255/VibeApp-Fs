-- Keep the legacy Peach ingress on the same canonical H owner key used by Meta and Android.
-- Phone-like identifiers are normalized to digits-only before inbox rows are persisted.
-- Existing phone-like keys are migrated without touching non-phone fallback keys such as conversation:<id>.

create or replace function public.h_normalize_inbox_contact_phone()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  digits text;
begin
  if new.contact_phone is null then
    return new;
  end if;

  digits := regexp_replace(new.contact_phone, '[^0-9]', '', 'g');
  if new.contact_phone ~ '^\s*\+?[0-9][0-9 ()-]*\s*$'
     and digits ~ '^[0-9]{8,20}$' then
    new.contact_phone := digits;
  end if;
  return new;
end;
$$;

drop trigger if exists h_runtime_inbox_normalize_contact_phone on public.h_runtime_inbox;
create trigger h_runtime_inbox_normalize_contact_phone
before insert or update of contact_phone on public.h_runtime_inbox
for each row
execute function public.h_normalize_inbox_contact_phone();

update public.h_runtime_inbox
set contact_phone = regexp_replace(contact_phone, '[^0-9]', '', 'g'),
    updated_at = now()
where contact_phone is not null
  and contact_phone ~ '^\s*\+?[0-9][0-9 ()-]*\s*$'
  and regexp_replace(contact_phone, '[^0-9]', '', 'g') ~ '^[0-9]{8,20}$'
  and contact_phone <> regexp_replace(contact_phone, '[^0-9]', '', 'g');

update public.h_runtime_chat
set user_key = regexp_replace(user_key, '[^0-9]', '', 'g')
where user_key ~ '^\s*\+?[0-9][0-9 ()-]*\s*$'
  and regexp_replace(user_key, '[^0-9]', '', 'g') ~ '^[0-9]{8,20}$'
  and user_key <> regexp_replace(user_key, '[^0-9]', '', 'g');

update public.h_runtime_tasks
set user_key = regexp_replace(user_key, '[^0-9]', '', 'g'),
    updated_at = now()
where user_key ~ '^\s*\+?[0-9][0-9 ()-]*\s*$'
  and regexp_replace(user_key, '[^0-9]', '', 'g') ~ '^[0-9]{8,20}$'
  and user_key <> regexp_replace(user_key, '[^0-9]', '', 'g');

update public.h_runtime_reminders
set user_key = regexp_replace(user_key, '[^0-9]', '', 'g'),
    updated_at = now()
where user_key ~ '^\s*\+?[0-9][0-9 ()-]*\s*$'
  and regexp_replace(user_key, '[^0-9]', '', 'g') ~ '^[0-9]{8,20}$'
  and user_key <> regexp_replace(user_key, '[^0-9]', '', 'g');
