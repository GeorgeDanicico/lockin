-- Lock In schema. Safe to run more than once.
-- Run by hand in the Supabase SQL Editor. Never through `supabase db push` or migrations.

create schema if not exists lockin;

-- My goals: one row per thing I track, with its weekly target.
-- An area I stop doing is set inactive, not deleted, so its past entries keep their area.
create table if not exists lockin.areas (
  id            uuid primary key default gen_random_uuid(),
  slug          text not null unique check (slug ~ '^[a-z][a-z0-9-]*$' and slug <> 'other'),
  name          text not null,
  unit          text not null check (unit in ('minutes', 'count', 'sessions', 'days')),
  weekly_target numeric not null check (weekly_target > 0),
  weight        numeric not null default 1 check (weight > 0),   -- counts more in the overall score
  rubric        text not null,          -- what 10/10 looks like, and what lowers the score
  is_active     boolean not null default true,
  created_at    timestamptz not null default now()
);

-- What I said, word for word. Several notes per day are allowed.
create table if not exists lockin.daily_notes (
  id         uuid primary key default gen_random_uuid(),
  day        date not null,             -- my local day, set by the CLI, never now()
  raw_text   text not null check (length(raw_text) between 1 and 4000),
  created_at timestamptz not null default now()
);
create index if not exists daily_notes_day_idx on lockin.daily_notes (day);

-- What Claude turned the note into. The day comes from the note.
create table if not exists lockin.entries (
  id          uuid primary key default gen_random_uuid(),
  note_id     uuid not null references lockin.daily_notes (id) on delete cascade,
  area_id     uuid references lockin.areas (id) on delete set null,   -- null = "other"
  description text not null,
  quantity    numeric check (quantity > 0),   -- in the area's unit; only for minutes/count
  created_at  timestamptz not null default now()
);
create index if not exists entries_note_id_idx on lockin.entries (note_id);
create index if not exists entries_area_id_idx on lockin.entries (area_id);

-- One review per week. Saving again replaces it.
create table if not exists lockin.reviews (
  id          uuid primary key default gen_random_uuid(),
  week_start  date not null unique check (extract(isodow from week_start) = 1),   -- a Monday
  score       numeric(3, 1) not null check (score between 0 and 10),   -- weighted average of the areas
  area_scores jsonb not null check (jsonb_typeof(area_scores) = 'array'),
              -- [{ "slug", "actual", "target", "score", "comment" }]
              -- keeps each area's target as it was that week, so changing a target later
              -- doesn't change what past reviews meant
  summary     text not null check (length(summary) between 1 and 1500),   -- what the iPhone gets
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
