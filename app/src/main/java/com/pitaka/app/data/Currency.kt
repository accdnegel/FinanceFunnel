package com.pitaka.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Curated list of currencies offered when creating a Pitaka. Users aren't limited to these
 *  (a custom 3-letter code can be typed), but these cover the common cases without typing. */
val commonCurrencies = listOf("USD", "PHP", "EUR", "GBP", "JPY", "SGD", "AUD", "INR")

/** Single-row table: which currency totals are displayed in on the Home dashboard. */
@Entity(tableName = "currency_settings")
data class CurrencySettings(
    @PrimaryKey val id: Int = ROW_ID,
    val baseCurrency: String = "USD"
) {
    companion object {
        const val ROW_ID = 0
    }
}

/**
 * Manually-maintained exchange rate: 1 unit of [code] = [rateToBase] units of the base currency.
 * There's deliberately no live-rate fetching — this app works fully offline, so rates are
 * whatever the user last typed in.
 */
@Entity(tableName = "exchange_rates", primaryKeys = ["code"])
data class ExchangeRate(
    val code: String,
    val rateToBase: Double
)
