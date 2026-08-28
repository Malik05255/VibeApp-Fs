package com.almi.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AlmiBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.30f),
        color = Color.Transparent,
    ) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6D4AFF), Color(0xFFFF7D69)),
                    start = Offset.Zero,
                    end = Offset(w, h),
                ),
                cornerRadius = CornerRadius(w * 0.30f),
            )

            val stroke = w * 0.075f
            val white = Color.White
            val hanger = Path().apply {
                moveTo(w * 0.50f, h * 0.31f)
                cubicTo(w * 0.50f, h * 0.18f, w * 0.68f, h * 0.19f, w * 0.64f, h * 0.34f)
                cubicTo(w * 0.62f, h * 0.40f, w * 0.54f, h * 0.42f, w * 0.50f, h * 0.45f)
                lineTo(w * 0.22f, h * 0.69f)
                lineTo(w * 0.78f, h * 0.69f)
                lineTo(w * 0.50f, h * 0.45f)
            }
            drawPath(
                path = hanger,
                color = white,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(
                color = white,
                radius = w * 0.055f,
                center = Offset(w * 0.79f, h * 0.25f),
            )
        }
    }
}

@Composable
fun AlmiWordmark(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AlmiBrandMark(size = if (compact) 36.dp else 42.dp)
        Box(contentAlignment = Alignment.CenterStart) {
            Text(
                text = "ALMI",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
