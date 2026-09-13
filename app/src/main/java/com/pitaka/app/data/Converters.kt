package com.pitaka.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromLedgerType(type: LedgerType): String = type.name

    @TypeConverter
    fun toLedgerType(value: String): LedgerType = LedgerType.valueOf(value)

    @TypeConverter
    fun fromGoalType(type: GoalType): String = type.name

    @TypeConverter
    fun toGoalType(value: String): GoalType = GoalType.valueOf(value)
}
