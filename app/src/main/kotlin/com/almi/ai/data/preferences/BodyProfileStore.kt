package com.almi.ai.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the user wants ALMI to represent them during the fitting journey. */
enum class JourneyMode {
    AVATAR,
    PHOTO,
}

/**
 * Measurements intentionally stay explicit instead of using generic size labels. Values are
 * persisted internally in inches so conversions never accumulate rounding errors.
 */
enum class BodyMeasurePoint(val key: String) {
    NECK("neck"),
    SHOULDERS("shoulders"),
    CHEST("chest"),
    WAIST("waist"),
    HIPS("hips"),
    ARM_LENGTH("arm_length"),
    WRIST("wrist"),
    HAND("hand"),
    THIGH("thigh"),
    INSEAM("inseam"),
    CALF("calf"),
    FOOT("foot"),
}

/**
 * The fitting journey should not force all 12 measurements before it becomes useful.
 * These six measurements give ALMI enough user-entered sizing context for a strong first pass.
 * The remaining measurements refine sleeve, trouser and accessory fit later.
 */
val essentialBodyMeasurements: List<BodyMeasurePoint> = listOf(
    BodyMeasurePoint.SHOULDERS,
    BodyMeasurePoint.CHEST,
    BodyMeasurePoint.WAIST,
    BodyMeasurePoint.HIPS,
    BodyMeasurePoint.ARM_LENGTH,
    BodyMeasurePoint.INSEAM,
)

/**
 * Deliberate guided order used by the interactive body lab. It moves from large/easy landmarks
 * to smaller refinements so the session feels progressive instead of random.
 */
val guidedMeasurementOrder: List<BodyMeasurePoint> = listOf(
    BodyMeasurePoint.SHOULDERS,
    BodyMeasurePoint.CHEST,
    BodyMeasurePoint.WAIST,
    BodyMeasurePoint.HIPS,
    BodyMeasurePoint.ARM_LENGTH,
    BodyMeasurePoint.INSEAM,
    BodyMeasurePoint.NECK,
    BodyMeasurePoint.WRIST,
    BodyMeasurePoint.HAND,
    BodyMeasurePoint.THIGH,
    BodyMeasurePoint.CALF,
    BodyMeasurePoint.FOOT,
)

data class BodyProfile(
    // Defaults are visual mannequin starting values only. The explicit flags prevent them from
    // ever being represented to generation as facts until the user actually edits/confirms them.
    val heightInches: Float = 68f,
    val weightPounds: Float = 165f,
    val hasExplicitHeight: Boolean = false,
    val hasExplicitWeight: Boolean = false,
    val measurementsInches: Map<BodyMeasurePoint, Float> = emptyMap(),
) {
    val completedMeasurements: Int
        get() = measurementsInches.size

    val completionFraction: Float
        get() = completedMeasurements.toFloat() / BodyMeasurePoint.entries.size.toFloat()

    val essentialCompletedMeasurements: Int
        get() = essentialBodyMeasurements.count(measurementsInches::containsKey)

    val essentialCompletionFraction: Float
        get() = essentialCompletedMeasurements.toFloat() / essentialBodyMeasurements.size.toFloat()

    /** True once the user has supplied the measurements needed for a useful first fitting pass. */
    val isFitReady: Boolean
        get() = essentialBodyMeasurements.all(measurementsInches::containsKey)

    /** Full precision profile, including all optional refinements. */
    val isComplete: Boolean
        get() = completedMeasurements == BodyMeasurePoint.entries.size

    /** Next unfinished point in the measurement coach flow. */
    val nextRecommendedMeasurement: BodyMeasurePoint?
        get() = guidedMeasurementOrder.firstOrNull { it !in measurementsInches }

    val remainingEssentialMeasurements: List<BodyMeasurePoint>
        get() = essentialBodyMeasurements.filterNot(measurementsInches::containsKey)
}

@Singleton
class BodyProfileStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _onboardingComplete = MutableStateFlow(preferences.getBoolean(KEY_ONBOARDING_COMPLETE, false))
    val onboardingComplete: StateFlow<Boolean> = _onboardingComplete.asStateFlow()

    private val _journeyMode = MutableStateFlow(readJourneyMode())
    val journeyMode: StateFlow<JourneyMode?> = _journeyMode.asStateFlow()

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<BodyProfile> = _profile.asStateFlow()

    fun setJourneyMode(mode: JourneyMode) {
        preferences.edit().putString(KEY_JOURNEY_MODE, mode.name).apply()
        _journeyMode.value = mode
    }

    fun setHeightInches(value: Float) {
        if (!value.isFinite() || value !in MIN_HEIGHT_IN..MAX_HEIGHT_IN) return
        preferences.edit().putFloat(KEY_HEIGHT_IN, value).apply()
        _profile.value = _profile.value.copy(
            heightInches = value,
            hasExplicitHeight = true,
        )
    }

    fun setWeightPounds(value: Float) {
        if (!value.isFinite() || value !in MIN_WEIGHT_LB..MAX_WEIGHT_LB) return
        preferences.edit().putFloat(KEY_WEIGHT_LB, value).apply()
        _profile.value = _profile.value.copy(
            weightPounds = value,
            hasExplicitWeight = true,
        )
    }

    fun setMeasurement(point: BodyMeasurePoint, inches: Float) {
        if (!inches.isFinite() || inches !in MIN_MEASUREMENT_IN..MAX_MEASUREMENT_IN) return
        preferences.edit().putFloat(measurementKey(point), inches).apply()
        _profile.value = _profile.value.copy(
            measurementsInches = _profile.value.measurementsInches + (point to inches),
        )
    }

    fun clearMeasurement(point: BodyMeasurePoint) {
        preferences.edit().remove(measurementKey(point)).apply()
        _profile.value = _profile.value.copy(
            measurementsInches = _profile.value.measurementsInches - point,
        )
    }

    fun completeOnboarding() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        _onboardingComplete.value = true
    }

    fun reopenBodyLab() {
        preferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, false).apply()
        _onboardingComplete.value = false
    }

    /**
     * Supplies only user-entered sizing facts to generation. No inferred body traits are invented.
     * This context is used only when the user explicitly chose the avatar journey.
     */
    fun currentPromptContext(): String? {
        if (_journeyMode.value != JourneyMode.AVATAR) return null
        val current = _profile.value
        val measurements = current.measurementsInches
            .toList()
            .sortedBy { it.first.ordinal }
            .joinToString(", ") { (point, value) -> "${point.key}=${format(value)}in" }

        val enteredFacts = buildList {
            if (current.hasExplicitHeight) add("height=${format(current.heightInches)}in")
            if (current.hasExplicitWeight) add("weight=${format(current.weightPounds)}lb")
            if (measurements.isNotBlank()) add("measurements: $measurements")
        }

        return buildString {
            append("Preserve the user's entered body proportions when fitting the garment.")
            if (enteredFacts.isNotEmpty()) append(" User-entered sizing facts: ${enteredFacts.joinToString(", ")}.")
            else append(" The user has not entered sizing measurements yet.")
            append(" Measurement profile status=${if (current.isFitReady) "fit-ready" else "partial"}.")
            append(" Do not infer missing measurements or alter identity, pose, or body proportions beyond fitting the garment naturally.")
        }
    }

    private fun readJourneyMode(): JourneyMode? =
        preferences.getString(KEY_JOURNEY_MODE, null)
            ?.let { stored -> runCatching { JourneyMode.valueOf(stored) }.getOrNull() }

    private fun readProfile(): BodyProfile {
        val measurements = buildMap {
            BodyMeasurePoint.entries.forEach { point ->
                if (preferences.contains(measurementKey(point))) {
                    put(point, preferences.getFloat(measurementKey(point), 0f))
                }
            }
        }
        return BodyProfile(
            heightInches = preferences.getFloat(KEY_HEIGHT_IN, 68f),
            weightPounds = preferences.getFloat(KEY_WEIGHT_LB, 165f),
            hasExplicitHeight = preferences.contains(KEY_HEIGHT_IN),
            hasExplicitWeight = preferences.contains(KEY_WEIGHT_LB),
            measurementsInches = measurements,
        )
    }

    private fun measurementKey(point: BodyMeasurePoint): String = "measurement_${point.key}_in"

    private fun format(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else "%.1f".format(java.util.Locale.US, value)

    companion object {
        private const val PREFERENCES_NAME = "almi_body_profile"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete_v6"
        private const val KEY_JOURNEY_MODE = "journey_mode"
        private const val KEY_HEIGHT_IN = "height_inches"
        private const val KEY_WEIGHT_LB = "weight_pounds"

        private const val MIN_HEIGHT_IN = 36f
        private const val MAX_HEIGHT_IN = 96f
        private const val MIN_WEIGHT_LB = 45f
        private const val MAX_WEIGHT_LB = 700f
        private const val MIN_MEASUREMENT_IN = 1f
        private const val MAX_MEASUREMENT_IN = 120f
    }
}
