# Lock In: implementation plan

A personal tracker for a Lock In plan. Every evening I tell Claude what I did; Claude saves it to a
database. Every Monday morning Claude grades the past week against my goals, and my iPhone shows me the
summary.

It is for one person only, and it is kept apart from the expense tracker (the family hub): its own
repo, its own database schema and its own database logins.

## How it fits together

```
Evening      me ──"today I did…"──▶ Claude (Remote Control session on the VPS)
                                     └─ skill `lockin` ──▶ `lockin log` (native Spring CLI)
                                                            └─ lockin_writer ──▶ Supabase: lockin.*

Monday 05:00 systemd timer ──▶ `claude -p` + skill `lockin-weekly-review`
                                     ├─ `lockin week`        (areas, totals, entries, past reviews)
                                     └─ `lockin review save` (score, area scores, summary)

Monday       iPhone Shortcut ──GET + x-lockin-key──▶ Edge Function `lockin-latest`
                                                       └─ lockin_reader ──▶ lockin.reviews
             ◀── summary ── Show / Speak
```

## Decisions and why

| Question | Decision | Why |
| --- | --- | --- |
| MCP server, or a skill plus a CLI? | **Skill + CLI.** | There is one client (Claude Code on the VPS). An MCP server pays off when several clients (claude.ai, the phone app, ChatGPT) need the same tools. A CLI still gives strict, validated writes, so Claude never writes free-form SQL, and I can run it myself. |
| Where does the data live? | **The existing Supabase project, in its own `lockin` schema.** | No self-hosted Postgres in Docker on the VPS, so no data-loss risk from the VPS. |
| Which credentials does the CLI use? | **A dedicated `lockin_writer` role**, not the service-role key. | The service-role key bypasses row-level security on every table, family expenses included. An unattended Claude must not hold it. `lockin_writer` can touch only the `lockin` schema. |
| How does the CLI connect? | **JDBC through the Supabase session pooler** (port 5432 on the pooler host). | The direct connection is IPv6-only, and the VPS may not have IPv6. The session pooler is the IPv4 way in and behaves like plain Postgres (prepared statements work). The username must be `lockin_writer.<project-ref>`. If the VPS has working IPv6 (`curl -6 https://ifconfig.co`), the direct connection works just as well. |
| Migrations? | **Plain SQL files in this repo (`db/NNN_*.sql`), run once by hand in the Supabase SQL Editor.** | The tables have nothing to do with the family hub, so they stay out of its `supabase/migrations/`. `supabase db push` compares only migration history, not the schema, so tables created by hand don't break it. Never run `supabase db push` from this repo: two repos pushing migrations to one project conflict. |
| Spring Shell, picocli, or plain Spring Boot? | **Plain Spring Boot 4**: an `ApplicationRunner` that dispatches on the first argument. | The step 2 spike failed Spring Shell 4.0.4: in non-interactive mode it couldn't parse `--file -`, exited 0 on parse errors and printed stack traces on others; working around it took more code than the dispatcher. picocli's last release found is 4.7.7 (April 2025). A dozen JSON-in, JSON-out commands need neither. |
| JVM or native? | **GraalVM native image.** | Much less memory, near-instant start, and a one-shot CLI gains nothing from the JIT. The VPS is already busy with the app's containers (768 MB web, 256 MB worker). |
| Scheduler? | **A systemd user timer**, not cron. | Debian/Ubuntu cron ignores `CRON_TZ`; systemd's `OnCalendar` takes a timezone. `Persistent=true` runs a missed review once the VPS is back, where cron would silently skip the week. Claude Code cloud routines are out: they run in Anthropic's cloud without the VPS's credentials. |
| How does the review reach the phone? | **A Supabase Edge Function guarded by a key I generate.** | SSH from Shortcuts can't pass the VPS's 2FA, and a 2FA-exempt user isn't possible here. Supabase Storage would mean an upload credential on the VPS (service-role or S3 keys, both project-wide). The function reads the database with a read-only role, needs no new credential on the VPS, and isn't cached. |
| Which model grades? | Whatever model the Claude Code session uses; prefer the strongest one (Opus) for the weekly run. | One call a week; grading a week against a rubric benefits from the reasoning. Daily logging works with any model. |

## Versions

| Piece | Version |
| --- | --- |
| Java | 25 (LTS) |
| Spring Boot | 4.2.0 |
| GraalVM | 25 or newer (GraalVM Community 25 or Liberica NIK 25). Spring Boot 4 requires GraalVM 25+ for native images. |
| Build | Maven with `spring-boot-starter-parent` (gives the `native` and `nativeTest` profiles) |
| JSON | Jackson 3, through Boot 4's Jackson starter |

## Repo layout

```
lockin/
  cli/                          Spring Boot 4 app (Maven)
  db/
    001_schema.sql
    002_roles.sql
  skills/
    lockin/SKILL.md
    lockin-weekly-review/SKILL.md
  supabase/functions/lockin-latest/index.ts
  ops/
    lockin                      wrapper script → /usr/local/bin/lockin
    weekly-review.sh
    systemd/lockin-review.service
    systemd/lockin-review.timer
  areas.example.json
  docs/plan.md                  this file
  .github/workflows/release.yml
```

---

## Step 0: facts to collect

From the VPS:

- [ ] CPU architecture: `uname -m` (`x86_64` or `aarch64`), picks the CI runner.
- [ ] Linux distribution and version: `cat /etc/os-release`. The CI runner's Ubuntu must not be newer (glibc).
- [ ] Does the Remote Control Claude run on the host or in a container? (Where the binary and the skills go.)
- [ ] Does the VPS have IPv6? `curl -6 https://ifconfig.co` (direct connection vs session pooler).

From me:

- [ ] My timezone (IANA name, for example `Europe/Bucharest`).
- [ ] My Lock In areas, each with a weekly target, a unit and what 10/10 looks like.
- [ ] Which Supabase plan the project is on (backups; see step 9).

From the Supabase dashboard:

- [ ] **Connect → Session pooler** connection string (host and region).
- [ ] Project ref.

## Step 1: database

### `db/001_schema.sql`

Safe to run more than once.

```sql
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
```

The letter grade (A ≥ 9, B ≥ 7.5, C ≥ 6, D ≥ 4, F below) is worked out from `score` when it's shown,
not stored. Unlogged days are worked out from `daily_notes` by `lockin week`, not stored.

How totals are counted, per area unit (done in SQL by `lockin week`, never by Claude):

| Unit | Weekly actual |
| --- | --- |
| `minutes`, `count` | `sum(quantity)` |
| `sessions` | number of entries |
| `days` | number of distinct days with an entry |

### `db/002_roles.sql`

No passwords in this file.

```sql
do $$
begin
  if not exists (select from pg_roles where rolname = 'lockin_writer') then
    create role lockin_writer login noinherit connection limit 3;
  end if;
  if not exists (select from pg_roles where rolname = 'lockin_reader') then
    create role lockin_reader login noinherit connection limit 5;
  end if;
end
$$;

-- Nobody else gets in, whatever the project's default privileges are.
revoke all on schema lockin from public;
revoke all on all tables in schema lockin from public, anon, authenticated;

grant usage on schema lockin to lockin_writer, lockin_reader;
grant select, insert, update, delete on all tables in schema lockin to lockin_writer;
grant select on lockin.reviews to lockin_reader;

-- Tables added later by 003_… and on get the same grants without anyone remembering to.
alter default privileges in schema lockin
  grant select, insert, update, delete on tables to lockin_writer;

alter role lockin_writer set search_path = lockin;
alter role lockin_reader set search_path = lockin;
```

Then, by hand in the SQL Editor (not saved anywhere):

```sql
alter role lockin_writer password '<generated>';
alter role lockin_reader password '<generated>';
```

Generate each with `openssl rand -base64 32`.

**After any restore of the project, these passwords must be set again** (Supabase's daily backups
don't keep custom role passwords; see the expense tracker's `docs/backups.md`).

Rules:

- Don't add `lockin` to **Settings → Data API → Exposed schemas**. The schema is reachable only through
  the two logins, so it needs no row-level security.
- Any later change goes in a new file (`003_….sql`). New tables get `lockin_writer`'s grants
  automatically; `lockin_reader` still needs an explicit grant.

### Done when

- [ ] Both files ran in the SQL Editor; passwords set.
- [ ] Connected as `lockin_writer.<ref>` through the session pooler with `psql`:
      `select * from lockin.areas` works, `select * from public.expenses` gives *permission denied*.
- [ ] As `lockin_reader`: `select` on `lockin.reviews` works; `insert` and `select` on
      `lockin.entries` are denied.

## Step 2: Spring scaffold and spike (go / no-go)

**Result: done.** Spring Shell 4.0.4 failed the checks below (see "Decisions and why"), so the
no-go fallback was taken. Native `lockin ping`: about 20 ms, 47 MB max RSS (macOS, Apple Silicon).

Dependencies: `spring-boot-starter-jdbc` (HikariCP excluded), `spring-boot-starter-jackson`,
`postgresql`; for tests `spring-boot-starter-jdbc-test`, `spring-boot-testcontainers`,
`testcontainers-postgresql`. Native build: `native-maven-plugin` from the Boot parent's `native`
profile, image name `lockin`.

`application.yaml`:

```yaml
spring:
  main:
    banner-mode: off   # the banner goes to stdout, which carries only JSON
  datasource:
    url: ${LOCKIN_DB_URL}
    username: ${LOCKIN_DB_USER}
    password: ${LOCKIN_DB_PASSWORD}
    type: org.springframework.jdbc.datasource.SimpleDriverDataSource   # no pool for a one-shot CLI
```

`lockin.zone` (`LOCKIN_TZ`) and `lockin.max-backfill-days` (7) are added in step 3 with the first
command that needs them.

- Logs go to **stderr only** (`logback-spring.xml`, level WARN). **stdout carries only command
  output**, because Claude reads it.
- Exit codes: 0 ok, 2 bad call (unknown command, invalid input), 1 failure (database, startup).
- Connection URL:
  `jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require`
  with user `lockin_writer.<project-ref>`.

Spike: one command, `lockin ping`, which runs `select 1` and prints `{"ok":true}`. Build it native
and check:

- [x] Output appears on stdout, and nothing else does.
- [x] A command can read JSON from stdin: `lockin areas save` (step 3).
- [x] The exit code is non-zero on an error.
- [x] Start time and memory (`/usr/bin/time -l` on macOS, `-v` on Linux).

**No-go fallback (taken):** keep Boot 4 native but drop Spring Shell and use an
`ApplicationRunner` with a small command dispatcher. Nothing else in the plan changes.

## Step 3: CLI commands

Every command prints JSON on stdout. Errors print JSON on stderr, `{"error":"…","details":…}`,
with a non-zero exit code.

| Command | Does |
| --- | --- |
| `lockin areas show` | Active areas with units, targets, weights, rubrics, sorted by slug. **Done.** |
| `lockin areas save` | Reads an array of one or more `{ "slug", "name", "unit", "weekly_target", "weight"?, "rubric" }` from stdin (weight defaults to 1). In one transaction, creates each area or updates the one with the same slug, making it active again; other areas aren't touched. Rejects an empty list, a repeated slug, a bad slug, an unknown unit, a target or weight ≤ 0, a blank name or rubric, and unknown fields. Prints the saved areas. **Done.** |
| `lockin areas remove <slug>...` | Sets one or more areas inactive (never deleted, so their entries keep their area). Every slug must be an active area, or nothing changes. Prints the removed slugs. **Done.** |
| `lockin log [--force]` | Reads from stdin `{ "day", "note", "entries"?: [{ "area"?, "description", "quantity"? }] }` and saves the note and entries in one transaction. `area` omitted, null or `"other"` = no area. Rejects an area that isn't active (the error lists the active ones), a `quantity` on `sessions`/`days` areas or on "other", a quantity ≤ 0, a blank description, a note over 4,000 characters, future days, and days older than `max-backfill-days` unless `--force`. Prints the saved note: `{ "id", "day", "text", "entries": [{ "id", "area", "description", "quantity" }] }`. **Done.** |
| `lockin day [--day YYYY-MM-DD]` | The day's notes (default today), oldest first, in the same shape. For confirming and correcting. **Done.** |
| `lockin entry delete --id <uuid>` | Deletes one entry; prints it. **Done.** |
| `lockin note delete --id <uuid>` | Deletes a note and its entries; prints them. **Done.** |
| `lockin week [--week-start YYYY-MM-DD]` | Everything the review needs in one call: `week_start`, `week_end`, active `areas` each with its `actual` **computed in SQL**, `days` (each with its note texts and entries), `unlogged_days`, and `recent_reviews` (the last 4 before this week). Default: the last completed Monday–Sunday week in `lockin.zone`; `--week-start` must be a Monday. **Done.** |
| `lockin review save` | Reads from stdin `{ "week_start", "score", "area_scores": [{ "slug", "actual", "target", "score", "comment" }], "summary" }`. Checks: `week_start` is a Monday of a completed week; every active area scored exactly once; scores 0–10; `score` has at most one decimal and is within 0.1 of the weighted average of the area scores; `summary` 1–1,500 characters. Replaces an existing review of that week. Prints the saved review. **Done.** |
| `lockin reviews [--last N]` | The last N reviews (default 1, at most 52), newest first: `{ "week_start", "score", "grade", "area_scores", "summary" }`, with the grade worked out from the score. **Done.** |

Example `log` input:

```json
{
  "day": "2026-11-20",
  "note": "Today i did the following: read 10 minutes, did my duolingo in spanish, read a few articles, spent time with my wife",
  "entries": [
    { "area": "reading",      "description": "Read a book", "quantity": 10 },
    { "area": "spanish",      "description": "Duolingo lesson" },
    { "area": "reading",      "description": "Read a few articles" },
    { "area": "relationship", "description": "Spent time with my wife" }
  ]
}
```

(The articles entry has no quantity: no minutes were stated.)

### Code layout (`cli/src/main/java/…/lockin/`)

Boundary-control-entity: one package per business component, each split into
`boundary` (the commands: one class per command, implementing `cli.boundary.Command` and returning
the result to print), `control` (logic and SQL, only when there is some: a one-query command uses
`JdbcClient` directly) and `entity` (records). A subpackage exists only once it has a class.

| Component | `boundary` | `control` | `entity` |
| --- | --- | --- | --- |
| `cli` | `Command`, `CommandLine` (dispatch, JSON on stdout, errors on stderr, exit codes), `Input` (reads JSON from stdin) | | |
| `ping` | `Ping` | | |
| `areas` | `AreasShow`, `AreasSave`, `AreasRemove` | `AreaRepository` (`JdbcClient`) | `Area` (checks its own rules), `Unit` |
| `log` | `Log`, `Day`, `EntryDelete`, `NoteDelete` | `LogRepository` (log rules, SQL) | `LogRequest`, `Note`, `Entry` |
| `review` | `Week`, `ReviewSave`, `Reviews` | `WeekRepository` (weekly totals in SQL), `ReviewRepository` (review rules, SQL) | `WeekReport`, `AreaTotal`, `LoggedDay`, `LoggedEntry`, `Review`, `AreaScore` |

- JSON is snake_case (`weekly_target`), like the database, and unknown fields are rejected.
- Invalid input (`IllegalArgumentException`, or a JSON error) exits 2 with
  `{"error":"invalid_input","details":"…"}`; any other failure exits 1 with `command_failed`.
- SQL names the schema (`lockin.areas`), so it works for any login, not only through
  `lockin_writer`'s `search_path`.

### Native-image rules

- Jackson reads records by reflection, which a native image allows only for registered types.
  **Register every input and output record** with `@RegisterReflectionForBinding`.
- No other reflection-heavy libraries.
- If a hint is missing, run the GraalVM tracing agent once on the JVM build to find it.

### Tests

- Unit tests for the validation rules (slugs, quantities, days, review checks).
- Integration tests with **Testcontainers Postgres**, running `db/*.sql` first, for every command.
- The same tests run again as a native build in CI (`./mvnw -PnativeTest test`), to catch missing
  hints before a release.

### Done when

- [x] All commands work on the JVM against the Testcontainers database (37 tests).
- [ ] `./mvnw -PnativeTest test` passes.

## Step 4: native build and deploy

- Develop and run tests on the JVM; build native only in CI.
- **Build in GitHub Actions**, not on the VPS or the Mac: `native-image` needs several GB of RAM
  and builds only for the OS and CPU it runs on.

`.github/workflows/release.yml`, on a `v*` tag:

1. Runner matching the VPS: `ubuntu-24.04` (x86_64) or `ubuntu-24.04-arm` (aarch64). Its Ubuntu must
   not be newer than the VPS's distribution, or the binary may need a newer glibc than the VPS has
   (alternative: build mostly-static).
2. `graalvm/setup-graalvm` with Java 25.
3. `./mvnw -Pnative native:compile` with `-R:MaxHeapSize=64m` in the native build arguments.
4. Smoke test: `./cli/target/lockin --help`.
5. Attach the binary to the GitHub Release.

On the VPS:

- `gh release download vX.Y.Z` → `/usr/local/lib/lockin/lockin-bin`.
- `/usr/local/bin/lockin` (from `ops/lockin`) is a wrapper that loads the credentials and runs the
  binary, so the password is never in Claude's environment or on a command line:

  ```bash
  #!/usr/bin/env bash
  set -a; . "$HOME/.config/lockin/env"; set +a
  exec /usr/local/lib/lockin/lockin-bin "$@"
  ```

- `~/.config/lockin/env`, `chmod 600`:

  ```
  LOCKIN_DB_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
  LOCKIN_DB_USER=lockin_writer.<project-ref>
  LOCKIN_DB_PASSWORD=<generated>
  LOCKIN_TZ=<my timezone>
  ```

### Done when

- [ ] `lockin ping` works on the VPS against the live project.
- [ ] Memory and start time measured on the VPS.

## Step 5: skills

Source in `skills/`, symlinked into `~/.claude/skills/` on the VPS (where the Remote Control Claude
runs).

### `lockin` (daily logging)

- **Triggers:** "today I did…", "log my day", "lock in", listing what I did today.
- **Steps:**
  1. `lockin areas show` to get the valid areas and their units.
  2. Work out the day in my timezone. Before 04:00, assume the previous day and say so.
  3. Split the message into entries, each mapped to an area slug.
  4. `lockin log` with the JSON on stdin.
  5. Show a short table of what was saved.
- **Rules:**
  - Quantities only when I stated them. "A few articles" gets no minutes.
  - Quantities only for `minutes` and `count` areas.
  - Anything that fits no area is saved with no area ("other"). Never invent areas.
  - Save my sentence word for word as the note.
  - No grading and no pep talk in the daily log.
- **Corrections:** `lockin day`, then `lockin entry delete` / `lockin note delete`, then log again.
- **Goals:** "add a goal…", "change my reading target to…", "I'm stopping Spanish":
  1. `lockin areas show`.
  2. Propose the new or changed areas (slug, name, unit, weekly target, weight, rubric) and ask me
     to confirm: they decide how my weeks are graded.
  3. `lockin areas save` with an array of the confirmed areas (one or several), or
     `lockin areas remove <slug>...`.

### `lockin-weekly-review` (unattended)

- **Never asks questions.** It runs at 05:00 with nobody to answer.
- **Steps:**
  1. `lockin week --week-start <last Monday>`.
  2. Score each area 0–10 from actual vs target, using that area's rubric.
  3. Overall score = weighted average of the area scores (weights from the areas).
  4. Write the summary (see below).
  5. `lockin review save` with the JSON on stdin. On a validation error, fix it and retry once.
- **What the summary says**, in this order, in one text:
  - The week's dates and the score with its letter grade ("Week of 16 to 22 November: 7.8, a B.").
  - Where the time actually went.
  - The areas furthest below target.
  - The trend against the last 4 reviews.
  - Unlogged days, if any; they are not counted as zeros, and the summary says they might be days
    I did things but didn't log.
  - Next week: at most 3 concrete actions.
- **Format:** under 200 words, plain sentences, no markdown or emoji, numbers easy to read and to
  hear (it works shown on screen or read out loud).
- **Tone:** honest. No inflated scores.

### Done when

- [ ] A few real days logged through the Remote Control session.
- [ ] The weekly review run by hand once (step 9) and the rubric tuned.

## Step 6: weekly schedule

systemd **user** units in `~/.config/systemd/user/` (from `ops/systemd/`). Run
`loginctl enable-linger <user>` once, so they run without an open login session.

`lockin-review.timer`:

```ini
[Unit]
Description=Weekly Lock In review

[Timer]
OnCalendar=Mon *-*-* 05:00:00 <my timezone>
Persistent=true

[Install]
WantedBy=timers.target
```

`lockin-review.service`:

```ini
[Unit]
Description=Weekly Lock In review

[Service]
Type=oneshot
ExecStart=%h/lockin/ops/weekly-review.sh
```

`ops/weekly-review.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$HOME/lockin/logs"
claude -p "Run the lockin-weekly-review skill for the week that ended yesterday." \
  --allowedTools "Bash(lockin *)" \
  >> "$HOME/lockin/logs/review-$(date +%F).log" 2>&1
```

- `--allowedTools` limits the unattended run to the `lockin` CLI. Check the exact pattern syntax
  against the installed `claude --help`.
- It uses the Claude login already on the VPS.

### Done when

- [ ] `systemctl --user start lockin-review.service` produces a review.
- [ ] `systemctl --user list-timers` shows the next Monday run.

## Step 7: Edge Function `lockin-latest`

`supabase/functions/lockin-latest/index.ts` (Deno), sketch:

```ts
import postgres from "npm:postgres@<pinned>";

const key = Deno.env.get("LOCKIN_READ_KEY")!;
const zone = Deno.env.get("LOCKIN_TZ")!;
const sql = postgres(Deno.env.get("LOCKIN_READER_DB_URL")!, { prepare: false, max: 1 });

async function sameKey(given: string): Promise<boolean> {
  const enc = new TextEncoder();
  const [a, b] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(given)),
    crypto.subtle.digest("SHA-256", enc.encode(key)),
  ]);
  const x = new Uint8Array(a), y = new Uint8Array(b);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}

Deno.serve(async (req) => {
  if (req.method !== "GET" || !(await sameKey(req.headers.get("x-lockin-key") ?? ""))) {
    return new Response("Not found", { status: 404 });
  }
  const [row] = await sql`
    select week_start, summary,
           week_start = (date_trunc('week', (now() at time zone ${zone})::date) - interval '7 days')::date
             as is_last_week
    from lockin.reviews
    order by week_start desc
    limit 1`;
  const text = !row
    ? "There is no Lock In review yet."
    : row.is_last_week
      ? row.summary
      : `No review for last week yet. The latest one is from the week of ${row.week_start}. ${row.summary}`;
  return new Response(text, {
    headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
  });
});
```

- Wrong or missing key → 404, so a scanner can't tell the function exists.
- The key goes in a header, never in the URL.
- Reads through the **transaction pooler** (port 6543) as `lockin_reader.<project-ref>`, with
  `prepare: false` (the transaction pooler doesn't support prepared statements).
- Each request reads the database, so a stale review is never served from a cache.

Deploy and configure:

```bash
supabase functions deploy lockin-latest --project-ref <ref> --no-verify-jwt
supabase secrets set --project-ref <ref> \
  LOCKIN_READ_KEY="$(openssl rand -base64 32)" \
  LOCKIN_READER_DB_URL="postgres://lockin_reader.<ref>:<password>@aws-0-<region>.pooler.supabase.com:6543/postgres?sslmode=require" \
  LOCKIN_TZ="<my timezone>"
```

(Save the generated key before setting it; the Shortcut needs it.) Deploying a function doesn't
touch the project's migration history. Rotating the key: set a new secret, edit the Shortcut.

### Done when

- [ ] `curl` without the header → 404.
- [ ] `curl -H "x-lockin-key: …"` → the latest summary.

## Step 8: iPhone Shortcut

1. **Get Contents of URL**: `https://<ref>.supabase.co/functions/v1/lockin-latest`, method GET,
   header `x-lockin-key: <key>`.
2. **Show Notification** (or **Speak Text**) with the result.

Trigger: a personal automation, for example **When my alarm is stopped**, on Mondays. Works over
mobile data; no Tailscale or SSH needed.

The key lives in the Shortcut, which syncs through iCloud. Anyone who gets it can hear the latest
weekly summary, nothing more, and the key can be rotated in a minute.

## Step 9: first week

- [ ] Set up my real goals through the Remote Control session (or by hand:
      `lockin areas save < areas.json`, starting from `areas.example.json`).
- [ ] Log a few real days.
- [ ] Run the weekly review by hand in the Remote Control session; tune the rubrics and the
      summary until they read right.
- [ ] Enable the timer (step 6).
- [ ] Backups: fill in the table in the expense tracker's `docs/backups.md`. If the project is on the
      Free plan (no automatic backups), add a weekly `supabase db dump --schema lockin` to the timer
      and copy the dump off the VPS.

## Build order

1. Database (step 1)
2. Spike: go / no-go (step 2)
3. Commands and tests (step 3)
4. CI native build and VPS install (step 4)
5. Skills (step 5)
6. First manual review (step 9)
7. Timer (step 6)
8. Edge Function (step 7)
9. Shortcut (step 8)

## Sources

- [Spring Boot 4.0 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)
- [Spring Boot 4.1 system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
- [Spring Boot 4.2 native image: first application](https://docs.spring.io/spring-boot/4.2/how-to/native-image/developing-your-first-application.html)
- [Announcing picocli 4.7.7](https://groups.google.com/g/picocli/c/loHcQVSZlfE)
- [Supabase backups](https://supabase.com/docs/guides/platform/backups)
