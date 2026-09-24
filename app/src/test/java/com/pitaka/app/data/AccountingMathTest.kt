package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountingMathTest {
    @Test
    fun editRatioScalesDestinationLeg() {
        val ratio = AccountingMath.editRatio(10.0, 25.0)
        assertEquals(2.5, ratio, 0.000001)
        assertEquals(1450.0, AccountingMath.scaleAllocation(580.0, ratio), 0.000001)
    }

    @Test
    fun reverseIsItsOwnInverse() {
        val value = 123.456
        assertEquals(value, AccountingMath.reverse(AccountingMath.reverse(value)), 0.000001)
    }

    @Test
    fun editThenReverseNewLegReturnsZeroDelta() {
        val original = 580.0
        val ratio = AccountingMath.editRatio(10.0, 25.0)
        val edited = AccountingMath.scaleAllocation(original, ratio)
        assertEquals(0.0, edited + AccountingMath.reverse(edited), 0.000001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteRatio() {
        AccountingMath.scaleAllocation(100.0, Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteValue() {
        AccountingMath.reverse(Double.POSITIVE_INFINITY)
    }
}
