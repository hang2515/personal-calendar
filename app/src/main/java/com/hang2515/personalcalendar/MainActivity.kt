package com.hang2515.personalcalendar

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import com.hang2515.personalcalendar.ui.PersonalCalendarApp
import com.hang2515.personalcalendar.ui.theme.PersonalCalendarTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var openCalendarId by mutableStateOf<String?>(null)
    private var openRequest by mutableIntStateOf(0)
    private var permissionRevision by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receiveReminderIntent(intent)
        setContent {
            PersonalCalendarTheme {
                PersonalCalendarApp(openCalendarId, openRequest, permissionRevision, onOpenRequestConsumed = {
                    openCalendarId = null
                    // 通知导航是一次性事件，旋转或切换底栏不能再次消费旧 Intent。
                    intent.removeExtra(ReminderScheduler.EXTRA_CALENDAR_ID)
                    intent.removeExtra(CalendarContract.EXTRA_CUSTOM_APP_URI)
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveReminderIntent(intent)
        openRequest++
    }

    private fun receiveReminderIntent(incoming: Intent) {
        openCalendarId = calendarIdFromReminderIntent(incoming)
        if (incoming.action == CalendarContract.ACTION_HANDLE_CUSTOM_EVENT) {
            setResult(if (openCalendarId == null) RESULT_CANCELED else RESULT_OK)
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRevision++
        lifecycleScope.launch {
            try {
                com.hang2515.personalcalendar.backup.BackupWork.schedule(this@MainActivity)
                ReminderScheduler.open(this@MainActivity).refreshAll()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ReminderScheduler", "无法重新安排提醒：${error.javaClass.simpleName}")
            }
        }
    }
}

internal fun calendarIdFromReminderIntent(intent: Intent): String? {
    if (intent.action != CalendarContract.ACTION_HANDLE_CUSTOM_EVENT) return intent.getStringExtra(ReminderScheduler.EXTRA_CALENDAR_ID)
    val value = intent.getStringExtra(CalendarContract.EXTRA_CUSTOM_APP_URI) ?: return null
    val uri = Uri.parse(value)
    return uri.pathSegments.takeIf { uri.scheme == "personal-calendar" && uri.authority == "managed-reminder" && it.size == 2 }
        ?.firstOrNull()?.takeIf { it.isNotBlank() && it.length <= 128 }
}
