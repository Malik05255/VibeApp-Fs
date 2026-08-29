package com.almi.ai.ui.body

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.view.Choreographer
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
import kotlin.math.sin

internal enum class BodyRendererState { LOADING, READY, ERROR }

/**
 * Filament renderer for the calibrated body-measurement surface.
 *
 * Measurement mode intentionally returns to and stays on the front camera after the cinematic
 * intro. The red measurement overlay is a calibrated front-view layer, so allowing free orbit
 * would make otherwise-correct anatomical markers drift away from the body.
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
    private var focused = false

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

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
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

            // Lighter blue stage that matches the current ALMI measurement mockup.
            current.scene.skybox = Skybox.Builder()
                .color(0.018f, 0.070f, 0.155f, 1f)
                .build(current.engine)

            current.view.renderQuality = current.view.renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }

            // Dynamic resolution and bloom were the two post-processing paths most likely to create
            // the colored right-edge flash / shimmer seen on the real device. Measurement mode is
            // static enough that deterministic native resolution is preferable.
            current.view.dynamicResolutionOptions = current.view.dynamicResolutionOptions.apply {
                enabled = false
            }
            current.view.bloomOptions = current.view.bloomOptions.apply {
                enabled = false
            }
            current.view.antiAliasing = View.AntiAliasing.FXAA
            current.view.ambientOcclusionOptions = current.view.ambientOcclusionOptions.apply {
                enabled = true
            }
            current.view.multiSampleAntiAliasingOptions =
                current.view.multiSampleAntiAliasingOptions.apply { enabled = false }

            installStudioLights(current)
            current.camera.setExposure(10.5f, 1.0f / 125.0f, 100.0f)

            // Consume touches on the Filament surface. The calibrated measurement markers are for
            // the front view; free orbit would make the 2D anatomical overlay incorrect.
            surfaceView.setOnTouchListener { _, _ -> true }

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

        // Soft, balanced clinical light: enough surface definition without hot edge flares.
        addDirectional(0.98f, 0.99f, 1.00f, 48_000f, -0.42f, -0.64f, -0.66f, true)
        addDirectional(0.78f, 0.88f, 1.00f, 19_000f, 0.66f, -0.22f, -0.70f, false)
        addDirectional(0.72f, 0.84f, 1.00f, 10_000f, -0.12f, 0.28f, 0.94f, false)
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
                    0.62f,
                    0.79f,
                    0.97f,
                    0.90f,
                )
                material.setParameter("metallicFactor", 0.00f)
                material.setParameter("roughnessFactor", 0.32f)
                material.setParameter(
                    "emissiveFactor",
                    Colors.RgbType.LINEAR,
                    0.012f,
                    0.028f,
                    0.050f,
                )
                material.setParameter("emissiveStrength", 0.18f)
                material.setParameter("reflectance", 0.28f)
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
                // Pale icy body pixels are substantially brighter than the blue stage.
                if (red >= 72 && green >= 100 && blue >= 145 && blue > green + 12) bodyLike += 1
                samples += 1
                x += stepX
            }
            y += stepY
        }
        return samples > 0 && bodyLike.toFloat() / samples >= 0.0015f
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

    fun playIntroSpin(durationMs: Long = 2_100L, onFinished: () -> Unit) {
        focused = false
        targetCameraY = -0.03
        targetCameraDistance = overviewDistance
        introStartNanos = 0L
        introDurationNanos = durationMs.coerceAtLeast(700L) * 1_000_000L
        introFromYaw = yaw
        introFinished = onFinished
    }

    /** Kept for the legacy internal activity; dressmaker mode itself stays front-calibrated. */
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
            val callback = introFinished
            introFinished = null
            if (callback != null) surfaceView.post(callback)
        }
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
            BodySideMeasurement.LEFT_UPPER_ARM,
            BodySideMeasurement.RIGHT_UPPER_ARM,
            BodyMeasurePoint.UPPER_ARM,
        )?.let { (left, right) ->
            scaleBoneRadial(asset, manager, LEFT_UPPER_ARM, left.coerceIn(.90f, 1.10f))
            scaleBoneRadial(asset, manager, RIGHT_UPPER_ARM, right.coerceIn(.90f, 1.10f))
        }

        ratioPair(
            BodySideMeasurement.LEFT_WRIST,
            BodySideMeasurement.RIGHT_WRIST,
            BodyMeasurePoint.WRIST,
        )?.let { (left, right) ->
            scaleBoneRadial(asset, manager, LEFT_LOWER_ARM, left.coerceIn(.92f, 1.08f))
            scaleBoneRadial(asset, manager, RIGHT_LOWER_ARM, right.coerceIn(.92f, 1.08f))
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

    private fun resolveBone(
        asset: com.google.android.filament.gltfio.FilamentAsset,
        manager: com.google.android.filament.TransformManager,
        candidates: Array<String>,
    ): Pair<Int, Int>? {
        val entity = candidates.asSequence()
            .map { asset.getFirstEntityByName(it) }
            .firstOrNull { it != 0 } ?: return null
        val instance = manager.getInstance(entity)
        if (instance == 0) return null
        baseBoneTransforms.getOrPut(entity) {
            FloatArray(16).also { manager.getTransform(instance, it) }
        }
        return entity to instance
    }

    private fun scaleBoneY(
        asset: com.google.android.filament.gltfio.FilamentAsset,
        manager: com.google.android.filament.TransformManager,
        candidates: Array<String>,
        ratio: Float,
    ) {
        val (_, instance) = resolveBone(asset, manager, candidates) ?: return
        val out = FloatArray(16).also { manager.getTransform(instance, it) }
        for (row in 0..3) out[4 + row] *= ratio
        runCatching { manager.setTransform(instance, out) }
    }

    private fun scaleBoneRadial(
        asset: com.google.android.filament.gltfio.FilamentAsset,
        manager: com.google.android.filament.TransformManager,
        candidates: Array<String>,
        ratio: Float,
    ) {
        val (_, instance) = resolveBone(asset, manager, candidates) ?: return
        val out = FloatArray(16).also { manager.getTransform(instance, it) }
        for (row in 0..3) {
            out[row] *= ratio
            out[8 + row] *= ratio
        }
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
        val waistVolume = cm(BodyMeasurePoint.WAIST)?.let { ((it - 76f) / 58f).coerceIn(0f, .90f) } ?: 0f
        val abdomenVolume = cm(BodyMeasurePoint.ABDOMEN)?.let { ((it - 80f) / 60f).coerceIn(0f, .95f) } ?: 0f

        set("overall_mass", mass)
        set(
            "gut_volume",
            maxOf(
                ((kg - 68f) / 64f).coerceIn(0f, .92f),
                waistVolume,
                abdomenVolume,
            ),
        )
        set("face_roundness", (mass * .46f).coerceIn(0f, .60f))
        set("shoulder_drop", 0.28f)
        set("hand_splay", 0.06f)

        cm(BodyMeasurePoint.NECK)?.let {
            set("neck_thickness", ((it - 30f) / 22f).coerceIn(0f, .90f))
        }
        cm(BodyMeasurePoint.SHOULDERS)?.let {
            set("clavicle_width", ((it - 34f) / 28f).coerceIn(0f, 1f))
            set("deltoid_width", ((it - 38f) / 30f).coerceIn(0f, .75f))
        }
        cm(BodyMeasurePoint.SHOULDER_LENGTH)?.let {
            set("shoulder_slope", ((it - 10f) / 10f).coerceIn(0f, .70f))
        }
        cm(BodyMeasurePoint.CHEST)?.let {
            set("chest_depth", ((it - 78f) / 58f).coerceIn(0f, 1f))
            set("ribcage_depth", ((it - 76f) / 60f).coerceIn(0f, .90f))
        }
        cm(BodyMeasurePoint.UNDERBUST)?.let {
            set("ribcage_depth", ((it - 66f) / 50f).coerceIn(0f, .90f))
        }
        cm(BodyMeasurePoint.BUST_HEIGHT)?.let {
            set("torso_length", ((it - 20f) / 24f).coerceIn(0f, .65f))
        }
        cm(BodyMeasurePoint.WAIST)?.let {
            set("waist_narrow", ((86f - it) / 34f).coerceIn(0f, 1f))
            set("oblique_def", ((90f - it) / 40f).coerceIn(0f, .55f))
        }
        cm(BodyMeasurePoint.HIPS)?.let {
            set("hip_width", ((it - 82f) / 54f).coerceIn(0f, 1f))
            set("pelvis_width", ((it - 82f) / 54f).coerceIn(0f, .90f))
            set("glute_volume", ((it - 86f) / 58f).coerceIn(0f, .85f))
        }
        cm(BodyMeasurePoint.ARM_LENGTH)?.let {
            val arm = ((it - 50f) / 38f).coerceIn(0f, .82f)
            set("upper_arm_length", arm * .52f)
            set("forearm_length", arm * .48f)
        }
        cm(BodyMeasurePoint.UPPER_ARM)?.let {
            val girth = ((it - 22f) / 28f).coerceIn(0f, .90f)
            set("bicep_peak", girth)
            set("tricep_horse", girth * .82f)
        }
        cm(BodyMeasurePoint.WRIST)?.let {
            set("forearm_girth", ((it - 13f) / 18f).coerceIn(0f, .65f))
        }

        // Preserve advanced channels from older profiles even though they are no longer shown as
        // primary dressmaker hotspots.
        cm(BodyMeasurePoint.HAND)?.let { set("hand_length", ((it - 16f) / 12f).coerceIn(0f, 1f)) }
        cm(BodyMeasurePoint.THIGH)?.let { set("quad_sweep", ((it - 48f) / 38f).coerceIn(0f, .9f)) }
        cm(BodyMeasurePoint.INSEAM)?.let { set("leg_length", ((it - 70f) / 45f).coerceIn(0f, .85f)) }
        cm(BodyMeasurePoint.CALF)?.let { set("calf_diamond", ((it - 31f) / 24f).coerceIn(0f, .9f)) }
        cm(BodyMeasurePoint.FOOT)?.let { set("foot_length", ((it - 22f) / 12f).coerceIn(0f, .9f)) }

        val renderableManager = current.engine.renderableManager
        val instance = renderableManager.getInstance(bodyEntity)
        if (instance != 0) runCatching { renderableManager.setMorphWeights(instance, weights, 0) }
    }
}
