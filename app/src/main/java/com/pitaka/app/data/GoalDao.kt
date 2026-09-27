package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class GoalWithProgress(
    val id: Long,
    val name: String,
    val type: GoalType,
    val targetAmount: Double,
    val currency: String,
    val targetBalances: String,
    val targetDate: Long?,
    val colorHex: String?,
    val cardStyle: String,
    val createdAt: Long,
    val progress: Double,
    val currencyBalances: String,
    val archivedAt: Long?
)

@Dao
interface GoalDao {
    @Insert
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE goalId = :id AND type = 'GOAL_CONTRIBUTION'")
    suspend fun countContributions(id: Long): Int

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: Long): Goal?

    @Query("""
        SELECT g.id, g.name, g.type, g.targetAmount, g.currency, g.targetBalances, g.targetDate, g.currencyBalances, g.colorHex, g.cardStyle, g.createdAt, g.archivedAt,
               COALESCE((
                                     SELECT SUM(CASE
                                             WHEN l.type = 'GOAL_CONTRIBUTION' THEN COALESCE(l.goalAmount, l.amount)
                                             WHEN l.type IN ('GOAL_WITHDRAWAL', 'GOAL_EXPENSE') THEN -COALESCE(l.goalAmount, l.amount)
                                             ELSE 0 END)
                   FROM ledger_entries l
                   WHERE l.goalId = g.id
                                         AND l.type IN ('GOAL_CONTRIBUTION', 'GOAL_WITHDRAWAL', 'GOAL_EXPENSE')
                     AND COALESCE(l.goalCurrency, l.currency) = g.currency
               ), 0) AS progress
        FROM goals g
        WHERE g.archivedAt IS NULL
        ORDER BY g.createdAt DESC
    """)
    fun observeGoalsWithProgress(): Flow<List<GoalWithProgress>>

    @Query("""
        SELECT g.id, g.name, g.type, g.targetAmount, g.currency, g.targetBalances, g.targetDate, g.currencyBalances, g.colorHex, g.cardStyle, g.createdAt, g.archivedAt,
               COALESCE((
                   SELECT SUM(CASE
                       WHEN l.type = 'GOAL_CONTRIBUTION' THEN COALESCE(l.goalAmount, l.amount)
                       WHEN l.type IN ('GOAL_WITHDRAWAL', 'GOAL_EXPENSE') THEN -COALESCE(l.goalAmount, l.amount)
                       ELSE 0 END)
                   FROM ledger_entries l
                   WHERE l.goalId = g.id
                     AND l.type IN ('GOAL_CONTRIBUTION', 'GOAL_WITHDRAWAL', 'GOAL_EXPENSE')
                     AND COALESCE(l.goalCurrency, l.currency) = g.currency
               ), 0) AS progress
        FROM goals g
        ORDER BY g.createdAt DESC
    """)
    fun observeAllGoalsWithProgress(): Flow<List<GoalWithProgress>>

    @Query("""
        SELECT COALESCE(SUM(CASE
            WHEN l.type = 'GOAL_CONTRIBUTION' THEN COALESCE(l.goalAmount, l.amount)
            WHEN l.type IN ('GOAL_WITHDRAWAL', 'GOAL_EXPENSE') THEN -COALESCE(l.goalAmount, l.amount)
            ELSE 0 END), 0)
        FROM ledger_entries l
        JOIN goals g ON g.id = l.goalId
        WHERE l.type IN ('GOAL_CONTRIBUTION', 'GOAL_WITHDRAWAL', 'GOAL_EXPENSE')
          AND g.type = :type
          AND COALESCE(l.goalCurrency, l.currency) = g.currency
    """)
    fun observeTotalProgressForType(type: GoalType): Flow<Double>

    @Query("SELECT COUNT(*) FROM goals WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) AND id != :excludeId")
    suspend fun countByNormalizedName(name: String, excludeId: Long = 0): Int
}
