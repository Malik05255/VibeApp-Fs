package com.malik.lmai.feature.reminder

import com.malik.lmai.feature.assistant.HOwnerIdentity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-memory location scope for H.
 *
 * H no longer persists personal anchors or travel coordinates in Android storage. Durable
 * owner state belongs to the authenticated cloud account; this store is only a live-session
 * working view and is isolated by the current H owner key.
 */
@Singleton
class HLocationScopeStore @Inject constructor(
    private val ownerIdentity: HOwnerIdentity,
) {
    private val lock = Any()
    private val sessionConfigs = mutableMapOf<String, HLocationScopeConfig>()

    fun load(nowMs: Long = System.currentTimeMillis()): HLocationScopeConfig = synchronized(lock) {
        val ownerKey = ownerIdentity.currentOwnerKey()
        val current = sessionConfigs[ownerKey] ?: HLocationScopeConfig()
        if (
            current.travelAnchor != null &&
            current.travelExpiresAtMs != null &&
            current.travelExpiresAtMs <= nowMs
        ) {
            current.copy(travelAnchor = null, travelExpiresAtMs = null).also {
                sessionConfigs[ownerKey] = it
            }
        } else {
            current
        }
    }

    fun setBaseAnchor(point: HGeoPoint?) {
        update { it.copy(baseAnchor = point) }
    }

    fun setRadiusKm(radiusKm: Double) {
        val safe = radiusKm.coerceIn(
            HLocationScopeConfig.MIN_RADIUS_KM,
            HLocationScopeConfig.MAX_RADIUS_KM,
        )
        update { it.copy(radiusKm = safe) }
    }

    fun setExplicitDistantOverride(enabled: Boolean) {
        update { it.copy(explicitDistantOverride = enabled) }
    }

    fun startTravelMode(point: HGeoPoint, expiresAtMs: Long? = null) {
        update {
            it.copy(
                travelAnchor = point,
                travelExpiresAtMs = expiresAtMs,
            )
        }
    }

    fun clearTravel() {
        update { it.copy(travelAnchor = null, travelExpiresAtMs = null) }
    }

    fun clearCurrentOwnerSession() {
        synchronized(lock) {
            sessionConfigs.remove(ownerIdentity.currentOwnerKey())
        }
    }

    fun evaluateCandidate(
        candidate: HGeoPoint,
        explicitDistantPlace: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): HLocationScopeDecision = HLocationScopePolicy.evaluateCandidate(
        config = load(nowMs),
        candidate = candidate,
        explicitDistantPlace = explicitDistantPlace,
        nowMs = nowMs,
    )

    fun evaluateCurrentPosition(
        current: HGeoPoint,
        nowMs: Long = System.currentTimeMillis(),
    ): HLocationScopeDecision = HLocationScopePolicy.evaluateCurrentPosition(load(nowMs), current, nowMs)

    private fun update(transform: (HLocationScopeConfig) -> HLocationScopeConfig) {
        synchronized(lock) {
            val ownerKey = ownerIdentity.currentOwnerKey()
            val current = sessionConfigs[ownerKey] ?: HLocationScopeConfig()
            sessionConfigs[ownerKey] = transform(current)
        }
    }
}
