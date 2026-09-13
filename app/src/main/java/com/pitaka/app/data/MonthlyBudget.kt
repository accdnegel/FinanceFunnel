package com.pitaka.app.data

import androidx.room.Entity

/**
 * A Monthly Max Expense limit for a specific month ("yyyy-MM"). If a month has no row of
 * its own, the most recent earlier month's limit carries forward automatically — set it
 * once and it applies going forward until you explicitly change it for a later month.
 */
@Entity(tableName = "monthly_budgets", primaryKeys = ["month"])
data class MonthlyBudget(
    val month: String,
    val limit: Double
)
