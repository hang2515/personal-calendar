package com.hang2515.personalcalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 只在确认时提交；滚动和取消不会修改表单里的提醒时间。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelTimePicker(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit, title: String = "提醒时间") {
    val hours = rememberLazyListState(initialFirstVisibleItemIndex = initialMinute / 60)
    val minutes = rememberLazyListState(initialFirstVisibleItemIndex = initialMinute % 60)
    val hour by remember { derivedStateOf { hours.centeredValue(initialMinute / 60) } }
    val minute by remember { derivedStateOf { minutes.centeredValue(initialMinute % 60) } }
    val rowHeight = 48.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PageHeading(title, "上下滑动选择 · 北京时间")
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxWidth().height(rowHeight).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TimeWheel("小时", 24, hours, hour, rowHeight, Modifier.weight(1f))
                    Text(":", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    TimeWheel("分钟", 60, minutes, minute, rowHeight, Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = { onConfirm(hour * 60 + minute) },
                    enabled = !hours.isScrollInProgress && !minutes.isScrollInProgress,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("确定 %02d:%02d".format(hour, minute)) }
            }
        }
    }
}

private fun LazyListState.centeredValue(fallback: Int): Int {
    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: fallback
}

@Composable
private fun TimeWheel(label: String, count: Int, state: LazyListState, value: Int, rowHeight: Dp, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    // 上下留白让 00 和最后一个值也能落在中间，停下时自动吸附到完整的一行。
    LazyColumn(state = state, flingBehavior = rememberSnapFlingBehavior(state),
        modifier = modifier.height(rowHeight * 5).semantics {
            contentDescription = label
            stateDescription = "%02d".format(value)
            progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..(count - 1).toFloat(), count - 2)
            setProgress { target ->
                scope.launch { state.animateScrollToItem(target.roundToInt().coerceIn(0, count - 1)) }; true
            }
        }, contentPadding = PaddingValues(vertical = rowHeight * 2), horizontalAlignment = Alignment.CenterHorizontally) {
        items((0 until count).toList(), key = { it }) { number ->
            Box(Modifier.fillMaxWidth().height(rowHeight).clickable { scope.launch { state.animateScrollToItem(number) } }
                .semantics { selected = number == value }, contentAlignment = Alignment.Center) {
                Text("%02d".format(number), fontSize = if (number == value) 28.sp else 22.sp,
                    fontWeight = if (number == value) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (number == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f))
            }
        }
    }
}
