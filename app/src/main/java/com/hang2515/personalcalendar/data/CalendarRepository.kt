package com.hang2515.personalcalendar.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.hang2515.personalcalendar.domain.isScheduled
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException

class CalendarRepository(
    private val database: CalendarDatabase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
    private val reminderScheduler: ReminderScheduler? = null,
) {
    private val dao = database.calendarDao()

    fun observeCalendars() = dao.observeCalendars()

    fun observeSchedulesOn(date: LocalDate) = dao.observeSchedulesOn(date.toString())

    fun observeCheckInsOn(date: LocalDate) = dao.observeCheckInsOn(date.toString())

    fun observeCheckInsInMonth(calendarId: String, month: YearMonth) =
        dao.observeCheckInsInMonth(calendarId, month.atDay(1).toString(), month.atEndOfMonth().toString())

    fun observeChangesInMonth(calendarId: String, month: YearMonth) =
        dao.observeChangesInMonth(calendarId, month.atDay(1).toString(), month.atEndOfMonth().toString())

    suspend fun createCalendar(
        name: String,
        weekdayMask: Int,
        today: LocalDate,
        reminderMinute: Int = 480,
        reminderEnabled: Boolean = false,
    ) {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) { "日历名称应为 1 至 40 个字符" }
        require(weekdayMask in 1..127) { "请至少选择一个星期" }
        require(reminderMinute in 0..1439) { "提醒时间无效" }
        val calendar = CalendarEntity(
            UUID.randomUUID().toString(), trimmedName, today.toString(), weekdayMask,
            reminderMinute = reminderMinute, reminderEnabled = reminderEnabled,
        )
        database.withTransaction {
            dao.insertCalendar(calendar)
            dao.upsertScheduleChange(ScheduleChangeEntity(calendar.id, today.toString(), weekdayMask))
        }
        refreshReminder(calendar.id)
    }

    suspend fun editCalendar(
        calendarId: String,
        name: String,
        weekdayMask: Int,
        today: LocalDate,
        reminderMinute: Int,
        reminderEnabled: Boolean,
    ) {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) { "日历名称应为 1 至 40 个字符" }
        require(weekdayMask in 1..127) { "请至少选择一个星期" }
        require(reminderMinute in 0..1439) { "提醒时间无效" }
        database.withTransaction {
            val calendar = dao.getCalendar(calendarId) ?: throw IllegalArgumentException("日历不存在")
            if (calendar.name != trimmedName || calendar.weekdayMask != weekdayMask ||
                calendar.reminderMinute != reminderMinute || calendar.reminderEnabled != reminderEnabled
            ) {
                check(dao.updateDetails(calendarId, trimmedName, weekdayMask, reminderMinute, reminderEnabled) == 1)
                if (!calendar.isPaused && calendar.weekdayMask != weekdayMask) {
                    val effectiveDate = if (dao.hasCheckIn(calendarId, today.toString())) today.plusDays(1) else today
                    dao.upsertScheduleChange(ScheduleChangeEntity(calendarId, effectiveDate.toString(), weekdayMask))
                }
            }
        }
        refreshReminder(calendarId)
    }

    suspend fun setPaused(calendarId: String, isPaused: Boolean, today: LocalDate) {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        database.withTransaction {
            val calendar = dao.getCalendar(calendarId) ?: throw IllegalArgumentException("日历不存在")
            if (calendar.isPaused != isPaused) {
                check(dao.updatePaused(calendarId, isPaused) == 1)
                val effectiveDate = if (dao.hasCheckIn(calendarId, today.toString())) today.plusDays(1) else today
                dao.upsertScheduleChange(
                    ScheduleChangeEntity(calendarId, effectiveDate.toString(), if (isPaused) 0 else calendar.weekdayMask),
                )
            }
        }
        refreshReminder(calendarId)
    }

    suspend fun checkIn(calendarId: String, today: LocalDate): Boolean {
        val inserted = database.withTransaction {
            require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
            val calendar = dao.getCalendar(calendarId) ?: throw IllegalArgumentException("日历不存在")
            val mask = dao.getScheduleMaskOn(calendarId, today.toString()) ?: 0
            require(isScheduled(today, calendar.startLocalDate(), mask)) { "今天没有安排打卡" }
            dao.insertCheckIn(CheckInEntity(calendarId, today.toString())) != -1L
        }
        if (inserted) {
            reminderScheduler?.dismissNotification(calendarId)
            refreshReminder(calendarId)
        }
        return inserted
    }

    private suspend fun refreshReminder(calendarId: String) {
        try {
            reminderScheduler?.refresh(calendarId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("ReminderScheduler", "无法更新提醒：${error.javaClass.simpleName}")
        }
    }

    companion object {
        fun open(context: Context): CalendarRepository {
            val database = CalendarDatabase.open(context)
            return CalendarRepository(database, reminderScheduler = ReminderScheduler(context, database))
        }
    }
}
