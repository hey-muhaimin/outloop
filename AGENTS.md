# Outloop

Android app (Kotlin + Jetpack Compose) that interrupts short-form video feeds (YouTube Shorts, IG/FB Reels, etc.) via an accessibility service + overlay shield. Single Gradle module `:app`; `applicationId = com.suprbuild.outloop`, source namespace stays `com.example` (legacy FeedSurgeon class names are intentional — do not rename). Room + KSP, Moshi codegen, DataStore preferences, WorkManager ruleset sync, Robolectric UI tests. Gradle 9.3.1 wrapper, AGP 9.1.1, Kotlin 2.4.21, compileSdk 36 / minSdk 24.

## Dev environment (Windows, git-bash)

- Shell is git-bash; pass native `C:/...` paths to native tools (gradle, java), not `/c/...`.
- `local.properties` (gitignored, absent after AI Studio re-imports): `sdk.dir=C\:/Users/mahmu/Android/Sdk` — the colon MUST be escaped or lint aborts with `PropertyEscape`.
- Java is not on PATH: JDK 17 at `C:/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot` (set `JAVA_HOME`); a Foojay-provisioned JDK 21 lives in `~/.gradle/jdks`.
- `debug.keystore` at project root (gitignored) is required by the debug signingConfig (alias `androiddebugkey`, store/key pass `android`). Recreate with:
  `keytool -genkey -v -keystore E:/Dev/Projects/outloop/debug.keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android, OU=Android, O=Android, L=Unknown, ST=Unknown, C=US"`
- `.env` is optional (secrets plugin falls back to `.env.example`); no `google-services.json` needed — plugin is set to WARN.
- Do not remove/modify the `signingConfigs` or their assignments in `app/build.gradle.kts` (AI Studio build compatibility).
- Git root is the PARENT dir `E:/Dev/Projects`, not this folder; `outloop/` files are currently untracked there and the old root-level layout shows as deleted. Check `git rev-parse --show-toplevel` before committing.

## Build & test

- Debug APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. Always build it after implementing changes and verify the APK exists.
- Unit + Robolectric tests: `./gradlew :app:testDebugUnitTest`; lint: `./gradlew :app:lintDebug`.
- Full check (what CI runs): `./scripts/verify.sh` (ruleset validation + tests + lint).
- Packaged builds: `./scripts/build-debug.sh` / `./scripts/package-release.sh --mode debug|release|all` → `build-output/` with `.sha256` files. Release needs `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` env vars.
- Release verify: with those env vars set, `./gradlew :app:assembleRelease :app:bundleRelease` (auto-runs `lintVitalRelease` + `minifyReleaseWithR8`). Outputs: `app/build/outputs/apk/release/app-release.apk`, `app/build/outputs/bundle/release/app-release.aab`, R8 map `app/build/outputs/mapping/release/mapping.txt` (upload to Play Console). Signing alias is `upload`; for verification use a throwaway keystore and delete it after — never commit real keys. Permanent-key workflow: `powershell_keystore_workflow.md`; Play forms: `play_submission_checklist.md`.
- Signature checks: `jarsigner -verify <aab>` ("jar verified."; AAB JarInputStream warnings are normal), `C:/Users/mahmu/Android/Sdk/build-tools/36.0.0/apksigner.bat verify --verbose <apk>`, same dir `aapt2.exe dump badging <apk>` (expect `com.suprbuild.outloop`, label `Outloop`).
- Version bumps: `./scripts/bump-version.sh --patch|--minor|--major` (edits `app/build.gradle.kts`).
- Verify test results from `app/build/test-results/testDebugUnitTest/TEST-*.xml` (tests/failures/errors attrs), not just BUILD SUCCESSFUL — a failed `packageDebug` can abort before tests run and Gradle then reports stale UP-TO-DATE tests.
- Single test class: `./gradlew :app:testDebugUnitTest --tests "com.example.ExampleRobolectricTest"`. Test methods use backtick names with spaces — camel-case wildcards match nothing.

## Pitfalls

- `scripts/*.sh` pass git-bash `/e/...` paths to native Windows `python3` → FileNotFoundError. Run them with a `python3` shim on PATH that `cygpath -w`-converts existing absolute-path args (shim kept at `C:/Users/mahmu/AppData/Local/hermes/cache/scratch/outloop-verify/python3`).
- Lint caches its analysis and `local.properties` is not a declared lint input — after fixing it, force `./gradlew :app:lintAnalyzeDebug --rerun` or lint keeps reporting the stale error.
- Robolectric tests are `@Config(sdk = [36])` → need a Java 21 test JVM; host JDK is 17. `app/build.gradle.kts` sets a Foojay-provisioned Java 21 `javaLauncher` on all `Test` tasks (resolver in `settings.gradle.kts`). CI (JDK 17) relies on this block.
- `packageDebug` fails with "Unable to delete directory" when another process holds `app-debug.apk` (seen: Phone Link / PhoneExperienceHost.exe). Don't kill user processes; redirect the build dir via a Gradle init script (`-I script --no-configuration-cache`).
- Configuration cache is ON (`gradle.properties`); Kotlin compiles in-process; workers capped at 4.
- `IMPLEMENTATION_PLAN.md` and `README.md` are stale AI Studio artifacts (wrong applicationId/version) — trust `app/build.gradle.kts` and `scripts/README.md` instead.
- Compose tests: `assertTextContains` on a tagged container needs `semantics(mergeDescendants = true)`; tags inside `clickable` ancestors need `useUnmergedTree = true`; `performScrollTo()` before clicking scrollable content, but pinned bottom-bar buttons have no scroll parent (throws). `captureToImage()` times out under Robolectric — assert layout bounds instead.
- Never collect `dataStore.preferencesFlow` infinitely in `MainViewModel.init` — it starves the `stateIn(WhileSubscribed)` uiState combine. Sync globals from UI `LaunchedEffect` instead.

## Ruleset pipeline

- Source of truth: `ruleset/ruleset.json` (per-app viewIds/selectors). Edit it, then run `./scripts/sync-ruleset.sh` — validates schema, copies to `app/src/main/assets/ruleset.json`, and with `--sync-app-version` updates `DEFAULT_RULESET_VERSION` in `SettingsDataStore.kt`. Never hand-edit the assets copy.
- `--push` updates the remote GitHub Gist; requires `GITHUB_PAT` exported. Never commit or hardcode a PAT.
- `RulesetRepository` enforces trusted HTTPS endpoints and falls back to bundled JSON on repeated network failures — keep those behaviors when editing.

## Conventions

- Layout under `app/src/main/java/com/example/`: `data/local` (Room entities/DAO + `SettingsDataStore`), `data/model`, `data/repository`, `service/` (accessibility service, overlay shield, selectors), `ui/screens` + `ui/components` + `MainViewModel.kt`, `worker/`.
- Theming: `FeedSurgeonTheme(appearanceMode)` provides `OutloopTheme.colors` (`LightOutloopColors`/`DarkOutloopColors` semantic palette via `LocalOutloopColors`). Use palette tokens, not hardcoded colors — both light and dark must work.
- Screens expose `testTag`s for the Robolectric Compose tests in `app/src/test/java/com/example/`; after replacing a screen, grep main + test for old tags and removed symbols before reporting done.
- Commits: lowercase conventional style — `feat:`, `fix:`, `refactor:`, `chore:`, `ui:` (optional scope, e.g. `feat(logging):`).
- Play-compliance invariants (don't break): Settings' Accessibility row stays gated on `disclosureAccepted || isAccessibilityEnabled` with the disclosure sheet (`SettingsScreen.kt`); battery-optimization exemption only from user taps in troubleshooting (Onboarding PROBLEM step / Diagnostics); keep `<queries>` scoped to the 5 feed packages + launcher intent — no `QUERY_ALL_PACKAGES`, no storage permissions; accessibility config must not set `isAccessibilityTool`.
