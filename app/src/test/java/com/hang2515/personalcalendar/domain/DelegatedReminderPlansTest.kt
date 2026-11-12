package com.hang2515.personalcalendar.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DelegatedReminderPlansTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday = LocalDate.of(2026, 11, 9)
    private val started = monday.minusDays(9)

    @Test fun dailyPlanIsAnUnboundedSystemRecurrenceAndSkipsCompletedOrPassedToday() {
        val rules = listOf(ScheduleChange(started, 127))
        val before = monday.atTime(10, 0).atZone(zone)
        val plan = delegatedReminderPlans(before, started, rules, 660, false).single()
        assertEquals(monday.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(), plan.startsAtMillis)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU;WKST=MO", plan.recurrence)
        assertEquals(monday.plusDays(1).atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
            delegatedReminderPlans(before, started, rules, 660, true).single().startsAtMillis)
        assertEquals(monday.plusDays(1).atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
            delegatedReminderPlans(monday.atTime(11, 0).atZone(zone), started, rules, 660, false).single().startsAtMillis)
    }

    @Test fun futurePauseEndsOldRecurrenceAndFutureResumeRemainsScheduledWithoutOpeningApp() {
        val rules = listOf(ScheduleChange(started, 127), ScheduleChange(monday.plusDays(1), 0),
            ScheduleChange(monday.plusDays(20), 1))
        val plans = delegatedReminderPlans(monday.atTime(10, 0).atZone(zone), started, rules, 660, false)
        assertEquals(2, plans.size)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU;WKST=MO;UNTIL=20261109T155959Z", plans[0].recurrence)
        assertEquals(LocalDate.of(2026, 11, 30).atTime(11, 0).atZone(zone).toInstant().toEpochMilli(), plans[1].startsAtMillis)
        assertEquals("FREQ=WEEKLY;BYDAY=MO;WKST=MO", plans[1].recurrence)
    }

    @Test fun noOccurrenceBeforeNextRuleProducesNoStaleEventAndBeijingTimeIsFixed() {
        val rules = listOf(ScheduleChange(started, 64), ScheduleChange(monday.plusDays(2), 0))
        assertTrue(delegatedReminderPlans(monday.atTime(10, 0).atZone(zone), started, rules, 660, false).isEmpty())
        val now = monday.atTime(2, 0).atZone(ZoneId.of("UTC"))
        val plan = delegatedReminderPlans(now, started, listOf(ScheduleChange(started, 127)), 660, false).single()
        assertEquals(monday.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(), plan.startsAtMillis)
    }
}
