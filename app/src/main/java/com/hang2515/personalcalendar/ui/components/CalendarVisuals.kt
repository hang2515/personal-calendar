package com.hang2515.personalcalendar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CalendarPalette(val accent: Color, val pale: Color)

fun calendarPalette(colorKey: String): CalendarPalette = when (colorKey) {
    "mint" -> CalendarPalette(Color(0xFF529879), Color(0xFFDCEEE3))
    "blue" -> CalendarPalette(Color(0xFF5D8EAD), Color(0xFFDCEBF5))
    "lavender" -> CalendarPalette(Color(0xFF9077AD), Color(0xFFECE5F3))
    "coral" -> CalendarPalette(Color(0xFFC97568), Color(0xFFF9E2DB))
    "sand" -> CalendarPalette(Color(0xFFAD8B5A), Color(0xFFF6EBD7))
    else -> CalendarPalette(Color(0xFF4F8B64), Color(0xFFDDEBDD))
}

@Composable
fun CalendarGlyph(iconKey: String, colorKey: String, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val palette = calendarPalette(colorKey)
    Box(
        modifier = modifier.size(size).background(palette.pale, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.55f)) {
            scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
                when (iconKey) {
                    "fitness" -> {
                        drawLine(palette.accent, Offset(7f, 12f), Offset(17f, 12f), strokeWidth = 2f)
                        drawLine(palette.accent, Offset(5f, 7f), Offset(5f, 17f), strokeWidth = 3f, cap = StrokeCap.Round)
                        drawLine(palette.accent, Offset(8f, 9f), Offset(8f, 15f), strokeWidth = 2f, cap = StrokeCap.Round)
                        drawLine(palette.accent, Offset(16f, 9f), Offset(16f, 15f), strokeWidth = 2f, cap = StrokeCap.Round)
                        drawLine(palette.accent, Offset(19f, 7f), Offset(19f, 17f), strokeWidth = 3f, cap = StrokeCap.Round)
                    }
                    "book" -> {
                        val book = Path().apply {
                            moveTo(3f, 5f)
                            cubicTo(7f, 3f, 10f, 4f, 12f, 6f)
                            cubicTo(14f, 4f, 17f, 3f, 21f, 5f)
                            lineTo(21f, 19f)
                            cubicTo(17f, 17f, 14f, 18f, 12f, 20f)
                            cubicTo(10f, 18f, 7f, 17f, 3f, 19f)
                            close()
                        }
                        drawPath(book, palette.accent, style = Stroke(width = 1.7f))
                        drawLine(palette.accent, Offset(12f, 6f), Offset(12f, 20f), strokeWidth = 1.7f)
                    }
                    "heart" -> {
                        val heart = Path().apply {
                            moveTo(12f, 21f)
                            cubicTo(9f, 18f, 2f, 13f, 2f, 8f)
                            cubicTo(2f, 2f, 9f, 1f, 12f, 6f)
                            cubicTo(15f, 1f, 22f, 2f, 22f, 8f)
                            cubicTo(22f, 13f, 15f, 18f, 12f, 21f)
                            close()
                        }
                        drawPath(heart, palette.accent, style = Stroke(width = 1.7f))
                    }
                    else -> {
                        val leaf = Path().apply {
                            moveTo(4f, 20f)
                            cubicTo(4f, 10f, 10f, 4f, 21f, 3f)
                            cubicTo(21f, 14f, 15f, 20f, 4f, 20f)
                            close()
                        }
                        drawPath(leaf, palette.accent, style = Stroke(width = 1.7f))
                        drawLine(palette.accent, Offset(4f, 20f), Offset(16f, 9f), strokeWidth = 1.7f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}
