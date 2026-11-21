#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

BUILD_AAB=true
if [[ "${1:-}" == "--apk-only" ]]; then
  BUILD_AAB=false
fi

GRADLE="$(resolve_gradle)"
VERSION_NAME="$(read_version_name)"
VERSION_CODE="$(read_version_code)"

KEYSTORE_PATH="${KEYSTORE_PATH:-$ROOT_DIR/my-upload-key.jks}"
if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "Error: Release keystore not found at '$KEYSTORE_PATH'." >&2
  echo "Set KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD environment variables before building a signed release." >&2
  exit 1
fi
if [[ -z "${STORE_PASSWORD:-}" || -z "${KEY_PASSWORD:-}" ]]; then
  echo "Error: STORE_PASSWORD and KEY_PASSWORD must be exported for release signing." >&2
  exit 1
fi

echo "Building Outloop Release artifacts (v${VERSION_NAME} / ${VERSION_CODE})..."
TASKS=(":app:assembleRelease")
if [[ "$BUILD_AAB" == true ]]; then
  TASKS+=(":app:bundleRelease")
fi

(cd "$ROOT_DIR" && "$GRADLE" "${TASKS[@]}")

BUNDLED_DIR="$ROOT_DIR/bundled-apk"
mkdir -p "$BUNDLED_DIR"

APK_SRC="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
if [[ -f "$APK_SRC" ]]; then
  APK_DST="$BUNDLED_DIR/outloop-v${VERSION_NAME}-${VERSION_CODE}-release.apk"
  cp "$APK_SRC" "$APK_DST"
  cp "$APK_SRC" "$BUNDLED_DIR/outloop-release-latest.apk"
  echo "$(compute_sha256 "$APK_DST")  $(basename "$APK_DST")" > "${APK_DST}.sha256"
  echo "Release APK bundled: $APK_DST"
fi

if [[ "$BUILD_AAB" == true ]]; then
  AAB_SRC="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"
  if [[ -f "$AAB_SRC" ]]; then
    AAB_DST="$BUNDLED_DIR/outloop-v${VERSION_NAME}-${VERSION_CODE}-release.aab"
    cp "$AAB_SRC" "$AAB_DST"
    echo "$(compute_sha256 "$AAB_DST")  $(basename "$AAB_DST")" > "${AAB_DST}.sha256"
    echo "Release AAB bundled: $AAB_DST"
  fi
fi
