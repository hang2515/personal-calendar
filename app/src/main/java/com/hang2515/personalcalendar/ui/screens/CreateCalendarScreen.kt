package com.hang2515.personalcalendar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hang2515.personalcalendar.data.CalendarRepository
import com.hang2515.personalcalendar.data.CalendarEntity
import com.hang2515.personalcalendar.ui.components.ReminderPermissionCard
import com.hang2515.personalcalendar.ui.components.CalendarGlyph
import com.hang2515.personalcalendar.ui.components.calendarPalette
import com.hang2515.personalcalendar.ui.components.WheelTimePicker
import com.hang2515.personalcalendar.ui.components.BackButton
import com.hang2515.personalcalendar.ui.components.PageHeading
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
    permissionRevision: Int = 0,
) {
    var name by rememberSaveable(calendar?.id) { mutableStateOf(calendar?.name ?: "") }
    var isDaily by rememberSaveable(calendar?.id) { mutableStateOf(calendar?.weekdayMask == 127 || calendar == null) }
    var selectedWeekdays by rememberSaveable(calendar?.id) {
        mutableIntStateOf(calendar?.weekdayMask?.takeUnless { it == 127 } ?: 31)
    }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf(false) }
    var reminderMinute by rememberSaveable(calendar?.id) { mutableIntStateOf(calendar?.reminderMinute ?: 480) }
    var reminderEnabled by rememberSaveable(calendar?.id) { mutableStateOf(calendar?.reminderEnabled ?: false) }
    var iconKey by rememberSaveable(calendar?.id) { mutableStateOf(calendar?.iconKey ?: "leaf") }
    var colorKey by rememberSaveable(calendar?.id) { mutableStateOf(calendar?.colorKey ?: "sage") }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val mask = if (isDaily) 127 else selectedWeekdays
    val isNameValid = name.trim().isNotEmpty() && name.trim().length <= 40

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BackButton(onBack, if (calendar == null) "返回日历" else "返回详情")
        PageHeading(if (calendar == null) "新的坚持" else "编辑日历",
            if (calendar == null) "从一件小事开始，把每一天记下来。" else "调整今后的计划，保留过去的坚持。")
        FormCard {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; saveError = false },
                label = { Text("日历名称") },
                placeholder = { Text("例如：吃药、阅读或运动") },
                supportingText = { Text("${name.trim().length} / 40") },
                isError = name.trim().length > 40,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FormCard {
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
        FormCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("计划日提醒", style = MaterialTheme.typography.titleMedium)
                Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it }, modifier = Modifier.semantics { contentDescription = "计划日提醒" })
            }
            if (reminderEnabled) {
                Surface(onClick = { showTimePicker = true }, color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("提醒时间：%02d:%02d".format(reminderMinute / 60, reminderMinute % 60), style = MaterialTheme.typography.titleMedium)
                        Text("调整 ›", style = MaterialTheme.typography.labelLarge)
                    }
                }
                ReminderPermissionCard(permissionRevision)
            }
        }
        FormCard {
            Text("图标与颜色", style = MaterialTheme.typography.titleMedium)
            val icons = listOf("leaf" to "叶子", "fitness" to "运动", "book" to "阅读", "heart" to "爱心")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                icons.forEach { (key, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { iconKey = key },
                            shape = CircleShape,
                            border = if (iconKey == key) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.semantics {
                                contentDescription = "$label 图标"
                                selected = iconKey == key
                            },
                        ) { CalendarGlyph(key, colorKey, size = 52.dp) }
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val colors = listOf("sage", "mint", "blue", "lavender", "coral", "sand")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                FlowRow(modifier = Modifier.fillMaxWidth(), maxItemsInEachRow = if (maxWidth >= 308.dp) 6 else 3,
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { key ->
                        Surface(
                            onClick = { colorKey = key },
                            shape = CircleShape,
                            border = if (colorKey == key) BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground) else null,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = when (key) {
                                    "sage" -> "鼠尾草绿"
                                    "mint" -> "薄荷绿"
                                    "blue" -> "浅蓝色"
                                    "lavender" -> "薰衣草紫"
                                    "coral" -> "珊瑚粉"
                                    else -> "暖沙色"
                                }
                                selected = colorKey == key
                            },
                        ) {
                            Box(modifier = Modifier.background(calendarPalette(key).pale).padding(6.dp)
                                .background(calendarPalette(key).accent, CircleShape))
                        }
                    }
                }
            }
        }
        Text("预览效果", style = MaterialTheme.typography.titleMedium)
        val previewName = name.trim().ifEmpty { "我的坚持日历" }
        val previewSchedule = if (mask == 0) "请选择重复日期" else scheduleDescription(mask)
        val previewReminder = if (reminderEnabled) "%02d:%02d 提醒".format(reminderMinute / 60, reminderMinute % 60) else "提醒关闭"
        Card(
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "日历预览：$previewName，$previewSchedule，$previewReminder"
            },
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = calendarPalette(colorKey).pale),
        ) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CalendarGlyph(iconKey, colorKey, size = 56.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(previewName, style = MaterialTheme.typography.titleLarge)
                    Text(previewSchedule)
                    Text(previewReminder)
                }
            }
        }
        Text(if (calendar == null) "开始日期：${today}" else "今天已打卡时，新规则从明天开始。")
        Button(
            onClick = {
                isSaving = true
                saveError = false
                scope.launch {
                    try {
                        if (calendar == null) repository.createCalendar(name, mask, today, reminderMinute, reminderEnabled, iconKey, colorKey)
                        else repository.editCalendar(calendar.id, name, mask, today, reminderMinute, reminderEnabled, iconKey, colorKey)
                        onCreated()
                    } catch (_: Exception) {
                        saveError = true
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = isNameValid && mask != 0 && !isSaving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(if (isSaving) "正在保存…" else if (calendar == null) "创建日历" else "保存修改") }
        if (saveError) Text("日历未保存，请重试。", color = MaterialTheme.colorScheme.error)
    }
    if (showTimePicker) WheelTimePicker(reminderMinute, onDismiss = { showTimePicker = false }, onConfirm = {
        reminderMinute = it; showTimePicker = false
    })
}

@Composable
private fun FormCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
