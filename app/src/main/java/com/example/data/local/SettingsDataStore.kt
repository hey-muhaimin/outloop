package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.InterventionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

private val Context.feedSurgeonDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "outloop_prefs"
)

enum class AppearanceMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    companion object {
        fun fromStored(raw: String?): AppearanceMode =
            entries.firstOrNull { it.name == raw || it.label.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

data class AppPreferences(
    val disclosureAccepted: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val autoUpdateRules: Boolean = true,
    val eventCaptureEnabled: Boolean = false,
    val rulesetVersion: String = DEFAULT_RULESET_VERSION,
    val rulesetEndpointUrl: String = DEFAULT_GIST_URL,
    val rulesetEtag: String = "",
    val lastRulesetCheckMs: Long = 0L,
    val reclaimedSecondsPerCut: Int = 180,
    val showInterventionBanner: Boolean = true,
    val cachedRulesetJson: String = BUNDLED_RULESET_JSON,
    val globalPauseUntilMs: Long = 0L,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val defaultInterventionMode: InterventionMode = InterventionMode.OVERLAY,
    val hapticFeedbackEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = DEFAULT_QUIET_START_MINUTES,
    val quietEndMinutes: Int = DEFAULT_QUIET_END_MINUTES,
    val quietOverrideUntilMs: Long = 0L,
    val dailyBudgetCuts: Int = 0,
    val strictModeEnabled: Boolean = false
) {
    fun isGloballyPaused(nowMs: Long = System.currentTimeMillis()): Boolean =
        globalPauseUntilMs == Long.MAX_VALUE || globalPauseUntilMs > nowMs

    fun isInQuietHours(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!quietHoursEnabled) return false
        if (quietOverrideUntilMs > nowMs) return false
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return isWithinQuietWindow(minuteOfDay, quietStartMinutes, quietEndMinutes)
    }

    fun nextQuietWindowEndMs(nowMs: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, quietEndMinutes / 60)
            set(Calendar.MINUTE, quietEndMinutes % 60)
        }
        if (cal.timeInMillis <= nowMs) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    companion object {
        const val DEFAULT_RULESET_VERSION = "v2026.11.18"
        const val DEFAULT_GIST_URL =
            "https://gist.githubusercontent.com/hey-muhaimin/1ebadd94effdba1255d42661ef218fc9/raw/ruleset.json"
        const val AUTO_SYNC_INTERVAL_MS = 6L * 60L * 60L * 1000L
        const val DEFAULT_QUIET_START_MINUTES = 22 * 60
        const val DEFAULT_QUIET_END_MINUTES = 7 * 60

        fun isWithinQuietWindow(
            minuteOfDay: Int,
            startMinutes: Int,
            endMinutes: Int
        ): Boolean {
            val m = minuteOfDay.coerceIn(0, 1439)
            val s = startMinutes.coerceIn(0, 1439)
            val e = endMinutes.coerceIn(0, 1439)
            if (s == e) return false
            return if (s < e) {
                m in s until e
            } else {
                m >= s || m < e
            }
        }

        fun formatMinutesOfDay(minutes: Int): String {
            val normalized = minutes.coerceIn(0, 1439)
            val hour24 = normalized / 60
            val minute = normalized % 60
            val amPm = if (hour24 < 12) "AM" else "PM"
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
        }

        val BUNDLED_RULESET_JSON = """
            {
              "version": "v2026.11.18",
              "updatedAt": "2026-11-18",
              "apps": [
                {
                  "packageName": "com.google.android.youtube",
                  "displayName": "YouTube",
                  "feedLabel": "Shorts",
                  "homeTabLabel": "Home",
                  "viewIds": [
                    "com.google.android.youtube:id/reel_recycler",
                    "com.google.android.youtube:id/reel_watch_fragment_root",
                    "com.google.android.youtube:id/shorts_player"
                  ],
                  "tabContentDescription": "Shorts"
                },
                {
                  "packageName": "com.instagram.android",
                  "displayName": "Instagram",
                  "feedLabel": "Reels",
                  "homeTabLabel": "Home",
                  "viewIds": [
                    "com.instagram.android:id/clips_viewer_view_pager",
                    "com.instagram.android:id/clips_video_container"
                  ],
                  "tabContentDescription": "Reels"
                },
                {
                  "packageName": "com.facebook.katana",
                  "displayName": "Facebook",
                  "feedLabel": "Reels",
                  "homeTabLabel": "Home",
                  "viewIds": [
                    "com.facebook.katana:id/reels_viewer_root",
                    "com.facebook.katana:id/fb_reels_container"
                  ],
                  "tabContentDescription": "Reels"
                },
                {
                  "packageName": "com.snapchat.android",
                  "displayName": "Snapchat",
                  "feedLabel": "Spotlight",
                  "homeTabLabel": "Chat",
                  "viewIds": [
                    "com.snapchat.android:id/spotlight_feed_container",
                    "com.snapchat.android:id/spotlight_opera_view"
                  ],
                  "tabContentDescription": "Spotlight"
                },
                {
                  "packageName": "com.reddit.frontpage",
                  "displayName": "Reddit",
                  "feedLabel": "Watch",
                  "homeTabLabel": "Home",
                  "viewIds": [
                    "com.reddit.frontpage:id/video_player_root",
                    "com.reddit.frontpage:id/watch_feed_recycler"
                  ],
                  "tabContentDescription": "Watch"
                }
              ]
            }
        """.trimIndent()
    }
}

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("disclosure_accepted")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val AUTO_UPDATE_RULES = booleanPreferencesKey("auto_update_rules")
        val EVENT_CAPTURE_ENABLED = booleanPreferencesKey("event_capture_enabled")
        val RULESET_VERSION = stringPreferencesKey("ruleset_version")
        val RULESET_ENDPOINT_URL = stringPreferencesKey("ruleset_endpoint_url")
        val RULESET_ETAG = stringPreferencesKey("ruleset_etag")
        val LAST_RULESET_CHECK_MS = longPreferencesKey("last_ruleset_check_ms")
        val RECLAIMED_SECONDS_PER_CUT = intPreferencesKey("reclaimed_seconds_per_cut")
        val SHOW_INTERVENTION_BANNER = booleanPreferencesKey("show_intervention_banner")
        val CACHED_RULESET_JSON = stringPreferencesKey("cached_ruleset_json")
        val GLOBAL_PAUSE_UNTIL_MS = longPreferencesKey("global_pause_until_ms")
        val APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val DEFAULT_INTERVENTION_MODE = stringPreferencesKey("default_intervention_mode")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_MINUTES = intPreferencesKey("quiet_start_minutes")
        val QUIET_END_MINUTES = intPreferencesKey("quiet_end_minutes")
        val QUIET_OVERRIDE_UNTIL_MS = longPreferencesKey("quiet_override_until_ms")
        val DAILY_BUDGET_CUTS = intPreferencesKey("daily_budget_cuts")
        val STRICT_MODE_ENABLED = booleanPreferencesKey("strict_mode_enabled")
    }

    val preferencesFlow: Flow<AppPreferences> = context.feedSurgeonDataStore.data.map { prefs ->
        val rawMode = prefs[Keys.DEFAULT_INTERVENTION_MODE]
        val defaultMode = InterventionMode.entries.firstOrNull { it.name == rawMode }
            ?: InterventionMode.OVERLAY
        AppPreferences(
            disclosureAccepted = prefs[Keys.DISCLOSURE_ACCEPTED] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            autoUpdateRules = prefs[Keys.AUTO_UPDATE_RULES] ?: true,
            eventCaptureEnabled = prefs[Keys.EVENT_CAPTURE_ENABLED] ?: false,
            rulesetVersion = prefs[Keys.RULESET_VERSION] ?: AppPreferences.DEFAULT_RULESET_VERSION,
            rulesetEndpointUrl = prefs[Keys.RULESET_ENDPOINT_URL] ?: AppPreferences.DEFAULT_GIST_URL,
            rulesetEtag = prefs[Keys.RULESET_ETAG] ?: "",
            lastRulesetCheckMs = prefs[Keys.LAST_RULESET_CHECK_MS] ?: 0L,
            reclaimedSecondsPerCut = prefs[Keys.RECLAIMED_SECONDS_PER_CUT] ?: 180,
            showInterventionBanner = prefs[Keys.SHOW_INTERVENTION_BANNER] ?: true,
            cachedRulesetJson = prefs[Keys.CACHED_RULESET_JSON] ?: AppPreferences.BUNDLED_RULESET_JSON,
            globalPauseUntilMs = prefs[Keys.GLOBAL_PAUSE_UNTIL_MS] ?: 0L,
            appearanceMode = AppearanceMode.fromStored(prefs[Keys.APPEARANCE_MODE]),
            defaultInterventionMode = defaultMode,
            hapticFeedbackEnabled = prefs[Keys.HAPTIC_FEEDBACK_ENABLED] ?: true,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            quietStartMinutes = (prefs[Keys.QUIET_START_MINUTES] ?: AppPreferences.DEFAULT_QUIET_START_MINUTES).coerceIn(0, 1439),
            quietEndMinutes = (prefs[Keys.QUIET_END_MINUTES] ?: AppPreferences.DEFAULT_QUIET_END_MINUTES).coerceIn(0, 1439),
            quietOverrideUntilMs = prefs[Keys.QUIET_OVERRIDE_UNTIL_MS] ?: 0L,
            dailyBudgetCuts = (prefs[Keys.DAILY_BUDGET_CUTS] ?: 0).coerceIn(0, 100),
            strictModeEnabled = prefs[Keys.STRICT_MODE_ENABLED] ?: false
        )
    }

    suspend fun setDisclosureAccepted(accepted: Boolean) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.DISCLOSURE_ACCEPTED] = accepted
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setAutoUpdateRules(enabled: Boolean) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.AUTO_UPDATE_RULES] = enabled
        }
    }

    suspend fun setEventCaptureEnabled(enabled: Boolean) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.EVENT_CAPTURE_ENABLED] = enabled
        }
    }

    suspend fun setReclaimedSecondsPerCut(seconds: Int) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.RECLAIMED_SECONDS_PER_CUT] = seconds.coerceIn(30, 600)
        }
    }

    suspend fun setShowInterventionBanner(show: Boolean) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.SHOW_INTERVENTION_BANNER] = show
        }
    }

    suspend fun setGlobalPauseUntilMs(untilMs: Long) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.GLOBAL_PAUSE_UNTIL_MS] = untilMs
        }
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.APPEARANCE_MODE] = mode.name
        }
    }

    suspend fun setDefaultInterventionMode(mode: InterventionMode) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_INTERVENTION_MODE] = mode.name
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setQuietHours(
        enabled: Boolean,
        startMinutes: Int,
        endMinutes: Int
    ) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.QUIET_HOURS_ENABLED] = enabled
            prefs[Keys.QUIET_START_MINUTES] = startMinutes.coerceIn(0, 1439)
            prefs[Keys.QUIET_END_MINUTES] = endMinutes.coerceIn(0, 1439)
            prefs[Keys.QUIET_OVERRIDE_UNTIL_MS] = 0L
        }
    }

    suspend fun setQuietOverrideUntilMs(untilMs: Long) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.QUIET_OVERRIDE_UNTIL_MS] = untilMs
        }
    }

    suspend fun setDailyBudgetCuts(budgetCuts: Int) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.DAILY_BUDGET_CUTS] = budgetCuts.coerceIn(0, 100)
        }
    }

    suspend fun setStrictModeEnabled(enabled: Boolean) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.STRICT_MODE_ENABLED] = enabled
        }
    }

    suspend fun restoreBackupPreferences(
        appearanceMode: AppearanceMode,
        defaultInterventionMode: InterventionMode,
        reclaimedSecondsPerCut: Int,
        hapticFeedbackEnabled: Boolean,
        showInterventionBanner: Boolean,
        autoUpdateRules: Boolean,
        quietHoursEnabled: Boolean,
        quietStartMinutes: Int,
        quietEndMinutes: Int,
        dailyBudgetCuts: Int,
        strictModeEnabled: Boolean = false
    ) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.APPEARANCE_MODE] = appearanceMode.name
            prefs[Keys.DEFAULT_INTERVENTION_MODE] = defaultInterventionMode.name
            prefs[Keys.RECLAIMED_SECONDS_PER_CUT] = reclaimedSecondsPerCut.coerceIn(30, 600)
            prefs[Keys.HAPTIC_FEEDBACK_ENABLED] = hapticFeedbackEnabled
            prefs[Keys.SHOW_INTERVENTION_BANNER] = showInterventionBanner
            prefs[Keys.AUTO_UPDATE_RULES] = autoUpdateRules
            prefs[Keys.QUIET_HOURS_ENABLED] = quietHoursEnabled
            prefs[Keys.QUIET_START_MINUTES] = quietStartMinutes.coerceIn(0, 1439)
            prefs[Keys.QUIET_END_MINUTES] = quietEndMinutes.coerceIn(0, 1439)
            prefs[Keys.QUIET_OVERRIDE_UNTIL_MS] = 0L
            prefs[Keys.DAILY_BUDGET_CUTS] = dailyBudgetCuts.coerceIn(0, 100)
            prefs[Keys.STRICT_MODE_ENABLED] = strictModeEnabled
        }
    }

    suspend fun saveFetchedRuleset(
        version: String,
        rawJson: String,
        checkedAtMs: Long,
        etag: String? = null
    ) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.RULESET_VERSION] = version
            prefs[Keys.CACHED_RULESET_JSON] = rawJson
            prefs[Keys.LAST_RULESET_CHECK_MS] = checkedAtMs
            if (etag != null) {
                prefs[Keys.RULESET_ETAG] = etag
            }
        }
    }

    suspend fun updateLastCheckedOnly(checkedAtMs: Long, etag: String? = null) {
        context.feedSurgeonDataStore.edit { prefs ->
            prefs[Keys.LAST_RULESET_CHECK_MS] = checkedAtMs
            if (etag != null) {
                prefs[Keys.RULESET_ETAG] = etag
            }
        }
    }
}
