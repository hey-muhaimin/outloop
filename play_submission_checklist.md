# Outloop — Google Play Console Submission Checklist

**Application Identity**
- **App Name**: `Outloop`
- **Application ID (`applicationId`)**: `com.suprbuild.outloop`
- **Internal Namespace (`namespace`)**: `com.example`
- **Version**: `17.0` (`versionCode = 17`)
- **SDK Levels**: `minSdk = 24` · `targetSdk = 36`
- **Release Track**: Closed Testing → **Production**
- **Privacy Policy URL**: `https://suprbuild.com/privacy`

---

## 1. Pre-Upload Build & Integrity Checklist

- [ ] **Clean Workspace**: Confirm `git status` shows no tracked `*.jks`, `*.keystore`, `.env`, or `build-output/` artifacts.
- [ ] **Verification Suite Passed**:
  - `./scripts/verify.sh --release` *(runs `sync-ruleset.sh --validate-only`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:lintVitalRelease`, and `:app:reportReleaseComposeMappingErrors`)*
  - PowerShell equivalent: `.\gradlew.bat :app:testDebugUnitTest :app:lintVitalRelease :app:reportReleaseComposeMappingErrors`
- [ ] **Signed Release Bundle Built**: Build `app-release.aab` via `./scripts/build-release.sh` (or `.\gradlew.bat :app:bundleRelease` / AI Studio **Generate Signed AAB**) with `isMinifyEnabled = true` and `isShrinkResources = true`.
- [ ] **Signature Verified**: Confirm `jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab` reports `jar verified.` and matches your Play upload key SHA-256 fingerprint.
- [ ] **R8 Mapping Saved**: Retain `app/build/outputs/mapping/release/mapping.txt` (Play Console automatically attaches bundle mapping files or accepts manual upload for deobfuscation).
- [ ] **Play App Signing Enrolled**: Confirm **Google Play App Signing** is active under *Setup → App integrity*.
- [ ] **Real-Device Smoke Test (Release Build)**: Install the minified release build on a physical Android device and verify:
  - Onboarding flow (`WELCOME` → `CHOOSE_APPS` → `ALLOW_ACCESS` with **Accessibility disclosure** modal `"Agree and continue"` vs `"Not now"` → `CHECKING` / `PROBLEM` → `READY`).
  - Settings → **Accessibility service** gates unconsented users behind the **Accessibility disclosure** modal before opening `ACTION_ACCESSIBILITY_SETTINGS`.
  - Enabling Outloop in Android Accessibility Settings starts `FeedSurgeonService` and displays the ongoing silent notification (`"Outloop active"` with **Pause 15 min** / **Resume**).
  - Opening YouTube Shorts, Instagram Reels, or Facebook Reels triggers the refined pause overlay (inline **Home | Exit** dock + full-width **Pause** button), where **Home** returns to the guarded app's own Home tab and **Exit** navigates to device Home.
  - Testing **Export local backup**, **Restore from backup**, and **Open app battery settings** from Diagnostics.

---

## 2. Store Listing & App Metadata Compliance

- [ ] **App Title**: `Outloop` (7 characters; complies with Google Play's ≤ 30-character cap; no emojis, ALL CAPS, or promotional buzzwords such as "Free", "Best", or "#1").
- [ ] **Short & Full Descriptions**:
  - Accurately describe Outloop as an on-device digital wellbeing tool that detects and interrupts short-form video feeds (YouTube Shorts, Instagram Reels, Facebook Reels, TikTok For You, X For You, Snapchat Spotlight, and Reddit Watch) while keeping access to the rest of those apps.
  - Do **not** claim affiliation with or endorsement by Google, YouTube, Meta, Instagram, Facebook, TikTok, X, Snapchat, or Reddit.
  - Do **not** make unverified medical or clinical addiction-treatment claims.
- [ ] **Graphics Assets**:
  - High-res icon (`512 × 512` PNG) matching the adaptive launcher icon (`ic_launcher`).
  - Feature graphic (`1024 × 500` PNG/JPEG).
  - At least 2 phone screenshots (Home, Onboarding Accessibility Disclosure, Pause Overlay, Insights, Settings) showing real app UI without misleading system-notification imitation or prohibited buzzwords.
- [ ] **Category & Contact Details**:
  - Primary Category: **Productivity** (or **Health & Fitness**).
  - Developer Email / Website / Privacy Policy URL configured and monitored for Google Play review notices.

---

## 3. Privacy Policy Requirements (`https://suprbuild.com/privacy`)

Before submitting to Production, confirm the live HTTPS page at `https://suprbuild.com/privacy` returns HTTP `200` (no login or geo blocks) and aligns with the in-app **Privacy** screen:

- [ ] **App & Developer Identification**: Explicitly names **Outloop** (`com.suprbuild.outloop`) and lists an active developer support email.
- [ ] **AccessibilityService Data Access & Purpose**:
  - Explains that Outloop uses Android's `AccessibilityService` (`FeedSurgeonService`) solely to inspect technical on-screen view IDs (`flagReportViewIds`), active package names, and navigation tab labels (`flagRetrieveInteractiveWindows`, `canRetrieveWindowContent="true"`) in apps the user explicitly selects.
  - Explains that this is used strictly on-device to recognize when a short-form video feed opens, show the pause overlay or return to the app's Home tab, and track local session limits.
- [ ] **Strict Non-Collection & Non-Sharing Statement**:
  - Explicitly states that Outloop does **not** read, record, upload, sell, or share private messages, passwords, form inputs, media streams, or personal identifiers.
  - Explicitly states that zero Accessibility data leaves the device.
- [ ] **On-Device Storage & User-Initiated Exports**:
  - Documents that app rules, preferences, quiet hours, and interruption counts remain strictly on-device (Room SQLite + Jetpack DataStore), with Android cloud Auto Backup disabled (`android:allowBackup="false"`).
  - Documents that optional troubleshooting logs (`accessibility_events_log.txt`) are disabled by default (`eventCaptureEnabled = false`), capture only structural package/view-ID/tab metadata locally, and are shared only when the user invokes the Android share sheet (`FileProvider`) or cleared in Diagnostics.
  - Documents that JSON backups are written/read solely at user-selected locations via Android's Storage Access Framework (`CreateDocument` / `OpenDocument`).
- [ ] **Optional Remote Ruleset Checks**:
  - Matches the in-app **Optional rule checks** disclosure card: when *Auto-update feed rules* is enabled in Diagnostics, Outloop performs read-only HTTPS `HEAD`/`GET` checks (using standard `ETag` conditional headers) to fetch public selector definitions (`ruleset.json`) with zero personal, usage, or screen data uploaded.
- [ ] **Revocation & Deletion Instructions**:
  - Explains how users can revoke Accessibility access anytime (*Android Settings → Accessibility → Outloop*) and erase all local data (*Settings → Clear activity history* and *Diagnostics → Clear log*).

---

## 4. Data Safety Questionnaire (*App Content → Data safety*)

Outloop includes zero third-party analytics, crash reporting, ad networks, authentication, or cloud database SDKs.

| Play Console Question | Recommended Answer | Technical Basis in Codebase |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **No** | All state, preferences, rules, and interruption events remain local in Room/DataStore (`android:allowBackup="false"`). |
| **Optional Ruleset Fetch (`RulesetRepository`)** | Does not trigger "Data collected" | Unauthenticated one-way HTTPS `HEAD`/`GET` to `gist.githubusercontent.com/.../ruleset.json`; zero user/device identifiers or usage data transmitted. |
| **Opt-in Troubleshooting Log (`AccessibilityEventLogger`)** | Local-only / user-initiated share | Disabled by default (`eventCaptureEnabled = false`). Written locally only if toggled on, and exported only when the user manually triggers `Intent.createChooser` via `FileProvider`. |
| **JSON Backup Export/Restore (`LocalBackupManager`)** | Local-only / user-initiated file picker | Uses SAF `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT`; Outloop never transmits backup files to developer servers. |

---

## 5. App Content Rating (IARC) & Target Audience Settings

### Target Audience & Content (*App Content → Target audience and content*)
- [ ] **Target Age Groups**: Select **18 and over** (or **13–15, 16–17, 18 and over**).
- [ ] **Exclude Under-13 Children**: Do **NOT** select any age band under 13 (avoids Google Play Families Policy restrictions on cross-app `<queries>` and `AccessibilityService`).
- [ ] **Appeal to Children**: Answer **No**.

### Content Rating Questionnaire (IARC) & Additional Declarations
- [ ] **Category**: Utility, Productivity, Communication, or Other (Non-Game).
- [ ] **Violence / Sexuality / Profanity / Substances / Gambling / UGC / Social / Location / IAP**: **No** across all categories.
- [ ] **App Access**: **All functionality is available without special access** (no login required).
- [ ] **Ads / News / COVID-19 / Financial / Government**: **No**.

---

## 6. Sensitive Permissions & Special Declarations

### A. Accessibility API Declaration (*App Content → Accessibility*)
- [ ] **Is your app an accessibility tool (`isAccessibilityTool`)?** → Select **No**.
  - *Critical*: `accessibility_service_config.xml` intentionally omits `android:isAccessibilityTool="true"`. Outloop is a digital wellbeing focus utility, not an assistive tool for users with disabilities.
- [ ] **Prominent Disclosure Verification**:
  - Onboarding (`OnboardingStep.ALLOW_ACCESS` → **Open settings**) gates navigation behind the **Accessibility disclosure** modal (`!disclosureAccepted && !isAccessibilityEnabled`) with explicit **Agree and continue** / **Not now** buttons and a link to **How detection works** (`PrivacyScreen`).
  - Settings (**Apps and access → Accessibility service**) applies the same unconsented modal gate before launching `Settings.ACTION_ACCESSIBILITY_SETTINGS`.
- [ ] **Core Functionality & Justification Copy (Play Console form)**:
  - **Core purpose**:
    > Outloop is an on-device digital wellbeing app that helps users pause intentional or unintentional scrolling in short-form video feeds (YouTube Shorts, Instagram Reels, Facebook Reels, TikTok For You, X For You, Snapchat Spotlight, and Reddit Watch) while keeping full access to the rest of those apps.
  - **Why Accessibility API is required**:
    > Android provides no alternative API to inspect active UI view IDs and selected tab labels inside third-party apps to distinguish a short-form video feed from search, messaging, or home feeds, or to immediately present a pause overlay or navigate back to the app's Home tab when the feed opens. All UI node inspection runs strictly in memory on-device; no messages, passwords, or video content are read, stored off-device, or shared.
- [ ] **Unedited Demo Video Requirements**:
  Upload a continuous, unedited recording showing:
  1. Opening Outloop → reaching the prominent **Accessibility disclosure** modal → tapping **Agree and continue**.
  2. Turning on **Outloop** under **Android Settings → Accessibility**.
  3. Opening a guarded app (e.g., YouTube or Instagram), entering **Shorts** or **Reels**, and triggering the pause overlay (showing **Home**, **Exit**, and **Pause**).
  4. Using **Pause 15 min** / **Resume** or returning to Outloop to toggle protection.

### B. Foreground Service (`specialUse`) Declaration (*App Content → Foreground service permissions*)
`AndroidManifest.xml` declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `android:foregroundServiceType="specialUse"`, and `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` on `FeedSurgeonService`.

- [ ] **Service Name**: `com.example.service.FeedSurgeonService`
- [ ] **Manifest Subtype Property (Exact Match)**:
  > `Runs alongside Android Accessibility Service strictly on-device to recognize short-form video feeds in user-selected apps, show pause overlays or return to the app Home tab, and display ongoing pause/resume status controls.`
- [ ] **Play Console Justification Copy**:
  > Outloop requires a persistent `specialUse` foreground service (`FeedSurgeonService`) alongside its user-enabled AccessibilityService so Android and OEM task managers do not suspend feed detection when Outloop's UI is swiped from Recents. It displays a silent ongoing notification (`Outloop active`) with one-tap **Pause 15 min** / **Resume** controls for transparent user awareness. Standard Android 14+ FGS types (`dataSync`, `mediaPlayback`, `location`, `health`, `connectedDevice`, etc.) do not cover an on-device digital wellbeing accessibility monitor.

### C. Battery Optimization Permission (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — Intentionally Omitted
- **Status**: **Compliant (Not Declared)**.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` has been removed from `AndroidManifest.xml` to comply with Google Play Production policy restrictions.
- Troubleshooting flows in `OnboardingScreen` and `DiagnosticsScreen` call `ProtectionHealthHelper.openBatterySettings(context)`, which opens standard Android **App Info / Battery settings** (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, falling back to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) instead of requesting the restricted direct whitelist intent.

### D. Other Manifest Declarations Check
- [ ] **`POST_NOTIFICATIONS`**: Runtime-requested on Android 13+ via `ActivityResultContracts.RequestPermission()`; non-blocking if denied.
- [ ] **`<queries>`**: Scoped explicitly to the 7 supported packages (`com.google.android.youtube`, `com.instagram.android`, `com.facebook.katana`, `com.zhiliaoapp.musically`, `com.twitter.android`, `com.snapchat.android`, `com.reddit.frontpage`) plus `Intent.ACTION_MAIN` (`CATEGORY_LAUNCHER`) for user-selected apps. Does **not** request `QUERY_ALL_PACKAGES`.
- [ ] **Storage & Overlays**: Uses Storage Access Framework (`CreateDocument` / `OpenDocument`) for JSON backups, `FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION` for user-initiated diagnostic log sharing, and `TYPE_ACCESSIBILITY_OVERLAY` for the pause overlay. Does **not** request `READ_EXTERNAL_STORAGE`, `READ_MEDIA_*`, or `SYSTEM_ALERT_WINDOW`.

---

## 7. Production Rollout Checklist (Closed Testing → Production)

1. Confirm all **Closed Testing** feedback and Pre-Launch Report checks show zero crashes and zero R8 missing-class issues.
2. Upload/promote the signed `app-release.aab` (`com.suprbuild.outloop`, `versionCode = 17`, `versionName = "17.0"`) in **Production → Create new release** (or **Closed testing → Create new release**).
3. **Remove Shadowed / Retained Lower Version Codes (CRITICAL)**:
   - Outloop builds a **single universal bundle** (`minSdk = 24`, `targetSdk = 36`, no ABI/density `splits`, no product flavors).
   - Under **App bundles** → **New app bundles** and **Previous release → Included**, verify that **only `versionCode = 17`** is present.
   - Remove every lower version code (`5, 6, 8, 9, 11, 12, 13, 14, 15, 16`) from **New app bundles** (click the three dots / trash **Remove** icon on each) and remove `7 (7.0)` (or any retained bundle) from **Previous release → Included** (or click **Discard release** and start a clean release uploading only `versionCode = 17`).
4. Verify **App Content** declarations (Accessibility API + demo video, Foreground Service `specialUse` + demo video, Data Safety, Privacy Policy, Target Audience, IARC Rating) are complete and up to date.
5. Add release notes ("What's new"):
   - Refined Bio-Mint design across Today, Insights, Settings, and the 2-row pause overlay (**Home**, **Exit**, and **Pause**).
   - Daily Accessibility pause summary chart in Insights backed by on-device Room logs.
   - Improved **Home** navigation to return directly to the guarded app's Home tab from Shorts or Reels.
   - Updated Privacy and Accessibility disclosures for clearer on-device transparency and Play compliance.
6. Select target countries/regions (and staged rollout percentage if desired) and submit for review.
