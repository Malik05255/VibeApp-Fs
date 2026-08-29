package com.almi.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AppThemeMode

/**
 * ALMI adaptive UI scale.
 *
 * Android dp already handles physical density; this value handles the second problem: available
 * viewport. Small / short phones need a slightly tighter product density, while normal and large
 * phones should not inflate controls simply because more pixels are available.
 */
val LocalAlmiUiScale = staticCompositionLocalOf { 1f }

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

private fun almiShapes(scale: Float) = Shapes(
    extraSmall = RoundedCornerShape((9f * scale).dp),
    small = RoundedCornerShape((13f * scale).dp),
    medium = RoundedCornerShape((18f * scale).dp),
    large = RoundedCornerShape((24f * scale).dp),
    extraLarge = RoundedCornerShape((30f * scale).dp),
)

private fun almiTypography(scale: Float): Typography {
    fun fs(value: Float) = (value * scale).sp

    return Typography(
        displaySmall = TextStyle(
            fontSize = fs(36f),
            lineHeight = fs(40f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.75).sp,
        ),
        headlineLarge = TextStyle(
            fontSize = fs(29f),
            lineHeight = fs(34f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.45).sp,
        ),
        headlineMedium = TextStyle(
            fontSize = fs(24f),
            lineHeight = fs(29f),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25).sp,
        ),
        headlineSmall = TextStyle(fontSize = fs(20f), lineHeight = fs(25f), fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = fs(18f), lineHeight = fs(23f), fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = fs(15f), lineHeight = fs(20f), fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = fs(15.5f), lineHeight = fs(23f), fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = fs(14f), lineHeight = fs(20f), fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = fs(12f), lineHeight = fs(17f), fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = fs(13f), lineHeight = fs(18f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.sp),
        labelMedium = TextStyle(fontSize = fs(11f), lineHeight = fs(15f), fontWeight = FontWeight.SemiBold, letterSpacing = 0.22.sp),
        labelSmall = TextStyle(fontSize = fs(10f), lineHeight = fs(14f), fontWeight = FontWeight.Bold, letterSpacing = 0.55.sp),
    )
}

@Composable
fun AlmiTheme(themeMode: AppThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val uiScale = when {
        width < 350 || height < 650 -> 0.86f
        width < 380 || height < 720 -> 0.91f
        width < 420 || height < 800 -> 0.96f
        else -> 1f
    }

    CompositionLocalProvider(LocalAlmiUiScale provides uiScale) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = almiTypography(uiScale),
            shapes = almiShapes(uiScale),
            content = content,
        )
    }
}
