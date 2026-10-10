package com.hang2515.personalcalendar.domain

import java.time.LocalDate
import java.time.ZonedDateTime

fun nextReminderAt(
    now: ZonedDateTime,
    startDate: LocalDate,
    changes: List<ScheduleChange>,
    minuteOfDay: Int,
    completedDates: Set<LocalDate>,
): ZonedDateTime? {
    require(minuteOfDay in 0..1439)
    for (offset in 0..7) {
        val date = now.toLocalDate().plusDays(offset.toLong())
        val mask = scheduleMaskOn(date, changes)
        if (!isScheduled(date, startDate, mask) || date in completedDates) continue
        val reminder = date.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(now.zone)
        if (reminder.isAfter(now)) return reminder
    }
    return null
}
