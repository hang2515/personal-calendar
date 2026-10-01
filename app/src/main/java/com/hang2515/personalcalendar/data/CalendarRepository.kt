package com.hang2515.personalcalendar.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.hang2515.personalcalendar.domain.isScheduled
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class CalendarRepository(private val database: CalendarDatabase) {
    private val dao = database.calendarDao()

    fun observeCalendars() = dao.observeCalendars()

    fun observeCheckInsOn(date: LocalDate) = dao.observeCheckInsOn(date.toString())

    fun observeCheckInsInMonth(calendarId: String, month: YearMonth) =
        dao.observeCheckInsInMonth(calendarId, month.atDay(1).toString(), month.atEndOfMonth().toString())

    suspend fun createCalendar(name: String, weekdayMask: Int, today: LocalDate) {
        require(today == LocalDate.now(ZoneId.of("Asia/Shanghai"))) { "日期已变化，请重试" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) { "日历名称应为 1 至 40 个字符" }
        require(weekdayMask in 1..127) { "请至少选择一个星期" }
        dao.insertCalendar(CalendarEntity(UUID.randomUUID().toString(), trimmedName, today.toString(), weekdayMask))
    }

    suspend fun checkIn(calendarId: String, today: LocalDate): Boolean = database.withTransaction {
        require(today == LocalDate.now(ZoneId.of("Asia/Shanghai"))) { "日期已变化，请重试" }
        val calendar = dao.getCalendar(calendarId) ?: throw IllegalArgumentException("日历不存在")
        require(isScheduled(today, calendar.startLocalDate(), calendar.weekdayMask)) { "今天没有安排打卡" }
        dao.insertCheckIn(CheckInEntity(calendarId, today.toString())) != -1L
    }

    companion object {
        fun open(context: Context): CalendarRepository {
            val database = Room.databaseBuilder(
                context.applicationContext,
                CalendarDatabase::class.java,
                "personal-calendar-local.db",
            ).build()
            return CalendarRepository(database)
        }
    }
}
