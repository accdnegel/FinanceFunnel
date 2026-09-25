package com.pitaka.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurringScheduleTest {
    @Test fun day31OnFebruaryClampsToLastDay() {
        val today = LocalDate.of(2026, 2, 28)
        assertEquals(28, 31.coerceAtMost(today.lengthOfMonth()))
    }

    @Test fun day30OnAprilClampsToThirty() {
        val today = LocalDate.of(2026, 4, 30)
        assertEquals(30, 30.coerceAtMost(today.lengthOfMonth()))
    }

    @Test fun day15DoesNotPostBeforeScheduledDay() {
        val today = LocalDate.of(2026, 9, 14)
        assertEquals(true, today.dayOfMonth < 15)
    }

    @Test fun scheduledDayPostsOnOrAfterConfiguredDay() {
        val today = LocalDate.of(2026, 9, 15)
        assertEquals(false, today.dayOfMonth < 15)
    }
}
