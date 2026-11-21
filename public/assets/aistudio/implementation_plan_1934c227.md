# Stillspace Brand, Design System & Habit UX Plan

Transforms the app from its clinical "Feed Surgeon" identity into **Stillspace**, a calm productivity and habit-building companion powered by a Warm Obsidian, Electric Amber, and Sage design system, focus-first copy, and a minimal geometric ring adaptive app icon.

## User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed brand, visual, copy, and icon choices from clarification alongside platform constraints:

- **Brand Identity (Confirmed)**: Rename user-facing branding from **Feed Surgeon** to **Stillspace** across the launcher label, platform metadata, onboarding walkthrough, accessibility service disclosure, and foreground notification.
- **Design System Mood (Confirmed)**: Replace clinical Mint Teal surfaces with **Warm Obsidian** backgrounds (`#11100E`), warm stone glass cards (`#191714` / `#221F1B`), **Electric Amber** primary actions (`#F5A623`), and **Calm Sage** habit/streak accents (`#88C09E`).
- **Copy & Metric Framing (Confirmed)**: Reframe clinical terms (`cuts`, `surgeon`, `blocker health`, `protected`) into focus-first productivity language (**Focus Wins**, **Time Reclaimed**, **Focus Streak**, **Intentional Passes**, **Rule Sync Status**).
- **Adaptive App Icon (Confirmed)**: Generate a new adaptive launcher icon centered on a **minimal geometric ring breaking an endless play loop** in Electric Amber and Sage over Warm Obsidian, paired with a matching monochrome status-bar notification icon.
- **Technical Continuity Guardrail**: Existing Android `applicationId`, Room database schema, Compose `testTag` identifiers, and Accessibility Service class names remain unchanged internally so updates install seamlessly without data loss or broken tests.

---

## 1. Overview & Core Concept

- **What It Does**: Helps users build intentional digital habits by quietly stepping in when short-form video loops open, reframing every interruption as a positive **Focus Win** rather than a clinical restriction.
- **Target Audience / Persona**: Mindful professionals, students, and everyday smartphone users looking to protect deep work and calm evening routines without deleting their favorite apps.
- **Key Value**: Replaces sterile, medical-feeling friction with an inviting, warm productivity studio aesthetic that celebrates focus streaks, reclaimed hours, and deliberate breaks.

---

## 2. User Experience & Visual Design

- **Key User Flows & Copy Transformation**:
  1. **First-Launch Onboarding & Prominent Disclosure**: Introduces **Stillspace** with warm obsidian glass chips (`Instant Loop Break`, `100% On-Device`, `5-Min Mindful Pass`), updates Accessibility disclosure text to reference Stillspace instead of Feed Surgeon, and aligns all four steps around building better attention habits while retaining Play-compliant consent and decline controls.
  2. **Home Focus Hub**: Header displays the **Stillspace** title and dynamic habit status pill (`Focus Active`, `On a Break`, `Finish Setup`, `Reconnect`, `Pick Apps`). The hero ring gauge highlights **Focus Wins today** and **Time Reclaimed**, backed by an Electric Amber primary progress sweep and Calm Sage streak/status highlights.
  3. **Insights & Habit Progress**: Displays **Focus Wins this week**, a **Focus Streak** header badge, tappable 7-day momentum bars, **Time Reclaimed**, **Intentional Passes**, **Focus Breakdown by App**, and a transparent explanation of how reclaimed focus time is estimated.
  4. **App Focus Rules & Settings**: Renames `"Blocker health"` to **Rule Sync Status**, frames intervention modes as **Mindful Pause Screen** vs. **Quick Return**, updates break controls to **Take an Intentional Break**, and refreshes the foreground service notification to **"Stillspace active"**.
  5. **On-Device Mindful Pause Overlay**: Refreshes the system accessibility overlay (`AccessibilityOverlayShield`) and its in-app interactive preview (`MindfulPausePreviewDialog` + `LiquidShieldOverlay`) with Warm Obsidian glass surfaces, Electric Amber primary CTA, and a gently breathing Sage + Amber ambient orb.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Warm Obsidian productivity studio — grounded charcoal-stone translucency, tactile specular rim borders, zero harsh clinical greens.
  - *Color Palette & Mood*:
    - Canvas & Glow: Warm Obsidian (`#11100E`), Ambient Umber Glow (`#1E1A14`)
    - Glass Surfaces & Borders: Warm Glass (`#D9191714`), Elevated Glass (`#E6221F1B`), Dock Glass (`#EB181512`), Stone Hairline (`#2C2823`), Soft Ivory Specular Rim
    - Primary Action (**Electric Amber**): `#F5A623` (Container `#33240D`, Rim `#8F6018`, On-Amber Ink `#1A1104`)
    - Habit & Streak Accent (**Calm Sage**): `#88C09E` (Container `#182920`, Rim `#3A694F`)
    - Attention & Pause Accents: Soft Gold (`#E59A38`) and Muted Terracotta (`#D96B5A`)
    - Typography Colors: Warm Alabaster (`#F5F2EC`), Warm Slate (`#9C968D`), Muted Ash (`#68625A`)
  - *Typography & Hierarchy*: Keeps `Plus Jakarta Sans` for crisp, approachable headings and `JetBrains Mono` for tabular counters, version tags, and latency/log metrics, with refined contrast and letter-spacing across alabaster and slate text tiers.
  - *Adaptive App Icon & Artwork*:
    - Launcher icon foreground depicts a crisp Electric Amber and Sage broken-loop geometric ring slicing an endless play triangle on solid `#11100E` Warm Obsidian, exported into adaptive XML layers and square/round density PNGs (`mdpi` through `xxxhdpi`).
    - Updates the monochrome status-bar notification vector to the matching broken-loop ring glyph and refreshes the empty-state insights illustration to harmonize with the Warm Obsidian + Amber/Sage palette.

- **Interactive Feedback & Motion**:
  - Preserves tactile `0.98f` spring press compression, sliding pill dock/rail springs, staggered Bento card reveals, and the breathing radial orb animation — re-tuned to transition smoothly between Calm Sage and Electric Amber luminescence.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Electric Amber Primary vs. Calm Sage Secondary Hierarchy**
  - *Chosen Approach*: Assign Electric Amber (`#F5A623`) to primary CTAs, active nav pills, and the hero ring gauge sweep, while reserving Calm Sage (`#88C09E`) for active status indicators, streaks, and breathing orb equilibrium.
  - *Why*: Amber creates unmistakable energy and focal hierarchy on dark stone surfaces without feeling medical or sterile, while Sage delivers the restful habit reinforcement expected from a brand named Stillspace.
  - *Alternatives Considered*: Using Sage as the sole accent—rejected because low-saturation green alone lacks punch on primary CTAs and risks looking too close to the previous mint-teal palette.

- **Decision 2: User-Facing Rebrand with Stable Internal Identifiers**
  - *Chosen Approach*: Update every user-visible string, notification title, platform metadata title, Gradle project name, theme tokens, and launcher icon to **Stillspace**, while keeping internal Kotlin package names, Room entities, and `applicationId` intact.
  - *Why*: Guarantees seamless upgrades over existing installs, preserves persisted user streaks/preferences, and keeps Play Store / accessibility service configuration uninterrupted.
  - *Alternatives Considered*: Renaming Kotlin packages and Room database names—rejected as high-risk churn with zero user-facing benefit.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```text
┌───────────────────────────────────────────────────────────────────────────┐
│                    Stillspace Identity & Design System                    │
│  App Name ("Stillspace") │ Platform Metadata │ Adaptive Broken-Ring Icon  │
└──────────────┬─────────────────────────┬──────────────────────┬───────────┘
               │                         │                      │
               ▼                         ▼                      ▼
┌──────────────────────────┐ ┌───────────────────────┐ ┌────────────────────┐
│  Warm Obsidian Theme     │ │ Focus-First UI Copy   │ │ Native System UI   │
│  - Canvas #11100E        │ │ - Focus Wins / Streak │ │ - Overlay Card     │
│  - Glass #191714/#221F1B │ │ - Time Reclaimed      │ │   (Amber CTA +     │
│  - Electric Amber #F5A623│ │ - Intentional Passes  │ │    Sage Orb)       │
│  - Calm Sage #88C09E     │ │ - Rule Sync Status    │ │ - Foreground Notif │
└──────────────┬───────────┘ └───────────┬───────────┘ └─────────┬──────────┘
               │                         │                       │
               └─────────────────────────┼───────────────────────┘
                                         ▼
              ┌────────────────────────────────────────────────────┐
              │   Compose Screens, Motion Visuals & Navigation     │
              │   OnboardingScaffold -> Home -> Insights -> Rules  │
              └────────────────────────────────────────────────────┘
```

- **Data Model & State**: Reuses existing Room database tables, DataStore preference keys, WorkManager ETag ruleset sync, and `MainViewModel` state flows without breaking persistence migrations.
- **Interactive Component & State Mapping**:
  - Theme tokens propagate automatically across `SurgeonCard` (warm glass + specular rim), `FloatingPillNavBar` & `ObsidianSideRail` (Amber active pill + Warm Obsidian dock), `ProtectionRingGauge` (Amber-to-Sage progress arc), and `LiquidShieldOverlay` (Amber + Sage breathing orb).
  - Native `AccessibilityOverlayShield` mirrors the exact same Warm Obsidian (`#11100E` / `#191714`), Electric Amber (`#F5A623`), and Calm Sage (`#88C09E`) colors so the transition between host apps, system overlay, and in-app dashboard feels completely unified.
  - String resource updates flow consistently into onboarding consent, Home status summaries, Insights labels, Settings diagnostics, and system accessibility descriptions.
- **Verification Strategy**: Update affected string assertions in the Robolectric UI/unit test suites, verify adaptive icon resources and PNG headers, compile both debug (`compile_applet`) and release minification (`minifyReleaseWithR8`), and pass the full `:app:testDebugUnitTest` suite.
