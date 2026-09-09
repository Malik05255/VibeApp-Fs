-- Follow-up fixes from the Supabase database advisor.
-- Add a covering index for the verified-knowledge source-gap FK and remove one of two
-- byte-for-byte equivalent task indexes.

create index if not exists h_runtime_verified_knowledge_source_gap_idx
  on public.h_runtime_verified_knowledge (source_gap_id);

drop index if exists public.h_runtime_tasks_user_status_idx;
