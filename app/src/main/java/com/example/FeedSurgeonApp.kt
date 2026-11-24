package com.example

import android.app.Application
import android.util.Log
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.repository.ControlledAppsRepository
import com.example.data.repository.InterventionRepository
import com.example.data.repository.RulesetRepository
import com.example.service.AccessibilityEventLogger
import com.example.worker.RulesetSyncWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FeedSurgeonApp : Application() {
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled background startup error", throwable)
        }
    )

    val database: FeedSurgeonDatabase by lazy {
        FeedSurgeonDatabase.getInstance(this)
    }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(this)
    }

    val interventionRepository: InterventionRepository by lazy {
        InterventionRepository(database.feedSurgeonDao(), settingsDataStore)
    }

    val controlledAppsRepository: ControlledAppsRepository by lazy {
        interventionRepository
    }

    val rulesetRepository: RulesetRepository by lazy {
        RulesetRepository(this, database.feedSurgeonDao(), settingsDataStore)
    }

    val localBackupManager: com.example.data.repository.LocalBackupManager by lazy {
        com.example.data.repository.LocalBackupManager(database.feedSurgeonDao(), settingsDataStore)
    }

    val eventLogger: AccessibilityEventLogger by lazy {
        AccessibilityEventLogger(this)
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { interventionRepository.ensureDefaultRulesSeeded() }
                .onFailure { Log.e(TAG, "Failed to seed default rules", it) }
            runCatching { rulesetRepository.ensureLocalCacheSeeded() }
                .onFailure { Log.e(TAG, "Failed to seed local ruleset cache", it) }
            runCatching {
                val prefs = settingsDataStore.preferencesFlow.first()
                if (prefs.autoUpdateRules) {
                    RulesetSyncWorker.schedulePeriodicSync(this@FeedSurgeonApp)
                }
                rulesetRepository.autoSyncIfDue(force = false)
            }.onFailure { Log.e(TAG, "Failed to schedule or run initial ruleset sync", it) }
        }
    }

    private companion object {
        const val TAG = "FeedSurgeonApp"
    }
}
