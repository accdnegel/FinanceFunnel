package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseFunnelDao {
    @Insert suspend fun insert(funnel: ExpenseFunnel): Long
    @Update suspend fun update(funnel: ExpenseFunnel)
    @Delete suspend fun delete(funnel: ExpenseFunnel)
    @Query("SELECT * FROM expense_funnels ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExpenseFunnel>>
    @Query("SELECT * FROM expense_funnels WHERE id = :id")
    suspend fun get(id: Long): ExpenseFunnel?
    @Query("SELECT COALESCE(SUM(amount), 0) FROM ledger_entries WHERE type = 'EXPENSE' AND funnelId = :funnelId")
    fun observeSpent(funnelId: Long): Flow<Double>
}
