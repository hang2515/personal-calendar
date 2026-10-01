package com.hang2515.personalcalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ScheduleChangesTest {
    private val changes = listOf(
        ScheduleChange(LocalDate.of(2026, 9, 29), 127),
        ScheduleChange(LocalDate.of(2026, 10, 2), 0),
        ScheduleChange(LocalDate.of(2026, 10, 5), 1),
    )

    @Test
    fun pausedDatesStayUnscheduledAfterResuming() {
        assertEquals(127, scheduleMaskOn(LocalDate.of(2026, 10, 1), changes))
        assertEquals(0, scheduleMaskOn(LocalDate.of(2026, 10, 2), changes))
        assertEquals(0, scheduleMaskOn(LocalDate.of(2026, 10, 4), changes))
        assertEquals(1, scheduleMaskOn(LocalDate.of(2026, 10, 5), changes))
        assertFalse(isScheduled(LocalDate.of(2026, 10, 4), changes.first().date, scheduleMaskOn(LocalDate.of(2026, 10, 4), changes)))
        assertTrue(isScheduled(LocalDate.of(2026, 10, 5), changes.first().date, scheduleMaskOn(LocalDate.of(2026, 10, 5), changes)))
    }

    @Test
    fun monthlyProgressExcludesPausedDatesAndFollowsEditedRule() {
        val progress = monthlyProgress(
            month = YearMonth.of(2026, 10),
            today = LocalDate.of(2026, 10, 6),
            completedDates = setOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 4)),
        ) { date -> isScheduled(date, changes.first().date, scheduleMaskOn(date, changes)) }

        assertEquals(2, progress.plannedDays)
        assertEquals(1, progress.completedDays)
    }
}
