package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.InterventionOutcome
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedSurgeonDao {
    @Query("SELECT * FROM target_app_rules ORDER BY isBuiltIn DESC, displayName ASC")
    fun observeAllRules(): Flow<List<TargetAppRuleEntity>>

    @Query("SELECT * FROM target_app_rules WHERE enabled = 1 ORDER BY isBuiltIn DESC, displayName ASC")
    fun observeEnabledRules(): Flow<List<TargetAppRuleEntity>>

    @Query("SELECT * FROM target_app_rules ORDER BY isBuiltIn DESC, displayName ASC")
    suspend fun getAllRulesSnapshot(): List<TargetAppRuleEntity>

    @Query("SELECT * FROM target_app_rules WHERE packageName = :packageName LIMIT 1")
    fun observeRuleByPackage(packageName: String): Flow<TargetAppRuleEntity?>

    @Query("SELECT * FROM target_app_rules WHERE packageName = :packageName LIMIT 1")
    suspend fun getRuleByPackage(packageName: String): TargetAppRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: TargetAppRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<TargetAppRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRulesIfMissing(rules: List<TargetAppRuleEntity>)

    @Update
    suspend fun updateRule(rule: TargetAppRuleEntity)

    @Query("DELETE FROM target_app_rules WHERE packageName = :packageName AND isBuiltIn = 0")
    suspend fun deleteCustomRule(packageName: String)

    @Query("DELETE FROM target_app_rules")
    suspend fun clearAllRules()

    @Query("SELECT * FROM intervention_events WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    fun observeEventsSince(sinceMs: Long): Flow<List<InterventionEventEntity>>

    @Query("SELECT * FROM intervention_events ORDER BY timestampMs DESC LIMIT 200")
    fun observeRecentEvents(): Flow<List<InterventionEventEntity>>

    @Query("SELECT * FROM intervention_events ORDER BY timestampMs DESC")
    suspend fun getAllEventsSnapshot(): List<InterventionEventEntity>

    @Insert
    suspend fun insertEvent(event: InterventionEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<InterventionEventEntity>)

    @Query("UPDATE intervention_events SET wasAllowedOnce = 1, outcome = 'ALLOWED_ONCE' WHERE id = :eventId")
    suspend fun markEventAllowedOnce(eventId: Long)

    @Query("UPDATE intervention_events SET outcome = :outcome WHERE id = :eventId")
    suspend fun updateEventOutcome(eventId: Long, outcome: InterventionOutcome)

    @Query("DELETE FROM intervention_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Long)

    @Query("DELETE FROM intervention_events")
    suspend fun clearAllEvents()

    @Query("SELECT * FROM app_daily_usage WHERE dateKey = :dateKey ORDER BY trackedSeconds DESC")
    fun observeDailyUsageForDate(dateKey: String): Flow<List<AppDailyUsageEntity>>

    @Query("SELECT * FROM app_daily_usage WHERE packageName = :packageName AND dateKey = :dateKey LIMIT 1")
    fun observeAppDailyUsage(packageName: String, dateKey: String): Flow<AppDailyUsageEntity?>

    @Query("SELECT * FROM app_daily_usage WHERE packageName = :packageName AND dateKey = :dateKey LIMIT 1")
    suspend fun getAppDailyUsage(packageName: String, dateKey: String): AppDailyUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyUsage(usage: AppDailyUsageEntity)

    @Transaction
    suspend fun incrementDailyUsageSeconds(
        packageName: String,
        dateKey: String,
        deltaSeconds: Long,
        updatedAtMs: Long
    ) {
        if (deltaSeconds <= 0L) return
        val existing = getAppDailyUsage(packageName, dateKey)
        val updatedTotal = (existing?.trackedSeconds ?: 0L) + deltaSeconds
        upsertDailyUsage(
            AppDailyUsageEntity(
                packageName = packageName,
                dateKey = dateKey,
                trackedSeconds = updatedTotal,
                lastUpdatedMs = updatedAtMs
            )
        )
    }

    @Query("DELETE FROM app_daily_usage")
    suspend fun clearAllDailyUsage()

    @Transaction
    suspend fun replaceAllDataForBackup(
        rules: List<TargetAppRuleEntity>,
        events: List<InterventionEventEntity>
    ) {
        clearAllEvents()
        clearAllRules()
        if (rules.isNotEmpty()) {
            upsertRules(rules)
        }
        if (events.isNotEmpty()) {
            insertEvents(events)
        }
    }
}
