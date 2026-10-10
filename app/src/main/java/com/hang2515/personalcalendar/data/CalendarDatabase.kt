package com.hang2515.personalcalendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [CalendarEntity::class, CheckInEntity::class, ScheduleChangeEntity::class, LocalState::class], version = 5, exportSchema = true)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun localStateDao(): LocalStateDao

    companion object {
        @Volatile private var instance: CalendarDatabase? = null

        fun open(context: Context): CalendarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CalendarDatabase::class.java,
                "personal-calendar-local.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendars ADD COLUMN reminderMinute INTEGER NOT NULL DEFAULT 480")
                db.execSQL("ALTER TABLE calendars ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendars ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'leaf'")
                db.execSQL("ALTER TABLE calendars ADD COLUMN colorKey TEXT NOT NULL DEFAULT 'sage'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS local_state (id INTEGER NOT NULL, datasetId TEXT NOT NULL, localRevision INTEGER NOT NULL, boundAccountSub TEXT, backupEnabled INTEGER NOT NULL, lastBackedUpRevision INTEGER, lastBackupAt TEXT, cloudETag TEXT, PRIMARY KEY(id))")
                db.execSQL("INSERT INTO local_state(id, datasetId, localRevision, backupEnabled) VALUES (1, ?, 0, 0)", arrayOf(java.util.UUID.randomUUID().toString()))
            }
        }
    }
}
