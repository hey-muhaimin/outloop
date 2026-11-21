#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_GRADLE="$ROOT_DIR/app/build.gradle.kts"

resolve_gradle() {
  if command -v gradle >/dev/null 2>&1; then
    echo "gradle"
  elif [[ -x "$ROOT_DIR/gradlew" ]]; then
    echo "$ROOT_DIR/gradlew"
  else
    echo "Error: Neither gradle nor ./gradlew was found on PATH." >&2
    exit 1
  fi
}

read_version_code() {
  grep -E 'versionCode\s*=\s*[0-9]+' "$APP_GRADLE" | head -n1 | sed -E 's/[^0-9]//g'
}

read_version_name() {
  grep -E 'versionName\s*=\s*"[^"]+"' "$APP_GRADLE" | head -n1 | sed -E 's/.*"([^"]+)".*/\1/'
}

compute_sha256() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | awk '{print $1}'
  else
    echo "unavailable"
  fi
}
