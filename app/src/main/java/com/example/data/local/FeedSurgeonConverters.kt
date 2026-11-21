package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.InterventionMode
import com.example.data.model.InterventionOutcome

class FeedSurgeonConverters {
    @TypeConverter
    fun fromInterventionMode(mode: InterventionMode): String = mode.name

    @TypeConverter
    fun toInterventionMode(value: String): InterventionMode =
        InterventionMode.entries.firstOrNull { it.name == value } ?: InterventionMode.OVERLAY

    @TypeConverter
    fun fromInterventionOutcome(outcome: InterventionOutcome): String = outcome.name

    @TypeConverter
    fun toInterventionOutcome(value: String): InterventionOutcome =
        InterventionOutcome.entries.firstOrNull { it.name == value } ?: InterventionOutcome.CONFIRMED
}
