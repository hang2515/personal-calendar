package com.hang2515.personalcalendar.ui.components

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReminderPermissionCard(permissionRevision: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheduler = remember(context) { ReminderScheduler.open(context) }
    val scope = rememberCoroutineScope()
    var requestRevision by remember { mutableIntStateOf(0) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    fun openSettings(intent: Intent) {
        settingsError = null
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { settingsError = "无法打开系统设置，请到手机设置中找到个人日历并开启权限。" }
    }
    fun notificationSettings() = openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    })
    val requestNotification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requestRevision++
        if (it) scope.launch {
            try {
                scheduler.refreshAll()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ReminderScheduler", "无法重新安排提醒：${error.javaClass.simpleName}")
            }
        }
    }
    val canNotify = remember(permissionRevision, requestRevision) { scheduler.canPostNotifications() }
    val canBeExact = remember(permissionRevision, requestRevision) { scheduler.canScheduleExact() }
    val needsRuntimePermission = Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                !canNotify -> {
                    Text("通知尚未开启，提醒不会显示。")
                    TextButton(onClick = {
                        if (needsRuntimePermission) {
                            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationSettings()
                        }
                    }) { Text("开启通知") }
                    // 系统在多次拒绝后可能不再弹窗，始终保留可恢复授权的入口。
                    if (needsRuntimePermission) TextButton(onClick = ::notificationSettings) { Text("打开通知设置") }
                }
                !canBeExact -> {
                    Text("提醒已开启；系统可能延迟送达。")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        TextButton(onClick = {
                            openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = "package:${context.packageName}".toUri()
                            })
                        }) { Text("允许准时提醒") }
                    }
                }
                else -> Text("通知与准时提醒已开启。")
            }
            settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
