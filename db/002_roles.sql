-- Lock In roles and grants. Safe to run more than once. No passwords in this file:
-- set them by hand in the SQL Editor afterwards (see docs/plan.md, step 1).

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
