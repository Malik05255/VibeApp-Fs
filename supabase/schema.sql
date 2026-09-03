create table if not exists users (
  id uuid primary key default gen_random_uuid(),
  google_id text unique not null,
  email text unique not null,
  display_name text,
  photo_url text,
  created_at timestamp with time zone default now(),
  last_login_at timestamp with time zone default now()
);

create table if not exists projects (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id) on delete cascade,
  title text not null,
  data jsonb,
  images jsonb,
  created_at timestamp with time zone default now(),
  updated_at timestamp with time zone default now()
);

alter table projects enable row level security;
