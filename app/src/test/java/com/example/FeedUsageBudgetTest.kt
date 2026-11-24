package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TargetAppRuleEntity
import com.example.data.repository.InterventionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedUsageBudgetTest {

    private lateinit var db: FeedSurgeonDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var repo: InterventionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FeedSurgeonDatabase::class.java).build()
        settings = SettingsDataStore(context)
        repo = InterventionRepository(db.feedSurgeonDao(), settings)
    }

    @Test
    fun `addFeedUsageSeconds aggregates per dateKey`() = runBlocking {
        val pkg = "com.test.app"
        val key = InterventionRepository.dateKeyForMs()
        repo.addFeedUsageSeconds(pkg, key, 60, System.currentTimeMillis())
        repo.addFeedUsageSeconds(pkg, key, 30, System.currentTimeMillis())
        val rows = repo.getFeedUsageForDate(key)
        assertEquals(1, rows.size)
        assertEquals(90L, rows[0].trackedSeconds)

        val otherKey = "1999-01-01"
        repo.addFeedUsageSeconds(pkg, otherKey, 10, System.currentTimeMillis())
        assertEquals(10L, repo.getFeedUsageForDate(otherKey)[0].trackedSeconds)
        assertEquals(90L, repo.getFeedUsageForDate(key)[0].trackedSeconds)
    }

    @Test
    fun `TargetAppRuleEntity daily limit helpers`() {
        val rule = TargetAppRuleEntity(
            packageName = "com.test.app",
            displayName = "App",
            feedLabel = "Feed",
            viewIdsRaw = "com.test.app:id/feed"
        )
        assertFalse(rule.hasDailyLimit())
        assertEquals(0L, rule.dailyLimitSeconds)
        val limited = rule.copy(dailyLimitMinutes = 30)
        assertTrue(limited.hasDailyLimit())
        assertEquals(1800L, limited.dailyLimitSeconds)
    }

    @Test
    fun `globalFeedTimeBudgetMinutes preference roundtrip and clamp`() = runBlocking {
        assertEquals(0, settings.preferencesFlow.first().globalFeedTimeBudgetMinutes)
        settings.setGlobalFeedTimeBudgetMinutes(45)
        assertEquals(45, settings.preferencesFlow.first().globalFeedTimeBudgetMinutes)
        settings.setGlobalFeedTimeBudgetMinutes(999)
        assertEquals(480, settings.preferencesFlow.first().globalFeedTimeBudgetMinutes)
    }

    @Test
    fun `setDailyLimitMinutes updates the stored rule`() = runBlocking {
        val rule = TargetAppRuleEntity(
            packageName = "com.test.app",
            displayName = "App",
            feedLabel = "Feed",
            viewIdsRaw = "com.test.app:id/feed"
        )
        db.feedSurgeonDao().upsertRule(rule)
        repo.setDailyLimitMinutes("com.test.app", 30)
        val updated = db.feedSurgeonDao().getRuleByPackage("com.test.app")
        assertEquals(30, updated?.dailyLimitMinutes)
    }
}
