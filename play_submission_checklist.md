# Outloop — Google Play Console Submission Checklist

**Application Identity**
- **App Name**: `Outloop`
- **Application ID (`applicationId`)**: `com.suprbuild.outloop`
- **Internal Namespace (`namespace`)**: `com.example`
- **Version**: `1.0.2` (`versionCode = 3`)
- **SDK Levels**: `minSdk = 24` · `targetSdk = 36`
- **Privacy Policy URL**: `https://suprbuild.com/privacy`

---

## 1. Pre-Upload Build & Integrity Checklist

- [ ] **Branch & Clean Workspace**: Verify local `chore/play-release-prep` (or release branch) has clean `git status` with no tracked `*.jks`, `*.keystore`, `.env`, or `build-output/` artifacts.
- [ ] **Verification Suite Passed**:
  - `.\gradlew.bat clean :app:testDebugUnitTest :app:lintDebug :app:lintVitalRelease`
  - `./scripts/sync-ruleset.sh --validate-only` (if running Git Bash / WSL / CI)
- [ ] **Signed Release Bundle Built**: Build `app-release.aab` via `.\gradlew.bat :app:bundleRelease` with `isMinifyEnabled = true` and `isShrinkResources = true`.
- [ ] **Signature Verified**: Confirm `jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab` reports `jar verified.` and matches your upload key SHA-256 fingerprint.
- [ ] **Play App Signing Enrolled**: Enable **Google Play App Signing** in Play Console (*Setup → App signing*) before uploading the first Internal Testing bundle.
- [ ] **Real-Device Smoke Test (Release APK/AAB)**: Install the R8-minified release build (`bundletool` or `app-release.apk`) on a physical Android device and test:
  - Onboarding Welcome → Prominent Disclosure (**Agree and continue** vs **Not now**) → App selection → Style → Accessibility Settings flow.
  - Declining onboarding (**Not now**) → opening **Settings → Accessibility permission** → verifying the prominent **Accessibility disclosure** modal blocks direct redirection until accepted.
  - Enabling Outloop in Android Accessibility Settings and confirming `FeedSurgeonService` posts the `On-Device Protection` foreground notification.
  - Triggering an actual intervention on YouTube Shorts / Instagram Reels, tapping **Allow once** / **Pause 15 min**, and confirming **Export local backup** / **Restore from backup**.

---

## 2. Store Listing & App Metadata Compliance

- [ ] **App Title**: `Outloop` (7 characters; complies with Google Play's $\le 30$-character cap; no emojis, ALL CAPS, or promotional buzzwords such as "Free", "Best", or "#1").
- [ ] **Short & Full Descriptions**:
  - Accurately describe Outloop as an on-device digital wellbeing tool that detects and interrupts short-form video feeds (YouTube Shorts, Instagram Reels, Facebook Reels, Snapchat Spotlight, Reddit Watch) while keeping access to the rest of those apps.
  - Do **not** claim affiliation with or endorsement by YouTube, Instagram, Meta, Snapchat, Reddit, or Google.
  - Do **not** make unverified medical or clinical addiction-treatment claims.
- [ ] **Graphics Assets**:
  - High-res icon (`512 × 512` PNG) matching the adaptive launcher icon (`ic_launcher`).
  - Feature graphic (`1024 × 500` PNG/JPEG).
  - At least 2 phone screenshots (light/dark mode Home, Onboarding Privacy Disclosure, Insights, Settings) showing real app UI without misleading device-notification imitation or prohibited buzzwords.
- [ ] **Category & Contact Details**:
  - Primary Category: **Productivity** (or **Health & Fitness**).
  - Developer Email / Website / Privacy Policy URL configured and monitored for Google Play review notices.

---

## 3. Privacy Policy Requirements (`https://suprbuild.com/privacy`)

Before submitting for review, confirm the live HTTPS page at `https://suprbuild.com/privacy` returns HTTP `200` (without login/geo blocks) and covers all of the following:

- [ ] **App & Developer Identification**: Explicitly names **Outloop** (`com.suprbuild.outloop`) and lists a working developer contact email.
- [ ] **AccessibilityService Data Access & Purpose**:
  - Explains that Outloop uses Android's `AccessibilityService` (`FeedSurgeonService`) solely to read active screen view IDs (`flagReportViewIds`) and visible/selected tab labels (`flagRetrieveInteractiveWindows`, `canRetrieveWindowContent="true"`) in apps the user explicitly enables.
  - Explains that this is used exclusively to recognize when a short-form video feed opens and perform the user's chosen intervention (pause overlay, Back navigation, or Home tab redirection).
- [ ] **Strict Non-Collection & Non-Sharing Statement**:
  - Explicitly states that Outloop does **not** read, record, upload, sell, or share private messages, passwords, form inputs, media streams, or personal identifiers.
  - Explicitly states that zero Accessibility data leaves the device.
- [ ] **On-Device Storage & User-Initiated Exports**:
  - Documents that app rules, preferences, quiet hours, and interruption statistics are stored strictly on-device (Room SQLite + Jetpack DataStore), with Android cloud Auto Backup disabled (`android:allowBackup="false"`).
  - Documents that optional troubleshooting logs (`accessibility_events_log.txt`) are disabled by default, store only structural package/view-ID/tab snapshots locally, and are exported only when the user manually opens the Android share sheet (`FileProvider`) or deletes them in Diagnostics.
  - Documents that JSON backups are created and restored solely at user-chosen locations via Android's Storage Access Framework (`CreateDocument` / `OpenDocument`).
- [ ] **Remote Ruleset Sync Disclosure**:
  - Explains that when *Auto-update feed rules* is enabled (or when manually triggered in Diagnostics), Outloop performs a read-only HTTPS `HEAD`/`GET` request to fetch updated public selector definitions (`ruleset.json`) from GitHub Gist without transmitting user identifiers or analytics.
- [ ] **Revocation & Deletion Instructions**:
  - Explains how users can revoke Accessibility access at any time (*Android Settings → Accessibility → Outloop*) and erase all local counts/logs (*Settings → Clear activity history* and *Diagnostics → Clear*).

---

## 4. Data Safety Questionnaire (*App Content → Data safety*)

Outloop includes zero third-party analytics, crash reporting, ad networks, authentication, or cloud database SDKs.

| Play Console Question | Recommended Answer | Technical Basis in Codebase |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **No** | All state, preferences, rules, and interruption events remain local in Room/DataStore (`android:allowBackup="false"`). |
| **Remote Ruleset Fetch (`RulesetRepository`)** | Does not trigger "Data collected" | Unauthenticated one-way HTTPS `HEAD`/`GET` to `gist.githubusercontent.com/.../ruleset.json`; no user/device payload is sent. |
| **Opt-in Diagnostic Log (`AccessibilityEventLogger`)** | Local-only / user-initiated share | Disabled by default (`eventCaptureEnabled = false`). Written locally only if toggled on, and shared only if the user invokes `Intent.createChooser` via `FileProvider`. |
| **JSON Backup Export/Import (`LocalBackupManager`)** | Local-only / user-initiated file picker | Uses SAF `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`; Outloop never transmits backup files to a developer server. |

- [ ] Confirm `app/build.gradle.kts` still has all unused Firebase/Analytics/Auth/Ads dependencies commented out before final submission.
- [ ] If any SDK (such as Firebase Analytics, Crashlytics, or Firestore) is added in a future release, update the Data Safety form and Privacy Policy before publishing that update.

---

## 5. App Content Rating (IARC) & Target Audience Settings

### Target Audience & Content (*App Content → Target audience and content*)
- [ ] **Target Age Groups**: Select **18 and over** (or **13–15, 16–17, 18 and over**).
- [ ] **Exclude Under-13 Children**: Do **NOT** select any age band under 13.
  - *Critical Rationale*: Selecting ages under 13 enrolls the app in the **Google Play Families Policy** program, which restricts cross-app `<queries>` visibility, social-app inspection, and `AccessibilityService` usage.
- [ ] **Appeal to Children**: Answer **No** (the store listing, monochrome editorial design, and digital wellbeing copy are geared toward teen/adult focus management, not children).

### Content Rating Questionnaire (IARC)
- [ ] **Category**: Utility, Productivity, Communication, or Other (Non-Game).
- [ ] **Violence / Sexuality / Profanity / Substances / Gambling**: **No** across all categories.
- [ ] **User-Generated Content / Social Interaction / Location Sharing / Digital Purchases**: **No** (no in-app social feeds, chat, location access, or IAP).
- [ ] **Expected IARC Rating**: **Everyone / PEGI 3 / USK 0**.

### Additional App Content Declarations
- [ ] **App Access**: Select **All functionality is available without special access** (no login or demo credentials required).
- [ ] **Ads**: Select **No, my app does not contain ads**.
- [ ] **News / COVID-19 / Financial / Government**: **No**.

---

## 6. Sensitive Permissions & Special Declarations

### A. Accessibility API Declaration (*App Content → Accessibility*)
- [ ] **Is your app an accessibility tool (`isAccessibilityTool`)?** $\rightarrow$ Select **No**.
  - *Critical*: `accessibility_service_config.xml` intentionally omits `android:isAccessibilityTool="true"`. Outloop is a digital wellbeing focus utility, not an assistive tool for users with disabilities. Selecting **Yes** will fail Play review.
- [ ] **Prominent Disclosure Verification**:
  - Verify Onboarding (`OnboardingStep.PRIVACY`) displays a clear, un-obscured screen explaining what data is observed, what is never read, that zero data is collected off-device or shared, and provides **Agree and continue** / **Not now** plus links to **Read the full privacy details** and **Privacy policy**.
  - Verify Settings (*Privacy and permissions → Accessibility permission*) gates unconsented users behind the **Accessibility disclosure** modal (`!disclosureAccepted && !isAccessibilityEnabled`) before redirecting to `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- [ ] **Core Functionality & Justification Copy (for Play Console form)**:
  - **What is the core purpose of your app?**
    > Outloop is an on-device digital wellbeing app that helps users reduce unintentional scrolling in short-form video feeds (such as YouTube Shorts, Instagram Reels, Facebook Reels, Snapchat Spotlight, and Reddit Watch) while preserving normal access to the rest of those apps.
  - **Why does your app require the Accessibility API instead of an alternative API?**
    > Android provides no non-Accessibility API to inspect active UI view IDs and selected tab labels inside third-party apps to distinguish a short-form video feed from messaging, search, or subscriptions, or to immediately present a pause overlay or step back when the feed opens. All UI node inspection happens strictly in memory on-device; no messages, passwords, or media content are read, stored off-device, or shared.
- [ ] **Unedited Demo Video Requirements**:
  Upload a continuous, unedited screen recording (YouTube unlisted link or direct file) demonstrating on an Android device:
  1. Opening Outloop from fresh install → reaching the prominent disclosure screen → tapping **Agree and continue**.
  2. Navigating to **Android Settings → Accessibility → Outloop** and turning it **On**.
  3. Opening YouTube (or Instagram), tapping **Shorts** (or **Reels**), and showing Outloop stepping in with the pause overlay / Back exit.
  4. Returning to Outloop (or notification quick action) and showing **Pause 15 min** / turning protection off.

### B. Foreground Service (`specialUse`) Declaration (*App Content → Foreground service permissions*)
`AndroidManifest.xml` declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `android:foregroundServiceType="specialUse"`, and `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` on `FeedSurgeonService`.

- [ ] **Service Name**: `com.example.service.FeedSurgeonService`
- [ ] **Manifest Subtype Property**:
  > `Continuously monitors accessibility events strictly on-device to exit short-form video feeds even when the app is cleared from recents.`
- [ ] **Play Console Justification Copy**:
  > Outloop requires a persistent `specialUse` foreground service (`FeedSurgeonService`) alongside its user-enabled AccessibilityService so Android and OEM task managers do not terminate active feed interruption when Outloop's UI is swiped from Recents. It displays a silent, low-priority ongoing status notification ("Outloop active · Guarding X selected apps") with one-tap **Pause 15 min** / **Resume** controls so the user always has transparent awareness and control. None of Android 14's standard foreground service types (`dataSync`, `mediaPlayback`, `location`, `health`, `connectedDevice`, etc.) apply to an on-device digital-wellbeing accessibility guard.
- [ ] **Video Proof**: Ensure the continuous demo video clearly shows the persistent notification appearing after enabling the service and continuing to work after swiping Outloop out of Recents.

### C. Battery Optimization Exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
`AndroidManifest.xml` declares `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which is triggered **only** on explicit user tap inside troubleshooting/recovery flows (`OnboardingStep.PROBLEM` and `DiagnosticsScreen`) via `ProtectionHealthHelper`.

- [ ] **Play Console Justification Copy (if requested during review)**:
  > Outloop never prompts for battery optimization exemption on launch or during normal setup. The action is shown only inside troubleshooting when Android reports that Accessibility is enabled in Settings but fails to bind or stay connected due to aggressive OEM background process killers, preventing silent failure of user-configured feed protection.
- [ ] **Contingency Fallback (if Play Review rejects this permission)**:
  Remove `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />` from `AndroidManifest.xml` and update `ProtectionHealthHelper.requestIgnoreBatteryOptimizations`, `OnboardingScreen`, and `DiagnosticsScreen` to launch `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` directly instead of `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

### D. Other Manifest Declarations Check
- [ ] **`POST_NOTIFICATIONS`**: Runtime-requested on Android 13+ via `ActivityResultContracts.RequestPermission()`; non-blocking if denied.
- [ ] **`<queries>`**: Scoped explicitly to the 5 supported packages (`com.google.android.youtube`, `com.instagram.android`, `com.facebook.katana`, `com.snapchat.android`, `com.reddit.frontpage`) plus `Intent.ACTION_MAIN` (`CATEGORY_LAUNCHER`) for user-added custom apps. Does **not** request `QUERY_ALL_PACKAGES`.
- [ ] **Storage & Overlays**: Uses Storage Access Framework (`CreateDocument` / `OpenDocument`) for backups and `TYPE_ACCESSIBILITY_OVERLAY` for the pause shield. Does **not** request `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, or `SYSTEM_ALERT_WINDOW`.

---

## 7. Release Rollout Sequence in Play Console

1. **Internal Testing Track**: Upload `app-release.aab` (`com.suprbuild.outloop`, `1.0.2` / `3`) to Internal Testing first.
2. **Pre-Launch Report**: Wait for Google Play's automated Firebase Test Lab crawl report; verify zero crashes, zero missing-class R8 errors, and no unintended permission alerts.
3. **Closed / Production Review**: Complete all **App Content** declarations (Privacy Policy, Data Safety, Ads, App Access, Target Audience, IARC Rating, Accessibility API + Video, Foreground Service `specialUse` + Video) and submit for review.
