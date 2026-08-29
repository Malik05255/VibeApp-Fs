package com.almi.ai.ui.body

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Skybox
import com.google.android.filament.View
import com.google.android.filament.utils.Float3
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal enum class BodyRendererState { LOADING, READY, ERROR }

/**
 * Native Filament runtime used only by BodyMeasurementActivity.
 *
 * One Engine + one SurfaceView + one complete humanoid GLB. No compatibility figure, no
 * SceneView, no Compose-owned renderer, no second glTF loader. The camera is owned here so
 * measurement hotspots can smoothly focus a body region while retaining 360° orbit and pinch.
 */
internal class PersistentFilamentRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onStateChanged: (BodyRendererState) -> Unit,
) {
    companion object {
        init {
            Filament.init()
            Utils.init()
        }

        private const val BODY_MODEL = "almi3d/almi_humanoid.glb"
    }

    private var viewer: ModelViewer? = null
    private var initialized = false
    private var running = false
    private var framePosted = false
    private var loadPosted = false
    private var readySent = false
    private var styled = false
    private var baseRootTransform: FloatArray? = null

    private var pendingWidth = 1f
    private var pendingHeight = 1f
    private var pendingDepth = 1f

    private var yaw = 0.0
    private var cameraDistance = 2.75
    private var targetCameraDistance = 2.75
    private var cameraTargetY = 0.0
    private var targetCameraY = 0.0
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDistance = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!running) return
            val current = viewer ?: return

            cameraDistance += (targetCameraDistance - cameraDistance) * 0.14
            cameraTargetY += (targetCameraY - cameraTargetY) * 0.14
            updateCamera(current)
            current.render(frameTimeNanos)

            if (!readySent && current.asset != null && current.progress >= 0.96f) {
                readySent = true
                styleHumanoid(current)
                onStateChanged(BodyRendererState.READY)
            } else if (readySent && !styled) {
                styleHumanoid(current)
            }
            postFrame()
        }
    }

    fun initialize() {
        if (initialized) return
        onStateChanged(BodyRendererState.LOADING)
        if (!surfaceView.holder.surface.isValid) {
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    surfaceView.post { initializeOnSurface() }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        } else {
            initializeOnSurface()
        }
    }

    private fun initializeOnSurface() {
        if (initialized || !surfaceView.holder.surface.isValid) return
        initialized = true
        try {
            val engine = Engine.create(Engine.Backend.OPENGL)
            val current = ModelViewer(surfaceView, engine = engine, manipulator = null)
            viewer = current

            current.scene.skybox = Skybox.Builder()
                .color(0.004f, 0.014f, 0.032f, 1f)
                .build(current.engine)

            current.view.renderQuality = current.view.renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }
            current.view.dynamicResolutionOptions = current.view.dynamicResolutionOptions.apply {
                enabled = true
                quality = View.QualityLevel.MEDIUM
            }
            current.view.antiAliasing = View.AntiAliasing.FXAA
            current.view.ambientOcclusionOptions = current.view.ambientOcclusionOptions.apply {
                enabled = true
            }
            current.view.bloomOptions = current.view.bloomOptions.apply {
                enabled = false
            }
            current.view.multiSampleAntiAliasingOptions = current.view.multiSampleAntiAliasingOptions.apply {
                enabled = false
            }

            surfaceView.setOnTouchListener { _, event -> handleTouch(event) }
            if (!loadPosted) {
                loadPosted = true
                surfaceView.postDelayed({ loadHumanoid() }, 120L)
            }
            if (running) postFrame()
        } catch (_: Throwable) {
            onStateChanged(BodyRendererState.ERROR)
            stop()
        }
    }

    private fun loadHumanoid() {
        val current = viewer ?: return
        if (!surfaceView.isAttachedToWindow) return
        try {
            val bytes = context.assets.open(BODY_MODEL).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                flip()
            }
            current.loadModelGlb(buffer)
            current.transformToUnitCube(Float3(0f, 0f, 0f))
            captureBaseTransform(current)
            applyBodyShape()
            updateCamera(current)
        } catch (_: Throwable) {
            onStateChanged(BodyRendererState.ERROR)
        }
    }

    fun updateBodyShape(width: Float, height: Float, depth: Float) {
        pendingWidth = width.coerceIn(.78f, 1.32f)
        pendingHeight = height.coerceIn(.88f, 1.16f)
        pendingDepth = depth.coerceIn(.78f, 1.32f)
        applyBodyShape()
    }

    fun focusOn(normalizedY: Float, distance: Float) {
        targetCameraY = normalizedY.coerceIn(-0.85f, 0.85f).toDouble()
        targetCameraDistance = distance.coerceIn(1.35f, 2.75f).toDouble()
    }

    fun resetFocus() {
        targetCameraY = 0.0
        targetCameraDistance = 2.75
    }

    fun start() {
        if (running) return
        running = true
        if (viewer != null) postFrame()
    }

    fun stop() {
        running = false
        framePosted = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun postFrame() {
        if (!running || framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                pinchDistance = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) pinchDistance = pointerDistance(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val now = pointerDistance(event)
                    if (pinchDistance > 0f && now > 0f) {
                        val ratio = pinchDistance / now
                        targetCameraDistance = (targetCameraDistance * ratio).coerceIn(1.25, 4.0)
                    }
                    pinchDistance = now
                } else {
                    val dx = event.x - lastX
                    yaw += dx.toDouble() * 0.0105
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> pinchDistance = 0f
        }
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private fun updateCamera(current: ModelViewer) {
        val distance = cameraDistance
        val eyeX = sin(yaw) * distance
        val eyeZ = cos(yaw) * distance
        val eyeY = cameraTargetY * 0.18
        current.camera.lookAt(
            eyeX, eyeY, eyeZ,
            0.0, cameraTargetY, 0.0,
            0.0, 1.0, 0.0,
        )
    }

    private fun captureBaseTransform(current: ModelViewer) {
        val asset = current.asset ?: return
        val manager = current.engine.transformManager
        val instance = manager.getInstance(asset.root)
        if (instance == 0) return
        baseRootTransform = FloatArray(16).also { manager.getTransform(instance, it) }
    }

    private fun applyBodyShape() {
        val current = viewer ?: return
        val asset = current.asset ?: return
        val base = baseRootTransform ?: return
        val manager = current.engine.transformManager
        val instance = manager.getInstance(asset.root)
        if (instance == 0) return

        val out = base.copyOf()
        for (row in 0..3) {
            out[row] *= pendingWidth
            out[4 + row] *= pendingHeight
            out[8 + row] *= pendingDepth
        }
        manager.setTransform(instance, out)
    }

    private fun styleHumanoid(current: ModelViewer) {
        val asset = current.asset ?: return
        val rm = current.engine.renderableManager
        var touched = false
        asset.renderableEntities.forEach { entity ->
            val ri = rm.getInstance(entity)
            if (ri == 0) return@forEach
            val primitives = rm.getPrimitiveCount(ri)
            for (primitive in 0 until primitives) {
                val material = rm.getMaterialInstanceAt(ri, primitive)
                runCatching {
                    material.setParameter("baseColorFactor", Colors.RgbaType.SRGB, 0.28f, 0.55f, 0.92f, 1.0f)
                    material.setParameter("metallicFactor", 0.08f)
                    material.setParameter("roughnessFactor", 0.34f)
                    touched = true
                }
            }
        }
        styled = touched
    }
}
