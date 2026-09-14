package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_funnels")
data class ExpenseFunnel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limit: Double,
    val currency: String = "PHP",
    /** Serialized currency-to-balance map; supports multiple currencies without losing legacy fields. */
    val currencyBalances: String = "PHP=0",
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val colorHex: String? = null
)

data class ExpenseFunnelWithSpend(
    val id: Long,
    val name: String,
    val limit: Double,
    val currency: String = "PHP",
    /** Serialized currency-to-balance map; supports multiple currencies without losing legacy fields. */
    val currencyBalances: String = "PHP=0",
    val validFrom: Long?,
    val validUntil: Long?,
    val colorHex: String?,
    val spent: Double
) {
    val remaining: Double get() = limit - spent
}
