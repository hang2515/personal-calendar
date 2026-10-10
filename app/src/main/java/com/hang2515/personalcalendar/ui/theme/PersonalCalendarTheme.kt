package com.hang2515.personalcalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val WarmBackground = Color(0xFFF7F8F2)
val Sage = Color(0xFF35664C)
val DarkGreen = Color(0xFF17392C)
val SoftGreen = Color(0xFFEAF3E9)

private val colorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SoftGreen,
    onPrimaryContainer = DarkGreen,
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0ECDD),
    onSecondaryContainer = DarkGreen,
    background = WarmBackground,
    onBackground = DarkGreen,
    surface = Color.White,
    onSurface = DarkGreen,
    surfaceVariant = Color(0xFFF0F3EC),
    onSurfaceVariant = Color(0xFF647166),
    outline = Color(0xFF768477),
    outlineVariant = Color(0xFFDFE5DB),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = WarmBackground,
    surfaceContainerHighest = Color.White,
)

@Composable
fun PersonalCalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        ),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
