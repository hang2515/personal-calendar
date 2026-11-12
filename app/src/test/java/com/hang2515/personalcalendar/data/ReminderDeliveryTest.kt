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
import java.time.ZoneId
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderDeliveryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-09T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
    @org.junit.Before fun enableSystemCalendar() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        TestCalendarProvider.install()
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test fun alarmModeRegistersVisibleAlarmWhileOrdinaryNotificationDoesNot() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(AlarmManager::class.java)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("reading", "阅读", "2026-11-01", 127, reminderMinute = 660, reminderEnabled = true))
            dao.upsertScheduleChange(ScheduleChangeEntity("reading", "2026-11-01", 127))
            val scheduler = ReminderScheduler(context, database, clock)
            scheduler.refresh("reading")
            assertEquals(0, Shadows.shadowOf(manager).scheduledAlarms.size)
            assertNull(manager.nextAlarmClock)

            // 已保存的闹钟选择必须真正改变系统调度方式，而不只是改变界面文案。
            context.getSharedPreferences("reminder-notification-settings", 0).edit()
                .putBoolean("alarm_clock", true).commit()
            scheduler.refresh("reading")
            assertNotNull(manager.nextAlarmClock)
            assertEquals(Instant.parse("2026-11-09T03:00:00Z").toEpochMilli(), manager.nextAlarmClock.triggerTime)
            assertEquals("reading", Shadows.shadowOf(manager.nextAlarmClock.showIntent).savedIntent
                .getStringExtra(ReminderScheduler.EXTRA_CALENDAR_ID))
        } finally { database.close() }
    }

    @Test fun reminderModeIsPersistentAndMissingExactPermissionDoesNotPretendToRegisterAlarm() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(AlarmManager::class.java)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("reading", "阅读", "2026-11-01", 127, reminderEnabled = true))
            dao.upsertScheduleChange(ScheduleChangeEntity("reading", "2026-11-01", 127))
            val scheduler = ReminderScheduler(context, database, clock)
            scheduler.setAlarmClockEnabled(true)
            assertTrue(ReminderScheduler(context, database, clock).isAlarmClockEnabled())
            assertTrue(Shadows.shadowOf(manager).scheduledAlarms.isEmpty())
            scheduler.setAlarmClockEnabled(false)
            assertEquals(0, Shadows.shadowOf(manager).scheduledAlarms.size)
            assertNull(manager.nextAlarmClock)
        } finally { database.close() }
    }

    @Test fun scheduledTestUsesCurrentModeAndDoesNotChangeBusinessRecords() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            scheduler.setAlarmClockEnabled(true)
            scheduler.scheduleTestReminder()
            val state = scheduler.testReminderState()!!
            assertEquals(Instant.parse("2026-11-09T00:01:00Z").toEpochMilli(), state.expectedAtMillis)
            assertEquals(0L, state.postedAtMillis)
            assertNotNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock)
            assertEquals(state, ReminderScheduler(context, database, clock).testReminderState())
            assertTrue(database.calendarDao().exportCalendars().isEmpty())
            assertTrue(database.calendarDao().exportCheckIns().isEmpty())
        } finally { database.close() }
    }

    @Test fun onlyCurrentDueTestCanBeConfirmed() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            scheduler.scheduleTestReminder()
            val previous = scheduler.testReminderState()!!
            scheduler.scheduleTestReminder()
            val current = scheduler.testReminderState()!!
            assertNotEquals(previous.token, current.token)
            scheduler.confirmTestReminder(current.token)
            assertEquals(0L, scheduler.testReminderState()!!.postedAtMillis)
            val due = ReminderScheduler(context, database, Clock.offset(clock, Duration.ofMinutes(1)))
            due.confirmTestReminder(previous.token)
            assertEquals(0L, due.testReminderState()!!.postedAtMillis)
            due.confirmTestReminder(current.token)
            assertEquals(current.expectedAtMillis, due.testReminderState()!!.postedAtMillis)
            val notifications = Shadows.shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
            assertEquals(0, notifications.allNotifications.size)
            due.confirmTestReminder(current.token)
            assertEquals(0, notifications.allNotifications.size)
            due.setNotificationsEnabled(false)
            assertNull(due.testReminderState())
            assertTrue(notifications.allNotifications.isEmpty())
            assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
        } finally { database.close() }
    }

    @Test fun changingModeCancelsPendingTestAndDeniedCalendarPermissionCannotStartTest() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            scheduler.scheduleTestReminder()
            scheduler.setAlarmClockEnabled(true)
            assertNull(scheduler.testReminderState())
            assertTrue(Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
            Shadows.shadowOf(context).denyPermissions(Manifest.permission.WRITE_CALENDAR)
            val failure = runCatching { scheduler.scheduleTestReminder() }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertNull(scheduler.testReminderState())
        } finally { database.close() }
    }

    @Test fun alarmMarkerCanBeDisabledAfterCalendarPermissionIsRevoked() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val scheduler = ReminderScheduler(context, database, clock)
            scheduler.setAlarmClockEnabled(true)
            scheduler.scheduleTestReminder()
            val test = scheduler.testReminderState()
            val alarms = context.getSystemService(AlarmManager::class.java)
            assertNotNull(alarms.nextAlarmClock)
            Shadows.shadowOf(context).denyPermissions(Manifest.permission.WRITE_CALENDAR)
            scheduler.setAlarmClockEnabled(false)
            assertFalse(scheduler.isAlarmClockEnabled())
            assertNull(alarms.nextAlarmClock)
            assertEquals(test, scheduler.testReminderState())
        } finally { database.close() }
    }
}
