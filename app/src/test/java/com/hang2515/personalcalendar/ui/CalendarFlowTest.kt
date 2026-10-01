package com.hang2515.personalcalendar.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.hang2515.personalcalendar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarFlowTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun newCalendarAppearsTodayAndCanBeCheckedIn() {
        composeRule.onNodeWithText("日历").performClick()
        composeRule.onNodeWithText("＋ 新建日历").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("阅读")
        composeRule.onNodeWithText("创建日历").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("我的日历").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("今天").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("完成打卡").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("完成打卡").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("今天已完成").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
