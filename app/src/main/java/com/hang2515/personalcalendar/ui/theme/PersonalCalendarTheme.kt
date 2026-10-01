package com.hang2515.personalcalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WarmBackground = Color(0xFFF8F9F5)
val Sage = Color(0xFF5B9A70)
val DarkGreen = Color(0xFF17392C)
val SoftGreen = Color(0xFFEAF3E9)

private val colorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SoftGreen,
    onPrimaryContainer = DarkGreen,
    background = WarmBackground,
    onBackground = DarkGreen,
    surface = Color.White,
    onSurface = DarkGreen,
)

@Composable
fun PersonalCalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
