package com.hang2515.personalcalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT id, name, startDate, weekdayMask, isPaused, reminderMinute, reminderEnabled, iconKey, colorKey FROM calendars ORDER BY id LIMIT 1001")
    suspend fun exportCalendars(): List<CalendarEntity>

    @Query("SELECT calendarId, effectiveDate, weekdayMask FROM schedule_changes ORDER BY calendarId, effectiveDate LIMIT 10001")
    suspend fun exportChanges(): List<ScheduleChangeEntity>

    @Query("SELECT calendarId, localDate FROM check_ins ORDER BY calendarId, localDate LIMIT 10001")
    suspend fun exportCheckIns(): List<CheckInEntity>

    @Query("DELETE FROM calendars WHERE id IN (:ids)")
    suspend fun deleteCalendars(ids: List<String>): Int

    @Insert
    suspend fun insertCalendars(calendars: List<CalendarEntity>)

    @Insert
    suspend fun insertChanges(changes: List<ScheduleChangeEntity>)

    @Insert
    suspend fun insertCheckIns(checkIns: List<CheckInEntity>)

    @Query("SELECT id, name, startDate, weekdayMask, isPaused, reminderMinute, reminderEnabled, iconKey, colorKey FROM calendars ORDER BY name COLLATE NOCASE, id")
    fun observeCalendars(): Flow<List<CalendarEntity>>

    @Query("SELECT id, name, startDate, weekdayMask, isPaused, reminderMinute, reminderEnabled, iconKey, colorKey FROM calendars WHERE id = :calendarId")
    suspend fun getCalendar(calendarId: String): CalendarEntity?

    @Query("SELECT id FROM calendars")
    suspend fun getCalendarIds(): List<String>

    @Insert
    suspend fun insertCalendar(calendar: CalendarEntity)

    @Query("UPDATE calendars SET name = :name, weekdayMask = :weekdayMask, reminderMinute = :reminderMinute, reminderEnabled = :reminderEnabled, iconKey = :iconKey, colorKey = :colorKey WHERE id = :calendarId")
    suspend fun updateDetails(
        calendarId: String,
        name: String,
        weekdayMask: Int,
        reminderMinute: Int,
        reminderEnabled: Boolean,
        iconKey: String,
        colorKey: String,
    ): Int

    @Query("UPDATE calendars SET isPaused = :isPaused WHERE id = :calendarId")
    suspend fun updatePaused(calendarId: String, isPaused: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScheduleChange(change: ScheduleChangeEntity)

    @Query("SELECT weekdayMask FROM schedule_changes WHERE calendarId = :calendarId AND effectiveDate <= :date ORDER BY effectiveDate DESC LIMIT 1")
    suspend fun getScheduleMaskOn(calendarId: String, date: String): Int?

    @Query("SELECT weekdayMask FROM schedule_changes WHERE calendarId = :calendarId AND effectiveDate = :date")
    suspend fun getScheduleChangeOn(calendarId: String, date: String): Int?

    @Query("DELETE FROM schedule_changes WHERE calendarId = :calendarId AND effectiveDate = :date")
    suspend fun deleteScheduleChangeOn(calendarId: String, date: String): Int

    @Query("SELECT calendarId, effectiveDate, weekdayMask FROM schedule_changes WHERE effectiveDate = (SELECT MAX(other.effectiveDate) FROM schedule_changes AS other WHERE other.calendarId = schedule_changes.calendarId AND other.effectiveDate <= :date)")
    fun observeSchedulesOn(date: String): Flow<List<ScheduleChangeEntity>>

    @Query("SELECT calendarId, effectiveDate, weekdayMask FROM schedule_changes WHERE calendarId = :calendarId AND (effectiveDate BETWEEN :from AND :through OR effectiveDate = (SELECT MAX(previous.effectiveDate) FROM schedule_changes AS previous WHERE previous.calendarId = :calendarId AND previous.effectiveDate < :from)) ORDER BY effectiveDate")
    fun observeChangesInMonth(calendarId: String, from: String, through: String): Flow<List<ScheduleChangeEntity>>

    @Query("SELECT calendarId, effectiveDate, weekdayMask FROM schedule_changes WHERE calendarId = :calendarId AND (effectiveDate BETWEEN :from AND :through OR effectiveDate = (SELECT MAX(previous.effectiveDate) FROM schedule_changes AS previous WHERE previous.calendarId = :calendarId AND previous.effectiveDate < :from)) ORDER BY effectiveDate")
    suspend fun getChangesInRange(calendarId: String, from: String, through: String): List<ScheduleChangeEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM check_ins WHERE calendarId = :calendarId AND localDate = :date)")
    suspend fun hasCheckIn(calendarId: String, date: String): Boolean

    @Query("SELECT calendarId, localDate FROM check_ins WHERE localDate = :date")
    fun observeCheckInsOn(date: String): Flow<List<CheckInEntity>>

    @Query("SELECT calendarId, localDate FROM check_ins WHERE calendarId = :calendarId AND localDate BETWEEN :from AND :through")
    fun observeCheckInsInMonth(calendarId: String, from: String, through: String): Flow<List<CheckInEntity>>

    @Query("SELECT calendarId, localDate FROM check_ins WHERE localDate BETWEEN :from AND :through")
    fun observeAllCheckInsInMonth(from: String, through: String): Flow<List<CheckInEntity>>

    // 一次读取月内变更及各日历月初生效规则，列表统计不逐个日历查询。
    @Query("""
        WITH previous AS (
            SELECT calendarId, MAX(effectiveDate) AS effectiveDate
            FROM schedule_changes WHERE effectiveDate < :from GROUP BY calendarId
        )
        SELECT changes.calendarId, changes.effectiveDate, changes.weekdayMask
        FROM schedule_changes AS changes INNER JOIN previous
        ON changes.calendarId = previous.calendarId AND changes.effectiveDate = previous.effectiveDate
        UNION ALL
        SELECT calendarId, effectiveDate, weekdayMask FROM schedule_changes
        WHERE effectiveDate BETWEEN :from AND :through
        ORDER BY calendarId, effectiveDate
    """)
    fun observeAllChangesInMonth(from: String, through: String): Flow<List<ScheduleChangeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIn(checkIn: CheckInEntity): Long

    @Query("DELETE FROM check_ins WHERE calendarId = :calendarId AND localDate = :date")
    suspend fun deleteCheckIn(calendarId: String, date: String): Int

    @Query("DELETE FROM calendars WHERE id = :calendarId")
    suspend fun deleteCalendar(calendarId: String): Int
}
