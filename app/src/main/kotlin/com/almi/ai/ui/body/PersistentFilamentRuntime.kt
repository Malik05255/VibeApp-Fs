package com.almi.ai.ui.body

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
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
 * Filament-only renderer for the ALMI body map.
 *
 * The GLB is prepared at build time with a self-emissive translucent body material. The runtime
 * also re-applies the visible blue body parameters as a safety net, hides non-reference hair and
 * private geometry, and does not release the measurement overlay until the Body renderable has
 * survived several rendered frames.
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
        private const val INCH_TO_CM = 2.54f
        private const val POUND_TO_KG = 0.45359237f
        private const val READY_WARMUP_FRAMES = 4
    }

    private var viewer: ModelViewer? = null
    private var initialized = false
    private var running = false
    private var framePosted = false
    private var loadPosted = false
    private var readySent = false
    private var readyWarmupFrames = 0
    private var baseRootTransform: FloatArray? = null
    private var pendingProfile: BodyProfile? = null
    private val studioLights = mutableListOf<Int>()

    private var pendingWidth = 1f
    private var pendingHeight = 1f
    private var pendingDepth = 1f

    private var yaw = 0.0
    private var cameraDistance = 2.15
    private var targetCameraDistance = 2.15
    private var cameraTargetY = 0.0
    private var targetCameraY = 0.0
    private var lastX = 0f
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
                val asset = current.asset
                if (asset == null || asset.renderableEntities.isEmpty()) {
                    onStateChanged(BodyRendererState.ERROR)
                    stop()
                    return
                }

                if (!ensureBodyRenderableVisible(current)) {
                    onStateChanged(BodyRendererState.ERROR)
                    stop()
                    return
                }

                applyReferenceMaterial(current)
                hideNamedRenderable(current, "GrowthTrackHair")
                hideNamedRenderable(current, "PrivateAnatomy")
                applyMorphs()

                readyWarmupFrames += 1
                if (readyWarmupFrames >= READY_WARMUP_FRAMES) {
                    readySent = true
                    onStateChanged(BodyRendererState.READY)
                }
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

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int,
                ) = Unit

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
                enabled = false
            }
            current.view.bloomOptions = current.view.bloomOptions.apply {
                enabled = true
            }
            current.view.multiSampleAntiAliasingOptions =
                current.view.multiSampleAntiAliasingOptions.apply {
                    enabled = false
                }

            installStudioLights(current)

            // Brighter, deterministic exposure. The body is also emissive, so it remains readable
            // even if a device's directional-light path behaves differently.
            current.camera.setExposure(8.0f, 1.0f / 125.0f, 100.0f)

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

    private fun installStudioLights(current: ModelViewer) {
        if (studioLights.isNotEmpty()) return

        fun addDirectional(
            red: Float,
            green: Float,
            blue: Float,
            intensity: Float,
            x: Float,
            y: Float,
            z: Float,
            shadows: Boolean,
        ) {
            val entity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(red, green, blue)
                .intensity(intensity)
                .direction(x, y, z)
                .castShadows(shadows)
                .build(current.engine, entity)
            current.scene.addEntity(entity)
            studioLights += entity
        }

        addDirectional(
            red = 1.00f,
            green = 0.96f,
            blue = 0.92f,
            intensity = 95_000f,
            x = -0.52f,
            y = -0.66f,
            z = -0.73f,
            shadows = true,
        )
        addDirectional(
            red = 0.78f,
            green = 0.88f,
            blue = 1.00f,
            intensity = 38_000f,
            x = 0.76f,
            y = -0.30f,
            z = -0.58f,
            shadows = false,
        )
        addDirectional(
            red = 1.00f,
            green = 0.82f,
            blue = 0.70f,
            intensity = 27_000f,
            x = -0.18f,
            y = 0.34f,
            z = 0.92f,
            shadows = false,
        )
    }

    private fun loadHumanoid() {
        val current = viewer ?: return
        if (!surfaceView.isAttachedToWindow) return

        try {
            val bytes = context.assets.open(BODY_MODEL).use { it.readBytes() }
            if (bytes.size < 1_000_000) {
                onStateChanged(BodyRendererState.ERROR)
                return
            }

            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                flip()
            }

            current.loadModelGlb(buffer)
            current.transformToUnitCube(Float3(0f, 0f, 0f))
            captureBaseTransform(current)
            applyBodyShape()
            applyMorphs()
            updateCamera(current)
        } catch (_: Throwable) {
            onStateChanged(BodyRendererState.ERROR)
        }
    }

    private fun ensureBodyRenderableVisible(current: ModelViewer): Boolean {
        val asset = current.asset ?: return false
        val bodyEntity = asset.getFirstEntityByName("Body")
        if (bodyEntity == 0) return false
        val renderableManager = current.engine.renderableManager
        val instance = renderableManager.getInstance(bodyEntity)
        if (instance == 0) return false
        return runCatching {
            renderableManager.setLayerMask(instance, 0xFF, 0xFF)
            renderableManager.getPrimitiveCount(instance) > 0
        }.getOrDefault(false)
    }

    /**
     * Runtime safety net for the body appearance. The build-time GLB patch is authoritative, but
     * these parameters make the Body primitive visible even when a device retains source PBR
     * values while the asset textures are still streaming.
     */
    private fun applyReferenceMaterial(current: ModelViewer) {
        val asset = current.asset ?: return
        val bodyEntity = asset.getFirstEntityByName("Body")
        if (bodyEntity == 0) return
        val renderableManager = current.engine.renderableManager
        val instance = renderableManager.getInstance(bodyEntity)
        if (instance == 0) return

        val primitiveCount = renderableManager.getPrimitiveCount(instance)
        for (primitive in 0 until primitiveCount) {
            val material = renderableManager.getMaterialInstanceAt(instance, primitive)
            runCatching {
                material.setParameter(
                    "baseColorFactor",
                    Colors.RgbaType.SRGB,
                    0.28f,
                    0.54f,
                    0.96f,
                    0.82f,
                )
                material.setParameter("metallicFactor", 0.02f)
                material.setParameter("roughnessFactor", 0.22f)
            }
        }
    }

    private fun hideNamedRenderable(current: ModelViewer, name: String) {
        val asset = current.asset ?: return
        val entity = asset.getFirstEntityByName(name)
        if (entity == 0) return
        val renderableManager = current.engine.renderableManager
        val instance = renderableManager.getInstance(entity)
        if (instance != 0) {
            runCatching { renderableManager.setLayerMask(instance, 0xFF, 0x00) }
        }
    }

    fun updateBodyShape(width: Float, height: Float, depth: Float) {
        pendingWidth = width.coerceIn(.78f, 1.32f)
        pendingHeight = height.coerceIn(.88f, 1.16f)
        pendingDepth = depth.coerceIn(.78f, 1.32f)
        applyBodyShape()
    }

    fun updateProfile(profile: BodyProfile) {
        pendingProfile = profile
        applyMorphs()
    }

    fun focusOn(normalizedY: Float, distance: Float) {
        targetCameraY = normalizedY.coerceIn(-0.85f, 0.85f).toDouble()
        targetCameraDistance = distance.coerceIn(1.30f, 2.45f).toDouble()
    }

    fun resetFocus() {
        targetCameraY = 0.0
        targetCameraDistance = 2.15
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
                pinchDistance = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pinchDistance = pointerDistance(event)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val now = pointerDistance(event)
                    if (pinchDistance > 0f && now > 0f) {
                        targetCameraDistance =
                            (targetCameraDistance * (pinchDistance / now)).coerceIn(1.20, 3.6)
                    }
                    pinchDistance = now
                } else {
                    val dx = event.x - lastX
                    yaw += dx.toDouble() * 0.0105
                    lastX = event.x
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> pinchDistance = 0f
        }
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(
            event.getX(0) - event.getX(1),
            event.getY(0) - event.getY(1),
        )
    }

    private fun updateCamera(current: ModelViewer) {
        val distance = cameraDistance
        current.camera.lookAt(
            sin(yaw) * distance,
            cameraTargetY * 0.18,
            cos(yaw) * distance,
            0.0,
            cameraTargetY,
            0.0,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun captureBaseTransform(current: ModelViewer) {
        val asset = current.asset ?: return
        val manager = current.engine.transformManager
        val instance = manager.getInstance(asset.root)
        if (instance == 0) return
        baseRootTransform = FloatArray(16).also {
            manager.getTransform(instance, it)
        }
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

    private fun applyMorphs() {
        val current = viewer ?: return
        val asset = current.asset ?: return
        val profile = pendingProfile ?: return
        val bodyEntity = asset.getFirstEntityByName("Body")
        if (bodyEntity == 0) return

        val names = asset.getMorphTargetNames(bodyEntity)
        if (names.isEmpty()) return
        val weights = FloatArray(names.size)

        fun set(name: String, value: Float) {
            val index = names.indexOf(name)
            if (index >= 0) {
                weights[index] = value.coerceIn(0f, 1f)
            }
        }

        fun cm(point: BodyMeasurePoint): Float? =
            profile.measurementsInches[point]?.times(INCH_TO_CM)

        val kg = profile.weightPounds * POUND_TO_KG
        val mass = ((kg - 62f) / 70f).coerceIn(0f, 1f)
        set("overall_mass", mass)
        set(
            "gut_volume",
            maxOf(
                ((kg - 78f) / 55f).coerceIn(0f, .85f),
                cm(BodyMeasurePoint.WAIST)?.let {
                    ((it - 86f) / 50f).coerceIn(0f, .85f)
                } ?: 0f,
            ),
        )
        set("face_roundness", (mass * .42f).coerceIn(0f, .55f))

        cm(BodyMeasurePoint.SHOULDERS)?.let {
            set("clavicle_width", ((it - 38f) / 26f).coerceIn(0f, 1f))
            set("deltoid_width", ((it - 42f) / 28f).coerceIn(0f, .8f))
        }
        cm(BodyMeasurePoint.CHEST)?.let {
            set("chest_depth", ((it - 88f) / 55f).coerceIn(0f, 1f))
            set("pec_thickness", ((it - 92f) / 50f).coerceIn(0f, .85f))
        }
        cm(BodyMeasurePoint.WAIST)?.let {
            set("waist_narrow", ((88f - it) / 36f).coerceIn(0f, 1f))
            set("oblique_def", ((92f - it) / 42f).coerceIn(0f, .65f))
        }
        cm(BodyMeasurePoint.HIPS)?.let {
            set("hip_width", ((it - 88f) / 48f).coerceIn(0f, 1f))
            set("glute_volume", ((it - 92f) / 52f).coerceIn(0f, .85f))
        }
        cm(BodyMeasurePoint.ARM_LENGTH)?.let {
            val arm = ((it - 52f) / 35f).coerceIn(0f, .8f)
            set("upper_arm_length", arm * .52f)
            set("forearm_length", arm * .48f)
        }
        cm(BodyMeasurePoint.HAND)?.let {
            set("hand_length", ((it - 16f) / 12f).coerceIn(0f, 1f))
        }
        cm(BodyMeasurePoint.THIGH)?.let {
            set("quad_sweep", ((it - 48f) / 38f).coerceIn(0f, .9f))
        }
        cm(BodyMeasurePoint.INSEAM)?.let {
            set("leg_length", ((it - 70f) / 45f).coerceIn(0f, .85f))
        }
        cm(BodyMeasurePoint.CALF)?.let {
            set("calf_diamond", ((it - 31f) / 24f).coerceIn(0f, .9f))
        }
        cm(BodyMeasurePoint.NECK)?.let {
            set("neck_thickness", ((it - 32f) / 24f).coerceIn(0f, .9f))
        }
        cm(BodyMeasurePoint.FOOT)?.let {
            set("foot_length", ((it - 22f) / 12f).coerceIn(0f, .9f))
        }

        val renderableManager = current.engine.renderableManager
        val instance = renderableManager.getInstance(bodyEntity)
        if (instance != 0) {
            runCatching {
                renderableManager.setMorphWeights(instance, weights, 0)
            }
        }
    }
}
