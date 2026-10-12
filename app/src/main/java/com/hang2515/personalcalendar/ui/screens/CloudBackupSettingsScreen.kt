package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.ui.components.BackButton
import com.hang2515.personalcalendar.ui.components.PageHeading

internal data class BackupSettingsUiState(
    val signedIn: Boolean,
    val canUseCloud: Boolean,
    val automatic: Boolean,
    val minute: Int,
    val account: String?,
    val lastBackup: String?,
    val cloudSummary: String?,
    val canRestore: Boolean,
    val busy: Boolean,
    val message: String?,
    val configured: Boolean,
    val accountProblem: Boolean = false,
)

@Composable
internal fun CloudBackupSettingsScreen(
    state: BackupSettingsUiState,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onBackup: () -> Unit,
    onRead: () -> Unit,
    onRestore: () -> Unit,
    onAutomaticChange: (Boolean) -> Unit,
    onTime: () -> Unit,
    onSecurity: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BackButton(onBack, "返回我的")
        PageHeading("云备份设置", "把每一份坚持，安心保存")
        if (!state.signedIn) {
            Text(if (state.configured) "登录后可使用云备份，本机记录随时可用。" else "此安装包尚未配置云服务。")
            Button(onClick = onLogin, enabled = state.configured && !state.busy) { Text("登录 / 注册") }
        } else Text(state.account ?: "云备份账号", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BackupSettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.primary)
                Text("备份到云端", style = MaterialTheme.typography.titleLarge)
            }
            Text("把这部手机的全部日历和打卡保存到当前账号。", style = MaterialTheme.typography.bodyMedium)
            Text(state.lastBackup?.let { "最近成功备份：$it" } ?: "还没有成功备份记录",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onBackup, enabled = state.canUseCloud && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("立即备份") }
        }
        BackupSettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary)
                Text("从云端恢复", style = MaterialTheme.typography.titleLarge)
            }
            Text("先读取账号里的备份，确认内容后再恢复到这部手机。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onRead, enabled = state.canUseCloud && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("读取云备份") }
            state.cloudSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (state.canRestore) {
                Text("恢复会替换本机全部记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRestore, enabled = state.canUseCloud && !state.busy) { Text("恢复到这部手机") }
            }
        }
        BackupSettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("自动备份", style = MaterialTheme.typography.titleLarge)
                    Text(if (state.automatic) "已开启 · 每天备份一次" else "已关闭", style = MaterialTheme.typography.bodySmall)
                }
                Switch(state.automatic, onAutomaticChange, enabled = (state.automatic || state.canUseCloud) && !state.busy,
                    modifier = Modifier.semantics { contentDescription = "每日自动备份" })
            }
            Surface(onClick = onTime, enabled = !state.busy, color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "选择自动备份时间" }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每天 %02d:%02d".format(state.minute / 60, state.minute % 60), style = MaterialTheme.typography.titleMedium)
                        Text("北京时间", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("修改 ›", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("需要联网；省电限制或断网可能延后。手动备份不影响自动开关。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = onSecurity, enabled = !state.busy) { Text("账号与安全") }
        if (state.signedIn || state.accountProblem) TextButton(onClick = onSignOut, enabled = !state.busy) {
            Text(if (state.accountProblem) "清除本机登录状态" else "退出云备份账号")
        }
    }
}

@Composable
private fun BackupSettingCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
