package com.hang2515.personalcalendar

import android.content.Intent
import android.os.Bundle
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
        openCalendarId = intent.getStringExtra(ReminderScheduler.EXTRA_CALENDAR_ID)
        setContent {
            PersonalCalendarTheme {
                PersonalCalendarApp(openCalendarId, openRequest, permissionRevision)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openCalendarId = intent.getStringExtra(ReminderScheduler.EXTRA_CALENDAR_ID)
        openRequest++
    }

    override fun onResume() {
        super.onResume()
        permissionRevision++
        lifecycleScope.launch {
            try {
                ReminderScheduler.open(this@MainActivity).refreshAll()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ReminderScheduler", "无法重新安排提醒：${error.javaClass.simpleName}")
            }
        }
    }
}
