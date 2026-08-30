package com.almi.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlmiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // V12 owns language and RTL directly from Compose state in MainActivity. Applying
        // AppCompat locales from Application.onCreate() can recreate the activity while Hilt and
        // Compose are still bootstrapping on some vendor ROMs. Keep cold start side-effect free.
    }
}
