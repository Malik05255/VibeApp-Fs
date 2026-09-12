-- H memory integrity: explicit correction/forget semantics with backward-compatible active state.
-- Existing rows become active automatically. History is retained but only active memories are recalled.

alter table public.h_runtime_memories
  add column if not exists memory_state text not null default 'active',
  add column if not exists memory_chain_key text,
  add column if not exists superseded_at timestamptz,
  add column if not exists forgotten_at timestamptz;

alter table public.h_runtime_memories
  drop constraint if exists h_runtime_memories_memory_state_check;
alter table public.h_runtime_memories
  add constraint h_runtime_memories_memory_state_check
  check (memory_state in ('active','superseded','forgotten'));

create index if not exists h_runtime_memories_active_user_idx
  on public.h_runtime_memories(user_key, updated_at desc)
  where memory_state = 'active';

create unique index if not exists h_runtime_memories_active_chain_idx
  on public.h_runtime_memories(user_key, memory_chain_key)
  where memory_state = 'active' and memory_chain_key is not null;

create or replace function public.h_runtime_save_memory(
  p_user_key text,
  p_category text,
  p_body text,
  p_original_text text default null
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_body text;
  v_existing public.h_runtime_memories%rowtype;
  v_inserted public.h_runtime_memories%rowtype;
begin
  p_user_key := btrim(coalesce(p_user_key,''));
  if p_user_key = '' then raise exception 'memory_user_key_required'; end if;
  v_body := left(regexp_replace(btrim(coalesce(p_body,'')),'\s+',' ','g'),280);
  if v_body = '' then raise exception 'invalid_memory_body'; end if;

  perform pg_advisory_xact_lock(hashtextextended(p_user_key || E'\n' || lower(v_body), 41));

  select * into v_existing
    from public.h_runtime_memories
   where user_key = p_user_key
     and memory_state = 'active'
     and body = v_body
   order by updated_at desc, id desc
   limit 1
   for update;

  if found then
    update public.h_runtime_memories
       set category = coalesce(nullif(btrim(p_category),''), v_existing.category),
           original_text = coalesce(nullif(btrim(p_original_text),''), v_existing.original_text),
           updated_at = now()
     where id = v_existing.id
     returning * into v_existing;
    return jsonb_build_object(
      'ok',true,'saved',true,'duplicate',true,'corrected',false,
      'memoryId',v_existing.id::text,'state',v_existing.memory_state,'body',v_existing.body,'category',v_existing.category
    );
  end if;

  insert into public.h_runtime_memories(user_key,category,body,original_text,memory_state,created_at,updated_at)
  values(p_user_key,coalesce(nullif(btrim(p_category),''),'note'),v_body,nullif(btrim(p_original_text),''),'active',now(),now())
  returning * into v_inserted;

  return jsonb_build_object(
    'ok',true,'saved',true,'duplicate',false,'corrected',false,
    'memoryId',v_inserted.id::text,'state',v_inserted.memory_state,'body',v_inserted.body,'category',v_inserted.category
  );
end;
$$;

create or replace function public.h_runtime_correct_memory(
  p_user_key text,
  p_old_body text,
  p_new_body text,
  p_category text default null,
  p_original_text text default null
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_old_body text;
  v_new_body text;
  v_old public.h_runtime_memories%rowtype;
  v_existing_new public.h_runtime_memories%rowtype;
  v_new public.h_runtime_memories%rowtype;
  v_chain text;
begin
  p_user_key := btrim(coalesce(p_user_key,''));
  if p_user_key = '' then raise exception 'memory_user_key_required'; end if;
  v_old_body := left(regexp_replace(btrim(coalesce(p_old_body,'')),'\s+',' ','g'),280);
  v_new_body := left(regexp_replace(btrim(coalesce(p_new_body,'')),'\s+',' ','g'),280);
  if v_old_body = '' or v_new_body = '' then raise exception 'memory_correction_body_required'; end if;
  if v_old_body = v_new_body then
    return public.h_runtime_save_memory(p_user_key,p_category,v_new_body,p_original_text)
      || jsonb_build_object('corrected',false,'sameBody',true);
  end if;

  perform pg_advisory_xact_lock(hashtextextended(p_user_key || E'\n' || lower(v_old_body), 42));

  select * into v_old
    from public.h_runtime_memories
   where user_key = p_user_key
     and memory_state = 'active'
     and body = v_old_body
   order by updated_at desc, id desc
   limit 1
   for update;

  if not found then
    return jsonb_build_object('ok',false,'matched',false,'error','memory_target_not_found');
  end if;

  v_chain := coalesce(nullif(v_old.memory_chain_key,''),'chain:' || v_old.id::text);

  select * into v_existing_new
    from public.h_runtime_memories
   where user_key = p_user_key
     and memory_state = 'active'
     and body = v_new_body
     and id <> v_old.id
   order by updated_at desc, id desc
   limit 1
   for update;

  update public.h_runtime_memories
     set memory_state = 'superseded',
         superseded_at = now(),
         memory_chain_key = v_chain,
         updated_at = now()
   where id = v_old.id;

  if v_existing_new.id is not null then
    update public.h_runtime_memories
       set category = coalesce(nullif(btrim(p_category),''),v_existing_new.category),
           original_text = coalesce(nullif(btrim(p_original_text),''),v_existing_new.original_text),
           memory_chain_key = coalesce(v_existing_new.memory_chain_key,v_chain),
           updated_at = now()
     where id = v_existing_new.id
     returning * into v_new;
  else
    insert into public.h_runtime_memories(
      user_key,category,body,original_text,memory_state,memory_chain_key,created_at,updated_at
    ) values(
      p_user_key,
      coalesce(nullif(btrim(p_category),''),v_old.category),
      v_new_body,
      coalesce(nullif(btrim(p_original_text),''),v_old.original_text),
      'active',v_chain,now(),now()
    ) returning * into v_new;
  end if;

  return jsonb_build_object(
    'ok',true,'matched',true,'corrected',true,
    'supersededMemoryId',v_old.id::text,'memoryId',v_new.id::text,
    'state',v_new.memory_state,'body',v_new.body,'category',v_new.category
  );
end;
$$;

create or replace function public.h_runtime_forget_memory(
  p_user_key text,
  p_body text
) returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_body text;
  v_count integer;
begin
  p_user_key := btrim(coalesce(p_user_key,''));
  if p_user_key = '' then raise exception 'memory_user_key_required'; end if;
  v_body := left(regexp_replace(btrim(coalesce(p_body,'')),'\s+',' ','g'),280);
  if v_body = '' then raise exception 'memory_forget_body_required'; end if;

  perform pg_advisory_xact_lock(hashtextextended(p_user_key || E'\n' || lower(v_body), 43));
  update public.h_runtime_memories
     set memory_state = 'forgotten', forgotten_at = now(), updated_at = now()
   where user_key = p_user_key
     and memory_state = 'active'
     and body = v_body;
  get diagnostics v_count = row_count;

  return jsonb_build_object(
    'ok',v_count > 0,'matched',v_count > 0,'forgotten',v_count,
    'error',case when v_count = 0 then 'memory_target_not_found' else null end
  );
end;
$$;

revoke all on function public.h_runtime_save_memory(text,text,text,text) from public,anon,authenticated;
revoke all on function public.h_runtime_correct_memory(text,text,text,text,text) from public,anon,authenticated;
revoke all on function public.h_runtime_forget_memory(text,text) from public,anon,authenticated;
grant execute on function public.h_runtime_save_memory(text,text,text,text) to service_role;
grant execute on function public.h_runtime_correct_memory(text,text,text,text,text) to service_role;
grant execute on function public.h_runtime_forget_memory(text,text) to service_role;

comment on function public.h_runtime_correct_memory(text,text,text,text,text) is
  'Explicit exact-target memory correction. Retains superseded history while preventing stale recall.';
comment on function public.h_runtime_forget_memory(text,text) is
  'Explicit exact-target forget. Marks active memory forgotten without broad semantic deletion.';
