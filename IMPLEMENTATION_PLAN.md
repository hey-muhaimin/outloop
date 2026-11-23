# Implementation Plan: Measured Feed Time + Budget Guardrails

**Replaces**: the stale v1.0.0 package-ID rebuild plan (already executed).
**Goal**: Close the gaps found in the realtime-data analysis of Outloop:
1. "Reclaimed" time is user-estimated, not measured (Settings slider default 180s/cut, `SettingsScreen.kt`; sums feed `InterventionEventEntity.reclaimedSeconds`, `MainViewModel` `todaySeconds`).
2. No daily time budgets exist in v1.0.2 — `TargetAppRuleEntity` has no daily limit (the v7.0 tree adds per-app limits).
3. No measured feed time anywhere — DB has only `target_app_rules` + `intervention_events` (v7.0 adds `app_daily_usage`).
4. Insights show estimates, not the truth — time-in-feed is invisible.

Note: analysis was on the v7.0 tree (`/e/Dev/Active/Outloop`); this plan targets the primary app (`/e/Dev/Projects/outloop`, v1.0.2) — every gap above applies there.

---

## 1. Phase 1 — Data: `app_feed_usage` table (no behavior change)

- New entity `AppFeedUsageEntity(packageName, dateKey, trackedSeconds, lastUpdatedMs)` in `data/local/` (mirrors v7.0 `AppDailyUsageEntity` naming).
- DAO methods in `FeedSurgeonDao`: `incrementUsageSeconds(pkg, dateKey, delta, updatedAt)`, `observeDailyUsageForDate(dateKey)`, `getAppFeedUsage(pkg, dateKey)`.
- Room `version = 3 → 4` + `MIGRATION_3_4` (existing pattern: `MIGRATION_2_3`).
- `InterventionRepository`: `addFeedUsageSeconds(...)`, `observeAppFeedUsage(...)`, `getTodayFeedSeconds(...)`, `getTotalTodayFeedSeconds(...)` (reuses `dateKeyForMs`/`startOfTodayMs`).

## 2. Phase 2 — Measure: realtime feed-session accumulation in `FeedSurgeonService`

- Track feed-session start in `ForegroundPackageMonitor.Transition.EnteredMonitoredApp`; flush deltas on `ExitedMonitoredApp`, on `executeIntervention`, and on `onInterrupt`/`onUnbind`/`onDestroy` (elapsedRealtime deltas, flush-on-exit style like `AppSessionTimeTracker` in v7.0 — hooks already present: `foregroundMonitor`, `overlayShield`).
- Future refinement: gate accumulation on `SelectorMatcher.findMatchingSelector(root, rule)` so only time *in the feed* counts.

## 3. Phase 3 — Budget: preferences + service enforcement

- `AppPreferences.globalFeedTimeBudgetMinutes: Int = 0` (0 = off; recommend enabling at 60 min/day) — new `AppPreferences` field, `SettingsDataStore` parse/serialize (existing int pattern), new "Daily feed time budget" row in `SettingsScreen` (0–480 min, minute steps).
- Per-app limits in v1.0.2: add `TargetAppRuleEntity.dailyLimitMinutes: Int = 0` + `hasDailyLimit()`/`dailyLimitSeconds` (v7.0 API mirror) — `InterventionRepository.setDailyLimitMinutes` + UI (Home rows).
- Enforcement in `FeedSurgeonService.onAccessibilityEvent` (near the existing cooldown check, ~line 379):
  - `rule.hasDailyLimit() && todayFeedSeconds >= rule.dailyLimitSeconds` → intervene, `matchedSelector = "daily_limit:NNm"`.
  - `globalFeedTimeBudgetMinutes > 0 && totalTodayFeedSeconds >= budget` → intervene, `matchedSelector = "global_feed_budget:NNm"`.
  - Both respect snooze + `rule.cooldownMs`; use the existing `recordIntervention` path.

## 4. Phase 4 — Report: UI

- `MainViewModel`: `todayFeedSecondsByPackage`, `totalTodayFeedSeconds`, `feedBudgetText` (e.g. "12m / 60m today").
- `HomeScreen`: per-app rows show measured feed time (`InterventionRepository.formatDurationCompact`) + per-app limit badge when set (v7.0 HomeScreen pattern).
- `InsightsScreen`: headline becomes "Time in feeds today" = `totalTodayFeedSeconds` (was reclaimed-sum); streaks unchanged.
- `SettingsScreen`: new global budget row.

## 5. Tests & verification (per `outloop/AGENTS.md`)

- Robolectric unit tests: `InterventionRepository.addFeedUsageSeconds` same-date aggregation, budget checks as a pure function (extract from the service), `AppPreferences` roundtrip.
- `./scripts/verify.sh` — ruleset validation + `:app:testDebugUnitTest` + `:app:lintDebug`.
- `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
