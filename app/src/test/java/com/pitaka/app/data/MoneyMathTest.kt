package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyMathTest {
    @Test fun decimalAdditionAvoidsBinaryFloatingPointDrift() {
        assertEquals(0.3, MoneyMath.add(0.1, 0.2), 0.0)
    }

    @Test fun decimalSubtractionIsStable() {
        assertEquals(0.2, MoneyMath.subtract(0.3, 0.1), 0.0)
    }

    @Test fun multiplicationUsesControlledDecimalScale() {
        assertEquals(0.33, MoneyMath.multiply(0.11, 3.0), 0.0)
    }

    @Test fun divisionUsesHalfUpRounding() {
        assertEquals(0.33, MoneyMath.divide(1.0, 3.0), 0.00000001)
    }

    @Test fun roundUsesRequestedScale() {
        assertEquals(10.13, MoneyMath.round(10.125, 2), 0.0)
    }

    @Test fun rejectsNonFiniteInputs() {
        assertThrows(IllegalArgumentException::class.java) { MoneyMath.add(Double.NaN, 1.0) }
        assertThrows(IllegalArgumentException::class.java) { MoneyMath.divide(1.0, 0.0) }
    }
}
