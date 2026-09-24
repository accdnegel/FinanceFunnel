package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurrencyBalancesTest {
    @Test fun addCreatesAndUpdatesCurrencyBalance() {
        val first = CurrencyBalances.add("", "php", 100.0)
        val second = CurrencyBalances.add(first, "PHP", 25.5)
        assertEquals(125.5, CurrencyBalances.parse(second)["PHP"] ?: 0.0, 0.000001)
    }

    @Test fun parseNormalizesCodesAndIgnoresMalformedTokens() {
        val balances = CurrencyBalances.parse(" php = 10 | USD=20 |broken|EUR=nope ")
        assertEquals(10.0, balances["PHP"] ?: 0.0, 0.000001)
        assertEquals(20.0, balances["USD"] ?: 0.0, 0.000001)
        assertEquals(0.0, balances["EUR"] ?: 0.0, 0.000001)
    }

    @Test fun encodeIsStableAndSorted() {
        assertEquals("EUR=2.0|PHP=10.0|USD=5.0", CurrencyBalances.encode(mapOf("USD" to 5.0, "PHP" to 10.0, "EUR" to 2.0)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidCurrencyCode() {
        CurrencyBalances.add("", "US", 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteBalance() {
        CurrencyBalances.add("", "PHP", Double.NaN)
    }

    @Test
    fun addAndReverseReturnToOriginalBalance() {
        val original = CurrencyBalances.encode(mapOf("PHP" to 1000.0, "USD" to 50.0))
        val afterAdd = CurrencyBalances.add(original, "PHP", 125.75)
        val restored = CurrencyBalances.add(afterAdd, "PHP", -125.75)
        assertEquals(original, restored)
    }

    @Test
    fun currenciesRemainIndependent() {
        val balances = CurrencyBalances.add("PHP=100", "USD", 25.0)
        val updated = CurrencyBalances.add(balances, "PHP", -40.0)
        val parsed = CurrencyBalances.parse(updated)
        assertEquals(60.0, parsed["PHP"] ?: 0.0, 0.000001)
        assertEquals(25.0, parsed["USD"] ?: 0.0, 0.000001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteDelta() {
        CurrencyBalances.add("PHP=100", "PHP", Double.POSITIVE_INFINITY)
    }
}


