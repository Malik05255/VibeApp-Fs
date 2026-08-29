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
 * ALMI Atelier / Light — warm paper, ink and one restrained clay signal.
 * The palette is intentionally fashion-editorial rather than generic app-blue.
 */
private val AtelierLight = lightColorScheme(
    primary = Color(0xFF171411),
    onPrimary = Color(0xFFFFFAF4),
    primaryContainer = Color(0xFFE7DDD2),
    onPrimaryContainer = Color(0xFF171411),
    secondary = Color(0xFF736A62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE3DA),
    onSecondaryContainer = Color(0xFF2B2723),
    tertiary = Color(0xFFAA5738),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7DDD1),
    onTertiaryContainer = Color(0xFF56210F),
    background = Color(0xFFF3EEE7),
    onBackground = Color(0xFF171411),
    surface = Color(0xFFFCF8F3),
    onSurface = Color(0xFF171411),
    surfaceVariant = Color(0xFFECE4DC),
    onSurfaceVariant = Color(0xFF69615A),
    outline = Color(0xFFB9AEA4),
    outlineVariant = Color(0xFFDDD4CC),
    error = Color(0xFFB93A3F),
    onError = Color.White,
    errorContainer = Color(0xFFFADCDD),
    onErrorContainer = Color(0xFF61191D),
)

/** ALMI Atelier / Dark — black coffee, graphite and warm ivory. */
private val AtelierDark = darkColorScheme(
    primary = Color(0xFFF3ECE4),
    onPrimary = Color(0xFF171411),
    primaryContainer = Color(0xFF29241F),
    onPrimaryContainer = Color(0xFFF7F0E8),
    secondary = Color(0xFFC4B9AE),
    onSecondary = Color(0xFF221E1A),
    secondaryContainer = Color(0xFF302A25),
    onSecondaryContainer = Color(0xFFE9E0D7),
    tertiary = Color(0xFFD17A57),
    onTertiary = Color(0xFF351407),
    tertiaryContainer = Color(0xFF4B2719),
    onTertiaryContainer = Color(0xFFFFDDD0),
    background = Color(0xFF0C0B0A),
    onBackground = Color(0xFFF2ECE6),
    surface = Color(0xFF151311),
    onSurface = Color(0xFFF4EEE7),
    surfaceVariant = Color(0xFF211E1A),
    onSurfaceVariant = Color(0xFFBDB3AA),
    outline = Color(0xFF625B55),
    outlineVariant = Color(0xFF302C28),
    error = Color(0xFFFF7479),
    onError = Color(0xFF3B080B),
    errorContainer = Color(0xFF59191C),
    onErrorContainer = Color(0xFFFFDADB),
)

private val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(17.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AtelierTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 38.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.0).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.65).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-.35).sp,
    ),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = .02.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .18.sp),
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
    val scale = when {
        width < 350 || height < 650 -> .84f
        width < 380 || height < 720 -> .89f
        width < 420 || height < 800 -> .93f
        width < 450 || height < 880 -> .96f
        else -> 1f
    }

    CompositionLocalProvider(
        LocalDensity provides Density(systemDensity.density * scale, systemDensity.fontScale),
        LocalAlmiUiScale provides 1f,
    ) {
        MaterialTheme(
            colorScheme = if (dark) AtelierDark else AtelierLight,
            typography = AtelierTypography,
            shapes = AtelierShapes,
            content = content,
        )
    }
}
