package com.almi.ai.ui.body

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal enum class V11BodyState { LOADING, READY, ERROR }

internal data class V11ScreenPoint(
    val x: Float,
    val y: Float,
    val visible: Boolean = true,
)

internal data class V11BodyProjection(
    val points: Map<String, V11ScreenPoint>,
    val yawRadians: Double,
) {
    operator fun get(name: String): V11ScreenPoint? = points[name]
}

/**
 * ALMI v11 body renderer.
 *
 * Priorities are deliberately strict:
 * 1. full high-density body mesh;
 * 2. stable, neutral material and lighting;
 * 3. anatomical landmark projection derived from the rig;
 * 4. no decorative rendering feature that can cost stability or battery.
 */
internal class V11BodyRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onStateChanged: (V11BodyState) -> Unit,
    private val onProjectionChanged: (V11BodyProjection) -> Unit,
) {
    companion object {
        init {
            Filament.init()
            Utils.init()
        }

        private const val MODEL = "almi3d/almi_humanoid.glb"
        private const val OVERVIEW_DISTANCE = 3.04
        private const val INCH_TO_CM = 2.54f
        private const val POUND_TO_KG = 0.45359237f

        private val RIG_BONES = arrayOf(
            "Hips", "Spine", "Spine1", "Spine2", "Neck", "Head",
            "LeftShoulder", "LeftUpperArm", "LeftForeArm", "LeftHand",
            "RightShoulder", "RightUpperArm", "RightForeArm", "RightHand",
            "LeftUpLeg", "LeftLeg", "LeftFoot",
            "RightUpLeg", "RightLeg", "RightFoot",
        )
    }

    private var viewer: ModelViewer? = null
    private var initialized = false
    private var running = false
    private var framePosted = false
    private var ready = false
    private var readyFrames = 0
    private var projectionFrame = 0

    private var baseRootTransform: FloatArray? = null
    private var profile = BodyProfile()
    private var shapeWidth = 1f
    private var shapeHeight = 1f
    private var shapeDepth = 1f

    private var yaw = 0.0
    private var cameraDistance = OVERVIEW_DISTANCE
    private var targetDistance = OVERVIEW_DISTANCE
    private var cameraY = -0.03
    private var targetY = -0.03
    private var focused = false

    private var lastX = 0f
    private var downX = 0f
    private var downY = 0f
    private var pinchDistance = 0f
    private var lastTapMs = 0L

    private val lowPowerDevice: Boolean by lazy {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.isLowRamDevice == true || Runtime.getRuntime().availableProcessors() <= 4
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!running) return
            val current = viewer ?: return

            cameraDistance += (targetDistance - cameraDistance) * .16
            cameraY += (targetY - cameraY) * .16
            updateCamera(current)
            current.render(frameTimeNanos)

            if (!ready && current.asset != null && current.progress >= .97f) {
                readyFrames += 1
                if (readyFrames >= 4) {
                    if (!prepareBody(current)) {
                        fail()
                        return
                    }
                    ready = true
                    onStateChanged(V11BodyState.READY)
                    dispatchProjection(current)
                }
            } else if (ready) {
                // Markers do not need 60 projection solves per second. 30Hz preserves visual lock
                // while removing a large amount of matrix work on mid-range devices.
                projectionFrame += 1
                if (projectionFrame % 2 == 0) dispatchProjection(current)
            }
            postFrame()
        }
    }

    fun initialize() {
        if (initialized) return
        onStateChanged(V11BodyState.LOADING)
        surfaceView.background = null
        surfaceView.holder.setFormat(PixelFormat.OPAQUE)
        surfaceView.setZOrderOnTop(false)

        if (surfaceView.holder.surface.isValid) {
            initializeOnSurface()
        } else {
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = surfaceView.post { initializeOnSurface() }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
    }

    private fun initializeOnSurface() {
        if (initialized || !surfaceView.holder.surface.isValid) return
        initialized = true
        try {
            val engine = Engine.create(Engine.Backend.OPENGL)
            val current = ModelViewer(surfaceView, engine = engine, manipulator = null)
            viewer = current

            // Neutral charcoal stage: high contrast without blue contamination on the model.
            current.scene.skybox = Skybox.Builder()
                .color(.020f, .020f, .022f, 1f)
                .build(current.engine)

            current.view.renderQuality = current.view.renderQuality.apply {
                hdrColorBuffer = if (lowPowerDevice) View.QualityLevel.MEDIUM else View.QualityLevel.HIGH
            }
            current.view.dynamicResolutionOptions = current.view.dynamicResolutionOptions.apply {
                enabled = false
            }
            current.view.bloomOptions = current.view.bloomOptions.apply { enabled = false }
            current.view.antiAliasing = View.AntiAliasing.FXAA
            current.view.multiSampleAntiAliasingOptions = current.view.multiSampleAntiAliasingOptions.apply {
                enabled = false
            }
            current.view.ambientOcclusionOptions = current.view.ambientOcclusionOptions.apply {
                enabled = !lowPowerDevice
            }

            installLights(current)
            current.camera.setExposure(8.2f, 1f / 120f, 100f)
            surfaceView.setOnTouchListener { _, event -> handleTouch(event) }
            loadModel(current)
            if (running) postFrame()
        } catch (_: Throwable) {
            fail()
        }
    }

    private fun installLights(current: ModelViewer) {
        fun light(r: Float, g: Float, b: Float, intensity: Float, x: Float, y: Float, z: Float) {
            val entity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                .intensity(intensity)
                .direction(x, y, z)
                .castShadows(false)
                .build(current.engine, entity)
            current.scene.addEntity(entity)
        }

        // Broad neutral key + warm fill + subtle rim. No colored wash on the anatomy.
        light(1.00f, .99f, .97f, 56_000f, -.42f, -.72f, -.56f)
        light(.96f, .91f, .86f, 20_000f, .72f, -.16f, -.66f)
        if (!lowPowerDevice) light(.88f, .90f, .96f, 8_000f, -.14f, .26f, .94f)
    }

    private fun loadModel(current: ModelViewer) {
        try {
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            require(bytes.size > 1_000_000) { "Body GLB is unexpectedly small" }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                flip()
            }
            current.loadModelGlb(buffer)
            current.transformToUnitCube(Float3(0f, 0f, 0f))
            captureRoot(current)
            applyShape()
            applyProfileMorphs()
            updateCamera(current)
        } catch (_: Throwable) {
            fail()
        }
    }

    private fun prepareBody(current: ModelViewer): Boolean {
        val asset = current.asset ?: return false
        val body = asset.getFirstEntityByName("Body")
        if (body == 0) return false
        val rm = current.engine.renderableManager
        val instance = rm.getInstance(body)
        if (instance == 0 || rm.getPrimitiveCount(instance) <= 0) return false

        rm.setLayerMask(instance, 0xFF, 0xFF)
        hide(current, "GrowthTrackHair")
        hide(current, "GrowthTrackEyes")
        hide(current, "PrivateAnatomy")
        applyNeutralMaterial(current, body)
        applyShape()
        applyProfileMorphs()
        return true
    }

    private fun applyNeutralMaterial(current: ModelViewer, body: Int) {
        val rm = current.engine.renderableManager
        val instance = rm.getInstance(body)
        if (instance == 0) return
        repeat(rm.getPrimitiveCount(instance)) { primitive ->
            val material = rm.getMaterialInstanceAt(instance, primitive)
            runCatching {
                // Warm matte pearl: enough human warmth to read form, neutral enough for measurement.
                material.setParameter(
                    "baseColorFactor",
                    Colors.RgbaType.SRGB,
                    .73f,
                    .68f,
                    .62f,
                    1f,
                )
                material.setParameter("metallicFactor", 0f)
                material.setParameter("roughnessFactor", .58f)
                material.setParameter("reflectance", .24f)
                material.setParameter(
                    "emissiveFactor",
                    Colors.RgbType.LINEAR,
                    .002f,
                    .002f,
                    .002f,
                )
                material.setParameter("emissiveStrength", .02f)
            }
        }
    }

    private fun hide(current: ModelViewer, name: String) {
        val entity = current.asset?.getFirstEntityByName(name) ?: return
        if (entity == 0) return
        val rm = current.engine.renderableManager
        val instance = rm.getInstance(entity)
        if (instance != 0) runCatching { rm.setLayerMask(instance, 0xFF, 0x00) }
    }

    fun updateProfile(value: BodyProfile) {
        profile = value
        applyProfileMorphs()
    }

    fun updateBodyShape(width: Float, height: Float, depth: Float) {
        shapeWidth = width.coerceIn(.72f, 1.42f)
        shapeHeight = height.coerceIn(.78f, 1.26f)
        shapeDepth = depth.coerceIn(.72f, 1.46f)
        applyShape()
        targetDistance = overviewDistance()
    }

    fun focusOn(normalizedY: Float, distance: Float) {
        focused = true
        targetY = normalizedY.coerceIn(-.75f, .75f).toDouble()
        targetDistance = distance.coerceIn(1.72f, 3.05f).toDouble()
    }

    fun resetFocus() {
        focused = false
        targetY = -.03
        targetDistance = overviewDistance()
    }

    fun resetView() {
        focused = false
        yaw = 0.0
        targetY = -.03
        targetDistance = overviewDistance()
    }

    fun onViewportTouch(event: MotionEvent): Boolean = handleTouch(event)

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

    private fun fail() {
        if (ready) return
        stop()
        onStateChanged(V11BodyState.ERROR)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                downX = event.x
                downY = event.y
                pinchDistance = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) pinchDistance = pointerDistance(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val now = pointerDistance(event)
                    if (pinchDistance > 0f && now > 0f) {
                        focused = false
                        targetDistance = (targetDistance * (pinchDistance / now)).coerceIn(1.58, 4.30)
                    }
                    pinchDistance = now
                } else {
                    val dx = event.x - lastX
                    if (abs(dx) > .12f) {
                        focused = false
                        yaw += dx * .0095
                    }
                    lastX = event.x
                }
            }
            MotionEvent.ACTION_UP -> {
                val travel = hypot(event.x - downX, event.y - downY)
                if (travel <= context.resources.displayMetrics.density * 17f) {
                    val now = event.eventTime
                    if (now - lastTapMs in 50L..320L) resetView()
                    lastTapMs = now
                }
                pinchDistance = 0f
            }
            MotionEvent.ACTION_CANCEL -> pinchDistance = 0f
        }
        return true
    }

    private fun pointerDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        return hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
    }

    private fun updateCamera(current: ModelViewer) {
        current.camera.lookAt(
            sin(yaw) * cameraDistance,
            cameraY * .16,
            cos(yaw) * cameraDistance,
            0.0,
            cameraY,
            0.0,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun overviewDistance(): Double {
        val widthPenalty = 1f + (shapeWidth - 1f).coerceAtLeast(0f) * .16f
        val heightPenalty = 1f + (shapeHeight - 1f).coerceAtLeast(0f) * .70f
        return (OVERVIEW_DISTANCE * widthPenalty * heightPenalty).coerceIn(2.90, 3.92)
    }

    private fun captureRoot(current: ModelViewer) {
        val asset = current.asset ?: return
        val manager = current.engine.transformManager
        val instance = manager.getInstance(asset.root)
        if (instance != 0) {
            baseRootTransform = FloatArray(16).also { manager.getTransform(instance, it) }
        }
    }

    private fun applyShape() {
        val current = viewer ?: return
        val asset = current.asset ?: return
        val base = baseRootTransform ?: return
        val manager = current.engine.transformManager
        val instance = manager.getInstance(asset.root)
        if (instance == 0) return
        val out = base.copyOf()
        for (row in 0..3) {
            out[row] *= shapeWidth
            out[4 + row] *= shapeHeight
            out[8 + row] *= shapeDepth
        }
        runCatching { manager.setTransform(instance, out) }
    }

    private fun applyProfileMorphs() {
        val current = viewer ?: return
        val asset = current.asset ?: return
        val body = asset.getFirstEntityByName("Body")
        if (body == 0) return
        val names = asset.getMorphTargetNames(body)
        if (names.isEmpty()) return
        val weights = FloatArray(names.size)

        fun set(name: String, value: Float) {
            val index = names.indexOf(name)
            if (index >= 0) weights[index] = value.coerceIn(0f, 1f)
        }
        fun cm(point: BodyMeasurePoint): Float? = profile.measurementsInches[point]?.times(INCH_TO_CM)

        val kg = profile.weightPounds * POUND_TO_KG
        val mass = ((kg - 45f) / 90f).coerceIn(0f, 1f)
        val bulk = ((kg - 58f) / 82f).coerceIn(0f, 1f)
        val waist = cm(BodyMeasurePoint.WAIST)
        val abdomen = cm(BodyMeasurePoint.ABDOMEN)
        val chest = cm(BodyMeasurePoint.CHEST)
        val hips = cm(BodyMeasurePoint.HIPS)

        set("overall_mass", mass)
        set("face_roundness", mass * .42f)
        set("gut_volume", maxOf(
            bulk * .64f,
            waist?.let { ((it - 69f) / 66f).coerceIn(0f, 1f) } ?: 0f,
            abdomen?.let { ((it - 73f) / 68f).coerceIn(0f, 1f) } ?: 0f,
        ))
        set("shoulder_drop", .16f)
        set("hand_splay", .04f)

        chest?.let {
            set("chest_depth", ((it - 74f) / 62f).coerceIn(0f, 1f))
            set("ribcage_depth", ((it - 72f) / 68f).coerceIn(0f, .90f))
        }
        cm(BodyMeasurePoint.UNDERBUST)?.let {
            set("ribcage_depth", ((it - 63f) / 57f).coerceIn(0f, .92f))
        }
        cm(BodyMeasurePoint.NECK)?.let {
            set("neck_thickness", ((it - 29f) / 25f).coerceIn(0f, .90f))
        }
        cm(BodyMeasurePoint.SHOULDERS)?.let {
            set("clavicle_width", ((it - 33f) / 29f).coerceIn(0f, 1f))
            set("deltoid_width", ((it - 35f) / 33f).coerceIn(0f, .78f))
        }
        waist?.let {
            set("waist_narrow", ((87f - it) / 38f).coerceIn(0f, 1f))
            set("oblique_def", ((91f - it) / 45f).coerceIn(0f, .52f))
        }
        hips?.let {
            set("hip_width", ((it - 77f) / 64f).coerceIn(0f, 1f))
            set("pelvis_width", ((it - 77f) / 64f).coerceIn(0f, .92f))
            set("glute_volume", ((it - 81f) / 64f).coerceIn(0f, .88f))
        }
        cm(BodyMeasurePoint.UPPER_ARM)?.let {
            val girth = ((it - 20f) / 34f).coerceIn(0f, .92f)
            set("bicep_peak", girth)
            set("tricep_horse", girth * .82f)
        }
        cm(BodyMeasurePoint.WRIST)?.let {
            set("forearm_girth", ((it - 12f) / 19f).coerceIn(0f, .66f))
        }
        cm(BodyMeasurePoint.ARM_LENGTH)?.let {
            val arm = ((it - 46f) / 44f).coerceIn(0f, .84f)
            set("upper_arm_length", arm * .52f)
            set("forearm_length", arm * .48f)
        }

        val rm = current.engine.renderableManager
        val instance = rm.getInstance(body)
        if (instance != 0) runCatching { rm.setMorphWeights(instance, weights, 0) }
    }

    private fun dispatchProjection(current: ModelViewer) {
        val asset = current.asset ?: return
        val tm = current.engine.transformManager
        val view = current.camera.getViewMatrix(FloatArray(16))
        val projection = current.camera.getProjectionMatrix(DoubleArray(16))
        val rig = linkedMapOf<String, V11ScreenPoint>()

        RIG_BONES.forEach { name ->
            val entity = asset.getFirstEntityByName(name)
            if (entity == 0) return@forEach
            val instance = tm.getInstance(entity)
            if (instance == 0) return@forEach
            val world = tm.getWorldTransform(instance, FloatArray(16))
            project(world[12], world[13], world[14], view, projection)?.let { rig[name] = it }
        }
        if (rig.isEmpty()) return

        // Anatomical calibration is based on the visible neck-to-pelvis axis rather than arbitrary
        // spine-bone labels. These ratios track clothing-measurement landmarks much more reliably.
        val neck = rig["Neck"] ?: return
        val hips = rig["Hips"] ?: return
        val leftShoulder = mix(rig["LeftShoulder"], rig["LeftUpperArm"], .16f) ?: return
        val rightShoulder = mix(rig["RightShoulder"], rig["RightUpperArm"], .16f) ?: return
        val head = rig["Head"] ?: neck
        val centerX = (leftShoulder.x + rightShoulder.x) * .5f
        val shoulderSpan = abs(rightShoulder.x - leftShoulder.x).coerceAtLeast(.13f)

        fun torso(t: Float): V11ScreenPoint = mix(neck, hips, t) ?: hips
        val chest = torso(.30f).copy(x = centerX)
        val underBust = torso(.42f).copy(x = centerX)
        val waist = torso(.61f).copy(x = centerX)
        val abdomen = torso(.74f).copy(x = centerX)
        val hip = torso(.94f).copy(x = centerX)
        val crown = extrapolate(neck, head, 1.46f)?.copy(x = centerX) ?: head

        val landmarks = linkedMapOf<String, V11ScreenPoint>().apply {
            putAll(rig)
            put("AnatomyCrown", crown)
            put("AnatomyNeck", neck.copy(x = centerX))
            put("AnatomyChest", chest)
            put("AnatomyUnderbust", underBust)
            put("AnatomyWaist", waist)
            put("AnatomyAbdomen", abdomen)
            put("AnatomyHip", hip)
            put("AnatomyBustLeft", chest.copy(x = centerX - shoulderSpan * .17f))
            put("AnatomyBustRight", chest.copy(x = centerX + shoulderSpan * .17f))
            put("AnatomyCenterX", chest.copy(x = centerX))
        }
        onProjectionChanged(V11BodyProjection(landmarks, yaw))
    }

    private fun mix(a: V11ScreenPoint?, b: V11ScreenPoint?, t: Float): V11ScreenPoint? {
        if (a == null) return b
        if (b == null) return a
        return V11ScreenPoint(
            x = a.x + (b.x - a.x) * t,
            y = a.y + (b.y - a.y) * t,
            visible = a.visible || b.visible,
        )
    }

    private fun extrapolate(a: V11ScreenPoint?, b: V11ScreenPoint?, t: Float): V11ScreenPoint? {
        if (a == null || b == null) return b ?: a
        return V11ScreenPoint(
            x = a.x + (b.x - a.x) * t,
            y = a.y + (b.y - a.y) * t,
            visible = a.visible || b.visible,
        )
    }

    private fun project(
        x: Float,
        y: Float,
        z: Float,
        view: FloatArray,
        projection: DoubleArray,
    ): V11ScreenPoint? {
        val vx = view[0] * x + view[4] * y + view[8] * z + view[12]
        val vy = view[1] * x + view[5] * y + view[9] * z + view[13]
        val vz = view[2] * x + view[6] * y + view[10] * z + view[14]
        val vw = view[3] * x + view[7] * y + view[11] * z + view[15]

        val cx = projection[0] * vx + projection[4] * vy + projection[8] * vz + projection[12] * vw
        val cy = projection[1] * vx + projection[5] * vy + projection[9] * vz + projection[13] * vw
        val cw = projection[3] * vx + projection[7] * vy + projection[11] * vz + projection[15] * vw
        if (!cw.isFinite() || abs(cw) < 1e-7) return null
        val nx = cx / cw
        val ny = cy / cw
        if (!nx.isFinite() || !ny.isFinite()) return null
        val sx = ((nx + 1.0) * .5).toFloat()
        val sy = ((1.0 - ny) * .5).toFloat()
        return V11ScreenPoint(
            x = sx,
            y = sy,
            visible = sx in -.12f..1.12f && sy in -.12f..1.12f,
        )
    }
}
