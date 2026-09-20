package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class GoalType {
    SAVINGS,
    INVESTMENT
}

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GoalType,
    val targetAmount: Double,
    val currency: String = "PHP",
    val currencyBalances: String = "PHP=0",
    val targetDate: Long,
    val colorHex: String? = null,
    val cardStyle: String = "solid",
    val createdAt: Long = System.currentTimeMillis()
)