package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyBalancesTest {
    @Test
    fun addCreatesAndUpdatesIndependentCurrencyBalances() {
        val raw = CurrencyBalances.add("PHP=1000", "USD", 25.0)
        val updated = CurrencyBalances.add(raw, "PHP", -100.0)
        val values = CurrencyBalances.parse(updated)

        assertEquals(900.0, values["PHP"] ?: 0.0, 0.0)
        assertEquals(25.0, values["USD"] ?: 0.0, 0.0)
    }

    @Test
    fun encodingIsStableAndSorted() {
        assertEquals(
            "EUR=3.0|PHP=1000.0|USD=25.0",
            CurrencyBalances.encode(mapOf("USD" to 25.0, "PHP" to 1000.0, "EUR" to 3.0))
        )
    }

    @Test
    fun malformedEntriesDoNotCrashParsing() {
        val values = CurrencyBalances.parse("PHP=1000|broken|USD=25|EUR=nope")
        assertEquals(1000.0, values["PHP"] ?: 0.0, 0.0)
        assertEquals(25.0, values["USD"] ?: 0.0, 0.0)
    }
}
