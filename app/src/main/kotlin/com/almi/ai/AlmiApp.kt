package com.almi.ai

import android.app.Application
import com.almi.ai.update.AlmiUpdateScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AlmiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // V12 owns language and RTL directly from Compose state in MainActivity. Applying
        // AppCompat locales from Application.onCreate() can recreate the activity while Hilt and
        // Compose are still bootstrapping on some vendor ROMs. Keep locale bootstrap side-effect free.
        // The platform scheduler is safe here and keeps update discovery alive while ALMI is closed.
        AlmiUpdateScheduler.schedule(this)
    }
}
