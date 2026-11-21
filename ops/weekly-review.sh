#!/usr/bin/env bash
# Run every Monday at 05:00 by lockin-review.timer (installed by ops/install.sh as
# <prefix>/lib/lockin/weekly-review.sh). Grades the last completed week with Claude Code and the
# lockin-weekly-review skill. Claude can run nothing but the lockin CLI: Bash is its only tool, only
# `lockin …` commands are allowed, and anything else is denied instead of waiting for an answer.
set -euo pipefail
log_dir="${LOCKIN_LOG_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/lockin}"
mkdir -p "$log_dir"
log="$log_dir/review-$(date +%F).log"
find "$log_dir" -name 'review-*.log' -mtime +180 -delete
out="$(mktemp)"
trap 'rm -f -- "$out"' EXIT

cd "$HOME"
echo "== $(date +%FT%T%z) weekly review ($(claude --version 2>&1))" >> "$log"
status=0
# `lockin:*` rather than `lockin *`: older Claude Code versions only know the first form.
claude -p "/lockin-weekly-review Grade the last completed week." \
  --tools Bash \
  --allowedTools "Bash(lockin:*)" \
  --permission-mode dontAsk \
  --output-format json \
  > "$out" 2>> "$log" || status=$?

# claude exits 0 even when it was blocked and saved nothing. Log its answer and every denied
# command, and fail the service in that case so systemctl status shows it.
python3 - "$out" >> "$log" 2>&1 <<'PY' || status=1
import json, sys
raw = open(sys.argv[1]).read()
try:
    d = json.loads(raw)
except ValueError:
    print(raw)
    sys.exit(1)
print(d.get("result", ""))
denied = d.get("permission_denials") or []
for x in denied:
    print("DENIED:", x.get("tool_name"), json.dumps(x.get("tool_input")))
sys.exit(1 if d.get("is_error") or denied else 0)
PY
exit "$status"
