-- Decouple H durable identity material from the runtime polling secret.
--
-- This is intentionally a one-time copy, not a generated replacement key. Existing
-- Google/WhatsApp HMAC fingerprints and encrypted runtime-user-key ciphertext were derived
-- from poll_secret, so changing key material here would invalidate current links. Once all
-- identity consumers use identity_secret, poll_secret may be rotated independently.

insert into public.h_runtime_config (key, secret_value, updated_at)
select 'identity_secret', secret_value, now()
from public.h_runtime_config
where key = 'poll_secret'
on conflict (key) do nothing;

do $$
begin
  if not exists (
    select 1
    from public.h_runtime_config
    where key = 'identity_secret'
      and nullif(btrim(secret_value), '') is not null
  ) then
    raise exception 'H identity_secret bootstrap failed: poll_secret missing or empty';
  end if;
end
$$;

comment on table public.h_runtime_config is
  'Server-only H runtime configuration. identity_secret is durable identity key material and must never be exposed to Android or logs.';
