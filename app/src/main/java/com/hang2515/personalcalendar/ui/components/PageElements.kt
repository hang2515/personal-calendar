package com.hang2515.personalcalendar.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.YearMonth

@Composable
fun PageHeading(title: String, subtitle: String, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun BackButton(onClick: () -> Unit, label: String = "返回日历") {
    FilledTonalIconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = label)
    }
}

@Composable
fun MonthNavigation(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "上个月") }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${month.monthValue}月", style = MaterialTheme.typography.titleLarge)
            Text("${month.year}年", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "下个月") }
    }
}

@Composable
fun CompletedCheckIn(onUndo: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text("今天已完成", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = onUndo, enabled = enabled, modifier = Modifier.semantics { contentDescription = "撤销打卡" },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text("撤销")
        }
    }
}
