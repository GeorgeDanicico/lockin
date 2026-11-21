-- lockin_reader may see which days have a note, for the Shortcut's 01:00 "did I log yesterday?"
-- check (lockin-latest?check=yesterday). Safe to run more than once.
grant select on lockin.daily_notes to lockin_reader;
