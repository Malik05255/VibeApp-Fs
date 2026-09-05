package com.vibe.app.feature.ai

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight per-process provider telemetry used by the router.
 *
 * No prompts, tokens, API keys, or user content are stored here. Only aggregate
 * success/failure/latency information keyed by the local platform UID.
 */
@Singleton
class ProviderHealthTracker @Inject constructor() {

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

    private val stats = ConcurrentHashMap<String, MutableStats>()

    fun snapshot(platformUid: String): Snapshot {
        val value = stats[platformUid] ?: return Snapshot()
        synchronized(value) {
            return Snapshot(
                successCount = value.successCount,
                failureCount = value.failureCount,
                consecutiveFailures = value.consecutiveFailures,
                averageLatencyMs = value.averageLatencyMs,
                cooldownUntilMs = value.cooldownUntilMs,
            )
        }
    }

    fun recordSuccess(platformUid: String, latencyMs: Long) {
        val value = stats.computeIfAbsent(platformUid) { MutableStats() }
        synchronized(value) {
            value.successCount += 1
            value.consecutiveFailures = 0
            value.cooldownUntilMs = 0L
            value.averageLatencyMs = smoothLatency(value.averageLatencyMs, latencyMs)
        }
    }

    fun recordFailure(platformUid: String) {
        val now = System.currentTimeMillis()
        val value = stats.computeIfAbsent(platformUid) { MutableStats() }
        synchronized(value) {
            value.failureCount += 1
            value.consecutiveFailures += 1
            if (value.consecutiveFailures >= FAILURE_COOLDOWN_THRESHOLD) {
                value.cooldownUntilMs = now + FAILURE_COOLDOWN_MS
            }
        }
    }

    /**
     * Returns a bounded score adjustment. Cooling-down providers are heavily
     * penalized but remain visible as a last resort if every route is unhealthy.
     */
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

    private fun smoothLatency(previous: Long?, current: Long): Long {
        if (previous == null) return current.coerceAtLeast(0L)
        return ((previous * 3L) + current.coerceAtLeast(0L)) / 4L
    }

    companion object {
        private const val FAILURE_COOLDOWN_THRESHOLD = 3
        private const val FAILURE_COOLDOWN_MS = 5 * 60 * 1000L
    }
}
