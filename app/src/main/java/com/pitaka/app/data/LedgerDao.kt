package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class CategorySpend(val name: String, val total: Double)
data class MonthlyAmount(val month: String, val total: Double)

@Dao
interface LedgerDao {
    @Insert suspend fun insertEntry(entry: LedgerEntry): Long
    @Update suspend fun updateEntry(entry: LedgerEntry)
    @Delete suspend fun deleteEntry(entry: LedgerEntry)
    @Query("DELETE FROM ledger_entries WHERE pitakaId = :pitakaId OR fromPitakaId = :pitakaId OR toPitakaId = :pitakaId")
    suspend fun deleteEntriesForPitaka(pitakaId: Long)
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC") suspend fun getAllEntriesOnce(): List<LedgerEntry>
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC") fun observeAllEntries(): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE pitakaId = :pitakaId OR fromPitakaId = :pitakaId OR toPitakaId = :pitakaId ORDER BY date DESC")
    fun observeEntriesForPitaka(pitakaId: Long): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE goalId = :goalId ORDER BY date DESC")
    fun observeEntriesForGoal(goalId: Long): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE type = 'EXPENSE' ORDER BY date DESC")
    fun observeAllExpenses(): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE type = 'EXPENSE' AND LOWER(TRIM(category)) = LOWER(TRIM(:category)) ORDER BY date DESC")
    fun observeExpensesForCategory(category: String): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE type = 'EXPENSE' AND funnelId = :funnelId ORDER BY date DESC")
    fun observeExpensesForFunnel(funnelId: Long): Flow<List<LedgerEntry>>
    @Query("""SELECT strftime('%Y-%m', date / 1000, 'unixepoch') AS month, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type='EXPENSE' GROUP BY month ORDER BY month ASC""")
    fun observeMonthlyExpenses(): Flow<List<MonthlyAmount>>
    @Query("""SELECT strftime('%Y-%m', date / 1000, 'unixepoch') AS month, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type='INCOME' GROUP BY month ORDER BY month ASC""")
    fun observeMonthlyIncome(): Flow<List<MonthlyAmount>>
    @Query("""SELECT CASE WHEN TRIM(category)='' OR category IS NULL THEN 'Uncategorized Expense' ELSE MIN(TRIM(category)) END AS name, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type='EXPENSE' GROUP BY CASE WHEN TRIM(category)='' OR category IS NULL THEN 'uncategorized expense' ELSE LOWER(TRIM(category)) END ORDER BY total DESC""")
    fun observeExpenseBreakdown(): Flow<List<CategorySpend>>
    @Query("""SELECT CASE WHEN TRIM(category)='' OR category IS NULL THEN 'Uncategorized Expense' ELSE MIN(TRIM(category)) END AS name, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type='EXPENSE' AND strftime('%Y-%m',date/1000,'unixepoch')=:month GROUP BY CASE WHEN TRIM(category)='' OR category IS NULL THEN 'uncategorized expense' ELSE LOWER(TRIM(category)) END ORDER BY total DESC""")
    fun observeExpenseBreakdownForMonth(month:String): Flow<List<CategorySpend>>
    @Query("""SELECT MIN(TRIM(category)) AS category FROM ledger_entries WHERE type='EXPENSE' AND category IS NOT NULL AND TRIM(category)!='' GROUP BY LOWER(TRIM(category)) ORDER BY LOWER(TRIM(category)) ASC""")
    fun observeExpenseCategories(): Flow<List<String>>
    @Query("SELECT DISTINCT strftime('%Y-%m',date/1000,'unixepoch') AS month FROM ledger_entries ORDER BY month ASC")
    fun observeAvailableMonths(): Flow<List<String>>
    @Query("SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE type='EXPENSE' AND strftime('%Y-%m',date/1000,'unixepoch')=:month")
    fun observeExpenseTotalForMonth(month:String): Flow<Double>
    @Query("SELECT * FROM ledger_entries WHERE type='EXPENSE' AND strftime('%Y-%m',date/1000,'unixepoch')=:month ORDER BY amount DESC")
    fun observeExpensesForMonth(month:String): Flow<List<LedgerEntry>>
}