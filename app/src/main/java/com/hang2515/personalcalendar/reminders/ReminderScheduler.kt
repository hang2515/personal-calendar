package com.hang2515.personalcalendar.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.hang2515.personalcalendar.MainActivity
import com.hang2515.personalcalendar.data.CalendarDatabase
import com.hang2515.personalcalendar.domain.ScheduleChange
import com.hang2515.personalcalendar.domain.delegatedReminderPlans
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class ReminderTestState(val token: String, val expectedAtMillis: Long, val postedAtMillis: Long, val delegated: Boolean = false)

class ReminderScheduler(
    context: Context,
    private val database: CalendarDatabase,
    private val clock: Clock = Clock.system(ZoneId.of("Asia/Shanghai")),
) {
    private val context = context.applicationContext
    private val dao = database.calendarDao()
    private val alarmManager = this.context.getSystemService(AlarmManager::class.java)
    private val preferences = this.context.getSharedPreferences("reminder-notification-settings", Context.MODE_PRIVATE)
    private val systemCalendar = SystemCalendarReminders(this.context)

    fun isNotificationsEnabled(): Boolean = preferences.getBoolean("enabled", true)

    fun isAlarmClockEnabled(): Boolean = preferences.getBoolean("alarm_clock", false)

    suspend fun setAlarmClockEnabled(enabled: Boolean) = withContext(NonCancellable + Dispatchers.IO) {
        refreshMutex.withLock {
            if (isAlarmClockEnabled() != enabled) {
                if (systemCalendar.hasPermission()) cancelTestReminderLocked()
                else alarmManager.cancel(testAlarmIntent())
            }
            check(preferences.edit().putBoolean("alarm_clock", enabled).commit()) { "提醒方式未保存，请重试" }
        }
        refreshAll()
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) = withContext(NonCancellable + Dispatchers.IO) {
        // 保存后必须完成取消或重排；切页不能让关闭操作只执行一半。
        refreshMutex.withLock {
            if (!enabled) systemCalendar.clearManaged()
            check(preferences.edit().putBoolean("enabled", enabled).commit()) { "通知设置未保存，请重试" }
            if (!enabled) {
                cancelTestReminderLocked()
                recordCalendarError(null)
                NotificationManagerCompat.from(context).cancelAll()
                dao.getCalendarIds().forEach { alarmManager.cancel(alarmIntent(it)) }
            }
        }
        if (enabled) refreshAll()
    }

    suspend fun refreshAll() = withContext(NonCancellable + Dispatchers.IO) { refreshMutex.withLock {
        try {
            refreshLocked(null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordCalendarError("系统日历代管尚未更新，请检查日历授权后重试。")
            Log.e("ReminderScheduler", "无法重新安排提醒：${error.javaClass.simpleName}")
        }
    } }

    suspend fun refreshAfterRestore(previousIds: List<String>) = withContext(NonCancellable + Dispatchers.IO) {
        refreshMutex.withLock {
            previousIds.forEach { dismissNotification(it); alarmManager.cancel(alarmIntent(it)) }
        }
        refreshAll()
    }

    suspend fun refresh(calendarId: String) = withContext(NonCancellable + Dispatchers.IO) { refreshMutex.withLock {
        try {
            if (calendarSyncError() != null) {
                // 单个日历刷新成功不能清除其他日历的代管错误。
                alarmManager.cancel(alarmIntent(calendarId))
                refreshLocked(null)
            } else {
                refreshLocked(calendarId)
            }
        }
        catch (error: Exception) {
            recordCalendarError("系统日历代管尚未更新，请检查日历授权后重试。")
            throw error
        }
    } }

    private suspend fun refreshLocked(calendarId: String?) {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate().toString()
        val snapshot = database.withTransaction {
            Triple(if (calendarId == null) dao.getReminderCalendars() else listOfNotNull(dao.getCalendar(calendarId)),
                dao.getReminderChanges(today, calendarId), dao.getReminderCheckIns(today).map { it.calendarId }.toSet())
        }
        val changes = snapshot.second.groupBy { it.calendarId }
        val plans = snapshot.first.filter { isNotificationsEnabled() && it.reminderEnabled }.flatMap { calendar ->
            delegatedReminderPlans(now, calendar.startLocalDate(), changes[calendar.id].orEmpty().map {
                ScheduleChange(LocalDate.parse(it.effectiveDate), it.weekdayMask)
            }, calendar.reminderMinute, calendar.id in snapshot.third).map {
                DelegatedCalendarEvent(calendar.id, it.key, calendar.name, it.startsAtMillis, it.recurrence)
            }
        }
        if (calendarId != null) alarmManager.cancel(alarmIntent(calendarId))
        snapshot.first.forEach { alarmManager.cancel(alarmIntent(it.id)) }
        if (systemCalendar.hasPermission()) {
            systemCalendar.dismissAlerts(snapshot.third.let { completed ->
                if (calendarId == null) completed else completed.intersect(setOf(calendarId))
            }, now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli(),
                now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli())
            systemCalendar.reconcile(plans, calendarId?.let(::setOf))
            recordCalendarError(null)
        } else if (plans.isNotEmpty() || systemCalendar.hasProjection()) {
            recordCalendarError("请允许读写系统日历，提醒还未完成代管。")
        } else {
            recordCalendarError(null)
        }
        // 实际通知由系统日历发出；本应用仅在用户选择时额外登记可见闹钟标识。
        if (isAlarmClockEnabled() && canScheduleExact()) {
            plans.groupBy { it.calendarId }.forEach { (id, scheduled) ->
                scheduleAlarm(scheduled.minOf { it.startsAtMillis }, alarmIntent(id), openCalendarIntent(id))
            }
        }
    }

    fun canDelegateToCalendar(): Boolean = systemCalendar.hasPermission()
    fun calendarSyncError(): String? = preferences.getString("calendar_sync_error", null)
    fun observeCalendarSyncError() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "calendar_sync_error") trySend(calendarSyncError())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(calendarSyncError())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
    private fun recordCalendarError(message: String?) {
        check(preferences.edit().putString("calendar_sync_error", message).commit()) { "提醒代管状态未保存" }
    }

    private fun scheduleAlarm(triggerAtMillis: Long, alarmIntent: PendingIntent, showIntent: PendingIntent) {
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), alarmIntent)
    }

    private fun openCalendarIntent(calendarId: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = "personal-calendar://calendar/$calendarId".toUri()
            putExtra(EXTRA_CALENDAR_ID, calendarId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun dismissNotification(calendarId: String) {
        NotificationManagerCompat.from(context).cancel(calendarId.hashCode())
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun testReminderState(): ReminderTestState? {
        // 读取同一次偏好快照，不能拼接两个不同测试请求的时间和标识。
        val saved = preferences.all
        val token = saved["test_token"] as? String ?: return null
        if (saved["test_delegated"] != true) return null
        return ReminderTestState(token, saved["test_expected_at"] as? Long ?: 0,
            saved["test_posted_at"] as? Long ?: 0, saved["test_delegated"] as? Boolean ?: false)
    }

    fun observeTestReminder() = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith("test_")) trySend(testReminderState())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(testReminderState())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    suspend fun scheduleTestReminder() = withContext(NonCancellable + Dispatchers.IO) { refreshMutex.withLock {
        check(isNotificationsEnabled() && systemCalendar.hasPermission()) { "请先开启提醒并允许系统日历访问" }
        cancelTestReminderLocked()
        val token = UUID.randomUUID().toString()
        val expectedAt = clock.millis() + TEST_DELAY_MILLIS
        check(preferences.edit().putString("test_token", token).putLong("test_expected_at", expectedAt).putBoolean("test_delegated", true)
            .putLong("test_posted_at", 0).commit()) { "测试提醒未保存，请重试" }
        try {
            val show = PendingIntent.getActivity(context, 0,
                Intent(context, MainActivity::class.java).setData("personal-calendar://reminder-test".toUri()),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            systemCalendar.replaceTest(token, expectedAt)
            if (isAlarmClockEnabled() && canScheduleExact()) scheduleAlarm(expectedAt, testAlarmIntent(), show)
        } catch (error: Exception) {
            cancelTestReminderLocked()
            throw error
        }
    } }

    suspend fun confirmTestReminder(token: String) = withContext(NonCancellable + Dispatchers.IO) { refreshMutex.withLock {
        val test = testReminderState() ?: return@withLock
        if (!test.delegated || test.token != token || test.postedAtMillis != 0L || clock.millis() < test.expectedAtMillis) return@withLock
        systemCalendar.cancelTest()
        alarmManager.cancel(testAlarmIntent())
        check(preferences.edit().putLong("test_posted_at", clock.millis()).commit()) { "测试确认未保存，请重试" }
    } }

    private fun cancelTestReminderLocked() {
        if (testReminderState()?.delegated == true) systemCalendar.cancelTest()
        alarmManager.cancel(testAlarmIntent())
        NotificationManagerCompat.from(context).cancel(TEST_NOTIFICATION_TAG, 0)
        check(preferences.edit().remove("test_token").remove("test_expected_at")
            .remove("test_posted_at").remove("test_delegated").commit()) { "测试提醒未取消，请重试" }
    }

    private fun testAlarmIntent(): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TEST_REMINDER
            data = "personal-calendar://test-reminder".toUri()
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

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
        const val ACTION_TEST_REMINDER = "com.hang2515.personalcalendar.TEST_REMINDER"
        const val TEST_DELAY_MILLIS = 60_000L
        private const val TEST_NOTIFICATION_TAG = "reminder_test"
        private val refreshMutex = Mutex()

        fun open(context: Context): ReminderScheduler = ReminderScheduler(context, CalendarDatabase.open(context))
    }
}
