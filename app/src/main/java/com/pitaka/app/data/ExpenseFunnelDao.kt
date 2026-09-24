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

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE funnelId = :id AND type = 'EXPENSE'")
    suspend fun countExpenses(id: Long): Int

    @Query("SELECT * FROM expense_funnels WHERE id = :id")
    suspend fun get(id: Long): ExpenseFunnel?

    @Query("SELECT * FROM expense_funnels WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ExpenseFunnel?

    @Insert
    suspend fun insertAndReturn(funnel: ExpenseFunnel): Long

    @Query("""
        SELECT COALESCE(SUM(COALESCE(l.funnelAmount, l.amount)), 0)
        FROM ledger_entries l
        WHERE l.type = 'EXPENSE'
          AND l.funnelId = :funnelId
          AND COALESCE(l.funnelCurrency, l.currency) = (
              SELECT currency FROM expense_funnels WHERE id = :funnelId
          )
    """)
    fun observeSpent(funnelId: Long): Flow<Double>
}
