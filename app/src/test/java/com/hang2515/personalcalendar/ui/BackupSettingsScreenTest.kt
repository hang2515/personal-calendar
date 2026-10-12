package com.hang2515.personalcalendar.ui

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.hang2515.personalcalendar.ui.screens.*
import com.hang2515.personalcalendar.ui.theme.PersonalCalendarTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupSettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        File("build/ui-preview").mkdirs()
        File("build/ui-preview/$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun readAndUploadAreIndependentAndRestoreOnlyAppearsAfterReading() {
        var uploads = 0; var reads = 0; var restores = 0
        val hasRead = mutableStateOf(false)
        compose.setContent { PersonalCalendarTheme { Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            CloudBackupSettingsScreen(BackupSettingsUiState(true, true, false, 1320, "test@example.com", "2026-10-11 22:00",
                if (hasRead.value) "云端备份：2026-10-11 22:00\n2 个日历 · 8 条打卡" else null, hasRead.value, false, null, true),
                {}, {}, { uploads++ }, { reads++; hasRead.value = true }, { restores++ }, {}, {}, {}, {})
        } } }
        capture("backup-settings")
        compose.onNodeWithText("恢复到这部手机").assertDoesNotExist()
        compose.onNodeWithText("立即备份").performScrollTo().performClick()
        assertEquals(1, uploads); assertEquals(0, reads)
        compose.onNodeWithText("读取云备份").performScrollTo().performClick()
        compose.onNodeWithText("恢复到这部手机").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, reads); assertEquals(1, restores)
        compose.onNodeWithContentDescription("选择自动备份时间").performScrollTo()
        capture("backup-schedule")
    }

    @Test @Config(qualifiers = "w320dp-h800dp-xhdpi")
    fun automaticBackupCanBeDisabledWithoutValidLoginAtLargeFontSize() {
        var disabled = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                PersonalCalendarTheme { Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                    CloudBackupSettingsScreen(BackupSettingsUiState(false, false, true, 450, null, null, null, false, false, null, true),
                        {}, {}, {}, {}, {}, { disabled = !it }, {}, {}, {})
                } }
            }
        }
        compose.onNodeWithContentDescription("每日自动备份").performScrollTo().assertIsEnabled().performClick()
        assertTrue(disabled)
        compose.onNodeWithContentDescription("选择自动备份时间").performScrollTo().assertIsDisplayed()
        capture("backup-large-text")
    }
}
