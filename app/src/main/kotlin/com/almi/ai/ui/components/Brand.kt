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
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6D4AFF), Color(0xFFFF7D69)),
                    start = Offset.Zero,
                    end = Offset(this.size.width, this.size.height),
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(this.size.width * 0.30f),
            )

            val stroke = this.size.width * 0.075f
            val white = Color.White
            val hanger = Path().apply {
                moveTo(this@Canvas.size.width * 0.50f, this@Canvas.size.height * 0.31f)
                cubicTo(
                    this@Canvas.size.width * 0.50f,
                    this@Canvas.size.height * 0.18f,
                    this@Canvas.size.width * 0.68f,
                    this@Canvas.size.height * 0.19f,
                    this@Canvas.size.width * 0.64f,
                    this@Canvas.size.height * 0.34f,
                )
                cubicTo(
                    this@Canvas.size.width * 0.62f,
                    this@Canvas.size.height * 0.40f,
                    this@Canvas.size.width * 0.54f,
                    this@Canvas.size.height * 0.42f,
                    this@Canvas.size.width * 0.50f,
                    this@Canvas.size.height * 0.45f,
                )
                lineTo(this@Canvas.size.width * 0.22f, this@Canvas.size.height * 0.69f)
                lineTo(this@Canvas.size.width * 0.78f, this@Canvas.size.height * 0.69f)
                lineTo(this@Canvas.size.width * 0.50f, this@Canvas.size.height * 0.45f)
            }
            drawPath(
                path = hanger,
                color = white,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawCircle(
                color = white,
                radius = this.size.width * 0.055f,
                center = Offset(this.size.width * 0.79f, this.size.height * 0.25f),
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
