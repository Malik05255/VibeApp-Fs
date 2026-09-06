package com.malik.lmai.feature.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceAiProfile {
    LOCAL_FULL,
    LOCAL_LIGHT,
    CLOUD_FIRST,
    CLOUD_ONLY,
}

data class DeviceCapabilitySnapshot(
    val profile: DeviceAiProfile,
    val totalRamMb: Long,
    val sdkInt: Int,
    val mediaPerformanceClass: Int,
)

/**
 * Cheap runtime device profile. It never downloads a model or blocks routing.
 * Actual Gemini Nano availability is still verified by LocalNanoAgentGateway.
 */
@Singleton
class DeviceCapabilityProfiler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun snapshot(): DeviceCapabilitySnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val ramMb = memoryInfo.totalMem / (1024L * 1024L)
        val sdk = Build.VERSION.SDK_INT
        val performanceClass = if (sdk >= Build.VERSION_CODES.S) {
            Build.VERSION.MEDIA_PERFORMANCE_CLASS
        } else {
            0
        }

        val profile = when {
            sdk >= Build.VERSION_CODES.S && ramMb >= 8_000L -> DeviceAiProfile.LOCAL_FULL
            sdk >= Build.VERSION_CODES.S && ramMb >= 6_000L -> DeviceAiProfile.LOCAL_LIGHT
            ramMb >= 4_000L -> DeviceAiProfile.CLOUD_FIRST
            else -> DeviceAiProfile.CLOUD_ONLY
        }

        return DeviceCapabilitySnapshot(
            profile = profile,
            totalRamMb = ramMb,
            sdkInt = sdk,
            mediaPerformanceClass = performanceClass,
        )
    }
}
