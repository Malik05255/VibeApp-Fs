CREATE TABLE IF NOT EXISTS contacts (
  wa_id TEXT PRIMARY KEY,
  profile_name TEXT,
  last_inbound_at INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS inbound_messages (
  message_id TEXT PRIMARY KEY,
  wa_id TEXT NOT NULL,
  body TEXT,
  received_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS scheduled_jobs (
  id TEXT PRIMARY KEY,
  owner_wa_id TEXT NOT NULL,
  target_wa_id TEXT NOT NULL,
  body TEXT NOT NULL,
  due_at INTEGER NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING',
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_scheduled_jobs_due
  ON scheduled_jobs(status, due_at);

CREATE INDEX IF NOT EXISTS idx_scheduled_jobs_owner
  ON scheduled_jobs(owner_wa_id, status, due_at);

CREATE TABLE IF NOT EXISTS named_contacts (
  owner_wa_id TEXT NOT NULL,
  name_key TEXT NOT NULL,
  display_name TEXT NOT NULL,
  target_wa_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(owner_wa_id, name_key)
);

CREATE TABLE IF NOT EXISTS conversation_messages (
  id TEXT PRIMARY KEY,
  owner_wa_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK(role IN ('user', 'assistant')),
  body TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_owner
  ON conversation_messages(owner_wa_id, created_at DESC);

CREATE TABLE IF NOT EXISTS memory_items (
  id TEXT PRIMARY KEY,
  owner_wa_id TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'note',
  body TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_memory_items_owner
  ON memory_items(owner_wa_id, created_at DESC);
