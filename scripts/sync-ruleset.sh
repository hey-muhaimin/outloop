#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

RULESET_SRC="$ROOT_DIR/ruleset/ruleset.json"
ASSETS_DST="$ROOT_DIR/app/src/main/assets/ruleset.json"
SETTINGS_FILE="$ROOT_DIR/app/src/main/java/com/example/data/local/SettingsDataStore.kt"
GIST_ID="1ebadd94effdba1255d42661ef218fc9"

usage() {
  cat <<EOF
Usage: $(basename "$0") [--validate-only] [--push] [--sync-app-version]

Validates ruleset/ruleset.json schema, syncs it into app/src/main/assets/ruleset.json,
optionally updates DEFAULT_RULESET_VERSION in SettingsDataStore.kt, and when --push
is provided (or GITHUB_PAT is exported without --validate-only), updates GitHub Gist $GIST_ID.
EOF
}

VALIDATE_ONLY=false
PUSH_GIST=false
SYNC_APP_VERSION=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --validate-only) VALIDATE_ONLY=true; shift ;;
    --push) PUSH_GIST=true; shift ;;
    --sync-app-version) SYNC_APP_VERSION=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ ! -f "$RULESET_SRC" ]]; then
  echo "Error: Missing $RULESET_SRC" >&2
  exit 1
fi

echo "Validating $RULESET_SRC..."
RULESET_VERSION="$(python3 - "$RULESET_SRC" <<'PY'
import json, sys

path = sys.argv[1]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)

version = data.get("version", "")
apps = data.get("apps", [])
if not isinstance(version, str) or not version.strip():
    raise SystemExit("Invalid ruleset: 'version' must be a non-empty string")
if not isinstance(apps, list) or len(apps) == 0:
    raise SystemExit("Invalid ruleset: 'apps' must be a non-empty list")

seen_pkgs = set()
for idx, app in enumerate(apps):
    pkg = app.get("packageName", "").strip()
    display = app.get("displayName", "").strip()
    feed = app.get("feedLabel", "").strip()
    view_ids = app.get("viewIds", [])
    if not pkg or "." not in pkg:
        raise SystemExit(f"Invalid ruleset app[{idx}]: invalid packageName '{pkg}'")
    if pkg in seen_pkgs:
        raise SystemExit(f"Invalid ruleset app[{idx}]: duplicate packageName '{pkg}'")
    seen_pkgs.add(pkg)
    if not display or not feed:
        raise SystemExit(f"Invalid ruleset app[{idx}]: displayName and feedLabel are required")
    if not isinstance(view_ids, list) or len(view_ids) == 0 or any(not isinstance(v, str) or not v.strip() for v in view_ids):
        raise SystemExit(f"Invalid ruleset app[{idx}]: viewIds must be a non-empty list of strings")

print(version.strip())
PY
)"

mkdir -p "$(dirname "$ASSETS_DST")"
cp "$RULESET_SRC" "$ASSETS_DST"
echo "Synced ruleset ($RULESET_VERSION) to app/src/main/assets/ruleset.json"

if [[ "$SYNC_APP_VERSION" == true && -f "$SETTINGS_FILE" ]]; then
  python3 - "$SETTINGS_FILE" "$RULESET_VERSION" <<'PY'
import re, sys

settings_path, version = sys.argv[1], sys.argv[2]
with open(settings_path, "r", encoding="utf-8") as f:
    content = f.read()

updated = re.sub(
    r'const val DEFAULT_RULESET_VERSION = "[^"]+"',
    f'const val DEFAULT_RULESET_VERSION = "{version}"',
    content,
    count=1
)
with open(settings_path, "w", encoding="utf-8") as f:
    f.write(updated)
PY
  echo "Synced DEFAULT_RULESET_VERSION to $RULESET_VERSION in SettingsDataStore.kt"
fi

if [[ "$VALIDATE_ONLY" == true ]]; then
  echo "Validation and local asset sync complete (--validate-only)."
  exit 0
fi

if [[ -z "${GITHUB_PAT:-}" ]]; then
  if [[ "$PUSH_GIST" == true ]]; then
    echo "Error: GITHUB_PAT environment variable is not set. Export GITHUB_PAT before pushing." >&2
    exit 1
  fi
  echo "GITHUB_PAT not set; skipping remote Gist push (local validation & asset sync succeeded)."
  exit 0
fi

echo "Pushing $RULESET_SRC ($RULESET_VERSION) to GitHub Gist $GIST_ID..."
PAYLOAD_JSON="$(python3 - "$RULESET_SRC" "$RULESET_VERSION" <<'PY'
import json, sys

with open(sys.argv[1], "r", encoding="utf-8") as f:
    raw = f.read()

payload = {
    "description": f"Outloop ruleset {sys.argv[2]}",
    "files": {
        "ruleset.json": {
            "content": raw
        }
    }
}
print(json.dumps(payload))
PY
)"

HTTP_CODE="$(curl -sS -o /tmp/gist_update_resp.json -w "%{http_code}" \
  -X PATCH \
  -H "Accept: application/vnd.github+json" \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  -d "$PAYLOAD_JSON" \
  "https://api.github.com/gists/${GIST_ID}")"

if [[ "$HTTP_CODE" -lt 200 || "$HTTP_CODE" -ge 300 ]]; then
  echo "Error: GitHub Gist update failed with HTTP $HTTP_CODE" >&2
  cat /tmp/gist_update_resp.json >&2
  rm -f /tmp/gist_update_resp.json
  exit 1
fi

RAW_URL="$(python3 - /tmp/gist_update_resp.json <<'PY'
import json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    resp = json.load(f)
print(resp.get("files", {}).get("ruleset.json", {}).get("raw_url", ""))
PY
)"
rm -f /tmp/gist_update_resp.json
echo "Remote Gist updated successfully ($RULESET_VERSION): $RAW_URL"
