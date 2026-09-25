package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountingInvariantTest {
    @Test
    fun expenseAllocationEditRatioPreservesProportionalAllocation() {
        val ratio = AccountingMath.editRatio(100.0, 150.0)
        assertEquals(75.0, AccountingMath.scaleAllocation(50.0, ratio), 1e-9)
    }

    @Test
    fun goalAllocationEditRatioPreservesProportionalAllocation() {
        val ratio = AccountingMath.editRatio(80.0, 120.0)
        assertEquals(45.0, AccountingMath.scaleAllocation(30.0, ratio), 1e-9)
    }

    @Test
    fun transferDestinationAllocationScalesWithEditedSource() {
        val ratio = AccountingMath.editRatio(200.0, 250.0)
        assertEquals(312.5, AccountingMath.scaleAllocation(250.0, ratio), 1e-9)
    }

    @Test
    fun reversingAnEffectRestoresOriginalDelta() {
        val delta = 1234.5678
        assertEquals(0.0, AccountingMath.add(delta, -delta), 1e-9)
    }

    @Test
    fun zeroOldAmountDoesNotProduceInfiniteEditRatio() {
        assertEquals(1.0, AccountingMath.editRatio(0.0, 50.0), 1e-9)
    }
}
