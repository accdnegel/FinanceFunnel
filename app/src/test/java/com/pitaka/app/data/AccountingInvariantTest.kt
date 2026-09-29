package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(0.0, delta + AccountingMath.reverse(delta), 1e-9)
    }

    @Test
    fun zeroOldAmountDoesNotProduceInfiniteEditRatio() {
        assertEquals(1.0, AccountingMath.editRatio(0.0, 50.0), 1e-9)
    }

    @Test
    fun sourcePitakaOnlyIdentifiesEntriesThatRemovePitakaFunds() {
        assertEquals(11L, LedgerEntry(type = LedgerType.EXPENSE, amount = 1.0, name = "Expense", pitakaId = 11L).sourcePitakaId())
        assertEquals(12L, LedgerEntry(type = LedgerType.GOAL_CONTRIBUTION, amount = 1.0, name = "Contribution", pitakaId = 12L).sourcePitakaId())
        assertEquals(13L, LedgerEntry(type = LedgerType.TRANSFER, amount = 1.0, name = "Transfer", fromPitakaId = 13L, toPitakaId = 14L).sourcePitakaId())
        assertNull(LedgerEntry(type = LedgerType.GOAL_WITHDRAWAL, amount = 1.0, name = "Withdrawal", pitakaId = 15L).sourcePitakaId())
        assertNull(LedgerEntry(type = LedgerType.INCOME, amount = 1.0, name = "Income", pitakaId = 16L).sourcePitakaId())
    }
}
