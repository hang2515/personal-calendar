package com.hang2515.personalcalendar.ui

import android.content.Intent
import android.provider.CalendarContract
import com.hang2515.personalcalendar.calendarIdFromReminderIntent
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderIntentTest {
    @Test fun handlesSystemCalendarCustomEventAndPreservesExistingNotificationNavigation() {
        assertEquals("reading", calendarIdFromReminderIntent(Intent(CalendarContract.ACTION_HANDLE_CUSTOM_EVENT)
            .putExtra(CalendarContract.EXTRA_CUSTOM_APP_URI, "personal-calendar://managed-reminder/reading/2026-11-09")))
        assertEquals("reading", calendarIdFromReminderIntent(Intent().putExtra(ReminderScheduler.EXTRA_CALENDAR_ID, "reading")))
        listOf("https://managed-reminder/reading/2026-11-09", "personal-calendar://other/reading/2026-11-09",
            "personal-calendar://managed-reminder/reading", "personal-calendar://managed-reminder-test/test/token").forEach {
            assertNull(calendarIdFromReminderIntent(Intent(CalendarContract.ACTION_HANDLE_CUSTOM_EVENT)
                .putExtra(CalendarContract.EXTRA_CUSTOM_APP_URI, it)))
        }
    }
}
