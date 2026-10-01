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

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("我的日历", style = MaterialTheme.typography.headlineMedium)
        Text("每个目标，都有属于自己的坚持记录。", style = MaterialTheme.typography.bodyLarge)
        InfoCard("还没有日历", "你创建的每个坚持日历都会单独显示在这里。")
    }
}
