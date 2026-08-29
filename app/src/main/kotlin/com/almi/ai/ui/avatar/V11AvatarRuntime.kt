package com.almi.ai.ui.avatar

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
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
import kotlin.math.sin

internal class V11AvatarRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    initialPresentation: AvatarPresentation,
    initialAppearance: AvatarAppearance,
) {
    companion object {
        init {
            Filament.init()
            Utils.init()
        }
        private const val MODEL = "almi3d/almi_avatar_lite.glb"
    }

    private var viewer: ModelViewer? = null
    private var initialized = false
    private var running = false
    private var framePosted = false
    private var ready = false
    private var readyFrames = 0
    private var baseRoot: FloatArray? = null
    private var baseHair: FloatArray? = null

    private var presentation = initialPresentation
    private var appearance = initialAppearance
    private var yaw = 0.0
    private var targetYaw = 0.0

    private var motionStart = 0L
    private var motionDuration = 0L
    private var motionFromX = 0f
    private var motionToX = 0f
    private var motionSway = false

    private var turnStart = 0L
    private var turnDuration = 0L
    private var turnFrom = 0.0

    private val lowPower: Boolean by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true || Runtime.getRuntime().availableProcessors() <= 4
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!running) return
            val current = viewer ?: return
            updateMotion(frameTimeNanos)
            updateTurn(frameTimeNanos)
            if (turnDuration <= 0L) yaw += (targetYaw - yaw) * .12
            updateCamera(current)
            current.render(frameTimeNanos)

            if (!ready && current.asset != null && current.progress >= .97f) {
                readyFrames += 1
                if (readyFrames >= 4) {
                    ready = true
                    captureBaseTransforms(current)
                    applyPresentation()
                    applyAppearance()
                }
            }
            postFrame()
        }
    }

    fun initialize() {
        if (initialized) return
        surfaceView.background = null
        surfaceView.holder.setFormat(PixelFormat.OPAQUE)
        surfaceView.setZOrderOnTop(false)
        if (surfaceView.holder.surface.isValid) initializeOnSurface()
        else surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = surfaceView.post { initializeOnSurface() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
        })
    }

    private fun initializeOnSurface() {
        if (initialized || !surfaceView.holder.surface.isValid) return
        initialized = true
        runCatching {
            val engine = Engine.create(Engine.Backend.OPENGL)
            val current = ModelViewer(surfaceView, engine = engine, manipulator = null)
            viewer = current
            current.scene.skybox = Skybox.Builder().color(.018f, .017f, .016f, 1f).build(current.engine)
            current.view.renderQuality = current.view.renderQuality.apply {
                hdrColorBuffer = if (lowPower) View.QualityLevel.MEDIUM else View.QualityLevel.HIGH
            }
            current.view.dynamicResolutionOptions = current.view.dynamicResolutionOptions.apply { enabled = false }
            current.view.bloomOptions = current.view.bloomOptions.apply { enabled = false }
            current.view.antiAliasing = View.AntiAliasing.FXAA
            current.view.multiSampleAntiAliasingOptions = current.view.multiSampleAntiAliasingOptions.apply { enabled = false }
            current.view.ambientOcclusionOptions = current.view.ambientOcclusionOptions.apply { enabled = !lowPower }
            current.camera.setExposure(8.0f, 1f / 110f, 100f)
            installLights(current)

            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            require(bytes.size > 1_000_000)
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
            current.loadModelGlb(buffer)
            current.transformToUnitCube(Float3(0f, -.035f, 0f))
            updateCamera(current)
            if (running) postFrame()
        }
    }

    private fun installLights(current: ModelViewer) {
        fun add(r: Float, g: Float, b: Float, intensity: Float, x: Float, y: Float, z: Float) {
            val e = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                .intensity(intensity)
                .direction(x, y, z)
                .castShadows(false)
                .build(current.engine, e)
            current.scene.addEntity(e)
        }
        add(1f, .97f, .93f, 50_000f, -.48f, -.70f, -.52f)
        add(.92f, .84f, .78f, 17_000f, .66f, -.12f, -.72f)
        if (!lowPower) add(.88f, .90f, .96f, 7_000f, -.12f, .24f, .96f)
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

    fun update(presentation: AvatarPresentation, appearance: AvatarAppearance) {
        this.presentation = presentation
        this.appearance = appearance
        if (ready) {
            applyPresentation()
            applyAppearance()
        }
    }

    fun move(fromX: Float, toX: Float, durationMs: Long = 760L, sway: Boolean = true) {
        motionFromX = fromX
        motionToX = toX
        motionStart = 0L
        motionDuration = durationMs.coerceIn(350L, 1_300L) * 1_000_000L
        motionSway = sway
    }

    fun turntable(durationMs: Long = 2_200L) {
        turnFrom = yaw
        turnStart = 0L
        turnDuration = durationMs.coerceIn(1_300L, 3_500L) * 1_000_000L
    }

    private fun captureBaseTransforms(current: ModelViewer) {
        val asset = current.asset ?: return
        val tm = current.engine.transformManager
        val rootInstance = tm.getInstance(asset.root)
        if (rootInstance != 0) baseRoot = FloatArray(16).also { tm.getTransform(rootInstance, it) }
        val hair = asset.getFirstEntityByName("GrowthTrackHair")
        if (hair != 0) {
            val hairInstance = tm.getInstance(hair)
            if (hairInstance != 0) baseHair = FloatArray(16).also { tm.getTransform(hairInstance, it) }
        }
    }

    private fun updateMotion(frameTime: Long) {
        if (motionDuration <= 0L) return
        val current = viewer ?: return
        val asset = current.asset ?: return
        val base = baseRoot ?: return
        if (motionStart == 0L) motionStart = frameTime
        val t = ((frameTime - motionStart).toDouble() / motionDuration).coerceIn(0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t)
        val tm = current.engine.transformManager
        val instance = tm.getInstance(asset.root)
        if (instance != 0) {
            val out = base.copyOf()
            out[12] += motionFromX + (motionToX - motionFromX) * eased.toFloat()
            if (motionSway) out[13] += abs(sin(t * PI * 5.0)).toFloat() * .007f
            tm.setTransform(instance, out)
        }
        if (t >= 1.0) {
            motionDuration = 0L
            motionStart = 0L
            if (instance != 0) {
                val out = base.copyOf()
                out[12] += motionToX
                tm.setTransform(instance, out)
            }
        }
    }

    private fun updateTurn(frameTime: Long) {
        if (turnDuration <= 0L) return
        if (turnStart == 0L) turnStart = frameTime
        val t = ((frameTime - turnStart).toDouble() / turnDuration).coerceIn(0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t)
        yaw = turnFrom + eased * PI * 2.0
        if (t >= 1.0) {
            turnDuration = 0L
            turnStart = 0L
            yaw = 0.0
            targetYaw = 0.0
        }
    }

    private fun applyPresentation() {
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

        when (presentation) {
            AvatarPresentation.FEMININE -> {
                set("waist_narrow", .40f)
                set("hip_width", .38f)
                set("pelvis_width", .34f)
                set("glute_volume", .18f)
                set("chest_depth", .17f)
                set("face_roundness", .20f)
                set("clavicle_width", .04f)
            }
            AvatarPresentation.MASCULINE -> {
                set("waist_narrow", .10f)
                set("hip_width", .04f)
                set("pelvis_width", .03f)
                set("chest_depth", .22f)
                set("clavicle_width", .25f)
                set("deltoid_width", .15f)
                set("jaw_width", .15f)
            }
        }
        when (appearance.eyesVariant) {
            "wide" -> set("eye_size", .28f)
            "sharp" -> set("brow_depth", .20f)
        }
        if (appearance.eyebrowsVariant == "defined") set("brow_depth", .28f)
        when (appearance.mouthVariant) {
            "smile" -> set("smile", .38f)
            "full" -> set("lip_fullness", .38f)
        }

        val rm = current.engine.renderableManager
        fun apply(entity: Int) {
            if (entity == 0) return
            val instance = rm.getInstance(entity)
            if (instance != 0) runCatching { rm.setMorphWeights(instance, weights, 0) }
        }
        apply(body)
        apply(asset.getFirstEntityByName("ALMI_BaseLayer"))
    }

    private fun applyAppearance() {
        val current = viewer ?: return
        val asset = current.asset ?: return
        tint(current, asset.getFirstEntityByName("Body"), appearance.skinColor)
        tint(current, asset.getFirstEntityByName("GrowthTrackHair"), appearance.hairColor)
        visible(current, asset.getFirstEntityByName("PrivateAnatomy"), false)
        visible(current, asset.getFirstEntityByName("ALMI_BaseLayer"), true)
        visible(current, asset.getFirstEntityByName("GrowthTrackHair"), appearance.hairVariant != "bald")
        applyHairShape(current)
    }

    private fun applyHairShape(current: ModelViewer) {
        val asset = current.asset ?: return
        val hair = asset.getFirstEntityByName("GrowthTrackHair")
        val base = baseHair ?: return
        if (hair == 0) return
        val tm = current.engine.transformManager
        val instance = tm.getInstance(hair)
        if (instance == 0) return
        val out = base.copyOf()
        val (radial, vertical, yOffset) = when (appearance.hairVariant) {
            "shortFlat" -> Triple(.88f, .82f, -.010f)
            "shortCurly" -> Triple(1.00f, .95f, .006f)
            "bob" -> Triple(1.05f, 1.03f, -.012f)
            "longButNotTooLong" -> Triple(1.10f, 1.16f, -.030f)
            else -> Triple(1f, 1f, 0f)
        }
        for (row in 0..3) {
            out[row] *= radial
            out[4 + row] *= vertical
            out[8 + row] *= radial
        }
        out[13] += yOffset
        runCatching { tm.setTransform(instance, out) }
    }

    private fun tint(current: ModelViewer, entity: Int, hex: String) {
        if (entity == 0) return
        val color = runCatching { android.graphics.Color.parseColor("#$hex") }.getOrNull() ?: return
        val r = android.graphics.Color.red(color) / 255f
        val g = android.graphics.Color.green(color) / 255f
        val b = android.graphics.Color.blue(color) / 255f
        val rm = current.engine.renderableManager
        val instance = rm.getInstance(entity)
        if (instance == 0) return
        repeat(rm.getPrimitiveCount(instance)) { primitive ->
            runCatching {
                rm.getMaterialInstanceAt(instance, primitive).setParameter(
                    "baseColorFactor",
                    Colors.RgbaType.SRGB,
                    r, g, b, 1f,
                )
            }
        }
    }

    private fun visible(current: ModelViewer, entity: Int, visible: Boolean) {
        if (entity == 0) return
        val rm = current.engine.renderableManager
        val instance = rm.getInstance(entity)
        if (instance != 0) runCatching { rm.setLayerMask(instance, 0xFF, if (visible) 0xFF else 0x00) }
    }

    private fun updateCamera(current: ModelViewer) {
        val distance = 2.72
        current.camera.lookAt(
            sin(yaw) * distance,
            .035,
            cos(yaw) * distance,
            0.0,
            .02,
            0.0,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun postFrame() {
        if (!running || framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }
}
