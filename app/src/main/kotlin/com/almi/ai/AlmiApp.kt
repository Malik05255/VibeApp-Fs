package com.almi.ai

import android.app.Application
import com.almi.ai.data.preferences.AlmiPreferences
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlmiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlmiPreferences.applyStoredLanguage(this)
        // Filament is intentionally initialized lazily by PersistentFilamentRuntime via Utils.init(),
        // matching Google's glTF viewer sample. Do not initialize the native runtime here.
    }
}
