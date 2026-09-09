-- Backward-compatible H portable restore schema v2.
--
-- v2 adds only the owner's durable named contacts. H Google/WhatsApp routing identities,
-- provider credentials, raw media and cloud secrets remain outside the portable schema.
-- Existing v1 snapshots and h_restore_portable_snapshot_v1 stay supported unchanged.

alter table public.h_runtime_portable_restores
  drop constraint if exists h_runtime_portable_restores_schema_check;

alter table public.h_runtime_portable_restores
  add constraint h_runtime_portable_restores_schema_check
  check (schema_version in (1, 2));

create or replace function public.h_restore_portable_snapshot_v2(
  p_user_key text,
  p_snapshot_digest text,
  p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  existing_restore jsonb;
  core_payload jsonb;
  core_result jsonb;
  contact_item jsonb;
  contact_name_key text;
  contact_display_name text;
  contact_target_wa_id text;
  imported_created_at timestamptz;
  imported_updated_at timestamptz;
  inserted_contacts integer := 0;
  skipped_contacts integer := 0;
  final_result jsonb;
begin
  p_user_key := btrim(coalesce(p_user_key, ''));
  p_snapshot_digest := lower(btrim(coalesce(p_snapshot_digest, '')));

  if p_user_key = '' then
    raise exception 'portable_restore_user_key_required' using errcode = '22023';
  end if;
  if p_snapshot_digest !~ '^[0-9a-f]{64}$' then
    raise exception 'portable_restore_digest_invalid' using errcode = '22023';
  end if;
  if jsonb_typeof(p_payload) <> 'object'
     or p_payload->>'assistantIdentity' <> 'H'
     or p_payload->>'scope' <> 'portable_core_v2'
     or jsonb_typeof(p_payload->'memories') <> 'array'
     or jsonb_typeof(p_payload->'tasks') <> 'array'
     or jsonb_typeof(p_payload->'reminders') <> 'array'
     or jsonb_typeof(p_payload->'contacts') <> 'array'
  then
    raise exception 'portable_restore_payload_invalid' using errcode = '22023';
  end if;
  if jsonb_array_length(p_payload->'memories') > 500
     or jsonb_array_length(p_payload->'tasks') > 500
     or jsonb_array_length(p_payload->'reminders') > 500
     or jsonb_array_length(p_payload->'contacts') > 500
  then
    raise exception 'portable_restore_payload_too_large' using errcode = '22023';
  end if;

  -- Serialize v2 restores for one H identity before invoking the existing v1 core merge.
  -- A separate advisory-lock salt avoids coupling this wrapper to v1's internal lock.
  perform pg_advisory_xact_lock(hashtextextended(p_user_key, 1));

  select r.result into existing_restore
  from public.h_runtime_portable_restores r
  where r.user_key = p_user_key and r.snapshot_digest = p_snapshot_digest;

  if existing_restore is not null then
    return existing_restore || jsonb_build_object('idempotentReplay', true);
  end if;

  -- Reuse the already-hardened v1 transaction for memories, tasks, reminders and learning.
  -- The v1 ledger row is created inside the same transaction and is upgraded to v2 only
  -- after contacts also merge successfully. Any error rolls the entire transaction back.
  core_payload := (p_payload - 'contacts') || jsonb_build_object('scope', 'portable_core_v1');
  core_result := public.h_restore_portable_snapshot_v1(
    p_user_key,
    p_snapshot_digest,
    core_payload
  );

  for contact_item in select value from jsonb_array_elements(p_payload->'contacts') loop
    contact_name_key := btrim(coalesce(contact_item->>'nameKey', ''));
    contact_display_name := btrim(coalesce(contact_item->>'displayName', ''));
    contact_target_wa_id := btrim(coalesce(contact_item->>'targetWaId', ''));

    if contact_name_key = ''
       or char_length(contact_name_key) > 120
       or contact_display_name = ''
       or char_length(contact_display_name) > 120
       or contact_target_wa_id !~ '^[0-9]{8,20}$'
    then
      raise exception 'portable_restore_contact_invalid' using errcode = '22023';
    end if;

    -- Merge-only guarantee: a destination contact with the same normalized name wins.
    -- We never overwrite an existing target number during a Move H import.
    if exists (
      select 1 from public.h_runtime_contacts c
      where c.user_key = p_user_key and c.name_key = contact_name_key
    ) then
      skipped_contacts := skipped_contacts + 1;
    else
      imported_created_at := coalesce(nullif(contact_item->>'createdAt', '')::timestamptz, now());
      imported_updated_at := coalesce(nullif(contact_item->>'updatedAt', '')::timestamptz, imported_created_at);
      insert into public.h_runtime_contacts (
        user_key, name_key, display_name, target_wa_id, created_at, updated_at
      ) values (
        p_user_key,
        contact_name_key,
        contact_display_name,
        contact_target_wa_id,
        imported_created_at,
        imported_updated_at
      );
      inserted_contacts := inserted_contacts + 1;
    end if;
  end loop;

  final_result := core_result || jsonb_build_object(
    'schemaVersion', 2,
    'idempotentReplay', false,
    'contacts', jsonb_build_object(
      'inserted', inserted_contacts,
      'skippedExisting', skipped_contacts
    )
  );

  update public.h_runtime_portable_restores
  set schema_version = 2,
      result = final_result
  where user_key = p_user_key and snapshot_digest = p_snapshot_digest;

  return final_result;
end;
$$;

revoke all on function public.h_restore_portable_snapshot_v2(text, text, jsonb)
  from public, anon, authenticated;
grant execute on function public.h_restore_portable_snapshot_v2(text, text, jsonb)
  to service_role;

comment on function public.h_restore_portable_snapshot_v2(text, text, jsonb) is
  'Atomically merge-restores H portable core v2, preserving v1 behavior and adding named contacts without overwriting existing destination contacts.';
