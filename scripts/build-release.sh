#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

BUILD_APK=true
BUILD_AAB=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk-only) BUILD_AAB=false; shift ;;
    --aab-only) BUILD_APK=false; shift ;;
    -h|--help)
      cat <<EOF
Usage: $(basename "$0") [--apk-only | --aab-only]

Runs release lint & Compose mapping verification, builds signed release APK/AAB
artifacts (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD), and copies outputs
plus R8 mapping.txt and SHA-256 checksums to ./build-output/.
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

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
TASKS=(":app:lintVitalRelease" ":app:reportReleaseComposeMappingErrors")
if [[ "$BUILD_APK" == true ]]; then
  TASKS+=(":app:assembleRelease")
fi
if [[ "$BUILD_AAB" == true ]]; then
  TASKS+=(":app:bundleRelease")
fi

(cd "$ROOT_DIR" && "$GRADLE" "${TASKS[@]}")

BUNDLED_DIR="$ROOT_DIR/build-output"
mkdir -p "$BUNDLED_DIR"

if [[ "$BUILD_APK" == true ]]; then
  APK_SRC="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
  if [[ -f "$APK_SRC" ]]; then
    APK_DST="$BUNDLED_DIR/outloop-v${VERSION_NAME}-${VERSION_CODE}-release.apk"
    cp "$APK_SRC" "$APK_DST"
    cp "$APK_SRC" "$BUNDLED_DIR/outloop-release-latest.apk"
    cp "$APK_SRC" "$BUNDLED_DIR/app-release.apk"
    echo "$(compute_sha256 "$APK_DST")  $(basename "$APK_DST")" > "${APK_DST}.sha256"
    echo "Release APK copied to: $APK_DST"
  fi
fi

if [[ "$BUILD_AAB" == true ]]; then
  AAB_SRC="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"
  if [[ -f "$AAB_SRC" ]]; then
    AAB_DST="$BUNDLED_DIR/outloop-v${VERSION_NAME}-${VERSION_CODE}-release.aab"
    cp "$AAB_SRC" "$AAB_DST"
    cp "$AAB_SRC" "$BUNDLED_DIR/outloop-release-latest.aab"
    echo "$(compute_sha256 "$AAB_DST")  $(basename "$AAB_DST")" > "${AAB_DST}.sha256"
    echo "Release AAB bundled: $AAB_DST"
  fi
fi

MAPPING_SRC="$ROOT_DIR/app/build/outputs/mapping/release/mapping.txt"
if [[ -f "$MAPPING_SRC" ]]; then
  MAPPING_DST="$BUNDLED_DIR/outloop-v${VERSION_NAME}-${VERSION_CODE}-mapping.txt"
  cp "$MAPPING_SRC" "$MAPPING_DST"
  echo "R8 mapping saved to: $MAPPING_DST"
fi
