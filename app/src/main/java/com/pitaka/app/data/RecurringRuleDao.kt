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
}
