package com.almi.ai.ui.avatar

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
import kotlin.math.sin

/**
 * Lightweight, local-only Filament renderer used by the v9 avatar workshop.
 *
 * This intentionally does not share the Tailor Pro runtime: body measurement needs a stable
 * high-density digital twin while avatar editing benefits from the much smaller HM08 lite mesh.
 * Both assets are bundled at build time, so opening the workshop never downloads a model.
 */
internal class AvatarFilamentRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    initialPresentation: AvatarPresentation,
    initialAppearance: AvatarAppearance,
    private val onReady: () -> Unit = {},
) {
    companion object {
        init {
            Filament.init()
            Utils.init()
        }

        private const val AVATAR_MODEL = "almi3d/almi_avatar_lite.glb"
        private const val READY_FRAMES = 4
    }

    private var viewer: ModelViewer? = null
    private var initialized = false
    private var running = false
    private var framePosted = false
    private var ready = false
    private var warmupFrames = 0
    private var baseRootTransform: FloatArray? = null

    private var presentation = initialPresentation
    private var appearance = initialAppearance
    private var targetYaw = 0.0
    private var yaw = 0.0
    private var walkStartedNanos = 0L
    private var walkDurationNanos = 0L
    private var walkDirection = 1f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!running) return
            val current = viewer ?: return

            updateWalk(frameTimeNanos)
            yaw += (targetYaw - yaw) * 0.11
            updateCamera(current)
            current.render(frameTimeNanos)

            if (!ready && current.asset != null && current.progress >= .96f) {
                warmupFrames += 1
                if (warmupFrames >= READY_FRAMES) {
                    ready = true
                    applyPresentation()
                    applyAppearance()
                    surfaceView.post(onReady)
                }
            }
            postFrame()
        }
    }

    fun initialize() {
        if (initialized) return
        surfaceView.holder.setFormat(PixelFormat.OPAQUE)
        surfaceView.setZOrderOnTop(false)
        surfaceView.background = null

        if (surfaceView.holder.surface.isValid) {
            initializeOnSurface()
        } else {
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = initializeOnSurface()
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            })
        }
    }

    private fun initializeOnSurface() {
        if (initialized || !surfaceView.holder.surface.isValid) return
        initialized = true
        runCatching {
            val engine = Engine.create(Engine.Backend.OPENGL)
            val current = ModelViewer(surfaceView, engine = engine, manipulator = null)
            viewer = current

            current.scene.skybox = Skybox.Builder()
                .color(.018f, .024f, .038f, 1f)
                .build(current.engine)
            current.view.renderQuality = current.view.renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }
            current.view.dynamicResolutionOptions = current.view.dynamicResolutionOptions.apply {
                enabled = true
                minScale = .70f
                maxScale = 1.0f
                quality = View.QualityLevel.MEDIUM
            }
            current.view.bloomOptions = current.view.bloomOptions.apply { enabled = false }
            current.view.antiAliasing = View.AntiAliasing.FXAA
            current.view.ambientOcclusionOptions = current.view.ambientOcclusionOptions.apply { enabled = true }
            current.camera.setExposure(7.8f, 1f / 100f, 100f)
            installLights(current)

            val bytes = context.assets.open(AVATAR_MODEL).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                flip()
            }
            current.loadModelGlb(buffer)
            current.transformToUnitCube(Float3(0f, -.03f, 0f))
            current.asset?.root?.let { root ->
                val manager = current.engine.transformManager
                val instance = manager.getInstance(root)
                if (instance != 0) {
                    baseRootTransform = FloatArray(16).also { manager.getTransform(instance, it) }
                }
            }
            updateCamera(current)
            if (running) postFrame()
        }
    }

    private fun installLights(current: ModelViewer) {
        fun directional(intensity: Float, r: Float, g: Float, b: Float, x: Float, y: Float, z: Float) {
            val entity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                .intensity(intensity)
                .direction(x, y, z)
                .castShadows(false)
                .build(current.engine, entity)
            current.scene.addEntity(entity)
        }
        directional(52_000f, 1f, .98f, .96f, -.45f, -.72f, -.52f)
        directional(22_000f, .67f, .79f, 1f, .65f, -.12f, -.74f)
        directional(9_000f, .80f, .72f, 1f, -.12f, .30f, .94f)
    }

    fun start() {
        if (running) return
        running = true
        if (viewer != null) postFrame()
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        framePosted = false
    }

    fun update(presentation: AvatarPresentation, appearance: AvatarAppearance) {
        val presentationChanged = this.presentation != presentation
        this.presentation = presentation
        this.appearance = appearance
        if (ready) {
            if (presentationChanged) applyPresentation()
            applyAppearance()
        }
    }

    fun faceFront() {
        targetYaw = 0.0
    }

    fun rotatePreview() {
        targetYaw = if (abs(targetYaw) < .1) PI * .36 else 0.0
    }

    /** A short runway-style transition: root glides in while the rig gets a restrained walk sway. */
    fun playWalkIn(fromRight: Boolean, durationMs: Long = 900L) {
        walkDirection = if (fromRight) 1f else -1f
        walkStartedNanos = 0L
        walkDurationNanos = durationMs.coerceIn(500L, 1_500L) * 1_000_000L
    }

    private fun updateWalk(frameTimeNanos: Long) {
        if (walkDurationNanos <= 0L) return
        val current = viewer ?: return
        val asset = current.asset ?: return
        val base = baseRootTransform ?: return
        if (walkStartedNanos == 0L) walkStartedNanos = frameTimeNanos
        val t = ((frameTimeNanos - walkStartedNanos).toDouble() / walkDurationNanos).coerceIn(0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t)
        val stride = sin(t * PI * 5.0).toFloat()
        val manager = current.engine.transformManager
        val rootInstance = manager.getInstance(asset.root)
        if (rootInstance != 0) {
            val out = base.copyOf()
            out[12] += walkDirection * (1.0 - eased).toFloat() * .33f
            out[13] += abs(stride) * .008f
            manager.setTransform(rootInstance, out)
        }
        if (t >= 1.0) {
            walkDurationNanos = 0L
            walkStartedNanos = 0L
            if (rootInstance != 0) manager.setTransform(rootInstance, base)
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
                set("waist_narrow", .42f)
                set("hip_width", .44f)
                set("pelvis_width", .38f)
                set("glute_volume", .24f)
                set("chest_depth", .18f)
                set("face_roundness", .24f)
                set("clavicle_width", .04f)
                set("deltoid_width", .02f)
            }
            AvatarPresentation.MASCULINE -> {
                set("waist_narrow", .12f)
                set("hip_width", .05f)
                set("pelvis_width", .04f)
                set("chest_depth", .22f)
                set("clavicle_width", .28f)
                set("deltoid_width", .18f)
                set("jaw_width", .17f)
            }
        }
        val rm = current.engine.renderableManager
        val instance = rm.getInstance(body)
        if (instance != 0) runCatching { rm.setMorphWeights(instance, weights, 0) }
    }

    private fun applyAppearance() {
        val current = viewer ?: return
        val asset = current.asset ?: return
        setEntityColor(current, asset.getFirstEntityByName("Body"), appearance.skinColor)
        setEntityColor(current, asset.getFirstEntityByName("GrowthTrackHair"), appearance.hairColor)
        setVisible(current, asset.getFirstEntityByName("PrivateAnatomy"), false)
        setVisible(current, asset.getFirstEntityByName("GrowthTrackHair"), appearance.hairVariant != "bald")
        setVisible(current, asset.getFirstEntityByName("ALMI_GlassesRound"), appearance.accessoriesVariant == "round")
        setVisible(current, asset.getFirstEntityByName("ALMI_GlassesSquare"), appearance.accessoriesVariant == "wayfarers")
        setVisible(current, asset.getFirstEntityByName("ALMI_Cap"), appearance.accessoriesVariant == "cap")

        val hair = asset.getFirstEntityByName("GrowthTrackHair")
        if (hair != 0) {
            val manager = current.engine.transformManager
            val instance = manager.getInstance(hair)
            if (instance != 0) {
                val transform = FloatArray(16).also { manager.getTransform(instance, it) }
                val scale = when (appearance.hairVariant) {
                    "shortFlat" -> .86f
                    "shortCurly" -> .94f
                    "bob" -> 1.02f
                    "longButNotTooLong" -> 1.10f
                    else -> 1f
                }
                transform[0] *= scale
                transform[5] *= scale
                transform[10] *= scale
                runCatching { manager.setTransform(instance, transform) }
            }
        }
    }

    private fun setEntityColor(current: ModelViewer, entity: Int, value: String) {
        if (entity == 0) return
        val color = runCatching { android.graphics.Color.parseColor("#$value") }.getOrNull() ?: return
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
                    r,
                    g,
                    b,
                    1f,
                )
            }
        }
    }

    private fun setVisible(current: ModelViewer, entity: Int, visible: Boolean) {
        if (entity == 0) return
        val rm = current.engine.renderableManager
        val instance = rm.getInstance(entity)
        if (instance != 0) runCatching {
            rm.setLayerMask(instance, 0xFF, if (visible) 0xFF else 0x00)
        }
    }

    private fun updateCamera(current: ModelViewer) {
        val distance = 2.72
        current.camera.lookAt(
            sin(yaw) * distance,
            .04,
            kotlin.math.cos(yaw) * distance,
            0.0,
            .03,
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
