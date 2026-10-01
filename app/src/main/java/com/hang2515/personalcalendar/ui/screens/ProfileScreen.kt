package com.hang2515.personalcalendar.ui.screens

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.ui.components.InfoCard
import com.hang2515.personalcalendar.ui.components.ReminderPermissionCard
import com.hang2515.personalcalendar.ui.components.CalendarGlyph

@Composable
fun ProfileScreen(permissionRevision: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CalendarGlyph("leaf", "sage", size = 64.dp)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("本机使用", style = MaterialTheme.typography.titleLarge)
                    Text("你的坚持记录保存在这部手机", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        InfoCard("隐私与数据", "当前未启用账号和备份。卸载应用或清除应用数据会失去本机记录。")
        InfoCard("日历时区", "北京时间（Asia/Shanghai）")
        Text("通知与提醒", style = MaterialTheme.typography.titleMedium)
        ReminderPermissionCard(permissionRevision)
    }
}
