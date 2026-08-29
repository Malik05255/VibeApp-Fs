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
 * ALMI v8 — Atelier UI.
 *
 * The product shell is deliberately quiet: soft paper surfaces, graphite typography and one
 * electric-blue action colour. The body map owns a separate dark clinical palette because it is
 * an instrument, not a normal application page.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF111318),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9EBEF),
    onPrimaryContainer = Color(0xFF111318),
    secondary = Color(0xFF69707D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEDF1),
    onSecondaryContainer = Color(0xFF252932),
    tertiary = Color(0xFF4B67FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7EBFF),
    onTertiaryContainer = Color(0xFF16245C),
    background = Color(0xFFF4F5F7),
    onBackground = Color(0xFF111318),
    surface = Color(0xFFFCFCFD),
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF686E78),
    outline = Color(0xFFB9BEC7),
    outlineVariant = Color(0xFFDEE1E6),
    error = Color(0xFFEB554B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E5),
    onErrorContainer = Color(0xFF6B1713),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF6F7F9),
    onPrimary = Color(0xFF111318),
    primaryContainer = Color(0xFF242831),
    onPrimaryContainer = Color(0xFFF6F7F9),
    secondary = Color(0xFFB7BDC8),
    onSecondary = Color(0xFF171A20),
    secondaryContainer = Color(0xFF242831),
    onSecondaryContainer = Color(0xFFE4E7EC),
    tertiary = Color(0xFF91A2FF),
    onTertiary = Color(0xFF101A4B),
    tertiaryContainer = Color(0xFF283768),
    onTertiaryContainer = Color(0xFFE1E6FF),
    background = Color(0xFF090B0F),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF12151A),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF1D2128),
    onSurfaceVariant = Color(0xFFADB3BE),
    outline = Color(0xFF5D6470),
    outlineVariant = Color(0xFF2C3038),
    error = Color(0xFFFF786F),
    onError = Color(0xFF3D0704),
    errorContainer = Color(0xFF5E1915),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 42.sp,
        lineHeight = 45.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 25.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.45).sp,
    ),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.08.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.34.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.75.sp),
)

@Composable
fun AlmiTheme(themeMode: AppThemeMode, content: @Composable () -> Unit) {
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
