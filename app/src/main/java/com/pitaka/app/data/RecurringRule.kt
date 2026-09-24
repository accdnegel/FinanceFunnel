package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A monthly recurring Income or Expense (e.g. salary, rent). There's no background
 * scheduler (keeps the app dependency-light and fully offline) — instead, every time the
 * app opens, PitakaRepository.applyDueRecurringRules() checks each active rule and posts
 * it if today is on/after `dayOfMonth` and it hasn't already been applied this month.
 */
@Entity(tableName = "recurring_rules")
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: LedgerType, // INCOME or EXPENSE only
    val name: String,
    val amount: Double,
    val currency: String = "PHP",
    val category: String? = null, // EXPENSE only
    val pitakaId: Long,
    val dayOfMonth: Int, // 1-31; clamped to the last day of shorter months
    val active: Boolean = true,
    val lastAppliedMonth: String? = null, // "yyyy-MM" of the last month this was posted
    val createdAt: Long = System.currentTimeMillis()
)
