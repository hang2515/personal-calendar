package com.hang2515.personalcalendar.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val validActions = mutableSetOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            validActions += AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        }
        if (intent.action !in validActions) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.open(context).refreshAll()
                com.hang2515.personalcalendar.backup.BackupWork.schedule(context,
                    replace = intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED)
            } catch (error: Exception) {
                Log.e("ReminderReceiver", "重新安排提醒失败：${error.javaClass.simpleName}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
