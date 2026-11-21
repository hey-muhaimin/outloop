#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [--bump major|minor|patch] [--mode debug|release|all] [--skip-tests]

Validates and syncs bundled ruleset.json, runs verification tests, optionally bumps
the app version, builds APK/AAB artifacts, and copies outputs into ./build-output/
with SHA-256 checksums and RELEASE_INFO.txt.
EOF
}

BUMP=""
MODE="debug"
SKIP_TESTS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bump) BUMP="${2:?Missing bump kind}"; shift 2 ;;
    --mode) MODE="${2:?Missing build mode}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

"$SCRIPT_DIR/sync-ruleset.sh" --validate-only

if [[ -n "$BUMP" ]]; then
  "$SCRIPT_DIR/bump-version.sh" "--${BUMP}"
fi

GRADLE="$(resolve_gradle)"

if [[ "$SKIP_TESTS" == false ]]; then
  echo "Running JVM & Robolectric unit tests..."
  (cd "$ROOT_DIR" && "$GRADLE" :app:testDebugUnitTest)
fi

case "$MODE" in
  debug)
    "$SCRIPT_DIR/build-debug.sh"
    ;;
  release)
    "$SCRIPT_DIR/build-release.sh"
    ;;
  all)
    "$SCRIPT_DIR/build-debug.sh"
    "$SCRIPT_DIR/build-release.sh"
    ;;
  *)
    echo "Invalid mode '$MODE'. Expected debug, release, or all." >&2
    exit 1
    ;;
esac

VERSION_NAME="$(read_version_name)"
VERSION_CODE="$(read_version_code)"
BUNDLED_DIR="$ROOT_DIR/build-output"
mkdir -p "$BUNDLED_DIR"

cat > "$BUNDLED_DIR/RELEASE_INFO.txt" <<EOF
App: Outloop
Version Name: $VERSION_NAME
Version Code: $VERSION_CODE
Build Mode: $MODE
Timestamp (UTC): $(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

echo "Packaging complete. Metadata written to $BUNDLED_DIR/RELEASE_INFO.txt"
