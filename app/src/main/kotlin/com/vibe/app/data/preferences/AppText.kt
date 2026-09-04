package com.vibe.app.data.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

/**
 * Resolves string resources against the locale selected inside lm_AI.
 *
 * Background services and repositories do not necessarily receive an Activity
 * context after a runtime locale change, so reading directly from their base
 * context can return strings in the device language. This accessor always
 * creates a configuration using the current per-app locale first.
 */
object AppText {
    @Volatile
    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg formatArgs: Any): String {
        val base = checkNotNull(applicationContext) { "AppText has not been initialized" }
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (!appLocales.isEmpty) appLocales[0] ?: Locale.getDefault() else Locale.getDefault()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val resources = base.createConfigurationContext(configuration).resources
        return if (formatArgs.isEmpty()) resources.getString(id) else resources.getString(id, *formatArgs)
    }
}
