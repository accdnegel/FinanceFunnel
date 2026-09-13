package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class GoalType {
    SAVINGS,
    INVESTMENT
}

/**
 * A Goal is a target-tracking "funnel" or "piggy bank" — either a Savings pool or an
 * Investment allocation. Its progress is derived from the sum of GOAL_CONTRIBUTION
 * ledger entries linked to it (see LedgerDao), not stored directly.
 *
 * Investment-type goals represent non-liquid assets: contributing to one pulls money
 * out of a Pitaka (like an expense would) but is NOT counted toward the monthly
 * expense limit, since it's reallocation rather than consumption.
 */
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: GoalType,
    val targetAmount: Double,
    val targetDate: Long,
    val colorHex: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
