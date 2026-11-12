package com.hang2515.personalcalendar.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class DelegatedReminderPlan(val key: String, val startsAtMillis: Long, val recurrence: String)

fun delegatedReminderPlans(
    now: ZonedDateTime,
    startDate: LocalDate,
    changes: List<ScheduleChange>,
    minuteOfDay: Int,
    completedToday: Boolean,
): List<DelegatedReminderPlan> {
    require(minuteOfDay in 0..1439)
    val localNow = now.withZoneSameInstant(ZoneId.of("Asia/Shanghai"))
    val today = localNow.toLocalDate()
    val rules = changes.sortedBy { it.date }
    val labels = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    return rules.mapIndexedNotNull { index, rule ->
        require(rule.weekdayMask in 0..127)
        val until = rules.getOrNull(index + 1)?.date
        if (rule.weekdayMask == 0 || (until != null && !until.isAfter(today))) return@mapIndexedNotNull null
        var from = maxOf(today, startDate, rule.date)
        if (from == today && (completedToday ||
                !from.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(localNow.zone).isAfter(localNow))) {
            from = from.plusDays(1)
        }
        val first = (0..6).asSequence().map { from.plusDays(it.toLong()) }
            .firstOrNull { (until == null || it.isBefore(until)) && isScheduled(it, startDate, rule.weekdayMask) }
            ?: return@mapIndexedNotNull null
        val weekdays = labels.indices.filter { rule.weekdayMask and (1 shl it) != 0 }.joinToString(",") { labels[it] }
        val end = until?.atStartOfDay(localNow.zone)?.minusSeconds(1)?.withZoneSameInstant(ZoneId.of("UTC"))
        val recurrence = "FREQ=WEEKLY;BYDAY=$weekdays;WKST=MO" +
            (end?.format(DateTimeFormatter.ofPattern("';UNTIL='yyyyMMdd'T'HHmmss'Z'")) ?: "")
        DelegatedReminderPlan(rule.date.toString(), first.atTime(minuteOfDay / 60, minuteOfDay % 60)
            .atZone(localNow.zone).toInstant().toEpochMilli(), recurrence)
    }
}
