package com.almi.ai.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lightweight background update watch using Android's platform JobScheduler.
 *
 * The job only checks the latest manifest and, when an update applies to the installed build,
 * delegates to [AlmiUpdateNotifier]. The notifier itself guarantees one visible notification per
 * (releaseId, installedVersionCode), so periodic background execution can never spam the user.
 */
internal class AlmiUpdateJobService : JobService() {
    private var running: Job? = null
    private var scope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val jobScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = jobScope
        running = jobScope.launch {
            val manager = AlmiUpdateManager(applicationContext)
            manager.check(manual = false)
            when (val state = manager.state.value) {
                is AlmiUpdateState.Available -> {
                    AlmiUpdateNotifier.notifyOnce(
                        applicationContext,
                        state.release,
                        readLanguage(applicationContext),
                    )
                }
                else -> Unit
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        scope?.cancel()
        running = null
        scope = null
        return true
    }

    private fun readLanguage(context: Context): String =
        context.getSharedPreferences("almi_ai_settings", Context.MODE_PRIVATE)
            .getString("language", "ar")
            ?.takeIf { it == "ar" || it == "en" }
            ?: "ar"
}

internal object AlmiUpdateScheduler {
    private const val JOB_ID = 0xA1_12_01
    private const val INTERVAL_MILLIS = 6L * 60L * 60L * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val component = ComponentName(context, AlmiUpdateJobService::class.java)
        val info = JobInfo.Builder(JOB_ID, component)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(INTERVAL_MILLIS)
            .build()
        scheduler.schedule(info)
    }
}
