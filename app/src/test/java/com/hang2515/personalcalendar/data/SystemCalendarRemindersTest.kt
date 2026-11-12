package com.hang2515.personalcalendar.data

import android.Manifest
import android.content.ContentValues
import android.provider.CalendarContract
import com.hang2515.personalcalendar.reminders.DelegatedCalendarEvent
import com.hang2515.personalcalendar.reminders.SystemCalendarReminders
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SystemCalendarRemindersTest {
    private lateinit var provider: TestCalendarProvider
    private lateinit var delegate: SystemCalendarReminders
    private val context get() = RuntimeEnvironment.getApplication()
    private val event = DelegatedCalendarEvent("reading", "2026-11-09", "阅读", 1794193200000, "FREQ=WEEKLY;BYDAY=MO;WKST=MO")

    @Before fun prepare() {
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        provider = TestCalendarProvider.install()
        delegate = SystemCalendarReminders(context)
    }
    private fun count(table: String): Int = provider.database.rawQuery("SELECT COUNT(_id) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    @Test fun createsLocalCalendarAndRepeatingAlertIdempotently() {
        delegate.reconcile(listOf(event))
        assertEquals(1, count("calendars")); assertEquals(1, count("events")); assertEquals(1, count("reminders"))
        provider.database.rawQuery("SELECT account_type,calendar_timezone FROM calendars", null).use {
            assertTrue(it.moveToFirst()); assertEquals(CalendarContract.ACCOUNT_TYPE_LOCAL, it.getString(0)); assertEquals("Asia/Shanghai", it.getString(1))
        }
        provider.database.rawQuery("SELECT minutes,method FROM reminders", null).use {
            assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertEquals(CalendarContract.Reminders.METHOD_ALERT, it.getInt(1))
        }
        val previousWrites = provider.mutations
        delegate.reconcile(listOf(event))
        assertEquals(previousWrites, provider.mutations)
        delegate.reconcile(listOf(event.copy(title = "新名称")))
        assertEquals(1, count("events"))
    }

    @Test fun scopedUpdatesAndClearNeverDeleteUnrelatedCalendarsOrManuallyAddedEvents() {
        delegate.reconcile(listOf(event, event.copy(calendarId = "exercise")))
        val ownCalendar = provider.database.rawQuery("SELECT calendar_id FROM events LIMIT 1", null).use { it.moveToFirst(); it.getLong(0) }
        provider.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, ownCalendar); put(CalendarContract.Events.TITLE, "用户自行添加")
        })
        val other = provider.insert(CalendarContract.Calendars.CONTENT_URI, ContentValues().apply { put(CalendarContract.Calendars.ACCOUNT_NAME, "other") })
        provider.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, android.content.ContentUris.parseId(other)); put(CalendarContract.Events.TITLE, "其他日历")
        })
        delegate.reconcile(emptyList(), setOf("reading"))
        assertEquals(3, count("events"))
        delegate.clearManaged()
        assertEquals(2, count("events")); assertEquals(2, count("calendars"))
    }

    @Test fun providerFailureRollsBackEventAndDoesNotTreatUnreadableDataAsEmpty() {
        provider.failNextReminder = true
        assertTrue(runCatching { delegate.reconcile(listOf(event)) }.isFailure)
        assertEquals(0, count("events")); assertEquals(0, count("reminders"))
        delegate.reconcile(listOf(event))
        provider.failQuery = true
        assertTrue(runCatching { delegate.reconcile(emptyList()) }.isFailure)
        provider.failQuery = false
        assertEquals(1, count("events"))
    }

    @Test fun revokedPermissionCannotPretendManagedRemindersWereCleared() {
        delegate.reconcile(listOf(event))
        Shadows.shadowOf(context).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        assertTrue(runCatching { delegate.clearManaged() }.isFailure)
        assertEquals(1, count("events"))
    }

    @Test fun dismissesOnlySelectedSourcesAlertsWithinToday() {
        delegate.reconcile(listOf(event, event.copy(calendarId = "exercise")))
        val ids = provider.database.rawQuery("SELECT _id FROM events ORDER BY _id", null).use {
            buildList { while (it.moveToNext()) add(it.getLong(0)) }
        }
        listOf(ids[0] to 100L, ids[0] to 200L, ids[1] to 100L).forEach { (id, begin) ->
            provider.insert(CalendarContract.CalendarAlerts.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.CalendarAlerts.EVENT_ID, id); put(CalendarContract.CalendarAlerts.BEGIN, begin)
                put(CalendarContract.CalendarAlerts.STATE, CalendarContract.CalendarAlerts.STATE_FIRED)
            })
        }
        delegate.dismissAlerts(setOf("reading"), 100, 200)
        provider.database.rawQuery("SELECT state FROM calendar_alerts ORDER BY _id", null).use {
            assertTrue(it.moveToNext()); assertEquals(CalendarContract.CalendarAlerts.STATE_DISMISSED, it.getInt(0))
            assertTrue(it.moveToNext()); assertEquals(CalendarContract.CalendarAlerts.STATE_FIRED, it.getInt(0))
            assertTrue(it.moveToNext()); assertEquals(CalendarContract.CalendarAlerts.STATE_FIRED, it.getInt(0))
        }
    }
}
