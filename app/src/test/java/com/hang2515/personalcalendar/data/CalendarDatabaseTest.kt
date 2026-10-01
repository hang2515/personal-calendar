package com.hang2515.personalcalendar.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarDatabaseTest {
    @Test
    fun completedDayStaysPlannedWhenPausedAndEditedLaterThatDay() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        val today = LocalDate.of(2026, 10, 1)
        val tomorrow = today.plusDays(1)
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("one", "阅读", today.toString(), 127))
            dao.upsertScheduleChange(ScheduleChangeEntity("one", today.toString(), 127))
            val clock = Clock.fixed(Instant.parse("2026-10-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
            val repository = CalendarRepository(database, clock)

            assertTrue(repository.checkIn("one", today))
            assertFalse(repository.checkIn("one", today))
            repository.setPaused("one", true, today)
            assertEquals(127, dao.getScheduleMaskOn("one", today.toString()))
            assertEquals(0, dao.getScheduleMaskOn("one", tomorrow.toString()))

            repository.setPaused("one", false, today)
            repository.editCalendar("one", "阅读 20 分钟", 1, today)
            assertEquals("阅读 20 分钟", dao.getCalendar("one")?.name)
            assertEquals(127, dao.getScheduleMaskOn("one", today.toString()))
            assertEquals(1, dao.getScheduleMaskOn("one", tomorrow.toString()))
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationPreservesCalendarsAndCheckIns() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "calendar-migration-test.db"
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE calendars(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, startDate TEXT NOT NULL, weekdayMask INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE check_ins(calendarId TEXT NOT NULL, localDate TEXT NOT NULL, PRIMARY KEY(calendarId, localDate), FOREIGN KEY(calendarId) REFERENCES calendars(id) ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_check_ins_localDate ON check_ins(localDate)")
            old.execSQL("INSERT INTO calendars VALUES ('one', '阅读', '2026-10-01', 127)")
            old.execSQL("INSERT INTO check_ins VALUES ('one', '2026-10-01')")
            old.version = 1
        }
        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, databaseName)
            .addMigrations(CalendarDatabase.MIGRATION_1_2)
            .build()
        try {
            val dao = database.calendarDao()
            assertNotNull(dao.getCalendar("one"))
            assertFalse(dao.getCalendar("one")!!.isPaused)
            assertEquals(127, dao.getScheduleMaskOn("one", "2026-10-01"))
            assertEquals(1, dao.observeCheckInsOn("2026-10-01").first().size)
            assertEquals(-1L, dao.insertCheckIn(CheckInEntity("one", "2026-10-01")))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
