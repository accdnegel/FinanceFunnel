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


/** Aggregates balances for a Pitaka hierarchy. A parent is a container, so once it has
 * children its own stored balance is excluded and only descendant balances are summed. */
fun hierarchicalBalanceMap(pitakas: List<Pitaka>, pitakaId: Long): Map<String, Double> {
    val byParent = pitakas.groupBy { it.parentPitakaId }
    fun collect(id: Long): Map<String, Double> {
        val children = byParent[id].orEmpty()
        if (children.isEmpty()) {
            val leaf = pitakas.firstOrNull { it.id == id } ?: return emptyMap()
            return CurrencyBalances.parse(leaf.currencyBalances)
        }
        val result = mutableMapOf<String, Double>()
        children.forEach { child ->
            collect(child.id).forEach { (currency, amount) ->
                result[currency] = (result[currency] ?: 0.0) + amount
            }
        }
        return result
    }
    return collect(pitakaId)
}
