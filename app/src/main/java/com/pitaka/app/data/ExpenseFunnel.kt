package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_funnels")
data class ExpenseFunnel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val limit: Double,
    val currency: String = "PHP",
    val currencyBalances: String = "PHP=0",
    val validFrom: Long? = null,
    val validUntil: Long? = null,
    val colorHex: String? = null,
    val cardStyle: String = "solid",
    /** System funnels are created by the app and are not user-created categories. */
    val isSystem: Boolean = false
)

data class ExpenseFunnelWithSpend(
    val id: Long,
    val name: String,
    val limit: Double,
    val currency: String = "PHP",
    val currencyBalances: String = "PHP=0",
    val validFrom: Long?,
    val validUntil: Long?,
    val colorHex: String?,
    val cardStyle: String = "solid",
    val isSystem: Boolean = false,
    val spent: Double
) {
    val remaining: Double get() = limit - spent
    val progress: Double get() = if (limit > 0.0) (spent / limit).coerceIn(0.0, 1.0) else 0.0
}