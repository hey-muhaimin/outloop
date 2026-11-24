package com.example

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import android.view.accessibility.AccessibilityEvent
import com.example.data.repository.DiagnosticsBridge
import com.example.service.FeedSurgeonService
import com.example.service.ForegroundPackageMonitor
import com.example.data.local.AppPreferences
import com.example.data.local.AppearanceMode
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.local.InterventionEventEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InspectedNode
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionOutcome
import com.example.data.model.LiveTreeSnapshot
import com.example.data.model.ProtectionStatus
import com.example.data.repository.BackupRestoreResult
import com.example.data.repository.ControlledAppsRepository
import com.example.data.repository.InterventionRepository
import com.example.data.repository.LocalBackupManager
import com.example.data.repository.RulesetRepository
import com.example.data.repository.RulesetSyncResult
import com.example.service.AccessibilityEventLogger
import com.example.service.AppSessionTimeTracker
import com.example.service.ForegroundProtectionNotifier
import com.example.service.InstalledAppDetector
import com.example.service.OverlayPresentationConfig
import com.example.service.OverlayTriggerMode
import com.example.service.ProtectionActionReceiver
import com.example.service.ProtectionHealthHelper
import com.example.service.SelectorMatcher
import com.example.service.TransparentPauseDimOverlay
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.ui.MainUiState
import com.example.ui.MainViewModel
import com.example.worker.RulesetSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Outloop", context.getString(R.string.app_name))
    }

    @Test
    fun `default built-in rules use overlay shield mode`() {
        val defaults = FeedSurgeonDatabase.defaultBuiltInRules()
        assertEquals(7, defaults.size)
        assertTrue(defaults.all { it.interventionMode == InterventionMode.OVERLAY })
    }

    @Test
    fun `selector matcher detects youtube shorts in snapshot and skips when snoozed`() {
        val ytRule = FeedSurgeonDatabase.defaultBuiltInRules().first {
            it.packageName == "com.google.android.youtube"
        }
        val snapshot = LiveTreeSnapshot(
            packageName = "com.google.android.youtube",
            appDisplayName = "YouTube",
            capturedAtMs = System.currentTimeMillis(),
            isDiagnosticSample = true,
            nodes = listOf(
                InspectedNode(0, "FrameLayout", null, null, isSelected = false),
                InspectedNode(
                    1,
                    "RecyclerView",
                    "com.google.android.youtube:id/reel_recycler",
                    null,
                    isSelected = false,
                    isMatched = true
                ),
                InspectedNode(2, "TabView", null, "Shorts", isSelected = true, isMatched = true)
            )
        )

        val matched = SelectorMatcher.matchSelectorInSnapshot(snapshot, ytRule)
        assertEquals("com.google.android.youtube:id/reel_recycler", matched)

        val snoozedRule = ytRule.copy(snoozeUntilMs = System.currentTimeMillis() + 60_000L)
        assertNull(SelectorMatcher.matchSelectorInSnapshot(snapshot, snoozedRule))
    }

    @Test
    fun `ruleset repository parses all seven bundled apps and seeds local cache`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        val repo = RulesetRepository(context, db.feedSurgeonDao(), settings)

        val parsed = repo.parseAndValidate(AppPreferences.BUNDLED_RULESET_JSON)
        assertNotNull(parsed)
        assertEquals("v2026.11.21", parsed!!.version)
        val packages = parsed.apps.map { it.packageName }.toSet()
        assertTrue(packages.containsAll(FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES))
        assertEquals(7, packages.size)

        val cacheFile = File(context.filesDir, "ruleset_cache.json")
        cacheFile.delete()
        repo.ensureLocalCacheSeeded()
        assertTrue(cacheFile.exists())
        val cachedParsed = repo.parseAndValidate(cacheFile.readText())
        assertNotNull(cachedParsed)
        assertEquals(7, cachedParsed!!.apps.size)
    }

    @Test
    fun `autoSyncIfDue uses local cache when autoUpdateRules is disabled`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        settings.setAutoUpdateRules(false)
        val repo = RulesetRepository(context, db.feedSurgeonDao(), settings)

        repo.ensureLocalCacheSeeded()
        val result = repo.autoSyncIfDue(force = false)
        assertTrue(result is RulesetSyncResult.Success)
        val success = result as RulesetSyncResult.Success
        assertTrue(success.fromLocalCache)
        assertEquals(7, success.updatedApps)
    }

    @Test
    fun `installed app detector finds installed enabled packages accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowPm = shadowOf(context.packageManager)
        shadowPm.installPackage(
            PackageInfo().apply {
                packageName = "com.google.android.youtube"
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    packageName = "com.google.android.youtube"
                    enabled = true
                }
            }
        )
        shadowPm.installPackage(
            PackageInfo().apply {
                packageName = "com.facebook.katana"
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    packageName = "com.facebook.katana"
                    enabled = false
                }
            }
        )

        val installed = InstalledAppDetector.getInstalledPackages(
            context,
            FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES
        )
        assertTrue("com.google.android.youtube" in installed)
        assertFalse("com.facebook.katana" in installed)
        assertFalse("com.instagram.android" in installed)
    }

    @Test
    fun `accessibility event logger enforces strict zero data logging without disk writes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = AccessibilityEventLogger(context)
        logger.clear()
        assertEquals(0L, logger.getFileSizeBytes())

        // Seed a legacy file to confirm clear() and getFileSizeBytes() purge it
        logger.getFile().writeText("legacy_entry\n")
        assertTrue(logger.getFile().exists())
        logger.clear()
        assertFalse(logger.getFile().exists())

        logger.logEvent(
            packageName = "com.google.android.youtube",
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            eventClassName = "androidx.recyclerview.widget.RecyclerView",
            rootNode = null,
            matchedSelector = "com.google.android.youtube:id/reel_recycler",
            outcome = "TRIGGERED:OVERLAY"
        )

        assertEquals("", logger.readTail(20))
        assertEquals(0L, logger.getFileSizeBytes())
        assertFalse(logger.getFile().exists())
        assertNull(logger.getShareUri())
        val snapshot = logger.captureSnapshot(null)
        assertEquals("redacted", snapshot.rootPackage)
        assertTrue(snapshot.visibleViewIds.isEmpty())
        assertTrue(snapshot.selectedTabs.isEmpty())
    }

    @Test
    fun `ruleset sync worker handles autoUpdateRules disabled cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val settings = SettingsDataStore(context)
        settings.setAutoUpdateRules(false)

        RulesetSyncWorker.schedulePeriodicSync(context)
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RulesetSyncWorker.UNIQUE_PERIODIC_WORK_NAME)
            .get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.first().state)

        val worker = TestListenableWorkerBuilder<RulesetSyncWorker>(context).build()
        val result = worker.doWork()
        assertTrue(result is ListenableWorker.Result.Success)
    }

    @Test
    fun `foreground protection notifier builds ongoing silent notification`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = ForegroundProtectionNotifier.buildNotification(context, 3)
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(
            "Outloop active",
            notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        assertEquals(
            "Guarding 3 selected apps on your device",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        )
    }

    @Test
    fun `ruleset repository verifies gist etag via HEAD before downloading full ruleset`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        settings.saveFetchedRuleset(
            version = AppPreferences.DEFAULT_RULESET_VERSION,
            rawJson = AppPreferences.BUNDLED_RULESET_JSON,
            checkedAtMs = 1L,
            etag = "\"abc123etag\""
        )

        val seenMethods = mutableListOf<String>()
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                seenMethods += req.method
                if (req.method == "HEAD") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("ETag", "\"abc123etag\"")
                        .body("".toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    throw AssertionError("Unexpected GET request when HEAD ETag matched")
                }
            }
            .build()

        val repo = RulesetRepository(context, db.feedSurgeonDao(), settings, okHttpClient = mockClient)
        repo.ensureLocalCacheSeeded()
        settings.saveFetchedRuleset(
            version = AppPreferences.DEFAULT_RULESET_VERSION,
            rawJson = AppPreferences.BUNDLED_RULESET_JSON,
            checkedAtMs = 1L,
            etag = "\"abc123etag\""
        )
        val result = repo.syncFromRemoteOrFallback()

        assertTrue(result is RulesetSyncResult.Success)
        val success = result as RulesetSyncResult.Success
        assertTrue(success.etagUnchanged)
        assertTrue(success.fromLocalCache)
        assertEquals(listOf("HEAD"), seenMethods)
    }

    @Test
    fun `ruleset repository reverts to hardcoded json on repeated network failures`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        settings.saveFetchedRuleset(
            version = "v2099.01.01",
            rawJson = "{corrupted_json",
            checkedAtMs = 123L,
            etag = "\"stale-etag\""
        )
        File(context.filesDir, "ruleset_cache.json").writeText("{corrupted_json")

        val failingClient = OkHttpClient.Builder()
            .addInterceptor { throw java.io.IOException("Simulated offline error") }
            .build()

        val repo = RulesetRepository(context, db.feedSurgeonDao(), settings, okHttpClient = failingClient)
        val fallback = repo.revertToHardcodedRuleset("Repeated failures")

        assertTrue(fallback is RulesetSyncResult.Success)
        val success = fallback as RulesetSyncResult.Success
        assertTrue(success.revertedToHardcoded)
        assertEquals(AppPreferences.DEFAULT_RULESET_VERSION, success.version)
        assertEquals(7, success.updatedApps)
        assertEquals("", settings.preferencesFlow.first().rulesetEtag)
        assertEquals(7, db.feedSurgeonDao().getAllRulesSnapshot().size)
    }

    @Test
    fun `strict package root selector ignores unrelated active window`() {
        val unrelatedActive = AccessibilityNodeInfo().apply {
            packageName = "com.android.launcher"
        }
        val youtubeWindow = AccessibilityNodeInfo().apply {
            packageName = "com.google.android.youtube"
        }

        val resolved = SelectorMatcher.selectTargetWindowRoot(
            targetPackage = "com.google.android.youtube",
            activeRoot = unrelatedActive,
            windowRoots = listOf(unrelatedActive, youtubeWindow)
        )
        assertSame(youtubeWindow, resolved)

        val missing = SelectorMatcher.selectTargetWindowRoot(
            targetPackage = "com.instagram.android",
            activeRoot = unrelatedActive,
            windowRoots = listOf(unrelatedActive, youtubeWindow)
        )
        assertNull(missing)
    }

    @Test
    fun `intervention repository records outcomes and filters failed events from insights`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        val repo = InterventionRepository(db.feedSurgeonDao(), settings)
        repo.ensureDefaultRulesSeeded()
        repo.clearHistory()

        val ytRule = db.feedSurgeonDao().getRuleByPackage("com.google.android.youtube")!!
        val confirmedId = repo.recordIntervention(
            rule = ytRule,
            matchedSelector = "com.google.android.youtube:id/reel_recycler",
            actionTaken = InterventionMode.HOME_TAB,
            outcome = InterventionOutcome.ATTEMPTED
        )
        repo.updateInterventionOutcome(confirmedId, InterventionOutcome.CONFIRMED)

        val failedId = repo.recordIntervention(
            rule = ytRule,
            matchedSelector = "tab:\"Shorts\"",
            actionTaken = InterventionMode.BACK,
            outcome = InterventionOutcome.ATTEMPTED
        )
        repo.updateInterventionOutcome(failedId, InterventionOutcome.FAILED)

        val allowedId = repo.recordIntervention(
            rule = ytRule,
            matchedSelector = "com.google.android.youtube:id/shorts_player",
            actionTaken = InterventionMode.OVERLAY,
            outcome = InterventionOutcome.ATTEMPTED
        )
        repo.allowOnceForEvent(allowedId, ytRule.packageName)

        val allEvents = repo.recentEvents.first()
        assertEquals(3, allEvents.size)
        assertEquals(
            InterventionOutcome.CONFIRMED,
            allEvents.first { it.id == confirmedId }.outcome
        )
        assertEquals(
            InterventionOutcome.FAILED,
            allEvents.first { it.id == failedId }.outcome
        )
        val allowedEvent = allEvents.first { it.id == allowedId }
        assertEquals(InterventionOutcome.ALLOWED_ONCE, allowedEvent.outcome)
        assertTrue(allowedEvent.wasAllowedOnce)

        val counted = MainViewModel.filterCountedEvents(allEvents)
        assertEquals(2, counted.size)
        assertFalse(counted.any { it.id == failedId })
    }

    @Test
    fun `migration 2 to 3 adds outcome column defaulting to CONFIRMED`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_2_3_test.db"
        context.deleteDatabase(dbName)
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS intervention_events (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            packageName TEXT NOT NULL,
                            appName TEXT NOT NULL,
                            feedLabel TEXT NOT NULL,
                            matchedSelector TEXT NOT NULL,
                            actionTaken TEXT NOT NULL,
                            timestampMs INTEGER NOT NULL,
                            reclaimedSeconds INTEGER NOT NULL,
                            wasAllowedOnce INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = factory.create(config)
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO intervention_events (packageName, appName, feedLabel, matchedSelector, actionTaken, timestampMs, reclaimedSeconds, wasAllowedOnce)
            VALUES ('com.google.android.youtube', 'YouTube', 'Shorts', 'id/reel_recycler', 'OVERLAY', 1000, 165, 0)
            """.trimIndent()
        )
        FeedSurgeonDatabase.MIGRATION_2_3.migrate(db)

        val cursor = db.query("SELECT outcome FROM intervention_events")
        assertTrue(cursor.moveToFirst())
        assertEquals("CONFIRMED", cursor.getString(0))
        cursor.close()
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `deriveProtectionStatus distinguishes all five operational states`() {
        assertEquals(
            ProtectionStatus.PERMISSION_NEEDED,
            MainViewModel.deriveProtectionStatus(
                isAccessibilityEnabled = false,
                isServiceConnected = false,
                activeInstalledCount = 2,
                isGloballyPaused = false
            )
        )
        assertEquals(
            ProtectionStatus.SERVICE_DISCONNECTED,
            MainViewModel.deriveProtectionStatus(
                isAccessibilityEnabled = true,
                isServiceConnected = false,
                activeInstalledCount = 2,
                isGloballyPaused = false
            )
        )
        assertEquals(
            ProtectionStatus.NO_APPS_ACTIVE,
            MainViewModel.deriveProtectionStatus(
                isAccessibilityEnabled = true,
                isServiceConnected = true,
                activeInstalledCount = 0,
                isGloballyPaused = false
            )
        )
        assertEquals(
            ProtectionStatus.PAUSED,
            MainViewModel.deriveProtectionStatus(
                isAccessibilityEnabled = true,
                isServiceConnected = true,
                activeInstalledCount = 1,
                isGloballyPaused = true
            )
        )
        assertEquals(
            ProtectionStatus.OPERATIONAL,
            MainViewModel.deriveProtectionStatus(
                isAccessibilityEnabled = true,
                isServiceConnected = true,
                activeInstalledCount = 2,
                isGloballyPaused = false
            )
        )
    }

    @Test
    fun `global pause and app snooze persist and clear cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        val repo = InterventionRepository(db.feedSurgeonDao(), settings)
        repo.ensureDefaultRulesSeeded()

        repo.pauseGloballyForMinutes(15)
        assertTrue(settings.preferencesFlow.first().isGloballyPaused())
        repo.resumeGlobalProtection()
        assertFalse(settings.preferencesFlow.first().isGloballyPaused())

        repo.snoozeForMinutes("com.google.android.youtube", 15)
        val snoozedRule = db.feedSurgeonDao().getRuleByPackage("com.google.android.youtube")!!
        assertTrue(snoozedRule.isCurrentlySnoozed())
        repo.clearSnooze("com.google.android.youtube")
        val resumedRule = db.feedSurgeonDao().getRuleByPackage("com.google.android.youtube")!!
        assertFalse(resumedRule.isCurrentlySnoozed())
    }

    @Test
    fun `quiet hours window evaluates daytime overnight boundaries and override accurately`() {
        // Daytime window: 09:00 (540) to 17:00 (1020)
        assertTrue(AppPreferences.isWithinQuietWindow(540, 540, 1020))
        assertTrue(AppPreferences.isWithinQuietWindow(800, 540, 1020))
        assertFalse(AppPreferences.isWithinQuietWindow(1020, 540, 1020))
        assertFalse(AppPreferences.isWithinQuietWindow(539, 540, 1020))

        // Overnight window: 22:00 (1320) to 07:00 (420)
        assertTrue(AppPreferences.isWithinQuietWindow(1320, 1320, 420))
        assertTrue(AppPreferences.isWithinQuietWindow(60, 1320, 420))
        assertFalse(AppPreferences.isWithinQuietWindow(420, 1320, 420))
        assertFalse(AppPreferences.isWithinQuietWindow(720, 1320, 420))

        // Equal start and end disables window matching
        assertFalse(AppPreferences.isWithinQuietWindow(600, 600, 600))

        // 24-hour full-day test: verify override suspends active quiet hours
        val now = System.currentTimeMillis()
        val overridden = AppPreferences(
            quietHoursEnabled = true,
            quietStartMinutes = 0,
            quietEndMinutes = 23 * 60 + 59,
            quietOverrideUntilMs = now + 60_000L
        )
        assertFalse(overridden.isInQuietHours(now))
    }

    @Test
    fun `deriveProtectionStatus returns QUIET_HOURS when enabled and not globally paused`() {
        assertEquals(
            ProtectionStatus.QUIET_HOURS,
            MainViewModel.deriveProtectionStatus(
                isAccessibilityEnabled = true,
                isServiceConnected = true,
                activeInstalledCount = 3,
                isGloballyPaused = false,
                isInQuietHours = true
            )
        )
        assertEquals(
            ProtectionStatus.PAUSED,
            MainViewModel.deriveProtectionStatus(
                isAccessibilityEnabled = true,
                isServiceConnected = true,
                activeInstalledCount = 3,
                isGloballyPaused = true,
                isInQuietHours = true
            )
        )
    }

    @Test
    fun `ruleset repository enforces trusted https endpoints`() {
        assertTrue(RulesetRepository.isTrustedHttpsUrl(AppPreferences.DEFAULT_GIST_URL))
        assertFalse(RulesetRepository.isTrustedHttpsUrl("http://example.com/ruleset.json"))
        assertFalse(RulesetRepository.isTrustedHttpsUrl("https://localhost/ruleset.json"))
        assertFalse(RulesetRepository.isTrustedHttpsUrl(""))
    }

    @Test
    fun `foreground protection notifier attaches Pause 15 min and Resume actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val activeNotif = ForegroundProtectionNotifier.buildNotification(
            context = context,
            state = ForegroundProtectionNotifier.NotificationProtectionState(
                activeInstalledCount = 2,
                isPaused = false,
                isQuietHours = false
            )
        )
        assertEquals(1, activeNotif.actions.size)
        assertEquals("Pause 15 min", activeNotif.actions[0].title.toString())

        val pausedNotif = ForegroundProtectionNotifier.buildNotification(
            context = context,
            state = ForegroundProtectionNotifier.NotificationProtectionState(
                activeInstalledCount = 2,
                isPaused = true,
                isQuietHours = false
            )
        )
        assertEquals(1, pausedNotif.actions.size)
        assertEquals("Resume", pausedNotif.actions[0].title.toString())

        val quietNotif = ForegroundProtectionNotifier.buildNotification(
            context = context,
            state = ForegroundProtectionNotifier.NotificationProtectionState(
                activeInstalledCount = 2,
                isPaused = false,
                isQuietHours = true,
                quietEndLabel = "7:00 AM"
            )
        )
        assertEquals(
            "Quiet hours active",
            quietNotif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        )
        assertEquals(1, quietNotif.actions.size)
        assertEquals("Resume", quietNotif.actions[0].title.toString())
    }

    @Test
    fun `protection action receiver pauses and resumes protection via settings`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>() as FeedSurgeonApp
        context.interventionRepository.ensureDefaultRulesSeeded()
        context.interventionRepository.resumeGlobalProtection()

        val receiver = ProtectionActionReceiver()
        receiver.onReceive(context, Intent(ProtectionActionReceiver.ACTION_PAUSE_15_MIN))
        var attempts = 0
        while (!context.settingsDataStore.preferencesFlow.first().isGloballyPaused() && attempts < 40) {
            Thread.sleep(50)
            attempts++
        }
        assertTrue(context.settingsDataStore.preferencesFlow.first().isGloballyPaused())

        receiver.onReceive(context, Intent(ProtectionActionReceiver.ACTION_RESUME_PROTECTION))
        attempts = 0
        while (context.settingsDataStore.preferencesFlow.first().isGloballyPaused() && attempts < 40) {
            Thread.sleep(50)
            attempts++
        }
        assertFalse(context.settingsDataStore.preferencesFlow.first().isGloballyPaused())
    }

    @Test
    fun `local backup manager exports previews validates and restores rules events and preferences`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val dao = db.feedSurgeonDao()
        val settings = SettingsDataStore(context)
        val repo = InterventionRepository(dao, settings)
        val backupManager = LocalBackupManager(dao, settings)

        repo.ensureDefaultRulesSeeded()
        repo.clearHistory()
        dao.upsertRule(
            TargetAppRuleEntity(
                packageName = "com.custom.shortapp",
                displayName = "ShortApp",
                feedLabel = "Clips",
                enabled = true,
                interventionMode = InterventionMode.BACK,
                cooldownMs = 900,
                homeTabLabel = "Home",
                viewIdsRaw = "com.custom.shortapp:id/clips",
                tabFallbackLabel = "Clips",
                isBuiltIn = false,
                matchedSelectorCount = 1,
                totalSelectorCount = 1
            )
        )
        dao.insertEvent(
            InterventionEventEntity(
                packageName = "com.custom.shortapp",
                appName = "ShortApp",
                feedLabel = "Clips",
                matchedSelector = "com.custom.shortapp:id/clips",
                actionTaken = InterventionMode.BACK,
                timestampMs = 1_750_000_000_000L,
                reclaimedSeconds = 180,
                wasAllowedOnce = false,
                outcome = InterventionOutcome.CONFIRMED
            )
        )
        settings.setAppearanceMode(AppearanceMode.DARK)
        settings.setQuietHours(enabled = true, startMinutes = 22 * 60, endMinutes = 7 * 60)
        settings.setDailyBudgetCuts(15)
        settings.setStrictModeEnabled(false)
        settings.setPreferredPauseMinutes(30)
        repo.toggleAlwaysOpenApp("Slack", true)

        val json = backupManager.exportToJson()
        val preview = backupManager.inspectBackupJson(json)
        assertNotNull(preview)
        assertEquals(8, preview!!.ruleCount)
        assertEquals(1, preview.customRuleCount)
        assertEquals(1, preview.eventCount)

        // Mutate state cleanly, then restore
        dao.deleteCustomRule("com.custom.shortapp")
        dao.clearAllEvents()
        settings.setAppearanceMode(AppearanceMode.LIGHT)
        settings.setQuietHours(enabled = false, startMinutes = 0, endMinutes = 0)
        settings.setDailyBudgetCuts(0)
        settings.setStrictModeEnabled(true)
        settings.setPreferredPauseMinutes(15)
        repo.toggleAlwaysOpenApp("Slack", false)

        val invalidResult = backupManager.restoreFromJson("{invalid json")
        assertTrue(invalidResult is BackupRestoreResult.Error)

        val restored = backupManager.restoreFromJson(json)
        assertTrue(restored is BackupRestoreResult.Success)
        val success = restored as BackupRestoreResult.Success
        assertEquals(8, success.restoredRules)
        assertEquals(1, success.restoredEvents)

        val restoredCustom = dao.getRuleByPackage("com.custom.shortapp")
        assertNotNull(restoredCustom)
        assertEquals("ShortApp", restoredCustom!!.displayName)
        assertEquals(1, dao.getAllEventsSnapshot().size)

        val restoredPrefs = settings.preferencesFlow.first()
        assertEquals(AppearanceMode.DARK, restoredPrefs.appearanceMode)
        assertTrue(restoredPrefs.quietHoursEnabled)
        assertEquals(15, restoredPrefs.dailyBudgetCuts)
        assertFalse(restoredPrefs.strictModeEnabled)
        assertEquals(30, restoredPrefs.preferredPauseMinutes)
        assertTrue("Slack" in restoredPrefs.alwaysOpenApps)

        // Clean up test mutations so shared Robolectric DB/DataStore remains pristine for other tests
        dao.deleteCustomRule("com.custom.shortapp")
        dao.clearAllEvents()
        settings.setQuietHours(enabled = false, startMinutes = 22 * 60, endMinutes = 7 * 60)
        settings.setDailyBudgetCuts(0)
        settings.setStrictModeEnabled(true)
        settings.setPreferredPauseMinutes(15)
        repo.toggleAlwaysOpenApp("Slack", false)
        settings.setAppearanceMode(AppearanceMode.LIGHT)
    }

    @Test
    fun `scroll selector matcher matches visible scroll source viewId and falls back cleanly`() {
        val ytRule = FeedSurgeonDatabase.defaultBuiltInRules().first {
            it.packageName == "com.google.android.youtube"
        }
        val matchingSource = AccessibilityNodeInfo().apply {
            packageName = "com.google.android.youtube"
            viewIdResourceName = "com.google.android.youtube:id/reel_recycler"
            isVisibleToUser = true
        }
        val unrelatedRoot = AccessibilityNodeInfo().apply {
            packageName = "com.google.android.youtube"
        }

        val directMatch = SelectorMatcher.findMatchingScrollSelector(
            sourceNode = matchingSource,
            root = unrelatedRoot,
            rule = ytRule
        )
        assertEquals("com.google.android.youtube:id/reel_recycler", directMatch)

        val nonMatchingSource = AccessibilityNodeInfo().apply {
            packageName = "com.google.android.youtube"
            viewIdResourceName = "com.google.android.youtube:id/home_feed"
            isVisibleToUser = true
        }
        val noMatch = SelectorMatcher.findMatchingScrollSelector(
            sourceNode = nonMatchingSource,
            root = unrelatedRoot,
            rule = ytRule
        )
        assertNull(noMatch)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `detectShortFormFeedPattern detects vertical feed containers and ignores messages or search`() {
        val igRule = FeedSurgeonDatabase.defaultBuiltInRules().first {
            it.packageName == "com.instagram.android"
        }
        val rootNode = AccessibilityNodeInfo().apply {
            packageName = "com.instagram.android"
            isVisibleToUser = true
            setBoundsInScreen(android.graphics.Rect(0, 0, 1080, 2400))
        }

        // Scroll container with unknown exact ID but vertical short-form layout tag hint
        val verticalShortFormPager = AccessibilityNodeInfo().apply {
            packageName = "com.instagram.android"
            className = "androidx.viewpager2.widget.ViewPager2"
            viewIdResourceName = "com.instagram.android:id/vertical_reels_feed_pager"
            isScrollable = true
            isVisibleToUser = true
            setBoundsInScreen(android.graphics.Rect(0, 100, 1080, 2300))
        }
        val detected = SelectorMatcher.detectShortFormFeedPattern(
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            sourceNode = verticalShortFormPager,
            root = rootNode,
            rule = igRule
        )
        assertEquals(SelectorMatcher.PATTERN_VERTICAL_FEED_CONTAINER, detected)

        // DM inbox RecyclerView must NOT match even though scrollable and full screen
        val dmInboxList = AccessibilityNodeInfo().apply {
            packageName = "com.instagram.android"
            className = "androidx.recyclerview.widget.RecyclerView"
            viewIdResourceName = "com.instagram.android:id/dm_inbox_thread_list"
            isScrollable = true
            isVisibleToUser = true
            setBoundsInScreen(android.graphics.Rect(0, 100, 1080, 2300))
        }
        val dmMatch = SelectorMatcher.detectShortFormFeedPattern(
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            sourceNode = dmInboxList,
            root = rootNode,
            rule = igRule
        )
        assertNull(dmMatch)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `detectShortFormFeedPattern ignores scroll events from unmonitored packages and non-scrolling views`() {
        val ytRule = FeedSurgeonDatabase.defaultBuiltInRules().first {
            it.packageName == "com.google.android.youtube"
        }
        val rootNode = AccessibilityNodeInfo().apply {
            packageName = "com.google.android.youtube"
            isVisibleToUser = true
            setBoundsInScreen(android.graphics.Rect(0, 0, 1080, 2400))
        }

        // Scroll event from an unmonitored package (e.g. settings) must return null
        val unmonitoredNode = AccessibilityNodeInfo().apply {
            packageName = "com.android.settings"
            className = "androidx.recyclerview.widget.RecyclerView"
            isScrollable = true
            isVisibleToUser = true
            setBoundsInScreen(android.graphics.Rect(0, 100, 1080, 2300))
        }
        val unmonitoredMatch = SelectorMatcher.detectShortFormFeedPattern(
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            sourceNode = unmonitoredNode,
            root = rootNode,
            rule = ytRule
        )
        assertNull(unmonitoredMatch)

        // Non-scrolling container without layout ID hint must return null
        val staticView = AccessibilityNodeInfo().apply {
            packageName = "com.google.android.youtube"
            className = "android.widget.FrameLayout"
            isScrollable = false
            isVisibleToUser = true
            setBoundsInScreen(android.graphics.Rect(0, 100, 1080, 2300))
        }
        val staticMatch = SelectorMatcher.detectShortFormFeedPattern(
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            sourceNode = staticView,
            root = rootNode,
            rule = ytRule
        )
        assertNull(staticMatch)
    }

    @Test
    fun `cold start MainViewModel initializes cleanly and resolves initial state`() {
        val app = ApplicationProvider.getApplicationContext<FeedSurgeonApp>()
        runBlocking(Dispatchers.IO) {
            app.interventionRepository.ensureDefaultRulesSeeded()
        }
        val store = ViewModelStore()
        val collectorScope = CoroutineScope(Dispatchers.Default)
        try {
            val viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(app)
            )[MainViewModel::class.java]

            var loadedState: MainUiState? = null
            val latch = CountDownLatch(1)
            val job = collectorScope.launch {
                loadedState = viewModel.uiState.first { !it.isInitialLoading }
                latch.countDown()
            }

            val deadline = System.currentTimeMillis() + 5_000L
            while (latch.count > 0 && System.currentTimeMillis() < deadline) {
                ShadowLooper.idleMainLooper()
                latch.await(20, TimeUnit.MILLISECONDS)
            }
            job.cancel()

            val state = loadedState
            assertNotNull("Expected MainViewModel to emit non-loading uiState", state)
            assertFalse(state!!.isInitialLoading)
            assertTrue(state.rules.size >= 5)
        } finally {
            store.clear()
        }
    }

    @Test
    fun `cold start MainActivity creates and resumes without crashing`() {
        val app = ApplicationProvider.getApplicationContext<FeedSurgeonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)

        val controller = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
        val activity = controller.get()
        assertFalse(activity.isFinishing)
        assertNotNull(activity.window)
        controller.pause().stop().destroy()
    }

    @Test
    fun `FeedSurgeonService lifecycle starts unbinds and destroys cleanly`() {
        val controller = Robolectric.buildService(FeedSurgeonService::class.java)
        val service = controller.create().startCommand(0, 0).get()
        assertNotNull(service)
        controller.unbind().destroy()
    }

    @Test
    fun `foreground package monitor detects short-form app entry transient windows and exits`() {
        val rulesByPkg = FeedSurgeonDatabase.defaultBuiltInRules().associateBy { it.packageName }
        val monitor = ForegroundPackageMonitor()
        val hostPkg = "com.suprbuild.outloop"

        val launcher = monitor.onWindowStateChanged(
            packageName = "com.android.launcher3",
            className = "Launcher",
            rulesByPackage = rulesByPkg,
            hostPackageName = hostPkg,
            isOverlayShowing = false
        )
        assertTrue(launcher is ForegroundPackageMonitor.Transition.UnmonitoredApp)

        val enteredYt = monitor.onWindowStateChanged(
            packageName = "com.google.android.youtube",
            className = "WatchWhileActivity",
            rulesByPackage = rulesByPkg,
            hostPackageName = hostPkg,
            isOverlayShowing = false
        )
        assertTrue(enteredYt is ForegroundPackageMonitor.Transition.EnteredMonitoredApp)
        assertEquals("YouTube", (enteredYt as ForegroundPackageMonitor.Transition.EnteredMonitoredApp).rule.displayName)
        assertEquals("com.google.android.youtube", monitor.currentForegroundPackage)

        // System UI notifications or overlay window must not clear active monitored app
        val systemUi = monitor.onWindowStateChanged(
            packageName = "com.android.systemui",
            className = "VolumeDialog",
            rulesByPackage = rulesByPkg,
            hostPackageName = hostPkg,
            isOverlayShowing = false
        )
        assertTrue(systemUi is ForegroundPackageMonitor.Transition.IgnoredTransientWindow)
        assertEquals("com.google.android.youtube", monitor.currentForegroundPackage)

        val ownOverlay = monitor.onWindowStateChanged(
            packageName = hostPkg,
            className = "FrameLayout",
            rulesByPackage = rulesByPkg,
            hostPackageName = hostPkg,
            isOverlayShowing = true
        )
        assertTrue(ownOverlay is ForegroundPackageMonitor.Transition.IgnoredTransientWindow)

        // Switching to an unmonitored app exits monitored state
        val exited = monitor.onWindowStateChanged(
            packageName = "com.android.chrome",
            className = "ChromeTabbedActivity",
            rulesByPackage = rulesByPkg,
            hostPackageName = hostPkg,
            isOverlayShowing = false
        )
        assertTrue(exited is ForegroundPackageMonitor.Transition.ExitedMonitoredApp)
        assertEquals("com.google.android.youtube", (exited as ForegroundPackageMonitor.Transition.ExitedMonitoredApp).previousPackage)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `FeedSurgeonService tracks foreground package changes via TYPE_WINDOW_STATE_CHANGED`() {
        val controller = Robolectric.buildService(FeedSurgeonService::class.java)
        val service = controller.create().get()
        DiagnosticsBridge.clearForegroundMonitoredApp()

        val enterEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.instagram.android"
            className = "com.instagram.mainactivity.InstagramMainActivity"
        }
        service.onAccessibilityEvent(enterEvent)
        assertEquals("Instagram", DiagnosticsBridge.foregroundMonitoredAppLabel.value)

        val exitEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
            packageName = "com.android.launcher3"
            className = "com.android.launcher3.Launcher"
        }
        service.onAccessibilityEvent(exitEvent)
        assertNull(DiagnosticsBridge.foregroundMonitoredAppLabel.value)

        controller.unbind().destroy()
    }

    @Test
    fun `controlled apps repository seeds observes and toggles controlled packages in Room`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        val repo: ControlledAppsRepository = InterventionRepository(db.feedSurgeonDao(), settings)

        repo.ensureDefaultRulesSeeded()
        val all = repo.allRules.first()
        assertTrue(all.size >= 7)
        assertTrue(repo.isControlledPackage("com.google.android.youtube"))
        assertTrue(repo.isControlledPackage("com.zhiliaoapp.musically"))
        assertFalse(repo.isControlledPackage("com.android.calculator2"))

        repo.setRuleEnabled("com.google.android.youtube", false)
        assertFalse(repo.getRule("com.google.android.youtube")!!.enabled)
        assertFalse(repo.activeRules.first().any { it.packageName == "com.google.android.youtube" })

        repo.setRuleEnabled("com.google.android.youtube", true)
        assertTrue(repo.activeRules.first().any { it.packageName == "com.google.android.youtube" })

        repo.upsertCustomRule(
            packageName = "com.custom.reelsapp",
            displayName = "ReelsApp",
            feedLabel = "Clips",
            viewIds = listOf("com.custom.reelsapp:id/clips_pager"),
            tabFallbackLabel = "Clips",
            homeTabLabel = "Home",
            interventionMode = InterventionMode.OVERLAY
        )
        assertTrue(repo.isControlledPackage("com.custom.reelsapp"))
        repo.deleteCustomRule("com.custom.reelsapp")
        assertFalse(repo.isControlledPackage("com.custom.reelsapp"))
    }

    @Suppress("DEPRECATION")
    @Test
    fun `FeedSurgeonService recognizes short-form packages and scroll monitoring events`() {
        val controller = Robolectric.buildService(FeedSurgeonService::class.java)
        val service = controller.create().get()

        assertTrue(service.isControlledPackage("com.instagram.android"))
        assertTrue(service.isControlledPackage("com.google.android.youtube"))
        assertTrue(service.isControlledPackage("com.zhiliaoapp.musically"))
        assertTrue(service.isControlledPackage("com.twitter.android"))
        assertFalse(service.isControlledPackage("com.android.settings"))

        assertTrue(service.isScrollMonitoringEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED))
        assertFalse(service.isScrollMonitoringEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))

        val scrollEvent = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED).apply {
            packageName = "com.zhiliaoapp.musically"
            className = "androidx.recyclerview.widget.RecyclerView"
        }
        service.onAccessibilityEvent(scrollEvent)

        controller.unbind().destroy()
    }

    @Test
    fun `migration 3 to 4 adds dailyLimitMinutes column and creates app_daily_usage table`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_3_4_test.db"
        context.deleteDatabase(dbName)
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS target_app_rules (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        feedLabel TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        interventionMode TEXT NOT NULL,
                        cooldownMs INTEGER NOT NULL,
                        homeTabLabel TEXT NOT NULL,
                        viewIdsRaw TEXT NOT NULL,
                        tabFallbackLabel TEXT NOT NULL,
                        snoozeUntilMs INTEGER NOT NULL,
                        allowOnceUntilMs INTEGER NOT NULL,
                        isBuiltIn INTEGER NOT NULL,
                        matchedSelectorCount INTEGER NOT NULL,
                        totalSelectorCount INTEGER NOT NULL,
                        isOutdated INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS intervention_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        feedLabel TEXT NOT NULL,
                        matchedSelector TEXT NOT NULL,
                        actionTaken TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        reclaimedSeconds INTEGER NOT NULL,
                        wasAllowedOnce INTEGER NOT NULL,
                        outcome TEXT NOT NULL DEFAULT 'CONFIRMED'
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        val db = helper.writableDatabase
        FeedSurgeonDatabase.MIGRATION_3_4.migrate(db)

        val ruleCursor = db.query("PRAGMA table_info(target_app_rules)")
        var foundDailyLimitColumn = false
        while (ruleCursor.moveToNext()) {
            if (ruleCursor.getString(1) == "dailyLimitMinutes") {
                foundDailyLimitColumn = true
            }
        }
        ruleCursor.close()
        assertTrue(foundDailyLimitColumn)

        val usageCursor = db.query("PRAGMA table_info(app_daily_usage)")
        val usageColumns = mutableSetOf<String>()
        while (usageCursor.moveToNext()) {
            usageColumns.add(usageCursor.getString(1))
        }
        usageCursor.close()
        assertTrue(usageColumns.containsAll(listOf("packageName", "dateKey", "trackedSeconds", "lastUpdatedMs")))
        helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `controlled apps repository tracks daily usage seconds and configures per-app daily limits`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        val repo: ControlledAppsRepository = InterventionRepository(db.feedSurgeonDao(), settings)

        repo.ensureDefaultRulesSeeded()
        repo.setDailyLimitMinutes("com.google.android.youtube", 25)
        assertEquals(25, repo.getRule("com.google.android.youtube")!!.dailyLimitMinutes)

        val dateKey = InterventionRepository.dateKeyForMs(System.currentTimeMillis())
        val initial = repo.getTodayUsageSeconds("com.google.android.youtube", dateKey)
        repo.addTrackedUsageSeconds("com.google.android.youtube", dateKey, 90L)
        repo.addTrackedUsageSeconds("com.google.android.youtube", dateKey, 45L)
        assertEquals(initial + 135L, repo.getTodayUsageSeconds("com.google.android.youtube", dateKey))

        val rows = repo.observeDailyUsageForDate(dateKey).first()
        assertTrue(rows.any { it.packageName == "com.google.android.youtube" && it.trackedSeconds >= 135L })

        repo.setDailyLimitMinutes("com.google.android.youtube", 0)
        assertEquals(0, repo.getRule("com.google.android.youtube")!!.dailyLimitMinutes)
    }

    @Test
    fun `app session time tracker accumulates foreground duration and handles app switches`() {
        val tracker = AppSessionTimeTracker()
        val nowWall = System.currentTimeMillis()

        assertTrue(tracker.onPackageEntered("com.instagram.android", nowElapsedMs = 1_000L, nowWallMs = nowWall).isEmpty())
        assertEquals("com.instagram.android", tracker.activePackage())
        assertEquals(15L, tracker.getPendingSeconds("com.instagram.android", nowElapsedMs = 16_500L))

        // Switching to YouTube flushes whole seconds (15s) for Instagram and retains anchor cleanly
        val switchSlices = tracker.onPackageEntered("com.google.android.youtube", nowElapsedMs = 16_500L, nowWallMs = nowWall + 15_500L)
        assertEquals(1, switchSlices.size)
        assertEquals("com.instagram.android", switchSlices.first().packageName)
        assertEquals(15L, switchSlices.first().deltaSeconds)
        assertEquals("com.google.android.youtube", tracker.activePackage())

        val exitSlices = tracker.onPackageExited("com.google.android.youtube", nowElapsedMs = 46_800L, nowWallMs = nowWall + 45_800L)
        assertEquals(1, exitSlices.size)
        assertEquals("com.google.android.youtube", exitSlices.first().packageName)
        assertEquals(30L, exitSlices.first().deltaSeconds)
        assertNull(tracker.activePackage())
    }

    @Test
    fun `transparent pause dim overlay renders translucent scrim and handles daily limit mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val overlay = TransparentPauseDimOverlay(context)
        var goHomeClicked = false
        var exitAppClicked = false
        var snoozeMinutesRequested: Int? = null

        val handles = overlay.buildOverlayViews(
            config = OverlayPresentationConfig(
                appName = "Instagram",
                feedLabel = "Reels",
                triggerMode = OverlayTriggerMode.DAILY_LIMIT_REACHED,
                dimAlpha = 0.80f,
                dailyUsedSeconds = 1860L,
                dailyLimitMinutes = 30,
                preferredPauseMinutes = 15
            ),
            onDismissRequest = {},
            onGoHome = { goHomeClicked = true },
            onExitApp = { exitAppClicked = true },
            onSnoozeMinutes = { snoozeMinutesRequested = it }
        )

        val scrimBg = handles.dimScrim.background as ColorDrawable
        assertTrue(Color.alpha(scrimBg.color) in 180..220)
        assertTrue(handles.root.contentDescription.toString().contains("Daily limit reached"))

        val actionDock = handles.modalCard.findViewWithTag<android.view.View>(
            TransparentPauseDimOverlay.TAG_ACTION_DOCK
        )
        val homeBtn = handles.modalCard.findViewWithTag<android.view.View>(
            TransparentPauseDimOverlay.TAG_HOME_ACTION_BTN
        )
        val exitBtn = handles.modalCard.findViewWithTag<android.view.View>(
            TransparentPauseDimOverlay.TAG_EXIT_ACTION_BTN
        )
        val pauseBtn = handles.modalCard.findViewWithTag<android.widget.Button>(
            TransparentPauseDimOverlay.TAG_PAUSE_GUARD_BTN
        )
        assertNotNull(actionDock)
        assertNotNull(homeBtn)
        assertNotNull(exitBtn)
        assertNotNull(pauseBtn)
        assertEquals("Extend limit \u00B7 15 min", pauseBtn.text.toString())

        homeBtn.performClick()
        assertTrue(goHomeClicked)
        assertFalse(exitAppClicked)

        exitBtn.performClick()
        assertTrue(exitAppClicked)
        assertNull(snoozeMinutesRequested)
    }

    @Test
    fun `transparent pause dim overlay supports feed guard pause flow and strict mode countdown`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val overlay = TransparentPauseDimOverlay(context)
        var snoozeMinutesRequested: Int? = null

        val instantHandles = overlay.buildOverlayViews(
            config = OverlayPresentationConfig(
                appName = "YouTube",
                feedLabel = "Shorts",
                triggerMode = OverlayTriggerMode.FEED_GUARD,
                strictModeEnabled = false,
                preferredPauseMinutes = 15
            ),
            onDismissRequest = {},
            onGoHome = {},
            onExitApp = {},
            onSnoozeMinutes = { snoozeMinutesRequested = it }
        )
        val instantPauseBtn = instantHandles.modalCard.findViewWithTag<android.widget.Button>(
            TransparentPauseDimOverlay.TAG_PAUSE_GUARD_BTN
        )
        assertEquals("Pause for 15 min", instantPauseBtn.text.toString())
        instantPauseBtn.performClick()
        assertEquals(15, snoozeMinutesRequested)

        val strictHandles = overlay.buildOverlayViews(
            config = OverlayPresentationConfig(
                appName = "YouTube",
                feedLabel = "Shorts",
                triggerMode = OverlayTriggerMode.FEED_GUARD,
                strictModeEnabled = true,
                preferredPauseMinutes = 15
            ),
            onDismissRequest = {},
            onGoHome = {},
            onExitApp = {},
            onSnoozeMinutes = {}
        )
        val strictPauseBtn = strictHandles.modalCard.findViewWithTag<android.widget.Button>(
            TransparentPauseDimOverlay.TAG_PAUSE_GUARD_BTN
        )
        strictPauseBtn.performClick()
        assertNull(
            strictHandles.modalCard.findViewWithTag<android.view.View>(
                TransparentPauseDimOverlay.TAG_ACTION_DOCK
            )
        )
    }

    @Test
    fun `FeedSurgeonService evaluates per-app daily limit against tracked usage`() {
        val controller = Robolectric.buildService(FeedSurgeonService::class.java)
        val service = controller.create().get()
        val limitedRule = FeedSurgeonDatabase.defaultBuiltInRules()
            .first { it.packageName == "com.instagram.android" }
            .copy(dailyLimitMinutes = 15)

        service.seedTrackedUsageForTesting("com.instagram.android", 600L)
        assertEquals(600L, service.getTodayTrackedSeconds("com.instagram.android"))
        assertFalse(service.isDailyLimitReached(limitedRule))

        service.seedTrackedUsageForTesting("com.instagram.android", 900L)
        assertTrue(service.isDailyLimitReached(limitedRule))

        assertTrue(service.exitCompleteAppToDeviceHome())
        val shadowService = org.robolectric.Shadows.shadowOf(service)
        assertTrue(
            shadowService.globalActionsPerformed.contains(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            )
        )

        controller.unbind().destroy()
    }

    @Test
    fun `SelectorMatcher findHomeTabNode selects target app Home tab over feed captions and ignores Outloop window`() {
        fun makeNode(
            pkg: String,
            cls: String = "android.view.View",
            viewId: String? = null,
            desc: String? = null,
            text: String? = null,
            clickable: Boolean = false,
            selected: Boolean = false
        ): AccessibilityNodeInfo = AccessibilityNodeInfo().apply {
            packageName = pkg
            className = cls
            viewIdResourceName = viewId
            contentDescription = desc
            this.text = text
            isClickable = clickable
            isSelected = selected
            isVisibleToUser = true
        }

        val ytRoot = makeNode("com.google.android.youtube", "android.widget.FrameLayout")
        val feedCaptionHome = makeNode(
            pkg = "com.google.android.youtube",
            cls = "android.widget.TextView",
            text = "Home",
            clickable = true
        )
        val pivotBar = makeNode(
            pkg = "com.google.android.youtube",
            cls = "android.widget.LinearLayout",
            viewId = "com.google.android.youtube:id/pivot_bar"
        )
        val ytHomeTab = makeNode(
            pkg = "com.google.android.youtube",
            cls = "android.widget.Button",
            viewId = "com.google.android.youtube:id/tab_home",
            desc = "Home, tab 1 of 4",
            clickable = true,
            selected = false
        )
        val ytShortsTab = makeNode(
            pkg = "com.google.android.youtube",
            cls = "android.widget.Button",
            desc = "Shorts, tab 2 of 4",
            clickable = true,
            selected = true
        )
        org.robolectric.Shadows.shadowOf(pivotBar).addChild(ytHomeTab)
        org.robolectric.Shadows.shadowOf(pivotBar).addChild(ytShortsTab)
        org.robolectric.Shadows.shadowOf(ytRoot).addChild(feedCaptionHome)
        // Add >650 feed nodes before pivotBar to ensure deep feed trees still reach bottom tabs
        val feedContainer = makeNode("com.google.android.youtube", "androidx.recyclerview.widget.RecyclerView")
        repeat(650) {
            org.robolectric.Shadows.shadowOf(feedContainer)
                .addChild(makeNode("com.google.android.youtube", "android.widget.FrameLayout"))
        }
        org.robolectric.Shadows.shadowOf(ytRoot).addChild(feedContainer)
        org.robolectric.Shadows.shadowOf(ytRoot).addChild(pivotBar)

        val outloopWindow = makeNode("com.suprbuild.outloop", "android.widget.FrameLayout")
        val outloopHomeTab = makeNode(
            pkg = "com.suprbuild.outloop",
            desc = "Home",
            clickable = true
        )
        org.robolectric.Shadows.shadowOf(outloopWindow).addChild(outloopHomeTab)

        val matchedYtHome = SelectorMatcher.findHomeTabNode(
            root = ytRoot,
            homeTabLabel = "Home",
            packageName = "com.google.android.youtube",
            additionalRoots = listOf(outloopWindow)
        )
        assertNotNull(matchedYtHome)
        assertEquals(ytHomeTab.viewIdResourceName, matchedYtHome?.viewIdResourceName)
        assertEquals(ytHomeTab.contentDescription, matchedYtHome?.contentDescription)

        val fbWindow = makeNode("com.facebook.katana", "android.widget.FrameLayout")
        val fbTabBar = makeNode("com.facebook.katana", "android.widget.TabWidget")
        val fbNewsFeedTab = makeNode(
            pkg = "com.facebook.katana",
            desc = "News Feed, tab 1 of 6",
            clickable = true
        )
        val fbReelsTab = makeNode(
            pkg = "com.facebook.katana",
            desc = "Reels, tab 2 of 6",
            clickable = true,
            selected = true
        )
        org.robolectric.Shadows.shadowOf(fbTabBar).addChild(fbNewsFeedTab)
        org.robolectric.Shadows.shadowOf(fbTabBar).addChild(fbReelsTab)
        org.robolectric.Shadows.shadowOf(fbWindow).addChild(fbTabBar)

        val matchedFbHome = SelectorMatcher.findHomeTabNode(
            root = fbWindow,
            homeTabLabel = "Home",
            packageName = "com.facebook.katana",
            additionalRoots = listOf(outloopWindow)
        )
        assertNotNull(matchedFbHome)
        assertEquals(fbNewsFeedTab.contentDescription, matchedFbHome?.contentDescription)
        assertEquals("com.facebook.katana", matchedFbHome?.packageName?.toString())

        // Ensure Outloop's own window is never matched when searching for Facebook's Home tab
        assertNull(
            SelectorMatcher.findHomeTabNode(
                root = outloopWindow,
                homeTabLabel = "Home",
                packageName = "com.facebook.katana"
            )
        )

        val fbReelsFullscreen = makeNode("com.facebook.katana", "android.widget.FrameLayout")
        val backIconBtn = makeNode(
            pkg = "com.facebook.katana",
            desc = "Navigate up",
            clickable = true
        )
        org.robolectric.Shadows.shadowOf(fbReelsFullscreen).addChild(backIconBtn)
        val matchedBack = SelectorMatcher.findInAppBackNode(fbReelsFullscreen, "com.facebook.katana")
        assertNotNull(matchedBack)
        assertEquals(backIconBtn.contentDescription, matchedBack?.contentDescription)
    }

    @Test
    fun `battery optimization compliance excludes restricted permission and uses safe settings intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(
            "Restricted permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS must not be declared",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in requested
        )

        val intent = ProtectionHealthHelper.createAppBatterySettingsIntent(context)
        assertFalse(intent.action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        assertTrue(
            intent.action in setOf(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                Settings.ACTION_SETTINGS
            )
        )
    }

    @Test
    fun `manifest declares AccessibilityService and specialUse foreground service metadata cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_META_DATA
        )
        val requested = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue("android.permission.FOREGROUND_SERVICE" in requested)
        assertTrue("android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in requested)
        assertTrue("android.permission.POST_NOTIFICATIONS" in requested)
        assertTrue("android.permission.INTERNET" in requested)

        val serviceInfo = packageInfo.services?.firstOrNull {
            it.name == FeedSurgeonService::class.java.name
        }
        assertNotNull("FeedSurgeonService must be declared in manifest", serviceInfo)
        assertEquals(android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE, serviceInfo!!.permission)
        assertEquals(
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            serviceInfo.foregroundServiceType
        )
        assertTrue(
            context.getString(R.string.accessibility_service_desc)
                .contains("strictly on-device", ignoreCase = true)
        )
    }

    @Test
    fun `settings data store defaults global feed budget to 60m clamps 0 to 480 and restores via backup`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val dao = db.feedSurgeonDao()
        val settings = SettingsDataStore(context)
        val repo = InterventionRepository(dao, settings)
        val backupManager = LocalBackupManager(dao, settings)
        repo.ensureDefaultRulesSeeded()

        assertEquals(60, AppPreferences().globalFeedTimeBudgetMinutes)

        settings.setGlobalFeedTimeBudgetMinutes(-15)
        assertEquals(0, settings.preferencesFlow.first().globalFeedTimeBudgetMinutes)

        settings.setGlobalFeedTimeBudgetMinutes(999)
        assertEquals(480, settings.preferencesFlow.first().globalFeedTimeBudgetMinutes)

        settings.setGlobalFeedTimeBudgetMinutes(90)
        val backupJson = backupManager.exportToJson()
        settings.setGlobalFeedTimeBudgetMinutes(15)
        val result = backupManager.restoreFromJson(backupJson)
        assertTrue(result is BackupRestoreResult.Success)
        assertEquals(90, settings.preferencesFlow.first().globalFeedTimeBudgetMinutes)

        // Restore clean default for subsequent tests
        settings.setGlobalFeedTimeBudgetMinutes(AppPreferences.DEFAULT_GLOBAL_FEED_BUDGET_MINUTES)
    }

    @Test
    fun `intervention repository sums total today tracked usage seconds across monitored apps`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        val repo: ControlledAppsRepository = InterventionRepository(db.feedSurgeonDao(), settings)
        val isolatedDateKey = "2099-01-01"

        val baseTotal = repo.getTotalTodayUsageSeconds(isolatedDateKey)
        repo.addTrackedUsageSeconds("com.google.android.youtube", isolatedDateKey, 400L)
        repo.addTrackedUsageSeconds("com.instagram.android", isolatedDateKey, 500L)

        assertEquals(baseTotal + 900L, repo.getTotalTodayUsageSeconds(isolatedDateKey))
    }

    @Test
    fun `FeedSurgeonService evaluates global feed time budget across monitored apps`() {
        val controller = Robolectric.buildService(FeedSurgeonService::class.java)
        val service = controller.create().get()

        service.seedTrackedUsageForTesting("com.instagram.android", 1_800L)
        service.seedTrackedUsageForTesting("com.google.android.youtube", 1_200L)
        assertEquals(3_000L, service.getTotalTodayTrackedSeconds())

        service.seedPreferencesForTesting(AppPreferences(globalFeedTimeBudgetMinutes = 60))
        assertFalse(service.isGlobalFeedBudgetReached())

        service.seedTrackedUsageForTesting("com.google.android.youtube", 1_800L)
        assertEquals(3_600L, service.getTotalTodayTrackedSeconds())
        assertTrue(service.isGlobalFeedBudgetReached())

        service.seedPreferencesForTesting(AppPreferences(globalFeedTimeBudgetMinutes = 0))
        assertFalse(service.isGlobalFeedBudgetReached())

        controller.unbind().destroy()
    }

    @Test
    fun `transparent pause dim overlay renders global budget reached mode and extends budget`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val overlay = TransparentPauseDimOverlay(context)
        var snoozeRequested: Int? = null

        val handles = overlay.buildOverlayViews(
            config = OverlayPresentationConfig(
                appName = "YouTube",
                feedLabel = "Shorts",
                triggerMode = OverlayTriggerMode.GLOBAL_BUDGET_REACHED,
                strictModeEnabled = false,
                globalBudgetMinutes = 60,
                totalTodayFeedSeconds = 3_900L,
                preferredPauseMinutes = 15
            ),
            onDismissRequest = {},
            onGoHome = {},
            onExitApp = {},
            onSnoozeMinutes = { snoozeRequested = it }
        )

        assertTrue(handles.root.contentDescription.toString().contains("Daily feed budget reached"))
        val pauseBtn = handles.modalCard.findViewWithTag<android.widget.Button>(
            TransparentPauseDimOverlay.TAG_PAUSE_GUARD_BTN
        )
        assertNotNull(pauseBtn)
        assertEquals("Extend budget \u00B7 15 min", pauseBtn.text.toString())

        pauseBtn.performClick()
        assertEquals(15, snoozeRequested)
    }
}
