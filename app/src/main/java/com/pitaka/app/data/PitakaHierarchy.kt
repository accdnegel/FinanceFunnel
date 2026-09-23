package com.pitaka.app.data

/**
 * Returns the balances that should be displayed for a Pitaka.
 *
 * A leaf Pitaka owns its own balances. Once it has children, it becomes a
 * container and its displayed balance is the recursive sum of its descendants.
 */
fun Pitaka.effectiveBalances(allPitakas: List<Pitaka>): Map<String, Double> {
    val children = allPitakas.filter { it.parentPitakaId == id }
    if (children.isEmpty()) return CurrencyBalances.parse(currencyBalances)
    val totals = mutableMapOf<String, Double>()
    children.forEach { child ->
        child.effectiveBalances(allPitakas).forEach { (code, amount) ->
            totals[code] = (totals[code] ?: 0.0) + amount
        }
    }
    return totals.filterValues { kotlin.math.abs(it) > 0.0000001 }
}

fun Pitaka.hasChildren(allPitakas: List<Pitaka>): Boolean =
    allPitakas.any { it.parentPitakaId == id }
