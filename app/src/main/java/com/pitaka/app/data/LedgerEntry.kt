package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LedgerType {
    INCOME,            // money enters a Pitaka from outside the system (e.g. salary)
    EXPENSE,           // money leaves a Pitaka via actual spending (counts toward the monthly cap)
    TRANSFER,          // money moves from one Pitaka to another (net worth unchanged)
    GOAL_CONTRIBUTION, // money leaves a Pitaka into a Goal's progress (net worth unchanged;
                       // does NOT count toward the monthly expense cap)
    ADJUSTMENT         // manual correction to a Pitaka's balance, bypassing the normal logs.
                       // `amount` is signed here (positive or negative) unlike other types.
}

/**
 * A single unified ledger row. Which fields are meaningful depends on `type`:
 *
 * - INCOME:            pitakaId = destination Pitaka (amount added)
 * - EXPENSE:           pitakaId = source Pitaka (amount removed), category is set
 * - TRANSFER:          fromPitakaId / toPitakaId used instead of pitakaId. `amount` is removed
 *                      from fromPitaka in its own currency; `secondaryAmount` (if set, for
 *                      cross-currency transfers) is what's added to toPitaka in ITS currency.
 *                      If secondaryAmount is null, the same `amount` applies to both sides.
 * - GOAL_CONTRIBUTION: pitakaId = source Pitaka (amount removed), goalId = destination Goal
 * - ADJUSTMENT:        pitakaId = the Pitaka being corrected, amount is signed (+/-)
 *
 * Total net worth (all Pitakas + all Goal progress) only changes via INCOME, EXPENSE, and
 * ADJUSTMENT — TRANSFER and GOAL_CONTRIBUTION are pure reallocations.
 */
@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: LedgerType,
    val amount: Double,
    val currency: String = "PHP",
    val name: String,
    val category: String? = null,
    val pitakaId: Long? = null,
    val fromPitakaId: Long? = null,
    val toPitakaId: Long? = null,
    val secondaryAmount: Double? = null,
    val goalId: Long? = null,
    val funnelId: Long? = null,
    val date: Long = System.currentTimeMillis()
)
