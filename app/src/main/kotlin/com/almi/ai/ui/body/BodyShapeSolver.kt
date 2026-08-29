package com.almi.ai.ui.body

import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import kotlin.math.pow

/**
 * Deterministic body-shape solver. It only reacts to values the user explicitly entered.
 *
 * The bundled source human is still a single mesh, so the solver remains conservative: torso
 * measurements drive width/depth while height, arm length and inseam influence longitudinal scale.
 * A future rig-aware solver can replace this without changing the measurement UI or persisted data.
 */
data class DigitalTwinShape(
    val heightScale: Float,
    val widthScale: Float,
    val depthScale: Float,
    val headWidthCompensation: Float,
    val headDepthCompensation: Float,
    val confidence: Float,
    val enteredShapeFacts: Int,
) {
    val isPersonalized: Boolean get() = enteredShapeFacts > 0
}

object BodyShapeSolver {
    private const val BASE_HEIGHT_IN = 68f
    private const val BASE_WEIGHT_LB = 165f
    private const val BASE_SHOULDERS_IN = 18f
    private const val BASE_CHEST_IN = 40f
    private const val BASE_WAIST_IN = 34f
    private const val BASE_HIPS_IN = 40f
    private const val BASE_ARM_IN = 24f
    private const val BASE_INSEAM_IN = 31.5f

    fun solve(profile: BodyProfile): DigitalTwinShape {
        val m = profile.measurementsInches

        val explicitHeight = if (profile.hasExplicitHeight) {
            ratio(profile.heightInches, BASE_HEIGHT_IN, 0.78f, 1.24f)
        } else null
        val armLength = m[BodyMeasurePoint.ARM_LENGTH]
            ?.let { ratio(it, BASE_ARM_IN, 0.78f, 1.26f) }
        val inseam = m[BodyMeasurePoint.INSEAM]
            ?.let { ratio(it, BASE_INSEAM_IN, 0.78f, 1.27f) }

        val longitudinal = weightedAverage(
            buildList {
                explicitHeight?.let { add(it to 0.70f) }
                inseam?.let { add(it to 0.20f) }
                armLength?.let { add(it to 0.10f) }
            }
        ) ?: 1f
        val heightScale = longitudinal.coerceIn(0.78f, 1.24f)

        val shoulder = m[BodyMeasurePoint.SHOULDERS]
            ?.let { ratio(it, BASE_SHOULDERS_IN, 0.76f, 1.34f) }
        val chest = m[BodyMeasurePoint.CHEST]
            ?.let { ratio(it, BASE_CHEST_IN, 0.76f, 1.38f) }
        val waist = m[BodyMeasurePoint.WAIST]
            ?.let { ratio(it, BASE_WAIST_IN, 0.72f, 1.45f) }
        val hips = m[BodyMeasurePoint.HIPS]
            ?.let { ratio(it, BASE_HIPS_IN, 0.76f, 1.40f) }

        val massScale = if (profile.hasExplicitWeight) {
            val baseline = BASE_WEIGHT_LB * heightScale.toDouble().pow(2.15).toFloat()
            ratio(profile.weightPounds, baseline, 0.72f, 1.52f)
        } else 1f
        val massWidthHint = 1f + (massScale - 1f) * 0.23f
        val massDepthHint = 1f + (massScale - 1f) * 0.31f

        val measuredWidth = weightedAverage(
            buildList {
                shoulder?.let { add(it to 0.34f) }
                chest?.let { add(it to 0.28f) }
                waist?.let { add(it to 0.12f) }
                hips?.let { add(it to 0.26f) }
            }
        )
        val measuredDepth = weightedAverage(
            buildList {
                chest?.let { add(it to 0.35f) }
                waist?.let { add(it to 0.28f) }
                hips?.let { add(it to 0.37f) }
            }
        )

        val widthScale = blendAvailable(measuredWidth, massWidthHint, profile.hasExplicitWeight, 0.82f)
            .coerceIn(0.76f, 1.38f)
        val depthScale = blendAvailable(measuredDepth, massDepthHint, profile.hasExplicitWeight, 0.74f)
            .coerceIn(0.74f, 1.44f)

        // All facts that currently participate in visible deformation.
        val factCount = buildList {
            if (profile.hasExplicitHeight) add(Unit)
            if (profile.hasExplicitWeight) add(Unit)
            if (m[BodyMeasurePoint.SHOULDERS] != null) add(Unit)
            if (m[BodyMeasurePoint.CHEST] != null) add(Unit)
            if (m[BodyMeasurePoint.WAIST] != null) add(Unit)
            if (m[BodyMeasurePoint.HIPS] != null) add(Unit)
            if (m[BodyMeasurePoint.ARM_LENGTH] != null) add(Unit)
            if (m[BodyMeasurePoint.INSEAM] != null) add(Unit)
        }.size

        // Confidence describes whether the core body envelope is known. Arm/inseam are useful
        // refinements, but their absence must not make a complete torso profile look incomplete.
        val coreFactCount = buildList {
            if (profile.hasExplicitHeight) add(Unit)
            if (profile.hasExplicitWeight) add(Unit)
            if (m[BodyMeasurePoint.SHOULDERS] != null) add(Unit)
            if (m[BodyMeasurePoint.CHEST] != null) add(Unit)
            if (m[BodyMeasurePoint.WAIST] != null) add(Unit)
            if (m[BodyMeasurePoint.HIPS] != null) add(Unit)
        }.size

        return DigitalTwinShape(
            heightScale = heightScale,
            widthScale = widthScale,
            depthScale = depthScale,
            headWidthCompensation = safeInverse(widthScale),
            headDepthCompensation = safeInverse(depthScale),
            confidence = (coreFactCount / 6f).coerceIn(0f, 1f),
            enteredShapeFacts = factCount,
        )
    }

    private fun ratio(value: Float, baseline: Float, min: Float, max: Float): Float =
        (value / baseline).coerceIn(min, max)

    private fun weightedAverage(values: List<Pair<Float, Float>>): Float? {
        if (values.isEmpty()) return null
        val totalWeight = values.sumOf { it.second.toDouble() }.toFloat()
        if (totalWeight <= 0f) return null
        return values.sumOf { (value, weight) -> (value * weight).toDouble() }.toFloat() / totalWeight
    }

    private fun blendAvailable(
        measured: Float?,
        fallback: Float,
        hasFallbackFact: Boolean,
        measuredWeight: Float,
    ): Float = when {
        measured != null && hasFallbackFact -> measured * measuredWeight + fallback * (1f - measuredWeight)
        measured != null -> measured
        hasFallbackFact -> fallback
        else -> 1f
    }

    private fun safeInverse(value: Float): Float = if (value == 0f) 1f else 1f / value
}
