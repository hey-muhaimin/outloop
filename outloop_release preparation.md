# Outloop Release Preparation — Play Compliance, R8 Verification & Signing Readiness

Audit and harden Outloop for Google Play policy compliance first—closing consent-gate and disclosure gaps for `AccessibilityService`, `FOREGROUND_SERVICE_SPECIAL_USE`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`—then verify R8-minified release compilation and deliver a Windows PowerShell upload keystore workflow and Play Console submission checklist.

## User Review & Critical Decisions

> [!IMPORTANT]
> No permanent production keystore will be generated or stored in this cloud workspace. R8 release compilation (`minifyReleaseWithR8` / `bundleRelease`) will be verified using ephemeral `/tmp` signing credentials that are removed immediately after verification, while your permanent upload keystore will be generated locally on your Windows PC.

- **Confirmed Sequencing**: Execute the Google Play policy compliance audit and in-app disclosure/consent fixes first, followed by R8 release build verification and Play Console submission preparation.
- **Confirmed Keystore Strategy**: Generate the permanent `my-upload-key.jks` upload keystore locally on your Windows PC via PowerShell using `keytool` and environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`), keeping `.gitignore` protection intact.
- **Confirmed Permissions Posture**: Retain `AccessibilityService`, `FOREGROUND_SERVICE_SPECIAL_USE`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the manifest, tighten their in-app user triggers and disclosures, and prepare exact Play Console policy justifications.
- **Confirmed Verification & Deliverables**: Verify R8 release shrinking/packaging alongside the full unit test and lint suites, and deliver a complete Play Console submission checklist (Accessibility declaration, Special-Use FGS declaration, Data Safety mapping, and Store Listing checks).

---

## 1. Overview & Core Concept

- **What It Does**: Eliminates the highest-risk Google Play review rejection vectors for a digital-wellbeing accessibility app, validates that release R8/ProGuard shrinking preserves all runtime entry points and serialization adapters, and equips you with exact commands and declaration copy to sign and submit the production App Bundle.
- **Target Audience / Persona**: Release maintainer preparing Outloop `com.suprbuild.outloop` (`1.0.2` / version code `3`) for Google Play Internal Testing and App Review.
- **Key Value**: Avoids policy rejections during Play App Review (especially bypassing Accessibility prominent disclosure from Settings or unclear off-device data claims) and catches R8 shrinking/reflection defects before production release.

---

## 2. User Experience & Compliance Disclosures

- **Key Compliance Flow (Consent-Before-Settings Gate)**:
  1. **First-Run Onboarding**: User reaches the prominent Accessibility disclosure step, reviews what technical screen labels are inspected, what personal content is never read, and an explicit assurance that zero data leaves the device or is shared with third parties, with direct links to full privacy details and the external privacy policy.
  2. **Decline Path ("Not now")**: User can decline and enter the app with protection off and `hasAcceptedAccessibilityDisclosure` remaining `false`.
  3. **Settings Re-Entry Gate**: If a user who previously declined taps the Accessibility permission row in Settings while accessibility is still off, Outloop presents the prominent disclosure modal with affirmative consent ("Agree and continue" / "Not now") *before* redirecting to Android Accessibility Settings—preventing any unconsented bypass path.
  4. **Troubleshooting & Battery Optimization**: Battery optimization exemption remains strictly user-initiated from troubleshooting states (Onboarding connection-problem helper or Diagnostics), never auto-requested on launch.
- **Visual Identity & Copy Posture**:
  - Any new Settings disclosure modal reuses Outloop's existing modal sheet component, tonal card spacing, primary/text button hierarchy, and semantic color tokens—preserving visual consistency without altering core app aesthetics.
  - Disclosure copy remains plain, transparent, non-promotional, and compliant with Play branding rules (`Outloop`, under 30 characters, no emojis or buzzwords).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Enforce Disclosure Gate on Every Accessibility Settings Entry Point**:
  - *Chosen Approach*: Gate Settings-initiated redirects to Android Accessibility Settings on `hasAcceptedAccessibilityDisclosure` (when accessibility is not already enabled), presenting the existing prominent disclosure sheet with an explicit "Agree and continue" action and external Privacy Policy link.
  - *Why*: Google Play reviewers routinely tap "Not now" during onboarding and then trigger Accessibility access from Settings; a direct Settings launch without prior consent is a frequent cause of Accessibility API rejections.
  - *Alternatives Considered*: Relying solely on onboarding disclosure, which leaves Settings as an unguarded bypass path after onboarding is skipped or declined.
- **Decision 2 — Retain All Three Sensitive Permissions with Play Console Justification Pack**:
  - *Chosen Approach*: Keep `AccessibilityService`, `FOREGROUND_SERVICE_SPECIAL_USE`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` as requested, verify manifest metadata and runtime triggers, and prepare exact Play Console App Content declaration text and demo video guidance.
  - *Why*: Real-time short-form feed detection across OEM task-removal behaviors requires Accessibility + a Special-Use foreground service notification, while battery-exemption guidance helps users on aggressive OEMs recover dropped bindings.
  - *Alternatives Considered*: Stripping `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in favor of generic app-settings intents (available later as a one-line fallback if Play Review pushes back on the battery declaration).
- **Decision 3 — Ephemeral `/tmp` R8 Verification vs Local Windows Keystore**:
  - *Chosen Approach*: Verify R8 release minification, resource shrinking, and `.aab` / `.apk` assembly in this workspace using a disposable `/tmp` keystore passed via environment variables (deleted immediately after build verification), while supplying PowerShell commands for generating the real `my-upload-key.jks` locally on Windows.
  - *Why*: Proves release R8 rules, ProGuard keep directives, and `bundleRelease` / `assembleRelease` work end-to-end without storing sensitive production signing material in an ephemeral cloud container.
  - *Alternatives Considered*: Skipping release build verification until local Windows signing, which would leave R8 shrinking defects undetected until the final packaging step.

---

## 4. Technical Architecture & Verification Strategy

### Play Compliance & Release Verification Pipeline

```
┌─────────────────────────────────────────────────────────────────────────┐
│             Phase 2–4: Play Compliance & Release Pipeline               │
└─────────────────────────────────────────────────────────────────────────┘
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         ▼                           ▼                           ▼
┌─────────────────┐       ┌─────────────────────┐     ┌───────────────────┐
│  Play Policy    │       │   Manifest & Code   │     │   R8 / ProGuard   │
│  Consent Hardening      │   Integrity Audit   │     │   Release Verify  │
│                 │       │                     │     │                   │
│ • Gate Settings │       │ • Scoped <queries>  │     │ • Verify keep     │
│   Accessibility │       │   (no QUERY_ALL)    │     │   rules (Room,    │
│   behind        │       │ • SAF backups (no   │     │   Moshi, Service, │
│   disclosure    │       │   storage perms)    │     │   WorkManager)    │
│ • Explicit zero │       │ • JSON-only ruleset │     │ • Disposable /tmp │
│   data sharing  │       │   sync (no DCL)     │     │   release APK/AAB │
│   + policy link │       │ • FGS specialUse    │     │   build & lint    │
└────────┬────────┘       └──────────┬──────────┘     └─────────┬─────────┘
         │                           │                          │
         └───────────────────────────┼──────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────┐
                  │   Test, Lint & Release Verify Gate   │
                  │                                      │
                  │  1. Robolectric test coverage for    │
                  │     Settings disclosure gate         │
                  │  2. :app:testDebugUnitTest           │
                  │  3. :app:lintDebug & lintVitalRelease│
                  │  4. :app:bundleRelease / assembleRel │
                  └──────────────────┬───────────────────┘
                                     ▼
                  ┌──────────────────────────────────────┐
                  │   Local Signing & Play Console Pack  │
                  │                                      │
                  │  • Windows PowerShell keytool steps  │
                  │  • Accessibility & FGS declarations  │
                  │  • Battery exemption justification   │
                  │  • Data Safety & Store checklist     │
                  └──────────────────────────────────────┘
```

### State & Compliance Mapping

- **Disclosure State Propagation**:
  - `hasAcceptedAccessibilityDisclosure` in preferences already persists user consent from Onboarding; Settings receives this state via `MainUiState` and triggers `onAcceptDisclosure` only after explicit confirmation in the Settings disclosure modal before opening `ACTION_ACCESSIBILITY_SETTINGS`.
  - Both Onboarding and Settings disclosures surface: inspected scope (on-screen view IDs and labels in user-selected apps), excluded content (messages, passwords, videos), zero off-device collection/sharing statement, and external Privacy Policy link (`https://suprbuild.com/privacy`).
- **Release R8 & ProGuard Safety**:
  - Existing keep rules protect manifest entry points, Room database/entities, and Moshi JSON adapters; during release verification, we confirm `:app:bundleRelease`, `:app:assembleRelease`, and `:app:lintVitalRelease` succeed with `isMinifyEnabled = true` and `isShrinkResources = true` and zero missing-class warnings.
  - Any disposable `/tmp` keystore created for R8 verification is deleted before finishing the turn, and `build-output/` remains clean/untracked.
- **Local Windows Signing & Play Console Handoff**:
  - Final delivery includes copy-ready PowerShell commands to generate `my-upload-key.jks`, export signing environment variables, build and verify the signed `.aab` (`jarsigner -verify` / `bundletool`), and complete every Google Play Console App Content form (`AccessibilityService` video + disclosure notes, `FOREGROUND_SERVICE_SPECIAL_USE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, and Data Safety).
