package com.hang2515.personalcalendar.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.backup.BackupApiException
import com.hang2515.personalcalendar.backup.BackupCoordinator
import com.hang2515.personalcalendar.backup.BackupPreview
import com.hang2515.personalcalendar.backup.BackupSnapshot
import com.hang2515.personalcalendar.backup.CloudConfig
import com.hang2515.personalcalendar.data.LocalState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CloudBackupCard(onAccountChanged: (Boolean, String?) -> Unit) {
    val context = LocalContext.current
    val coordinator = remember { BackupCoordinator.open(context) }
    val scope = coordinator.operationScope
    var state by remember { mutableStateOf<LocalState?>(null) }
    var signedIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var action by remember { mutableStateOf<String?>(null) }
    var showSecurity by remember { mutableStateOf(false) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var accountReadFailed by remember { mutableStateOf(false) }

    fun refreshAccount() {
        signedIn = coordinator.auth.isSignedIn()
        accountEmail = coordinator.auth.accountEmail()
        accountReadFailed = false
        onAccountChanged(signedIn, accountEmail)
    }

    fun runAction(block: suspend () -> String) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try { message = block(); state = coordinator.local.state(); refreshAccount() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                message = if (error is BackupApiException || error is IllegalArgumentException || error is IllegalStateException)
                    error.message ?: "操作未完成，请重试" else "网络或本机存储异常，请重试"
            } finally { busy = false }
        }
    }
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        response.data?.let { intent -> runAction { coordinator.finishLogin(intent); "登录成功，请预览云备份后选择操作" } }
    }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                state = coordinator.local.state(); refreshAccount()
                coordinator.lastError()?.let { message = it }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { accountReadFailed = true; message = "无法读取备份状态，请清除本机登录状态后重新登录" }
            delay(3000)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("云备份", style = MaterialTheme.typography.titleLarge)
            Text("日常使用无需登录。开启备份后，可在换机时恢复日历和打卡。")
            if (!CloudConfig.isConfigured) {
                Text("此安装包使用本地模式，尚未连接云服务。")
            } else {
                Text(if (signedIn) "云备份账号已登录" else "尚未登录云备份账号")
                Text(if (state?.backupEnabled == true) "自动备份已开启" else "自动备份已关闭")
                state?.lastBackupAt?.let { time ->
                    val display = runCatching { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(time)) }.getOrDefault(time)
                    Text("最近备份：$display")
                }
                if (state?.backupEnabled == true && state?.localRevision != state?.lastBackedUpRevision) Text("有修改等待备份")
                if (!signedIn) {
                    Text("欢迎使用个人日历", style = MaterialTheme.typography.titleMedium)
                    Button(enabled = !busy, onClick = {
                        try { login.launch(coordinator.auth.loginIntent()) }
                        catch (_: Exception) { message = "无法打开登录页面，请确认已安装浏览器" }
                    }) { Text("登录 / 注册") }
                } else {
                    OutlinedButton(enabled = !busy, onClick = { runAction {
                        preview = coordinator.preview(); "已读取云备份，请选择操作"
                    } }) { Text("查看云备份 / 设置") }
                    if (state?.backupEnabled == true) {
                        Button(enabled = !busy, onClick = { runAction {
                            if (coordinator.upload()) "备份已完成" else "已上传一份备份，仍有新修改待上传"
                        } }) { Text("立即备份") }
                        TextButton(enabled = !busy, onClick = { runAction { coordinator.stop(false); "自动备份已关闭" } }) { Text("关闭自动备份") }
                    }
                }
                TextButton(enabled = !busy, onClick = { showSecurity = true }) { Text("账号与安全") }
                if (signedIn || accountReadFailed) TextButton(enabled = !busy, onClick = { runAction {
                    coordinator.stop(true); preview = null; "已退出云备份账号，本机记录保留"
                } }) { Text(if (accountReadFailed) "清除本机登录状态" else "退出云备份账号") }
            }
            if (busy) CircularProgressIndicator()
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (showSecurity) {
        AlertDialog(onDismissRequest = { showSecurity = false }, title = { Text("账号安全") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(accountEmail ?: "尚未登录云备份账号")
                Text("账号通过邮箱注册。密码由 AWS Cognito 保护，应用不保存密码。需要修改或找回密码时，在登录页面点击“忘记密码”，通过邮箱验证码设置新密码。")
                Text("退出登录会清除这部手机的登录状态并停止备份，本机记录保留。重新登录后，需要重新确认开启云备份。")
            } },
            confirmButton = { TextButton(onClick = {
                try { login.launch(coordinator.auth.loginIntent()); showSecurity = false }
                catch (_: Exception) { message = "无法打开登录页面，请确认已安装浏览器"; showSecurity = false }
            }) { Text("打开登录页找回密码") } },
            dismissButton = { TextButton(onClick = { showSecurity = false }) { Text("关闭") } })
    }
    preview?.let { selected ->
        val snapshot = selected.backup?.let { BackupSnapshot.decode(it.body) }
        AlertDialog(onDismissRequest = { if (!busy) preview = null }, title = { Text("云备份设置") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (snapshot == null) "此账号尚无云备份。" else "备份时间：${snapshot.createdAt}\n${snapshot.calendars.size} 个日历，${snapshot.checkIns.size} 条打卡")
                if (state?.boundAccountSub != null && state?.boundAccountSub != selected.account) Text("本机记录曾绑定其他账号。继续上传会把本机记录保存到当前账号。")
                Button(enabled = !busy, onClick = { action = "upload" }) { Text(if (snapshot == null) "开启备份" else "用本机数据覆盖云备份") }
                if (snapshot != null) OutlinedButton(enabled = !busy, onClick = { action = "restore" }) { Text("恢复到本机") }
            } }, confirmButton = { TextButton(onClick = { preview = null }) { Text("关闭") } })
    }
    action?.let { selectedAction ->
        AlertDialog(onDismissRequest = { action = null }, title = { Text("确认操作") },
            text = { Text(if (selectedAction == "restore") "恢复将替换本机全部日历、规则和打卡记录，无法与本机记录合并。恢复后自动备份默认关闭。"
                else "将当前手机的全部日历和打卡备份到已登录账号，并开启自动备份。已有云备份将被替换。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val selected = preview ?: return@TextButton
                action = null; preview = null
                runAction {
                    if (selectedAction == "restore") { coordinator.restore(selected); "恢复完成，已重新安排本地提醒" }
                    else { coordinator.enable(selected); if (coordinator.upload()) "云备份已开启并完成首次备份" else "云备份已开启，仍有新修改待上传" }
                }
            }) { Text("确认") } }, dismissButton = { TextButton(onClick = { action = null }) { Text("取消") } })
    }
}
