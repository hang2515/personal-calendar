package com.hang2515.personalcalendar.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import com.hang2515.personalcalendar.data.*
import com.hang2515.personalcalendar.ui.screens.CalendarDetailScreen
import com.hang2515.personalcalendar.ui.theme.PersonalCalendarTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackfillScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missedDateRequiresConfirmationAndCompletedDateCanBeUndone() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), CalendarDatabase::class.java).build()
        try {
            val calendar = CalendarEntity("backfill-ui", "每天阅读", "2026-10-01", 127)
            val dao = database.calendarDao()
            dao.insertCalendar(calendar)
            dao.upsertScheduleChange(ScheduleChangeEntity(calendar.id, "2026-10-01", 127))
            val clock = Clock.fixed(Instant.parse("2026-10-12T03:00:00Z"), ZoneId.of("Asia/Shanghai"))
            val repository = CalendarRepository(database, clock)
            compose.setContent { PersonalCalendarTheme {
                CalendarDetailScreen(calendar, repository, LocalDate.of(2026, 10, 12), {}, {}, {})
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("2026-10-10，未完成").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("2026-10-10，未完成").performScrollTo().performClick()
            compose.onNodeWithText("补签这一天？").assertIsDisplayed()
            assertFalse(dao.hasCheckIn(calendar.id, "2026-10-10"))
            File("build/ui-preview").mkdirs()
            File("build/ui-preview/backfill-confirm.png").outputStream().use {
                compose.onNodeWithText("补签这一天？").onParent().onParent().captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("确认补签").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("2026-10-10，已完成").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(dao.hasCheckIn(calendar.id, "2026-10-10"))
            compose.onNodeWithText("已完成 1 / 31").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("2026-10-10，已完成").performScrollTo().performClick()
            compose.onNodeWithText("确认撤销").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("2026-10-10，未完成").fetchSemanticsNodes().isNotEmpty() }
            assertFalse(dao.hasCheckIn(calendar.id, "2026-10-10"))
        } finally { database.close() }
    }
}
