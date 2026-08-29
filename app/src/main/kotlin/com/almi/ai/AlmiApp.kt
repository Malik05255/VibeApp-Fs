package com.almi.ai

import android.app.Application
import com.almi.ai.data.preferences.AlmiPreferences
import com.google.android.filament.Filament
import com.google.android.filament.utils.Utils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlmiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlmiPreferences.applyStoredLanguage(this)

        // Google Filament's Android samples explicitly initialize both native runtimes before
        // creating an Engine. Keep this scoped to the isolated body process so the main app never
        // loads the 3D runtime unless the measurement screen is opened.
        if (getProcessName().endsWith(":body3d")) {
            Filament.init()
            Utils.init()
        }
    }
}
