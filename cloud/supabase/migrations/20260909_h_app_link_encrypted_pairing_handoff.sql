-- Allow Android H to finalize owner pairing without asking the owner to re-enter a WhatsApp number.
-- The WhatsApp handler stores only an AES-GCM encrypted runtime user key on the short-lived pairing row.

alter table if exists public.h_runtime_owner_pairing
  add column if not exists consumed_user_key_ciphertext text;

comment on column public.h_runtime_owner_pairing.consumed_user_key_ciphertext is
  'Server-only encrypted H runtime user key captured when owner pairing is consumed. Never stores a raw WhatsApp id.';
