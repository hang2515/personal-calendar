package com.hang2515.personalcalendar.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.isToggleable
import com.hang2515.personalcalendar.MainActivity
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarFlowTest {
    private val remainingMonthDays: Int get() = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).let { it.lengthOfMonth() - it.dayOfMonth + 1 }
    @get:Rule(order = 0) val workManagerRule = object : ExternalResource() {
        override fun before() {
            WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication())
        }
        override fun after() {
            WorkManager.getInstance(RuntimeEnvironment.getApplication()).cancelAllWork().result.get()
        }
    }
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun systemBackFromCreateReturnsToCalendarList() {
        composeRule.onNodeWithText("日历").performClick()
        composeRule.onNodeWithContentDescription("新建日历").performClick()
        composeRule.onNodeWithText("新的坚持").assertExists()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("我的日历").assertIsDisplayed()
    }

    @Test
    fun notificationSettingsRemainReachableWhenPermissionIsDenied() {
        org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("通知与提醒").performScrollTo().performClick()
        composeRule.onNodeWithText("打开通知设置").performScrollTo().assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            org.robolectric.Shadows.shadowOf(composeRule.activity).nextStartedActivity.action)
    }

    @Test
    fun reminderTimeUsesScrollableHourAndMinuteSelectors() {
        composeRule.onNodeWithText("日历").performClick()
        composeRule.onNodeWithContentDescription("新建日历").performClick()
        composeRule.onNode(isToggleable()).performScrollTo().performClick()
        composeRule.onNodeWithText("提醒时间：08:00").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("小时").assertExists()
        composeRule.onNodeWithContentDescription("分钟").assertExists()
    }

    @Test
    fun newCalendarShowsLivePreviewAndMonthlyCardCountUpdatesAfterCheckIn() {
        composeRule.onNodeWithText("日历").performClick()
        composeRule.onNodeWithContentDescription("新建日历").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("散步")
        composeRule.onNodeWithText("预览效果").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("日历预览：散步，每天，提醒关闭").assertExists()
        composeRule.onNodeWithText("创建日历").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("本月 0 / $remainingMonthDays 天").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("今天").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("完成打卡").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("完成打卡").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("今天已完成").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("日历").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("本月 1 / $remainingMonthDays 天").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun profileHelpAndPrivacyCanBeOpenedWithoutSigningIn() {
        composeRule.onNodeWithText("我的").performClick()
        composeRule.onNodeWithText("通知尚未开启，提醒不会显示。").assertDoesNotExist()
        composeRule.onNodeWithText("通知与提醒").performScrollTo().performClick()
        composeRule.onNodeWithText("通知与提醒设置").assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithText("帮助与反馈").performScrollTo().performClick()
        composeRule.onNodeWithText("如何记录与恢复").assertIsDisplayed()
        composeRule.onNodeWithText("分享反馈信息").assertExists()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithText("隐私与数据").performScrollTo().performClick()
        composeRule.onNodeWithText("数据保存与删除").assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithText("时区设置").performScrollTo().performClick()
        composeRule.onNodeWithText("统一使用北京时间").assertIsDisplayed()
    }

    @Test
    fun calendarCanBeCreatedCheckedInUndoneAndDeleted() {
        composeRule.onNodeWithText("日历").performClick()
        composeRule.onNodeWithContentDescription("新建日历").performClick()
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
        composeRule.onNodeWithContentDescription("撤销打卡").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("完成打卡").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("日历").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("本月 0 / $remainingMonthDays 天").fetchSemanticsNodes().isNotEmpty()
        }
        // 月历位于惰性列表的屏外项，先让列表定位并组合该项，再点击入口。
        composeRule.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("查看完整日历"))
        composeRule.onNodeWithText("查看完整日历").performClick()
        composeRule.onNodeWithContentDescription("日历管理").performClick()
        composeRule.onNodeWithText("删除日历").performClick()
        composeRule.onNodeWithText("确认删除").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("还没有日历").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
