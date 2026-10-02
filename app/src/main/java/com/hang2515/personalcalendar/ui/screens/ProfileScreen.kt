package com.hang2515.personalcalendar.ui.screens

import android.content.Intent
import android.os.Build
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.BuildConfig
import com.hang2515.personalcalendar.ui.components.ReminderPermissionCard
import com.hang2515.personalcalendar.ui.components.CalendarGlyph

@Composable
fun ProfileScreen(permissionRevision: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isSignedIn by remember { mutableStateOf(false) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf<String?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CalendarGlyph("leaf", "sage", size = 64.dp)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (isSignedIn) "云备份账号" else "本机使用", style = MaterialTheme.typography.titleLarge)
                    Text(if (isSignedIn) accountEmail ?: "账号已登录" else "无需登录，也能记录每一天", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        CloudBackupCard { signedIn, email -> isSignedIn = signedIn; accountEmail = email }
        Text("通知与提醒", style = MaterialTheme.typography.titleMedium)
        ReminderPermissionCard(permissionRevision)
        ProfileSetting("时区设置", "北京时间", onClick = { section = "timezone" })
        ProfileSetting("隐私与数据", "了解数据保存、恢复与删除", onClick = { section = "privacy" })
        ProfileSetting("帮助与反馈", "使用帮助与问题反馈", onClick = { shareError = null; section = "help" })
        Text("个人日历 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    }
    section?.let { selected ->
        AlertDialog(onDismissRequest = { section = null },
            title = { Text(when (selected) {
                "timezone" -> "统一使用北京时间"
                "privacy" -> "数据保存与删除"
                else -> "如何记录与恢复"
            }) },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (selected) {
                    "timezone" -> {
                        Text("日历日期、每天打卡和提醒统一按北京时间（Asia/Shanghai，UTC+8）计算。")
                        Text("旅行或修改手机时区不会改变已有打卡归属。当前版本固定此时区，不提供切换，提醒时间也按北京时间执行。")
                    }
                    "privacy" -> {
                        Text("日历和打卡首先保存在本机。未登录或未开启云备份时，不上传这些记录。")
                        Text("开启云备份后，日历、历史规则和打卡上传至 AWS 香港区域的私有加密存储。密码由 Cognito 管理，手机只加密保存登录令牌。")
                        Text("恢复会替换本机全部日历，必须预览并确认。换机、卸载或清除应用数据前，请先确认最近备份成功。")
                        Text("在日历详情可以删除该日历。退出登录保留本机数据，也不会删除已有云备份；关闭备份后，云端仍保留上次快照。")
                        Text("当前版本不提供账号注销或彻底删除云备份入口。如需清除云端数据，请联系应用维护者处理当前及历史版本。")
                    }
                    else -> {
                        Text("1. 在“日历”新建目标，设置重复星期、图标、颜色和提醒。可用预览检查效果。")
                        Text("2. 在“今天”或日历详情打卡；误操作仅能在当天撤销。过去的漏打卡不能补签，未安排日期不计入完成率。")
                        Text("3. 提醒需要通知权限；省电限制可能影响送达。检查上方“通知与提醒”及手机的应用后台设置。")
                        Text("4. 登录后预览云备份，确认开启或恢复。当前是整份备份；多部手机发生冲突时，请先预览再决定覆盖，记录不会自动合并。")
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
private fun ProfileSetting(title: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Text("›")
        }
    }
}
