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

/**
 * ALMI Precision Atelier.
 * A neutral editorial base keeps clothing and body imagery dominant; signal red is reserved for
 * interaction, measurement hotspots and active system state.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFFD83B32),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5DDD8),
    onPrimaryContainer = Color(0xFF4A1612),
    secondary = Color(0xFF5E6461),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E8E4),
    onSecondaryContainer = Color(0xFF1B1F1D),
    tertiary = Color(0xFF25725F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDEEE8),
    onTertiaryContainer = Color(0xFF123C32),
    background = Color(0xFFF2F0EA),
    onBackground = Color(0xFF171816),
    surface = Color(0xFFFBFAF6),
    onSurface = Color(0xFF171816),
    surfaceVariant = Color(0xFFE9E7E1),
    onSurfaceVariant = Color(0xFF646660),
    outline = Color(0xFFB7B6B0),
    outlineVariant = Color(0xFFD9D7D0),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF6D62),
    onPrimary = Color(0xFF2F0503),
    primaryContainer = Color(0xFF5C1F1B),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFBFC5C0),
    onSecondary = Color(0xFF252A27),
    secondaryContainer = Color(0xFF343936),
    onSecondaryContainer = Color(0xFFE1E7E2),
    tertiary = Color(0xFF72D4B9),
    onTertiary = Color(0xFF07382C),
    tertiaryContainer = Color(0xFF174E40),
    onTertiaryContainer = Color(0xFFB7F5E3),
    background = Color(0xFF0D0E0C),
    onBackground = Color(0xFFF1F0EA),
    surface = Color(0xFF151614),
    onSurface = Color(0xFFF4F2EC),
    surfaceVariant = Color(0xFF222421),
    onSurfaceVariant = Color(0xFFB6B8B2),
    outline = Color(0xFF60635E),
    outlineVariant = Color(0xFF353733),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7A1B18),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(5.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 40.sp,
        lineHeight = 43.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.9).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.65).sp,
    ),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.12.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.42.sp),
    labelSmall = TextStyle(fontSize = 9.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.85.sp),
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
