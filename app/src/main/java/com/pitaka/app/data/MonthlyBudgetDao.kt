package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyBudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: MonthlyBudget)

    @Query("DELETE FROM monthly_budgets WHERE month = :month")
    suspend fun clearForMonth(month: String)

    @Query("SELECT * FROM monthly_budgets WHERE month = :month")
    suspend fun getExactForMonth(month: String): MonthlyBudget?

    /**
     * The limit that actually applies to `month`: an exact match if one was set for that
     * month, otherwise the most recent earlier month's limit (carried forward).
     */
    @Query(
        """
        SELECT * FROM monthly_budgets
        WHERE month <= :month
        ORDER BY month DESC
        LIMIT 1
        """
    )
    fun observeEffectiveBudget(month: String): Flow<MonthlyBudget?>

    @Query("SELECT * FROM monthly_budgets ORDER BY month DESC")
    fun observeAllBudgets(): Flow<List<MonthlyBudget>>
}
