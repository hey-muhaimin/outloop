package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.AppearanceMode
import com.example.data.local.FeedSurgeonDao
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.local.InterventionEventEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionOutcome
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class BackupPreferencesDto(
    val appearanceMode: String = AppearanceMode.SYSTEM.name,
    val defaultInterventionMode: String = InterventionMode.OVERLAY.name,
    val reclaimedSecondsPerCut: Int = 180,
    val hapticFeedbackEnabled: Boolean = true,
    val showInterventionBanner: Boolean = true,
    val autoUpdateRules: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val dailyBudgetCuts: Int = 0
)

data class BackupRuleDto(
    val packageName: String,
    val displayName: String,
    val feedLabel: String,
    val enabled: Boolean,
    val interventionMode: String,
    val cooldownMs: Int,
    val homeTabLabel: String,
    val viewIdsRaw: String,
    val tabFallbackLabel: String,
    val isBuiltIn: Boolean
)

data class BackupEventDto(
    val packageName: String,
    val appName: String,
    val feedLabel: String,
    val matchedSelector: String,
    val actionTaken: String,
    val timestampMs: Long,
    val reclaimedSeconds: Int,
    val wasAllowedOnce: Boolean,
    val outcome: String
)

data class OutloopBackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val preferences: BackupPreferencesDto = BackupPreferencesDto(),
    val rules: List<BackupRuleDto> = emptyList(),
    val events: List<BackupEventDto> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class OutloopBackupPreview(
    val schemaVersion: Int,
    val exportedAtMs: Long,
    val ruleCount: Int,
    val customRuleCount: Int,
    val eventCount: Int
)

sealed interface BackupRestoreResult {
    data class Success(
        val restoredRules: Int,
        val restoredEvents: Int
    ) : BackupRestoreResult

    data class Error(val message: String) : BackupRestoreResult
}

class LocalBackupManager(
    private val dao: FeedSurgeonDao,
    private val settingsDataStore: SettingsDataStore,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) {
    private val adapter = moshi.adapter(OutloopBackupPayload::class.java).indent("  ")

    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val prefs = settingsDataStore.preferencesFlow.first()
        val rules = dao.getAllRulesSnapshot()
        val events = dao.getAllEventsSnapshot()

        val payload = OutloopBackupPayload(
            schemaVersion = OutloopBackupPayload.CURRENT_SCHEMA_VERSION,
            appVersion = BuildConfig.VERSION_NAME,
            exportedAtMs = System.currentTimeMillis(),
            preferences = BackupPreferencesDto(
                appearanceMode = prefs.appearanceMode.name,
                defaultInterventionMode = prefs.defaultInterventionMode.name,
                reclaimedSecondsPerCut = prefs.reclaimedSecondsPerCut,
                hapticFeedbackEnabled = prefs.hapticFeedbackEnabled,
                showInterventionBanner = prefs.showInterventionBanner,
                autoUpdateRules = prefs.autoUpdateRules,
                quietHoursEnabled = prefs.quietHoursEnabled,
                quietStartMinutes = prefs.quietStartMinutes,
                quietEndMinutes = prefs.quietEndMinutes,
                dailyBudgetCuts = prefs.dailyBudgetCuts
            ),
            rules = rules.map { rule ->
                BackupRuleDto(
                    packageName = rule.packageName,
                    displayName = rule.displayName,
                    feedLabel = rule.feedLabel,
                    enabled = rule.enabled,
                    interventionMode = rule.interventionMode.name,
                    cooldownMs = rule.cooldownMs,
                    homeTabLabel = rule.homeTabLabel,
                    viewIdsRaw = rule.viewIdsRaw,
                    tabFallbackLabel = rule.tabFallbackLabel,
                    isBuiltIn = rule.isBuiltIn
                )
            },
            events = events.map { ev ->
                BackupEventDto(
                    packageName = ev.packageName,
                    appName = ev.appName,
                    feedLabel = ev.feedLabel,
                    matchedSelector = ev.matchedSelector,
                    actionTaken = ev.actionTaken.name,
                    timestampMs = ev.timestampMs,
                    reclaimedSeconds = ev.reclaimedSeconds,
                    wasAllowedOnce = ev.wasAllowedOnce,
                    outcome = ev.outcome.name
                )
            }
        )
        adapter.toJson(payload)
    }

    fun inspectBackupJson(rawJson: String): OutloopBackupPreview? {
        val parsed = parseAndValidate(rawJson) ?: return null
        return OutloopBackupPreview(
            schemaVersion = parsed.schemaVersion,
            exportedAtMs = parsed.exportedAtMs,
            ruleCount = parsed.rules.size,
            customRuleCount = parsed.rules.count { !it.isBuiltIn },
            eventCount = parsed.events.size
        )
    }

    suspend fun restoreFromJson(rawJson: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val parsed = parseAndValidate(rawJson)
            ?: return@withContext BackupRestoreResult.Error("This file isn\u2019t a valid Outloop backup.")

        val builtInDefaults = FeedSurgeonDatabase.defaultBuiltInRules().associateBy { it.packageName }
        val restoredRuleMap = linkedMapOf<String, TargetAppRuleEntity>()

        parsed.rules.forEach { dto ->
            val cleanPkg = dto.packageName.trim()
            if (cleanPkg.isEmpty()) return@forEach
            val mode = InterventionMode.entries.firstOrNull { it.name == dto.interventionMode }
                ?: InterventionMode.OVERLAY
            val defaultBuiltIn = builtInDefaults[cleanPkg]
            val cleanViewIds = dto.viewIdsRaw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            restoredRuleMap[cleanPkg] = TargetAppRuleEntity(
                packageName = cleanPkg,
                displayName = dto.displayName.trim().ifEmpty { defaultBuiltIn?.displayName ?: cleanPkg },
                feedLabel = dto.feedLabel.trim().ifEmpty { defaultBuiltIn?.feedLabel ?: "Feed" },
                enabled = dto.enabled,
                interventionMode = mode,
                cooldownMs = dto.cooldownMs.coerceIn(500, 1500),
                homeTabLabel = dto.homeTabLabel.trim().ifEmpty { defaultBuiltIn?.homeTabLabel ?: "Home" },
                viewIdsRaw = dto.viewIdsRaw.ifBlank { defaultBuiltIn?.viewIdsRaw.orEmpty() },
                tabFallbackLabel = dto.tabFallbackLabel.trim().ifEmpty { defaultBuiltIn?.tabFallbackLabel.orEmpty() },
                snoozeUntilMs = 0L,
                allowOnceUntilMs = 0L,
                isBuiltIn = dto.isBuiltIn || cleanPkg in FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES,
                matchedSelectorCount = cleanViewIds.size,
                totalSelectorCount = cleanViewIds.size,
                isOutdated = false
            )
        }

        // Always guarantee built-in rules remain present even if restoring from an older backup
        builtInDefaults.forEach { (pkg, fallbackRule) ->
            if (pkg !in restoredRuleMap) {
                restoredRuleMap[pkg] = fallbackRule
            }
        }

        val restoredEvents = parsed.events
            .filter { it.packageName.isNotBlank() && it.timestampMs > 0L }
            .take(MAX_RESTORED_EVENTS)
            .map { dto ->
                val action = InterventionMode.entries.firstOrNull { it.name == dto.actionTaken }
                    ?: InterventionMode.OVERLAY
                val outcome = InterventionOutcome.entries.firstOrNull { it.name == dto.outcome }
                    ?: if (dto.wasAllowedOnce) InterventionOutcome.ALLOWED_ONCE else InterventionOutcome.CONFIRMED
                InterventionEventEntity(
                    packageName = dto.packageName.trim(),
                    appName = dto.appName.trim().ifEmpty { dto.packageName.trim() },
                    feedLabel = dto.feedLabel.trim().ifEmpty { "Feed" },
                    matchedSelector = dto.matchedSelector.trim(),
                    actionTaken = action,
                    timestampMs = dto.timestampMs,
                    reclaimedSeconds = dto.reclaimedSeconds.coerceIn(30, 600),
                    wasAllowedOnce = dto.wasAllowedOnce,
                    outcome = outcome
                )
            }

        val finalRules = restoredRuleMap.values.toList()
        dao.replaceAllDataForBackup(rules = finalRules, events = restoredEvents)

        val prefDto = parsed.preferences
        val restoredAppearance = AppearanceMode.fromStored(prefDto.appearanceMode)
        val restoredDefaultMode = InterventionMode.entries.firstOrNull {
            it.name == prefDto.defaultInterventionMode
        } ?: InterventionMode.OVERLAY

        settingsDataStore.restoreBackupPreferences(
            appearanceMode = restoredAppearance,
            defaultInterventionMode = restoredDefaultMode,
            reclaimedSecondsPerCut = prefDto.reclaimedSecondsPerCut,
            hapticFeedbackEnabled = prefDto.hapticFeedbackEnabled,
            showInterventionBanner = prefDto.showInterventionBanner,
            autoUpdateRules = prefDto.autoUpdateRules,
            quietHoursEnabled = prefDto.quietHoursEnabled,
            quietStartMinutes = prefDto.quietStartMinutes,
            quietEndMinutes = prefDto.quietEndMinutes,
            dailyBudgetCuts = prefDto.dailyBudgetCuts
        )

        BackupRestoreResult.Success(
            restoredRules = finalRules.size,
            restoredEvents = restoredEvents.size
        )
    }

    private fun parseAndValidate(rawJson: String): OutloopBackupPayload? {
        if (rawJson.isBlank()) return null
        return try {
            val payload = adapter.fromJson(rawJson) ?: return null
            if (payload.schemaVersion !in 1..OutloopBackupPayload.CURRENT_SCHEMA_VERSION) return null
            if (payload.exportedAtMs <= 0L) return null
            if (payload.rules.any { it.packageName.isBlank() }) return null
            if (payload.events.any { it.packageName.isBlank() || it.timestampMs <= 0L }) return null
            payload
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_RESTORED_EVENTS = 10_000
    }
}
