package com.hang2515.personalcalendar.data

import android.Manifest
import android.app.AlarmManager
import androidx.room.Room
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemCalendarIntegrationTest {
    @Test fun refreshingACalendarWithoutRemindersKeepsOtherCalendarsAuthorizationError() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            val repository = CalendarRepository(database, clock, scheduler)
            repository.createCalendar("需要提醒", 127, today, 660, true)
            assertNotNull(scheduler.calendarSyncError())
            repository.createCalendar("无需提醒", 127, today, 660, false)
            assertNotNull(scheduler.calendarSyncError())
        } finally { database.close() }
    }
    private val clock = Clock.fixed(Instant.parse("2026-11-09T02:00:00Z"), ZoneId.of("Asia/Shanghai"))
    private val today = LocalDate.of(2026, 11, 9)
    @Test fun ordinaryReminderIsDelegatedWithoutOwnNotificationPermissionAndTracksBusinessChanges() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val provider = TestCalendarProvider.install()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            val repository = CalendarRepository(database, clock, scheduler)
            val id = repository.createCalendar("阅读", 127, today, 660, true).let { database.calendarDao().getCalendarIds().single() }
            fun eventCount() = provider.database.rawQuery("SELECT COUNT(_id) FROM events", null).use { it.moveToFirst(); it.getInt(0) }
            fun startTime() = provider.database.rawQuery("SELECT dtstart FROM events", null).use { it.moveToFirst(); it.getLong(0) }
            assertEquals(1, eventCount())
            assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
            assertEquals(Instant.parse("2026-11-09T03:00:00Z").toEpochMilli(), startTime())
            repository.checkIn(id, today)
            assertEquals(Instant.parse("2026-11-10T03:00:00Z").toEpochMilli(), startTime())
            repository.setPaused(id, true, today)
            assertEquals(0, eventCount())
            repository.undoCheckIn(id, today)
            repository.setPaused(id, false, today)
            assertEquals(1, eventCount())
            scheduler.setNotificationsEnabled(false)
            assertEquals(0, eventCount())
            scheduler.setNotificationsEnabled(true)
            assertEquals(1, eventCount())
            repository.deleteCalendar(id)
            assertEquals(0, eventCount())
        } finally { database.close() }
    }

    @Test fun revokedCalendarPermissionDoesNotFalselyTurnOffExistingDelegatedReminders() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val provider = TestCalendarProvider.install()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            CalendarRepository(database, clock, scheduler).createCalendar("阅读", 127, today, 660, true)
            Shadows.shadowOf(context).denyPermissions(Manifest.permission.WRITE_CALENDAR)
            assertTrue(runCatching { scheduler.setNotificationsEnabled(false) }.isFailure)
            assertTrue(scheduler.isNotificationsEnabled())
            provider.database.rawQuery("SELECT COUNT(_id) FROM events", null).use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        } finally { database.close() }
    }

    @Test fun projectionErrorAndRevokedPermissionNeverEnableDuplicateLocalDelivery() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.POST_NOTIFICATIONS)
        TestCalendarProvider.install()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            val repository = CalendarRepository(database, clock, scheduler)
            repository.createCalendar("阅读", 127, today, 660, true)
            val id = database.calendarDao().getCalendarIds().single()
            context.getSharedPreferences("reminder-notification-settings", 0).edit().putString("calendar_sync_error", "其他日历写入失败").commit()
            val due = ReminderScheduler(context, database, Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneId.of("Asia/Shanghai")))
            due.refresh(id)
            assertTrue(Shadows.shadowOf(context.getSystemService(android.app.NotificationManager::class.java)).allNotifications.isEmpty())
            Shadows.shadowOf(context).denyPermissions(Manifest.permission.WRITE_CALENDAR)
            due.refresh(id)
            assertTrue(Shadows.shadowOf(context.getSystemService(android.app.NotificationManager::class.java)).allNotifications.isEmpty())
        } finally { database.close() }
    }
}
