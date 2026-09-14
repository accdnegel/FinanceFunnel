package com.pitaka.app.data

/** Small dependency-free representation of balances held in multiple currencies. */
object CurrencyBalances {
    fun parse(raw: String?): MutableMap<String, Double> = raw.orEmpty()
        .split('|').asSequence()
        .mapNotNull { token ->
            val parts = token.split('=', limit = 2)
            if (parts.size != 2) null else parts[0].trim().uppercase().takeIf { it.isNotEmpty() }?.let { code ->
                code to (parts[1].toDoubleOrNull() ?: 0.0)
            }
        }.toMap().toMutableMap()

    fun encode(values: Map<String, Double>): String = values
        .filterKeys { it.isNotBlank() }
        .toSortedMap()
        .entries.joinToString("|") { "${it.key.uppercase()}=${it.value}" }

    fun add(raw: String?, currency: String, delta: Double): String {
        val values = parse(raw)
        val code = currency.trim().uppercase().ifBlank { "PHP" }
        values[code] = (values[code] ?: 0.0) + delta
        return encode(values)
    }
}

fun String.supportedCurrencies(): Set<String> = CurrencyBalances.parse(this).keys

fun Map<String, Double>.displayLines(): String = entries.sortedBy { it.key }.joinToString(" • ") { "${it.key} ${"%,.2f".format(it.value)}" }
