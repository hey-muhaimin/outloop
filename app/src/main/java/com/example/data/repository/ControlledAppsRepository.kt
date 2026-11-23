package com.example.data.repository

import com.example.data.local.AppDailyUsageEntity
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import kotlinx.coroutines.flow.Flow

/**
 * Base repository contract for storing, observing, and updating the list of
 * short-form video apps controlled by [com.example.service.FeedSurgeonService],
 * along with per-app daily usage tracking and configurable daily time limits.
 */
interface ControlledAppsRepository {
    val allRules: Flow<List<TargetAppRuleEntity>>
    val activeRules: Flow<List<TargetAppRuleEntity>>

    fun observeRule(packageName: String): Flow<TargetAppRuleEntity?>

    fun observeDailyUsageForDate(dateKey: String): Flow<List<AppDailyUsageEntity>>

    fun observeAppDailyUsage(packageName: String, dateKey: String): Flow<AppDailyUsageEntity?>

    suspend fun ensureDefaultRulesSeeded()

    suspend fun getRule(packageName: String): TargetAppRuleEntity?

    suspend fun isControlledPackage(packageName: String): Boolean

    suspend fun setRuleEnabled(packageName: String, enabled: Boolean)

    suspend fun setDailyLimitMinutes(packageName: String, limitMinutes: Int)

    suspend fun getTodayUsageSeconds(packageName: String, dateKey: String): Long

    suspend fun addTrackedUsageSeconds(
        packageName: String,
        dateKey: String,
        deltaSeconds: Long,
        updatedAtMs: Long = System.currentTimeMillis()
    )

    suspend fun upsertCustomRule(
        packageName: String,
        displayName: String,
        feedLabel: String,
        viewIds: List<String>,
        tabFallbackLabel: String,
        homeTabLabel: String,
        interventionMode: InterventionMode
    )

    suspend fun deleteCustomRule(packageName: String)
}
