package com.hang2515.personalcalendar.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.hang2515.personalcalendar.MainActivity
import com.hang2515.personalcalendar.R
import com.hang2515.personalcalendar.data.CalendarDatabase
import com.hang2515.personalcalendar.domain.ScheduleChange
import com.hang2515.personalcalendar.domain.isScheduled
import com.hang2515.personalcalendar.domain.nextReminderAt
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReminderScheduler(
    context: Context,
    private val database: CalendarDatabase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
) {
    private val context = context.applicationContext
    private val dao = database.calendarDao()
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)
    private val notificationManager = this.context.getSystemService(NotificationManager::class.java)

    suspend fun refreshAll() {
        dao.getCalendarIds().forEach { calendarId ->
            try {
                refresh(calendarId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ReminderScheduler", "无法重新安排提醒：${error.javaClass.simpleName}")
            }
        }
    }

    suspend fun refresh(calendarId: String) = withContext(Dispatchers.IO) { refreshMutex.withLock {
        val alarmIntent = alarmIntent(calendarId)
        alarmManager.cancel(alarmIntent)
        val calendar = dao.getCalendar(calendarId) ?: return@withLock
        if (!calendar.reminderEnabled || !canPostNotifications()) return@withLock

        val now = ZonedDateTime.now(clock)
        val from = now.toLocalDate()
        val through = from.plusDays(7)
        val changes = dao.getChangesInRange(calendarId, from.toString(), through.toString()).map {
            ScheduleChange(LocalDate.parse(it.effectiveDate), it.weekdayMask)
        }
        val completed = if (dao.hasCheckIn(calendarId, from.toString())) setOf(from) else emptySet()
        val next = nextReminderAt(now, calendar.startLocalDate(), changes, calendar.reminderMinute, completed)
            ?: return@withLock
        val triggerAtMillis = next.toInstant().toEpochMilli()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmIntent)
        }
    } }

    fun dismissNotification(calendarId: String) {
        NotificationManagerCompat.from(context).cancel(calendarId.hashCode())
    }

    suspend fun deliver(calendarId: String) {
        val calendar = dao.getCalendar(calendarId) ?: return
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val mask = dao.getScheduleMaskOn(calendarId, today.toString()) ?: 0
        val reminderTime = today.atTime(calendar.reminderMinute / 60, calendar.reminderMinute % 60).atZone(now.zone)
        if (calendar.reminderEnabled && canPostNotifications() &&
            isScheduled(today, calendar.startLocalDate(), mask) &&
            !dao.hasCheckIn(calendarId, today.toString()) && !now.isBefore(reminderTime)
        ) {
            val openCalendar = Intent(context, MainActivity::class.java).apply {
                data = "personal-calendar://calendar/$calendarId".toUri()
                putExtra(EXTRA_CALENDAR_ID, calendarId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openIntent = PendingIntent.getActivity(
                context, 0, openCalendar, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("坚持提醒")
                .setContentText("今天记得完成“${calendar.name}”的打卡")
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(calendarId.hashCode(), notification)
            } catch (_: SecurityException) {
                // 权限可能在校验后被撤销；下次进入应用时会重新检查权限并安排提醒。
            }
        }
        refresh(calendarId)
    }

    fun canPostNotifications(): Boolean {
        createChannel()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notificationManager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun createChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "坚持提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "到点提醒完成坚持日历中的计划"
            },
        )
    }

    private fun alarmIntent(calendarId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = "personal-calendar://reminder/$calendarId".toUri()
            putExtra(EXTRA_CALENDAR_ID, calendarId)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val EXTRA_CALENDAR_ID = "calendar_id"
        private const val CHANNEL_ID = "calendar_reminders"
        private val refreshMutex = Mutex()

        fun open(context: Context): ReminderScheduler = ReminderScheduler(context, CalendarDatabase.open(context))
    }
}
