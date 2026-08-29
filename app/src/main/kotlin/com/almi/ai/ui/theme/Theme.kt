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
 * ALMI Quiet Editorial.
 *
 * The normal application is intentionally almost monochrome. Clothing, generated imagery and
 * avatar artwork carry the colour. Cobalt is reserved for navigation/AI state and red is reserved
 * for destructive/error/measurement state. The body-measurement lab owns its separate dark navy
 * technical palette so it can feel like an instrument rather than another settings page.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF111318),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9EAEC),
    onPrimaryContainer = Color(0xFF111318),
    secondary = Color(0xFF666B73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0F1F2),
    onSecondaryContainer = Color(0xFF202329),
    tertiary = Color(0xFF315BFF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7ECFF),
    onTertiaryContainer = Color(0xFF10216F),
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF111318),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFF0F0ED),
    onSurfaceVariant = Color(0xFF6D7076),
    outline = Color(0xFFB8BBC0),
    outlineVariant = Color(0xFFE2E3E4),
    error = Color(0xFFE5483E),
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E5),
    onErrorContainer = Color(0xFF6B1713),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF7F7F5),
    onPrimary = Color(0xFF111318),
    primaryContainer = Color(0xFF2A2D32),
    onPrimaryContainer = Color(0xFFF7F7F5),
    secondary = Color(0xFFB9BDC4),
    onSecondary = Color(0xFF1A1D21),
    secondaryContainer = Color(0xFF292C31),
    onSecondaryContainer = Color(0xFFE4E6EA),
    tertiary = Color(0xFF8BA5FF),
    onTertiary = Color(0xFF0D1A50),
    tertiaryContainer = Color(0xFF1D326E),
    onTertiaryContainer = Color(0xFFDDE4FF),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFF5F5F2),
    surface = Color(0xFF17191D),
    onSurface = Color(0xFFF5F5F2),
    surfaceVariant = Color(0xFF22252A),
    onSurfaceVariant = Color(0xFFB2B5BB),
    outline = Color(0xFF63666D),
    outlineVariant = Color(0xFF303339),
    error = Color(0xFFFF766D),
    onError = Color(0xFF3C0704),
    errorContainer = Color(0xFF5E1915),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 38.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.0).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
    ),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.05.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.28.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.55.sp),
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
