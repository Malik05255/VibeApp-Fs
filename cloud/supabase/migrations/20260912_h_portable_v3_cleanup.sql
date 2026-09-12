-- Immediate cleanup controls for H portable v3 staging.
-- Export/import staging contains sanitized owner portable state only, but it should still be
-- retained for the minimum time necessary. These helpers allow Android and backup flows to
-- remove transient staging immediately while preserving the durable restore idempotency ledger.

create or replace function public.h_finish_portable_export_v3(
  p_user_key text,
  p_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_deleted integer := 0;
begin
  p_user_key := btrim(coalesce(p_user_key, ''));
  if p_user_key = '' then raise exception 'portable_export_user_key_required'; end if;

  delete from public.h_runtime_portable_export_sessions
   where id = p_session_id
     and user_key = p_user_key;
  get diagnostics v_deleted = row_count;

  return jsonb_build_object(
    'ok', true,
    'finished', v_deleted = 1,
    'stagingDeleted', v_deleted = 1
  );
end;
$$;

revoke all on function public.h_finish_portable_export_v3(text, uuid)
  from public, anon, authenticated;
grant execute on function public.h_finish_portable_export_v3(text, uuid)
  to service_role;

create or replace function public.h_abort_portable_restore_v3(
  p_user_key text,
  p_import_session_id uuid
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_status text;
  v_deleted integer := 0;
begin
  p_user_key := btrim(coalesce(p_user_key, ''));
  if p_user_key = '' then raise exception 'portable_v3_restore_user_key_required'; end if;

  select status into v_status
    from public.h_runtime_portable_import_sessions
   where id = p_import_session_id
     and user_key = p_user_key;

  if not found then
    return jsonb_build_object('ok', true, 'aborted', false, 'alreadyAbsent', true);
  end if;

  -- A completed restore is represented durably by h_runtime_portable_restores. Do not remove
  -- its tiny session row here; only staging sessions are abortable.
  if v_status <> 'staging' then
    return jsonb_build_object('ok', true, 'aborted', false, 'alreadyRestored', true);
  end if;

  delete from public.h_runtime_portable_import_sessions
   where id = p_import_session_id
     and user_key = p_user_key
     and status = 'staging';
  get diagnostics v_deleted = row_count;

  return jsonb_build_object(
    'ok', true,
    'aborted', v_deleted = 1,
    'stagingDeleted', v_deleted = 1
  );
end;
$$;

revoke all on function public.h_abort_portable_restore_v3(text, uuid)
  from public, anon, authenticated;
grant execute on function public.h_abort_portable_restore_v3(text, uuid)
  to service_role;

create or replace function public.h_cleanup_portable_restore_pages_v3()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if new.status = 'restored' and old.status is distinct from new.status then
    delete from public.h_runtime_portable_import_pages
     where import_session_id = new.id;
  end if;
  return new;
end;
$$;

revoke all on function public.h_cleanup_portable_restore_pages_v3()
  from public, anon, authenticated;

drop trigger if exists h_runtime_portable_import_cleanup_pages_v3
  on public.h_runtime_portable_import_sessions;
create trigger h_runtime_portable_import_cleanup_pages_v3
after update of status on public.h_runtime_portable_import_sessions
for each row
when (new.status = 'restored' and old.status is distinct from new.status)
execute function public.h_cleanup_portable_restore_pages_v3();

comment on function public.h_finish_portable_export_v3(text, uuid) is
  'Deletes one owner-scoped portable v3 export staging session after the client has assembled its bundle.';
comment on function public.h_abort_portable_restore_v3(text, uuid) is
  'Deletes an owner-scoped portable v3 import staging session that has not been restored.';
comment on function public.h_cleanup_portable_restore_pages_v3() is
  'Deletes transient portable v3 import pages immediately after atomic restore succeeds; the idempotency ledger remains durable.';
