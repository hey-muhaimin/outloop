#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

"$SCRIPT_DIR/sync-ruleset.sh" --validate-only

GRADLE="$(resolve_gradle)"
echo "Running unit tests and Android lint verification..."
(cd "$ROOT_DIR" && "$GRADLE" :app:testDebugUnitTest :app:lintDebug)
echo "Verification passed."
