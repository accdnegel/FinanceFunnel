package com.pitaka.app.data

/** Small dependency-free representation of balances held in multiple currencies. */
object CurrencyBalances {
    private fun normalizedCode(currency: String): String {
        val code = currency.trim().uppercase()
        require(code.isNotBlank()) { "Currency code cannot be blank." }
        require(code.length == 3 && code.all { it in 'A'..'Z' }) { "Currency code must be exactly 3 letters." }
        return code
    }

    private fun checkedAmount(amount: Double): Double {
        require(amount.isFinite()) { "Money amount must be finite." }
        return amount
    }

    fun parse(raw: String?): MutableMap<String, Double> {
        val result = mutableMapOf<String, Double>()
        raw.orEmpty().split('|').forEach { token ->
            val parts = token.split('=', limit = 2)
            if (parts.size != 2) return@forEach
            val code = parts[0].trim().uppercase()
            if (code.isBlank()) return@forEach
            val normalized = code.takeIf { it.length == 3 && it.all { ch -> ch in 'A'..'Z' } } ?: return@forEach
            val amount = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@forEach
            // Be tolerant of legacy/corrupted serialized rows: malformed tokens do not crash
            // the dashboard, and duplicate legacy entries are safely combined.
            result[normalized] = (result[normalized] ?: 0.0) + amount
        }
        return result
    }

    fun encode(values: Map<String, Double>): String = values
        .filterKeys { it.isNotBlank() }
        .map { (code, amount) -> normalizedCode(code) to checkedAmount(amount) }
        .toMap()
        .toSortedMap()
        .entries.joinToString("|") { "${it.key}=${it.value}" }

    fun add(raw: String?, currency: String, delta: Double): String {
        val values = parse(raw)
        val code = normalizedCode(currency)
        checkedAmount(delta)
        values[code] = checkedAmount((values[code] ?: 0.0) + delta)
        return encode(values)
    }
}

fun String.supportedCurrencies(): Set<String> = CurrencyBalances.parse(this).keys

fun Map<String, Double>.displayLines(): String = entries.sortedBy { it.key }.joinToString(" • ") { "${it.key} ${"%,.2f".format(it.value)}" }
