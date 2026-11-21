#!/usr/bin/env bash
# Checks that a native lockin binary starts, without a database: with no command it must exit 2
# with a "no_command" error. Used by the Build workflow and by ops/install.sh.
set -uo pipefail
binary="${1:?usage: smoke-test.sh <binary>}"
err="$(env LOCKIN_DB_URL=jdbc:postgresql://127.0.0.1:1/none LOCKIN_DB_USER=none LOCKIN_DB_PASSWORD=none \
  "$binary" 2>&1 >/dev/null)"
status=$?
if [[ $status -ne 2 || $err != *no_command* ]]; then
  echo "Smoke test failed for $binary (exit $status):" >&2
  echo "$err" >&2
  exit 1
fi
