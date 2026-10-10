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
    fun monthlyProgressIncludesFuturePlansButNeverFutureCheckIns() {
        val everyDay = weekdayMask(DayOfWeek.entries.toSet())
        val progress = monthlyProgress(
            month = java.time.YearMonth.of(2026, 10),
            today = today,
            startDate = LocalDate.of(2026, 9, 1),
            weekdayMask = everyDay,
            completedDates = setOf(today, today.plusDays(1)),
        )

        assertEquals(31, progress.plannedDays)
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

        assertEquals(9, progress.plannedDays)
        assertEquals(1, progress.completedDays)
    }

    @Test fun dailyCalendarCreatedOnOctoberThirdHasTwentyNinePlansAndTwoCompletions() {
        val progress = monthlyProgress(java.time.YearMonth.of(2026, 10), LocalDate.of(2026, 10, 10),
            LocalDate.of(2026, 10, 3), 127, setOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 8)))
        assertEquals(MonthlyProgress(29, 2), progress)
    }

    @Test fun monthBoundariesRespectCreationLeapYearAndFutureMonth() {
        val start = LocalDate.of(2028, 2, 28)
        val today = LocalDate.of(2028, 2, 28)
        assertEquals(MonthlyProgress(0, 0), monthlyProgress(java.time.YearMonth.of(2028, 1), today, start, 127, emptySet()))
        assertEquals(MonthlyProgress(2, 1), monthlyProgress(java.time.YearMonth.of(2028, 2), today, start, 127, setOf(start)))
        assertEquals(MonthlyProgress(31, 0), monthlyProgress(java.time.YearMonth.of(2028, 3), today, start, 127,
            setOf(LocalDate.of(2028, 3, 1))))
    }
}
