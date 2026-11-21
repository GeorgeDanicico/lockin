// lockin-latest: the latest weekly Lock In review as plain text, for the iPhone Shortcut.
// With ?check=yesterday: nothing (204) if yesterday was logged, else one sentence.
//
// GET with header `x-lockin-key: <LOCKIN_READ_KEY>`. A wrong or missing key, or any other method,
// gets a bare 404. (That doesn't hide the function: the gateway adds `x-deno-execution-id` to
// everything a function returns. The key is the protection.) Deploy with JWT verification off.
//
// Secrets:
//   LOCKIN_READ_KEY             the key the Shortcut sends (at least 32 characters)
//   LOCKIN_READER_DB_PASSWORD   lockin_reader's password
//   LOCKIN_DB_HOST              the pooler host, e.g. aws-0-<region>.pooler.supabase.com
//   LOCKIN_TZ                   optional, IANA name; defaults to the CLI's Europe/Bucharest
// SUPABASE_URL is set by Supabase; the project ref for the pooler username comes from it.
//
// The password is passed on its own, not inside a connection URL: a base64 password can contain
// "/", "+" and "=", which break URL parsing unless escaped.

import postgres from "npm:postgres@3.4.9";

const key = Deno.env.get("LOCKIN_READ_KEY") ?? "";
const zone = Deno.env.get("LOCKIN_TZ") || "Europe/Bucharest";
const password = Deno.env.get("LOCKIN_READER_DB_PASSWORD") ?? "";
const host = Deno.env.get("LOCKIN_DB_HOST") ?? "";
const ref = new URL(Deno.env.get("SUPABASE_URL") ?? "http://missing").hostname.split(".")[0];

// Without a proper key every request would compare against "", so refuse everything instead.
const configured = key.length >= 32 && password !== "" && host !== "" && ref !== "missing";
if (!configured) {
  console.error(
    "lockin-latest is not configured: set LOCKIN_READ_KEY (32+ characters), " +
      "LOCKIN_READER_DB_PASSWORD and LOCKIN_DB_HOST",
  );
}

// Transaction pooler (6543) as lockin_reader; it doesn't support prepared statements.
const sql = postgres({
  host,
  port: 6543,
  database: "postgres",
  username: `lockin_reader.${ref}`,
  password,
  ssl: "require",
  prepare: false,
  max: 1,
  idle_timeout: 20,
  connect_timeout: 10,
});

const notFound = () => new Response("Not found", { status: 404 });

function text(body: string, status = 200): Response {
  return new Response(body, {
    status,
    headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
  });
}

// Compares SHA-256 digests in constant time, so the response time doesn't leak the key.
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

// ?check=yesterday, for the 01:00 Shortcut: an empty 204 when yesterday (in my zone) has a note,
// so the Shortcut shows nothing; otherwise one sentence to show. At 01:00 "yesterday" is the day
// that just ended. Logging between 00:00 and 04:00 still counts for it (the skill's rule), but
// only if it happens before the check runs.
async function yesterday(): Promise<Response> {
  try {
    const [row] = await sql`
      with d as (select (now() at time zone ${zone})::date - 1 as day)
      select exists (select from lockin.daily_notes n where n.day = d.day) as logged,
             to_char(d.day, 'FMDay, FMDD FMMonth') as day_name
      from d`;
    if (row.logged) return new Response(null, { status: 204, headers: { "cache-control": "no-store" } });
    return text(`You didn't log ${row.day_name}.`);
  } catch (e) {
    console.error("lockin-latest: checking yesterday failed", e);
    return text("The Lock In check for yesterday could not be loaded right now.", 500);
  }
}

Deno.serve(async (req) => {
  if (!configured || req.method !== "GET" || !(await sameKey(req.headers.get("x-lockin-key") ?? ""))) {
    return notFound();
  }
  const check = new URL(req.url).searchParams.get("check");
  if (check === "yesterday") return yesterday();
  if (check !== null) return notFound();
  try {
    // is_last_week: the review is for the Monday-Sunday week before the current one, in my zone.
    // The date is formatted here: postgres.js would hand back a JS Date at UTC midnight.
    const [row] = await sql`
      select summary,
             to_char(week_start, 'FMDD FMMonth YYYY') as week_of,
             week_start = (date_trunc('week', (now() at time zone ${zone})::date) - interval '7 days')::date
               as is_last_week
      from lockin.reviews
      order by week_start desc
      limit 1`;
    if (!row) return text("There is no Lock In review yet.");
    if (row.is_last_week) return text(row.summary);
    return text(
      `No review for last week yet. The latest one is from the week of ${row.week_of}. ${row.summary}`,
    );
  } catch (e) {
    console.error("lockin-latest: reading the review failed", e);
    return text("The Lock In review could not be loaded right now.", 500);
  }
});
