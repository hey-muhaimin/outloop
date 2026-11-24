package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FeedSurgeonApp
import com.example.data.local.AppPreferences
import com.example.data.local.AppearanceMode
import com.example.data.local.InterventionEventEntity
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionOutcome
import com.example.data.model.InterventionToastState
import com.example.data.model.ProtectionStatus
import com.example.data.repository.BackupRestoreResult
import com.example.data.repository.DiagnosticsBridge
import com.example.data.repository.InterventionRepository
import com.example.data.repository.OutloopBackupPreview
import com.example.data.repository.RulesetSyncResult
import com.example.service.AccessibilityStatusHelper
import com.example.service.InstalledAppDetector
import com.example.service.LaunchableAppInfo
import com.example.service.ProtectionHealthHelper
import com.example.worker.RulesetSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class DayBarStat(
    val dayLetter: String,
    val dayName: String,
    val dateLabel: String,
    val count: Int,
    val reclaimedMinutes: Int,
    val fraction: Float,
    val isToday: Boolean
)

data class InsightBucketStat(
    val shortLabel: String,
    val detailLabel: String,
    val count: Int,
    val fraction: Float
)

data class AppBreakdownStat(
    val packageName: String,
    val displayName: String,
    val label: String,
    val count: Int,
    val fraction: Float
)

data class MainUiState(
    val isInitialLoading: Boolean = true,
    val preferences: AppPreferences = AppPreferences(),
    val rules: List<TargetAppRuleEntity> = emptyList(),
    val installedPackages: Set<String> = emptySet(),
    val hasScannedInstalledApps: Boolean = true,
    val isAccessibilityEnabled: Boolean = false,
    val isServiceConnected: Boolean = false,
    val lastConnectedAtMs: Long = 0L,
    val protectionStatus: ProtectionStatus = ProtectionStatus.PERMISSION_NEEDED,
    val activeInstalledCount: Int = 0,
    val installedSupportedCount: Int = 0,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isNotificationPermissionGranted: Boolean = true,
    val todayCutsCount: Int = 0,
    val todayReclaimedMinutes: Int = 0,
    val todayReclaimedText: String = "0 min",
    val todayHourBuckets: List<InsightBucketStat> = emptyList(),
    val todayByAppBreakdown: List<AppBreakdownStat> = emptyList(),
    val staleRulesCount: Int = 0,
    val weeklyCutsCount: Int = 0,
    val weeklyDayBars: List<DayBarStat> = emptyList(),
    val weeklyReclaimedText: String = "0 min",
    val byAppBreakdown: List<AppBreakdownStat> = emptyList(),
    val thirtyDayCutsCount: Int = 0,
    val thirtyDayReclaimedText: String = "0 min",
    val thirtyDayWeekBuckets: List<InsightBucketStat> = emptyList(),
    val thirtyDayByAppBreakdown: List<AppBreakdownStat> = emptyList(),
    val lastDetectionSummary: String = "None recorded yet",
    val lastInterventionOutcomeSummary: String = "None recorded yet",
    val foregroundAppSummary: String = "Waiting for monitored app",
    val recentToast: InterventionToastState? = null,
    val isSyncingRules: Boolean = false,
    val syncStatusMessage: String? = null,
    val eventLogPreview: String = "",
    val eventLogSizeBytes: Long = 0L,
    val isGloballyPaused: Boolean = false,
    val globalPauseUntilMs: Long = 0L,
    val isInQuietHours: Boolean = false,
    val quietHoursEndLabel: String = "",
    val launchableApps: List<LaunchableAppInfo> = emptyList(),
    val utilityApps: List<LaunchableAppInfo> = emptyList(),
    val streakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val weeklyDeltaText: String? = null,
    val todayUsageSecondsByPackage: Map<String, Long> = emptyMap(),
    val todayMeasuredFeedSeconds: Long = 0L,
    val todayMeasuredFeedText: String = "0m",
    val globalFeedBudgetMinutes: Int = AppPreferences.DEFAULT_GLOBAL_FEED_BUDGET_MINUTES,
    val isGlobalFeedBudgetReached: Boolean = false,
    val currentTimeMs: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = (application as? FeedSurgeonApp)
        ?: (application.applicationContext as FeedSurgeonApp)
    private val interventionRepo = app.interventionRepository
    private val rulesetRepo = app.rulesetRepository
    private val backupManager = app.localBackupManager
    private val settingsDataStore = app.settingsDataStore
    private val eventLogger = app.eventLogger

    private val _accessibilityEnabled = MutableStateFlow(
        AccessibilityStatusHelper.isServiceEnabled(application)
    )
    private val _batteryOptimizationIgnored = MutableStateFlow(
        ProtectionHealthHelper.isIgnoringBatteryOptimizations(application)
    )
    private val _notificationPermissionGranted = MutableStateFlow(
        ProtectionHealthHelper.isNotificationPermissionGranted(application)
    )
    private val _installedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _hasScannedInstalledApps = MutableStateFlow(false)
    private val _launchableApps = MutableStateFlow<List<LaunchableAppInfo>>(emptyList())
    private val _utilityApps = MutableStateFlow<List<LaunchableAppInfo>>(emptyList())
    private val _isSyncingRules = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)
    private val _eventLogPreview = MutableStateFlow("")
    private val _eventLogSizeBytes = MutableStateFlow(0L)
    private val _nowMs = MutableStateFlow(System.currentTimeMillis())

    private data class TransientState(
        val accessibilityEnabled: Boolean,
        val serviceConnected: Boolean,
        val lastConnectedAtMs: Long,
        val batteryOptimizationIgnored: Boolean,
        val notificationPermissionGranted: Boolean,
        val installedPackages: Set<String>,
        val hasScannedInstalledApps: Boolean,
        val launchableApps: List<LaunchableAppInfo>,
        val utilityApps: List<LaunchableAppInfo>,
        val isSyncing: Boolean,
        val syncMessage: String?,
        val toast: InterventionToastState?,
        val logPreview: String,
        val logSizeBytes: Long,
        val nowMs: Long,
        val foregroundAppLabel: String?
    )

    private data class Tuple8<A, B, C, D, E, F, G, H>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
        val seventh: G,
        val eighth: H
    )

    private val syncAndLogFlow = combine(
        _isSyncingRules,
        _syncMessage,
        DiagnosticsBridge.recentToast,
        _eventLogPreview,
        _eventLogSizeBytes,
        _launchableApps,
        _nowMs,
        DiagnosticsBridge.foregroundMonitoredAppLabel
    ) { array ->
        Tuple8(
            array[0] as Boolean,
            array[1] as String?,
            array[2] as InterventionToastState?,
            array[3] as String,
            array[4] as Long,
            @Suppress("UNCHECKED_CAST")
            (array[5] as List<LaunchableAppInfo>),
            array[6] as Long,
            array[7] as String?
        )
    }

    private data class Tuple7<A, B, C, D, E, F, G>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
        val seventh: G
    )

    private val environmentFlow = combine(
        _accessibilityEnabled,
        DiagnosticsBridge.isServiceConnected,
        DiagnosticsBridge.lastConnectedTimestampMs,
        _batteryOptimizationIgnored,
        _notificationPermissionGranted,
        _installedPackages,
        _hasScannedInstalledApps
    ) { array ->
        Tuple7(
            array[0] as Boolean,
            array[1] as Boolean,
            array[2] as Long,
            array[3] as Boolean,
            array[4] as Boolean,
            @Suppress("UNCHECKED_CAST")
            (array[5] as Set<String>),
            array[6] as Boolean
        )
    }

    private val transientFlow = combine(
        environmentFlow,
        syncAndLogFlow,
        _utilityApps
    ) { env, extra, utilities ->
        TransientState(
            accessibilityEnabled = env.first,
            serviceConnected = env.second,
            lastConnectedAtMs = env.third,
            batteryOptimizationIgnored = env.fourth,
            notificationPermissionGranted = env.fifth,
            installedPackages = env.sixth,
            hasScannedInstalledApps = env.seventh,
            launchableApps = extra.sixth,
            utilityApps = utilities,
            isSyncing = extra.first,
            syncMessage = extra.second,
            toast = extra.third,
            logPreview = extra.fourth,
            logSizeBytes = extra.fifth,
            nowMs = extra.seventh,
            foregroundAppLabel = extra.eighth
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayUsageFlow = _nowMs
        .map { InterventionRepository.dateKeyForMs(it) }
        .distinctUntilChanged()
        .flatMapLatest { dateKey ->
            interventionRepo.observeDailyUsageForDate(dateKey)
        }

    val uiState: StateFlow<MainUiState> = combine(
        interventionRepo.preferences,
        interventionRepo.allRules,
        interventionRepo.eventsLast30Days,
        todayUsageFlow,
        transientFlow
    ) { prefs, rules, events, dailyUsageRows, transient ->
        val todayUsageByPackage = dailyUsageRows.associate { it.packageName to it.trackedSeconds }
        val todayMeasuredFeedSeconds = todayUsageByPackage.values.sum()
        val todayMeasuredFeedText = InterventionRepository.formatDurationCompact(todayMeasuredFeedSeconds)
        val globalBudgetMinutes = prefs.globalFeedTimeBudgetMinutes
        val isGlobalFeedBudgetReached = prefs.hasGlobalFeedBudget() &&
            todayMeasuredFeedSeconds >= prefs.globalFeedBudgetSeconds
        val installed = if (transient.hasScannedInstalledApps) {
            transient.installedPackages
        } else {
            InstalledAppDetector.getInstalledPackages(app, rules.map { it.packageName })
        }
        val nowMs = transient.nowMs
        val countedEvents = filterCountedEvents(events)

        val todayStart = InterventionRepository.startOfTodayMs(nowMs)
        val todayEvents = countedEvents.filter { it.timestampMs >= todayStart }
        val todaySeconds = todayEvents.sumOf { it.reclaimedSeconds }
        val todayBuckets = buildTodayTimeBuckets(todayEvents)

        val weeklyBars = buildWeeklyDayBars(countedEvents, nowMs)
        val weekStart = InterventionRepository.startOfWeekDaysAgoMs(6, nowMs)
        val weekEvents = countedEvents.filter { it.timestampMs >= weekStart }
        val weekSeconds = weekEvents.sumOf { it.reclaimedSeconds }
        val prevWeekStart = InterventionRepository.startOfWeekDaysAgoMs(13, nowMs)
        val prevWeekSeconds = countedEvents
            .filter { it.timestampMs in prevWeekStart until weekStart }
            .sumOf { it.reclaimedSeconds }
        val weeklyDeltaText = when {
            prevWeekSeconds > 0 -> {
                val pct = (((weekSeconds - prevWeekSeconds).toFloat() / prevWeekSeconds.toFloat()) * 100f).toInt()
                if (pct >= 0) "+$pct% vs last week" else "$pct% vs last week"
            }
            weekSeconds > 0 -> "First active week"
            else -> null
        }

        val thirtyDayStart = InterventionRepository.startOfWeekDaysAgoMs(27, nowMs)
        val thirtyDayEvents = countedEvents.filter { it.timestampMs >= thirtyDayStart }
        val thirtyDaySeconds = thirtyDayEvents.sumOf { it.reclaimedSeconds }
        val thirtyDayBuckets = buildThirtyDayWeekBuckets(thirtyDayEvents, nowMs)

        val todayByApp = buildAppBreakdown(rules, todayEvents)
        val weekByApp = buildAppBreakdown(rules, weekEvents)
        val thirtyDayByApp = buildAppBreakdown(rules, thirtyDayEvents)

        val installedRules = rules.filter { it.packageName in installed }
        val activeInstalledCount = installedRules.count { it.enabled }
        val staleCount = installedRules.count { it.isOutdated }
        val isGloballyPaused = prefs.isGloballyPaused(nowMs)
        val isInQuietHours = prefs.isInQuietHours(nowMs)
        val protectionStatus = deriveProtectionStatus(
            isAccessibilityEnabled = transient.accessibilityEnabled,
            isServiceConnected = transient.serviceConnected,
            activeInstalledCount = activeInstalledCount,
            isGloballyPaused = isGloballyPaused,
            isInQuietHours = isInQuietHours
        )

        val latestEvent = events.firstOrNull()
        val lastDetectionSummary = latestEvent?.let {
            "${it.appName} ${it.feedLabel} \u00B7 ${formatRelativeAgo(it.timestampMs, nowMs)}"
        } ?: "None recorded yet"
        val lastOutcomeSummary = latestEvent?.let { formatOutcomeSummary(it.outcome) }
            ?: "None recorded yet"
        val currentStreak = computeStreakDays(countedEvents, nowMs)
        val bestStreak = computeBestStreakDays(countedEvents, nowMs).coerceAtLeast(currentStreak)

        MainUiState(
            isInitialLoading = false,
            preferences = prefs,
            rules = rules,
            installedPackages = installed,
            hasScannedInstalledApps = true,
            isAccessibilityEnabled = transient.accessibilityEnabled,
            isServiceConnected = transient.serviceConnected,
            lastConnectedAtMs = transient.lastConnectedAtMs,
            protectionStatus = protectionStatus,
            activeInstalledCount = activeInstalledCount,
            installedSupportedCount = installedRules.size,
            isBatteryOptimizationIgnored = transient.batteryOptimizationIgnored,
            isNotificationPermissionGranted = transient.notificationPermissionGranted,
            todayCutsCount = todayEvents.size,
            todayReclaimedMinutes = todaySeconds / 60,
            todayReclaimedText = formatReclaimedDuration(todaySeconds),
            todayHourBuckets = todayBuckets,
            todayByAppBreakdown = todayByApp,
            staleRulesCount = staleCount,
            weeklyCutsCount = weekEvents.size,
            weeklyDayBars = weeklyBars,
            weeklyReclaimedText = formatReclaimedDuration(weekSeconds),
            byAppBreakdown = weekByApp,
            thirtyDayCutsCount = thirtyDayEvents.size,
            thirtyDayReclaimedText = formatReclaimedDuration(thirtyDaySeconds),
            thirtyDayWeekBuckets = thirtyDayBuckets,
            thirtyDayByAppBreakdown = thirtyDayByApp,
            lastDetectionSummary = lastDetectionSummary,
            lastInterventionOutcomeSummary = lastOutcomeSummary,
            foregroundAppSummary = transient.foregroundAppLabel?.let { "$it (foreground)" }
                ?: "Waiting for monitored app",
            recentToast = transient.toast,
            isSyncingRules = transient.isSyncing,
            syncStatusMessage = transient.syncMessage,
            eventLogPreview = transient.logPreview,
            eventLogSizeBytes = transient.logSizeBytes,
            isGloballyPaused = isGloballyPaused,
            globalPauseUntilMs = prefs.globalPauseUntilMs,
            isInQuietHours = isInQuietHours,
            quietHoursEndLabel = AppPreferences.formatMinutesOfDay(prefs.quietEndMinutes),
            launchableApps = transient.launchableApps,
            utilityApps = transient.utilityApps,
            streakDays = currentStreak,
            bestStreakDays = bestStreak,
            weeklyDeltaText = weeklyDeltaText,
            todayUsageSecondsByPackage = todayUsageByPackage,
            todayMeasuredFeedSeconds = todayMeasuredFeedSeconds,
            todayMeasuredFeedText = todayMeasuredFeedText,
            globalFeedBudgetMinutes = globalBudgetMinutes,
            isGlobalFeedBudgetReached = isGlobalFeedBudgetReached,
            currentTimeMs = nowMs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            runCatching { interventionRepo.ensureDefaultRulesSeeded() }
        }
        refreshEventLog()
        refreshInstalledApps(app)

        viewModelScope.launch {
            runCatching {
                eventLogger.logVersion.collect {
                    _eventLogPreview.value = eventLogger.readTail(120)
                    _eventLogSizeBytes.value = eventLogger.getFileSizeBytes()
                }
            }
        }

        // Real-time Accessibility permission observer
        viewModelScope.launch {
            runCatching {
                AccessibilityStatusHelper.observeServiceEnabled(app).collect { enabled ->
                    _accessibilityEnabled.value = enabled
                }
            }
        }

        // Real-time app install / uninstall / update observer
        viewModelScope.launch {
            runCatching {
                InstalledAppDetector.observePackageChanges(app).collect {
                    refreshInstalledApps(app)
                }
            }
        }

        // Re-scan installed apps whenever tracked rule package names change (e.g. custom rules or sync)
        viewModelScope.launch {
            runCatching {
                interventionRepo.allRules
                    .map { rules -> rules.map { it.packageName }.sorted() }
                    .distinctUntilChanged()
                    .collect { packages ->
                        if (packages.isNotEmpty()) {
                            val scanned = withContext(Dispatchers.IO) {
                                InstalledAppDetector.getInstalledPackages(app, packages)
                            }
                            _installedPackages.value = scanned
                            _hasScannedInstalledApps.value = true
                        }
                    }
            }
        }

        // Lightweight real-time clock for countdowns, quiet-hours transitions, and relative labels
        viewModelScope.launch {
            while (true) {
                var nextDelayMs = 15_000L
                try {
                    val now = System.currentTimeMillis()
                    _nowMs.value = now
                    val current = uiState.value
                    if (current.preferences.globalPauseUntilMs in 1L..now &&
                        current.preferences.globalPauseUntilMs != Long.MAX_VALUE
                    ) {
                        interventionRepo.resumeGlobalProtection()
                    }
                    current.rules.forEach { rule ->
                        if (rule.snoozeUntilMs in 1L..now) {
                            interventionRepo.clearSnooze(rule.packageName)
                        }
                    }
                    val hasActiveCountdown = (current.isGloballyPaused && current.globalPauseUntilMs != Long.MAX_VALUE) ||
                        current.rules.any { it.snoozeUntilMs > now }
                    nextDelayMs = if (hasActiveCountdown) 1_000L else 15_000L
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                }
                delay(nextDelayMs)
            }
        }
    }

    fun refreshAccessibilityStatus(context: Context) {
        _accessibilityEnabled.value = AccessibilityStatusHelper.isServiceEnabled(context)
        refreshProtectionHealth(context)
    }

    fun refreshProtectionHealth(context: Context) {
        _batteryOptimizationIgnored.value =
            ProtectionHealthHelper.isIgnoringBatteryOptimizations(context)
        _notificationPermissionGranted.value =
            ProtectionHealthHelper.isNotificationPermissionGranted(context)
    }

    fun refreshInstalledApps(context: Context) {
        val appContext = context.applicationContext ?: context
        viewModelScope.launch {
            runCatching {
                val packages = withContext(Dispatchers.IO) {
                    val dbPackages = runCatching {
                        app.database.feedSurgeonDao().getAllRulesSnapshot().map { it.packageName }
                    }.getOrDefault(emptyList())
                    (com.example.data.local.FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES + dbPackages).distinct()
                }
                val installed = withContext(Dispatchers.IO) {
                    InstalledAppDetector.getInstalledPackages(appContext, packages)
                }
                _installedPackages.value = installed
                _hasScannedInstalledApps.value = true
                val discovered = withContext(Dispatchers.IO) {
                    InstalledAppDetector.getLaunchableApps(appContext)
                }
                _launchableApps.value = discovered
                val utilities = withContext(Dispatchers.IO) {
                    InstalledAppDetector.getInstalledUtilityApps(appContext, discovered)
                }
                _utilityApps.value = utilities
            }
        }
    }

    fun triggerAutoSyncIfNeeded() {
        viewModelScope.launch {
            runCatching {
                rulesetRepo.ensureLocalCacheSeeded()
                when (val result = rulesetRepo.autoSyncIfDue(force = false)) {
                    is RulesetSyncResult.Success -> {
                        if (!result.fromLocalCache) {
                            _syncMessage.value = "Updated focus rules for ${result.updatedApps} apps"
                        }
                    }
                    is RulesetSyncResult.Error -> Unit
                }
            }
        }
    }

    fun setAutoUpdateRules(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAutoUpdateRules(enabled)
            if (enabled) {
                RulesetSyncWorker.schedulePeriodicSync(app)
                triggerAutoSyncIfNeeded()
            } else {
                RulesetSyncWorker.cancelPeriodicSync(app)
            }
        }
    }

    fun setEventCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setEventCaptureEnabled(enabled)
            refreshEventLog()
        }
    }

    fun refreshEventLog() {
        viewModelScope.launch {
            runCatching {
                _eventLogPreview.value = eventLogger.readTail(120)
                _eventLogSizeBytes.value = eventLogger.getFileSizeBytes()
            }
        }
    }

    fun clearEventLog() {
        viewModelScope.launch {
            eventLogger.clear()
            _eventLogPreview.value = ""
            _eventLogSizeBytes.value = 0L
        }
    }

    fun createShareLogIntent(): Intent? {
        val uri = eventLogger.getShareUri() ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Outloop Accessibility Event Log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun acceptDisclosure() {
        viewModelScope.launch {
            settingsDataStore.setDisclosureAccepted(true)
        }
    }

    fun declineDisclosure() {
        viewModelScope.launch {
            settingsDataStore.setDisclosureAccepted(false)
            settingsDataStore.setOnboardingCompleted(true)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(true)
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            settingsDataStore.setOnboardingCompleted(false)
        }
    }

    fun setRuleEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            interventionRepo.setRuleEnabled(packageName, enabled)
        }
    }

    fun setDailyLimitMinutes(packageName: String, limitMinutes: Int) {
        viewModelScope.launch {
            interventionRepo.setDailyLimitMinutes(packageName, limitMinutes)
        }
    }

    fun setInterventionMode(packageName: String, mode: InterventionMode) {
        viewModelScope.launch {
            interventionRepo.setInterventionMode(packageName, mode)
        }
    }

    fun setDefaultInterventionMode(mode: InterventionMode, applyToAllRules: Boolean = false) {
        viewModelScope.launch {
            interventionRepo.setDefaultInterventionMode(mode, applyToAllRules = applyToAllRules)
        }
    }

    fun setAppearanceMode(mode: AppearanceMode) {
        viewModelScope.launch {
            interventionRepo.setAppearanceMode(mode)
        }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            interventionRepo.setHapticFeedbackEnabled(enabled)
        }
    }

    fun setCooldownMs(packageName: String, cooldownMs: Int) {
        viewModelScope.launch {
            interventionRepo.setCooldownMs(packageName, cooldownMs)
        }
    }

    fun snooze15Minutes(packageName: String) {
        snoozeForMinutes(packageName, 15)
    }

    fun snoozeForMinutes(packageName: String, minutes: Int) {
        viewModelScope.launch {
            interventionRepo.snoozeForMinutes(packageName, minutes)
        }
    }

    fun allowOnceTemporary(packageName: String) {
        viewModelScope.launch {
            interventionRepo.allowOnceTemporary(packageName)
        }
    }

    fun resumeFromSnooze(packageName: String) {
        viewModelScope.launch {
            interventionRepo.clearSnooze(packageName)
        }
    }

    fun setGlobalProtectionPaused(paused: Boolean) {
        viewModelScope.launch {
            interventionRepo.setGlobalPause(paused)
        }
    }

    fun pauseGloballyForMinutes(minutes: Int) {
        viewModelScope.launch {
            interventionRepo.pauseGloballyForMinutes(minutes)
        }
    }

    fun resumeGlobalProtection() {
        viewModelScope.launch {
            interventionRepo.resumeGlobalProtection()
        }
    }

    fun allowOnceFromToast(toast: InterventionToastState) {
        viewModelScope.launch {
            interventionRepo.allowOnceForEvent(toast.eventId, toast.packageName)
        }
    }

    fun undoFromToast(toast: InterventionToastState) {
        viewModelScope.launch {
            interventionRepo.undoIntervention(toast.eventId, toast.packageName)
        }
    }

    fun dismissToast() {
        DiagnosticsBridge.dismissToast()
    }

    fun updateRemoteRules() {
        viewModelScope.launch {
            _isSyncingRules.value = true
            _syncMessage.value = null
            when (val result = rulesetRepo.syncFromRemoteOrFallback()) {
                is RulesetSyncResult.Success -> {
                    _syncMessage.value = when {
                        result.etagUnchanged ->
                            "Your focus rules are already up to date"
                        result.revertedToHardcoded ->
                            "Restored built-in focus rules for ${result.updatedApps} apps"
                        result.fromLocalCache ->
                            result.networkErrorReason
                                ?: "Using saved focus rules (${result.updatedApps} apps)"
                        else ->
                            "Updated focus rules for ${result.updatedApps} apps"
                    }
                }
                is RulesetSyncResult.Error -> {
                    _syncMessage.value = result.message
                }
            }
            _isSyncingRules.value = false
        }
    }

    fun setReclaimedSecondsPerCut(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setReclaimedSecondsPerCut(seconds)
        }
    }

    fun setShowInterventionBanner(show: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setShowInterventionBanner(show)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            interventionRepo.clearHistory()
        }
    }

    fun setQuietHours(enabled: Boolean, startMinutes: Int, endMinutes: Int) {
        viewModelScope.launch {
            interventionRepo.setQuietHours(enabled, startMinutes, endMinutes)
        }
    }

    fun resumeFromQuietHoursEarly() {
        viewModelScope.launch {
            interventionRepo.resumeFromQuietHoursEarly()
        }
    }

    fun setDailyBudgetCuts(budgetCuts: Int) {
        viewModelScope.launch {
            interventionRepo.setDailyBudgetCuts(budgetCuts)
        }
    }

    fun setStrictModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            interventionRepo.setStrictModeEnabled(enabled)
        }
    }

    fun toggleAlwaysOpenApp(identifier: String, enabled: Boolean) {
        viewModelScope.launch {
            interventionRepo.toggleAlwaysOpenApp(identifier, enabled)
        }
    }

    fun setPreferredPauseMinutes(minutes: Int) {
        viewModelScope.launch {
            interventionRepo.setPreferredPauseMinutes(minutes)
        }
    }

    fun setGlobalFeedTimeBudgetMinutes(minutes: Int) {
        viewModelScope.launch {
            interventionRepo.setGlobalFeedTimeBudgetMinutes(minutes)
        }
    }

    fun upsertCustomRule(
        packageName: String,
        displayName: String,
        feedLabel: String,
        viewIds: List<String>,
        tabFallbackLabel: String,
        homeTabLabel: String,
        interventionMode: InterventionMode
    ) {
        viewModelScope.launch {
            interventionRepo.upsertCustomRule(
                packageName = packageName,
                displayName = displayName,
                feedLabel = feedLabel,
                viewIds = viewIds,
                tabFallbackLabel = tabFallbackLabel,
                homeTabLabel = homeTabLabel,
                interventionMode = interventionMode
            )
            refreshInstalledApps(app)
        }
    }

    fun deleteCustomRule(packageName: String) {
        viewModelScope.launch {
            interventionRepo.deleteCustomRule(packageName)
            refreshInstalledApps(app)
        }
    }

    fun exportBackupToUri(
        context: Context,
        uri: Uri,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val json = backupManager.exportToJson()
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                        stream.flush()
                    } ?: error("Could not open file for writing.")
                    "Backup exported cleanly."
                }.getOrElse { err ->
                    "Backup failed: ${err.message ?: "unable to write file"}"
                }
            }
            onComplete(message)
        }
    }

    fun inspectBackupUri(
        context: Context,
        uri: Uri,
        onResult: (OutloopBackupPreview?, String?, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Could not read selected file.")
                    val preview = backupManager.inspectBackupJson(json)
                        ?: return@runCatching Triple(null, null, "This file isn\u2019t a valid Outloop backup.")
                    Triple(preview, json, null)
                }.getOrElse { err ->
                    Triple(null, null, err.message ?: "Unable to read backup file.")
                }
            }
            onResult(result.first, result.second, result.third)
        }
    }

    fun restoreBackupJson(
        rawJson: String,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch {
            val message = when (val result = backupManager.restoreFromJson(rawJson)) {
                is BackupRestoreResult.Success -> {
                    refreshInstalledApps(app)
                    "Restored ${result.restoredRules} app rules and ${result.restoredEvents} events."
                }
                is BackupRestoreResult.Error -> result.message
            }
            onComplete(message)
        }
    }

    private fun buildTodayTimeBuckets(events: List<InterventionEventEntity>): List<InsightBucketStat> {
        val counts = intArrayOf(0, 0, 0, 0)
        val cal = Calendar.getInstance()
        events.forEach { ev ->
            cal.timeInMillis = ev.timestampMs
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val idx = when (hour) {
                in 5..11 -> 0
                in 12..16 -> 1
                in 17..21 -> 2
                else -> 3
            }
            counts[idx]++
        }
        val labels = listOf(
            "Morning" to "Morning (5am\u201312pm)",
            "Afternoon" to "Afternoon (12pm\u20135pm)",
            "Evening" to "Evening (5pm\u201310pm)",
            "Night" to "Night (10pm\u20135am)"
        )
        val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        return labels.mapIndexed { idx, (shortLabel, detail) ->
            val c = counts[idx]
            InsightBucketStat(
                shortLabel = shortLabel,
                detailLabel = detail,
                count = c,
                fraction = if (c == 0) 0.08f else (c.toFloat() / max.toFloat()).coerceIn(0.14f, 1f)
            )
        }
    }

    private fun buildThirtyDayWeekBuckets(
        events: List<InterventionEventEntity>,
        nowMs: Long
    ): List<InsightBucketStat> {
        val bucketRanges = listOf(
            27 to 21,
            20 to 14,
            13 to 7,
            6 to 0
        )
        val labels = listOf(
            "Wk 1" to "3 weeks ago",
            "Wk 2" to "2 weeks ago",
            "Wk 3" to "Last week",
            "Wk 4" to "This week"
        )
        val counts = bucketRanges.map { (startAgo, endAgo) ->
            val start = InterventionRepository.startOfWeekDaysAgoMs(startAgo, nowMs)
            val end = InterventionRepository.startOfWeekDaysAgoMs(endAgo, nowMs) + 24L * 60 * 60 * 1000
            events.count { it.timestampMs in start until end }
        }
        val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        return labels.mapIndexed { idx, (shortLabel, detail) ->
            val c = counts[idx]
            InsightBucketStat(
                shortLabel = shortLabel,
                detailLabel = detail,
                count = c,
                fraction = if (c == 0) 0.08f else (c.toFloat() / max.toFloat()).coerceIn(0.14f, 1f)
            )
        }
    }

    private fun buildWeeklyDayBars(
        events: List<InterventionEventEntity>,
        nowMs: Long
    ): List<DayBarStat> {
        val cal = Calendar.getInstance()
        val dayNameFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val dateLabelFormat = SimpleDateFormat("MMM d", Locale.getDefault())
        val raw = (6 downTo 0).map { daysAgo ->
            val dayStart = InterventionRepository.startOfWeekDaysAgoMs(daysAgo, nowMs)
            val dayEnd = dayStart + 24L * 60 * 60 * 1000
            cal.timeInMillis = dayStart
            val letter = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "M"
                Calendar.TUESDAY -> "T"
                Calendar.WEDNESDAY -> "W"
                Calendar.THURSDAY -> "T"
                Calendar.FRIDAY -> "F"
                Calendar.SATURDAY -> "S"
                else -> "S"
            }
            val dayEvents = events.filter { it.timestampMs in dayStart until dayEnd }
            DayBarStat(
                dayLetter = letter,
                dayName = dayNameFormat.format(cal.time),
                dateLabel = dateLabelFormat.format(cal.time),
                count = dayEvents.size,
                reclaimedMinutes = dayEvents.sumOf { it.reclaimedSeconds } / 60,
                fraction = 0f,
                isToday = daysAgo == 0
            )
        }
        val max = raw.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        return raw.map { stat ->
            stat.copy(
                fraction = if (stat.count == 0) 0.08f else (stat.count.toFloat() / max.toFloat()).coerceIn(0.14f, 1f)
            )
        }
    }

    private fun computeStreakDays(
        events: List<InterventionEventEntity>,
        nowMs: Long
    ): Int {
        if (events.isEmpty()) return 0
        var streak = 0
        var daysAgo = 0
        while (daysAgo < 30) {
            val dayStart = InterventionRepository.startOfWeekDaysAgoMs(daysAgo, nowMs)
            val dayEnd = dayStart + 24L * 60 * 60 * 1000
            val hasActivity = events.any { it.timestampMs in dayStart until dayEnd }
            if (hasActivity) {
                streak++
                daysAgo++
            } else if (daysAgo == 0) {
                daysAgo++
            } else {
                break
            }
        }
        return streak
    }

    private fun computeBestStreakDays(
        events: List<InterventionEventEntity>,
        nowMs: Long
    ): Int {
        if (events.isEmpty()) return 0
        var best = 0
        var current = 0
        for (daysAgo in 29 downTo 0) {
            val dayStart = InterventionRepository.startOfWeekDaysAgoMs(daysAgo, nowMs)
            val dayEnd = dayStart + 24L * 60 * 60 * 1000
            if (events.any { it.timestampMs in dayStart until dayEnd }) {
                current++
                if (current > best) best = current
            } else {
                current = 0
            }
        }
        return best
    }

    private fun buildAppBreakdown(
        rules: List<TargetAppRuleEntity>,
        events: List<InterventionEventEntity>
    ): List<AppBreakdownStat> {
        val countsByPkg = events.groupingBy { it.packageName }.eachCount()
        val maxCount = (countsByPkg.values.maxOrNull() ?: 0).coerceAtLeast(1)
        return rules
            .sortedWith(compareByDescending<TargetAppRuleEntity> { countsByPkg[it.packageName] ?: 0 }.thenBy { it.displayName })
            .map { rule ->
                val count = countsByPkg[rule.packageName] ?: 0
                AppBreakdownStat(
                    packageName = rule.packageName,
                    displayName = rule.displayName,
                    label = "${rule.displayName} ${rule.feedLabel}",
                    count = count,
                    fraction = if (count == 0) 0f else (count.toFloat() / maxCount.toFloat()).coerceIn(0.06f, 1f)
                )
            }
    }

    private fun formatReclaimedDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        return when {
            minutes <= 0 -> "0 min"
            minutes < 60 -> "$minutes min"
            else -> {
                val h = minutes / 60
                val m = minutes % 60
                if (m == 0) "${h} h" else "$h h $m min"
            }
        }
    }

    private fun formatRelativeAgo(timestampMs: Long, nowMs: Long): String {
        val diffSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60L -> "Just now"
            diffSec < 3600L -> "${diffSec / 60L} min ago"
            diffSec < 86400L -> "${diffSec / 3600L} h ago"
            else -> "${diffSec / 86400L} d ago"
        }
    }

    private fun formatOutcomeSummary(outcome: InterventionOutcome): String = when (outcome) {
        InterventionOutcome.CONFIRMED -> "Completed, you went back"
        InterventionOutcome.ALLOWED_ONCE -> "Allowed once"
        InterventionOutcome.ATTEMPTED -> "Attempted"
        InterventionOutcome.FAILED -> "Could not confirm exit"
    }

    companion object {
        fun deriveProtectionStatus(
            isAccessibilityEnabled: Boolean,
            isServiceConnected: Boolean,
            activeInstalledCount: Int,
            isGloballyPaused: Boolean,
            isInQuietHours: Boolean = false
        ): ProtectionStatus = when {
            !isAccessibilityEnabled -> ProtectionStatus.PERMISSION_NEEDED
            !isServiceConnected -> ProtectionStatus.SERVICE_DISCONNECTED
            activeInstalledCount <= 0 -> ProtectionStatus.NO_APPS_ACTIVE
            isGloballyPaused -> ProtectionStatus.PAUSED
            isInQuietHours -> ProtectionStatus.QUIET_HOURS
            else -> ProtectionStatus.OPERATIONAL
        }

        fun filterCountedEvents(events: List<InterventionEventEntity>): List<InterventionEventEntity> =
            events.filter { it.outcome != InterventionOutcome.FAILED }
    }
}
