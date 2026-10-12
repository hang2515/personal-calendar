package com.hang2515.personalcalendar.ui.screens

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.backup.*
import com.hang2515.personalcalendar.data.LocalState
import com.hang2515.personalcalendar.ui.components.WheelTimePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CloudBackupCard(
    onAccountChanged: (Boolean, String?) -> Unit,
    settingsOpen: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onCloseSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coordinator = remember { BackupCoordinator.open(context) }
    var state by remember { mutableStateOf<LocalState?>(null) }
    var signedIn by remember { mutableStateOf(false) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var accountReadFailed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }
    var cloudSummary by remember { mutableStateOf<String?>(null) }
    var canRestore by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<String?>(null) }
    var showSecurity by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var minute by remember { mutableIntStateOf(coordinator.timeSettings.minute()) }

    fun refreshAccount() {
        signedIn = coordinator.auth.isSignedIn()
        accountEmail = coordinator.auth.accountEmail()
        minute = coordinator.timeSettings.minute()
        accountReadFailed = false
        onAccountChanged(signedIn, accountEmail)
    }
    fun clearReadBackup() { preview = null; cloudSummary = null; canRestore = false }
    fun runAction(block: suspend () -> String) {
        if (busy) return
        busy = true; message = null
        coordinator.operationScope.launch {
            try { message = block(); state = coordinator.local.state(); refreshAccount() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                message = if (error is BackupApiException || error is IllegalArgumentException || error is IllegalStateException)
                    error.message ?: "操作未完成，请重试" else "网络或本机存储异常，请重试"
            } finally { busy = false }
        }
    }
    val login = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { response ->
        response.data?.let { intent -> runAction {
            coordinator.finishLogin(intent); clearReadBackup(); "登录成功，可以立即备份或读取云备份"
        } } ?: run { message = "已取消登录，本机记录不受影响" }
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
        var previousBackgroundError: String? = null
        while (true) {
            try {
                state = coordinator.local.state(); refreshAccount()
                val backgroundError = coordinator.lastError()
                if (backgroundError != null && backgroundError != previousBackgroundError) message = backgroundError
                previousBackgroundError = backgroundError
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { accountReadFailed = true; message = "无法读取登录或备份状态，请清除本机登录状态后重新登录" }
            delay(3000)
        }
    }
    val lastBackup = state?.lastBackupAt?.let(::backupDisplayTime)
    val canUseCloud = CloudConfig.isConfigured && signedIn && !accountReadFailed && state != null
    if (settingsOpen) {
        CloudBackupSettingsScreen(
            BackupSettingsUiState(signedIn, canUseCloud, state?.backupEnabled == true, minute, accountEmail,
                lastBackup, cloudSummary, canRestore, busy, message, CloudConfig.isConfigured, accountReadFailed),
            onBack = onCloseSettings, onLogin = ::openLogin,
            onBackup = { runAction {
                clearReadBackup(); preview = coordinator.preview(); action = "upload"
                "已读取当前云备份，请确认上传"
            } },
            onRead = { runAction {
                clearReadBackup()
                val selected = coordinator.preview()
                val snapshot = selected.backup?.let { BackupSnapshot.decode(it.body) }
                preview = selected; canRestore = snapshot != null
                cloudSummary = if (snapshot == null) "此账号还没有云备份。" else
                    "云端备份：${backupDisplayTime(snapshot.createdAt)}\n${snapshot.calendars.size} 个日历 · ${snapshot.checkIns.size} 条打卡"
                "已读取云备份，本机记录没有改变"
            } },
            onRestore = { action = "restore" },
            onAutomaticChange = { enabled ->
                if (enabled) runAction {
                    clearReadBackup(); preview = coordinator.preview(); action = "automatic"
                    "请确认每天自动备份的时间和账号"
                } else runAction { coordinator.stop(false); clearReadBackup(); "自动备份已关闭，仍可手动备份" }
            },
            onTime = { showTime = true }, onSecurity = { showSecurity = true },
            onSignOut = { runAction { coordinator.stop(true); clearReadBackup(); "已退出云备份账号，本机记录保留" } },
            modifier = modifier,
        )
    } else {
        Surface(onClick = onOpenSettings, shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("云备份设置", style = MaterialTheme.typography.titleMedium)
                    Text(if (state?.backupEnabled == true) "每天 %02d:%02d 自动备份".format(minute / 60, minute % 60)
                        else "备份、恢复与自动备份时间", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("›", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (showTime) WheelTimePicker(minute, onDismiss = { showTime = false }, onConfirm = { selected ->
        showTime = false
        runAction { coordinator.setBackupTime(selected); "自动备份时间已设为每天 %02d:%02d".format(selected / 60, selected % 60) }
    }, title = "自动备份时间")
    if (showSecurity) AlertDialog(onDismissRequest = { showSecurity = false }, title = { Text("账号与安全") },
        text = { Text("密码由 AWS Cognito 管理。需要找回密码时，请在中文登录页选择“忘记密码”。退出登录会清除这部手机的登录状态并关闭自动备份，本机记录保留。") },
        confirmButton = { TextButton(enabled = !busy, onClick = { showSecurity = false; openLogin() }) { Text("打开登录页面") } },
        dismissButton = { TextButton(onClick = { showSecurity = false }) { Text("关闭") } })
    action?.let { selectedAction ->
        val selected = preview
        if (selected != null) {
            val snapshot = selected.backup?.let { BackupSnapshot.decode(it.body) }
            val warning = if (state?.boundAccountSub != null && state?.boundAccountSub != selected.account)
                "\n本机记录曾绑定其他账号，继续会保存到当前账号。" else ""
            val title = when (selectedAction) { "restore" -> "恢复到这部手机？"; "automatic" -> "开启每日自动备份？"; else -> "备份到云端？" }
            val description = when (selectedAction) {
                "restore" -> "将恢复 ${snapshot?.calendars?.size ?: 0} 个日历和 ${snapshot?.checkIns?.size ?: 0} 条打卡，并替换本机全部记录，无法自动合并。恢复后自动备份关闭。"
                "automatic" -> "每天 %02d:%02d（北京时间）上传这部手机的全部日历与打卡。".format(minute / 60, minute % 60) +
                    (if (snapshot == null) "当前账号还没有备份。" else "已有云备份将被本机记录替换。") + warning
                else -> "上传本机全部日历与打卡。" +
                    (if (snapshot == null) "当前账号还没有备份。" else "将替换 ${backupDisplayTime(snapshot.createdAt)} 的云备份（${snapshot.calendars.size} 个日历、${snapshot.checkIns.size} 条打卡）。") +
                    "自动备份开关保持当前选择。" + warning
            }
            AlertDialog(onDismissRequest = { if (!busy) action = null }, title = { Text(title) },
                text = { Text(description, modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(enabled = !busy, onClick = {
                    action = null
                    runAction {
                        when (selectedAction) {
                            "restore" -> { coordinator.restore(selected); clearReadBackup(); "恢复完成，已重新安排本地提醒" }
                            "automatic" -> { coordinator.enable(selected); clearReadBackup(); "每日自动备份已开启" }
                            else -> { val uploaded = coordinator.backupNow(selected); clearReadBackup()
                                if (uploaded) "备份完成" else "已上传一份备份，仍有新修改待备份" }
                        }
                    }
                }) { Text(when (selectedAction) { "restore" -> "确认恢复"; "automatic" -> "确认开启"; else -> "确认备份" }) } },
                dismissButton = { TextButton(enabled = !busy, onClick = { action = null }) { Text("取消") } })
        }
    }
}

internal fun backupDisplayTime(time: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Shanghai")).format(Instant.parse(time))
}.getOrDefault("时间暂不可用")
