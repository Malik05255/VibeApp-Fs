package com.vibe.app.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.vibe.app.data.model.DynamicTheme
import com.vibe.app.data.model.ThemeMode

private val CleanLightColorScheme = lightColorScheme(
    primary = Color(0xFF2F6FED),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAF1FF),
    onPrimaryContainer = Color(0xFF173D84),
    secondary = Color(0xFF5C667A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1F4F8),
    onSecondaryContainer = Color(0xFF252D3A),
    tertiary = Color(0xFF5B5CE2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEEEFF),
    onTertiaryContainer = Color(0xFF34358A),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
    background = Color(0xFFFAFBFD),
    onBackground = Color(0xFF111318),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFF6F7FA),
    onSurfaceVariant = Color(0xFF697386),
    outline = Color(0xFFD5DBE5),
    outlineVariant = Color(0xFFE4E8EF),
    inverseSurface = Color(0xFF20242C),
    inverseOnSurface = Color(0xFFF8F9FB),
    inversePrimary = Color(0xFFB6CBFF),
    surfaceDim = Color(0xFFECEFF4),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFDFE),
    surfaceContainer = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color(0xFFF2F4F7),
    surfaceContainerHighest = Color(0xFFECEFF4),
)

private val CleanDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9DB8FF),
    onPrimary = Color(0xFF0B2D70),
    primaryContainer = Color(0xFF20458D),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFC0C8D8),
    onSecondary = Color(0xFF2A3240),
    secondaryContainer = Color(0xFF3A4353),
    onSecondaryContainer = Color(0xFFE0E6F0),
    tertiary = Color(0xFFC2C1FF),
    onTertiary = Color(0xFF333474),
    tertiaryContainer = Color(0xFF4B4C92),
    onTertiaryContainer = Color(0xFFE6E5FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0C111B),
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0xFF121824),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF1B2432),
    onSurfaceVariant = Color(0xFFB4BECC),
    outline = Color(0xFF5F6B7C),
    outlineVariant = Color(0xFF2C3645),
    inverseSurface = Color(0xFFE7EAF0),
    inverseOnSurface = Color(0xFF242C39),
    inversePrimary = Color(0xFF2F6FED),
    surfaceDim = Color(0xFF0C111B),
    surfaceBright = Color(0xFF273345),
    surfaceContainerLowest = Color(0xFF080D15),
    surfaceContainerLow = Color(0xFF101722),
    surfaceContainer = Color(0xFF151D29),
    surfaceContainerHigh = Color(0xFF1C2634),
    surfaceContainerHighest = Color(0xFF253142),
)

private val CleanShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun CleanVibeTheme(
    dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = if (dynamicTheme == DynamicTheme.ON) {
        if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (useDarkTheme) {
        CleanDarkColorScheme
    } else {
        CleanLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = CleanShapes,
        content = content,
    )
}
