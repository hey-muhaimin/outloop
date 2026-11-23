package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.FeedSurgeonApp
import com.example.data.local.AppPreferences
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionOutcome
import com.example.data.model.ProtectionStatus
import com.example.data.repository.DiagnosticsBridge
import com.example.data.repository.InterventionRepository
import com.example.worker.RulesetSyncWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class FeedSurgeonService : AccessibilityService() {

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            Log.e("FeedSurgeonService", "Unhandled service coroutine error", throwable)
        }
    )
    private val lastBlockTimestamps = ConcurrentHashMap<String, Long>()
    private val lastRecordedInterventionElapsed = ConcurrentHashMap<String, Long>()
    private val inFlightVerifications = ConcurrentHashMap.newKeySet<String>()
    private val lastHealthTimestamps = ConcurrentHashMap<String, Long>()
    private val lastLogTimestamps = ConcurrentHashMap<String, Long>()
    private val consecutiveTabFallbackCounts = ConcurrentHashMap<String, Int>()

    @Volatile
    private var allRulesByPackage: Map<String, TargetAppRuleEntity> = emptyMap()

    @Volatile
    private var activeRulesByPackage: Map<String, TargetAppRuleEntity> = emptyMap()

    @Volatile
    private var currentPrefs: AppPreferences = AppPreferences()

    private val foregroundMonitor = ForegroundPackageMonitor()
    private val sessionTracker = AppSessionTimeTracker()
    private val persistedTodayUsageSeconds = ConcurrentHashMap<String, Long>()
    private val observedDateKeyFlow = MutableStateFlow(InterventionRepository.dateKeyForMs())
    private var usageTickerJob: Job? = null
    private var overlayShield: AccessibilityOverlayShield? = null

    private val app: FeedSurgeonApp?
        get() = (applicationContext as? FeedSurgeonApp) ?: (application as? FeedSurgeonApp)

    @Volatile
    private var activeInstalledRuleCount: Int = 0

    @Volatile
    private var todayLoopsBrokenCount: Int = 0

    @Volatile
    private var todayTimeKeptText: String = "0 min"

    @Volatile
    private var currentStreakDays: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsBridge.setServiceConnected(true)
        overlayShield = AccessibilityOverlayShield(this)

        // Pre-seed immediately so zero accessibility events are missed before Room emits
        val defaults = FeedSurgeonDatabase.defaultBuiltInRules()
        allRulesByPackage = defaults.associateBy { it.packageName }
        activeRulesByPackage = defaults
            .filter { it.enabled }
            .associateBy { it.packageName }
        updateServicePackageScope(FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES.toList())

        val initialInstalled = InstalledAppDetector.getInstalledPackages(
            this,
            FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES.toList()
        )
        activeInstalledRuleCount = activeRulesByPackage.keys.count { it in initialInstalled }
        ForegroundProtectionNotifier.startForegroundProtection(this, activeInstalledRuleCount)

        // Schedule WorkManager periodic Gist sync directly from FeedSurgeonService lifecycle
        runCatching { RulesetSyncWorker.schedulePeriodicSync(this) }

        val hostApp = app ?: return

        serviceScope.launch {
            runCatching { hostApp.controlledAppsRepository.ensureDefaultRulesSeeded() }
            runCatching { hostApp.rulesetRepository.ensureLocalCacheSeeded() }
            runCatching { hostApp.rulesetRepository.autoSyncIfDue(force = false) }
            hostApp.controlledAppsRepository.allRules.collectLatest { rules ->
                allRulesByPackage = rules.associateBy { it.packageName }
                val enabledMap = rules.filter { it.enabled }.associateBy { it.packageName }
                activeRulesByPackage = enabledMap
                updateServicePackageScope(rules.map { it.packageName })

                val installedNow = InstalledAppDetector.getInstalledPackages(
                    this@FeedSurgeonService,
                    rules.map { it.packageName }
                )
                activeInstalledRuleCount = enabledMap.keys.count { it in installedNow }
                refreshForegroundNotification()
            }
        }

        serviceScope.launch {
            hostApp.settingsDataStore.preferencesFlow.collectLatest { prefs ->
                currentPrefs = prefs
                refreshForegroundNotification()
                runCatching {
                    if (prefs.autoUpdateRules) {
                        RulesetSyncWorker.schedulePeriodicSync(this@FeedSurgeonService)
                    } else {
                        RulesetSyncWorker.cancelPeriodicSync(this@FeedSurgeonService)
                    }
                }
            }
        }

        serviceScope.launch {
            InstalledAppDetector.observePackageChanges(this@FeedSurgeonService).collectLatest {
                val trackedPackages = activeRulesByPackage.keys.ifEmpty {
                    FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES
                }
                val installedNow = InstalledAppDetector.getInstalledPackages(
                    this@FeedSurgeonService,
                    trackedPackages
                )
                activeInstalledRuleCount = activeRulesByPackage.keys.count { it in installedNow }
                refreshForegroundNotification()
            }
        }

        serviceScope.launch {
            hostApp.interventionRepository.eventsLast30Days.collectLatest { events ->
                val nowMs = System.currentTimeMillis()
                val counted = events.filter { it.outcome != InterventionOutcome.FAILED }
                val todayStart = InterventionRepository.startOfTodayMs(nowMs)
                val todayEvents = counted.filter { it.timestampMs >= todayStart }
                todayLoopsBrokenCount = todayEvents.size
                val totalSeconds = todayEvents.sumOf { it.reclaimedSeconds }
                val minutes = totalSeconds / 60
                todayTimeKeptText = when {
                    minutes <= 0 -> if (todayEvents.isNotEmpty()) "1 min" else "0 min"
                    minutes < 60 -> "$minutes min"
                    minutes % 60 == 0 -> "${minutes / 60} h"
                    else -> "${minutes / 60} h ${minutes % 60} min"
                }
                var streak = 0
                var daysAgo = 0
                while (daysAgo < 30) {
                    val dayStart = InterventionRepository.startOfWeekDaysAgoMs(daysAgo, nowMs)
                    val dayEnd = dayStart + 24L * 60 * 60 * 1000
                    if (counted.any { it.timestampMs in dayStart until dayEnd }) {
                        streak++
                        daysAgo++
                    } else if (daysAgo == 0) {
                        daysAgo++
                    } else {
                        break
                    }
                }
                currentStreakDays = streak
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        serviceScope.launch {
            observedDateKeyFlow
                .flatMapLatest { dateKey ->
                    hostApp.controlledAppsRepository.observeDailyUsageForDate(dateKey)
                }
                .collectLatest { rows ->
                    persistedTodayUsageSeconds.clear()
                    rows.forEach { row ->
                        persistedTodayUsageSeconds[row.packageName] = row.trackedSeconds
                    }
                }
        }
    }

    private fun refreshForegroundNotification() {
        val prefs = currentPrefs
        val nowMs = System.currentTimeMillis()
        ForegroundProtectionNotifier.updateNotification(
            context = this,
            state = ForegroundProtectionNotifier.NotificationProtectionState(
                activeInstalledCount = activeInstalledRuleCount,
                isPaused = prefs.isGloballyPaused(nowMs),
                isQuietHours = prefs.isInQuietHours(nowMs),
                quietEndLabel = AppPreferences.formatMinutesOfDay(prefs.quietEndMinutes)
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ForegroundProtectionNotifier.startForegroundProtection(this, activeInstalledRuleCount)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Re-assert foreground status so clearing the UI from recents keeps monitoring active
        ForegroundProtectionNotifier.startForegroundProtection(this, activeInstalledRuleCount)
    }

    private fun updateServicePackageScope(@Suppress("UNUSED_PARAMETER") packages: List<String>) {
        val currentInfo = serviceInfo ?: AccessibilityServiceInfo()
        currentInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L
            // Keep packageNames unfiltered so TYPE_WINDOW_STATE_CHANGED monitors foreground
            // transitions into any monitored app (including custom apps) and clean exits out
            // of them; unmonitored apps are ignored immediately before any node inspection.
            packageNames = null
        }
        try {
            serviceInfo = currentInfo
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (!DiagnosticsBridge.isServiceConnected.value) {
            DiagnosticsBridge.setServiceConnected(true)
        }
        val prefs = currentPrefs
        val captureLogs = prefs.eventCaptureEnabled
        val eventType = event.eventType
        val eventClassName = event.className?.toString()
        val isWindowStateChanged = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        val rulesSnapshot = allRulesByPackage.ifEmpty {
            FeedSurgeonDatabase.defaultBuiltInRules().associateBy { it.packageName }
        }
        val activeRulesSnapshot = if (allRulesByPackage.isEmpty()) {
            rulesSnapshot.filterValues { it.enabled }
        } else {
            activeRulesByPackage
        }

        // Fast-path exit for high-frequency scroll/content events from unmonitored apps
        if (!isWindowStateChanged && rulesSnapshot[pkg] == null) {
            return
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        val nowMs = System.currentTimeMillis()
        val currentKey = InterventionRepository.dateKeyForMs(nowMs)
        if (observedDateKeyFlow.value != currentKey) {
            observedDateKeyFlow.value = currentKey
        }

        if (isWindowStateChanged) {
            when (
                val transition = foregroundMonitor.onWindowStateChanged(
                    packageName = pkg,
                    className = eventClassName,
                    rulesByPackage = rulesSnapshot,
                    hostPackageName = packageName,
                    isOverlayShowing = overlayShield?.isShowing() == true
                )
            ) {
                is ForegroundPackageMonitor.Transition.ExitedMonitoredApp -> {
                    flushSessionSlicesAsync(
                        sessionTracker.onPackageExited(
                            expectedPackage = transition.previousPackage,
                            nowElapsedMs = nowElapsed,
                            nowWallMs = nowMs
                        )
                    )
                    stopUsageTicker()
                    overlayShield?.dismiss()
                    inFlightVerifications.remove(transition.previousPackage)
                    consecutiveTabFallbackCounts.remove(transition.previousPackage)
                    DiagnosticsBridge.clearForegroundMonitoredApp(transition.previousPackage)
                    return
                }
                is ForegroundPackageMonitor.Transition.EnteredMonitoredApp -> {
                    flushSessionSlicesAsync(
                        sessionTracker.onPackageEntered(
                            packageName = pkg,
                            nowElapsedMs = nowElapsed,
                            nowWallMs = nowMs
                        )
                    )
                    startUsageTickerIfNeeded()
                    transition.previousPackage?.let { prev ->
                        if (prev != transition.packageName) {
                            overlayShield?.dismiss()
                            inFlightVerifications.remove(prev)
                            consecutiveTabFallbackCounts.remove(prev)
                        }
                    }
                    consecutiveTabFallbackCounts[pkg] = 0
                    DiagnosticsBridge.setForegroundMonitoredApp(pkg, transition.rule.displayName)
                    if (captureLogs) {
                        logEventThrottled(
                            pkg = pkg,
                            eventType = eventType,
                            eventClassName = eventClassName,
                            rootNode = null,
                            matchedSelector = null,
                            outcome = "FOREGROUND_ENTER:${transition.rule.displayName}",
                            nowElapsed = nowElapsed,
                            force = true
                        )
                    }
                }
                is ForegroundPackageMonitor.Transition.StayedInMonitoredApp -> {
                    flushSessionSlicesAsync(
                        sessionTracker.onPackageEntered(
                            packageName = pkg,
                            nowElapsedMs = nowElapsed,
                            nowWallMs = nowMs
                        )
                    )
                    startUsageTickerIfNeeded()
                    DiagnosticsBridge.setForegroundMonitoredApp(pkg, transition.rule.displayName)
                }
                ForegroundPackageMonitor.Transition.IgnoredTransientWindow,
                ForegroundPackageMonitor.Transition.UnmonitoredApp -> return
            }
        } else {
            // Ensure active session tracking resumes cleanly if the service connected mid-app
            if (sessionTracker.activePackage() == null) {
                flushSessionSlicesAsync(
                    sessionTracker.onPackageEntered(
                        packageName = pkg,
                        nowElapsedMs = nowElapsed,
                        nowWallMs = nowMs
                    )
                )
                startUsageTickerIfNeeded()
            }
        }

        if (pkg in prefs.alwaysOpenApps) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:always_open", nowElapsed)
            }
            return
        }

        if (prefs.isGloballyPaused(nowMs)) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:global_paused", nowElapsed)
            }
            return
        }
        if (prefs.isInQuietHours(nowMs)) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:quiet_hours", nowElapsed)
            }
            return
        }

        val rule = activeRulesSnapshot[pkg]
        if (rule == null) {
            if (captureLogs && rulesSnapshot[pkg] != null) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:rule_disabled", nowElapsed)
            }
            return
        }
        if (!rule.enabled) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:rule_disabled", nowElapsed)
            }
            return
        }

        if (overlayShield?.isShowing() == true) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:overlay_showing", nowElapsed)
            }
            return
        }

        if (pkg in inFlightVerifications) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:verification_in_flight", nowElapsed)
            }
            return
        }

        val dailyLimitReached = isDailyLimitReached(rule, nowElapsed)
        val isScrollEvent = isScrollMonitoringEvent(eventType)
        val scrollSource = if (isScrollEvent) {
            runCatching { event.source }.getOrNull()
        } else {
            null
        }
        val rootNode = resolveTargetRootNode(pkg) ?: scrollSource?.takeIf {
            SelectorMatcher.matchesTargetPackage(it, pkg)
        }

        if (dailyLimitReached) {
            if (rule.isCurrentlySnoozed(nowMs)) {
                if (captureLogs) {
                    logEventThrottled(pkg, eventType, eventClassName, rootNode, "daily_limit:${rule.dailyLimitMinutes}m", "SKIPPED:snoozed", nowElapsed)
                }
                return
            }
            val lastBlock = lastBlockTimestamps[pkg] ?: 0L
            if (nowElapsed - lastBlock < rule.cooldownMs) {
                if (captureLogs) {
                    logEventThrottled(pkg, eventType, eventClassName, rootNode, "daily_limit:${rule.dailyLimitMinutes}m", "SKIPPED:cooldown", nowElapsed)
                }
                return
            }
            lastBlockTimestamps[pkg] = nowElapsed
            val limitSelector = "daily_limit:${rule.dailyLimitMinutes}m"
            if (captureLogs) {
                logEventThrottled(
                    pkg = pkg,
                    eventType = eventType,
                    eventClassName = eventClassName,
                    rootNode = rootNode,
                    matchedSelector = limitSelector,
                    outcome = "TRIGGERED_DAILY_LIMIT:${rule.interventionMode.name}",
                    nowElapsed = nowElapsed,
                    force = true
                )
            }
            executeIntervention(
                rootNode = rootNode,
                rule = rule,
                matchedSelector = limitSelector,
                nowElapsed = nowElapsed,
                triggerMode = OverlayTriggerMode.DAILY_LIMIT_REACHED
            )
            return
        }

        if (rootNode == null) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:no_root_window", nowElapsed)
            }
            return
        }

        val matchedSelector = if (isScrollEvent) {
            SelectorMatcher.findMatchingScrollSelector(scrollSource, rootNode, rule)
        } else {
            SelectorMatcher.findMatchingSelector(rootNode, rule)
        }
        if (matchedSelector == null) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, rootNode, null, "NO_MATCH", nowElapsed)
            }
            return
        }

        if (rule.isCurrentlySnoozed(nowMs)) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, rootNode, matchedSelector, "SKIPPED:snoozed", nowElapsed)
            }
            return
        }

        val lastBlock = lastBlockTimestamps[pkg] ?: 0L
        if (nowElapsed - lastBlock < rule.cooldownMs) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, rootNode, matchedSelector, "SKIPPED:cooldown", nowElapsed)
            }
            return
        }
        lastBlockTimestamps[pkg] = nowElapsed

        if (captureLogs) {
            val triggerPrefix = if (isScrollEvent) "TRIGGERED_SCROLL" else "TRIGGERED"
            logEventThrottled(
                pkg,
                eventType,
                eventClassName,
                rootNode,
                matchedSelector,
                "$triggerPrefix:${rule.interventionMode.name}",
                nowElapsed,
                force = true
            )
        }

        // Update selector health without false-positiving on a single pre-inflation frame
        val lastHealth = lastHealthTimestamps[pkg] ?: 0L
        if (nowElapsed - lastHealth >= 500L) {
            lastHealthTimestamps[pkg] = nowElapsed
            val viewIdsMatchedCount = SelectorMatcher.countVisibleViewIdMatches(rootNode, rule)
            val isTabFallbackOnly = matchedSelector.startsWith("tab:") && viewIdsMatchedCount == 0
            val missStreak = if (isTabFallbackOnly) {
                (consecutiveTabFallbackCounts[pkg] ?: 0) + 1
            } else {
                0
            }
            consecutiveTabFallbackCounts[pkg] = missStreak
            val shouldMarkOutdated = missStreak >= 5

            val hostApp = app
            if (hostApp != null) {
                serviceScope.launch {
                    hostApp.interventionRepository.updateSelectorHealth(
                        packageName = rule.packageName,
                        matchedCount = viewIdsMatchedCount,
                        totalCount = rule.viewIds.size.coerceAtLeast(1),
                        outdated = shouldMarkOutdated
                    )
                    if (shouldMarkOutdated) {
                        hostApp.rulesetRepository.autoSyncIfDue(force = false)
                    }
                }
            }
        }

        executeIntervention(
            rootNode = rootNode,
            rule = rule,
            matchedSelector = matchedSelector,
            nowElapsed = nowElapsed,
            triggerMode = OverlayTriggerMode.FEED_GUARD
        )
    }

    private fun logEventThrottled(
        pkg: String,
        eventType: Int,
        eventClassName: String?,
        rootNode: AccessibilityNodeInfo?,
        matchedSelector: String?,
        outcome: String,
        nowElapsed: Long,
        force: Boolean = false
    ) {
        val hostApp = app ?: return
        val lastLogged = lastLogTimestamps[pkg] ?: 0L
        if (!force && nowElapsed - lastLogged < 400L) return
        lastLogTimestamps[pkg] = nowElapsed
        val snapshot = hostApp.eventLogger.captureSnapshot(rootNode)
        serviceScope.launch {
            hostApp.eventLogger.logCapturedSnapshot(
                packageName = pkg,
                eventType = eventType,
                eventClassName = eventClassName,
                snapshot = snapshot,
                matchedSelector = matchedSelector,
                outcome = outcome
            )
        }
    }

    private fun resolveTargetRootNode(pkg: String): AccessibilityNodeInfo? {
        val windowRoots = try {
            windows?.map { it.root } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        return SelectorMatcher.selectTargetWindowRoot(
            targetPackage = pkg,
            activeRoot = activeRoot,
            windowRoots = windowRoots
        )
    }

    private fun executeHomeTabThenBackFallback(
        rootNode: AccessibilityNodeInfo?,
        rule: TargetAppRuleEntity
    ) {
        val clickedHome = rootNode?.let {
            SelectorMatcher.findHomeTabNode(it, rule.homeTabLabel)
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } == true
        if (!clickedHome) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun isStillOnMatchedFeed(rule: TargetAppRuleEntity): Boolean {
        val verifyRoot = resolveTargetRootNode(rule.packageName) ?: return false
        return SelectorMatcher.findMatchingSelector(verifyRoot, rule) != null
    }

    private suspend fun verifyInterventionAndFallbackIfNeeded(
        rule: TargetAppRuleEntity,
        eventId: Long?
    ) {
        delay(350L)
        val hostApp = app
        if (!isStillOnMatchedFeed(rule)) {
            if (eventId != null && hostApp != null) {
                hostApp.interventionRepository.updateInterventionOutcome(
                    eventId,
                    InterventionOutcome.CONFIRMED
                )
            }
            return
        }

        if (rule.interventionMode == InterventionMode.HOME_TAB || rule.interventionMode == InterventionMode.OVERLAY) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            delay(300L)
            val cleared = !isStillOnMatchedFeed(rule)
            if (eventId != null && hostApp != null) {
                hostApp.interventionRepository.updateInterventionOutcome(
                    eventId,
                    if (cleared) InterventionOutcome.CONFIRMED else InterventionOutcome.FAILED
                )
            }
        } else if (eventId != null && hostApp != null) {
            hostApp.interventionRepository.updateInterventionOutcome(
                eventId,
                InterventionOutcome.FAILED
            )
        }
    }

    private fun executeIntervention(
        rootNode: AccessibilityNodeInfo?,
        rule: TargetAppRuleEntity,
        matchedSelector: String,
        nowElapsed: Long,
        triggerMode: OverlayTriggerMode = OverlayTriggerMode.FEED_GUARD
    ) {
        inFlightVerifications.add(rule.packageName)
        val lastRecorded = lastRecordedInterventionElapsed[rule.packageName] ?: 0L
        val shouldRecord = nowElapsed - lastRecorded >= DEDUP_WINDOW_MS
        if (shouldRecord) {
            lastRecordedInterventionElapsed[rule.packageName] = nowElapsed
        }

        val hostApp = app
        val shield = overlayShield
        val shouldShowDimOverlay = shield != null && (
            rule.interventionMode == InterventionMode.OVERLAY ||
                triggerMode == OverlayTriggerMode.DAILY_LIMIT_REACHED
            )
        if (shouldShowDimOverlay && shield != null) {
            val eventIdDeferred = CompletableDeferred<Long?>()
            serviceScope.launch {
                val eventId = if (shouldRecord && hostApp != null) {
                    hostApp.interventionRepository.recordIntervention(
                        rule = rule,
                        matchedSelector = matchedSelector,
                        actionTaken = InterventionMode.OVERLAY,
                        outcome = InterventionOutcome.ATTEMPTED
                    )
                } else {
                    null
                }
                eventIdDeferred.complete(eventId)
            }

            val (primaryActionLabel, secondaryActionLabel, usefulSummary) = resolveUsefulSurfaceCopy(rule)
            val dailyUsedSeconds = getTodayTrackedSeconds(rule.packageName, nowElapsed)

            shield.show(
                appName = rule.displayName,
                feedLabel = rule.feedLabel,
                useDarkTheme = false,
                hapticsEnabled = currentPrefs.hapticFeedbackEnabled,
                strictModeEnabled = currentPrefs.strictModeEnabled,
                todayLoopsBroken = todayLoopsBrokenCount + 1,
                timeKeptText = todayTimeKeptText,
                streakDays = currentStreakDays.coerceAtLeast(1),
                preferredPauseMinutes = currentPrefs.preferredPauseMinutes,
                primaryUsefulActionLabel = primaryActionLabel,
                secondaryUsefulActionLabel = secondaryActionLabel,
                usefulPartsSummary = usefulSummary,
                triggerMode = triggerMode,
                dailyUsedSeconds = dailyUsedSeconds,
                dailyLimitMinutes = rule.dailyLimitMinutes,
                onExitFeed = {
                    lastBlockTimestamps[rule.packageName] = SystemClock.elapsedRealtime()
                    val freshRoot = resolveTargetRootNode(rule.packageName) ?: rootNode
                    executeHomeTabThenBackFallback(freshRoot, rule)
                    serviceScope.launch {
                        try {
                            val eventId = eventIdDeferred.await()
                            verifyInterventionAndFallbackIfNeeded(rule, eventId)
                        } finally {
                            inFlightVerifications.remove(rule.packageName)
                        }
                    }
                },
                onAllowOnce = {
                    lastBlockTimestamps[rule.packageName] = SystemClock.elapsedRealtime()
                    serviceScope.launch {
                        try {
                            val eventId = eventIdDeferred.await()
                            if (hostApp != null) {
                                if (eventId != null) {
                                    hostApp.interventionRepository.allowOnceForEvent(eventId, rule.packageName)
                                } else {
                                    hostApp.interventionRepository.allowOnceTemporary(rule.packageName)
                                }
                            }
                        } finally {
                            inFlightVerifications.remove(rule.packageName)
                        }
                    }
                },
                onSnoozeMinutes = { minutes ->
                    lastBlockTimestamps[rule.packageName] = SystemClock.elapsedRealtime()
                    serviceScope.launch {
                        try {
                            if (hostApp != null) {
                                hostApp.interventionRepository.snoozeForMinutes(rule.packageName, minutes)
                                val eventId = eventIdDeferred.await()
                                if (eventId != null) {
                                    hostApp.interventionRepository.updateInterventionOutcome(
                                        eventId,
                                        InterventionOutcome.ALLOWED_ONCE
                                    )
                                }
                            }
                        } finally {
                            inFlightVerifications.remove(rule.packageName)
                        }
                    }
                },
                onAttachmentFailed = {
                    lastBlockTimestamps[rule.packageName] = SystemClock.elapsedRealtime()
                    val freshRoot = resolveTargetRootNode(rule.packageName) ?: rootNode
                    executeHomeTabThenBackFallback(freshRoot, rule)
                    serviceScope.launch {
                        try {
                            val eventId = eventIdDeferred.await()
                            verifyInterventionAndFallbackIfNeeded(rule, eventId)
                        } finally {
                            inFlightVerifications.remove(rule.packageName)
                        }
                    }
                }
            )
            return
        }

        when (rule.interventionMode) {
            InterventionMode.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            InterventionMode.HOME_TAB,
            InterventionMode.OVERLAY -> executeHomeTabThenBackFallback(rootNode, rule)
        }

        serviceScope.launch {
            try {
                val eventId = if (shouldRecord && hostApp != null) {
                    hostApp.interventionRepository.recordIntervention(
                        rule = rule,
                        matchedSelector = matchedSelector,
                        actionTaken = rule.interventionMode,
                        outcome = InterventionOutcome.ATTEMPTED
                    )
                } else {
                    null
                }
                verifyInterventionAndFallbackIfNeeded(rule, eventId)
            } finally {
                inFlightVerifications.remove(rule.packageName)
            }
        }
    }

    private fun startUsageTickerIfNeeded() {
        if (usageTickerJob?.isActive == true) return
        usageTickerJob = serviceScope.launch {
            while (isActive) {
                delay(USAGE_FLUSH_INTERVAL_MS)
                val nowElapsed = SystemClock.elapsedRealtime()
                val nowMs = System.currentTimeMillis()
                val dateKey = InterventionRepository.dateKeyForMs(nowMs)
                if (observedDateKeyFlow.value != dateKey) {
                    observedDateKeyFlow.value = dateKey
                }
                val slices = sessionTracker.flushActiveSession(nowElapsed, nowMs)
                if (slices.isNotEmpty()) {
                    persistUsageSlices(slices, nowMs)
                }
                val activePkg = sessionTracker.activePackage() ?: continue
                val rule = activeRulesByPackage[activePkg] ?: continue
                val prefs = currentPrefs
                if (activePkg in prefs.alwaysOpenApps ||
                    prefs.isGloballyPaused(nowMs) ||
                    prefs.isInQuietHours(nowMs) ||
                    !rule.enabled ||
                    rule.isCurrentlySnoozed(nowMs) ||
                    overlayShield?.isShowing() == true ||
                    activePkg in inFlightVerifications
                ) {
                    continue
                }
                if (isDailyLimitReached(rule, nowElapsed)) {
                    val lastBlock = lastBlockTimestamps[activePkg] ?: 0L
                    if (nowElapsed - lastBlock >= rule.cooldownMs) {
                        lastBlockTimestamps[activePkg] = nowElapsed
                        executeIntervention(
                            rootNode = resolveTargetRootNode(activePkg),
                            rule = rule,
                            matchedSelector = "daily_limit:${rule.dailyLimitMinutes}m",
                            nowElapsed = nowElapsed,
                            triggerMode = OverlayTriggerMode.DAILY_LIMIT_REACHED
                        )
                    }
                }
            }
        }
    }

    private fun stopUsageTicker() {
        usageTickerJob?.cancel()
        usageTickerJob = null
    }

    private fun flushSessionSlicesAsync(slices: List<AppSessionTimeTracker.TrackedUsageSlice>) {
        if (slices.isEmpty()) return
        val nowMs = System.currentTimeMillis()
        serviceScope.launch {
            persistUsageSlices(slices, nowMs)
        }
    }

    private suspend fun persistUsageSlices(
        slices: List<AppSessionTimeTracker.TrackedUsageSlice>,
        updatedAtMs: Long
    ) {
        val repo = app?.controlledAppsRepository ?: return
        val currentKey = InterventionRepository.dateKeyForMs(updatedAtMs)
        slices.forEach { slice ->
            if (slice.dateKey == currentKey) {
                persistedTodayUsageSeconds.compute(slice.packageName) { _, existing ->
                    (existing ?: 0L) + slice.deltaSeconds
                }
            }
            runCatching {
                repo.addTrackedUsageSeconds(
                    packageName = slice.packageName,
                    dateKey = slice.dateKey,
                    deltaSeconds = slice.deltaSeconds,
                    updatedAtMs = updatedAtMs
                )
            }
        }
    }

    override fun onInterrupt() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowMs = System.currentTimeMillis()
        flushSessionSlicesAsync(sessionTracker.flushActiveSession(nowElapsed, nowMs))
        sessionTracker.reset()
        stopUsageTicker()
        overlayShield?.dismiss()
        inFlightVerifications.clear()
        foregroundMonitor.reset()
        DiagnosticsBridge.clearForegroundMonitoredApp()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowMs = System.currentTimeMillis()
        flushSessionSlicesAsync(sessionTracker.flushActiveSession(nowElapsed, nowMs))
        sessionTracker.reset()
        stopUsageTicker()
        overlayShield?.dismiss()
        inFlightVerifications.clear()
        foregroundMonitor.reset()
        ForegroundProtectionNotifier.stopForegroundProtection(this)
        DiagnosticsBridge.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowMs = System.currentTimeMillis()
        val finalSlices = sessionTracker.flushActiveSession(nowElapsed, nowMs)
        val repo = app?.controlledAppsRepository
        if (finalSlices.isNotEmpty() && repo != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                finalSlices.forEach { slice ->
                    runCatching {
                        repo.addTrackedUsageSeconds(
                            packageName = slice.packageName,
                            dateKey = slice.dateKey,
                            deltaSeconds = slice.deltaSeconds,
                            updatedAtMs = nowMs
                        )
                    }
                }
            }
        }
        sessionTracker.reset()
        stopUsageTicker()
        overlayShield?.dismiss()
        inFlightVerifications.clear()
        foregroundMonitor.reset()
        ForegroundProtectionNotifier.stopForegroundProtection(this)
        DiagnosticsBridge.setServiceConnected(false)
        serviceScope.cancel()
    }

    private fun resolveUsefulSurfaceCopy(rule: TargetAppRuleEntity): Triple<String, String, String> =
        when (rule.packageName) {
            "com.instagram.android" -> Triple("Open DMs", "Go to Search", "DMs and Search are still open.")
            "com.google.android.youtube" -> Triple("Go to Home", "Go to Search", "Home and Search are still open.")
            "com.facebook.katana" -> Triple("Open Messenger", "Go to Home", "Messenger and Groups are still open.")
            "com.zhiliaoapp.musically" -> Triple("Open Inbox", "Open Profile", "Inbox and Profile are still open.")
            "com.twitter.android" -> Triple("Open Messages", "Go to Search", "Messages and Search are still open.")
            else -> Triple("Go to ${rule.homeTabLabel}", "Allow once", "${rule.homeTabLabel} is still open.")
        }

    internal fun isControlledPackage(packageName: String): Boolean {
        val rules = allRulesByPackage.ifEmpty {
            FeedSurgeonDatabase.defaultBuiltInRules().associateBy { it.packageName }
        }
        return rules.containsKey(packageName)
    }

    internal fun isScrollMonitoringEvent(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED

    internal fun seedTrackedUsageForTesting(packageName: String, persistedSeconds: Long) {
        persistedTodayUsageSeconds[packageName] = persistedSeconds.coerceAtLeast(0L)
    }

    internal fun getTodayTrackedSeconds(
        packageName: String,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ): Long {
        val persisted = persistedTodayUsageSeconds[packageName] ?: 0L
        val pending = sessionTracker.getPendingSeconds(packageName, nowElapsedMs)
        return persisted + pending
    }

    internal fun isDailyLimitReached(
        rule: TargetAppRuleEntity,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        if (!rule.hasDailyLimit()) return false
        return getTodayTrackedSeconds(rule.packageName, nowElapsedMs) >= rule.dailyLimitSeconds
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 2500L
        private const val USAGE_FLUSH_INTERVAL_MS = 30_000L
    }
}
