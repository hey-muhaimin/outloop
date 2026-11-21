package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.InterventionMode

@Entity(tableName = "target_app_rules")
data class TargetAppRuleEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val feedLabel: String,
    val enabled: Boolean = true,
    val interventionMode: InterventionMode = InterventionMode.OVERLAY,
    val cooldownMs: Int = 800,
    val homeTabLabel: String = "Home",
    val viewIdsRaw: String,
    val tabFallbackLabel: String = "",
    val snoozeUntilMs: Long = 0L,
    val allowOnceUntilMs: Long = 0L,
    val isBuiltIn: Boolean = false,
    val matchedSelectorCount: Int = 0,
    val totalSelectorCount: Int = 0,
    val isOutdated: Boolean = false
) {
    val viewIds: List<String>
        get() = viewIdsRaw.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    val shortViewIdsForDisplay: List<String>
        get() = viewIds.map { fullId ->
            val idx = fullId.indexOf(":id/")
            if (idx >= 0) "id/" + fullId.substring(idx + 4) else fullId
        }

    fun isCurrentlySnoozed(nowMs: Long = System.currentTimeMillis()): Boolean =
        snoozeUntilMs > nowMs || allowOnceUntilMs > nowMs
}
