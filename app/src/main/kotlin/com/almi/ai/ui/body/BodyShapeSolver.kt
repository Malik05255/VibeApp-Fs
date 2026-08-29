package com.almi.ai.ui.body

import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import kotlin.math.pow

/**
 * Deterministic body-shape solver for the dressmaker profile.
 *
 * Only user-entered values influence the twin. Bust, underbust, waist, abdomen and hips control the
 * torso envelope; full height and arm length control longitudinal proportions; weight supplies a
 * conservative fallback when circumference measurements are still missing.
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
    private const val BASE_HEIGHT_IN = 65f
    private const val BASE_WEIGHT_LB = 160f
    private const val BASE_SHOULDERS_IN = 16.5f
    private const val BASE_BUST_IN = 36f
    private const val BASE_UNDERBUST_IN = 32f
    private const val BASE_WAIST_IN = 29f
    private const val BASE_ABDOMEN_IN = 33f
    private const val BASE_HIPS_IN = 39f
    private const val BASE_ARM_IN = 23f

    fun solve(profile: BodyProfile): DigitalTwinShape {
        val m = profile.measurementsInches

        val explicitHeight = if (profile.hasExplicitHeight) {
            ratio(profile.heightInches, BASE_HEIGHT_IN, 0.78f, 1.24f)
        } else null
        val armLength = m[BodyMeasurePoint.ARM_LENGTH]
            ?.let { ratio(it, BASE_ARM_IN, 0.80f, 1.24f) }

        val longitudinal = weightedAverage(
            buildList {
                explicitHeight?.let { add(it to 0.88f) }
                armLength?.let { add(it to 0.12f) }
            },
        ) ?: 1f
        val heightScale = longitudinal.coerceIn(0.78f, 1.24f)

        val shoulder = m[BodyMeasurePoint.SHOULDERS]
            ?.let { ratio(it, BASE_SHOULDERS_IN, 0.76f, 1.34f) }
        val bust = m[BodyMeasurePoint.CHEST]
            ?.let { ratio(it, BASE_BUST_IN, 0.76f, 1.42f) }
        val underbust = m[BodyMeasurePoint.UNDERBUST]
            ?.let { ratio(it, BASE_UNDERBUST_IN, 0.76f, 1.38f) }
        val waist = m[BodyMeasurePoint.WAIST]
            ?.let { ratio(it, BASE_WAIST_IN, 0.72f, 1.48f) }
        val abdomen = m[BodyMeasurePoint.ABDOMEN]
            ?.let { ratio(it, BASE_ABDOMEN_IN, 0.74f, 1.48f) }
        val hips = m[BodyMeasurePoint.HIPS]
            ?.let { ratio(it, BASE_HIPS_IN, 0.76f, 1.44f) }

        val massScale = if (profile.hasExplicitWeight) {
            val baseline = BASE_WEIGHT_LB * heightScale.toDouble().pow(2.15).toFloat()
            ratio(profile.weightPounds, baseline, 0.72f, 1.55f)
        } else 1f
        val massWidthHint = 1f + (massScale - 1f) * 0.24f
        val massDepthHint = 1f + (massScale - 1f) * 0.34f

        val measuredWidth = weightedAverage(
            buildList {
                shoulder?.let { add(it to 0.25f) }
                bust?.let { add(it to 0.22f) }
                underbust?.let { add(it to 0.10f) }
                waist?.let { add(it to 0.10f) }
                abdomen?.let { add(it to 0.12f) }
                hips?.let { add(it to 0.21f) }
            },
        )
        val measuredDepth = weightedAverage(
            buildList {
                bust?.let { add(it to 0.24f) }
                underbust?.let { add(it to 0.16f) }
                waist?.let { add(it to 0.15f) }
                abdomen?.let { add(it to 0.25f) }
                hips?.let { add(it to 0.20f) }
            },
        )

        val widthScale = blendAvailable(measuredWidth, massWidthHint, profile.hasExplicitWeight, 0.82f)
            .coerceIn(0.76f, 1.40f)
        val depthScale = blendAvailable(measuredDepth, massDepthHint, profile.hasExplicitWeight, 0.74f)
            .coerceIn(0.74f, 1.46f)

        val factCount = buildList {
            if (profile.hasExplicitHeight) add(Unit)
            if (profile.hasExplicitWeight) add(Unit)
            if (m[BodyMeasurePoint.SHOULDERS] != null) add(Unit)
            if (m[BodyMeasurePoint.CHEST] != null) add(Unit)
            if (m[BodyMeasurePoint.UNDERBUST] != null) add(Unit)
            if (m[BodyMeasurePoint.WAIST] != null) add(Unit)
            if (m[BodyMeasurePoint.ABDOMEN] != null) add(Unit)
            if (m[BodyMeasurePoint.HIPS] != null) add(Unit)
            if (m[BodyMeasurePoint.ARM_LENGTH] != null) add(Unit)
            if (m[BodyMeasurePoint.UPPER_ARM] != null) add(Unit)
        }.size

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
