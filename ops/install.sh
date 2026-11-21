#!/usr/bin/env bash
# Installs the native lockin binary that the Build workflow made for one commit (by default this
# checkout's), the wrapper, and the skills. vps-apps runs it on every deploy; it also works by hand.
#
#   <prefix>/lib/lockin/lockin-bin   the binary (the one before it is kept as lockin-bin.previous)
#   <prefix>/bin/lockin              the wrapper (ops/lockin), which loads the database login
#   <skills dir>/<name>              a symlink to each skills/<name> in this checkout
#   <prefix>/lib/lockin/weekly-review.sh and lockin-review.{service,timer} in the systemd user dir
set -Eeuo pipefail
repo="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
commit="${LOCKIN_COMMIT:-$(git -C "$repo" rev-parse HEAD)}"
prefix="${LOCKIN_PREFIX:-$HOME/.local}"
env_file="${LOCKIN_ENV_FILE:-$HOME/.config/lockin/env}"
skills_dir="${LOCKIN_SKILLS_DIR:-$HOME/.claude/skills}"
units_dir="${LOCKIN_UNITS_DIR:-${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user}"
release_url="${LOCKIN_RELEASE_URL:-https://github.com/GeorgeDanicico/lockin/releases/download/builds}"
lib="$prefix/lib/lockin"
asset="lockin-linux-$(uname -m)-$commit"

# Created once on the VPS by hand (it holds the database password, which is never in the repo).
# Without it every lockin command fails, so the deploy fails too, before anything is replaced.
if [[ ! -r "$env_file" ]]; then
  echo "Missing $env_file: create it once (mode 600) with LOCKIN_DB_URL, LOCKIN_DB_USER," >&2
  echo "LOCKIN_DB_PASSWORD and LOCKIN_TZ. See Step 4 of docs/plan.md." >&2
  exit 1
fi

mkdir -p "$lib" "$prefix/bin"
tmp="$(mktemp -d "$lib/.download.XXXXXX")"
trap 'rm -rf -- "$tmp"' EXIT

download() { curl --fail --silent --show-error --location --retry 3 --output "$2" "$1"; }
# The binary is uploaded a few minutes after the push, once CI and the native build pass. Until
# then this fails, and vps-apps tries again on its next run.
if ! download "$release_url/$asset" "$tmp/lockin-bin" || ! download "$release_url/$asset.sha256" "$tmp/sha256"; then
  echo "No lockin build for $commit yet ($release_url/$asset)." >&2
  echo "It is published when the Build workflow for that commit finishes." >&2
  exit 1
fi
expected="$(cut -d ' ' -f 1 "$tmp/sha256")"
actual="$(sha256sum "$tmp/lockin-bin" | cut -d ' ' -f 1)"
if [[ $actual != "$expected" ]]; then
  echo "Checksum mismatch for $asset: expected $expected, got $actual." >&2
  exit 1
fi
chmod 755 "$tmp/lockin-bin"
bash "$repo/ops/smoke-test.sh" "$tmp/lockin-bin"

if [[ -f "$lib/lockin-bin" ]]; then cp -p "$lib/lockin-bin" "$lib/lockin-bin.previous"; fi
mv -f "$tmp/lockin-bin" "$lib/lockin-bin"
install -m 755 "$repo/ops/lockin" "$prefix/bin/lockin"

# A binary that can't reach the database is put back to the one before it.
if ! LOCKIN_ENV_FILE="$env_file" "$prefix/bin/lockin" ping; then
  if [[ -f "$lib/lockin-bin.previous" ]]; then
    mv -f "$lib/lockin-bin.previous" "$lib/lockin-bin"
    echo "lockin ping failed; restored the previous binary." >&2
  fi
  exit 1
fi

if [[ -d "$repo/skills" ]]; then
  mkdir -p "$skills_dir"
  for skill in "$repo"/skills/*/; do
    link="$skills_dir/$(basename -- "$skill")"
    if [[ -e "$link" && ! -L "$link" ]]; then
      echo "Skipped skill $link: it exists and is not a symlink." >&2
      continue
    fi
    ln -sfn -- "${skill%/}" "$link"
  done
fi

# The weekly review timer. Installing it doesn't enable it: that is done once by hand
# (systemctl --user enable --now lockin-review.timer) and stays enabled across deploys.
install -m 755 "$repo/ops/weekly-review.sh" "$lib/weekly-review.sh"
tz="$(sed -n "s/^LOCKIN_TZ=//p" "$env_file" | tail -n 1 | tr -d "\"' ")"
tz="${tz:-Europe/Bucharest}"
mkdir -p "$units_dir"
sed "s|@prefix@|$prefix|g" "$repo/ops/systemd/lockin-review.service" > "$units_dir/lockin-review.service"
sed "s|@tz@|$tz|g" "$repo/ops/systemd/lockin-review.timer" > "$units_dir/lockin-review.timer"
# A deploy runs outside a login session, so systemctl isn't told where the user manager is. With
# lingering on, it runs all the time and listens in /run/user/<uid>.
runtime_dir="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
if [[ -S "$runtime_dir/systemd/private" ]]; then
  if ! XDG_RUNTIME_DIR="$runtime_dir" systemctl --user daemon-reload; then
    echo "Installed the review timer, but systemctl --user daemon-reload failed." >&2
  fi
else
  echo "Installed the review timer, but the systemd user manager of $(id -un) isn't running." >&2
  echo "Run sudo loginctl enable-linger $(id -un) once; it then loads the units itself." >&2
fi
echo "Installed lockin $commit in $prefix."
