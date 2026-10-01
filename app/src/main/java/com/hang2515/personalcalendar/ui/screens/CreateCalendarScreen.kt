package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.data.CalendarEntity
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun CreateCalendarScreen(
    repository: CalendarRepository,
    today: LocalDate,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    calendar: CalendarEntity? = null,
) {
    var name by rememberSaveable(calendar?.id) { mutableStateOf(calendar?.name ?: "") }
    var isDaily by rememberSaveable(calendar?.id) { mutableStateOf(calendar?.weekdayMask == 127 || calendar == null) }
    var selectedWeekdays by rememberSaveable(calendar?.id) {
        mutableIntStateOf(calendar?.weekdayMask?.takeUnless { it == 127 } ?: 31)
    }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mask = if (isDaily) 127 else selectedWeekdays
    val isNameValid = name.trim().isNotEmpty() && name.trim().length <= 40

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        TextButton(onClick = onBack) { Text(if (calendar == null) "← 返回日历" else "← 返回详情") }
        Text(if (calendar == null) "新建坚持日历" else "编辑坚持日历", style = MaterialTheme.typography.headlineMedium)
        Text(if (calendar == null) "给想坚持的事一个名字，从今天开始记录。" else "修改名称和今后的重复日期，过去的记录会保留。")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; saveError = false },
            label = { Text("日历名称") },
            placeholder = { Text("例如：吃药、阅读或运动") },
            supportingText = { Text("1 至 40 个字符，可以和已有日历同名") },
            isError = name.trim().length > 40,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("重复日期", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = isDaily, onClick = { isDaily = true }, label = { Text("每天") })
                FilterChip(selected = !isDaily, onClick = { isDaily = false }, label = { Text("指定星期") })
            }
            if (!isDaily) {
                val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                for (rowIndex in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val indices = if (rowIndex == 0) 0..3 else 4..6
                        indices.forEach { index ->
                            val bit = 1 shl index
                            FilterChip(
                                selected = selectedWeekdays and bit != 0,
                                onClick = { selectedWeekdays = selectedWeekdays xor bit },
                                label = { Text(weekdayLabels[index]) },
                            )
                        }
                    }
                }
                if (selectedWeekdays == 0) Text("请至少选择一个星期", color = MaterialTheme.colorScheme.error)
            }
        }
        Text(if (calendar == null) "开始日期：${today}" else "今天已打卡时，新规则从明天开始。")
        Button(
            onClick = {
                isSaving = true
                saveError = false
                scope.launch {
                    try {
                        if (calendar == null) repository.createCalendar(name, mask, today)
                        else repository.editCalendar(calendar.id, name, mask, today)
                        onCreated()
                    } catch (_: Exception) {
                        saveError = true
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = isNameValid && mask != 0 && !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isSaving) "正在保存…" else if (calendar == null) "创建日历" else "保存修改") }
        if (saveError) Text("日历未保存，请重试。", color = MaterialTheme.colorScheme.error)
    }
}
