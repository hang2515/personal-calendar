package com.hang2515.personalcalendar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val calendarId = intent.getStringExtra(ReminderScheduler.EXTRA_CALENDAR_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.open(context).deliver(calendarId)
            } catch (error: Exception) {
                Log.e("ReminderReceiver", "提醒处理失败：${error.javaClass.simpleName}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
