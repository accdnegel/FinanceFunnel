package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Update
    suspend fun update(rule: RecurringRule)

    @Delete
    suspend fun delete(rule: RecurringRule)

    @Query("SELECT * FROM recurring_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecurringRule>>

    @Query("SELECT * FROM recurring_rules WHERE active = 1")
    suspend fun getActiveRulesOnce(): List<RecurringRule>

    /** Atomically claims a month so concurrent app-start catch-up calls cannot post twice. */
    @Query("UPDATE recurring_rules SET lastAppliedMonth = :month WHERE id = :ruleId AND active = 1 AND (lastAppliedMonth IS NULL OR lastAppliedMonth != :month)")
    suspend fun claimMonth(ruleId: Long, month: String): Int
}
