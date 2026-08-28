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
    primary = Color(0xFF6557D8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0EEFF),
    onPrimaryContainer = Color(0xFF28205F),
    secondary = Color(0xFF2F6F63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4F3EF),
    onSecondaryContainer = Color(0xFF123C34),
    tertiary = Color(0xFF278A73),
    onTertiary = Color.White,
    background = Color(0xFFFAFAFB),
    onBackground = Color(0xFF17171A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFF4F4F6),
    onSurfaceVariant = Color(0xFF6F6F78),
    outline = Color(0xFFDCDCE2),
    outlineVariant = Color(0xFFECECF0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFE9E7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC9C2FF),
    onPrimary = Color(0xFF312775),
    primaryContainer = Color(0xFF40358A),
    onPrimaryContainer = Color(0xFFF2F0FF),
    secondary = Color(0xFF9FD5C9),
    onSecondary = Color(0xFF12372F),
    secondaryContainer = Color(0xFF214E44),
    onSecondaryContainer = Color(0xFFD5F6EE),
    tertiary = Color(0xFF7ED8C1),
    onTertiary = Color(0xFF073A2F),
    background = Color(0xFF111114),
    onBackground = Color(0xFFF1F1F3),
    surface = Color(0xFF19191D),
    onSurface = Color(0xFFF1F1F3),
    surfaceVariant = Color(0xFF242429),
    onSurfaceVariant = Color(0xFFC4C3CA),
    outline = Color(0xFF44444C),
    outlineVariant = Color(0xFF303036),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7C1917),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
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
