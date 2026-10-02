package com.hang2515.personalcalendar.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.hang2515.personalcalendar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 以原生渲染生成可检查的 Compose 截图，产物仅写入忽略的构建目录。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiPreviewTest {
    @get:Rule(order = 0) val workManagerRule = object : ExternalResource() {
        override fun before() { WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication()) }
        override fun after() { WorkManager.getInstance(RuntimeEnvironment.getApplication()).cancelAllWork().result.get() }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String, node: SemanticsNodeInteraction = compose.onRoot()) {
        val directory = File("build/ui-preview").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun launcherUsesAdaptiveIconWithThemedLayer() {
        val icon = compose.activity.getDrawable(com.hang2515.personalcalendar.R.mipmap.ic_launcher)!!
        assertTrue(icon is AdaptiveIconDrawable)
        assertNotNull((icon as AdaptiveIconDrawable).monochrome)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 512, 512)
        icon.draw(Canvas(bitmap))
        val directory = File("build/ui-preview").apply { mkdirs() }
        File(directory, "launcher-icon.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun primaryScreensRenderWithRealCalendarAndCompletedState() {
        compose.onNodeWithText("日历").performClick()
        compose.onNodeWithContentDescription("新建日历").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("每天阅读")
        compose.onNodeWithContentDescription("计划日提醒").performScrollTo().performClick()
        capture("create")
        compose.onNodeWithText("提醒时间：08:00").performScrollTo().performClick()
        compose.onNodeWithContentDescription("小时").assertExists()
        capture("time-picker", compose.onNodeWithText("提醒时间").onParent().onParent())
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("创建日历").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("本月 0 / 1 天").fetchSemanticsNodes().isNotEmpty() }
        capture("calendars")
        compose.onNodeWithText("今天").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("完成打卡").fetchSemanticsNodes().isNotEmpty() }
        capture("today")
        compose.onNodeWithText("完成打卡").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("今天已完成").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("撤销打卡").assertIsDisplayed()
        capture("completed")
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("云备份").assertIsDisplayed()
        capture("profile")
        compose.onNodeWithText("通知与提醒").performClick()
        compose.onNodeWithText("通知与提醒设置").assertIsDisplayed()
        capture("notification-settings", compose.onNodeWithText("通知与提醒设置").onParent().onParent())
        compose.onNodeWithText("知道了").performClick()
        compose.onNodeWithText("日历").performClick()
        compose.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("查看完整日历"))
        compose.onNodeWithText("查看完整日历").performClick()
        compose.onNodeWithContentDescription("日历管理").assertIsDisplayed()
        capture("detail")
        compose.onNodeWithContentDescription("日历管理").performClick()
        compose.onNodeWithText("编辑日历").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("每天阅读")
        capture("edit")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("日历管理").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("我的日历").assertIsDisplayed()
    }
}
