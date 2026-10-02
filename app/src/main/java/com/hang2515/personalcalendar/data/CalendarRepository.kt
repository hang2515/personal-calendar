package com.hang2515.personalcalendar.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.hang2515.personalcalendar.domain.isScheduled
import com.hang2515.personalcalendar.domain.CalendarAppearance
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import com.hang2515.personalcalendar.domain.ScheduleChange
import com.hang2515.personalcalendar.domain.monthlyProgress
import com.hang2515.personalcalendar.domain.scheduleMaskOn

class CalendarRepository(
    private val database: CalendarDatabase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
    private val reminderScheduler: ReminderScheduler? = null,
    private val onChanged: (() -> Unit)? = null,
) {
    private val dao = database.calendarDao()

    fun observeCalendars() = dao.observeCalendars()

    fun observeSchedulesOn(date: LocalDate) = dao.observeSchedulesOn(date.toString())

    fun observeCheckInsOn(date: LocalDate) = dao.observeCheckInsOn(date.toString())

    fun observeCheckInsInMonth(calendarId: String, month: YearMonth) =
        dao.observeCheckInsInMonth(calendarId, month.atDay(1).toString(), month.atEndOfMonth().toString())

    fun observeChangesInMonth(calendarId: String, month: YearMonth) =
        dao.observeChangesInMonth(calendarId, month.atDay(1).toString(), month.atEndOfMonth().toString())

    fun observeMonthlyProgress(today: LocalDate) = combine(
        dao.observeCalendars(),
        dao.observeAllCheckInsInMonth(today.withDayOfMonth(1).toString(), today.toString()),
        dao.observeAllChangesInMonth(today.withDayOfMonth(1).toString(), today.toString()),
    ) { calendars, checkIns, changes ->
        val datesByCalendar = checkIns.groupBy { it.calendarId }
        val changesByCalendar = changes.groupBy { it.calendarId }
        calendars.associate { calendar ->
            val dates = datesByCalendar[calendar.id].orEmpty().map { LocalDate.parse(it.localDate) }.toSet()
            val rules = changesByCalendar[calendar.id].orEmpty().map {
                ScheduleChange(LocalDate.parse(it.effectiveDate), it.weekdayMask)
            }
            calendar.id to monthlyProgress(YearMonth.from(today), today, dates) { date ->
                isScheduled(date, calendar.startLocalDate(), scheduleMaskOn(date, rules))
            }
        }
    }

    suspend fun createCalendar(
        name: String,
        weekdayMask: Int,
        today: LocalDate,
        reminderMinute: Int = 480,
        reminderEnabled: Boolean = false,
        iconKey: String = "leaf",
        colorKey: String = "sage",
    ) {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) { "日历名称应为 1 至 40 个字符" }
        require(weekdayMask in 1..127) { "请至少选择一个星期" }
        require(reminderMinute in 0..1439) { "提醒时间无效" }
        require(iconKey in CalendarAppearance.iconKeys && colorKey in CalendarAppearance.colorKeys) { "日历样式无效" }
        val calendar = CalendarEntity(
            UUID.randomUUID().toString(), trimmedName, today.toString(), weekdayMask,
            reminderMinute = reminderMinute, reminderEnabled = reminderEnabled,
            iconKey = iconKey, colorKey = colorKey,
        )
        database.withTransaction {
            dao.insertCalendar(calendar)
            dao.upsertScheduleChange(ScheduleChangeEntity(calendar.id, today.toString(), weekdayMask))
            database.localStateDao().recordChange()
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
        iconKey: String,
        colorKey: String,
    ) {
        require(today == LocalDate.now(clock)) { "日期已变化，请重试" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty() && trimmedName.length <= 40) { "日历名称应为 1 至 40 个字符" }
        require(weekdayMask in 1..127) { "请至少选择一个星期" }
        require(reminderMinute in 0..1439) { "提醒时间无效" }
        require(iconKey in CalendarAppearance.iconKeys && colorKey in CalendarAppearance.colorKeys) { "日历样式无效" }
        database.withTransaction {
            val calendar = dao.getCalendar(calendarId) ?: throw IllegalArgumentException("日历不存在")
            if (calendar.name != trimmedName || calendar.weekdayMask != weekdayMask ||
                calendar.reminderMinute != reminderMinute || calendar.reminderEnabled != reminderEnabled ||
                calendar.iconKey != iconKey || calendar.colorKey != colorKey
            ) {
                check(dao.updateDetails(calendarId, trimmedName, weekdayMask, reminderMinute, reminderEnabled, iconKey, colorKey) == 1)
                database.localStateDao().recordChange()
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
                database.localStateDao().recordChange()
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
            (dao.insertCheckIn(CheckInEntity(calendarId, today.toString())) != -1L).also {
                if (it) database.localStateDao().recordChange()
            }
        }
        if (inserted) {
            reminderScheduler?.dismissNotification(calendarId)
            refreshReminder(calendarId)
        }
        return inserted
    }

    suspend fun undoCheckIn(calendarId: String, today: LocalDate): Boolean {
        val deleted = database.withTransaction {
            require(today == LocalDate.now(clock)) { "只能撤销今天的打卡" }
            val removed = dao.deleteCheckIn(calendarId, today.toString()) == 1
            if (removed) {
                database.localStateDao().recordChange()
                val tomorrow = today.plusDays(1).toString()
                val deferredMask = dao.getScheduleChangeOn(calendarId, tomorrow)
                if (deferredMask != null) {
                    // 打卡后修改的规则曾顺延到明天；撤销打卡后从今天生效。
                    dao.upsertScheduleChange(ScheduleChangeEntity(calendarId, today.toString(), deferredMask))
                    check(dao.deleteScheduleChangeOn(calendarId, tomorrow) == 1)
                }
            }
            removed
        }
        if (deleted) refreshReminder(calendarId)
        return deleted
    }

    suspend fun deleteCalendar(calendarId: String): Boolean {
        val deleted = database.withTransaction {
            (dao.deleteCalendar(calendarId) == 1).also { if (it) database.localStateDao().recordChange() }
        }
        if (deleted) {
            reminderScheduler?.dismissNotification(calendarId)
            refreshReminder(calendarId)
        }
        return deleted
    }

    private suspend fun refreshReminder(calendarId: String) {
        onChanged?.invoke()
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
            return CalendarRepository(database, reminderScheduler = ReminderScheduler(context, database),
                onChanged = { com.hang2515.personalcalendar.backup.BackupWork.schedule(context) })
        }
    }
}
