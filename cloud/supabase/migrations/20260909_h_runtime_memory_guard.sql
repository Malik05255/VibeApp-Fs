-- Central privacy guard for H shared memories.
-- Applies to every current and future writer (Android app sync, WhatsApp runtime, admin/runtime tools).

create or replace function public.h_runtime_memory_guard()
returns trigger
language plpgsql
set search_path = public
as $$
declare
  combined_text text;
  normalized_digits text;
begin
  new.body := left(regexp_replace(btrim(coalesce(new.body, '')), '\s+', ' ', 'g'), 280);
  if new.body = '' then
    raise exception 'invalid_memory_body' using errcode = '22023';
  end if;

  new.category := lower(btrim(coalesce(new.category, 'general')));
  if new.category not in ('identity', 'preference', 'relationship', 'idea', 'note', 'general') then
    new.category := 'general';
  end if;

  if new.original_text is not null then
    new.original_text := left(regexp_replace(btrim(new.original_text), '\s+', ' ', 'g'), 500);
    if new.original_text = '' then
      new.original_text := null;
    end if;
  end if;

  combined_text := lower(new.body || ' ' || coalesce(new.original_text, ''));
  normalized_digits := translate(
    combined_text,
    '٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹',
    '01234567890123456789'
  );

  if combined_text ~ '(password|passcode|pin|cvv|cvc|otp|api[ _-]?key|access[ _-]?token|refresh[ _-]?token|secret)'
     or combined_text ~ '(كلمة[[:space:]]*المرور|الرقم[[:space:]]*السري|رمز[[:space:]]*سري|رمز[[:space:]]*التحقق|كود[[:space:]]*التحقق|رمز[[:space:]]*الدخول|المفتاح[[:space:]]*السري|توكن|رمز[[:space:]]*otp|رقم[[:space:]]*البطاقة|رقم[[:space:]]*بطاقة)'
     or normalized_digits ~ '(^|[^0-9])([0-9][ -]?){13,19}([^0-9]|$)'
     or normalized_digits ~ '(^|[^0-9])[0-9]{6}([^0-9]|$)'
  then
    raise exception 'sensitive_memory_rejected' using errcode = '22023';
  end if;

  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists h_runtime_memory_guard_trigger on public.h_runtime_memories;
create trigger h_runtime_memory_guard_trigger
before insert or update of category, body, original_text
on public.h_runtime_memories
for each row
execute function public.h_runtime_memory_guard();

comment on function public.h_runtime_memory_guard() is
  'Normalizes H shared memories and rejects secret/credential/card/OTP-like content at the database boundary.';
