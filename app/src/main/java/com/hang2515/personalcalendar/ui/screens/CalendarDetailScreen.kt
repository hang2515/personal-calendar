package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.hang2515.personalcalendar.ui.components.CalendarGlyph
import com.hang2515.personalcalendar.ui.components.ProgressSummary
import com.hang2515.personalcalendar.ui.components.calendarPalette
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
    onDeleted: () -> Unit,
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
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val palette = calendarPalette(calendar.colorKey)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TextButton(onClick = onBack) { Text("← 返回日历") }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CalendarGlyph(calendar.iconKey, calendar.colorKey, size = 70.dp)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(calendar.name, style = MaterialTheme.typography.headlineMedium)
                Text(if (calendar.isPaused) "已暂停 · 后续日期不安排打卡" else scheduleDescription(calendar.weekdayMask))
                if (calendar.reminderEnabled) {
                    Text("计划日 %02d:%02d 提醒".format(calendar.reminderMinute / 60, calendar.reminderMinute % 60))
                }
            }
        }
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
        TextButton(onClick = { showDeleteConfirmation = true }) { Text("删除日历", color = MaterialTheme.colorScheme.error) }
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
                ProgressSummary("本月完成情况", progress.completedDays, progress.plannedDays, accentColor = palette.accent, containerColor = palette.pale)
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CalendarMonthGrid(month, today, calendar, ruleChanges, completedDates)
                    }
                }
                CalendarStatusLegend()
                if (YearMonth.from(today) == month && isScheduled(today, calendar.startLocalDate(), scheduleMaskOn(today, ruleChanges))) {
                    if (today in completedDates) {
                        InfoCard("今天已完成", "这一天的坚持已经记录。")
                        TextButton(
                            onClick = {
                                isSaving = true
                                saveError = false
                                scope.launch {
                                    try {
                                        repository.undoCheckIn(calendar.id, today)
                                    } catch (_: Exception) {
                                        saveError = true
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving,
                        ) { Text("撤销今日打卡") }
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
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) { Text(if (isSaving) "正在保存…" else "完成今日打卡") }
                    }
                }
            }
        }
        if (saveError) Text("操作未完成，请重试。", color = MaterialTheme.colorScheme.error)
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmation = false },
            title = { Text("删除“${calendar.name}”？") },
            text = { Text("这会永久删除这个日历、历史规则和全部打卡记录，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            try {
                                if (repository.deleteCalendar(calendar.id)) {
                                    showDeleteConfirmation = false
                                    onDeleted()
                                } else {
                                    saveError = true
                                }
                            } catch (_: Exception) {
                                saveError = true
                            } finally {
                                isDeleting = false
                                showDeleteConfirmation = false
                            }
                        }
                    },
                    enabled = !isDeleting,
                ) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }, enabled = !isDeleting) { Text("取消") } },
        )
    }
}

@Composable
internal fun CalendarStatusLegend() {
    Text("✓ 已完成　× 未完成　浅色：今天待完成\n描边：未来待完成　空白：未安排", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun CalendarMonthGrid(
    month: YearMonth,
    today: LocalDate,
    calendar: CalendarEntity,
    changes: List<ScheduleChange>,
    completedDates: Set<LocalDate>,
) {
    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")
    val palette = calendarPalette(calendar.colorKey)
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdays.forEach { label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    for (week in 0 until cellCount / 7) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (weekday in 0..6) {
                val day = week * 7 + weekday - leading + 1
                if (day !in 1..month.lengthOfMonth()) {
                    Box(modifier = Modifier.weight(1f).height(52.dp))
                } else {
                    val date = month.atDay(day)
                    val status = dayStatus(date, today, calendar.startLocalDate(), scheduleMaskOn(date, changes), date in completedDates)
                    val background = when (status) {
                        DayStatus.COMPLETED -> palette.accent
                        DayStatus.MISSED -> MaterialTheme.colorScheme.errorContainer
                        DayStatus.DUE -> palette.pale
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val statusText = when (status) {
                        DayStatus.COMPLETED -> "已完成"
                        DayStatus.MISSED -> "未完成"
                        DayStatus.DUE -> "今天待完成"
                        DayStatus.UPCOMING -> "未来待完成"
                        DayStatus.UNSCHEDULED -> "未安排"
                    }
                    Box(modifier = Modifier.weight(1f).height(52.dp).semantics { contentDescription = "${date}，$statusText" }, contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            color = background,
                            shape = CircleShape,
                            border = if (status == DayStatus.UPCOMING) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(
                                    day.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (status == DayStatus.COMPLETED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (status == DayStatus.COMPLETED || status == DayStatus.MISSED) {
                                    Text(if (status == DayStatus.COMPLETED) "✓" else "×", style = MaterialTheme.typography.labelSmall,
                                        color = if (status == DayStatus.COMPLETED) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
