package com.hang2515.personalcalendar.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarDatabaseTest {
    @Test
    fun versionThreeMigrationAddsSavedAppearanceDefaults() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "calendar-v3-migration-test.db"
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE calendars(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, startDate TEXT NOT NULL, weekdayMask INTEGER NOT NULL, isPaused INTEGER NOT NULL DEFAULT 0, reminderMinute INTEGER NOT NULL DEFAULT 480, reminderEnabled INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE check_ins(calendarId TEXT NOT NULL, localDate TEXT NOT NULL, PRIMARY KEY(calendarId, localDate), FOREIGN KEY(calendarId) REFERENCES calendars(id) ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_check_ins_localDate ON check_ins(localDate)")
            old.execSQL("CREATE TABLE schedule_changes(calendarId TEXT NOT NULL, effectiveDate TEXT NOT NULL, weekdayMask INTEGER NOT NULL, PRIMARY KEY(calendarId, effectiveDate), FOREIGN KEY(calendarId) REFERENCES calendars(id) ON DELETE CASCADE)")
            old.execSQL("INSERT INTO calendars VALUES ('three', '运动', '2026-10-01', 127, 0, 1140, 1)")
            old.execSQL("INSERT INTO schedule_changes VALUES ('three', '2026-10-01', 127)")
            old.version = 3
        }
        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, databaseName)
            .addMigrations(CalendarDatabase.MIGRATION_1_2, CalendarDatabase.MIGRATION_2_3, CalendarDatabase.MIGRATION_3_4)
            .build()
        try {
            val calendar = database.calendarDao().getCalendar("three")!!
            assertEquals("leaf", calendar.iconKey)
            assertEquals("sage", calendar.colorKey)
            assertEquals(1140, calendar.reminderMinute)
            assertTrue(calendar.reminderEnabled)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun dueReminderPostsNotificationOnlyBeforeCheckIn() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        val today = LocalDate.of(2026, 10, 1)
        val clock = Clock.fixed(Instant.parse("2026-10-01T00:01:00Z"), ZoneId.of("Asia/Shanghai"))
        val scheduler = ReminderScheduler(context, database, clock)
        val notifications = Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
        try {
            database.calendarDao().insertCalendar(CalendarEntity("one", "阅读", today.toString(), 127, reminderEnabled = true))
            database.calendarDao().upsertScheduleChange(ScheduleChangeEntity("one", today.toString(), 127))
            scheduler.deliver("one")
            assertEquals(1, notifications.allNotifications.size)
            assertEquals(
                "one",
                Shadows.shadowOf(notifications.allNotifications.single().contentIntent)
                    .savedIntent.getStringExtra(ReminderScheduler.EXTRA_CALENDAR_ID),
            )

            database.calendarDao().insertCheckIn(CheckInEntity("one", today.toString()))
            context.getSystemService(NotificationManager::class.java).cancelAll()
            scheduler.deliver("one")
            assertEquals(0, notifications.allNotifications.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun reminderIsRescheduledAfterCheckInPauseAndEdit() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        val today = LocalDate.of(2026, 10, 1)
        val clock = Clock.fixed(Instant.parse("2026-09-30T23:00:00Z"), ZoneId.of("Asia/Shanghai"))
        val scheduler = ReminderScheduler(context, database, clock)
        val repository = CalendarRepository(database, clock, scheduler)
        val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        try {
            repository.createCalendar("阅读", 127, today, 480, true)
            val calendarId = database.calendarDao().observeCalendars().first().single().id
            assertEquals(1, alarms.scheduledAlarms.size)

            repository.checkIn(calendarId, today)
            assertEquals(1, alarms.scheduledAlarms.size)

            repository.setPaused(calendarId, true, today)
            assertEquals(0, alarms.scheduledAlarms.size)

            repository.setPaused(calendarId, false, today)
            assertEquals(1, alarms.scheduledAlarms.size)

            repository.editCalendar(calendarId, "阅读", 127, today, 480, false, "leaf", "sage")
            assertEquals(0, alarms.scheduledAlarms.size)
        } finally {
            database.close()
        }
    }

    @Test
    fun undoTodayCheckInRestoresPendingReminderAndIsIdempotent() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        val today = LocalDate.of(2026, 10, 1)
        val clock = Clock.fixed(Instant.parse("2026-09-30T23:30:00Z"), ZoneId.of("Asia/Shanghai"))
        val scheduler = ReminderScheduler(context, database, clock)
        val repository = CalendarRepository(database, clock, scheduler)
        val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        try {
            repository.createCalendar("阅读", 127, today, 540, true)
            val id = database.calendarDao().observeCalendars().first().single().id
            assertTrue(repository.checkIn(id, today))
            assertTrue(repository.undoCheckIn(id, today))
            assertFalse(repository.undoCheckIn(id, today))
            assertFalse(database.calendarDao().hasCheckIn(id, today.toString()))
            assertEquals(1, alarms.scheduledAlarms.size)
            assertEquals(today.atTime(9, 0).atZone(clock.zone).toInstant().toEpochMilli(), alarms.scheduledAlarms.single().triggerAtMs)
        } finally {
            database.close()
        }
    }

    @Test
    fun undoAfterPausingTodayMovesDeferredRuleToToday() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        val today = LocalDate.of(2026, 10, 1)
        val clock = Clock.fixed(Instant.parse("2026-10-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"))
        val repository = CalendarRepository(database, clock)
        try {
            repository.createCalendar("阅读", 127, today)
            val id = database.calendarDao().observeCalendars().first().single().id
            assertTrue(repository.checkIn(id, today))
            repository.setPaused(id, true, today)
            assertEquals(127, database.calendarDao().getScheduleMaskOn(id, today.toString()))

            assertTrue(repository.undoCheckIn(id, today))
            assertEquals(0, database.calendarDao().getScheduleMaskOn(id, today.toString()))
            assertEquals(0, database.calendarDao().getScheduleMaskOn(id, today.plusDays(1).toString()))
            assertEquals(null, database.calendarDao().getScheduleChangeOn(id, today.plusDays(1).toString()))
            assertFalse(database.calendarDao().hasCheckIn(id, today.toString()))
            repository.setPaused(id, false, today)
            assertEquals(127, database.calendarDao().getScheduleMaskOn(id, today.plusDays(1).toString()))
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingCalendarRemovesHistoryAndReminder() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        val today = LocalDate.of(2026, 10, 1)
        val clock = Clock.fixed(Instant.parse("2026-09-30T23:00:00Z"), ZoneId.of("Asia/Shanghai"))
        val repository = CalendarRepository(database, clock, ReminderScheduler(context, database, clock))
        val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        try {
            repository.createCalendar("误建日历", 127, today, 540, true)
            val id = database.calendarDao().observeCalendars().first().single().id
            repository.checkIn(id, today)
            assertTrue(repository.deleteCalendar(id))
            assertFalse(repository.deleteCalendar(id))
            assertEquals(null, database.calendarDao().getCalendar(id))
            assertFalse(database.calendarDao().hasCheckIn(id, today.toString()))
            assertEquals(null, database.calendarDao().getScheduleMaskOn(id, today.toString()))
            assertEquals(0, alarms.scheduledAlarms.size)
        } finally {
            database.close()
        }
    }

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
            repository.editCalendar("one", "阅读 20 分钟", 1, today, 480, false, "book", "blue")
            assertEquals("阅读 20 分钟", dao.getCalendar("one")?.name)
            assertEquals("book", dao.getCalendar("one")?.iconKey)
            assertEquals("blue", dao.getCalendar("one")?.colorKey)
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
            .addMigrations(CalendarDatabase.MIGRATION_1_2, CalendarDatabase.MIGRATION_2_3, CalendarDatabase.MIGRATION_3_4)
            .build()
        try {
            val dao = database.calendarDao()
            assertNotNull(dao.getCalendar("one"))
            assertFalse(dao.getCalendar("one")!!.isPaused)
            assertFalse(dao.getCalendar("one")!!.reminderEnabled)
            assertEquals(480, dao.getCalendar("one")!!.reminderMinute)
            assertEquals(127, dao.getScheduleMaskOn("one", "2026-10-01"))
            assertEquals(1, dao.observeCheckInsOn("2026-10-01").first().size)
            assertEquals(-1L, dao.insertCheckIn(CheckInEntity("one", "2026-10-01")))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun versionTwoMigrationKeepsExistingCalendarAndAddsDisabledReminder() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val databaseName = "calendar-v2-migration-test.db"
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE calendars(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, startDate TEXT NOT NULL, weekdayMask INTEGER NOT NULL, isPaused INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE check_ins(calendarId TEXT NOT NULL, localDate TEXT NOT NULL, PRIMARY KEY(calendarId, localDate), FOREIGN KEY(calendarId) REFERENCES calendars(id) ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_check_ins_localDate ON check_ins(localDate)")
            old.execSQL("CREATE TABLE schedule_changes(calendarId TEXT NOT NULL, effectiveDate TEXT NOT NULL, weekdayMask INTEGER NOT NULL, PRIMARY KEY(calendarId, effectiveDate), FOREIGN KEY(calendarId) REFERENCES calendars(id) ON DELETE CASCADE)")
            old.execSQL("INSERT INTO calendars VALUES ('two', '运动', '2026-10-01', 127, 0)")
            old.execSQL("INSERT INTO schedule_changes VALUES ('two', '2026-10-01', 127)")
            old.version = 2
        }
        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, databaseName)
            .addMigrations(CalendarDatabase.MIGRATION_1_2, CalendarDatabase.MIGRATION_2_3, CalendarDatabase.MIGRATION_3_4)
            .build()
        try {
            val calendar = database.calendarDao().getCalendar("two")!!
            assertEquals("运动", calendar.name)
            assertEquals(480, calendar.reminderMinute)
            assertFalse(calendar.reminderEnabled)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
