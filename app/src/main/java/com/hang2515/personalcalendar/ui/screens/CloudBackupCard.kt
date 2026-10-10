package com.hang2515.personalcalendar.ui.screens

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var showManagement by remember { mutableStateOf(false) }

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
            ?: run { message = "已取消登录，本机记录不受影响" }
    }
    fun openLogin() {
        try { login.launch(coordinator.auth.loginIntent()) }
        catch (_: ActivityNotFoundException) { message = "没有可用于授权的浏览器，请检查默认浏览器设置" }
        catch (error: Exception) {
            Log.e("CloudAuth", "登录启动失败：${error.javaClass.simpleName}")
            message = if (!CloudConfig.isConfigured) "此安装包尚未配置云服务" else "登录请求未能启动，请重试或更新应用"
        }
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
    val lastBackup = state?.lastBackupAt?.let { time ->
        runCatching { DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(time)) }.getOrDefault("时间暂不可用")
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val heading: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("云备份", style = MaterialTheme.typography.titleMedium)
                    Text(when {
                        !CloudConfig.isConfigured -> "本地模式"
                        accountReadFailed -> "登录状态需要处理"
                        !signedIn -> "为每一份坚持，留一份备份"
                        state?.backupEnabled != true -> "已登录 · 自动备份未开启"
                        state?.localRevision != state?.lastBackedUpRevision -> "自动备份已开启 · 有更新待上传"
                        lastBackup != null -> "最近备份 $lastBackup"
                        else -> "自动备份已开启"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
                val accountAction: @Composable () -> Unit = {
                if (CloudConfig.isConfigured) {
                    if (!signedIn && !accountReadFailed) Button(enabled = !busy, onClick = ::openLogin) { Text("登录 / 注册") }
                    else TextButton(enabled = !busy, onClick = { showManagement = true }) { Text("管理 ›") }
                }
                }
                if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { heading(Modifier.fillMaxWidth()); accountAction() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        heading(Modifier.weight(1f)); accountAction()
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showManagement) {
        ModalBottomSheet(onDismissRequest = { showManagement = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("备份管理", style = MaterialTheme.typography.headlineMedium)
                Text(accountEmail ?: "云备份账号", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (state?.backupEnabled == true) "自动备份已开启" else "自动备份已关闭", style = MaterialTheme.typography.titleMedium)
                Text(lastBackup?.let { "最近备份：$it" } ?: "还没有成功备份记录", style = MaterialTheme.typography.bodyMedium)
                Text("备份保存整份日历与打卡。恢复前可预览，确认后替换本机记录。", style = MaterialTheme.typography.bodyMedium)
                if (signedIn) {
                    OutlinedButton(enabled = !busy, onClick = { runAction {
                        preview = coordinator.preview(); showManagement = false; "已读取云备份，请选择操作"
                    } }, modifier = Modifier.fillMaxWidth()) { Text("预览与恢复") }
                    if (state?.backupEnabled == true) {
                        Button(enabled = !busy, onClick = { runAction {
                            if (coordinator.upload()) "备份已完成" else "已上传一份备份，仍有新修改待上传"
                        } }, modifier = Modifier.fillMaxWidth()) { Text("立即备份") }
                        TextButton(enabled = !busy, onClick = { runAction { coordinator.stop(false); "自动备份已关闭" } }) { Text("关闭自动备份") }
                    }
                }
                TextButton(enabled = !busy, onClick = { showManagement = false; showSecurity = true }) { Text("账号与安全") }
                if (signedIn || accountReadFailed) TextButton(enabled = !busy, onClick = { runAction {
                    coordinator.stop(true); preview = null; showManagement = false; "已退出云备份账号，本机记录保留"
                } }) { Text(if (accountReadFailed) "清除本机登录状态" else "退出云备份账号") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
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
                openLogin(); showSecurity = false
            }) { Text("打开登录页找回密码") } },
            dismissButton = { TextButton(onClick = { showSecurity = false }) { Text("关闭") } })
    }
    preview?.takeIf { action == null }?.let { selected ->
        val snapshot = selected.backup?.let { BackupSnapshot.decode(it.body) }
        AlertDialog(onDismissRequest = { if (!busy) preview = null }, title = { Text("云备份设置") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
