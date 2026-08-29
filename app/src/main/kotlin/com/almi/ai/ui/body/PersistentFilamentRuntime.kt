package com.almi.ai.ui.body

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.View
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer

internal enum class BodyRendererState { LOADING, READY, ERROR }

/**
 * Persistent Filament runtime for the measurement Activity.
 *
 * Stability rules:
 * - Filament is initialized explicitly before any Engine/JNI call.
 * - OPENGL is forced; Vulkan is never selected implicitly on older/vendor Android drivers.
 * - ModelViewer owns one SurfaceView for the whole Activity lifetime.
 * - Engine/viewer creation waits for a valid native Surface.
 * - The renderer starts on an empty scene first, then glTF loading is staged later.
 * - A tiny core-glTF human can be used as a Filament-only compatibility path.
 */
internal class PersistentFilamentRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val compatibilityMode: Boolean,
    private val onStateChanged: (BodyRendererState) -> Unit,
) {
    companion object {
        init {
            Filament.init()
            Utils.init()
        }

        private const val BODY_MODEL = "almi3d/vitruvian_body.glb"
        private const val COMPAT_MODEL = "almi3d/compat_rigged_figure.glb"
        private const val PREFS = "almi_filament_boot_v2"
        private const val KEY_STAGE = "stage"
        private const val KEY_MODE = "mode"

        fun lastStage(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STAGE, "NONE") ?: "NONE"
    }

    private var modelViewer: ModelViewer? = null
    private var running = false
    private var initialized = false
    private var surfaceCallbackInstalled = false
    private var modelLoadPosted = false
    private var readyPosted = false
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
        onStateChanged(BodyRendererState.LOADING)
        markStage("WAIT_SURFACE")

        if (!surfaceView.holder.surface.isValid) {
            installSurfaceCallback()
            return
        }
        initializeOnValidSurface()
    }

    private fun installSurfaceCallback() {
        if (surfaceCallbackInstalled) return
        surfaceCallbackInstalled = true
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceView.post {
                    if (!initialized && holder.surface.isValid) initializeOnValidSurface()
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    private fun initializeOnValidSurface() {
        if (initialized) return
        initialized = true

        try {
            markStage("ENGINE_CREATE")
            val engine = Engine.create(Engine.Backend.OPENGL)

            markStage("MODELVIEWER_CREATE")
            val viewer = ModelViewer(surfaceView, engine = engine)
            modelViewer = viewer

            viewer.view.renderQuality = viewer.view.renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.LOW
            }
            viewer.view.dynamicResolutionOptions = viewer.view.dynamicResolutionOptions.apply {
                enabled = false
            }
            viewer.view.antiAliasing = View.AntiAliasing.NONE
            viewer.view.ambientOcclusionOptions = viewer.view.ambientOcclusionOptions.apply {
                enabled = false
            }
            viewer.view.bloomOptions = viewer.view.bloomOptions.apply {
                enabled = false
            }
            viewer.view.multiSampleAntiAliasingOptions = viewer.view.multiSampleAntiAliasingOptions.apply {
                enabled = false
            }

            surfaceView.setOnTouchListener { _, event ->
                viewer.onTouchEvent(event)
                true
            }

            markStage("EMPTY_RENDERER_READY")
            if (running) postFrame()

            if (!modelLoadPosted) {
                modelLoadPosted = true
                surfaceView.postDelayed({ loadBodyModel() }, 650L)
            }
        } catch (_: Throwable) {
            markStage("JAVA_INIT_ERROR")
            onStateChanged(BodyRendererState.ERROR)
            stop()
        }
    }

    private fun loadBodyModel() {
        val viewer = modelViewer ?: return
        if (!surfaceView.isAttachedToWindow) return
        val modelPath = if (compatibilityMode) COMPAT_MODEL else BODY_MODEL

        try {
            markStage("MODEL_READ")
            val bytes = context.assets.open(modelPath).use { it.readBytes() }
            val direct = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                flip()
            }

            markStage("MODEL_LOAD")
            viewer.loadModelGlb(direct)
            viewer.transformToUnitCube()
            captureBaseTransform(viewer)
            applyBodyShape()

            markStage("MODEL_STREAMING")
            if (!readyPosted) {
                readyPosted = true
                surfaceView.postDelayed({
                    if (modelViewer?.asset != null && surfaceView.isAttachedToWindow) {
                        markStage("READY")
                        onStateChanged(BodyRendererState.READY)
                    }
                }, if (compatibilityMode) 700L else 1_500L)
            }
        } catch (_: Throwable) {
            markStage("JAVA_MODEL_ERROR")
            onStateChanged(BodyRendererState.ERROR)
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

    fun markCleanClose() {
        markStage("CLOSED")
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

        val out = base.copyOf()
        for (row in 0..3) {
            out[row] *= pendingWidth
            out[4 + row] *= pendingHeight
            out[8 + row] *= pendingDepth
        }
        tm.setTransform(instance, out)
    }

    private fun markStage(stage: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STAGE, stage)
            .putString(KEY_MODE, if (compatibilityMode) "compat" else "high")
            .commit()
    }
}
