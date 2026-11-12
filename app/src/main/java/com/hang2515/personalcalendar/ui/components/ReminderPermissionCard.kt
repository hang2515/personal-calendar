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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hang2515.personalcalendar.reminders.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun ReminderPermissionCard(permissionRevision: Int, modifier: Modifier = Modifier, requestSetupOnEnter: Boolean = false) {
    val context = LocalContext.current
    val scheduler = remember(context) { ReminderScheduler.open(context) }
    val scope = rememberCoroutineScope()
    var requestRevision by remember { mutableIntStateOf(0) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var isChanging by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(requestSetupOnEnter) }
    var testRevision by remember { mutableIntStateOf(0) }
    fun openSettings(intent: Intent) {
        settingsError = null
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { settingsError = "无法打开该设置，请在手机的应用信息中找到个人日历并调整。" }
        catch (_: SecurityException) { settingsError = "系统未允许打开该设置，请在手机的应用信息中手动调整。" }
    }
    fun calendarIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
    fun appPermissionSettings() = openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri()))
    fun notificationSettings() {
        val calendarApp = context.packageManager.resolveActivity(calendarIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        if (calendarApp == null) { settingsError = "未找到系统日历应用，请先安装或启用系统日历。"; return }
        openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, calendarApp.activityInfo.packageName)
        })
    }
    fun exactSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
            })
        }
    }
    val requestCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        requestRevision++
        if (scheduler.canDelegateToCalendar()) scope.launch {
            try {
                scheduler.refreshAll()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("ReminderScheduler", "无法重新安排提醒：${error.javaClass.simpleName}")
            } finally { requestRevision++ }
        }
    }
    val canUseCalendar = remember(permissionRevision, requestRevision) { scheduler.canDelegateToCalendar() }
    val hasCalendarApp = remember(permissionRevision, requestRevision) {
        context.packageManager.resolveActivity(calendarIntent(), PackageManager.MATCH_DEFAULT_ONLY) != null
    }
    val calendarErrorFlow = remember(scheduler) { scheduler.observeCalendarSyncError() }
    val calendarError by calendarErrorFlow.collectAsState(initial = scheduler.calendarSyncError())
    val canBeExact = remember(permissionRevision, requestRevision) { scheduler.canScheduleExact() }
    val remindersEnabled = remember(permissionRevision, requestRevision) { scheduler.isNotificationsEnabled() }
    val alarmClockEnabled = remember(permissionRevision, requestRevision) { scheduler.isAlarmClockEnabled() }
    val testFlow = remember(scheduler) { scheduler.observeTestReminder() }
    val test by testFlow.collectAsState(initial = scheduler.testReminderState())
    val isTestOverdue = remember(test, permissionRevision, testRevision) {
        test?.let { it.postedAtMillis == 0L && System.currentTimeMillis() >= it.expectedAtMillis + 60_000 } == true
    }
    val isTestDue = remember(test, permissionRevision, testRevision) { test?.let { System.currentTimeMillis() >= it.expectedAtMillis } == true }
    fun requestCalendarAccess() = requestCalendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    fun startTest() {
        isChanging = true
        settingsError = null
        scope.launch {
            try { scheduler.scheduleTestReminder(); showSetup = false }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { settingsError = "测试提醒未写入系统日历，请检查日历授权后重试。" }
            finally { requestRevision++; isChanging = false }
        }
    }
    LaunchedEffect(test?.token, test?.postedAtMillis, permissionRevision) {
        val pending = test
        if (pending != null && pending.postedAtMillis == 0L) {
            delay((pending.expectedAtMillis - System.currentTimeMillis()).coerceAtLeast(0))
            testRevision++
            delay((pending.expectedAtMillis + 60_000 - System.currentTimeMillis()).coerceAtLeast(0))
            testRevision++
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("接收提醒通知", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = remindersEnabled, enabled = !isChanging, onCheckedChange = { enabled ->
                    isChanging = true
                    settingsError = null
                    scope.launch {
                        try {
                            scheduler.setNotificationsEnabled(enabled)
                            showSetup = enabled
                        }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { settingsError = if (!enabled && !scheduler.canDelegateToCalendar())
                            "请先恢复系统日历访问，再关闭提醒；已有代管事项尚未取消。" else "无法更新提醒设置，请重试。" }
                        finally { requestRevision++; isChanging = false }
                    }
                }, modifier = Modifier.semantics { contentDescription = "接收提醒通知" })
            }
            when {
                !remindersEnabled -> {
                    Text("已关闭所有日历提醒")
                    Text("各日历的提醒时间仍会保留，重新开启后恢复后续提醒。", style = MaterialTheme.typography.bodySmall)
                }
                !canUseCalendar -> {
                    Text("系统日历尚未授权，提醒还未完成代管。")
                    TextButton(onClick = ::requestCalendarAccess) { Text("开启日历授权") }
                    TextButton(onClick = ::appPermissionSettings) { Text("应用权限设置") }
                }
                !hasCalendarApp -> Text("未找到系统日历应用，无法代管提醒。")
                calendarError != null -> {
                    Text(calendarError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        scope.launch { scheduler.refreshAll(); requestRevision++ }
                    }) { Text("重试日历代管") }
                }
                alarmClockEnabled && !canBeExact -> {
                    Text("系统日历照常提醒；闹钟标识尚需允许准时提醒。")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        TextButton(onClick = ::exactSettings) { Text("允许准时提醒") }
                    }
                }
                else -> Text("系统日历访问已允许，请确认它的通知设置。")
            }
            Text("普通通知由系统日历离线发送。提醒会出现在“个人日历提醒”中，请在本应用修改；卸载前先关闭提醒。", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("闹钟提醒", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = alarmClockEnabled, enabled = !isChanging, onCheckedChange = { enabled ->
                    isChanging = true
                    settingsError = null
                    scope.launch {
                        try {
                            scheduler.setAlarmClockEnabled(enabled)
                            if (enabled) showSetup = true
                        } catch (error: CancellationException) { throw error }
                        catch (_: Exception) { settingsError = "提醒方式未保存，请重试。" }
                        finally { requestRevision++; isChanging = false }
                    }
                }, modifier = Modifier.semantics { contentDescription = "闹钟提醒" })
            }
            Text(if (alarmClockEnabled) "额外显示下一次系统闹钟标识，实际通知仍由系统日历发送；后续标识在打卡或打开应用时更新。"
                else "当前使用普通通知，不显示系统闹钟标识。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { requestRevision++; showSetup = true }, enabled = !isChanging) { Text("检查提醒设置") }
            if (remindersEnabled) {
                Text(when {
                    test == null -> "尚未验证后台提醒。"
                    test?.postedAtMillis?.let { it > 0 } == true -> "你已确认收到系统日历测试提醒。"
                    isTestOverdue -> "尚未确认收到，请检查系统日历通知设置后重试。"
                    else -> "测试已写入系统日历，请返回桌面或锁屏，约 1 分钟后检查。"
                }, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = ::startTest, enabled = !isChanging && canUseCalendar && hasCalendarApp) {
                    Text("后台提醒测试（1分钟）")
                }
                if (test?.delegated == true && test?.postedAtMillis == 0L) TextButton(enabled = !isChanging && isTestDue, onClick = {
                    val token = test?.token ?: return@TextButton
                    scope.launch {
                        try { scheduler.confirmTestReminder(token) }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { settingsError = "测试确认未保存，请重试。" }
                    }
                }) { Text("我已收到测试提醒") }
            }
            if (isChanging) Text("正在更新提醒设置…", style = MaterialTheme.typography.bodySmall)
            // 系统授权无论是否开启，都保留调整或关闭的入口。
            TextButton(onClick = ::notificationSettings, enabled = !isChanging) { Text("系统日历通知设置") }
            TextButton(onClick = { openSettings(calendarIntent()) }) { Text("打开系统日历") }
            settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (showSetup) AlertDialog(onDismissRequest = { showSetup = false }, title = { Text("设置提醒") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("普通提醒交给手机系统日历离线发送，应用完全退出后也无需由本应用接收定时广播。")
            Text(if (canUseCalendar) "系统日历访问：已允许。" else "需要读写日历权限，以创建和维护本应用专属的本地提醒；不读取其他事项，不关联账号上传。")
            if (!canUseCalendar) TextButton(onClick = ::appPermissionSettings) { Text("在系统设置中允许日历访问") }
            if (alarmClockEnabled) {
                Text(if (canBeExact) "下一次闹钟标识：已允许。" else "要显示下一次系统闹钟标识，还需允许“闹钟与提醒”。")
            }
            Text("请允许系统日历发送通知，并按需要设置声音、锁屏和悬浮显示。运行测试后返回桌面或锁屏，实际看到系统日历通知后再点“我已收到测试提醒”。无需开启本应用自启动。")
            TextButton(onClick = ::notificationSettings) { Text("检查系统日历通知") }
            if (!hasCalendarApp) Text("未找到系统日历应用，请先安装或启用它。", color = MaterialTheme.colorScheme.error)
            settingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = {
            TextButton(enabled = !isChanging && hasCalendarApp, onClick = {
                when {
                    !remindersEnabled -> {
                        isChanging = true
                        scope.launch {
                            try { scheduler.setNotificationsEnabled(true) }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { settingsError = "提醒未开启，请重试。" }
                            finally { requestRevision++; isChanging = false }
                        }
                    }
                    !canUseCalendar -> requestCalendarAccess()
                    alarmClockEnabled && !canBeExact -> exactSettings()
                    else -> startTest()
                }
            }) { Text(when {
                !remindersEnabled -> "开启提醒"
                !canUseCalendar -> "允许访问系统日历"
                alarmClockEnabled && !canBeExact -> "允许准时提醒"
                else -> "开始后台测试"
            }) }
        }, dismissButton = { TextButton(onClick = { showSetup = false }) { Text("稍后设置") } })
}
