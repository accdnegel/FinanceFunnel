package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Pitaka is a fund source/pool — a bank account, cash wallet, e-wallet, etc.
 * Its balance is a live, mutated field (not derived), updated every time a
 * transaction (income, expense, transfer, or goal contribution) touches it.
 */
@Entity(tableName = "pitakas")
data class Pitaka(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currentAmount: Double,
    val currency: String = "USD",
    val colorHex: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
