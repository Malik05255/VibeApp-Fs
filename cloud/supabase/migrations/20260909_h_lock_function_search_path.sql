-- Lock search_path on older H helper functions flagged by the Supabase security advisor.
-- This changes lookup safety only; function bodies and behavior remain unchanged.

alter function public.h_sync_reminder_delivery_lifecycle()
  set search_path = public;

alter function public.h_merge_learning_tag_max(jsonb, jsonb)
  set search_path = public;
