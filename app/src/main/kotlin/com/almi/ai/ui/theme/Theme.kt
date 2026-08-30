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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AppThemeMode

val LocalAlmiUiScale = staticCompositionLocalOf { 1f }

/**
 * ALMI Spatial Editorial.
 *
 * The previous Aurora palette made every screen compete with cyan/pink/violet accents and also
 * scaled Compose density down on smaller devices. V12 now keeps native device density and uses a
 * quieter editorial system: porcelain / ink / electric-indigo with restrained warm and botanical
 * accents. Individual worlds should inherit these tokens instead of inventing their own palette.
 */
private val EditorialLight = lightColorScheme(
    primary = Color(0xFF4D63F5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7EAFF),
    onPrimaryContainer = Color(0xFF1D2A78),
    secondary = Color(0xFFB66F5C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF8E6DF),
    onSecondaryContainer = Color(0xFF623326),
    tertiary = Color(0xFF2C8B71),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDF3EA),
    onTertiaryContainer = Color(0xFF14513F),
    background = Color(0xFFF7F6F2),
    onBackground = Color(0xFF191A1F),
    surface = Color(0xFFFFFEFB),
    onSurface = Color(0xFF191A1F),
    surfaceVariant = Color(0xFFF0EEE8),
    onSurfaceVariant = Color(0xFF64615C),
    outline = Color(0xFFAAA69E),
    outlineVariant = Color(0xFFDEDAD1),
    error = Color(0xFFC94D5E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE5E9),
    onErrorContainer = Color(0xFF6D2430),
)

private val EditorialDark = darkColorScheme(
    primary = Color(0xFFA6B0FF),
    onPrimary = Color(0xFF20295F),
    primaryContainer = Color(0xFF303B78),
    onPrimaryContainer = Color(0xFFEEF0FF),
    secondary = Color(0xFFE4A18C),
    onSecondary = Color(0xFF5D2F23),
    secondaryContainer = Color(0xFF603D34),
    onSecondaryContainer = Color(0xFFFFECE5),
    tertiary = Color(0xFF7DD4B8),
    onTertiary = Color(0xFF123E33),
    tertiaryContainer = Color(0xFF214F43),
    onTertiaryContainer = Color(0xFFE3FFF5),
    background = Color(0xFF0F1014),
    onBackground = Color(0xFFF4F2EC),
    surface = Color(0xFF17191E),
    onSurface = Color(0xFFF4F2EC),
    surfaceVariant = Color(0xFF20232A),
    onSurfaceVariant = Color(0xFFC9C6BF),
    outline = Color(0xFF85827B),
    outlineVariant = Color(0xFF363840),
    error = Color(0xFFFF9CA8),
    onError = Color(0xFF5E1D27),
    errorContainer = Color(0xFF6F303A),
    onErrorContainer = Color(0xFFFFE9EC),
)

private val EditorialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(38.dp),
)

private val EditorialTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 42.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.45).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = .05.sp),
    labelMedium = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = .22.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .75.sp),
)

@Composable
fun AlmiTheme(themeMode: AppThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    CompositionLocalProvider(LocalAlmiUiScale provides 1f) {
        MaterialTheme(
            colorScheme = if (dark) EditorialDark else EditorialLight,
            typography = EditorialTypography,
            shapes = EditorialShapes,
            content = content,
        )
    }
}
