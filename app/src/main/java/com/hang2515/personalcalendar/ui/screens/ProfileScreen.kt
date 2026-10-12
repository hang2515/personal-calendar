package com.hang2515.personalcalendar.ui.screens

import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.BuildConfig
import com.hang2515.personalcalendar.ui.components.ReminderPermissionCard
import com.hang2515.personalcalendar.ui.components.CalendarGlyph
import com.hang2515.personalcalendar.ui.components.PageHeading

@Composable
fun ProfileScreen(permissionRevision: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isSignedIn by remember { mutableStateOf(false) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf<String?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var showBackupSettings by rememberSaveable { mutableStateOf(false) }
    val accountChanged: (Boolean, String?) -> Unit = { signedIn, email -> isSignedIn = signedIn; accountEmail = email }
    if (showBackupSettings) {
        BackHandler { showBackupSettings = false }
        CloudBackupCard(onAccountChanged = accountChanged, settingsOpen = true,
            onCloseSettings = { showBackupSettings = false }, modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PageHeading("我的", "记录生活，也照顾好自己")
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarGlyph("leaf", "sage", size = 60.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (isSignedIn) "云备份账号" else "本机使用", style = MaterialTheme.typography.titleLarge)
                Text(if (isSignedIn) accountEmail ?: "账号已登录" else "无需登录，也能记录每一天", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CloudBackupCard(onAccountChanged = accountChanged, onOpenSettings = { showBackupSettings = true })
        Text("偏好与支持", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            ProfileSetting("通知与提醒", "权限与准时提醒", Icons.Outlined.Notifications, onClick = { section = "notifications" })
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSetting("时区设置", "北京时间", Icons.Outlined.DateRange, onClick = { section = "timezone" })
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSetting("隐私与数据", "保存、恢复与删除", Icons.Outlined.Lock, onClick = { section = "privacy" })
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProfileSetting("帮助与反馈", "随时为你解答", Icons.Outlined.Info, onClick = { shareError = null; section = "help" })
        }
        Text("个人日历 · ${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    section?.let { selected ->
        AlertDialog(onDismissRequest = { section = null },
            title = { Text(when (selected) {
                "notifications" -> "通知与提醒设置"
                "timezone" -> "统一使用北京时间"
                "privacy" -> "数据保存与删除"
                else -> "如何记录与恢复"
            }) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selected) {
                    "notifications" -> ReminderPermissionCard(permissionRevision)
                    "timezone" -> {
                        Text("日历日期、每天打卡和提醒统一按北京时间（Asia/Shanghai，UTC+8）计算。")
                        Text("旅行或修改手机时区不会改变已有打卡归属。当前版本固定此时区，不提供切换，提醒时间也按北京时间执行。")
                    }
                    "privacy" -> {
                        Text("日历和打卡首先保存在本机。登录后可手动备份，也可单独开启每天自动备份。")
                        Text("开启云备份后，日历、历史规则和打卡上传至 AWS 香港区域的私有加密存储。密码由 Cognito 管理，手机只加密保存登录令牌。")
                        Text("恢复会替换本机全部日历，必须先读取云备份并确认。换机、卸载或清除应用数据前，请先确认最近备份成功。")
                        Text("在日历详情可以删除该日历。退出登录保留本机数据，也不会删除已有云备份；关闭备份后，云端仍保留上次快照。")
                        Text("当前版本不提供账号注销或彻底删除云备份入口。如需清除云端数据，请联系应用维护者处理当前及历史版本。")
                    }
                    else -> {
                        Text("1. 在“日历”新建目标，设置重复星期、图标、颜色和提醒。可用预览检查效果。")
                        Text("2. 在“今天”或日历详情打卡；历史漏签可在日历详情点击日期补签，也可确认撤销历史打卡。未来或未安排日期不能补签。")
                        Text("3. 提醒需要通知权限；省电限制可能影响送达。在“我的 → 通知与提醒”检查权限，并查看手机的应用后台设置。")
                        Text("4. 在“云备份设置”选择立即备份、读取云备份或每天自动备份时间。恢复会整份替换本机记录；多部手机发生冲突时，请先读取再决定恢复或重新上传。")
                        Text("反馈时请填写操作步骤、预期与实际结果。下方只分享应用和系统版本，不自动附带账号或日历内容；由你选择接收人并发送。")
                        TextButton(onClick = {
                            try {
                                val feedback = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "个人日历使用反馈")
                                    putExtra(Intent.EXTRA_TEXT, "个人日历 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n\n操作步骤：\n预期结果：\n实际结果：")
                                }
                                context.startActivity(Intent.createChooser(feedback, "选择反馈接收方式"))
                            } catch (_: android.content.ActivityNotFoundException) {
                                shareError = "没有可用的分享应用，请记录问题后联系维护者。"
                            }
                        }) { Text("分享反馈信息") }
                        shareError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { section = null }) { Text("知道了") } })
    }
}

@Composable
private fun ProfileSetting(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›")
        }
    }
}
