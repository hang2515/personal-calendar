package com.hang2515.personalcalendar.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.hang2515.personalcalendar.domain.isScheduled
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Clock
import java.util.UUID

class CalendarRepository(
    private val database: CalendarDatabase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
) {
    private val dao = database.calendarDao()

    fun observeCalendars() = dao.observeCalendars()

    fun observeSchedulesOn(date: LocalDate) = dao.observeSchedulesOn(date.toString())

    fun observeCheckInsOn(date: LocalDate) = dao.observeCheckInsOn(date.toString())

    fun observeCheckInsInMonth(calendarId: String, month: YearMonth) =
        dao.observeCheckInsInMonth(calendarId, month.atDay(1).toString(), month.atEndOfMonth().toString())

    fun observeChangesInMonth(calendarId: String, month: YearMonth) =
        dao.observeChangesInMonth(calendarId, month.atDay(1).toString(), month.atEndOfMonth().toString())

    suspend fun createCalendar(name: String, weekdayMask: Int, today: LocalDate) {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) { "日历名称应为 1 至 40 个字符" }
        require(weekdayMask in 1..127) { "请至少选择一个星期" }
        val calendar = CalendarEntity(UUID.randomUUID().toString(), trimmedName, today.toString(), weekdayMask)
        database.withTransaction {
            dao.insertCalendar(calendar)
            dao.upsertScheduleChange(ScheduleChangeEntity(calendar.id, today.toString(), weekdayMask))
        }
    }

    suspend fun editCalendar(calendarId: String, name: String, weekdayMask: Int, today: LocalDate) {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) { "日历名称应为 1 至 40 个字符" }
        require(weekdayMask in 1..127) { "请至少选择一个星期" }
        database.withTransaction {
            val calendar = dao.getCalendar(calendarId) ?: throw IllegalArgumentException("日历不存在")
            if (calendar.name != trimmedName || calendar.weekdayMask != weekdayMask) {
                check(dao.updateDetails(calendarId, trimmedName, weekdayMask) == 1)
                if (!calendar.isPaused && calendar.weekdayMask != weekdayMask) {
                    val effectiveDate = if (dao.hasCheckIn(calendarId, today.toString())) today.plusDays(1) else today
                    dao.upsertScheduleChange(ScheduleChangeEntity(calendarId, effectiveDate.toString(), weekdayMask))
                }
            }
        }
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
    }

    suspend fun checkIn(calendarId: String, today: LocalDate): Boolean = database.withTransaction {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        val calendar = dao.getCalendar(calendarId) ?: throw IllegalArgumentException("日历不存在")
        val mask = dao.getScheduleMaskOn(calendarId, today.toString()) ?: 0
        require(isScheduled(today, calendar.startLocalDate(), mask)) { "今天没有安排打卡" }
        dao.insertCheckIn(CheckInEntity(calendarId, today.toString())) != -1L
    }

    companion object {
        fun open(context: Context): CalendarRepository {
            val database = Room.databaseBuilder(
                context.applicationContext,
                CalendarDatabase::class.java,
                "personal-calendar-local.db",
            ).addMigrations(CalendarDatabase.MIGRATION_1_2).build()
            return CalendarRepository(database)
        }
    }
}
