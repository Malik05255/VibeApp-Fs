-- A paid helper is activated only after the owner sees the live price snapshot and
-- explicitly confirms it in a second step. Pending API keys stay encrypted and expire
-- with the one-time setup token.

alter table public.h_runtime_ai_owner_paid_setup
  add column if not exists pending_secret_ciphertext text,
  add column if not exists pending_secret_iv text,
  add column if not exists pricing_ceiling jsonb,
  add column if not exists pricing_verified_at timestamptz;

alter table public.h_runtime_ai_owner_paid_setup
  add constraint h_ai_owner_paid_setup_pending_secret_pair_check
  check (
    (pending_secret_ciphertext is null and pending_secret_iv is null)
    or (pending_secret_ciphertext is not null and pending_secret_iv is not null)
  ) not valid;

alter table public.h_runtime_ai_owner_paid_setup
  validate constraint h_ai_owner_paid_setup_pending_secret_pair_check;

comment on column public.h_runtime_ai_owner_paid_setup.pricing_ceiling is
  'Live OpenRouter pricing snapshot shown to the owner before the separate activation confirmation.';
