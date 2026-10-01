package com.hang2515.personalcalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIn(checkIn: CheckInEntity): Long
}
