package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CurrencyRulesTest {
    private val rates = listOf(
        ExchangeRate(code = "PHP", rateToBase = 1.0),
        ExchangeRate(code = "USD", rateToBase = 57.0),
        ExchangeRate(code = "EUR", rateToBase = 66.0)
    )

    @Test
    fun convertsBetweenCurrenciesUsingBaseRates() {
        assertEquals(5700.0, CurrencyRules.convert(100.0, "USD", "PHP", rates), 0.000001)
        assertEquals(100.0, CurrencyRules.convert(5700.0, "PHP", "USD", rates), 0.000001)
        assertEquals(57.0 * 10.0 / 66.0, CurrencyRules.convert(10.0, "USD", "EUR", rates), 0.000001)
    }

    @Test
    fun sameCurrencyDoesNotRequireRate() {
        assertEquals(100.0, CurrencyRules.convert(100.0, "php", "PHP", emptyList()), 0.0)
    }

    @Test
    fun missingRateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyRules.convert(100.0, "USD", "JPY", rates)
        }
    }

    @Test
    fun invalidRateIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyRules.convert(100.0, "USD", "PHP", listOf(
                ExchangeRate(code = "USD", rateToBase = 0.0),
                ExchangeRate(code = "PHP", rateToBase = 1.0)
            ))
        }
    }

    @Test
    fun invalidAmountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyRules.requirePositiveFinite(Double.NaN, "Amount")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyRules.requirePositiveFinite(Double.POSITIVE_INFINITY, "Amount")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CurrencyRules.requirePositiveFinite(0.0, "Amount")
        }
    }
}
