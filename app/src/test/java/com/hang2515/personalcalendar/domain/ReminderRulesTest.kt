package com.hang2515.personalcalendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val startDate = LocalDate.of(2026, 10, 1)
    private val daily = listOf(ScheduleChange(startDate, 127))

    @Test
    fun reminderUsesLocalTimeAndMovesToNextDayAfterTimePasses() {
        val before = startDate.atTime(7, 59).atZone(zone)
        val after = startDate.atTime(8, 1).atZone(zone)

        assertEquals(startDate.atTime(8, 0).atZone(zone), nextReminderAt(before, startDate, daily, 480, emptySet()))
        assertEquals(startDate.plusDays(1).atTime(8, 0).atZone(zone), nextReminderAt(after, startDate, daily, 480, emptySet()))
    }

    @Test
    fun completedTodaySkipsItsReminder() {
        val now = startDate.atTime(7, 0).atZone(zone)
        assertEquals(
            startDate.plusDays(1).atTime(8, 0).atZone(zone),
            nextReminderAt(now, startDate, daily, 480, setOf(startDate)),
        )
    }

    @Test
    fun weekdayAndPauseChangesControlFutureReminder() {
        val mondayOnly = listOf(ScheduleChange(startDate, 1))
        val now = startDate.atTime(9, 0).atZone(zone)
        assertEquals(LocalDate.of(2026, 10, 5).atTime(8, 0).atZone(zone), nextReminderAt(now, startDate, mondayOnly, 480, emptySet()))

        val paused = listOf(ScheduleChange(startDate, 127), ScheduleChange(startDate.plusDays(1), 0))
        assertNull(nextReminderAt(now, startDate, paused, 480, emptySet()))
    }
}
