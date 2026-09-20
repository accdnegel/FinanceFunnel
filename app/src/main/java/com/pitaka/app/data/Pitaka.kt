package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Pitaka is a fund source/pool. Pitakas may be leaves or parents containing
 * other Pitakas (for example one bank with several currency accounts).
 */
@Entity(tableName = "pitakas")
data class Pitaka(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currentAmount: Double,
    val currency: String = "PHP",
    val currencyBalances: String = "PHP=0",
    val colorHex: String? = null,
    val parentPitakaId: Long? = null,
    /** Batik/card art identifier; "solid" preserves the legacy color-only appearance. */
    val cardStyle: String = "solid",
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)