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

/**
 * V12 Aurora: luminous, fashion-forward and deliberately free of black surfaces.
 * Light is an ice/glass studio. Dark is deep indigo/lavender rather than charcoal/black.
 */
private val AuroraLightColors = lightColorScheme(
    primary = Color(0xFF2359C4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDEBFF),
    onPrimaryContainer = Color(0xFF0A2A61),
    secondary = Color(0xFF8D4EC7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E5FF),
    onSecondaryContainer = Color(0xFF4D176C),
    tertiary = Color(0xFF00A7B7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9F8FA),
    onTertiaryContainer = Color(0xFF00545C),
    background = Color(0xFFF4FAFF),
    onBackground = Color(0xFF10233B),
    surface = Color(0xFFFDFEFF),
    onSurface = Color(0xFF10233B),
    surfaceVariant = Color(0xFFEAF4FF),
    onSurfaceVariant = Color(0xFF52677F),
    outline = Color(0xFFA9C2DA),
    outlineVariant = Color(0xFFD7E7F5),
    error = Color(0xFFE84D6A),
    onError = Color.White,
    errorContainer = Color(0xFFFFE7ED),
    onErrorContainer = Color(0xFF7A1930),
)

private val AuroraNightColors = darkColorScheme(
    primary = Color(0xFF9AC7FF),
    onPrimary = Color(0xFF153B73),
    primaryContainer = Color(0xFF315A94),
    onPrimaryContainer = Color(0xFFE8F2FF),
    secondary = Color(0xFFE0B9FF),
    onSecondary = Color(0xFF542B73),
    secondaryContainer = Color(0xFF745093),
    onSecondaryContainer = Color(0xFFFFF0FF),
    tertiary = Color(0xFF7BE7ED),
    onTertiary = Color(0xFF004F56),
    tertiaryContainer = Color(0xFF176E77),
    onTertiaryContainer = Color(0xFFE2FDFF),
    background = Color(0xFF151D3A),
    onBackground = Color(0xFFF0F6FF),
    surface = Color(0xFF20294C),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF2C3760),
    onSurfaceVariant = Color(0xFFD0D9F4),
    outline = Color(0xFF8594BD),
    outlineVariant = Color(0xFF44517D),
    error = Color(0xFFFF8DA2),
    onError = Color(0xFF6F122B),
    errorContainer = Color(0xFF8A3149),
    onErrorContainer = Color(0xFFFFE8EE),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(26.dp),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(48.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 38.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.1).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.55).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 25.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.05.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.25.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp),
)

@Composable
fun AlmiTheme(themeMode: AppThemeMode, content: @Composable () -> Unit) {
    val night = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val configuration = LocalConfiguration.current
    val systemDensity = LocalDensity.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val densityScale = when {
        width < 350 || height < 650 -> 0.84f
        width < 380 || height < 720 -> 0.88f
        width < 420 || height < 800 -> 0.92f
        width < 450 || height < 880 -> 0.95f
        width < 600 -> 0.97f
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
            colorScheme = if (night) AuroraNightColors else AuroraLightColors,
            typography = AlmiTypography,
            shapes = AlmiShapes,
            content = content,
        )
    }
}
