#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

GRADLE="$(resolve_gradle)"
VERSION_NAME="$(read_version_name)"
VERSION_CODE="$(read_version_code)"

echo "Building Outloop Debug APK (v${VERSION_NAME} / ${VERSION_CODE})..."
(cd "$ROOT_DIR" && "$GRADLE" :app:assembleDebug)

APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_PATH" ]]; then
  echo "Error: Expected debug APK not found at $APK_PATH" >&2
  exit 1
fi

BUNDLED_DIR="$ROOT_DIR/bundled-apk"
mkdir -p "$BUNDLED_DIR"

VERSIONED_APK="$BUNDLED_DIR/outloop-v${VERSION_NAME}-${VERSION_CODE}-debug.apk"
LATEST_APK="$BUNDLED_DIR/outloop-debug-latest.apk"

cp "$APK_PATH" "$VERSIONED_APK"
cp "$APK_PATH" "$LATEST_APK"

SHA="$(compute_sha256 "$VERSIONED_APK")"
echo "$SHA  $(basename "$VERSIONED_APK")" > "${VERSIONED_APK}.sha256"

echo "Debug APK bundled: $VERSIONED_APK"
echo "Latest symlink/copy: $LATEST_APK"
echo "SHA-256: $SHA"
