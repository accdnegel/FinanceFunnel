package com.pitaka.app.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Decimal-safe helpers for monetary calculations.
 *
 * Persistence remains Double for backward compatibility in schema v11.
 * New accounting arithmetic should use these helpers at boundaries where
 * decimal rounding matters, then convert back to Double only at the DAO boundary.
 */
object MoneyMath {
    private const val SCALE = 8

    fun add(a: Double, b: Double): Double =
        decimal(a).add(decimal(b)).toDoubleChecked()

    fun subtract(a: Double, b: Double): Double =
        decimal(a).subtract(decimal(b)).toDoubleChecked()

    fun multiply(a: Double, factor: Double): Double =
        decimal(a).multiply(decimal(factor)).setScale(SCALE, RoundingMode.HALF_UP).toDoubleChecked()

    fun divide(a: Double, b: Double): Double {
        require(b.isFinite() && b != 0.0) { "Divisor must be finite and non-zero." }
        return decimal(a).divide(decimal(b), SCALE, RoundingMode.HALF_UP).toDoubleChecked()
    }

    fun round(amount: Double, scale: Int = 2): Double {
        require(amount.isFinite()) { "Amount must be finite." }
        require(scale >= 0) { "Scale cannot be negative." }
        return decimal(amount).setScale(scale, RoundingMode.HALF_UP).toDoubleChecked()
    }

    private fun decimal(value: Double): BigDecimal {
        require(value.isFinite()) { "Amount must be finite." }
        return BigDecimal.valueOf(value)
    }

    private fun BigDecimal.toDoubleChecked(): Double {
        val result = toDouble()
        require(result.isFinite()) { "Monetary result must be finite." }
        return result
    }
}
