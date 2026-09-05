package com.vibe.app.data.preferences

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

object AppText {
    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var languageTag: String? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun setLanguage(language: String) {
        languageTag = when (language.trim().lowercase()) {
            "ar", "arabic", "العربية" -> "ar"
            "en", "english", "الإنجليزية" -> "en"
            else -> "en"
        }
    }

    fun get(@StringRes id: Int, vararg formatArgs: Any): String {
        val base = checkNotNull(applicationContext) { "AppText has not been initialized" }
        val locale = languageTag
            ?.takeIf { it.isNotBlank() }
            ?.let(Locale::forLanguageTag)
            ?: Locale.getDefault()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        val resources = base.createConfigurationContext(configuration).resources
        return if (formatArgs.isEmpty()) resources.getString(id) else resources.getString(id, *formatArgs)
    }
}
