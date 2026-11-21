package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.FeedSurgeonApp
import com.example.data.local.AppPreferences
import com.example.data.local.AppearanceMode
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionOutcome
import com.example.data.repository.DiagnosticsBridge
import com.example.worker.RulesetSyncWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class FeedSurgeonService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastBlockTimestamps = ConcurrentHashMap<String, Long>()
    private val lastRecordedInterventionElapsed = ConcurrentHashMap<String, Long>()
    private val inFlightVerifications = ConcurrentHashMap.newKeySet<String>()
    private val lastHealthTimestamps = ConcurrentHashMap<String, Long>()
    private val lastLogTimestamps = ConcurrentHashMap<String, Long>()
    private val consecutiveTabFallbackCounts = ConcurrentHashMap<String, Int>()

    @Volatile
    private var activeRulesByPackage: Map<String, TargetAppRuleEntity> = emptyMap()

    @Volatile
    private var currentPrefs: AppPreferences = AppPreferences()

    private var overlayShield: AccessibilityOverlayShield? = null

    private val app: FeedSurgeonApp
        get() = applicationContext as FeedSurgeonApp

    @Volatile
    private var activeInstalledRuleCount: Int = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticsBridge.setServiceConnected(true)
        overlayShield = AccessibilityOverlayShield(this)

        // Pre-seed immediately so zero accessibility events are missed before Room emits
        val defaults = FeedSurgeonDatabase.defaultBuiltInRules()
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
        RulesetSyncWorker.schedulePeriodicSync(this)

        serviceScope.launch {
            app.interventionRepository.ensureDefaultRulesSeeded()
            app.rulesetRepository.ensureLocalCacheSeeded()
            app.rulesetRepository.autoSyncIfDue(force = false)
            app.interventionRepository.allRules.collectLatest { rules ->
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
            app.settingsDataStore.preferencesFlow.collectLatest { prefs ->
                currentPrefs = prefs
                refreshForegroundNotification()
                if (prefs.autoUpdateRules) {
                    RulesetSyncWorker.schedulePeriodicSync(this@FeedSurgeonService)
                } else {
                    RulesetSyncWorker.cancelPeriodicSync(this@FeedSurgeonService)
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

    private fun updateServicePackageScope(packages: List<String>) {
        val currentInfo = serviceInfo ?: AccessibilityServiceInfo()
        val scopedPackages = packages.distinct().ifEmpty {
            FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES.toList()
        }.toTypedArray()

        currentInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100L
            packageNames = scopedPackages
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
        val nowElapsed = SystemClock.elapsedRealtime()
        val nowMs = System.currentTimeMillis()
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

        val rule = activeRulesByPackage[pkg]
        if (rule == null) {
            if (captureLogs) {
                logEventThrottled(pkg, eventType, eventClassName, null, null, "SKIPPED:no_active_rule", nowElapsed)
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

        val isScrollEvent = eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val scrollSource = if (isScrollEvent) {
            runCatching { event.source }.getOrNull()
        } else {
            null
        }
        val rootNode = resolveTargetRootNode(pkg) ?: scrollSource?.takeIf {
            SelectorMatcher.matchesTargetPackage(it, pkg)
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

        if (rule.isCurrentlySnoozed(System.currentTimeMillis())) {
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

            serviceScope.launch {
                app.interventionRepository.updateSelectorHealth(
                    packageName = rule.packageName,
                    matchedCount = viewIdsMatchedCount,
                    totalCount = rule.viewIds.size.coerceAtLeast(1),
                    outdated = shouldMarkOutdated
                )
                if (shouldMarkOutdated) {
                    app.rulesetRepository.autoSyncIfDue(force = false)
                }
            }
        }

        executeIntervention(rootNode, rule, matchedSelector, nowElapsed)
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
        val lastLogged = lastLogTimestamps[pkg] ?: 0L
        if (!force && nowElapsed - lastLogged < 400L) return
        lastLogTimestamps[pkg] = nowElapsed
        val snapshot = app.eventLogger.captureSnapshot(rootNode)
        serviceScope.launch {
            app.eventLogger.logCapturedSnapshot(
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
        return SelectorMatcher.selectTargetWindowRoot(
            targetPackage = pkg,
            activeRoot = rootInActiveWindow,
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
        if (!isStillOnMatchedFeed(rule)) {
            if (eventId != null) {
                app.interventionRepository.updateInterventionOutcome(
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
            if (eventId != null) {
                app.interventionRepository.updateInterventionOutcome(
                    eventId,
                    if (cleared) InterventionOutcome.CONFIRMED else InterventionOutcome.FAILED
                )
            }
        } else if (eventId != null) {
            app.interventionRepository.updateInterventionOutcome(
                eventId,
                InterventionOutcome.FAILED
            )
        }
    }

    private fun executeIntervention(
        rootNode: AccessibilityNodeInfo,
        rule: TargetAppRuleEntity,
        matchedSelector: String,
        nowElapsed: Long
    ) {
        inFlightVerifications.add(rule.packageName)
        val lastRecorded = lastRecordedInterventionElapsed[rule.packageName] ?: 0L
        val shouldRecord = nowElapsed - lastRecorded >= DEDUP_WINDOW_MS
        if (shouldRecord) {
            lastRecordedInterventionElapsed[rule.packageName] = nowElapsed
        }

        val shield = overlayShield
        if (rule.interventionMode == InterventionMode.OVERLAY && shield != null) {
            val eventIdDeferred = CompletableDeferred<Long?>()
            serviceScope.launch {
                val eventId = if (shouldRecord) {
                    app.interventionRepository.recordIntervention(
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

            val useDarkTheme = when (currentPrefs.appearanceMode) {
                AppearanceMode.DARK -> true
                AppearanceMode.LIGHT -> false
                AppearanceMode.SYSTEM ->
                    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
            }

            shield.show(
                appName = rule.displayName,
                feedLabel = rule.feedLabel,
                useDarkTheme = useDarkTheme,
                hapticsEnabled = currentPrefs.hapticFeedbackEnabled,
                onExitFeed = {
                    lastBlockTimestamps[rule.packageName] = SystemClock.elapsedRealtime()
                    val freshRoot = resolveTargetRootNode(rule.packageName)
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
                            if (eventId != null) {
                                app.interventionRepository.allowOnceForEvent(eventId, rule.packageName)
                            } else {
                                app.interventionRepository.allowOnceTemporary(rule.packageName)
                            }
                        } finally {
                            inFlightVerifications.remove(rule.packageName)
                        }
                    }
                },
                onSnooze15Minutes = {
                    lastBlockTimestamps[rule.packageName] = SystemClock.elapsedRealtime()
                    serviceScope.launch {
                        try {
                            app.interventionRepository.snoozeForMinutes(rule.packageName, 15)
                            val eventId = eventIdDeferred.await()
                            if (eventId != null) {
                                app.interventionRepository.updateInterventionOutcome(
                                    eventId,
                                    InterventionOutcome.ALLOWED_ONCE
                                )
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
                val eventId = if (shouldRecord) {
                    app.interventionRepository.recordIntervention(
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

    override fun onInterrupt() {
        overlayShield?.dismiss()
        inFlightVerifications.clear()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlayShield?.dismiss()
        inFlightVerifications.clear()
        ForegroundProtectionNotifier.stopForegroundProtection(this)
        DiagnosticsBridge.setServiceConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayShield?.dismiss()
        inFlightVerifications.clear()
        ForegroundProtectionNotifier.stopForegroundProtection(this)
        DiagnosticsBridge.setServiceConnected(false)
        serviceScope.cancel()
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 2500L
    }
}
