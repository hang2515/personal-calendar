package com.hang2515.personalcalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CalendarRulesTest {
    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun scheduledDaysStartOnCreationDateAndRespectSelectedWeekdays() {
        val mondayAndThursday = weekdayMask(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        assertFalse(isScheduled(LocalDate.of(2026, 9, 28), today, mondayAndThursday))
        assertTrue(isScheduled(today, today, mondayAndThursday))
        assertFalse(isScheduled(LocalDate.of(2026, 10, 2), today, mondayAndThursday))
        assertTrue(isScheduled(LocalDate.of(2026, 10, 5), today, mondayAndThursday))
    }

    @Test
    fun statusDistinguishesUnscheduledFutureMissedDueAndCompleted() {
        val startDate = LocalDate.of(2026, 9, 30)
        val everyDay = weekdayMask(DayOfWeek.entries.toSet())

        assertEquals(DayStatus.UNSCHEDULED, dayStatus(LocalDate.of(2026, 9, 29), today, startDate, everyDay, false))
        assertEquals(DayStatus.MISSED, dayStatus(LocalDate.of(2026, 9, 30), today, startDate, everyDay, false))
        assertEquals(DayStatus.DUE, dayStatus(today, today, startDate, everyDay, false))
        assertEquals(DayStatus.COMPLETED, dayStatus(today, today, startDate, everyDay, true))
        assertEquals(DayStatus.UPCOMING, dayStatus(LocalDate.of(2026, 10, 2), today, startDate, everyDay, false))
    }

    @Test
    fun monthlyProgressExcludesFutureAndUnscheduledDays() {
        val everyDay = weekdayMask(DayOfWeek.entries.toSet())
        val progress = monthlyProgress(
            month = java.time.YearMonth.of(2026, 10),
            today = today,
            startDate = LocalDate.of(2026, 9, 1),
            weekdayMask = everyDay,
            completedDates = setOf(today),
        )

        assertEquals(1, progress.plannedDays)
        assertEquals(1, progress.completedDays)
    }

    @Test
    fun monthlyProgressOnlyCountsScheduledWeekdaysEvenIfOtherDatesWereRecorded() {
        val progress = monthlyProgress(
            month = java.time.YearMonth.of(2026, 10),
            today = LocalDate.of(2026, 10, 6),
            startDate = LocalDate.of(2026, 9, 30),
            weekdayMask = weekdayMask(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            completedDates = setOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 6)),
        )

        assertEquals(2, progress.plannedDays)
        assertEquals(1, progress.completedDays)
    }
}
