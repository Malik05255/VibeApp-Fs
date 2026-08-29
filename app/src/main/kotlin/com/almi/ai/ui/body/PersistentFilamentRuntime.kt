package com.almi.ai.ui.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.BodySideMeasurement
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal enum class BodyRendererState { LOADING, READY, ERROR }

/**
 * Filament-only renderer for the ALMI body map.
 *
 * The body is kept fully inside the useful phone viewport, supports a short cinematic intro spin,
 * focuses smoothly on a selected measurement, and applies both anthropometric morphs and optional
 * left/right limb asymmetry without replacing Filament or flattening the GLB into a 2D mockup.
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
        private const val READY_WARMUP_FRAMES = 6
        private const val OVERVIEW_DISTANCE = 3.02

        private val LEFT_UPPER_ARM = arrayOf("upperarm01.L", "upperarm.L", "LeftArm", "mixamorig:LeftArm", "DEF-upper_arm.L")
        private val RIGHT_UPPER_ARM = arrayOf("upperarm01.R", "upperarm.R", "RightArm", "mixamorig:RightArm", "DEF-upper_arm.R")
        private val LEFT_LOWER_ARM = arrayOf("lowerarm01.L", "forearm.L", "LeftForeArm", "mixamorig:LeftForeArm", "DEF-forearm.L")
        private val RIGHT_LOWER_ARM = arrayOf("lowerarm01.R", "forearm.R", "RightForeArm", "mixamorig:RightForeArm", "DEF-forearm.R")
        private val LEFT_HAND = arrayOf("hand.L", "wrist.L", "LeftHand", "mixamorig:LeftHand", "DEF-hand.L")
        private val RIGHT_HAND = arrayOf("hand.R", "wrist.R", "RightHand", "mixamorig:RightHand", "DEF-hand.R")
        private val LEFT_UPPER_LEG = arrayOf("upperleg01.L", "upperleg.L", "thigh.L", "LeftUpLeg", "mixamorig:LeftUpLeg", "DEF-thigh.L")
        private val RIGHT_UPPER_LEG = arrayOf("upperleg01.R", "upperleg.R", "thigh.R", "RightUpLeg", "mixamorig:RightUpLeg", "DEF-thigh.R")
        private val LEFT_LOWER_LEG = arrayOf("lowerleg01.L", "lowerleg.L", "shin.L", "LeftLeg", "mixamorig:LeftLeg", "DEF-shin.L")
        private val RIGHT_LOWER_LEG = arrayOf("lowerleg01.R", "lowerleg.R", "shin.R", "RightLeg", "mixamorig:RightLeg", "DEF-shin.R")
        private val LEFT_FOOT = arrayOf("foot.L", "LeftFoot", "mixamorig:LeftFoot", "DEF-foot.L")
        private val RIGHT_FOOT = arrayOf("foot.R", "RightFoot", "mixamorig:RightFoot", "DEF-foot.R")
    }

    private var viewer: ModelViewer? = null
    private var initialized = false
    private var running = false
    private var framePosted = false
    private var loadPosted = false
    private var readySent = false
    private var readyWarmupFrames = 0
    private var verificationRequested = false
    private var baseRootTransform: FloatArray? = null
    private var pendingProfile: BodyProfile? = null
    private val studioLights = mutableListOf<Int>()
    private val baseBoneTransforms = mutableMapOf<Int, FloatArray>()

    private var pendingWidth = 1f
    private var pendingHeight = 1f
    private var pendingDepth = 1f

    private var yaw = 0.0
    private var cameraDistance = OVERVIEW_DISTANCE
    private var targetCameraDistance = OVERVIEW_DISTANCE
    private var overviewDistance = OVERVIEW_DISTANCE
    private var cameraTargetY = -0.03
    private var targetCameraY = -0.03
    private var lastX = 0f
    private var pinchDistance = 0f
    private var focused = false
    private var interactionsEnabled = true

    private var introStartNanos = 0L
    private var introDurationNanos = 0L
    private var introFromYaw = 0.0
    private var introFinished: (() -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!running) return
            val current = viewer ?: return

            updateIntroSpin(frameTimeNanos)
            cameraDistance += (targetCameraDistance - cameraDistance) * 0.14
            cameraTargetY += (targetCameraY - cameraTargetY) * 0.14
            updateCamera(current)
            current.render(frameTimeNanos)

            if (!readySent && current.asset != null && current.progress >= 0.96f) {
                val asset = current.asset
                if (asset == null || asset.renderableEntities.isEmpty()) {
                    failRenderer()
                    return
                }

                if (!ensureBodyRenderableVisible(current)) {
                    failRenderer()
                    return
                }

                applyReferenceMaterial(current)
                hideNamedRenderable(current, "GrowthTrackHair")
                hideNamedRenderable(current, "GrowthTrackEyes")
                hideNamedRenderable(current, "PrivateAnatomy")
                applyMorphs()
                applyAsymmetry()

                readyWarmupFrames += 1
                if (readyWarmupFrames >= READY_WARMUP_FRAMES && !verificationRequested) {
                    verificationRequested = true
                    current.debugGetNextFrameCallback { bitmap ->
                        if (hasVisibleBodyPixels(bitmap)) {
                            readySent = true
                            onStateChanged(BodyRendererState.READY)
                        } else {
                            failRenderer()
                        }
                    }
                }
            }

            postFrame()
        }
    }

    fun initialize() {
        if (initialized) return
        onStateChanged(BodyRendererState.LOADING)

        surfaceView.background = null
        surfaceView.holder.setFormat(PixelFormat.OPAQUE)

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
                ) {
                    refreshOverviewDistance()
                }

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
            surfaceView.background = null
            val engine = Engine.create(Engine.Backend.OPENGL)
            val current = ModelViewer(surfaceView, engine = engine, manipulator = null)
            viewer = current

            current.scene.skybox = Skybox.Builder()
                .color(0.003f, 0.011f, 0.026f, 1f)
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
                enabled = true
            }
            current.view.multiSampleAntiAliasingOptions =
                current.view.multiSampleAntiAliasingOptions.apply {
                    enabled = false
                }

            installStudioLights(current)
            current.camera.setExposure(11.0f, 1.0f / 125.0f, 100.0f)

            surfaceView.setOnTouchListener { _, event -> handleTouch(event) }

            if (!loadPosted) {
                loadPosted = true
                surfaceView.postDelayed({ loadHumanoid() }, 120L)
            }

            if (running) postFrame()
        } catch (_: Throwable) {
            failRenderer()
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
            red = 0.96f,
            green = 0.98f,
            blue = 1.00f,
            intensity = 66_000f,
            x = -0.50f,
            y = -0.62f,
            z = -0.70f,
            shadows = true,
        )
        addDirectional(
            red = 0.70f,
            green = 0.84f,
            blue = 1.00f,
            intensity = 24_000f,
            x = 0.72f,
            y = -0.26f,
            z = -0.62f,
            shadows = false,
        )
        addDirectional(
            red = 0.72f,
            green = 0.86f,
            blue = 1.00f,
            intensity = 18_000f,
            x = -0.16f,
            y = 0.30f,
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
                failRenderer()
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
            applyAsymmetry()
            refreshOverviewDistance()
            updateCamera(current)
        } catch (_: Throwable) {
            failRenderer()
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
                    0.20f,
                    0.39f,
                    0.62f,
                    0.82f,
                )
                material.setParameter("metallicFactor", 0.02f)
                material.setParameter("roughnessFactor", 0.38f)
                material.setParameter(
                    "emissiveFactor",
                    Colors.RgbType.LINEAR,
                    0.010f,
                    0.028f,
                    0.070f,
                )
                material.setParameter("emissiveStrength", 0.55f)
                material.setParameter("reflectance", 0.34f)
            }
        }
    }

    private fun hasVisibleBodyPixels(bitmap: Bitmap): Boolean {
        if (bitmap.width <= 0 || bitmap.height <= 0) return false

        val stepX = (bitmap.width / 72).coerceAtLeast(1)
        val stepY = (bitmap.height / 128).coerceAtLeast(1)
        var samples = 0
        var bodyLike = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val red = android.graphics.Color.red(pixel)
                val green = android.graphics.Color.green(pixel)
                val blue = android.graphics.Color.blue(pixel)
                if (blue >= 42 && blue > red + 10 && blue > green + 3) bodyLike += 1
                samples += 1
                x += stepX
            }
            y += stepY
        }
        return samples > 0 && bodyLike.toFloat() / samples >= 0.0022f
    }

    private fun failRenderer() {
        if (readySent) return
        onStateChanged(BodyRendererState.ERROR)
        stop()
    }

    private fun hideNamedRenderable(current: ModelViewer, name: String) {
        val asset = current.asset ?: return
        val entity = asset.getFirstEntityByName(name)
        if (entity == 0) return
        val renderableManager = current.engine.renderableManager
        val instance = renderableManager.getInstance(entity)
        if (instance != 0) runCatching { renderableManager.setLayerMask(instance, 0xFF, 0x00) }
    }

    fun updateBodyShape(width: Float, height: Float, depth: Float) {
        pendingWidth = width.coerceIn(.78f, 1.32f)
        pendingHeight = height.coerceIn(.88f, 1.16f)
        pendingDepth = depth.coerceIn(.78f, 1.32f)
        applyBodyShape()
        refreshOverviewDistance()
    }

    fun updateProfile(profile: BodyProfile) {
        pendingProfile = profile
        applyMorphs()
        applyAsymmetry()
        refreshOverviewDistance()
    }

    fun playIntroSpin(durationMs: Long = 2_200L, onFinished: () -> Unit) {
        focused = false
        interactionsEnabled = false
        targetCameraY = -0.03
        targetCameraDistance = overviewDistance
        introStartNanos = 0L
        introDurationNanos = durationMs.coerceAtLeast(700L) * 1_000_000L
        introFromYaw = yaw
        introFinished = onFinished
    }

    fun focusOn(normalizedY: Float, distance: Float) {
        focused = true
        targetCameraY = normalizedY.coerceIn(-0.85f, 0.85f).toDouble()
        targetCameraDistance = distance.coerceIn(1.20f, 2.20f).toDouble()
    }

    fun resetFocus() {
        focused = false
        targetCameraY = -0.03
        targetCameraDistance = overviewDistance
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

    private fun updateIntroSpin(frameTimeNanos: Long) {
        if (introDurationNanos <= 0L) return
        if (introStartNanos == 0L) introStartNanos = frameTimeNanos
        val progress = ((frameTimeNanos - introStartNanos).toDouble() / introDurationNanos.toDouble()).coerceIn(0.0, 1.0)
        val eased = progress * progress * (3.0 - 2.0 * progress)
        yaw = introFromYaw + eased * PI * 2.0
        if (progress >= 1.0) {
            yaw = 0.0
            introDurationNanos = 0L
            introStartNanos = 0L
            interactionsEnabled = true
            val callback = introFinished
            introFinished = null
            if (callback != null) surfaceView.post(callback)
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        if (!interactionsEnabled) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                pinchDistance = 0f
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) pinchDistance = pointerDistance(event)
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val now = pointerDistance(event)
                    if (pinchDistance > 0f && now > 0f) {
                        targetCameraDistance =
                            (targetCameraDistance * (pinchDistance / now)).coerceIn(1.20, 4.50)
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

    private fun refreshOverviewDistance() {
        val profile = pendingProfile
        val sideExtra = if (profile == null) 1f else {
            val leg = sideRatioMagnitude(profile, BodySideMeasurement.LEFT_INSEAM, BodySideMeasurement.RIGHT_INSEAM)
            val arm = sideRatioMagnitude(profile, BodySideMeasurement.LEFT_ARM_LENGTH, BodySideMeasurement.RIGHT_ARM_LENGTH)
            maxOf(leg, arm)
        }
        val widthPenalty = 1f + (pendingWidth - 1f).coerceAtLeast(0f) * .16f
        val heightPenalty = 1f + (pendingHeight - 1f).coerceAtLeast(0f) * .72f
        val shortScreenPenalty = if (surfaceView.width > 0 && surfaceView.height > 0) {
            val aspect = surfaceView.width.toFloat() / surfaceView.height.toFloat()
            if (aspect > .55f) 1.06f else 1f
        } else 1f
        overviewDistance = (OVERVIEW_DISTANCE * widthPenalty * heightPenalty * sideExtra * shortScreenPenalty)
            .coerceIn(2.88, 3.80)
        if (!focused && introDurationNanos <= 0L) targetCameraDistance = overviewDistance
    }

    private fun sideRatioMagnitude(
        profile: BodyProfile,
        leftKey: BodySideMeasurement,
        rightKey: BodySideMeasurement,
    ): Float {
        val left = profile.sideMeasurementsInches[leftKey]
        val right = profile.sideMeasurementsInches[rightKey]
        if (left == null || right == null || left <= 0f || right <= 0f) return 1f
        val average = (left + right) / 2f
        if (average <= 0f) return 1f
        return maxOf(left / average, right / average).coerceIn(1f, 1.14f)
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

    private fun applyAsymmetry() {
        val current = viewer ?: return
        val asset = current.asset ?: return
        val profile = pendingProfile ?: return
        val manager = current.engine.transformManager

        baseBoneTransforms.forEach { (entity, transform) ->
            val instance = manager.getInstance(entity)
            if (instance != 0) runCatching { manager.setTransform(instance, transform) }
        }

        fun ratioPair(
            leftKey: BodySideMeasurement,
            rightKey: BodySideMeasurement,
            fallback: BodyMeasurePoint,
        ): Pair<Float, Float>? {
            val explicitLeft = profile.sideMeasurementsInches[leftKey]
            val explicitRight = profile.sideMeasurementsInches[rightKey]
            if (explicitLeft == null && explicitRight == null) return null
            val generic = profile.measurementsInches[fallback]
            val left = explicitLeft ?: generic ?: explicitRight ?: return null
            val right = explicitRight ?: generic ?: explicitLeft ?: return null
            val average = (left + right) / 2f
            if (average <= 0f) return null
            return (left / average).coerceIn(.86f, 1.14f) to (right / average).coerceIn(.86f, 1.14f)
        }

        ratioPair(
            BodySideMeasurement.LEFT_ARM_LENGTH,
            BodySideMeasurement.RIGHT_ARM_LENGTH,
            BodyMeasurePoint.ARM_LENGTH,
        )?.let { (left, right) ->
            scaleBoneY(asset, manager, LEFT_UPPER_ARM, left)
            scaleBoneY(asset, manager, LEFT_LOWER_ARM, left)
            scaleBoneY(asset, manager, RIGHT_UPPER_ARM, right)
            scaleBoneY(asset, manager, RIGHT_LOWER_ARM, right)
        }

        ratioPair(
            BodySideMeasurement.LEFT_HAND_LENGTH,
            BodySideMeasurement.RIGHT_HAND_LENGTH,
            BodyMeasurePoint.HAND,
        )?.let { (left, right) ->
            scaleBoneY(asset, manager, LEFT_HAND, left)
            scaleBoneY(asset, manager, RIGHT_HAND, right)
        }

        ratioPair(
            BodySideMeasurement.LEFT_INSEAM,
            BodySideMeasurement.RIGHT_INSEAM,
            BodyMeasurePoint.INSEAM,
        )?.let { (left, right) ->
            scaleBoneY(asset, manager, LEFT_UPPER_LEG, left)
            scaleBoneY(asset, manager, LEFT_LOWER_LEG, left)
            scaleBoneY(asset, manager, RIGHT_UPPER_LEG, right)
            scaleBoneY(asset, manager, RIGHT_LOWER_LEG, right)
        }

        ratioPair(
            BodySideMeasurement.LEFT_FOOT_LENGTH,
            BodySideMeasurement.RIGHT_FOOT_LENGTH,
            BodyMeasurePoint.FOOT,
        )?.let { (left, right) ->
            scaleBoneY(asset, manager, LEFT_FOOT, left)
            scaleBoneY(asset, manager, RIGHT_FOOT, right)
        }
    }

    private fun scaleBoneY(
        asset: com.google.android.filament.gltfio.FilamentAsset,
        manager: com.google.android.filament.TransformManager,
        candidates: Array<String>,
        ratio: Float,
    ) {
        val entity = candidates.asSequence()
            .map { asset.getFirstEntityByName(it) }
            .firstOrNull { it != 0 } ?: return
        val instance = manager.getInstance(entity)
        if (instance == 0) return
        val base = baseBoneTransforms.getOrPut(entity) {
            FloatArray(16).also { manager.getTransform(instance, it) }
        }
        val out = base.copyOf()
        for (row in 0..3) out[4 + row] *= ratio
        runCatching { manager.setTransform(instance, out) }
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
            if (index >= 0) weights[index] = value.coerceIn(0f, 1f)
        }

        fun cm(point: BodyMeasurePoint): Float? = profile.measurementsInches[point]?.times(INCH_TO_CM)

        val kg = profile.weightPounds * POUND_TO_KG
        val mass = ((kg - 50f) / 92f).coerceIn(0f, 1f)
        set("overall_mass", mass)
        set(
            "gut_volume",
            maxOf(
                ((kg - 70f) / 62f).coerceIn(0f, .92f),
                cm(BodyMeasurePoint.WAIST)?.let { ((it - 84f) / 52f).coerceIn(0f, .92f) } ?: 0f,
            ),
        )
        set("face_roundness", (mass * .48f).coerceIn(0f, .62f))
        set("shoulder_drop", 0.30f)
        set("hand_splay", 0.08f)

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
        cm(BodyMeasurePoint.HAND)?.let { set("hand_length", ((it - 16f) / 12f).coerceIn(0f, 1f)) }
        cm(BodyMeasurePoint.THIGH)?.let { set("quad_sweep", ((it - 48f) / 38f).coerceIn(0f, .9f)) }
        cm(BodyMeasurePoint.INSEAM)?.let { set("leg_length", ((it - 70f) / 45f).coerceIn(0f, .85f)) }
        cm(BodyMeasurePoint.CALF)?.let { set("calf_diamond", ((it - 31f) / 24f).coerceIn(0f, .9f)) }
        cm(BodyMeasurePoint.NECK)?.let { set("neck_thickness", ((it - 32f) / 24f).coerceIn(0f, .9f)) }
        cm(BodyMeasurePoint.FOOT)?.let { set("foot_length", ((it - 22f) / 12f).coerceIn(0f, .9f)) }

        val renderableManager = current.engine.renderableManager
        val instance = renderableManager.getInstance(bodyEntity)
        if (instance != 0) runCatching { renderableManager.setMorphWeights(instance, weights, 0) }
    }
}
