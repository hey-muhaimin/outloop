# AGENTS.md — Outloop Engineering & Compliance Guide

This file defines mandatory project conventions, architecture boundaries, Google Play Policy guardrails, testing rules, and release workflows for AI coding agents and contributors working on **Outloop**.

---

## 1. Project Identity & Immutable Build Rules

- **App Name**: `Outloop` (`app/src/main/res/values/strings.xml` and root `metadata.json` **must stay in sync**).
- **Application ID**: `com.suprbuild.outloop` (set in `app/build.gradle.kts`). **Do not change `applicationId` unless explicitly requested.**
- **Internal Namespace**: `com.example` (keep Kotlin source directories and `R` package under `com/example` — never rename `namespace`).
- **Current Release Version**: `versionCode = 8`, `versionName = "8.0"`.
- **Toolchain**: Kotlin, Jetpack Compose, AGP `9.1.1`, Gradle `9.3.1`, JDK 21, `compileSdk = 36` (`minSdk = 24`, `targetSdk = 36`).
- **Protected Files & Signing Blocks**:
  - **NEVER** modify, delete, or overwrite `debug.keystore` or `debug.keystore.base64`.
  - **NEVER** modify `signingConfigs` (`release` and `debugConfig`) or `buildTypes` signing assignments in `app/build.gradle.kts`.
  - **NEVER** remove `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API` from `metadata.json`.
  - **NEVER** create or commit `local.properties`, `*.jks`, `*.keystore`, or `.env` files containing secrets.

---

## 2. Architecture & Core Modules (`app/src/main/java/com/example/`)

- **Entry & Navigation**:
  - `FeedSurgeonApp.kt`: Initializes `FeedSurgeonDatabase`, `SettingsDataStore`, `InterventionRepository`, `RulesetRepository`, and schedules `RulesetSyncWorker`.
  - `MainActivity.kt`: Enables `enableEdgeToEdge()`, hosts Compose navigation across Onboarding, Tabs (`Today`, `Insights`, `Settings`), and sub-screens (`ManageApps`, `AppDetail`, `Privacy`, `Diagnostics`), and respects `BackHandler`.
  - `ui/MainViewModel.kt`: Single source of truth exposing `StateFlow<MainUiState>`.
- **Local Persistence (`data/`)**:
  - **Room (`FeedSurgeonDatabase`, `FeedSurgeonDao`)**: Stores `TargetAppRuleEntity`, `InterventionEventEntity`, and `AppDailyUsageEntity`.
    - *Invariant*: Built-in rule seeding uses `insertRulesIfMissing` (`OnConflictStrategy.IGNORE`) so user settings like `dailyLimitMinutes` and `enabled` are never overwritten on database open.
  - **DataStore (`SettingsDataStore`, `AppPreferences`)**: Stores onboarding/disclosure acceptance, strict mode, quiet hours, preferred pause duration, daily pause target, appearance, and cached ruleset metadata.
  - **Repositories**: `InterventionRepository` (rules, events, usage tracking), `RulesetRepository` (bundled + conditional `ETag` HTTPS Gist sync), `LocalBackupManager` (SAF JSON export/preview/restore).
- **Accessibility & Overlay Engine (`service/`)**:
  - `FeedSurgeonService.kt`: `AccessibilityService` paired with `specialUse` foreground service lifecycle; tracks foreground packages (`ForegroundPackageMonitor`), enforces cooldowns/daily limits (`AppSessionTimeTracker`), and dispatches `OVERLAY`, `HOME_TAB`, or `BACK` interventions.
  - `SelectorMatcher.kt`: Inspects view IDs, fallback tab descriptions, target-app Home tab navigation (`findHomeTabNode`, ignoring Outloop's own overlay window), and single-step in-app back exit (`findInAppBackNode`).
  - `AccessibilityOverlayShield.kt` & `TransparentPauseDimOverlay.kt`: Renders `TYPE_ACCESSIBILITY_OVERLAY` with the 2-row action dock (inline `Home | Exit` + full-width `Pause`).
  - `ForegroundProtectionNotifier.kt` & `ProtectionActionReceiver.kt`: Posts silent ongoing notification (`Outloop active`) with `Pause 15 min` / `Resume` broadcast actions.
  - `ProtectionHealthHelper.kt`: Checks Accessibility/Notification/Battery status and launches compliant App Info / Battery settings.
  - `AccessibilityEventLogger.kt`: Opt-in (`eventCaptureEnabled = false` by default) local log writer + `FileProvider` share URI generator.

---

## 3. Accessibility Service Architecture, Feed Pausing Mechanics & Limitations

Outloop’s core functionality relies on `FeedSurgeonService` (extending Android `AccessibilityService`) to detect and pause short-form video loops (Reels, Shorts, TikTok, Spotlight, Watch feeds) in user-selected target applications.

### A. Purpose & Core Mission
- **Mission**: Break involuntary dopamine loops on short-form feeds while keeping messaging, search, profiles, and deliberate utility completely accessible.
- **Privacy Guarantee**: Operates 100% on-device with zero telemetry, zero cloud logging, and zero message/password inspection.

### B. Dual-Layer Detection Pipeline (`SelectorMatcher.kt`)
1. **Layer 1: Explicit Selector Matching**:
   - Inspects `viewIdResourceName` against configured `TargetAppRuleEntity.viewIds`.
   - Evaluates scroll source events (`AccessibilityEvent.TYPE_VIEW_SCROLLED`) against specific feed recycler view IDs.
   - Falls back to active tab content descriptions (`homeTabLabel` vs active tab labels).
2. **Layer 2: Structural Container Heuristics (`detectShortFormFeedPattern`)**:
   - Detects full-bleed vertical pagers (`ViewPager2`, `RecyclerView`) whose bounds fill `>= 55%` of screen height and `>= 70%` of screen width.
   - Matches layout hint tokens (`short`, `shorts`, `reel`, `reels`, `clips`, `spotlight`, `opera`, `watch_feed`, `vertical_pager`).
   - **Safe Exclusions**: Strictly filters out messages, direct chats, inboxes, search, comments, profiles, and settings via `NON_FEED_CONTAINER_HINTS`.

### C. Intervention & Pausing Mechanics
When a short-form feed or daily limit (`dailyLimitMinutes`) is triggered (subject to active cooldowns and snooze timers):
- **`OVERLAY` Mode**: Attaches `TYPE_ACCESSIBILITY_OVERLAY` via `AccessibilityOverlayShield` / `TransparentPauseDimOverlay`, displaying the 2-row action dock (`Go to Home`, `Exit App`, and `Pause Guard`).
- **`HOME_TAB` Mode**: Programmatically invokes `ACTION_CLICK` on the target app's root Home tab node (`findHomeTabNode`) or in-app back arrow (`findInAppBackNode`).
- **`BACK` Mode**: Fires `GLOBAL_ACTION_BACK` for single-step exit.

### D. Strict Zero-Data Privacy Architecture
- **No Disk Logging**: `AccessibilityEventLogger` does not write captured node trees or screen text to disk. Snapshots are explicitly stubbed (`CapturedTreeSnapshot("redacted", emptyList(), emptyList())`) to guarantee absolute user privacy.
- **Scoped Permissions**: No broad storage, query-all-packages, or overlay window permissions beyond `BIND_ACCESSIBILITY_SERVICE` and `specialUse` foreground service.

### E. Technical Limitations & OEM Countermeasures
- **OEM Background Task Kills**: Aggressive battery management on Xiaomi (MIUI/HyperOS), Samsung (OneUI), and Huawei can terminate accessibility bindings when swiped from recents. **Countermeasures**: `START_STICKY`, `onTaskRemoved` state re-assertion, `specialUse` ongoing notification, and `ProtectionHealthHelper` battery settings guidance.
- **Dynamic Layout Shifts & Obfuscation**: Apps frequently rename view IDs or run server-driven A/B tests. Handled by fallback tab matching, heuristic container checks, and conditional Gist ruleset sync (`RulesetRepository`).
- **WebViews & Canvas Feeds**: Custom-drawn Canvas views or obfuscated DOM inside embedded webviews cannot be inspected by accessibility node traversal.


---

## 3. Google Play Policy & Privacy Guardrails (CRITICAL)

Every code or manifest change **must** preserve Google Play Production compliance:

1. **No Restricted Permissions**:
   - **DO NOT** add `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` or launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Always route battery guidance via `ProtectionHealthHelper.openBatterySettings(context)` (`ACTION_APPLICATION_DETAILS_SETTINGS` / `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`).
   - **DO NOT** add `QUERY_ALL_PACKAGES`, `SYSTEM_ALERT_WINDOW`, `READ_EXTERNAL_STORAGE`, or `READ_MEDIA_*`. Keep `<queries>` scoped to supported packages + launcher intent; use SAF (`CreateDocument`/`OpenDocument`) and `FileProvider` for files.
2. **Accessibility API Rules (`FeedSurgeonService`)**:
   - **NEVER** set `android:isAccessibilityTool="true"` in `res/xml/accessibility_service_config.xml` (Outloop is a digital wellbeing app, not an assistive disability tool).
   - Every entry point to `Settings.ACTION_ACCESSIBILITY_SETTINGS` (`OnboardingScreen` and `SettingsScreen`) **MUST** remain gated behind an un-prechecked **Accessibility disclosure** modal requiring explicit user confirmation (`Agree and continue` vs `Not now`) when `!disclosureAccepted`.
   - Keep inspection strictly on-device; never log, transmit, or store message text, passwords, or media streams.
3. **Foreground Service (`specialUse`) Rules**:
   - Keep `android:foregroundServiceType="specialUse"` and `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` in `AndroidManifest.xml` synchronized with `ForegroundProtectionNotifier` and `PrivacyScreen`.
4. **Network & Data Safety Honesty**:
   - `INTERNET` is strictly for optional, unauthenticated, read-only HTTPS `HEAD`/`GET` (`ETag`) selector-ruleset checks (`RulesetRepository`). Keep `android:allowBackup="false"` and ensure `PrivacyScreen`, `About Outloop`, and `play_submission_checklist.md` stay consistent whenever behavior changes.

---

## 4. UI, Design System & Accessibility Conventions

- **Design Tokens**: Use `OutloopTheme.colors`, `MaterialTheme.typography`, and `OutloopMetrics` (`ui/theme/Theme.kt`). Do not hardcode hex colors inside screen composables.
- **Shared Components**: Reuse `OutloopTabHeader`, `OutloopSubScreenScaffold`, `OutloopTonalCard`, `OutloopModalSheet`, `OutloopPrimaryButton`, `OutloopSecondaryButton`, and `OutloopSwitch`.
- **Interactivity & A11y**:
  - Minimum `48.dp` touch target on all clickable elements.
  - Non-null `contentDescription` on meaningful icons/images.
  - Assign snake_case `Modifier.testTag("...")` to primary interactive UI elements and sub-screen actions.
  - Wrap secondary/sub-screen composables with `BackHandler` to return cleanly.

---

## 5. Testing Rules (Robolectric-Only)

- **NO Emulator / ADB / Espresso**: Never write `androidTest/` instrumentation tests or invoke `adb`.
- **Use Robolectric (`@Config(sdk = [36])`)**: All unit, Room migration, Service lifecycle, and Compose UI tests live in `app/src/test/java/com/example/` (`ExampleRobolectricTest.kt`, `FeedSurgeonUiRobolectricTest.kt`, `ExampleUnitTest.kt`).
- **Keep Shared State Clean**: Any test mutating Room rules or `SettingsDataStore` in shared Robolectric contexts must reset mutations before finishing so subsequent tests stay deterministic.
- **Always wait in Compose tests**: Call `composeRule.waitForIdle()` before assertions.

---

## 6. Build, Verification & Release Scripts

- **Prefer `gradle` over `./gradlew`** inside cloud build containers (`gradle` is pre-configured on `PATH`; `scripts/common.sh` resolves `gradle` first and falls back to `./gradlew`).
- **Primary Commands**:
  - Fast verification: `./scripts/verify.sh`
  - Full pre-release verification (tests + debug lint + release vital lint + Compose mapping errors): `./scripts/verify.sh --release`
  - Direct Gradle test run: `gradle :app:testDebugUnitTest`
  - Ruleset schema validation & asset sync (`ruleset/ruleset.json` -> `app/src/main/assets/ruleset.json`): `./scripts/sync-ruleset.sh --validate-only`
  - Version bump: `./scripts/bump-version.sh [--major|--minor|--patch|--version X.Y.Z] [--code N]`
  - Package debug/release outputs to `./build-output/`: `./scripts/build-debug.sh`, `./scripts/build-release.sh`, `./scripts/package-release.sh`
- **Before Finishing Any Change**:
  1. Verify compilation (`compile_applet` or `:app:assembleDebug`).
  2. Run `gradle :app:testDebugUnitTest` (or `./scripts/verify.sh --release` for manifest/resource/release changes).
  3. Keep `play_submission_checklist.md`, `powershell_keystore_workflow.md`, `README.md`, and `scripts/README.md` in sync if versions, permissions, or disclosures change.
