package com.almi.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4FE9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E3FF),
    onPrimaryContainer = Color(0xFF19135F),
    secondary = Color(0xFF4F5D75),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF17191D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17191D),
    surfaceVariant = Color(0xFFF0F1F4),
    onSurfaceVariant = Color(0xFF646871),
    outline = Color(0xFFD7D9DF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCAC5FF),
    onPrimary = Color(0xFF292080),
    primaryContainer = Color(0xFF3F35A7),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFFBCC7DB),
    background = Color(0xFF0E1013),
    onBackground = Color(0xFFE8E9ED),
    surface = Color(0xFF17191D),
    onSurface = Color(0xFFE8E9ED),
    surfaceVariant = Color(0xFF22252B),
    onSurfaceVariant = Color(0xFFB9BDC6),
    outline = Color(0xFF3B3F47),
    error = Color(0xFFFFB4AB),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun AlmiTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        shapes = AlmiShapes,
        content = content,
    )
}
