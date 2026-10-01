package com.hang2515.personalcalendar.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CalendarEntity::class, CheckInEntity::class], version = 1, exportSchema = true)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
}
