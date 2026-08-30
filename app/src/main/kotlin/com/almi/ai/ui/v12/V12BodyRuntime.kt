package com.almi.ai.ui.v12

import android.content.Context
import android.view.SurfaceView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation

internal enum class V12BodyRendererState { LOADING, READY, ERROR }

internal data class V12ProjectedPoint(
    val x: Float,
    val y: Float,
    val visible: Boolean,
)

internal data class V12BodyProjection(
    val points: Map<String, V12ProjectedPoint>,
    val yawRadians: Double,
    val cameraDistance: Double,
)

/**
 * Body Map adapter for the same high-detail v12 Digital Human used by Avatar Lab.
 *
 * The old compact VSim body renderer is no longer on the visible Body Map path. Measurement mode
 * uses the textured Vitruvian body + FACS head, a frozen skeletal idle, white authored clothing,
 * no hair obstruction, and Mixamo-rig landmark projection. Stored measurements remain owned by
 * BodyProfileStore; this renderer only supplies visual anatomy and screen-space landmarks.
 */
internal class V12BodyRuntime(
    context: Context,
    surfaceView: SurfaceView,
    presentation: AvatarPresentation,
    private val onStateChanged: (V12BodyRendererState) -> Unit,
    onProjectionChanged: (V12BodyProjection) -> Unit,
) {
    private var stopped = false

    private val measurementAppearance = AvatarAppearance(
        presentation = presentation,
        hairVariant = "bald",
        hairColor = "2C1B18",
        skinColor = when (presentation) {
            AvatarPresentation.FEMININE -> "F8D5C2"
            AvatarPresentation.MASCULINE -> "E8BC9D"
        },
        eyesVariant = "default",
        eyebrowsVariant = "default",
        mouthVariant = "default",
    )

    private val digitalHuman = V12DigitalHumanRuntime(
        context = context,
        surfaceView = surfaceView,
        initialPresentation = presentation,
        initialAppearance = measurementAppearance,
        measurementMode = true,
        onProjectionChanged = onProjectionChanged,
        onReady = { onStateChanged(V12BodyRendererState.READY) },
        onFailure = { onStateChanged(V12BodyRendererState.ERROR) },
    )

    fun initialize() {
        if (stopped) return
        onStateChanged(V12BodyRendererState.LOADING)
        digitalHuman.initialize()
    }

    fun start() {
        if (!stopped) digitalHuman.start()
    }

    fun stop() {
        if (stopped) return
        stopped = true
        digitalHuman.destroy()
    }

    fun resetView() {
        if (!stopped) digitalHuman.resetMeasurementView()
    }

    fun focusOn(anchorY: Float, distance: Float = 2.35f) {
        if (!stopped) digitalHuman.focusOn(anchorY, distance)
    }
}
