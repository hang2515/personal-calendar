package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.data.CalendarEntity
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.domain.DayStatus
import com.hang2515.personalcalendar.domain.ScheduleChange
import com.hang2515.personalcalendar.domain.dayStatus
import com.hang2515.personalcalendar.domain.isScheduled
import com.hang2515.personalcalendar.domain.monthlyProgress
import com.hang2515.personalcalendar.domain.scheduleMaskOn
import com.hang2515.personalcalendar.ui.DatabaseState
import com.hang2515.personalcalendar.ui.components.InfoCard
import com.hang2515.personalcalendar.ui.databaseState
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun CalendarDetailScreen(
    calendar: CalendarEntity,
    repository: CalendarRepository,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var monthText by rememberSaveable(calendar.id) { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val checkInsFlow = remember(repository, calendar.id, month) { repository.observeCheckInsInMonth(calendar.id, month) }
    val changesFlow = remember(repository, calendar.id, month) { repository.observeChangesInMonth(calendar.id, month) }
    val checkIns by databaseState(checkInsFlow)
    val changes by databaseState(changesFlow)
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    var isChangingPause by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 返回日历") }
        Text(calendar.name, style = MaterialTheme.typography.headlineMedium)
        Text(if (calendar.isPaused) "已暂停 · 后续日期不安排打卡" else scheduleDescription(calendar.weekdayMask))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text("编辑日历") }
            TextButton(
                onClick = {
                    isChangingPause = true
                    saveError = false
                    scope.launch {
                        try {
                            repository.setPaused(calendar.id, !calendar.isPaused, today)
                        } catch (_: Exception) {
                            saveError = true
                        } finally {
                            isChangingPause = false
                        }
                    }
                },
                enabled = !isChangingPause,
            ) { Text(if (calendar.isPaused) "恢复日历" else "暂停日历") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹ 上月") }
            Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("下月 ›") }
        }
        when {
            checkIns is DatabaseState.Loading || changes is DatabaseState.Loading -> InfoCard("正在读取记录", "稍等片刻。")
            checkIns is DatabaseState.Failed || changes is DatabaseState.Failed -> InfoCard("记录暂时无法读取", "请重新打开应用后再试。")
            else -> {
                val completedDates = (checkIns as DatabaseState.Ready).value.map { LocalDate.parse(it.localDate) }.toSet()
                val ruleChanges = (changes as DatabaseState.Ready).value.map {
                    ScheduleChange(LocalDate.parse(it.effectiveDate), it.weekdayMask)
                }
                val progress = monthlyProgress(month, today, completedDates) { date ->
                    isScheduled(date, calendar.startLocalDate(), scheduleMaskOn(date, ruleChanges))
                }
                Text("本月已完成 ${progress.completedDays} / ${progress.plannedDays} 天", style = MaterialTheme.typography.titleMedium)
                MonthGrid(month, today, calendar, ruleChanges, completedDates)
                Text("✓ 已完成   · 待完成   × 未完成", style = MaterialTheme.typography.bodySmall)
                if (YearMonth.from(today) == month && isScheduled(today, calendar.startLocalDate(), scheduleMaskOn(today, ruleChanges))) {
                    if (today in completedDates) {
                        InfoCard("今天已完成", "这一天的坚持已经记录。")
                    } else {
                        Button(
                            onClick = {
                                isSaving = true
                                saveError = false
                                scope.launch {
                                    try {
                                        repository.checkIn(calendar.id, today)
                                    } catch (_: Exception) {
                                        saveError = true
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (isSaving) "正在保存…" else "完成今日打卡") }
                    }
                }
            }
        }
        if (saveError) Text("打卡未保存，请重试。", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    calendar: CalendarEntity,
    changes: List<ScheduleChange>,
    completedDates: Set<LocalDate>,
) {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays.forEach { label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val leading = month.atDay(1).dayOfWeek.value - 1
    val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    for (week in 0 until cellCount / 7) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (weekday in 0..6) {
                val day = week * 7 + weekday - leading + 1
                if (day !in 1..month.lengthOfMonth()) {
                    Box(modifier = Modifier.weight(1f).height(54.dp))
                } else {
                    val date = month.atDay(day)
                    val status = dayStatus(date, today, calendar.startLocalDate(), scheduleMaskOn(date, changes), date in completedDates)
                    val background = when (status) {
                        DayStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                        DayStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
                        DayStatus.DUE -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val statusText = when (status) {
                        DayStatus.COMPLETED -> "已完成"
                        DayStatus.MISSED -> "未完成"
                        DayStatus.DUE -> "今天待完成"
                        DayStatus.UPCOMING -> "未来待完成"
                        DayStatus.UNSCHEDULED -> "未安排"
                    }
                    Surface(
                        modifier = Modifier.weight(1f).height(54.dp).semantics { contentDescription = "${date}，$statusText" },
                        color = background,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(day.toString(), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when (status) {
                                    DayStatus.COMPLETED -> "✓"
                                    DayStatus.MISSED -> "×"
                                    DayStatus.DUE -> "·"
                                    else -> " "
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
