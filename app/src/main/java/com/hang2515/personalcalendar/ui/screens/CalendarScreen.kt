package com.hang2515.personalcalendar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.data.CalendarEntity
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.ui.DatabaseState
import com.hang2515.personalcalendar.ui.components.InfoCard
import com.hang2515.personalcalendar.ui.components.CalendarGlyph
import com.hang2515.personalcalendar.ui.components.ProgressSummary
import com.hang2515.personalcalendar.ui.components.calendarPalette
import com.hang2515.personalcalendar.ui.components.PageHeading
import com.hang2515.personalcalendar.ui.components.MonthNavigation
import com.hang2515.personalcalendar.ui.databaseState
import com.hang2515.personalcalendar.domain.ScheduleChange
import com.hang2515.personalcalendar.domain.isScheduled
import com.hang2515.personalcalendar.domain.monthlyProgress
import com.hang2515.personalcalendar.domain.scheduleMaskOn
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    repository: CalendarRepository,
    today: LocalDate,
    openCalendarId: String?,
    openRequest: Int,
    permissionRevision: Int,
    onOpenRequestConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarsFlow = remember(repository) { repository.observeCalendars() }
    val calendars by databaseState(calendarsFlow)
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openRequest) {
        if (openCalendarId != null) {
            selectedId = openCalendarId
            isCreating = false
            isEditing = false
            onOpenRequestConsumed()
        }
    }
    BackHandler(enabled = isCreating || selectedId != null) {
        when {
            isCreating -> isCreating = false
            isEditing -> isEditing = false
            else -> selectedId = null
        }
    }

    when {
        isCreating -> CreateCalendarScreen(
            repository = repository,
            today = today,
            permissionRevision = permissionRevision,
            onBack = { isCreating = false },
            onCreated = { isCreating = false },
            modifier = modifier,
        )
        selectedId != null -> {
            val calendar = (calendars as? DatabaseState.Ready)?.value?.firstOrNull { it.id == selectedId }
            if (calendar != null) {
                if (isEditing) {
                    CreateCalendarScreen(
                        repository = repository,
                        today = today,
                        calendar = calendar,
                        permissionRevision = permissionRevision,
                        onBack = { isEditing = false },
                        onCreated = { isEditing = false },
                        modifier = modifier,
                    )
                } else {
                    CalendarDetailScreen(
                        calendar = calendar,
                        repository = repository,
                        today = today,
                        onBack = { selectedId = null },
                        onEdit = { isEditing = true },
                        onDeleted = { selectedId = null },
                        modifier = modifier,
                    )
                }
            } else {
                Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { selectedId = null }) { Text("← 返回日历") }
                    when (calendars) {
                        DatabaseState.Loading -> InfoCard("正在读取日历", "稍等片刻。")
                        DatabaseState.Failed -> InfoCard("日历暂时无法读取", "请重新打开应用后再试。")
                        is DatabaseState.Ready -> InfoCard("日历暂时无法打开", "请返回列表后重试。")
                    }
                }
            }
        }
        else -> CalendarListScreen(
            calendars = calendars,
            repository = repository,
            today = today,
            onCreate = { isCreating = true },
            onOpen = { selectedId = it.id },
            modifier = modifier,
        )
    }
}

@Composable
private fun CalendarListScreen(
    calendars: DatabaseState<List<CalendarEntity>>,
    repository: CalendarRepository,
    today: LocalDate,
    onCreate: () -> Unit,
    onOpen: (CalendarEntity) -> Unit,
    modifier: Modifier,
) {
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    val progressFlow = remember(repository, today) { repository.observeMonthlyProgress(today) }
    val progressByCalendar by databaseState(progressFlow)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageHeading("我的日历", "把坚持，慢慢变成日常") {
                FilledTonalIconButton(onClick = onCreate) { Icon(Icons.Outlined.Add, contentDescription = "新建日历") }
            }
        }
        when (calendars) {
            DatabaseState.Loading -> item { InfoCard("正在读取日历", "稍等片刻。") }
            DatabaseState.Failed -> item { InfoCard("日历暂时无法读取", "请重新打开应用后再试。") }
            is DatabaseState.Ready -> {
                if (calendars.value.isEmpty()) {
                    item { InfoCard("还没有日历", "创建一个属于自己的坚持日历，从今天开始记录。") }
                }
                if (calendars.value.isNotEmpty()) {
                    val selectedCalendar = calendars.value.firstOrNull { it.id == previewId } ?: calendars.value.first()
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(calendars.value, key = { it.id }) { calendar ->
                                val palette = calendarPalette(calendar.colorKey)
                                val isSelected = calendar.id == selectedCalendar.id
                                Card(
                                    onClick = { previewId = calendar.id },
                                    modifier = Modifier.width(144.dp).semantics { selected = isSelected },
                                    border = if (isSelected) BorderStroke(1.dp, palette.accent) else null,
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) palette.pale else MaterialTheme.colorScheme.surface),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CalendarGlyph(calendar.iconKey, calendar.colorKey, size = 52.dp)
                                        Text(calendar.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (calendar.isPaused) "已暂停" else scheduleDescription(calendar.weekdayMask),
                                            style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        val progress = (progressByCalendar as? DatabaseState.Ready)?.value?.get(calendar.id)
                                        Text(when {
                                            progress != null -> "本月 ${progress.completedDays} / ${progress.plannedDays} 天"
                                            progressByCalendar is DatabaseState.Failed -> "统计暂不可用"
                                            else -> "统计读取中…"
                                        }, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    item { CalendarListPreview(selectedCalendar, repository, today, onOpen = { onOpen(selectedCalendar) }) }
                }
            }
        }
    }
}

@Composable
private fun CalendarListPreview(
    calendar: CalendarEntity,
    repository: CalendarRepository,
    today: LocalDate,
    onOpen: () -> Unit,
) {
    var monthText by rememberSaveable(calendar.id) { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val checkInsFlow = remember(repository, calendar.id, month) { repository.observeCheckInsInMonth(calendar.id, month) }
    val changesFlow = remember(repository, calendar.id, month) { repository.observeChangesInMonth(calendar.id, month) }
    val checkIns by databaseState(checkInsFlow)
    val changes by databaseState(changesFlow)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MonthNavigation(month, onPrevious = { monthText = month.minusMonths(1).toString() }, onNext = { monthText = month.plusMonths(1).toString() })
            when {
                checkIns is DatabaseState.Loading || changes is DatabaseState.Loading -> Text("正在读取月历…")
                checkIns is DatabaseState.Failed || changes is DatabaseState.Failed -> Text("月历暂时无法读取")
                else -> {
                    val completedDates = (checkIns as DatabaseState.Ready).value.map { LocalDate.parse(it.localDate) }.toSet()
                    val ruleChanges = (changes as DatabaseState.Ready).value.map {
                        ScheduleChange(LocalDate.parse(it.effectiveDate), it.weekdayMask)
                    }
                    val progress = monthlyProgress(month, today, completedDates) { date ->
                        isScheduled(date, calendar.startLocalDate(), scheduleMaskOn(date, ruleChanges))
                    }
                    ProgressSummary("${calendar.name} · 本月", progress.completedDays, progress.plannedDays,
                        accentColor = calendarPalette(calendar.colorKey).accent,
                        containerColor = calendarPalette(calendar.colorKey).pale)
                    CalendarMonthGrid(month, today, calendar, ruleChanges, completedDates)
                    CalendarStatusLegend()
                }
            }
            TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("查看完整日历") }
        }
    }
}

internal fun scheduleDescription(mask: Int): String {
    if (mask == 127) return "每天"
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    return "每周" + names.indices.filter { mask and (1 shl it) != 0 }.joinToString("、") { names[it] }
}
