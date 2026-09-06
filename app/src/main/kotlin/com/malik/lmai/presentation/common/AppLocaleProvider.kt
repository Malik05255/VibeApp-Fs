package com.malik.lmai.presentation.common

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Applies the in-app locale to Compose resources without replacing LocalContext.
 *
 * Keeping the original Activity context is important: a configuration context is not an
 * Activity, and replacing LocalContext globally can crash code that requires an Activity
 * (navigation, credential flows, activity result launchers, browser intents, etc.).
 */
@Composable
fun AppLocaleProvider(
    language: String,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val normalizedLanguage = if (language == "ar") "ar" else "en"
    val locale = remember(normalizedLanguage) { Locale.forLanguageTag(normalizedLanguage) }

    val localizedConfiguration = remember(baseConfiguration, locale) {
        Configuration(baseConfiguration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localizedResources = remember(baseContext, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration).resources
    }
    val layoutDirection = if (normalizedLanguage == "ar") {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalResources provides localizedResources,
        LocalLayoutDirection provides layoutDirection,
        content = content,
    )
}
