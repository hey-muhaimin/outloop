package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionOutcome

@Entity(tableName = "intervention_events")
data class InterventionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appName: String,
    val feedLabel: String,
    val matchedSelector: String,
    val actionTaken: InterventionMode,
    val timestampMs: Long = System.currentTimeMillis(),
    val reclaimedSeconds: Int = 165,
    val wasAllowedOnce: Boolean = false,
    val outcome: InterventionOutcome = InterventionOutcome.CONFIRMED
)
