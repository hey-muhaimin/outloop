#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

RUN_RELEASE_CHECKS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release) RUN_RELEASE_CHECKS=true; shift ;;
    -h|--help)
      cat <<EOF
Usage: $(basename "$0") [--release]

Runs ruleset validation, JVM & Robolectric unit tests, and Android lint.
Pass --release to also run :app:lintVitalRelease and :app:reportReleaseComposeMappingErrors.
EOF
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

"$SCRIPT_DIR/sync-ruleset.sh" --validate-only

GRADLE="$(resolve_gradle)"
TASKS=(":app:testDebugUnitTest" ":app:lintDebug")
if [[ "$RUN_RELEASE_CHECKS" == true ]]; then
  TASKS+=(":app:lintVitalRelease" ":app:reportReleaseComposeMappingErrors")
fi

echo "Running verification tasks: ${TASKS[*]}..."
(cd "$ROOT_DIR" && "$GRADLE" "${TASKS[@]}")
echo "Verification passed."
