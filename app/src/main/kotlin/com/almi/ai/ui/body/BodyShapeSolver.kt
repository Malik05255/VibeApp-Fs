package com.almi.ai.ui.body

import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import kotlin.math.pow

/**
 * Converts user-entered anthropometric facts into conservative 3D deformation parameters.
 *
 * This is deliberately deterministic and local: it never invents a missing measurement. When a
 * dimension has not been entered, the solver leaves that axis close to the source model instead of
 * guessing a body type. The output drives real non-uniform mesh transforms in Filament.
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
    // Neutral proportions of the bundled source human. They are calibration anchors, not claimed
    // user measurements. Ratios are clamped to avoid anatomically destructive transforms.
    private const val BASE_HEIGHT_IN = 68f
    private const val BASE_WEIGHT_LB = 165f
    private const val BASE_SHOULDERS_IN = 18f
    private const val BASE_CHEST_IN = 40f
    private const val BASE_WAIST_IN = 34f
    private const val BASE_HIPS_IN = 40f

    fun solve(profile: BodyProfile): DigitalTwinShape {
        val measurements = profile.measurementsInches

        val heightScale = if (profile.hasExplicitHeight) {
            ratio(profile.heightInches, BASE_HEIGHT_IN, 0.78f, 1.24f)
        } else {
            1f
        }

        val shoulder = measurements[BodyMeasurePoint.SHOULDERS]
            ?.let { ratio(it, BASE_SHOULDERS_IN, 0.76f, 1.34f) }
        val chest = measurements[BodyMeasurePoint.CHEST]
            ?.let { ratio(it, BASE_CHEST_IN, 0.76f, 1.38f) }
        val waist = measurements[BodyMeasurePoint.WAIST]
            ?.let { ratio(it, BASE_WAIST_IN, 0.72f, 1.45f) }
        val hips = measurements[BodyMeasurePoint.HIPS]
            ?.let { ratio(it, BASE_HIPS_IN, 0.76f, 1.40f) }

        // Weight is used only after the user explicitly enters it. It contributes gently because
        // weight alone cannot tell us where body volume is distributed.
        val massScale = if (profile.hasExplicitWeight) {
            val heightAdjustedBaseline = BASE_WEIGHT_LB * heightScale.toDouble().pow(2.15).toFloat()
            ratio(profile.weightPounds, heightAdjustedBaseline, 0.72f, 1.52f)
        } else {
            1f
        }
        val massWidthHint = 1f + (massScale - 1f) * 0.22f
        val massDepthHint = 1f + (massScale - 1f) * 0.30f

        val widthCandidates = buildList {
            shoulder?.let { add(it to 0.34f) }
            chest?.let { add(it to 0.28f) }
            waist?.let { add(it to 0.12f) }
            hips?.let { add(it to 0.26f) }
        }
        val circumferenceCandidates = buildList {
            chest?.let { add(it to 0.35f) }
            waist?.let { add(it to 0.28f) }
            hips?.let { add(it to 0.37f) }
        }

        val measuredWidth = weightedAverage(widthCandidates)
        val measuredDepth = weightedAverage(circumferenceCandidates)

        // Width reacts mainly to entered circumferences/shoulders. Depth receives slightly more
        // weight influence so the result gains/loses volume rather than only becoming wider.
        val widthScale = blendAvailable(measuredWidth, massWidthHint, profile.hasExplicitWeight, 0.82f)
            .coerceIn(0.76f, 1.38f)
        val depthScale = blendAvailable(measuredDepth, massDepthHint, profile.hasExplicitWeight, 0.74f)
            .coerceIn(0.74f, 1.44f)

        val factCount = listOfNotNull(
            profile.heightInches.takeIf { profile.hasExplicitHeight },
            profile.weightPounds.takeIf { profile.hasExplicitWeight },
            measurements[BodyMeasurePoint.SHOULDERS],
            measurements[BodyMeasurePoint.CHEST],
            measurements[BodyMeasurePoint.WAIST],
            measurements[BodyMeasurePoint.HIPS],
        ).size

        return DigitalTwinShape(
            heightScale = heightScale,
            widthScale = widthScale,
            depthScale = depthScale,
            // The head is a separate mesh. Counter-scale X/Z so torso measurements do not distort
            // facial identity while the parent's Y scale still follows the entered height.
            headWidthCompensation = safeInverse(widthScale),
            headDepthCompensation = safeInverse(depthScale),
            confidence = (factCount / 6f).coerceIn(0f, 1f),
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
