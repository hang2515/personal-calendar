package com.hang2515.personalcalendar.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class DayStatus { UNSCHEDULED, UPCOMING, DUE, COMPLETED, MISSED }

data class MonthlyProgress(val plannedDays: Int, val completedDays: Int)

data class ScheduleChange(val date: LocalDate, val weekdayMask: Int)

fun scheduleMaskOn(date: LocalDate, changes: List<ScheduleChange>): Int =
    changes.lastOrNull { !it.date.isAfter(date) }?.weekdayMask ?: 0

fun weekdayMask(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day ->
    mask or (1 shl (day.value - 1))
}

fun isScheduled(date: LocalDate, startDate: LocalDate, weekdayMask: Int): Boolean =
    !date.isBefore(startDate) && weekdayMask and (1 shl (date.dayOfWeek.value - 1)) != 0

fun dayStatus(
    date: LocalDate,
    today: LocalDate,
    startDate: LocalDate,
    weekdayMask: Int,
    isCompleted: Boolean,
): DayStatus = when {
    !isScheduled(date, startDate, weekdayMask) -> DayStatus.UNSCHEDULED
    isCompleted -> DayStatus.COMPLETED
    date.isBefore(today) -> DayStatus.MISSED
    date.isAfter(today) -> DayStatus.UPCOMING
    else -> DayStatus.DUE
}

fun monthlyProgress(
    month: YearMonth,
    today: LocalDate,
    startDate: LocalDate,
    weekdayMask: Int,
    completedDates: Set<LocalDate>,
): MonthlyProgress = monthlyProgress(month, today, completedDates) { date ->
    isScheduled(date, startDate, weekdayMask)
}

fun monthlyProgress(
    month: YearMonth,
    today: LocalDate,
    completedDates: Set<LocalDate>,
    isPlanned: (LocalDate) -> Boolean,
): MonthlyProgress {
    var planned = 0
    var completed = 0
    for (day in 1..month.lengthOfMonth()) {
        val date = month.atDay(day)
        if (!date.isAfter(today) && isPlanned(date)) {
            planned++
            if (date in completedDates) completed++
        }
    }
    return MonthlyProgress(planned, completed)
}
