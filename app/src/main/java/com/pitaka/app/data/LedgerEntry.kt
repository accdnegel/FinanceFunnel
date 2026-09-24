package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LedgerType {
    INCOME,
    EXPENSE,
    TRANSFER,
    GOAL_CONTRIBUTION,
    ADJUSTMENT
}

/**
 * Unified audit ledger. [amount]/[currency] are always the actual transaction values.
 *
 * [funnelAmount]/[funnelCurrency] are the values applied to the selected Expense Funnel.
 * They may differ from the actual transaction when the user explicitly chooses conversion.
 *
 * [goalAmount]/[goalCurrency] are the values applied to the selected Goal. They may differ
 * from the actual transaction when the user explicitly chooses conversion.
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
    /** Explicit destination-side currency for cross-currency transfers. */
    val secondaryCurrency: String? = null,
    val goalId: Long? = null,
    val funnelId: Long? = null,
    val funnelAmount: Double? = null,
    val funnelCurrency: String? = null,
    val goalAmount: Double? = null,
    val goalCurrency: String? = null,
    val date: Long = System.currentTimeMillis(),
    /** Exchange-rate snapshot used when this transaction was created; null for legacy rows. */
    val conversionRateToBaseAtTransaction: Double? = null,
    /** Transaction amount converted using the stored historical rate. */
    val amountInBaseAtTransaction: Double? = null,
    /** Base currency used for the historical conversion snapshot. */
    val baseCurrencyAtTransaction: String? = null
)
