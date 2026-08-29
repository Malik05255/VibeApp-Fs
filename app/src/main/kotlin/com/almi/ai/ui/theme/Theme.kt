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

/** ALMI Eclipse: fashion-tech rather than generic Material colors. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6750F4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FF),
    onPrimaryContainer = Color(0xFF24165D),
    secondary = Color(0xFFB83FE5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7E7FF),
    onSecondaryContainer = Color(0xFF4D1262),
    tertiary = Color(0xFF15977D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF8F0),
    onTertiaryContainer = Color(0xFF0B4438),
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF12131B),
    surface = Color(0xFFFDFDFF),
    onSurface = Color(0xFF12131B),
    surfaceVariant = Color(0xFFF0F0F7),
    onSurfaceVariant = Color(0xFF656777),
    outline = Color(0xFFCACAD7),
    outlineVariant = Color(0xFFE4E3EC),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9C82FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B205B),
    onPrimaryContainer = Color(0xFFF2EDFF),
    secondary = Color(0xFFE061F2),
    onSecondary = Color(0xFF35003E),
    secondaryContainer = Color(0xFF4A1554),
    onSecondaryContainer = Color(0xFFFFD6FF),
    tertiary = Color(0xFF57D9BA),
    onTertiary = Color(0xFF00382C),
    tertiaryContainer = Color(0xFF0E4438),
    onTertiaryContainer = Color(0xFFB7F5E3),
    background = Color(0xFF070811),
    onBackground = Color(0xFFF5F3FF),
    surface = Color(0xFF10111B),
    onSurface = Color(0xFFF6F3FF),
    surfaceVariant = Color(0xFF191B29),
    onSurfaceVariant = Color(0xFFB8B8C8),
    outline = Color(0xFF4B4A60),
    outlineVariant = Color(0xFF2A293A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7A1B18),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp),
    headlineLarge = TextStyle(fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
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
