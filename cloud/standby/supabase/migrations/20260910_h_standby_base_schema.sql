-- Minimal H schema for a brand-new dedicated standby Supabase project.
--
-- This file is intentionally under cloud/standby and MUST NOT be applied to the primary.
-- It creates only the state required by exact_mirror_v1 plus standby health/config state.
-- It creates no scheduler, webhook, provider credential, AI route, or outbound execution path.

create table if not exists public.h_runtime_state (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

create table if not exists public.h_runtime_config (
  key text primary key,
  secret_value text not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.h_runtime_inbox (
  message_key text primary key,
  peach_message_id text,
  conversation_id bigint,
  contact_phone text,
  business_phone_number text,
  direction text,
  message_type text,
  body text,
  source_created_at timestamptz,
  raw jsonb not null default '{}'::jsonb,
  status text not null default 'new' check (status in ('new','processing','processed','ignored','failed')),
  error text,
  received_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  processed_at timestamptz,
  reply_text text
);

create table if not exists public.h_runtime_memories (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  category text not null default 'note',
  body text not null,
  original_text text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.h_runtime_contacts (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  name_key text not null,
  display_name text not null,
  target_wa_id text not null check (target_wa_id ~ '^[0-9]{8,20}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint h_runtime_contacts_user_name_unique unique (user_key, name_key)
);

create table if not exists public.h_runtime_tasks (
  id bigserial primary key,
  user_key text not null,
  conversation_id bigint,
  title text,
  body text not null,
  task_type text not null default 'general',
  priority text not null default 'medium' check (priority in ('simple','medium','important')),
  priority_source text not null default 'auto' check (priority_source in ('user','auto')),
  status text not null default 'active' check (status in ('active','paused','completed','cancelled')),
  due_at timestamptz,
  execution_plan jsonb not null default '{}'::jsonb,
  metadata jsonb not null default '{}'::jsonb,
  result_text text,
  paused_at timestamptz,
  completed_at timestamptz,
  cancelled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.h_runtime_reminders (
  id uuid primary key default gen_random_uuid(),
  user_key text not null,
  conversation_id bigint,
  body text not null,
  due_at timestamptz,
  status text not null default 'pending' check (status in ('pending','paused','sent','waiting_template','cancelled','failed')),
  attempts integer not null default 0,
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  sent_at timestamptz,
  priority_class text not null default 'medium' check (priority_class in ('simple','medium','important')),
  priority_source text not null default 'auto' check (priority_source in ('auto','user')),
  classification_reason text,
  paused_at timestamptz,
  task_id bigint references public.h_runtime_tasks(id) on delete set null,
  title text,
  original_text text,
  interpreted_text text,
  reminder_type text not null default 'TIME' check (reminder_type in ('TIME','LOCATION','PERSON','RECURRING','CONTEXTUAL')),
  lifecycle_status text not null default 'ACTIVE' check (lifecycle_status in ('ACTIVE','DEFERRED','COMPLETED','DISABLED','CANCELLED')),
  source text not null default 'WHATSAPP' check (source in ('APP_CHAT','WHATSAPP','MANUAL','IMPORTED')),
  domain text not null default 'PERSONAL' check (domain in ('PERSONAL','PROGRAMMING')),
  recurrence_rule text,
  person_name text,
  location jsonb,
  cooldown_until timestamptz,
  completed_at timestamptz,
  delivery_channel text not null default 'whatsapp' check (delivery_channel in ('app','whatsapp'))
);

create table if not exists public.h_runtime_learning_state (
  user_key text primary key,
  first_met_at timestamptz not null default now(),
  last_interaction_at timestamptz not null default now(),
  turn_count bigint not null default 0 check (turn_count >= 0),
  directness_score smallint not null default 0 check (directness_score between 0 and 20),
  technical_depth_score smallint not null default 0 check (technical_depth_score between 0 and 20),
  programming_interest_score smallint not null default 0 check (programming_interest_score between 0 and 20),
  solution_breadth_score smallint not null default 0 check (solution_breadth_score between 0 and 20),
  arabic_preference_score smallint not null default 0 check (arabic_preference_score between 0 and 20),
  concise_preference_score smallint not null default 0 check (concise_preference_score between 0 and 20),
  code_replacement_preference_score smallint not null default 0 check (code_replacement_preference_score between 0 and 20),
  interaction_samples bigint not null default 0 check (interaction_samples >= 0),
  interest_tags jsonb not null default '{}'::jsonb check (jsonb_typeof(interest_tags) = 'object'),
  updated_at timestamptz not null default now()
);

create table if not exists public.h_runtime_knowledge_gaps (
  id uuid primary key default gen_random_uuid(),
  user_key text not null check (char_length(user_key) between 1 and 256),
  query_key text not null check (query_key ~ '^[0-9a-f]{64}$'),
  query_text text not null check (char_length(query_text) between 1 and 600),
  status text not null default 'pending' check (status in ('pending','researching','candidate','verifying','verified','failed','dismissed')),
  first_reason text not null check (first_reason in ('explicit_uncertainty','research_no_evidence','verifier_rejected','tool_unavailable')),
  last_reason text not null check (last_reason in ('explicit_uncertainty','research_no_evidence','verifier_rejected','tool_unavailable')),
  priority text not null default 'medium' check (priority in ('simple','medium','important')),
  occurrences integer not null default 1 check (occurrences >= 1),
  research_attempts integer not null default 0 check (research_attempts >= 0),
  next_research_at timestamptz,
  last_researched_at timestamptz,
  verified_at timestamptz,
  verification_summary text check (verification_summary is null or char_length(verification_summary) <= 1200),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  candidate_answer text check (candidate_answer is null or char_length(candidate_answer) between 1 and 3000),
  candidate_model text check (candidate_model is null or char_length(candidate_model) <= 200),
  research_error text check (research_error is null or char_length(research_error) <= 500),
  candidate_at timestamptz,
  verification_attempts integer not null default 0,
  verification_error text check (verification_error is null or char_length(verification_error) <= 500),
  last_verification_attempt_at timestamptz,
  constraint h_runtime_knowledge_gaps_user_key_query_key_key unique (user_key, query_key)
);

create table if not exists public.h_runtime_verified_knowledge (
  id uuid primary key default gen_random_uuid(),
  user_key text not null check (char_length(user_key) between 1 and 256),
  query_key text not null check (query_key ~ '^[0-9a-f]{64}$'),
  query_text text not null check (char_length(query_text) between 1 and 600),
  answer_text text not null check (char_length(answer_text) between 1 and 3000),
  source_gap_id uuid references public.h_runtime_knowledge_gaps(id) on delete set null,
  verification_method text not null default 'independent_research_v1' check (verification_method = 'independent_research_v1'),
  verification_model text check (verification_model is null or char_length(verification_model) <= 200),
  verified_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  last_used_at timestamptz,
  use_count bigint not null default 0 check (use_count >= 0),
  constraint h_runtime_verified_knowledge_user_key_query_key_key unique (user_key, query_key)
);

alter table public.h_runtime_state enable row level security;
alter table public.h_runtime_config enable row level security;
alter table public.h_runtime_inbox enable row level security;
alter table public.h_runtime_memories enable row level security;
alter table public.h_runtime_contacts enable row level security;
alter table public.h_runtime_tasks enable row level security;
alter table public.h_runtime_reminders enable row level security;
alter table public.h_runtime_learning_state enable row level security;
alter table public.h_runtime_knowledge_gaps enable row level security;
alter table public.h_runtime_verified_knowledge enable row level security;

revoke all on table public.h_runtime_state from public, anon, authenticated;
revoke all on table public.h_runtime_config from public, anon, authenticated;
revoke all on table public.h_runtime_inbox from public, anon, authenticated;
revoke all on table public.h_runtime_memories from public, anon, authenticated;
revoke all on table public.h_runtime_contacts from public, anon, authenticated;
revoke all on table public.h_runtime_tasks from public, anon, authenticated;
revoke all on table public.h_runtime_reminders from public, anon, authenticated;
revoke all on table public.h_runtime_learning_state from public, anon, authenticated;
revoke all on table public.h_runtime_knowledge_gaps from public, anon, authenticated;
revoke all on table public.h_runtime_verified_knowledge from public, anon, authenticated;

grant all on table public.h_runtime_state to service_role;
grant all on table public.h_runtime_config to service_role;
grant all on table public.h_runtime_inbox to service_role;
grant all on table public.h_runtime_memories to service_role;
grant all on table public.h_runtime_contacts to service_role;
grant all on table public.h_runtime_tasks to service_role;
grant all on table public.h_runtime_reminders to service_role;
grant all on table public.h_runtime_learning_state to service_role;
grant all on table public.h_runtime_knowledge_gaps to service_role;
grant all on table public.h_runtime_verified_knowledge to service_role;
grant usage, select, update on sequence public.h_runtime_tasks_id_seq to service_role;

comment on table public.h_runtime_state is 'Standby-local runtime state. Standby-only base schema; not a primary migration.';
comment on table public.h_runtime_memories is 'H standby mirror data. Written by exact_mirror_v1 only while standby replica writes are enabled.';
