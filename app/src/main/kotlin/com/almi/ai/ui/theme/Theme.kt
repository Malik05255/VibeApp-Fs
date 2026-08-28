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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D4AFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEDE8FF),
    onPrimaryContainer = Color(0xFF251763),
    secondary = Color(0xFFFF7D69),
    onSecondary = Color(0xFF43120A),
    secondaryContainer = Color(0xFFFFE3DE),
    onSecondaryContainer = Color(0xFF6A1D12),
    tertiary = Color(0xFF2FAF91),
    onTertiary = Color.White,
    background = Color(0xFFFCFAF8),
    onBackground = Color(0xFF1D1A22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1A22),
    surfaceVariant = Color(0xFFF4F0F5),
    onSurfaceVariant = Color(0xFF6C6671),
    outline = Color(0xFFE1DCE4),
    outlineVariant = Color(0xFFEDE8EF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9BCFF),
    onPrimary = Color(0xFF35247E),
    primaryContainer = Color(0xFF4C37B8),
    onPrimaryContainer = Color(0xFFF0EBFF),
    secondary = Color(0xFFFFB4A7),
    onSecondary = Color(0xFF652116),
    secondaryContainer = Color(0xFF7E3125),
    onSecondaryContainer = Color(0xFFFFDAD3),
    tertiary = Color(0xFF79DDBF),
    onTertiary = Color(0xFF00382C),
    background = Color(0xFF121014),
    onBackground = Color(0xFFF0EDF1),
    surface = Color(0xFF1B181D),
    onSurface = Color(0xFFF0EDF1),
    surfaceVariant = Color(0xFF29252D),
    onSurfaceVariant = Color(0xFFC8C1CB),
    outline = Color(0xFF4D4751),
    outlineVariant = Color(0xFF37323B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
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
        typography = AlmiTypography,
        shapes = AlmiShapes,
        content = content,
    )
}
