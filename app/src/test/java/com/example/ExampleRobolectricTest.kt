package com.example

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.service.FeedSurgeonService
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
import com.example.data.repository.InterventionRepository
import com.example.data.repository.LocalBackupManager
import com.example.data.repository.RulesetRepository
import com.example.data.repository.RulesetSyncResult
import com.example.service.AccessibilityEventLogger
import com.example.service.ForegroundProtectionNotifier
import com.example.service.InstalledAppDetector
import com.example.service.ProtectionActionReceiver
import com.example.service.SelectorMatcher
import com.example.ui.MainViewModel
import com.example.worker.RulesetSyncWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and privacy url from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Outloop", context.getString(R.string.app_name))
        assertEquals("https://suprbuild.com/privacy", context.getString(R.string.privacy_policy_url))
    }

    @Test
    fun `default built-in rules use overlay shield mode`() {
        val defaults = FeedSurgeonDatabase.defaultBuiltInRules()
        assertEquals(5, defaults.size)
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
    fun `ruleset repository parses all five bundled apps and seeds local cache`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = FeedSurgeonDatabase.getInstance(context)
        val settings = SettingsDataStore(context)
        val repo = RulesetRepository(context, db.feedSurgeonDao(), settings)

        val parsed = repo.parseAndValidate(AppPreferences.BUNDLED_RULESET_JSON)
        assertNotNull(parsed)
        assertEquals("v2026.11.18", parsed!!.version)
        val packages = parsed.apps.map { it.packageName }.toSet()
        assertTrue(packages.containsAll(FeedSurgeonDatabase.BUILT_IN_PACKAGE_NAMES))
        assertEquals(5, packages.size)

        val cacheFile = File(context.filesDir, "ruleset_cache.json")
        cacheFile.delete()
        repo.ensureLocalCacheSeeded()
        assertTrue(cacheFile.exists())
        val cachedParsed = repo.parseAndValidate(cacheFile.readText())
        assertNotNull(cachedParsed)
        assertEquals(5, cachedParsed!!.apps.size)
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
        assertEquals(5, success.updatedApps)
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
    fun `accessibility event logger writes reads and clears local file`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logger = AccessibilityEventLogger(context)
        logger.clear()
        assertEquals(0L, logger.getFileSizeBytes())

        logger.logEvent(
            packageName = "com.google.android.youtube",
            eventType = 32,
            eventClassName = "com.google.android.apps.youtube.app.WatchWhileActivity",
            rootNode = null,
            matchedSelector = "com.google.android.youtube:id/reel_recycler",
            outcome = "TRIGGERED:OVERLAY"
        )

        val tail = logger.readTail(20)
        assertTrue(tail.contains("pkg=com.google.android.youtube"))
        assertTrue(tail.contains("match=com.google.android.youtube:id/reel_recycler"))
        assertTrue(tail.contains("outcome=TRIGGERED:OVERLAY"))
        assertTrue(logger.getFileSizeBytes() > 0L)

        logger.clear()
        assertEquals("", logger.readTail(20))
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
        assertEquals(5, success.updatedApps)
        assertEquals("", settings.preferencesFlow.first().rulesetEtag)
        assertEquals(5, db.feedSurgeonDao().getAllRulesSnapshot().size)
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

        val json = backupManager.exportToJson()
        val preview = backupManager.inspectBackupJson(json)
        assertNotNull(preview)
        assertEquals(6, preview!!.ruleCount)
        assertEquals(1, preview.customRuleCount)
        assertEquals(1, preview.eventCount)

        // Mutate state cleanly, then restore
        dao.deleteCustomRule("com.custom.shortapp")
        dao.clearAllEvents()
        settings.setAppearanceMode(AppearanceMode.LIGHT)
        settings.setQuietHours(enabled = false, startMinutes = 0, endMinutes = 0)
        settings.setDailyBudgetCuts(0)

        val invalidResult = backupManager.restoreFromJson("{invalid json")
        assertTrue(invalidResult is BackupRestoreResult.Error)

        val restored = backupManager.restoreFromJson(json)
        assertTrue(restored is BackupRestoreResult.Success)
        val success = restored as BackupRestoreResult.Success
        assertEquals(6, success.restoredRules)
        assertEquals(1, success.restoredEvents)

        val restoredCustom = dao.getRuleByPackage("com.custom.shortapp")
        assertNotNull(restoredCustom)
        assertEquals("ShortApp", restoredCustom!!.displayName)
        assertEquals(1, dao.getAllEventsSnapshot().size)

        val restoredPrefs = settings.preferencesFlow.first()
        assertEquals(AppearanceMode.DARK, restoredPrefs.appearanceMode)
        assertTrue(restoredPrefs.quietHoursEnabled)
        assertEquals(15, restoredPrefs.dailyBudgetCuts)

        // Clean up test mutations so shared Robolectric DB/DataStore remains pristine for other tests
        dao.deleteCustomRule("com.custom.shortapp")
        dao.clearAllEvents()
        settings.setQuietHours(enabled = false, startMinutes = 22 * 60, endMinutes = 7 * 60)
        settings.setDailyBudgetCuts(0)
        settings.setAppearanceMode(AppearanceMode.SYSTEM)
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

    @Test
    fun `cold start MainViewModel initializes cleanly and resolves initial state`() = runTest {
        val app = ApplicationProvider.getApplicationContext<FeedSurgeonApp>()
        app.interventionRepository.ensureDefaultRulesSeeded()
        val viewModel = MainViewModel(app)

        val loadedState = viewModel.uiState.first { !it.isInitialLoading }
        assertFalse(loadedState.isInitialLoading)
        assertTrue(loadedState.rules.size >= 5)
    }

    @Test
    fun `cold start MainActivity creates and resumes without crashing`() {
        val app = ApplicationProvider.getApplicationContext<FeedSurgeonApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ShadowLooper.idleMainLooper()
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNotNull(activity.window)
            }
        }
    }

    @Test
    fun `FeedSurgeonService lifecycle starts unbinds and destroys cleanly`() {
        val controller = Robolectric.buildService(FeedSurgeonService::class.java)
        val service = controller.create().startCommand(0, 0).get()
        assertNotNull(service)
        controller.unbind().destroy()
    }
}
