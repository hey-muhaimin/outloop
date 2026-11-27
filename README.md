# Outloop

**Break the infinite scroll loop. Keep the apps.**

Outloop is an open-source, privacy-first Android digital wellbeing app built with Kotlin and Jetpack Compose. It detects when you enter endless short-form video feeds inside supported apps—such as **YouTube Shorts**, **Instagram Reels**, **Facebook Reels**, **TikTok For You**, **X For You**, **Snapchat Spotlight**, and **Reddit Watch**—and gently intervenes with a pause overlay, a return to the app's own **Home** tab, or a Back navigation step while keeping Search, DMs, Subscriptions, and everyday utilities open.

- **Package ID**: `com.suprbuild.outloop`
- **Current Version**: `17.0` (`versionCode = 17`)
- **Min / Target SDK**: `24` / `36`
- **Privacy Policy**: [`https://suprbuild.com/privacy`](https://suprbuild.com/privacy)

---

## Key Capabilities

- **On-Device Feed Detection**: Uses `SelectorMatcher` and `AccessibilityNodeInfo` inspection strictly in device memory across 7 built-in apps plus user-defined custom app rules.
- **Three Flexible Intervention Modes**:
  - **Pause Overlay** (`OVERLAY`): Calm transparent dim overlay with an inline **Home | Exit** dock (returns to the guarded app's Home tab or closes to device Home) and a full-width **Pause** action.
  - **Home Tab Redirect** (`HOME_TAB`): Navigates directly back to the guarded app's Home/Feed tab.
  - **Step Back** (`BACK`): Exits the short-form player view with a single in-app back navigation.
- **Mindful Guardrails**: Per-app daily time budgets, **Strict mode** (10-second confirmation before pausing Guard), **Quiet hours** schedules, **Always open** exemptions for essential utility apps, and **Allow once** / timed snooze breaks.
- **Local Analytics & Portable Backups**: Weekly and per-app insights stored locally in Room, plus user-initiated JSON export/restore via Android's Storage Access Framework (`CreateDocument` / `OpenDocument`).

---

## Release Notes — `v17.0` (`versionCode = 17`)

- **Refined Pause Overlay Modal**: Unified 2-row action dock featuring inline **Home** and **Exit** buttons alongside a full-width **Pause** button for consistent ergonomics and visual hierarchy.
- **In-App Home Tab Navigation**: Tapping **Home** on the pause overlay navigates back to the currently guarded app's own Home/Feed tab (e.g., YouTube Home or Facebook News Feed) from Shorts or Reels instead of leaving the app for Outloop.
- **Google Play Production Compliance & Transparency**:
  - Removed the restricted `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission; troubleshooting flows now open standard Android **App Info / Battery settings**.
  - Updated Accessibility prominent disclosure modals, OS-level Accessibility service description, `specialUse` foreground service subtype metadata, and the in-app **Privacy** screen (**Persistent protection status** & **Optional rule checks**).
- **Database & Release Hardening**: Hardened Room default rule seeding (`OnConflictStrategy.IGNORE`), verified R8 minification/resource shrinking, and resolved Compose mapping checks (`0` errors).

---

## Transparency & Google Play Disclosures

Outloop is designed to operate **strictly on-device** with zero user accounts, zero analytics SDKs, zero ad networks, and `android:allowBackup="false"`.

### 1. Accessibility Service (`FeedSurgeonService` · `BIND_ACCESSIBILITY_SERVICE`)
- **Why it is needed**: Android provides no non-Accessibility API to inspect active UI view IDs (`flagReportViewIds`) and selected navigation tab labels (`flagRetrieveInteractiveWindows`, `canRetrieveWindowContent="true"`) inside third-party apps to distinguish an endless short-form feed from messages, search, or normal content.
- **Prominent Disclosure & Explicit Consent**: Before opening Android's Accessibility settings, both Onboarding (`OnboardingStep.ALLOW_ACCESS`) and Settings (**Apps and access → Accessibility service**) display an explicit **Accessibility disclosure** modal with **Agree and continue** and **Not now** actions and zero pre-checked consent.
- **What is accessed & data handling**:
  - Reads only technical screen labels, package names, and view IDs inside apps the user explicitly enables.
  - Never reads, records, uploads, or shares private messages, passwords, form fields, or video streams.
  - All detection and daily limit counters stay on-device. Users can revoke access anytime under **Android Settings → Accessibility → Outloop**.
- **Note**: Outloop omits `android:isAccessibilityTool="true"` because it is a digital wellbeing focus utility, not an assistive disability tool.

### 2. Foreground Service (`FOREGROUND_SERVICE_SPECIAL_USE` · `specialUse`)
- **Why `specialUse` is needed**: `FeedSurgeonService` runs as a `specialUse` foreground service alongside the user-enabled AccessibilityService so Android and OEM task managers do not terminate active feed protection when Outloop's UI is cleared from Recents. Standard foreground service types (`dataSync`, `mediaPlayback`, `location`, `health`, etc.) do not apply to an on-device digital wellbeing monitor.
- **Manifest Subtype Property (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`)**:
  > *"Runs alongside Android Accessibility Service strictly on-device to recognize short-form video feeds in user-selected apps, show pause overlays or return to the app Home tab, and display ongoing pause/resume status controls."*
- **Transparent User Control**: Displays a silent, low-priority ongoing notification (**`Outloop active`** · *"Guarding X selected apps on your device"*) with one-tap **Pause 15 min** and **Resume** actions (`ForegroundProtectionNotifier` + `ProtectionActionReceiver`).

### 3. Permissions & Network Transparency
- **`POST_NOTIFICATIONS`**: Requested at runtime on Android 13+ so the ongoing status notification and quick Pause/Resume controls are visible; the app continues functioning gracefully if denied.
- **`INTERNET` (Optional Read-Only Ruleset Sync)**: Used solely by `RulesetRepository` / `RulesetSyncWorker` when **Auto-update feed rules** is enabled (or manually triggered in **Diagnostics**) to send conditional HTTPS `HEAD`/`GET` (`ETag`) requests to a public GitHub Gist (`ruleset.json`) for updated selector definitions. Zero personal data, device IDs, usage metrics, or screen contents are transmitted.
- **Scoped `<queries>`**: Restricted to the 7 supported packages (`com.google.android.youtube`, `com.instagram.android`, `com.facebook.katana`, `com.zhiliaoapp.musically`, `com.twitter.android`, `com.snapchat.android`, `com.reddit.frontpage`) plus `ACTION_MAIN` / `CATEGORY_LAUNCHER` for user-selected apps. Never requests `QUERY_ALL_PACKAGES`.
- **No Storage or Battery Exemption Permissions**: Uses Android Storage Access Framework (`CreateDocument` / `OpenDocument`) for backups and `FileProvider` (`FLAG_GRANT_READ_URI_PERMISSION`) for opt-in troubleshooting log sharing (`eventCaptureEnabled = false` by default). Does **not** request `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `SYSTEM_ALERT_WINDOW`, or `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## Architecture & Tech Stack

- **UI**: Kotlin, Jetpack Compose, Material Design 3, Type-safe Navigation Compose (`OutloopTheme`, `OutloopMetrics`, `OutloopModalSheet`)
- **State & Storage**: MVVM (`MainViewModel` + `StateFlow`), Room (`FeedSurgeonDatabase`), Jetpack Preferences DataStore (`SettingsDataStore`)
- **Services & Background**: `FeedSurgeonService` (`AccessibilityService` + `specialUse` FGS), `AccessibilityOverlayShield`, `TransparentPauseDimOverlay`, `WorkManager` (`RulesetSyncWorker`)
- **Networking & Serialization**: OkHttp (`ETag` conditional requests), Moshi, `kotlinx.serialization`
- **Testing**: JUnit 4 + Robolectric (`sdk = 36`) + Compose UI Test + WorkManager Testing

---

## Building, Verification & Release Scripts

Prerequisites: **JDK 21**, **Android SDK (`compileSdk 36`, `minSdk 24`)**, and Gradle (`gradle` or `./gradlew` / `.\gradlew.bat`).

```bash
# Validate ruleset, run all JVM/Robolectric tests, debug lint, release vital lint, & Compose mapping check
./scripts/verify.sh --release

# Validate ruleset/ruleset.json and sync into app/src/main/assets/ruleset.json
./scripts/sync-ruleset.sh --validate-only

# Build debug APK into ./build-output/ with SHA-256 checksum
./scripts/build-debug.sh

# Build signed release AAB/APK (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD)
./scripts/build-release.sh --aab-only
```

### Related Release Guides
- [`play_submission_checklist.md`](play_submission_checklist.md) — Complete Google Play Console checklist, Data Safety answers, and Accessibility/FGS declaration copy.
- [`powershell_keystore_workflow.md`](powershell_keystore_workflow.md) — Windows PowerShell guide for upload keystore generation, release signing, and signature verification.
- [`scripts/README.md`](scripts/README.md) — Documentation for all helper scripts and CI automation.
