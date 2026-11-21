# Build & Release Scripts

All scripts work with either `./gradlew` (local/CI) or system `gradle`, and output packaged binaries to `./bundled-apk/`.

- `sync-ruleset.sh` — Validates `ruleset/ruleset.json`, syncs it into `app/src/main/assets/ruleset.json`, and pushes updates to the remote GitHub Gist when `GITHUB_PAT` is exported in the environment (`--validate-only`, `--push`, `--sync-app-version`). Never commit or hardcode a PAT in source files.
- `bump-version.sh` — Bump `versionCode` and semantic `versionName` (`--patch`, `--minor`, `--major`, `--version`, `--code`, `--tag`) in `app/build.gradle.kts`.
- `build-debug.sh` — Assemble `app-debug.apk`, copy to `./bundled-apk/`, and generate `.sha256` checksum.
- `build-release.sh` — Assemble signed `app-release.apk` and `app-release.aab` (requires `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`), copy to `./bundled-apk/`, and generate `.sha256` checksums.
- `package-release.sh` — Full pipeline: validates & syncs ruleset, optional `--bump`, runs `:app:testDebugUnitTest`, builds `--mode debug|release|all`, and writes `RELEASE_INFO.txt` into `./bundled-apk/`.
- `verify.sh` — Runs `sync-ruleset.sh --validate-only`, `:app:testDebugUnitTest`, and `:app:lintDebug`.
