package com.hang2515.personalcalendar.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT id, name, startDate, weekdayMask FROM calendars ORDER BY name COLLATE NOCASE, id")
    fun observeCalendars(): Flow<List<CalendarEntity>>

    @Query("SELECT id, name, startDate, weekdayMask FROM calendars WHERE id = :calendarId")
    suspend fun getCalendar(calendarId: String): CalendarEntity?

    @Insert
    suspend fun insertCalendar(calendar: CalendarEntity)

    @Query("SELECT calendarId, localDate FROM check_ins WHERE localDate = :date")
    fun observeCheckInsOn(date: String): Flow<List<CheckInEntity>>

    @Query("SELECT calendarId, localDate FROM check_ins WHERE calendarId = :calendarId AND localDate BETWEEN :from AND :through")
    fun observeCheckInsInMonth(calendarId: String, from: String, through: String): Flow<List<CheckInEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCheckIn(checkIn: CheckInEntity): Long
}
