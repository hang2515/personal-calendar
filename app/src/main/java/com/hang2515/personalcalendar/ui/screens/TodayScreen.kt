package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.domain.isScheduled
import com.hang2515.personalcalendar.ui.DatabaseState
import com.hang2515.personalcalendar.ui.components.InfoCard
import com.hang2515.personalcalendar.ui.components.CalendarGlyph
import com.hang2515.personalcalendar.ui.components.ProgressSummary
import com.hang2515.personalcalendar.ui.components.calendarPalette
import com.hang2515.personalcalendar.ui.databaseState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

@Composable
fun TodayScreen(repository: CalendarRepository, today: LocalDate, modifier: Modifier = Modifier) {
    val calendarsFlow = remember(repository) { repository.observeCalendars() }
    val checkInsFlow = remember(repository, today) { repository.observeCheckInsOn(today) }
    val schedulesFlow = remember(repository, today) { repository.observeSchedulesOn(today) }
    val calendars by databaseState(calendarsFlow)
    val checkIns by databaseState(checkInsFlow)
    val schedules by databaseState(schedulesFlow)
    val scope = rememberCoroutineScope()
    var savingId by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(today.format(dateFormatter), style = MaterialTheme.typography.headlineMedium)
                    Text("坚持小小的习惯，遇见更好的自己", style = MaterialTheme.typography.bodyMedium)
                }
                CalendarGlyph("leaf", "sand", size = 56.dp)
            }
        }
        when {
            calendars is DatabaseState.Loading || checkIns is DatabaseState.Loading || schedules is DatabaseState.Loading ->
                item { InfoCard("正在读取记录", "稍等片刻。") }
            calendars is DatabaseState.Failed || checkIns is DatabaseState.Failed || schedules is DatabaseState.Failed ->
                item { InfoCard("记录暂时无法读取", "请重新打开应用后再试。") }
            else -> {
                val allCalendars = (calendars as DatabaseState.Ready).value
                val completedIds = (checkIns as DatabaseState.Ready).value.map { it.calendarId }.toSet()
                val masks = (schedules as DatabaseState.Ready).value.associate { it.calendarId to it.weekdayMask }
                val scheduled = allCalendars.filter { isScheduled(today, it.startLocalDate(), masks[it.id] ?: 0) }
                if (scheduled.isEmpty()) {
                    item {
                        InfoCard(
                            if (allCalendars.isEmpty()) "今天没有安排的事项" else "今天没有需要打卡的日历",
                            if (allCalendars.isEmpty()) "在“日历”中创建坚持日历，今天的任务会显示在这里。" else "可以到“日历”查看其他日期的计划。",
                        )
                    }
                } else {
                    item {
                        ProgressSummary(
                            "今日完成情况",
                            completedIds.intersect(scheduled.map { it.id }.toSet()).size,
                            scheduled.size,
                        )
                    }
                    items(scheduled, key = { it.id }) { calendar ->
                        val palette = calendarPalette(calendar.colorKey)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = if (calendar.id in completedIds) palette.pale else MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    CalendarGlyph(calendar.iconKey, calendar.colorKey)
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(calendar.name, style = MaterialTheme.typography.titleLarge)
                                        if (calendar.reminderEnabled) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Text("%02d:%02d".format(calendar.reminderMinute / 60, calendar.reminderMinute % 60))
                                            }
                                        } else {
                                            Text(scheduleDescription(calendar.weekdayMask), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (calendar.id in completedIds) {
                                        Icon(Icons.Outlined.CheckCircle, contentDescription = "今天已完成", tint = palette.accent)
                                    }
                                }
                                if (calendar.id in completedIds) {
                                    Text("今天已完成", color = palette.accent, style = MaterialTheme.typography.labelLarge)
                                    TextButton(
                                        onClick = {
                                            savingId = calendar.id
                                            saveError = false
                                            scope.launch {
                                                try {
                                                    repository.undoCheckIn(calendar.id, today)
                                                } catch (_: Exception) {
                                                    saveError = true
                                                } finally {
                                                    savingId = null
                                                }
                                            }
                                        },
                                        enabled = savingId == null,
                                    ) { Text("撤销打卡") }
                                } else {
                                    Button(
                                        onClick = {
                                            savingId = calendar.id
                                            saveError = false
                                            scope.launch {
                                                try {
                                                    repository.checkIn(calendar.id, today)
                                                } catch (_: Exception) {
                                                    saveError = true
                                                } finally {
                                                    savingId = null
                                                }
                                            }
                                        },
                                        enabled = savingId == null,
                                        modifier = Modifier.fillMaxWidth().height(50.dp),
                                    ) { Text(if (savingId == calendar.id) "保存中…" else "完成打卡") }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (saveError) item { Text("打卡操作未完成，请重试。", color = MaterialTheme.colorScheme.error) }
    }
}
