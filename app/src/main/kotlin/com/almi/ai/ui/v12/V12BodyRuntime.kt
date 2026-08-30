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
 * The visible Body Scan now uses the async 4K Digital Human pipeline and exposes the actual
 * Filament resource-loading progress to Compose. This avoids a fake spinner while body/head
 * textures are decoded and uploaded, and keeps the holographic measurement material on the same
 * quality-first geometry used by Avatar Lab.
 */
internal class V12BodyRuntime(
    context: Context,
    surfaceView: SurfaceView,
    presentation: AvatarPresentation,
    private val onStateChanged: (V12BodyRendererState) -> Unit,
    private val onLoadProgress: (Float) -> Unit = {},
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
        onLoadProgress = { progress ->
            if (!stopped) onLoadProgress(progress.coerceIn(0f, 1f))
        },
        onReady = {
            if (!stopped) {
                onLoadProgress(1f)
                onStateChanged(V12BodyRendererState.READY)
            }
        },
        onFailure = {
            if (!stopped) onStateChanged(V12BodyRendererState.ERROR)
        },
    )

    fun initialize() {
        if (stopped) return
        onLoadProgress(0f)
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
