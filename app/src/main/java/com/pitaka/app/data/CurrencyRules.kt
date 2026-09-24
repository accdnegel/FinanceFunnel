package com.pitaka.app.data

/**
 * Central validation and conversion rules for all currency-aware money movement.
 *
 * Rates are stored as: 1 unit of [currency] = [rateToBase] units of the selected base currency.
 */
object CurrencyRules {
    fun normalize(code: String?): String =
        code.orEmpty().trim().uppercase().ifBlank { "PHP" }

    fun requireCurrency(code: String?): String {
        val normalized = normalize(code)
        require(normalized.length == 3 && normalized.all { it in 'A'..'Z' }) {
            "Currency must be a 3-letter ISO-style code."
        }
        return normalized
    }

    fun requirePositiveFinite(value: Double, label: String): Double {
        require(value.isFinite() && value > 0.0) { "$label must be a finite value greater than zero." }
        return value
    }

    fun requireNonNegativeFinite(value: Double, label: String): Double {
        require(value.isFinite() && value >= 0.0) { "$label must be a finite value greater than or equal to zero." }
        return value
    }

    fun requireRate(rate: Double): Double =
        requirePositiveFinite(rate, "Exchange rate")

    fun convert(amount: Double, from: String, to: String, rates: List<ExchangeRate>): Double {
        require(amount.isFinite()) { "Amount must be finite." }
        val source = requireCurrency(from)
        val destination = requireCurrency(to)
        if (source == destination) return amount

        val sourceRate = rates.firstOrNull { normalize(it.code) == source }?.rateToBase
            ?: throw IllegalArgumentException("No exchange rate is configured for $source.")
        val destinationRate = rates.firstOrNull { normalize(it.code) == destination }?.rateToBase
            ?: throw IllegalArgumentException("No exchange rate is configured for $destination.")
        requireRate(sourceRate)
        requireRate(destinationRate)

        val converted = amount * sourceRate / destinationRate
        require(converted.isFinite()) { "Currency conversion produced an invalid amount." }
        return converted
    }
}
