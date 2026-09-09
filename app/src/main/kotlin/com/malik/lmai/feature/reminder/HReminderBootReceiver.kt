package com.malik.lmai.feature.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Restores H's execution registrations from cloud after reboot or app update. */
class HReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val runtime = HReminderCloudRuntime(appContext)
                val scheduler = HReminderScheduler(appContext)
                runtime.pull().orEmpty()
                    .filter {
                        it.source != HReminderSource.WHATSAPP &&
                            it.isPersonal &&
                            it.isOpen
                    }
                    .forEach(scheduler::schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
