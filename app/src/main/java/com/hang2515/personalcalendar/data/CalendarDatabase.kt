package com.hang2515.personalcalendar.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CalendarEntity::class, CheckInEntity::class, ScheduleChangeEntity::class], version = 2, exportSchema = true)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendars ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS schedule_changes (" +
                        "calendarId TEXT NOT NULL, effectiveDate TEXT NOT NULL, weekdayMask INTEGER NOT NULL, " +
                        "PRIMARY KEY(calendarId, effectiveDate), " +
                        "FOREIGN KEY(calendarId) REFERENCES calendars(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO schedule_changes(calendarId, effectiveDate, weekdayMask) " +
                        "SELECT id, startDate, weekdayMask FROM calendars",
                )
            }
        }
    }
}
