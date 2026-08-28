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
    primary = Color(0xFF5F56E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECEAFF),
    onPrimaryContainer = Color(0xFF241D68),
    secondary = Color(0xFF6D7D9B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EEF7),
    onSecondaryContainer = Color(0xFF25364F),
    tertiary = Color(0xFF2EA98A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4F8F1),
    onTertiaryContainer = Color(0xFF12493C),
    background = Color(0xFFF3F7FB),
    onBackground = Color(0xFF171A22),
    surface = Color(0xFFFDFEFF),
    onSurface = Color(0xFF171A22),
    surfaceVariant = Color(0xFFF1F4F8),
    onSurfaceVariant = Color(0xFF687181),
    outline = Color(0xFFD9DFE8),
    outlineVariant = Color(0xFFE8EDF3),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFE9E7),
    onErrorContainer = Color(0xFF5C130F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBB4FF),
    onPrimary = Color(0xFF2D2478),
    primaryContainer = Color(0xFF3A3286),
    onPrimaryContainer = Color(0xFFF0EEFF),
    secondary = Color(0xFFB9C8E2),
    onSecondary = Color(0xFF24334B),
    secondaryContainer = Color(0xFF34435A),
    onSecondaryContainer = Color(0xFFE9EFF8),
    tertiary = Color(0xFF79DCC1),
    onTertiary = Color(0xFF073B31),
    tertiaryContainer = Color(0xFF174C40),
    onTertiaryContainer = Color(0xFFDAF7EE),
    background = Color(0xFF0D1016),
    onBackground = Color(0xFFF0F2F6),
    surface = Color(0xFF151922),
    onSurface = Color(0xFFF0F2F6),
    surfaceVariant = Color(0xFF202632),
    onSurfaceVariant = Color(0xFFBBC2CE),
    outline = Color(0xFF454D5B),
    outlineVariant = Color(0xFF2D3440),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7C1917),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val AlmiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val AlmiTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black),
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
