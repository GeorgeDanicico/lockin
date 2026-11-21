#!/usr/bin/env bash
# Run every Monday at 05:00 by lockin-review.timer (installed by ops/install.sh as
# <prefix>/lib/lockin/weekly-review.sh). Grades the week that ended yesterday with Claude Code and the
# lockin-weekly-review skill. Claude can run nothing but the lockin CLI: Bash is its only tool, only
# `lockin …` commands are allowed, and anything else is denied instead of waiting for an answer.
set -euo pipefail
log_dir="${LOCKIN_LOG_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/lockin}"
mkdir -p "$log_dir"
log="$log_dir/review-$(date +%F).log"
find "$log_dir" -name 'review-*.log' -mtime +180 -delete

cd "$HOME"
echo "== $(date -Is) weekly review" >> "$log"
claude -p "/lockin-weekly-review Grade the week that ended yesterday." \
  --tools Bash \
  --allowedTools "Bash(lockin *)" \
  --permission-mode dontAsk \
  >> "$log" 2>&1
