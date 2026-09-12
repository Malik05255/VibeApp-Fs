-- H memory integrity: one current truth in h_runtime_memories, explicit history outside recall.
-- Correction archives the old value and updates/reuses the current row. Forget archives then deletes.
-- Existing portable snapshots/restores therefore continue to move only currently recallable memory.

create table if not exists public.h_runtime_memory_history (
  id bigserial primary key,
  user_key text not null,
  source_memory_id text not null,
  event_type text not null check (event_type in ('superseded','forgotten')),
  category text not null,
  body text not null,
  original_text text,
  replacement_body text,
  occurred_at timestamptz not null default now()
);

create index if not exists h_runtime_memory_history_user_idx
  on public.h_runtime_memory_history(user_key, occurred_at desc);

alter table public.h_runtime_memory_history enable row level security;
revoke all on table public.h_runtime_memory_history from public, anon, authenticated;
grant select, insert, delete on table public.h_runtime_memory_history to service_role;

-- Old WhatsApp/runtime writers could have inserted the same exact memory more than once.
-- Archive every duplicate first, keep the newest current row, then enforce one exact current truth.
with ranked as (
  select id,
         row_number() over (
           partition by user_key, body
           order by updated_at desc nulls last, created_at desc nulls last, id desc
         ) as rn
    from public.h_runtime_memories
)
insert into public.h_runtime_memory_history(
  user_key,source_memory_id,event_type,category,body,original_text,replacement_body,occurred_at
)
select m.user_key,m.id::text,'superseded',m.category,m.body,m.original_text,m.body,now()
  from public.h_runtime_memories m
  join ranked r on r.id=m.id
 where r.rn>1;

with ranked as (
  select id,
         row_number() over (
           partition by user_key, body
           order by updated_at desc nulls last, created_at desc nulls last, id desc
         ) as rn
    from public.h_runtime_memories
)
delete from public.h_runtime_memories m
 using ranked r
 where r.id=m.id
   and r.rn>1;

create unique index if not exists h_runtime_memories_user_body_unique_idx
  on public.h_runtime_memories(user_key, body);

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

  perform pg_advisory_xact_lock(hashtextextended(p_user_key || E'\n' || lower(v_body),41));
  select * into v_existing from public.h_runtime_memories
   where user_key=p_user_key and body=v_body
   order by updated_at desc,id desc limit 1 for update;

  if found then
    update public.h_runtime_memories
       set category=coalesce(nullif(btrim(p_category),''),v_existing.category),
           original_text=coalesce(nullif(btrim(p_original_text),''),v_existing.original_text),
           updated_at=now()
     where id=v_existing.id returning * into v_existing;
    return jsonb_build_object('ok',true,'saved',true,'duplicate',true,'corrected',false,
      'memoryId',v_existing.id::text,'body',v_existing.body,'category',v_existing.category);
  end if;

  insert into public.h_runtime_memories(user_key,category,body,original_text,created_at,updated_at)
  values(p_user_key,coalesce(nullif(btrim(p_category),''),'note'),v_body,nullif(btrim(p_original_text),''),now(),now())
  returning * into v_inserted;
  return jsonb_build_object('ok',true,'saved',true,'duplicate',false,'corrected',false,
    'memoryId',v_inserted.id::text,'body',v_inserted.body,'category',v_inserted.category);
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
  v_current public.h_runtime_memories%rowtype;
  v_lock_old bigint;
  v_lock_new bigint;
begin
  p_user_key:=btrim(coalesce(p_user_key,''));
  if p_user_key='' then raise exception 'memory_user_key_required'; end if;
  v_old_body:=left(regexp_replace(btrim(coalesce(p_old_body,'')),'\s+',' ','g'),280);
  v_new_body:=left(regexp_replace(btrim(coalesce(p_new_body,'')),'\s+',' ','g'),280);
  if v_old_body='' or v_new_body='' then raise exception 'memory_correction_body_required'; end if;
  if v_old_body=v_new_body then
    return public.h_runtime_save_memory(p_user_key,p_category,v_new_body,p_original_text)
      || jsonb_build_object('corrected',false,'sameBody',true,'matched',true);
  end if;

  -- All normal H writers use the same lock namespace. Lock both exact keys in stable
  -- numeric order so app/WhatsApp corrections cannot deadlock each other.
  v_lock_old:=hashtextextended(p_user_key || E'\n' || lower(v_old_body),41);
  v_lock_new:=hashtextextended(p_user_key || E'\n' || lower(v_new_body),41);
  perform pg_advisory_xact_lock(least(v_lock_old,v_lock_new));
  if v_lock_old<>v_lock_new then
    perform pg_advisory_xact_lock(greatest(v_lock_old,v_lock_new));
  end if;

  select * into v_old from public.h_runtime_memories
   where user_key=p_user_key and body=v_old_body
   order by updated_at desc,id desc limit 1 for update;
  if not found then
    return jsonb_build_object('ok',false,'matched',false,'error','memory_target_not_found');
  end if;

  select * into v_existing_new from public.h_runtime_memories
   where user_key=p_user_key and body=v_new_body
   order by updated_at desc,id desc limit 1 for update;

  insert into public.h_runtime_memory_history(
    user_key,source_memory_id,event_type,category,body,original_text,replacement_body,occurred_at
  ) values(
    p_user_key,v_old.id::text,'superseded',v_old.category,v_old.body,v_old.original_text,v_new_body,now()
  );

  if v_existing_new.id is not null then
    delete from public.h_runtime_memories where id=v_old.id;
    update public.h_runtime_memories
       set category=coalesce(nullif(btrim(p_category),''),v_existing_new.category),
           original_text=coalesce(nullif(btrim(p_original_text),''),v_existing_new.original_text),
           updated_at=now()
     where id=v_existing_new.id returning * into v_current;
  else
    update public.h_runtime_memories
       set body=v_new_body,
           category=coalesce(nullif(btrim(p_category),''),v_old.category),
           original_text=coalesce(nullif(btrim(p_original_text),''),v_old.original_text),
           updated_at=now()
     where id=v_old.id returning * into v_current;
  end if;

  return jsonb_build_object('ok',true,'matched',true,'corrected',true,
    'memoryId',v_current.id::text,'body',v_current.body,'category',v_current.category);
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
  p_user_key:=btrim(coalesce(p_user_key,''));
  if p_user_key='' then raise exception 'memory_user_key_required'; end if;
  v_body:=left(regexp_replace(btrim(coalesce(p_body,'')),'\s+',' ','g'),280);
  if v_body='' then raise exception 'memory_forget_body_required'; end if;

  perform pg_advisory_xact_lock(hashtextextended(p_user_key || E'\n' || lower(v_body),41));
  with targets as (
    select * from public.h_runtime_memories where user_key=p_user_key and body=v_body for update
  ), archived as (
    insert into public.h_runtime_memory_history(
      user_key,source_memory_id,event_type,category,body,original_text,replacement_body,occurred_at
    ) select p_user_key,id::text,'forgotten',category,body,original_text,null,now() from targets
    returning source_memory_id
  )
  delete from public.h_runtime_memories m
   where m.id::text in (select source_memory_id from archived);
  get diagnostics v_count=row_count;

  return jsonb_build_object('ok',v_count>0,'matched',v_count>0,'forgotten',v_count,
    'error',case when v_count=0 then 'memory_target_not_found' else null end);
end;
$$;

revoke all on function public.h_runtime_save_memory(text,text,text,text) from public,anon,authenticated;
revoke all on function public.h_runtime_correct_memory(text,text,text,text,text) from public,anon,authenticated;
revoke all on function public.h_runtime_forget_memory(text,text) from public,anon,authenticated;
grant execute on function public.h_runtime_save_memory(text,text,text,text) to service_role;
grant execute on function public.h_runtime_correct_memory(text,text,text,text,text) to service_role;
grant execute on function public.h_runtime_forget_memory(text,text) to service_role;

comment on table public.h_runtime_memory_history is
  'Private H memory mutation history. Not recalled into prompts and not part of portable owner state.';
comment on function public.h_runtime_correct_memory(text,text,text,text,text) is
  'Explicit exact-target correction; archives the old value and leaves one current recallable truth.';
comment on function public.h_runtime_forget_memory(text,text) is
  'Explicit exact-target forget; archives then removes the current recallable value.';
