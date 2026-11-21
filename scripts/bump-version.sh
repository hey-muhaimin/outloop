#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

usage() {
  cat <<EOF
Usage: $(basename "$0") [--major | --minor | --patch | --version X.Y.Z] [--code N] [--tag]

Bumps versionCode and versionName in app/build.gradle.kts.

Options:
  --major           Increment major version (X+1.0.0) and versionCode
  --minor           Increment minor version (X.Y+1.0) and versionCode
  --patch           Increment patch version (X.Y.Z+1) and versionCode (default)
  --version X.Y.Z   Set explicit versionName (e.g. 1.2.0)
  --code N          Set explicit versionCode instead of +1
  --tag             Create an annotated git tag v<versionName>
  -h, --help        Show this help message
EOF
}

BUMP_TYPE="patch"
EXPLICIT_VERSION=""
EXPLICIT_CODE=""
CREATE_TAG=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --major) BUMP_TYPE="major"; shift ;;
    --minor) BUMP_TYPE="minor"; shift ;;
    --patch) BUMP_TYPE="patch"; shift ;;
    --version)
      EXPLICIT_VERSION="${2:?Missing value for --version}"
      shift 2
      ;;
    --code)
      EXPLICIT_CODE="${2:?Missing value for --code}"
      shift 2
      ;;
    --tag) CREATE_TAG=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

CURRENT_CODE="$(read_version_code)"
CURRENT_NAME="$(read_version_name)"

if [[ -z "$CURRENT_CODE" || -z "$CURRENT_NAME" ]]; then
  echo "Error: Could not read versionCode/versionName from $APP_GRADLE" >&2
  exit 1
fi

if [[ -n "$EXPLICIT_CODE" ]]; then
  NEW_CODE="$EXPLICIT_CODE"
else
  NEW_CODE="$((CURRENT_CODE + 1))"
fi

if [[ -n "$EXPLICIT_VERSION" ]]; then
  NEW_NAME="$EXPLICIT_VERSION"
else
  IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"
  MAJOR="${MAJOR:-1}"
  MINOR="${MINOR:-0}"
  PATCH="${PATCH:-0}"
  case "$BUMP_TYPE" in
    major) NEW_NAME="$((MAJOR + 1)).0.0" ;;
    minor) NEW_NAME="${MAJOR}.$((MINOR + 1)).0" ;;
    patch) NEW_NAME="${MAJOR}.${MINOR}.$((PATCH + 1))" ;;
  esac
fi

python3 - "$APP_GRADLE" "$NEW_CODE" "$NEW_NAME" <<'PYEOF'
import re
import sys

path, new_code, new_name = sys.argv[1], sys.argv[2], sys.argv[3]
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

updated, c1 = re.subn(r'(versionCode\s*=\s*)\d+', rf'\g<1>{new_code}', content, count=1)
updated, c2 = re.subn(r'(versionName\s*=\s*")[^"]+(")', rf'\g<1>{new_name}\g<2>', updated, count=1)

if c1 != 1 or c2 != 1:
    raise SystemExit("Failed to update versionCode/versionName in app/build.gradle.kts")

with open(path, "w", encoding="utf-8") as f:
    f.write(updated)
PYEOF

echo "Updated version: $CURRENT_NAME ($CURRENT_CODE) -> $NEW_NAME ($NEW_CODE)"

if [[ "$CREATE_TAG" == true ]]; then
  git -C "$ROOT_DIR" tag -a "v$NEW_NAME" -m "Release v$NEW_NAME ($NEW_CODE)"
  echo "Created git tag v$NEW_NAME"
fi
