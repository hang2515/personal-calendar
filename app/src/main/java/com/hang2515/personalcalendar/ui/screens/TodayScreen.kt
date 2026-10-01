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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(today.format(dateFormatter), style = MaterialTheme.typography.headlineMedium)
        Text("坚持小小的习惯，遇见更好的自己", style = MaterialTheme.typography.bodyLarge)
        InfoCard("今天没有安排的事项", "创建坚持日历后，今天需要打卡的事项会显示在这里。")
    }
}
