# Lock In

A personal tracker for a Lock In plan: log the day to Claude every evening, get a graded weekly
review every Monday, read out loud by an iPhone Shortcut.

![How Lock In fits together: me and Claude Code on the VPS, the lockin CLI and Supabase Postgres, and the iPhone Shortcuts calling the lockin-latest Edge Function](docs/architecture.svg)

- **Every evening** I tell Claude what I did, in the Claude app connected by Remote Control to Claude
  Code on the VPS. The `lockin` skill saves it with the `lockin` CLI, which writes to the `lockin`
  schema in Supabase as `lockin_writer` through the session pooler.
- **Every Monday at 05:00** a systemd user timer runs `claude -p` with the `lockin-weekly-review`
  skill. It reads the week with `lockin week` and saves the graded review with `lockin review save`.
- **iPhone Shortcuts** call the `lockin-latest` Edge Function with the `x-lockin-key` header, which
  reads as the read-only `lockin_reader`: daily at 01:00 (`?check=yesterday`, a reminder if
  yesterday wasn't logged) and on Monday at 07:30 (the weekly review, shown or spoken).

See [docs/plan.md](docs/plan.md) for the design and build order.
