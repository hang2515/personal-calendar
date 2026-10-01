package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.data.CalendarEntity
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.ui.DatabaseState
import com.hang2515.personalcalendar.ui.components.InfoCard
import com.hang2515.personalcalendar.ui.databaseState
import java.time.LocalDate

@Composable
fun CalendarScreen(
    repository: CalendarRepository,
    today: LocalDate,
    openCalendarId: String?,
    openRequest: Int,
    permissionRevision: Int,
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
            onCreate = { isCreating = true },
            onOpen = { selectedId = it.id },
            modifier = modifier,
        )
    }
}

@Composable
private fun CalendarListScreen(
    calendars: DatabaseState<List<CalendarEntity>>,
    onCreate: () -> Unit,
    onOpen: (CalendarEntity) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("我的日历", style = MaterialTheme.typography.headlineMedium)
                Text("每个目标，都有属于自己的坚持记录。")
            }
        }
        item { Button(onClick = onCreate) { Text("＋ 新建日历") } }
        when (calendars) {
            DatabaseState.Loading -> item { InfoCard("正在读取日历", "稍等片刻。") }
            DatabaseState.Failed -> item { InfoCard("日历暂时无法读取", "请重新打开应用后再试。") }
            is DatabaseState.Ready -> {
                if (calendars.value.isEmpty()) {
                    item { InfoCard("还没有日历", "创建一个属于自己的坚持日历，从今天开始记录。") }
                }
                items(calendars.value, key = { it.id }) { calendar ->
                    Card(onClick = { onOpen(calendar) }, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(calendar.name, style = MaterialTheme.typography.titleLarge)
                            Text(if (calendar.isPaused) "已暂停" else scheduleDescription(calendar.weekdayMask))
                            if (calendar.reminderEnabled) {
                                Text("提醒 %02d:%02d".format(calendar.reminderMinute / 60, calendar.reminderMinute % 60))
                            }
                            Text("查看月历 →", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

internal fun scheduleDescription(mask: Int): String {
    if (mask == 127) return "每天"
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    return "每周" + names.indices.filter { mask and (1 shl it) != 0 }.joinToString("、") { names[it] }
}
