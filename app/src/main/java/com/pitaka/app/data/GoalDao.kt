package com.pitaka.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class GoalWithProgress(
    val id: Long,
    val name: String,
    val type: GoalType,
    val targetAmount: Double,
    val currency: String,
    val targetDate: Long,
    val colorHex: String?,
    val cardStyle: String,
    val createdAt: Long,
    val progress: Double
)

@Dao
interface GoalDao {
    @Insert
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Delete
    suspend fun deleteGoal(goal: Goal)

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getGoal(id: Long): Goal?

    @Query("""
        SELECT g.id, g.name, g.type, g.targetAmount, g.currency, g.targetDate, g.colorHex, g.cardStyle, g.createdAt,
               COALESCE((
                   SELECT SUM(COALESCE(l.goalAmount, l.amount))
                   FROM ledger_entries l
                   WHERE l.goalId = g.id
                     AND l.type = 'GOAL_CONTRIBUTION'
                     AND COALESCE(l.goalCurrency, l.currency) = g.currency
               ), 0) AS progress
        FROM goals g
        ORDER BY g.createdAt DESC
    """)
    fun observeGoalsWithProgress(): Flow<List<GoalWithProgress>>

    @Query("""
        SELECT COALESCE(SUM(COALESCE(l.goalAmount, l.amount)), 0)
        FROM ledger_entries l
        JOIN goals g ON g.id = l.goalId
        WHERE l.type = 'GOAL_CONTRIBUTION'
          AND g.type = :type
          AND COALESCE(l.goalCurrency, l.currency) = g.currency
    """)
    fun observeTotalProgressForType(type: GoalType): Flow<Double>
}
