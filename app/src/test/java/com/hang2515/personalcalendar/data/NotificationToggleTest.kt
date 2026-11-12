package com.hang2515.personalcalendar.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationToggleTest {
    @org.junit.Before fun enableOptionalAlarmMarkerForLegacyAlarmChecks() {
        RuntimeEnvironment.getApplication().getSharedPreferences("reminder-notification-settings", 0).edit()
            .putBoolean("alarm_clock", true).commit()
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }
    private val clock = Clock.fixed(Instant.parse("2026-10-19T00:01:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test fun disablingCancelsAlarmsAndPostedNotificationsAndKeepsCalendarSettings() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            val calendar = CalendarEntity("reminder", "阅读", "2026-10-01", 127, reminderEnabled = true)
            dao.insertCalendar(calendar)
            dao.upsertScheduleChange(ScheduleChangeEntity(calendar.id, calendar.startDate, 127))
            val scheduler = ReminderScheduler(context, database, clock)
            val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
            val notifications = Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
            scheduler.refresh(calendar.id)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("calendar_reminders", "旧版提醒", NotificationManager.IMPORTANCE_DEFAULT))
            manager.notify(calendar.id.hashCode(), Notification.Builder(context, "calendar_reminders")
                .setSmallIcon(android.R.drawable.ic_dialog_info).build())
            assertEquals(1, alarms.scheduledAlarms.size)
            assertEquals(1, notifications.allNotifications.size)

            scheduler.setNotificationsEnabled(false)
            assertEquals(0, alarms.scheduledAlarms.size)
            assertEquals(0, notifications.allNotifications.size)
            assertEquals(calendar, dao.getCalendar(calendar.id))
            assertEquals(127, dao.getScheduleMaskOn(calendar.id, "2026-10-19"))
            assertFalse(dao.hasCheckIn(calendar.id, "2026-10-19"))

            val reopened = ReminderScheduler(context, database, clock)
            assertFalse(reopened.isNotificationsEnabled())
            reopened.refreshAll()
            reopened.refresh(calendar.id)
            assertEquals(0, alarms.scheduledAlarms.size)
            assertEquals(0, notifications.allNotifications.size)

            reopened.setNotificationsEnabled(true)
            assertTrue(reopened.isNotificationsEnabled())
            assertEquals(1, alarms.scheduledAlarms.size)
            assertEquals(Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(), alarms.scheduledAlarms.single().triggerAtTime)
            assertEquals(0, notifications.allNotifications.size)
        } finally { database.close() }
    }

    @Test fun reenablingRespectsCalendarPermissionAndEachCalendarsReminderSetting() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("reminder-notification-settings", 0).edit().putBoolean("alarm_clock", false).commit()
        val provider = TestCalendarProvider.install()
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val dao = database.calendarDao()
            dao.insertCalendar(CalendarEntity("on", "开启", "2026-10-01", 127, reminderEnabled = true))
            dao.insertCalendar(CalendarEntity("off", "关闭", "2026-10-01", 127, reminderEnabled = false))
            dao.upsertScheduleChange(ScheduleChangeEntity("on", "2026-10-01", 127))
            dao.upsertScheduleChange(ScheduleChangeEntity("off", "2026-10-01", 127))
            val scheduler = ReminderScheduler(context, database, clock)
            val alarms = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
            scheduler.setNotificationsEnabled(false)
            scheduler.setNotificationsEnabled(true)
            assertEquals(0, alarms.scheduledAlarms.size)
            Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            scheduler.refreshAll()
            assertEquals(0, alarms.scheduledAlarms.size)
            provider.database.rawQuery("SELECT COUNT(_id) FROM events", null).use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            assertFalse(dao.getCalendar("off")!!.reminderEnabled)
        } finally { database.close() }
    }
}
