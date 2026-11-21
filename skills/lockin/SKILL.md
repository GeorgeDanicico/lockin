---
name: lockin
description: Logs my Lock In day and manages my Lock In goals (areas) through the lockin CLI. Use when I say what I did today ("today I did…", "log my day", "lock in", a list of things I did), when I want to see or fix what was logged ("what did I log today", "delete that entry"), and when I want to add, change, pause, bring back or drop a goal ("add a goal", "change my reading target", "I'm stopping Spanish", "pause the gym", "what goals are inactive").
---

# Lock In: daily log and goals

`lockin` is a CLI on this machine that reads and writes my Lock In database. Every command prints
JSON on stdout. On an error it prints `{"error": "…", "details": …}` on stderr and exits with 2
(bad call or invalid input: fix it and try again) or 1 (failure: show me the error and stop).

Commands that take JSON read it from stdin. Pass it with a quoted here-document so nothing in it is
expanded:

```bash
lockin log <<'JSON'
{ … }
JSON
```

Never write SQL and never edit the database another way: `lockin` is the only way in.

## The model

- **Areas** are my goals. Each has a `slug`, a `name`, a `unit`, a `weekly_target`, a `weight` (how
  much it counts in the weekly score) and a `rubric` (what 10/10 looks like). The unit decides how a
  week's actual is counted:
  - `minutes`: the sum of the entries' quantities, in minutes.
  - `count`: the sum of the entries' quantities (pages, workouts, …).
  - `sessions`: the number of entries. Each entry is one session.
  - `days`: the number of different days with at least one entry.
- A **note** is what I said, word for word. A day can have several notes.
- An **entry** is one thing I did, taken from a note: an area (or none, "other"), a short
  description, and a quantity only for `minutes` and `count` areas.
- An **inactive** area is paused or dropped: it takes no entries and is not scored.

## Logging my day

1. `lockin areas show` to get the active areas, their units and slugs.
2. Work out the day in my timezone: `TZ=Europe/Bucharest date '+%F %H:%M %A'`. Before 04:00, log
   it for the previous day and say so. If I name a day ("yesterday", "on Thursday"), use that day.
3. Split my message into entries, each mapped to one active area slug, or `"other"` when nothing
   fits. Never invent an area and never stretch one to fit: if something looks like it deserves an
   area of its own, log it as other and say so.
4. Save it:

   ```bash
   lockin log <<'JSON'
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
   JSON
   ```

5. Show a short table of what was saved (area, description, quantity), from the command's output.

Rules:

- **`note` is my message word for word**: no fixing typos, no summarising, no leaving parts out. If
  it is over 4,000 characters, split it at sentence boundaries into several `lockin log` calls for
  the same day, each with the entries from its part.
- **Quantities only when I stated them.** "A few articles" gets no quantity. Converting what I said
  is fine ("an hour" is 60 minutes, "half an hour" is 30). Never estimate.
- **Quantities only for `minutes` and `count` areas.** Entries for `sessions` and `days` areas, and
  for other, take none.
- For a `sessions` area each entry counts as one session, so one session is one entry: don't split
  it into several. For a `days` area several entries on one day still count once.
- A note with nothing to map is still saved, with `"entries": []`.
- No grading, no scores and no pep talk in the daily log. That is the weekly review's job.
- A day may not be in the future, and a day more than 7 days ago needs `--force`
  (`lockin log --force`). Ask me before using `--force`.

## Seeing and fixing what was logged

- `lockin day` shows today's notes with their entries and ids; `lockin day --day=2026-11-18` shows
  another day. Shape: `[{ "id", "day", "text", "entries": [{ "id", "area", "description", "quantity" }] }]`.
- There is no edit. To fix something, delete it and log it again:
  - `lockin entry delete --id=<entry id>` deletes one entry.
  - `lockin note delete --id=<note id>` deletes a note **and all its entries**.
- An entry can't be added to an existing note: every `lockin log` saves a new note. So:
  - an entry that is wrong or shouldn't be there: delete just that entry;
  - an entry that is missing, or needs another area or quantity: delete the whole note and log it
    again, same text, with the corrected entries. Logging it again without deleting would save the
    note twice.
- Before deleting, show me what will go and wait for my yes, unless I named exactly that entry or
  note.

## Goals

- `lockin areas show` lists the active areas; `lockin areas show --inactive` lists the inactive
  ones, most recently deactivated first, each with `deactivated_on` (null if it was deactivated
  before that date was kept).
- **Adding or changing a goal:** propose the new or changed areas (slug, name, unit, weekly target,
  weight, rubric) and wait for my yes: they decide how my weeks are graded. Then save them, one or
  several at once. Saving a slug that exists replaces that area (and makes it active again):

  ```bash
  lockin areas save <<'JSON'
  [
    { "slug": "reading", "name": "Reading", "unit": "minutes", "weekly_target": 150, "weight": 1,
      "rubric": "10: 150 minutes or more, spread over at least 4 days. Lower in proportion to the minutes missed, and by 1 if it was all on one or two days." }
  ]
  JSON
  ```

  A slug is lowercase letters, digits and dashes, starts with a letter, and is never `other`.
  `weight` is optional (default 1). Every field is required on every save, so to change one field,
  start from `lockin areas show` and send the area back whole.
- **A rubric** says what 10 looks like and how the score drops, in terms the weekly review can apply
  to the week's numbers and entries. Keep it to one or two sentences.
- **A goal that isn't weekly** ("call my parents every two weeks"): a fractional `weekly_target` (0.5
  for every two weeks) and a rubric that also counts last week, since the review gets last week's
  actuals: "10 if at least 1 call this week or last week (last week's actual is in the recent
  reviews). 0 if neither." Without that, the score swings 10, 0, 10, 0.
- **Pausing or dropping a goal:** `lockin areas remove <slug>...`. It is never deleted, so its past
  entries keep it. **Bringing it back:** `lockin areas activate <slug>...`, with its target, weight
  and rubric unchanged.
- **Timing a pause:** the weekly review runs on Monday at 05:00 and only sees whether an area is
  active at that moment. So deactivate it before the week I'll skip, and activate it again after
  that week's Monday review. Say this when I ask to pause or resume something.
- **Cleanup:** when I ask about my goals, mention areas inactive for more than a month and ask
  whether they are still goals. Never delete an area; one that is no longer a goal stays inactive.
