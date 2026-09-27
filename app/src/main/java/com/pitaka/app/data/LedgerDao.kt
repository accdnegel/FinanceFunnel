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
    @Query("SELECT COUNT(*) FROM ledger_entries WHERE pitakaId = :pitakaId OR fromPitakaId = :pitakaId OR toPitakaId = :pitakaId")
    suspend fun countEntriesForPitaka(pitakaId: Long): Int

    @Query("DELETE FROM ledger_entries WHERE pitakaId = :pitakaId OR fromPitakaId = :pitakaId OR toPitakaId = :pitakaId")
    suspend fun deleteEntriesForPitaka(pitakaId: Long)
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC") suspend fun getAllEntriesOnce(): List<LedgerEntry>
    @Query("SELECT * FROM ledger_entries WHERE pitakaId = :pitakaId OR fromPitakaId = :pitakaId OR toPitakaId = :pitakaId ORDER BY date DESC")
    suspend fun getEntriesForPitakaOnce(pitakaId: Long): List<LedgerEntry>
    @Query("UPDATE ledger_entries SET pitakaId = CASE WHEN pitakaId = :oldId THEN :newId ELSE pitakaId END, fromPitakaId = CASE WHEN fromPitakaId = :oldId THEN :newId ELSE fromPitakaId END, toPitakaId = CASE WHEN toPitakaId = :oldId THEN :newId ELSE toPitakaId END WHERE pitakaId = :oldId OR fromPitakaId = :oldId OR toPitakaId = :oldId")
    suspend fun reassignPitakaReferences(oldId: Long, newId: Long)
    @Query("UPDATE ledger_entries SET funnelId = :newFunnelId WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND funnelId = :oldFunnelId")
    suspend fun reassignExpenseFunnel(oldFunnelId: Long, newFunnelId: Long)
    @Query("SELECT * FROM ledger_entries ORDER BY date DESC") fun observeAllEntries(): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE pitakaId = :pitakaId OR fromPitakaId = :pitakaId OR toPitakaId = :pitakaId ORDER BY date DESC")
    fun observeEntriesForPitaka(pitakaId: Long): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE goalId = :goalId ORDER BY date DESC")
    fun observeEntriesForGoal(goalId: Long): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE goalId = :goalId ORDER BY date DESC")
    suspend fun getEntriesForGoalOnce(goalId: Long): List<LedgerEntry>
    @Query("SELECT * FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') ORDER BY date DESC")
    fun observeAllExpenses(): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND LOWER(TRIM(category)) = LOWER(TRIM(:category)) ORDER BY date DESC")
    fun observeExpensesForCategory(category: String): Flow<List<LedgerEntry>>

    @Query("SELECT MIN(TRIM(category)) AS category FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND category IS NOT NULL AND TRIM(category)!='' GROUP BY LOWER(TRIM(category)) ORDER BY LOWER(TRIM(category)) ASC")
    fun observeExpenseCategories(): Flow<List<String>>
    @Query("SELECT * FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND funnelId = :funnelId ORDER BY date DESC")
    fun observeExpensesForFunnel(funnelId: Long): Flow<List<LedgerEntry>>
    @Query("SELECT * FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND funnelId = :funnelId ORDER BY date DESC")
    suspend fun getExpensesForFunnelOnce(funnelId: Long): List<LedgerEntry>
    @Query("""SELECT strftime('%Y-%m', date / 1000, 'unixepoch') AS month, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') GROUP BY month ORDER BY month ASC""")
    fun observeMonthlyExpenses(): Flow<List<MonthlyAmount>>
    @Query("""SELECT strftime('%Y-%m', date / 1000, 'unixepoch') AS month, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type='INCOME' GROUP BY month ORDER BY month ASC""")
    fun observeMonthlyIncome(): Flow<List<MonthlyAmount>>
    @Query("""SELECT CASE WHEN TRIM(category)='' OR category IS NULL THEN 'Uncategorized' ELSE MIN(TRIM(category)) END AS name, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') GROUP BY CASE WHEN TRIM(category)='' OR category IS NULL THEN 'uncategorized' ELSE LOWER(TRIM(category)) END ORDER BY total DESC""")
    fun observeExpenseBreakdown(): Flow<List<CategorySpend>>
    @Query("""SELECT CASE WHEN TRIM(category)='' OR category IS NULL THEN 'Uncategorized' ELSE MIN(TRIM(category)) END AS name, COALESCE(SUM(amount),0) AS total FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND strftime('%Y-%m',date/1000,'unixepoch')=:month GROUP BY CASE WHEN TRIM(category)='' OR category IS NULL THEN 'uncategorized' ELSE LOWER(TRIM(category)) END ORDER BY total DESC""")
    fun observeExpenseBreakdownForMonth(month:String): Flow<List<CategorySpend>>
    @Query("SELECT MIN(TRIM(category)) FROM ledger_entries WHERE type='EXPENSE' AND category IS NOT NULL AND TRIM(category)!='' AND LOWER(TRIM(category)) = LOWER(TRIM(:category))")
    suspend fun findCanonicalExpenseCategory(category: String): String?
    @Query("SELECT DISTINCT strftime('%Y-%m',date/1000,'unixepoch') AS month FROM ledger_entries WHERE strftime('%Y-%m',date/1000,'unixepoch') IS NOT NULL ORDER BY month ASC")
    fun observeAvailableMonths(): Flow<List<String>>
    @Query("SELECT COALESCE(SUM(amount),0) FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND strftime('%Y-%m',date/1000,'unixepoch')=:month")
    fun observeExpenseTotalForMonth(month:String): Flow<Double>
    @Query("SELECT * FROM ledger_entries WHERE type IN ('EXPENSE', 'GOAL_EXPENSE') AND strftime('%Y-%m',date/1000,'unixepoch')=:month ORDER BY amount DESC")
    fun observeExpensesForMonth(month:String): Flow<List<LedgerEntry>>
}