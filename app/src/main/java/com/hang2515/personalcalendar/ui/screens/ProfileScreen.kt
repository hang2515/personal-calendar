package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.ui.components.InfoCard
import com.hang2515.personalcalendar.ui.components.ReminderPermissionCard

@Composable
fun ProfileScreen(permissionRevision: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        InfoCard("本机记录", "日历与打卡目前只保存在这部手机，卸载应用或清除数据会失去记录。")
        ReminderPermissionCard(permissionRevision)
    }
}
