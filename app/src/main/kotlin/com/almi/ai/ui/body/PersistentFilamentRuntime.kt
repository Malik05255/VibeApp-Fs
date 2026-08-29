package com.almi.ai.ui.body

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import com.google.android.filament.View
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer

internal enum class BodyRendererState { LOADING, READY, ERROR }

/**
 * Persistent Filament runtime for the measurement Activity.
 *
 * The SurfaceView is created once by the Activity and is never owned by Compose/AndroidView.
 * This mirrors Google's sample architecture and avoids ModelViewer's detach/recreate hazard.
 */
internal class PersistentFilamentRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onStateChanged: (BodyRendererState) -> Unit,
) {
    companion object {
        init { Utils.init() }
        private const val BODY_MODEL = "almi3d/vitruvian_body.glb"
    }

    private var modelViewer: ModelViewer? = null
    private var running = false
    private var initialized = false
    private var baseRootTransform: FloatArray? = null
    private var pendingWidth = 1f
    private var pendingHeight = 1f
    private var pendingDepth = 1f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            modelViewer?.render(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        onStateChanged(BodyRendererState.LOADING)

        try {
            // Use the exact high-level constructor from Google's glTF viewer sample.
            val viewer = ModelViewer(surfaceView)
            modelViewer = viewer

            viewer.view.renderQuality = viewer.view.renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }
            viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
                enabled = true
                quality = View.QualityLevel.LOW
            }
            viewer.view.antiAliasing = View.AntiAliasing.FXAA
            viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply {
                enabled = false
            }
            viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
                enabled = false
            }

            surfaceView.setOnTouchListener { _, event ->
                viewer.onTouchEvent(event)
                true
            }

            val bytes = context.assets.open(BODY_MODEL).use { it.readBytes() }
            viewer.loadModelGlb(ByteBuffer.wrap(bytes))
            viewer.transformToUnitCube()
            captureBaseTransform(viewer)
            applyBodyShape()

            onStateChanged(BodyRendererState.READY)
            if (running) postFrame()
        } catch (_: Throwable) {
            onStateChanged(BodyRendererState.ERROR)
            stop()
        }
    }

    fun onOverlayTouch(event: MotionEvent) {
        modelViewer?.onTouchEvent(event)
    }

    fun updateBodyShape(width: Float, height: Float, depth: Float) {
        pendingWidth = width.coerceIn(.72f, 1.42f)
        pendingHeight = height.coerceIn(.82f, 1.20f)
        pendingDepth = depth.coerceIn(.72f, 1.42f)
        applyBodyShape()
    }

    fun start() {
        if (running) return
        running = true
        if (modelViewer != null) postFrame()
    }

    fun stop() {
        if (!running) return
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun postFrame() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun captureBaseTransform(viewer: ModelViewer) {
        val asset = viewer.asset ?: return
        val tm = viewer.engine.transformManager
        val instance = tm.getInstance(asset.root)
        if (instance == 0) return
        baseRootTransform = FloatArray(16).also { tm.getTransform(instance, it) }
    }

    private fun applyBodyShape() {
        val viewer = modelViewer ?: return
        val asset = viewer.asset ?: return
        val base = baseRootTransform ?: return
        val tm = viewer.engine.transformManager
        val instance = tm.getInstance(asset.root)
        if (instance == 0) return

        // Post-scale the normalized ModelViewer root transform in local X/Y/Z.
        val out = base.copyOf()
        for (row in 0..3) {
            out[row] *= pendingWidth
            out[4 + row] *= pendingHeight
            out[8 + row] *= pendingDepth
        }
        tm.setTransform(instance, out)
    }
}
