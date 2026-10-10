package com.hang2515.personalcalendar.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.hang2515.personalcalendar.ui.components.WheelTimePicker
import com.hang2515.personalcalendar.ui.theme.PersonalCalendarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WheelTimePickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scrollingToLastHourAndMinuteConfirms2359() {
        var saved = 480
        compose.setContent { PersonalCalendarTheme { WheelTimePicker(480, {}, { saved = it }) } }
        compose.onNodeWithContentDescription("小时").performScrollToIndex(23)
        compose.onNodeWithContentDescription("分钟").performScrollToIndex(59)
        compose.onNodeWithContentDescription("小时").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "23"))
        compose.onNodeWithText("确定 23:59").performClick()
        compose.runOnIdle { assertEquals(1439, saved) }
    }

    @Test fun midnightCanBeSelectedFromEndOfDay() {
        var saved = 1439
        compose.setContent { PersonalCalendarTheme { WheelTimePicker(1439, {}, { saved = it }) } }
        compose.onNodeWithContentDescription("小时").performScrollToIndex(0)
        compose.onNodeWithContentDescription("分钟").performScrollToIndex(0)
        compose.onNodeWithText("确定 00:00").performClick()
        compose.runOnIdle { assertEquals(0, saved) }
    }

    @Test fun cancellingAfterScrollingKeepsOriginalTime() {
        var saved = 480
        var dismissed = false
        compose.setContent { PersonalCalendarTheme { WheelTimePicker(480, { dismissed = true }, { saved = it }) } }
        compose.onNodeWithContentDescription("分钟").performScrollToIndex(35)
        compose.onNodeWithText("确定 08:35").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(480, saved); assertEquals(true, dismissed) }
    }

    @Test
    @Config(qualifiers = "w640dp-h320dp-land")
    fun shortScreenWithLargeFontCanScrollToConfirmWithoutChangingCenteredTime() {
        var saved = -1
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                PersonalCalendarTheme { WheelTimePicker(1439, {}, { saved = it }) }
            }
        }
        compose.onNodeWithText("确定 23:59").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1439, saved) }
    }
}
