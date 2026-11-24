# Build & Release Scripts

All scripts automatically use `gradle` if available on `PATH` or fall back to `./gradlew`, and output packaged artifacts to `./build-output/`.

- `verify.sh` — Runs `sync-ruleset.sh --validate-only`, `:app:testDebugUnitTest`, and `:app:lintDebug` (pass `--release` to also run `:app:lintVitalRelease` and `:app:reportReleaseComposeMappingErrors`).
- `sync-ruleset.sh` — Validates `ruleset/ruleset.json`, syncs it into `app/src/main/assets/ruleset.json`, and pushes updates to the remote GitHub Gist when `GITHUB_PAT` is exported (`--validate-only`, `--push`, `--sync-app-version`). Never commit or hardcode a PAT in source files.
- `bump-version.sh` — Bump `versionCode` and `versionName` (`--patch`, `--minor`, `--major`, `--version`, `--code`, `--tag`) in `app/build.gradle.kts`.
- `build-debug.sh` — Assemble `app-debug.apk`, copy to `./build-output/`, and generate a `.sha256` checksum.
- `build-release.sh` — Run release lint + Compose mapping checks, assemble signed `app-release.apk` / `app-release.aab` (`--apk-only`, `--aab-only`; requires `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`), and copy outputs, R8 `mapping.txt`, and `.sha256` checksums to `./build-output/`.
- `package-release.sh` — Full pipeline: validates & syncs ruleset, optionally bumps version (`--bump`), runs `:app:testDebugUnitTest` (unless `--skip-tests`), builds `--mode debug|release|all`, and writes `RELEASE_INFO.txt` into `./build-output/`.
- `common.sh` — Shared shell helpers (`resolve_gradle`, version readers, and `compute_sha256`).
