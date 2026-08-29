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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AppThemeMode

val LocalAlmiUiScale = staticCompositionLocalOf { 1f }

/** Porcelain runway: warm editorial surfaces with a restrained digital-lavender signal color. */
private val LightColors = lightColorScheme(
    primary = Color(0xFF161821),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E9EF),
    onPrimaryContainer = Color(0xFF14161D),
    secondary = Color(0xFF6C6675),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE8F0),
    onSecondaryContainer = Color(0xFF29242D),
    tertiary = Color(0xFF6655E8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAE7FF),
    onTertiaryContainer = Color(0xFF211B65),
    background = Color(0xFFF6F4F1),
    onBackground = Color(0xFF15161A),
    surface = Color(0xFFFDFCFA),
    onSurface = Color(0xFF17181D),
    surfaceVariant = Color(0xFFF0EDF0),
    onSurfaceVariant = Color(0xFF6E6972),
    outline = Color(0xFFBEB9C0),
    outlineVariant = Color(0xFFE0DCE1),
    error = Color(0xFFD6544B),
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E4),
    onErrorContainer = Color(0xFF6B1510),
)

/** Obsidian showroom: deep graphite rather than pure black, tuned for 3D and image content. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF3F0EC),
    onPrimary = Color(0xFF111218),
    primaryContainer = Color(0xFF252733),
    onPrimaryContainer = Color(0xFFF5F2EF),
    secondary = Color(0xFFBBB2C4),
    onSecondary = Color(0xFF1D1920),
    secondaryContainer = Color(0xFF2C2730),
    onSecondaryContainer = Color(0xFFECE5F0),
    tertiary = Color(0xFFAAA0FF),
    onTertiary = Color(0xFF211A63),
    tertiaryContainer = Color(0xFF302A66),
    onTertiaryContainer = Color(0xFFE9E5FF),
    background = Color(0xFF080A0F),
    onBackground = Color(0xFFF4F1EE),
    surface = Color(0xFF12141B),
    onSurface = Color(0xFFF5F2EF),
    surfaceVariant = Color(0xFF1D2029),
    onSurfaceVariant = Color(0xFFB4B0B9),
    outline = Color(0xFF5C5E68),
    outlineVariant = Color(0xFF30323B),
    error = Color(0xFFFF776E),
    onError = Color(0xFF3D0704),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(15.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 35.sp,
        lineHeight = 39.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 29.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.55).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.30).sp,
    ),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = .04.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .20.sp),
    labelSmall = TextStyle(fontSize = 9.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .72.sp),
)

@Composable
fun AlmiTheme(themeMode: AppThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val configuration = LocalConfiguration.current
    val systemDensity = LocalDensity.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp

    // One adaptive boundary keeps all new and legacy screens compact without per-screen hacks.
    val densityScale = when {
        width < 350 || height < 650 -> .83f
        width < 380 || height < 720 -> .87f
        width < 420 || height < 800 -> .91f
        width < 450 || height < 880 -> .94f
        width < 600 -> .97f
        else -> 1f
    }
    val adaptiveDensity = Density(
        density = systemDensity.density * densityScale,
        fontScale = systemDensity.fontScale,
    )

    CompositionLocalProvider(
        LocalDensity provides adaptiveDensity,
        LocalAlmiUiScale provides 1f,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AlmiTypography,
            shapes = AlmiShapes,
            content = content,
        )
    }
}
