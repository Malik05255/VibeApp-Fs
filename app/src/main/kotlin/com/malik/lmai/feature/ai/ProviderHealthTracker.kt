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
        val averageFirstOutputLatencyMs: Long? = null,
        val cooldownUntilMs: Long = 0L,
    ) {
        fun isCoolingDown(nowMs: Long): Boolean = cooldownUntilMs > nowMs
    }

    private data class MutableStats(
        var successCount: Int = 0,
        var failureCount: Int = 0,
        var consecutiveFailures: Int = 0,
        var averageLatencyMs: Long? = null,
        var averageFirstOutputLatencyMs: Long? = null,
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

    /**
     * Records the delay until the first user-visible text from a route.
     *
     * Perceived chat speed depends far more on time-to-first-text than on how long a
     * full answer takes. A route that makes the user wait several seconds is briefly
     * quarantined after the first slow turn so the very next message can try a faster
     * provider instead of repeating the same bad experience.
     */
    fun recordFirstOutput(platformUid: String, latencyMs: Long) {
        val now = System.currentTimeMillis()
        val value = stats.computeIfAbsent(platformUid, ::load)
        synchronized(value) {
            value.averageFirstOutputLatencyMs = smoothLatency(
                previous = value.averageFirstOutputLatencyMs,
                current = latencyMs,
            )
            if (latencyMs >= VERY_SLOW_FIRST_OUTPUT_MS) {
                value.cooldownUntilMs = max(
                    value.cooldownUntilMs,
                    now + INTERACTIVE_SLOW_COOLDOWN_MS,
                )
            }
            persist(platformUid, value)
        }
    }

    fun recordSuccess(platformUid: String, latencyMs: Long) {
        val value = stats.computeIfAbsent(platformUid, ::load)
        synchronized(value) {
            value.successCount = (value.successCount + 1).coerceAtMost(MAX_COUNTER)
            value.consecutiveFailures = 0
            // Do not clear a deliberate latency/rate-limit quarantine here. A slow
            // request can still complete successfully; clearing the cooldown would
            // immediately route the next turn back to the same slow provider.
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
                value.cooldownUntilMs = max(
                    value.cooldownUntilMs,
                    now + FAILURE_COOLDOWN_MS,
                )
            }
            persist(platformUid, value)
        }
    }

    /**
     * HTTP 429 / provider rate limits are deterministic for the immediate next turn.
     * Do not make the user hit the same exhausted route three times before switching.
     */
    fun recordRateLimit(platformUid: String) {
        val now = System.currentTimeMillis()
        val value = stats.computeIfAbsent(platformUid, ::load)
        synchronized(value) {
            value.failureCount = (value.failureCount + 1).coerceAtMost(MAX_COUNTER)
            value.consecutiveFailures = max(1, value.consecutiveFailures)
            value.cooldownUntilMs = max(
                value.cooldownUntilMs,
                now + RATE_LIMIT_COOLDOWN_MS,
            )
            persist(platformUid, value)
        }
    }

    fun scoreAdjustment(platformUid: String, nowMs: Long = System.currentTimeMillis()): Int {
        val value = snapshot(platformUid)
        if (value.isCoolingDown(nowMs)) return -80

        val reliability = reliabilityAdjustment(value)
        val latency = value.averageLatencyMs?.let { latencyMs ->
            when {
                latencyMs <= 1_500L -> 6
                latencyMs <= 3_000L -> 3
                latencyMs <= 7_000L -> 0
                latencyMs <= 15_000L -> -4
                else -> -8
            }
        } ?: 0

        val failurePenalty = failurePenalty(value)
        return max(-80, reliability + latency + failurePenalty)
    }

    /**
     * Stronger score used for interactive turns where perceived latency matters.
     *
     * Old installations already contain total latency but not first-output latency,
     * so total latency is used as an immediate migration fallback. Once a route has
     * emitted text on the new build, true time-to-first-output takes over.
     */
    fun interactiveScoreAdjustment(
        platformUid: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Int {
        val value = snapshot(platformUid)
        if (value.isCoolingDown(nowMs)) return -100

        val perceivedLatencyMs =
            value.averageFirstOutputLatencyMs ?: value.averageLatencyMs

        val latency = perceivedLatencyMs?.let { latencyMs ->
            when {
                latencyMs <= 700L -> 48
                latencyMs <= 1_200L -> 38
                latencyMs <= 2_000L -> 28
                latencyMs <= 3_500L -> 16
                latencyMs <= 5_000L -> 5
                latencyMs <= 8_000L -> -15
                latencyMs <= 12_000L -> -35
                latencyMs <= 18_000L -> -60
                else -> -90
            }
        } ?: 0

        return max(
            -100,
            reliabilityAdjustment(value) + latency + failurePenalty(value),
        )
    }

    private fun reliabilityAdjustment(value: Snapshot): Int {
        val total = value.successCount + value.failureCount
        if (total == 0) return 0
        val percent = (value.successCount * 100) / total
        return ((percent - 50) / 5).coerceIn(-10, 10)
    }

    private fun failurePenalty(value: Snapshot): Int =
        -min(18, value.consecutiveFailures * 6)

    private fun load(platformUid: String): MutableStats = MutableStats(
        successCount = preferences.getInt(key(platformUid, "success"), 0),
        failureCount = preferences.getInt(key(platformUid, "failure"), 0),
        consecutiveFailures = preferences.getInt(key(platformUid, "consecutive"), 0),
        averageLatencyMs = preferences
            .getLong(key(platformUid, "latency"), NO_LATENCY)
            .takeIf { it != NO_LATENCY },
        averageFirstOutputLatencyMs = preferences
            .getLong(key(platformUid, "first_output_latency"), NO_LATENCY)
            .takeIf { it != NO_LATENCY },
        cooldownUntilMs = preferences.getLong(key(platformUid, "cooldown"), 0L),
    )

    private fun persist(platformUid: String, value: MutableStats) {
        preferences.edit()
            .putInt(key(platformUid, "success"), value.successCount)
            .putInt(key(platformUid, "failure"), value.failureCount)
            .putInt(key(platformUid, "consecutive"), value.consecutiveFailures)
            .putLong(key(platformUid, "latency"), value.averageLatencyMs ?: NO_LATENCY)
            .putLong(
                key(platformUid, "first_output_latency"),
                value.averageFirstOutputLatencyMs ?: NO_LATENCY,
            )
            .putLong(key(platformUid, "cooldown"), value.cooldownUntilMs)
            .apply()
    }

    private fun MutableStats.toSnapshot() = Snapshot(
        successCount = successCount,
        failureCount = failureCount,
        consecutiveFailures = consecutiveFailures,
        averageLatencyMs = averageLatencyMs,
        averageFirstOutputLatencyMs = averageFirstOutputLatencyMs,
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
        private const val RATE_LIMIT_COOLDOWN_MS = 10 * 60 * 1000L
        private const val VERY_SLOW_FIRST_OUTPUT_MS = 6_000L
        private const val INTERACTIVE_SLOW_COOLDOWN_MS = 2 * 60 * 1000L
        private const val MAX_COUNTER = 10_000
        private const val NO_LATENCY = -1L
    }
}
