package com.malik.lmai.feature.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight persistent provider telemetry used by the router.
 *
 * This is the app's learning layer: it remembers only aggregate reliability and
 * latency for each provider across launches. It never stores prompts, responses,
 * API keys, or user content, and it performs no background work.
 */
@Singleton
class ProviderHealthTracker @Inject constructor(
    @ApplicationContext context: Context,
) {

    data class Snapshot(
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val consecutiveFailures: Int = 0,
        val averageLatencyMs: Long? = null,
        val cooldownUntilMs: Long = 0L,
    ) {
        fun isCoolingDown(nowMs: Long): Boolean = cooldownUntilMs > nowMs
    }

    private data class MutableStats(
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var consecutiveFailures: Int = 0,
        var averageLatencyMs: Long? = null,
        var cooldownUntilMs: Long = 0L,
    )

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val stats = ConcurrentHashMap<String, MutableStats>()

    fun snapshot(platformUid: String): Snapshot {
        val value = stats.computeIfAbsent(platformUid, ::load)
        synchronized(value) {
            return value.toSnapshot()
        }
    }

    fun recordSuccess(platformUid: String, latencyMs: Long) {
        val value = stats.computeIfAbsent(platformUid, ::load)
        synchronized(value) {
            value.successCount = (value.successCount + 1).coerceAtMost(MAX_COUNTER)
            value.consecutiveFailures = 0
            value.cooldownUntilMs = 0L
            value.averageLatencyMs = smoothLatency(value.averageLatencyMs, latencyMs)
            persist(platformUid, value)
        }
    }

    fun recordFailure(platformUid: String) {
        val now = System.currentTimeMillis()
        val value = stats.computeIfAbsent(platformUid, ::load)
        synchronized(value) {
            value.failureCount = (value.failureCount + 1).coerceAtMost(MAX_COUNTER)
            value.consecutiveFailures = (value.consecutiveFailures + 1)
                .coerceAtMost(FAILURE_COOLDOWN_THRESHOLD + 2)
            if (value.consecutiveFailures >= FAILURE_COOLDOWN_THRESHOLD) {
                value.cooldownUntilMs = now + FAILURE_COOLDOWN_MS
            }
            persist(platformUid, value)
        }
    }

    fun scoreAdjustment(platformUid: String, nowMs: Long = System.currentTimeMillis()): Int {
        val value = snapshot(platformUid)
        if (value.isCoolingDown(nowMs)) return -80

        val total = value.successCount + value.failureCount
        val reliability = if (total == 0) {
            0
        } else {
            val percent = (value.successCount * 100) / total
            ((percent - 50) / 5).coerceIn(-10, 10)
        }

        val latency = value.averageLatencyMs?.let { latencyMs ->
            when {
                latencyMs <= 1_500L -> 6
                latencyMs <= 3_000L -> 3
                latencyMs <= 7_000L -> 0
                latencyMs <= 15_000L -> -4
                else -> -8
            }
        } ?: 0

        val failurePenalty = -min(18, value.consecutiveFailures * 6)
        return max(-80, reliability + latency + failurePenalty)
    }

    private fun load(platformUid: String): MutableStats = MutableStats(
        successCount = preferences.getInt(key(platformUid, "success"), 0),
        failureCount = preferences.getInt(key(platformUid, "failure"), 0),
        consecutiveFailures = preferences.getInt(key(platformUid, "consecutive"), 0),
        averageLatencyMs = preferences
            .getLong(key(platformUid, "latency"), NO_LATENCY)
            .takeIf { it != NO_LATENCY },
        cooldownUntilMs = preferences.getLong(key(platformUid, "cooldown"), 0L),
    )

    private fun persist(platformUid: String, value: MutableStats) {
        preferences.edit()
            .putInt(key(platformUid, "success"), value.successCount)
            .putInt(key(platformUid, "failure"), value.failureCount)
            .putInt(key(platformUid, "consecutive"), value.consecutiveFailures)
            .putLong(key(platformUid, "latency"), value.averageLatencyMs ?: NO_LATENCY)
            .putLong(key(platformUid, "cooldown"), value.cooldownUntilMs)
            .apply()
    }

    private fun MutableStats.toSnapshot() = Snapshot(
        successCount = successCount,
        failureCount = failureCount,
        consecutiveFailures = consecutiveFailures,
        averageLatencyMs = averageLatencyMs,
        cooldownUntilMs = cooldownUntilMs,
    )

    private fun key(platformUid: String, field: String): String =
        "provider.$platformUid.$field"

    private fun smoothLatency(previous: Long?, current: Long): Long {
        if (previous == null) return current.coerceAtLeast(0L)
        return ((previous * 3L) + current.coerceAtLeast(0L)) / 4L
    }

    companion object {
        private const val PREFERENCES_NAME = "adaptive_ai_provider_health"
        private const val FAILURE_COOLDOWN_THRESHOLD = 3
        private const val FAILURE_COOLDOWN_MS = 5 * 60 * 1000L
        private const val MAX_COUNTER = 10_000
        private const val NO_LATENCY = -1L
    }
}
