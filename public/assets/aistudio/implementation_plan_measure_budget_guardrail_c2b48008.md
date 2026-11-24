# Implementation Plan: Measured Feed Time + Budget Guardrails

This plan implements accurate measured feed time tracking, per-app daily time limits, global daily feed time budgets, and automated Pause Guard enforcement when limits are reached.

## User Review & Critical Decisions

> [!IMPORTANT]
> Confirmed preferences from your clarifications:
> - **Default Global Daily Feed Time Budget**: 60 minutes per day.
> - **Limit Enforcement**: Intervene with Pause Guard overlay and daily limit badge when reached.
> - **UI Presentation**: Breakdown per app on Home and total feed time in Insights.

---

## 1. Overview & Core Concept

- **What It Delivers**:
  1. **Measured Feed Time**: Replaces user-estimated reclaimed time with precise active feed-session duration tracked via Accessibility Service lifecycle events (`AppFeedUsageEntity` Room table).
  2. **Daily Limits & Global Budgets**: Per-app daily limit (`dailyLimitMinutes` in `TargetAppRuleEntity`) and global daily feed time budget (`globalFeedTimeBudgetMinutes` in `AppPreferences`).
  3. **Automated Enforcement**: When daily limits or global budgets are reached, `FeedSurgeonService` automatically triggers the Pause Guard overlay.
  4. **Updated UI**: Home screen shows measured feed time and limit badges; Insights screen showcases total measured feed time in feeds today.
- **Target Audience**: Outloop users wanting precise accountability and strict guardrails against infinite scroll loops.

---

## 2. Planned Architecture & Changes

### A. Data Layer (`data/local/`)
1. **New Entity**: `AppFeedUsageEntity`
   - Fields: `packageName` (PrimaryKey), `dateKey` (String, e.g., "2026-11-23"), `trackedSeconds` (Long), `lastUpdatedMs` (Long).
2. **DAO Updates (`FeedSurgeonDao`)**:
   - `incrementUsageSeconds(pkg: String, dateKey: String, delta: Long, updatedAt: Long)`
   - `observeDailyUsageForDate(dateKey: String): Flow<List<AppFeedUsageEntity>>`
   - `getAppFeedUsage(pkg: String, dateKey: String): AppFeedUsageEntity?`
3. **Database Version Migration**:
   - Upgrade database version from 3 to 4 with `MIGRATION_3_4` creating `app_feed_usage` table.
4. **Repository Updates (`InterventionRepository`)**:
   - Add methods to record feed usage, observe daily usage flow, and query today's feed seconds per app and globally.

### B. Service & Tracking (`service/`)
1. **Session Accumulation**:
   - Track active feed sessions when entering monitored apps or detecting scroll/feed containers.
   - Flush elapsed delta seconds on app exit, rule match, or service lifecycle cleanup (`ForegroundPackageMonitor`).
2. **Budget & Limit Enforcement (`FeedSurgeonService`)**:
   - In accessibility event evaluation, check if `todayFeedSeconds >= rule.dailyLimitSeconds` or `totalTodayFeedSeconds >= globalBudgetSeconds`.
   - If exceeded, trigger intervention with matched selector `daily_limit:NNm` or `global_feed_budget:NNm`.

### C. Preferences & UI (`ui/` & `data/local/SettingsDataStore`)
1. **Preferences**:
   - Add `globalFeedTimeBudgetMinutes: Int = 60` to `AppPreferences` and `SettingsDataStore`.
   - Add `dailyLimitMinutes: Int = 0` to `TargetAppRuleEntity`.
2. **Settings Screen**:
   - Add a global feed budget picker row (0 to 480 minutes).
3. **Home Screen**:
   - Display measured feed time per app (e.g. "18m / 60m today") and limit badges.
4. **Insights Screen**:
   - Update headline metric to "Time in feeds today" using measured total feed time.

---

## 3. Verification Strategy

1. **Compilation**: Run `compile_applet` to verify Kotlin code, database migrations, and UI composables compile successfully.
2. **Robolectric Tests**:
   - Test `InterventionRepository` feed usage accumulation and date key parsing.
   - Test budget and daily limit checking logic.
   - Run `gradle :app:testDebugUnitTest`.
