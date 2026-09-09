-- Exact, owner-scoped recall of recently verified H knowledge.
--
-- The caller computes query_key through knowledge-gap.ts, the canonical normalization/hash
-- path already used by the Learning Queue. PostgreSQL only validates the key, applies the
-- owner boundary and enforces a 30-day freshness window.

create or replace function public.h_recall_verified_knowledge(
  p_user_key text,
  p_query_key text
)
returns table (
  answer_text text,
  verified_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_key text := btrim(coalesce(p_user_key, ''));
  v_query_key text := lower(btrim(coalesce(p_query_key, '')));
begin
  if v_user_key = ''
     or char_length(v_user_key) > 256
     or v_query_key !~ '^[0-9a-f]{64}$' then
    return;
  end if;

  return query
  with recalled as (
    update public.h_runtime_verified_knowledge k
       set last_used_at = now(),
           use_count = k.use_count + 1,
           updated_at = greatest(k.updated_at, now())
     where k.user_key = v_user_key
       and k.query_key = v_query_key
       and k.verified_at >= now() - interval '30 days'
     returning k.answer_text, k.verified_at
  )
  select recalled.answer_text, recalled.verified_at
    from recalled
   limit 1;
end;
$$;

revoke all on function public.h_recall_verified_knowledge(text,text) from public, anon, authenticated;
grant execute on function public.h_recall_verified_knowledge(text,text) to service_role;

comment on function public.h_recall_verified_knowledge(text,text) is
  'Returns exact owner-scoped H knowledge only for a canonical query key independently verified within 30 days; atomically records use.';
