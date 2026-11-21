# Outloop v1.0.0 Package ID Update & Clean Rebuild

Update Outloop's Android application identifier to `com.suprbuild.outloop.kxmpzq`, clear previous build outputs and caches, verify the entire app through automated tests, and deliver a freshly built debug APK in the `build-output` folder.

## User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed preferences from clarification are locked in below. Review and approve to trigger the clean rebuild.

- **Confirmed Application ID**: `com.suprbuild.outloop.kxmpzq` (keeping the Kotlin source namespace and version `1.0.0` / version code `1` intact).
- **Confirmed Cleanup Scope**: Clear prior build artifacts and caches (including Gradle caches and previous APK outputs) before compiling.
- **Confirmed Artifact Delivery**: Copy the newly built debug APK into `build-output` after verification passes.
- **Platform Signing Preservation**: Existing platform debug/release signing configuration and keystores remain untouched so build compatibility stays valid.

---

## 1. Overview & Core Concept

- **What It Does**: Aligns the compiled Android package identity (`applicationId`) across the manifest, `FileProvider` authorities, `BuildConfig`, and APK metadata to `com.suprbuild.outloop.kxmpzq` while preserving all Outloop v1.0.0 features (on-device feed interruption, quiet hours, daily pause targets, notification quick actions, real app icons, custom app rules, and local JSON backup/restore).
- **Target Audience / Persona**: End users and testers installing a fresh Outloop v1.0.0 package cleanly under the `com.suprbuild.outloop.kxmpzq` package identifier without stale build artifacts.
- **Key Value**: Produces a clean, reproducible debug APK signed consistently with the workspace signing setup and verified end-to-end against unit and UI tests.

---

## 2. User Experience & Visual Design

- **No Visual Regression**: All Outloop screens (Onboarding, Home, Insights, Settings, Your Apps, App Detail, Privacy, and Diagnostics), botanical light/dark themes, and adaptive launcher icons remain unchanged.
- **On-Device System Identity**:
  - Android OS app info, Accessibility Service binding, notification channel ownership, and shared `FileProvider` logs all resolve under `com.suprbuild.outloop.kxmpzq`.
  - Settings (**About Outloop**) and **Diagnostics** display version `1.0.0`.
- **Interactive Feedback & Delivery**: Users installing the APK from `build-output` get a clean first-run setup under the updated package identity with no cached legacy state.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Update `applicationId` Only While Preserving Source `namespace`**
  - *Chosen Approach*: Set Android `applicationId` to `com.suprbuild.outloop.kxmpzq` while keeping the internal Kotlin resource namespace stable.
  - *Why*: Changes the installed app's public Android package identity cleanly without risking broken resource (`R`) imports or Accessibility Service component class lookups.
  - *Alternatives Considered*: Renaming every Kotlin package folder; avoided because it adds unnecessary churn with zero user-facing benefit and risks manifest class-resolution mismatches.
- **Decision 2: Full Build Cache Wipe Before Assembly**
  - *Chosen Approach*: Remove existing build outputs, generated metadata, and Gradle/Kotlin caches prior to assembling and testing.
  - *Why*: Guarantees `BuildConfig`, `AndroidManifest` placeholders (`${applicationId}.fileprovider`), and APK output metadata are regenerated cleanly with zero stale `com.aistudio` or v2.0.0 artifacts.
  - *Alternatives Considered*: Incremental rebuild; passed over to honor clean-build isolation and ensure zero leftover metadata.
- **Decision 3: Preserve Existing Signing Keystore & Configs**
  - *Chosen Approach*: Leave workspace keystore files and Gradle signing blocks completely untouched while clearing generated APKs and build caches.
  - *Why*: Maintains platform signing compatibility across builds and avoids breaking installation or upgrade validation.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────┐
│                  Clean Rebuild & Delivery Flow                 │
├────────────────────────────────────────────────────────────────┤
│  1. Purge stale build outputs, APK folders, & Gradle caches    │
│                            │                                   │
│                            ▼                                   │
│  2. Configure applicationId = "com.suprbuild.outloop.kxmpzq"   │
│     (versionName = "1.0.0", versionCode = 1)                   │
│                            │                                   │
│                            ▼                                   │
│  3. Regenerate BuildConfig + Manifest FileProvider authority   │
│                            │                                   │
│                            ▼                                   │
│  4. Run 34 Robolectric Unit & Compose UI Tests                 │
│                            │                                   │
│                            ▼                                   │
│  5. Assemble Debug APK ──► Copy verified APK to build-output   │
└────────────────────────────────────────────────────────────────┘
```

- **Configuration & Manifest Binding**: Updating `applicationId` automatically propagates to `BuildConfig.APPLICATION_ID`, Android `FileProvider` authority (`${applicationId}.fileprovider`), and generated APK `output-metadata.json`.
- **Verification Sequence**: Execute full compilation, run all 34 JVM/Robolectric unit and Compose UI tests, verify generated APK metadata confirms `com.suprbuild.outloop.kxmpzq` (`1.0.0`), and copy the resulting binary to `build-output`.
