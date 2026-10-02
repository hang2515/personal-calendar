package com.hang2515.personalcalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hang2515.personalcalendar.data.CalendarEntity
import com.hang2515.personalcalendar.domain.ScheduleChange
import com.hang2515.personalcalendar.ui.screens.CalendarMonthGrid
import com.hang2515.personalcalendar.ui.theme.PersonalCalendarTheme
import com.hang2515.personalcalendar.ui.components.MonthNavigation
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun completedDayStatusIsNotClippedWithLargeTextOnNarrowScreen() {
        val today = LocalDate.of(2026, 10, 3)
        val calendar = CalendarEntity("layout-calendar", "阅读", "2026-10-01", 127)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                PersonalCalendarTheme {
                    Column(Modifier.fillMaxWidth().padding(40.dp)) {
                        CalendarMonthGrid(YearMonth.of(2026, 10), today, calendar,
                            listOf(ScheduleChange(LocalDate.of(2026, 10, 1), 127)), setOf(today))
                    }
                }
            }
        }
        val marker = compose.onNodeWithContentDescription("完成标记", useUnmergedTree = true)
        marker.assertIsDisplayed().assertWidthIsAtLeast(22.dp).assertHeightIsAtLeast(22.dp)
        val markerBounds = marker.fetchSemanticsNode().boundsInRoot
        val dayBounds = compose.onNodeWithContentDescription("2026-10-03，已完成").fetchSemanticsNode().boundsInRoot
        assertTrue("完成标记必须完整位于日期格中", dayBounds.contains(markerBounds.topLeft) && dayBounds.contains(markerBounds.bottomRight))
        val directory = File("build/ui-preview").apply { mkdirs() }
        File(directory, "calendar-large-text.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun monthNavigationRetainsBothActionsAtLargeFontSize() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                PersonalCalendarTheme {
                    var month by remember { mutableStateOf(YearMonth.of(2026, 12)) }
                    Column(Modifier.fillMaxWidth().padding(40.dp)) {
                        MonthNavigation(month, { month = month.minusMonths(1) }, { month = month.plusMonths(1) })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("下个月").assertIsDisplayed().performClick()
        compose.onNodeWithText("1月").assertIsDisplayed()
        compose.onNodeWithText("2027年").assertIsDisplayed()
        compose.onNodeWithContentDescription("上个月").assertIsDisplayed().performClick()
        compose.onNodeWithText("12月").assertIsDisplayed()
        compose.onNodeWithText("2026年").assertIsDisplayed()
    }
}
