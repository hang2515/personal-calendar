package com.hang2515.personalcalendar.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.ui.theme.PersonalCalendarTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationNavigationTest {
    @get:Rule(order = 0) val workManagerRule = object : ExternalResource() {
        override fun before() { WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication()) }
        override fun after() { WorkManager.getInstance(RuntimeEnvironment.getApplication()).cancelAllWork().result.get() }
    }
    @get:Rule(order = 1) val compose = createComposeRule()

    @Test fun notificationIsConsumedOnceAcrossTabChangesAndLaterNotificationsStillOpen() {
        val repository = CalendarRepository.open(RuntimeEnvironment.getApplication())
        val calendarId = runBlocking {
            val existingIds = repository.observeCalendars().first().map { it.id }.toSet()
            repository.createCalendar("通知目标", 127, LocalDate.now(ZoneId.of("Asia/Shanghai")))
            repository.observeCalendars().first().single { it.id !in existingIds }.id
        }
        val pendingId = mutableStateOf<String?>(calendarId)
        val request = mutableIntStateOf(0)
        var consumed = 0
        compose.setContent {
            PersonalCalendarTheme {
                PersonalCalendarApp(pendingId.value, request.intValue, 0) { pendingId.value = null; consumed++ }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("日历管理").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("返回日历").performClick()
        compose.onNodeWithText("今天").performClick()
        compose.onNodeWithText("日历").performClick()
        compose.onNodeWithText("我的日历").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, consumed); pendingId.value = calendarId; request.intValue++ }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("日历管理").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(2, consumed) }
    }
}
