-- Normalize every existing owner-paid/BYOK row to H's exclusive AI-routing contract.
-- This migration never enables or disables a route. It only removes legacy policy flags
-- that could imply hard-task segmentation or automatic free fallback.

update public.h_runtime_ai_provider_registry
set
  hard_tasks_only = false,
  allow_free_fallback = false,
  metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object(
    'exclusive_ai_routing', true,
    'hard_tasks_only', false,
    'allow_free_fallback', false
  ),
  updated_at = now()
where route_class = 'owner_paid';

update public.h_runtime_ai_owner_paid_setup
set
  hard_tasks_only = false,
  allow_free_fallback = false
where hard_tasks_only is distinct from false
   or allow_free_fallback is distinct from false;

update public.h_runtime_state
set
  value = coalesce(value, '{}'::jsonb) || jsonb_build_object(
    'exclusive_ai_routing', true,
    'hard_tasks_only', false,
    'allow_free_fallback', false
  ),
  updated_at = now()
where key = 'owner_paid_ai';
