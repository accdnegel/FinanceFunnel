package com.pitaka.app.data

/**
 * Pure arithmetic used by ledger edits. Keeping this separate makes the
 * transaction-leg invariants directly unit-testable without a Room database.
 */
object AccountingMath {
    fun editRatio(oldAmount: Double, newAmount: Double): Double {
        require(oldAmount.isFinite()) { "Existing ledger amount must be finite." }
        require(newAmount.isFinite()) { "New ledger amount must be finite." }
        return if (oldAmount != 0.0) newAmount / oldAmount else 1.0
    }

    fun scaleAllocation(original: Double, ratio: Double): Double {
        require(original.isFinite()) { "Existing allocation must be finite." }
        require(ratio.isFinite()) { "Edit ratio must be finite." }
        val result = original * ratio
        require(result.isFinite()) { "Scaled allocation must be finite." }
        return result
    }

    fun reverse(value: Double): Double {
        require(value.isFinite()) { "Ledger value must be finite." }
        return -value
    }
}
