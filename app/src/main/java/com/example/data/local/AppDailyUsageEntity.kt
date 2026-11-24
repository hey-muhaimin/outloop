package com.example.data.local

import androidx.room.Entity

/**
 * Stores cumulative foreground usage duration in seconds for a controlled package
 * scoped to a local calendar date (`yyyy-MM-dd`).
 */
@Entity(
    tableName = "app_daily_usage",
    primaryKeys = ["packageName", "dateKey"]
)
data class AppDailyUsageEntity(
    val packageName: String,
    val dateKey: String,
    val trackedSeconds: Long = 0L,
    val lastUpdatedMs: Long = 0L
)
