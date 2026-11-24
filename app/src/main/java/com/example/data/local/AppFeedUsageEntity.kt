package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Measured foreground time spent in a controlled package scoped to a local calendar date.
 * Populated by [com.example.service.FeedSurgeonService] from real-time window transitions.
 */
@Entity(
    tableName = "app_feed_usage",
    primaryKeys = ["packageName", "dateKey"]
)
data class AppFeedUsageEntity(
    val packageName: String,
    val dateKey: String,
    val trackedSeconds: Long = 0L,
    val lastUpdatedMs: Long = 0L
)
