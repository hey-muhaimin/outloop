# Outloop Tonal Bento UI & Feature Implementation Plan

Outloop will be redesigned into a pixel-perfect **Light Tonal Bento** Jetpack Compose interface matching the `Outloop-ui` visual system while wiring all newly introduced features (`Strict mode`, 10-second `Pause Guard` countdown, `Always open` list, and `TikTok` & `X` feed rules) into the live on-device protection engine, Room database, DataStore preferences, and Accessibility overlay shield.

## User Review & Critical Decisions

> [!IMPORTANT] All four product & scope choices from clarification are locked in below and govern this implementation.

- **Confirmed Decision 1 — Wire All New UI Features Cleanly**: `Strict mode` (10-second pause-guard reflection countdown), `Pause Guard` interactive duration & cooldown modal (`5 min`, `15 min`, `30 min`), `Always open` app/surface exclusions, and built-in `TikTok` (`For You feed`) and `X` (`For You timeline`) selector rules will be implemented end-to-end with real state persistence and Accessibility enforcement—while preserving Diagnostics, local JSON Backup/Restore, and History management cleanly inside Settings.
- **Confirmed Decision 2 — Restyle Sub-Screens to Tonal Bento**: `App Detail`, `Manage Apps`, `Privacy / How detection works`, and `Diagnostics` will all be restyled into the exact Light Tonal Bento visual grammar (warm cream canvas, dark forest hero cards, mint/peach tonal tiles, rounded ivory containers, and crisp 48dp+ interactive targets).
- **Confirmed Decision 3 — Update Adaptive Launcher Icon**: The Android adaptive launcher icon (`ic_launcher`, `ic_launcher_round`, and all density raster fallbacks) will be updated to match `Outloop · App icon concepts.png` (two thick dark-forest broken concentric arcs framing a sage-green center dot on a warm cream background).
- **Confirmed Decision 4 — Light Tonal Bento Only**: The app will standardize on the Light Tonal Bento palette from `Outloop-ui` for now; the `Theme` row in Settings remains functional for preference tracking with a clear Light-first experience.

---

## 1. Overview & Core Concept

- **What It Does**: Outloop helps users break mindless short-form video loops without deleting their apps. Using Android's Accessibility Service strictly on-device, it detects when infinite feeds (`Instagram Reels`, `YouTube Shorts`, `Facebook Reels`, `TikTok For You`, `X For You`, `Snapchat Spotlight`, `Reddit Watch`, or custom user rules) open, pauses the scroll with a calm intervention shield, and keeps useful parts of those apps (`DMs`, `Search`, `Messenger`) open.
- **Target Audience**: People who rely on social and video apps for messaging, search, and intentional utility, and want friction-free, privacy-respecting feed boundaries.
- **Key Value**: Calm, tactile clarity—every metric (`loops broken`, `time kept`, `streak`), onboarding step, pause guard countdown, and settings control follows one cohesive Tonal Bento design language with zero visual clutter.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme (Light Tonal Bento)

- **Aesthetic Direction**: Warm organic editorial bento—high-contrast forest-ink typography and deep forest hero cards balanced against soft sage-mint and warm blush-peach tonal containers on a cream paper canvas.
- **Color Palette & Tokens**:
  - *Canvas*: Warm Cream (`#F1EFE7`)
  - *Card Surface (Ivory)*: `#FAF8F2` with subtle hairline (`#1A1E1A` at `7%` alpha)
  - *Hero Dark Bento*: Deep Forest (`#29352D`) with Sage Ring (`#A9C7A1`) and Crisp Ivory Ink (`#FAF8F2`)
  - *Mint Tonal Tile / Pill*: Soft Sage Mint (`#D2E4CE`) with Forest Ink (`#1E2720`)
  - *Peach Tonal Tile / Note*: Warm Blush Peach (`#F0D7CA`) with Forest Ink (`#1E2720`)
  - *Primary Ink*: `#1A221C` | *Secondary Ink*: `#5A635B`
  - *Bottom Dock / Segment Active Fill*: Mint Pill (`#D2E4CE`) over `#FAF8F2` bar
  - *Switch Active Track*: Deep Forest (`#29352D`) with `#FFFFFF` thumb
- **Typography & Hierarchy**:
  - *Display & Screen Titles* (`Today`, `Insights`, `Settings`, `Break the loop.`, Hero KPI `14`, `9h 40m`): Bold `Plus Jakarta Sans` / `Inter` with tight tracking (`-0.6sp`) and `tnum` tabular numerals for live counters and timers.
  - *Body & Subtitles*: `Inter` Regular/Medium (`14.5sp` body, `13sp` row subtitles).
  - *Badges & Pills*: `Inter` SemiBold (`12sp`–`13sp`).
- **Component Styling & Layout**:
  - Generous `22dp`–`28dp` corner radii on bento cards, `14dp` rounded-square app monogram tiles (`#29352D` fill with bold white initial or app icon), `100dp` capsule pills for primary/secondary buttons, and `48dp` minimum touch targets on every clickable element.
  - Edge-to-edge layout (`enableEdgeToEdge()`) with proper status bar and navigation bar insets, plus responsive width clamping (`max = 580.dp`) and side-rail adaptation on tablets/foldables.

### Key User Flows (Screen-by-Screen)

1. **Onboarding (4-Step Flow with Segmented Progress Bar + `Skip`)**:
   - *Step 1 — Welcome*: Full-width dark forest hero card featuring the sage open-loop mark -> `"Break the loop. Keep the apps."` -> explanatory copy -> side-by-side tonal pills (`Feeds blocked` in mint, `Useful parts open` in peach) -> full-width dark pill `Get started`.
   - *Step 2 — Choose Apps*: `"Pick the apps to guard"` -> `"Feeds to block"` + `"X selected"` card containing `Instagram (Reels, Explore)`, `YouTube (Shorts)`, `Facebook (Reels, Feed)`, `TikTok (For You feed)`, `X (For You timeline)`, plus installed status / custom rules, with dark rounded-square checkboxes -> `Back` (outlined pill) + `Continue` (dark pill).
   - *Step 3 — Accessibility*: `"Let Outloop spot feeds"` -> Mint feature card (`Accessibility access / Required for Guard to work`) -> `"How to turn it on"` card with numbered mint circles (`1. Open Accessibility settings`, `2. Choose Outloop under Installed apps`, `3. Switch on Use Outloop`) -> Peach privacy assurance banner (`Used only to recognise feed screens, like Reels and Shorts.`) -> `Not now` + `Open settings` (automatically transitions through live connection verification on return).
   - *Step 4 — All Set*: Dark forest hero card with checkmark inside sage ring -> `"You're all set"` -> Summary card (`Guarded apps`, `Accessibility: Active`, `Schedule: All day`) -> Peach tip card (`Need a break? Pause Guard for 15 minutes from Home.`) -> `Go to Home`.
2. **Home Tab (`Today`)**:
   - *Top Chrome*: Open-loop mark + `Outloop` wordmark on left; live status pill (`Guard on` in mint, or warning/paused tone when paused/setup needed) on right; bold `"Today"` display title below.
   - *Hero Bento Row*: Dark forest card displaying large tabular count (`14` / `loops broken`) on left and large sage open-loop ring on right. Directly underneath: two equal-width bento tiles—Mint (`1h 52m` / `time kept`) and Peach (`6 days` / `streak`).
   - *Conditional Setup / Quiet Hours / Paused Banners*: Clean tonal bento banners appear when Accessibility setup is incomplete, Quiet Hours are active, or Guard is currently paused (with live countdown and `Resume now`).
   - *Guarded Apps Card*: Ivory rounded card titled `"Guarded apps"` listing enabled/installed apps with dark rounded-square initial badges (`I`, `Y`, `F`, `T`, `X`), useful-surface subtitles (e.g., `Reels blocked · DMs open`), and mint `On` / neutral `Off` status pills (tapping a row opens `App Detail`; tapping `"Manage"` or footer opens `Manage Apps`).
   - *Floating Pause Pill*: Right-aligned dark capsule pill above the bottom bar (`Pause guard · 15 min`). Tapping opens the **Pause Guard** modal (with instant pause if Strict mode is off, or a 10-second reflection countdown if Strict mode is on).
3. **Insights Tab**:
   - *Top Chrome & Range Selector*: Shared `Outloop` + `Guard on` top chrome -> `"Insights"` title -> 3-segment pill bar (`Day` | `Week` | `Month`) with mint active highlight.
   - *Dark Forest Chart Hero*: Displays range total (`9h 40m` / `Time kept this week`), delta badge (`+18% vs last week`), and 7 chunky vertical capsule bars (`M T W T F S S` for Week, time-of-day buckets for Day, 4-week buckets for Month) in sage green with the current/selected column highlighted in crisp ivory. Tapping any bar updates the focused breakdown readout.
   - *Twin Bento Stat Tiles*: Mint tile (`86` / `loops broken`) + Peach tile (`11 days` / `best streak` or current streak).
   - *Where Loops Start Card*: Ivory card listing top interrupted feeds (`Instagram Reels`, `YouTube Shorts`, `Facebook Reels`, etc.) with counts on the right and dark forest horizontal progress bars.
4. **Settings Tab**:
   - *Top Chrome*: Shared `Outloop` + `Guard on` top chrome -> `"Settings"` title.
   - *Card 1 — `Guard`*: Rows with mint rounded-square icon badges: `Guard` (master toggle), `Strict mode` (`Wait 10 s before pausing`, toggle), `Schedule` (shows active schedule e.g. `All day` or `Weekdays, 9:00 to 18:00`, opens Quiet Hours / Schedule sheet).
   - *Card 2 — `Apps and access`*: `Guarded apps` (`N apps`, navigates to `Manage Apps`), `Always open` (shows summary e.g. `DMs, Search, Maps and more`, opens interactive `Always open` exclusions sheet), `Accessibility service` (`Needed to spot feeds`, mint `Active` pill or action to enable/inspect).
   - *Card 3 — `General` & Data*: `Theme` (`Light Tonal Bento`, informational/selector sheet), `About Outloop` (`Version 1.0.2`, opens About & Diagnostics/Privacy/Backup drawer or expandable sub-links so `Privacy`, `Diagnostics`, `Local Backup/Restore`, `Daily Target`, `Time Estimate`, and `Clear History` remain cleanly accessible without cluttering the main Bento hierarchy).
5. **Accessibility & In-App Overlays**:
   - *Feed Blocked Overlay (`Loop broken`)*: Darkened scrim framing a crisp ivory modal card -> Dark forest header (`Instagram · Reels` pill + `"Loop broken"` + sage open-loop mark) -> body (`"Reels is guarded, and that is 14 loops broken today. DMs and Search are still open."`) -> Twin bento tiles (`1h 52m / time kept` in mint, `6 days / streak` in peach) -> `Open DMs` (dark primary pill, navigates back to useful app surface) -> `Go to Search` (mint secondary pill, triggers search/home fallback) -> `Pause for 15 min` text button (routes into Pause Guard flow if Strict mode is enabled, or snoozes directly).
   - *Pause Guard Overlay (`Strict mode`)*: Darkened scrim framing ivory modal card -> Dark forest header (`Strict mode` badge + circular sage progress ring counting down `10` -> `0` `seconds`) -> `"Pause Guard?"` -> `"Strict mode asks you to wait 10 seconds first. Still want a break?"` -> 3-segment duration selector (`5 min` | `15 min` | `30 min`) -> `Keep guarding` (dark primary pill) -> `Pause in 7 s` (disabled outlined pill that unlocks into `Pause for 15 min` when countdown hits 0; when Strict mode is off, immediately confirms pause).
6. **Restyled Secondary Screens & Adaptive App Icon**:
   - *Secondary Screens*: `App Detail`, `Manage Apps`, `Privacy / How detection works`, and `Diagnostics` adopt the Light Tonal Bento cards, dark forest hero headers where appropriate, mint/peach status tiles, and unified back navigation.
   - *Adaptive Launcher Icon*: Two thick `#29352D` broken concentric arcs around a `#4E7A5B` sage center dot over `#F5F2E8` warm cream, with matching adaptive XML and all five density square & circular PNG fallbacks.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1 — Strict Mode & Pause Guard Enforcement Scope**:
  - *Chosen Approach*: Wire `Strict mode` (`strictModeEnabled` in DataStore) both inside the app (when tapping `Pause guard · 15 min` on Home) and inside the live `AccessibilityOverlayShield` (when tapping `Pause for 15 min` on a blocked feed). If `Strict mode` is off, pausing applies immediately with the chosen duration (`5 min`, `15 min`, `30 min`); if `Strict mode` is on, the 10-second `Pause Guard` countdown dialog gates the pause button.
  - *Why*: Makes the `Strict mode` toggle in Settings genuinely meaningful everywhere a user tries to lower their guard.
  - *Alternatives Considered*: Showing the countdown only on Home would leave a bypass inside the Accessibility overlay.
- **Decision 2 — Preserving Power-User Settings Alongside Clean Bento Settings**:
  - *Chosen Approach*: Keep the top-level Settings screen faithful to the three-group Tonal Bento layout (`Guard`, `Apps and access`, `General`), while placing `Diagnostics`, `How detection works / Privacy`, `Time estimate`, `Daily pause target`, and `Backup / Restore / Clear history` cleanly inside `About Outloop` / `More controls` or structured sub-sheets so zero existing capabilities are lost.
  - *Why*: Delivers pixel-perfect fidelity to `C · Tonal Bento Settings.png` without breaking any existing user journeys or tests.
- **Decision 3 — Built-In TikTok & X Rules + `Always open` Exclusions**:
  - *Chosen Approach*: Add `com.zhiliaoapp.musically` (`TikTok` — `For You feed`) and `com.twitter.android` (`X` — `For You timeline`) to `ruleset.json`, `FeedSurgeonDatabase.defaultBuiltInRules()`, and `AndroidManifest.xml` `<queries>`, and store `alwaysOpenApps` / allowed sub-surfaces in `SettingsDataStore` so the Accessibility service skips excluded apps and surfaces `Always open` descriptions cleanly in Home and Overlay cards.
  - *Why*: Ensures real package detection and real blocking behavior for all 5 apps pictured in `Outloop-ui` (`Instagram`, `YouTube`, `Facebook`, `TikTok`, `X`).

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Hierarchy

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    DataStore + Room + Bundled Ruleset                   │
│  SettingsDataStore (strictMode, alwaysOpen, schedule, theme, metrics)   │
│  FeedSurgeonDatabase (YouTube, Instagram, Facebook, TikTok, X, ...)     │
└───────────────────────────────┬─────────────────────────────────────────┘
                                │ StateFlow / Flows
                                ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        MainViewModel (MVVM)                             │
│  Aggregates today/week/month time kept, streaks, top loops, & pause     │
│  guard state; exposes toggle/pause/strict-mode/always-open actions      │
└───────────────┬─────────────────────────────────────┬───────────────────┘
                │                                     │
                ▼                                     ▼
┌───────────────────────────────────────┐ ┌───────────────────────────────┐
│        Jetpack Compose UI             │ │  FeedSurgeonService (A11y)    │
│  · Light Tonal Bento Theme & Tokens   │ │  · Matches feed selectors     │
│  · Onboarding (4 steps + Skip)        │ │  · Respects Always Open list  │
│  · Home / Insights / Settings Tabs    │ │  · Renders Tonal Bento        │
│  · Pause Guard Countdown Dialog       │ │    Loop Broken & Strict Mode  │
│  · Restyled Detail/Manage/Privacy/Diag│ │    Overlay Shields            │
└───────────────────────────────────────┘ └───────────────────────────────┘
```

### Data Model & Interactive State Mapping

- **Preferences (`AppPreferences` / `SettingsDataStore`)**:
  - Add `strictModeEnabled: Boolean` (default `true` or `false` configurable in Settings), `alwaysOpenApps: Set<String>` (defaulting to non-feed utility apps/surfaces like `Messages`, `Maps`, `Phone`, plus per-app useful surfaces `DMs`, `Search`, `Messenger`), and `preferredPauseMinutes: Int` (`5`, `15`, `30`).
- **Rules & Database (`FeedSurgeonDatabase` / `ruleset.json`)**:
  - Update `BUILT_IN_PACKAGE_NAMES`, `defaultBuiltInRules()`, `BUNDLED_RULESET_JSON`, and `assets/ruleset.json` to include `TikTok` (`com.zhiliaoapp.musically`) and `X` (`com.twitter.android`), plus useful-part metadata (`DMs open`, `Search open`, `Messenger open`) so every guarded app card and overlay displays accurate contextual subtitles and action buttons.
- **Interactive Handlers**:
  - `HomeScreen`: `Guard on` pill -> Settings/Setup; `Guarded apps` row -> `AppDetailScreen`; `Pause guard · 15 min` pill -> opens `PauseGuardDialog` (immediate duration choice or 10-second `Strict mode` countdown before calling `pauseGloballyForMinutes(minutes)`).
  - `InsightsScreen`: `Day | Week | Month` segmented pill switches chart buckets, hero `Time kept` figure, delta comparison chip, `loops broken` + `best streak` tiles, and `Where loops start` horizontal bar list; tapping bars selects the highlighted bar.
  - `SettingsScreen`: `Guard` & `Strict mode` switches mutate DataStore immediately; `Schedule` opens Quiet Hours / Schedule sheet; `Guarded apps` opens `ManageAppsScreen`; `Always open` opens interactive exclusion picker sheet; `Accessibility service` opens Android Accessibility Settings (gated behind prominent disclosure); `Theme` & `About Outloop` open clean Tonal Bento sheets with access to Diagnostics, Privacy, Backup/Restore, Daily Target, and History reset.
  - `AccessibilityOverlayShield`: Renders the two-part Tonal Bento overlay (`Loop broken` hero + `time kept` & `streak` tiles + `Open DMs` / `Go to Search` / `Pause for 15 min`), and transitions in-place to the 10-second `Pause Guard?` countdown state when `Strict mode` is enabled and the user taps `Pause for 15 min`.
