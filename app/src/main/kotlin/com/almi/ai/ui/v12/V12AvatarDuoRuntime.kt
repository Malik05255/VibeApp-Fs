package com.almi.ai.ui.v12

import android.app.ActivityManager
import android.content.Context
import android.content.res.AssetManager
import android.graphics.PixelFormat
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.almi.ai.data.preferences.AvatarPresentation
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Fence
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.math.abs

/**
 * One cinematic scene for the v12 identity choice.
 *
 * Both authored PBR humans live in one Filament Scene and run their own skeleton animation.
 * Selection is a scene transition: the chosen body moves to centre while the other exits.
 */
internal class V12AvatarDuoRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val onReady: () -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
) {
    companion object {
        init {
            Filament.init()
            Utils.init()
        }

        private const val FEMALE_MODEL = "almi3d/almi_body_female_v12.glb"
        private const val MALE_MODEL = "almi3d/almi_body_male_v12.glb"
        private const val READY_FRAMES = 3
        private const val START_X = .53f
        private const val EXIT_X = 1.62f
        private const val MIN_MODEL_BYTES = 1_000_000L
    }

    private data class Character(
        val presentation: AvatarPresentation,
        val asset: FilamentAsset,
        val normalizedRoot: FloatArray,
        val animationIndex: Int,
        var x: Float,
        var targetX: Float,
        var animationStartNanos: Long = 0L,
    )

    private val lowPowerDevice: Boolean by lazy {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.isLowRamDevice == true || Runtime.getRuntime().availableProcessors() <= 4
    }

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var filamentView: View? = null
    private var camera: Camera? = null
    private var cameraEntity = 0
    private var swapChain: SwapChain? = null
    private var displayHelper: DisplayHelper? = null
    private var uiHelper: UiHelper? = null
    private var materialProvider: MaterialProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var skybox: Skybox? = null
    private val lightEntities = mutableListOf<Int>()

    private var male: Character? = null
    private var female: Character? = null
    private var selected: AvatarPresentation? = null
    private var initialized = false
    private var running = false
    private var destroyed = false
    private var framePosted = false
    private var ready = false
    private var warmupFrames = 0

    private val surfaceCallback = object : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            val currentEngine = engine ?: return
            swapChain?.let(currentEngine::destroySwapChain)
            swapChain = currentEngine.createSwapChain(surface)
            renderer?.let { displayHelper?.attach(it, surfaceView.display) }
        }

        override fun onDetachedFromSurface() {
            val currentEngine = engine ?: return
            displayHelper?.detach()
            swapChain?.let {
                currentEngine.destroySwapChain(it)
                currentEngine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            filamentView?.viewport = Viewport(0, 0, width, height)
            camera?.setLensProjection(39.0, width.toDouble() / height.toDouble(), .03, 50.0)
            engine?.let(::synchronizePendingFrames)
        }
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            framePosted = false
            if (!running || destroyed) return

            val currentRenderer = renderer ?: return
            val currentView = filamentView ?: return
            val currentSwapChain = swapChain
            if (currentSwapChain == null || uiHelper?.isReadyToRender != true) {
                postFrame()
                return
            }

            updateCharacter(male, frameTimeNanos)
            updateCharacter(female, frameTimeNanos)
            updateCamera()

            if (currentRenderer.beginFrame(currentSwapChain, frameTimeNanos)) {
                currentRenderer.render(currentView)
                currentRenderer.endFrame()
            }

            if (!ready) {
                warmupFrames += 1
                if (warmupFrames >= READY_FRAMES && male != null && female != null) {
                    ready = true
                    surfaceView.post(onReady)
                }
            }
            postFrame()
        }
    }

    fun initialize() {
        if (initialized || destroyed) return
        initialized = true
        surfaceView.setZOrderOnTop(false)
        surfaceView.holder.setFormat(PixelFormat.OPAQUE)
        surfaceView.background = null

        runCatching {
            val currentEngine = Engine.create(Engine.Backend.OPENGL)
            engine = currentEngine
            renderer = currentEngine.createRenderer()
            scene = currentEngine.createScene()
            cameraEntity = EntityManager.get().create()
            camera = currentEngine.createCamera(cameraEntity)
            filamentView = currentEngine.createView().also { view ->
                view.scene = scene
                view.camera = camera
                view.renderQuality = view.renderQuality.apply {
                    hdrColorBuffer = if (lowPowerDevice) View.QualityLevel.MEDIUM else View.QualityLevel.HIGH
                }
                view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
                    enabled = lowPowerDevice
                    quality = View.QualityLevel.HIGH
                }
                view.antiAliasing = View.AntiAliasing.FXAA
                view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
                    enabled = !lowPowerDevice
                }
                view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
                    enabled = !lowPowerDevice
                    quality = View.QualityLevel.HIGH
                }
                view.bloomOptions = view.bloomOptions.apply {
                    enabled = !lowPowerDevice
                    strength = .05f
                }
            }

            materialProvider = UbershaderProvider(currentEngine)
            assetLoader = AssetLoader(currentEngine, materialProvider!!, EntityManager.get())
            resourceLoader = ResourceLoader(currentEngine, true)
            displayHelper = DisplayHelper(context)
            uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).also {
                it.renderCallback = surfaceCallback
                it.attachTo(surfaceView)
            }

            skybox = Skybox.Builder()
                .color(.91f, .97f, 1f, 1f)
                .build(currentEngine)
                .also { scene?.skybox = it }
            camera?.setExposure(9.1f, 1f / 125f, 100f)
            installLights(currentEngine)

            male = loadCharacter(
                path = MALE_MODEL,
                presentation = AvatarPresentation.MASCULINE,
                startX = -START_X,
            )
            female = loadCharacter(
                path = FEMALE_MODEL,
                presentation = AvatarPresentation.FEMININE,
                startX = START_X,
            )
            applySelectionTargets()
            updateCamera()

            if (running) postFrame()
        }.onFailure {
            surfaceView.post { onFailure(it) }
            destroy()
        }
    }

    private fun readAssetDirect(path: String): ByteBuffer {
        val length = context.assets.openFd(path).use { it.length }
        check(length in MIN_MODEL_BYTES..Int.MAX_VALUE.toLong()) {
            "$path has invalid packaged length $length"
        }
        val buffer = ByteBuffer.allocateDirect(length.toInt())
        context.assets.open(path, AssetManager.ACCESS_STREAMING).use { input ->
            Channels.newChannel(input).use { channel ->
                while (buffer.hasRemaining()) {
                    val count = channel.read(buffer)
                    if (count < 0) break
                }
            }
        }
        check(!buffer.hasRemaining()) {
            "$path ended early: ${buffer.position()} / $length bytes"
        }
        buffer.flip()
        return buffer
    }

    private fun loadCharacter(
        path: String,
        presentation: AvatarPresentation,
        startX: Float,
    ): Character {
        val currentEngine = engine ?: error("Engine not initialized")
        val loader = assetLoader ?: error("Asset loader not initialized")
        val resources = resourceLoader ?: error("Resource loader not initialized")
        val buffer = readAssetDirect(path)
        val asset = loader.createAsset(buffer) ?: error("Could not parse $path")
        resources.loadResources(asset)
        asset.releaseSourceData()
        scene?.addEntities(asset.renderableEntities)
        if (asset.lightEntities.isNotEmpty()) scene?.addEntities(asset.lightEntities)

        val normalized = normalizeAsset(asset)
        val animator = asset.instance.animator
        val animationIndex = findAnimation(asset)
        val character = Character(
            presentation = presentation,
            asset = asset,
            normalizedRoot = normalized,
            animationIndex = animationIndex,
            x = startX,
            targetX = startX,
        )
        applyStageTransform(character)
        animator.updateBoneMatrices()
        currentEngine.flush()
        return character
    }

    private fun normalizeAsset(asset: FilamentAsset): FloatArray {
        val center = asset.boundingBox.center
        val half = asset.boundingBox.halfExtent
        val maxExtent = 2f * maxOf(half[0], half[1], half[2]).coerceAtLeast(.001f)
        val scaleFactor = 1.82f / maxExtent

        val scale = FloatArray(16)
        val translate = FloatArray(16)
        val normalized = FloatArray(16)
        Matrix.setIdentityM(scale, 0)
        Matrix.scaleM(scale, 0, scaleFactor, scaleFactor, scaleFactor)
        Matrix.setIdentityM(translate, 0)
        Matrix.translateM(translate, 0, -center[0], -center[1], -center[2])
        Matrix.multiplyMM(normalized, 0, scale, 0, translate, 0)
        return normalized
    }

    private fun updateCharacter(character: Character?, frameTimeNanos: Long) {
        character ?: return
        character.x += (character.targetX - character.x) * .105f
        if (abs(character.targetX - character.x) < .001f) character.x = character.targetX
        applyStageTransform(character)

        val animator = character.asset.instance.animator
        val index = character.animationIndex
        if (index in 0 until animator.animationCount) {
            if (character.animationStartNanos == 0L) character.animationStartNanos = frameTimeNanos
            val elapsed = ((frameTimeNanos - character.animationStartNanos).toDouble() / 1_000_000_000.0).toFloat()
            val duration = animator.getAnimationDuration(index)
            val time = if (duration > .001f) elapsed % duration else elapsed
            animator.applyAnimation(index, time)
            animator.updateBoneMatrices()
        }
    }

    private fun applyStageTransform(character: Character) {
        val currentEngine = engine ?: return
        val transformManager = currentEngine.transformManager
        val rootInstance = transformManager.getInstance(character.asset.root)
        if (rootInstance == 0) return

        val stage = FloatArray(16)
        val out = FloatArray(16)
        Matrix.setIdentityM(stage, 0)
        Matrix.translateM(stage, 0, character.x, -.02f, 0f)
        Matrix.multiplyMM(out, 0, stage, 0, character.normalizedRoot, 0)
        transformManager.setTransform(rootInstance, out)
    }

    fun select(presentation: AvatarPresentation?) {
        selected = presentation
        applySelectionTargets()
    }

    fun resetSelection() = select(null)

    private fun applySelectionTargets() {
        when (selected) {
            AvatarPresentation.MASCULINE -> {
                male?.targetX = 0f
                female?.targetX = EXIT_X
            }
            AvatarPresentation.FEMININE -> {
                male?.targetX = -EXIT_X
                female?.targetX = 0f
            }
            null -> {
                male?.targetX = -START_X
                female?.targetX = START_X
            }
        }
    }

    private fun findAnimation(asset: FilamentAsset): Int {
        val animator = asset.instance.animator
        if (animator.animationCount <= 0) return -1
        listOf("idle", "breath", "sway", "stand").forEach { hint ->
            for (index in 0 until animator.animationCount) {
                if (animator.getAnimationName(index).contains(hint, ignoreCase = true)) return index
            }
        }
        return 0
    }

    private fun updateCamera() {
        camera?.lookAt(
            0.0,
            .02,
            3.02,
            0.0,
            -.02,
            0.0,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun installLights(currentEngine: Engine) {
        fun directional(
            intensity: Float,
            r: Float,
            g: Float,
            b: Float,
            x: Float,
            y: Float,
            z: Float,
            shadows: Boolean,
        ) {
            val entity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                .intensity(intensity)
                .direction(x, y, z)
                .castShadows(shadows)
                .build(currentEngine, entity)
            scene?.addEntity(entity)
            lightEntities += entity
        }

        directional(76_000f, 1f, .985f, .96f, -.42f, -.74f, -.55f, !lowPowerDevice)
        directional(29_000f, .64f, .88f, 1f, .68f, -.10f, -.72f, false)
        directional(22_000f, 1f, .76f, .86f, -.62f, -.12f, -.70f, false)
        directional(12_000f, .83f, 1f, .94f, -.08f, .30f, .95f, false)
    }

    fun start() {
        if (running || destroyed) return
        running = true
        postFrame()
    }

    fun stop() {
        running = false
        framePosted = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun postFrame() {
        if (!running || framePosted || destroyed) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        stop()
        runCatching { uiHelper?.detach() }
        runCatching { displayHelper?.detach() }

        val currentEngine = engine
        if (currentEngine == null) {
            clearReferences()
            return
        }

        swapChain?.let {
            runCatching { currentEngine.destroySwapChain(it) }
            swapChain = null
        }

        listOfNotNull(male?.asset, female?.asset).forEach { asset ->
            runCatching { scene?.removeEntities(asset.renderableEntities) }
            runCatching { assetLoader?.destroyAsset(asset) }
        }
        male = null
        female = null

        lightEntities.forEach { entity ->
            runCatching { currentEngine.destroyEntity(entity) }
        }
        lightEntities.clear()

        runCatching { resourceLoader?.destroy() }
        runCatching { materialProvider?.destroyMaterials() }
        runCatching { materialProvider?.destroy() }
        runCatching { assetLoader?.destroy() }
        renderer?.let { runCatching { currentEngine.destroyRenderer(it) } }
        filamentView?.let { runCatching { currentEngine.destroyView(it) } }
        scene?.let { runCatching { currentEngine.destroyScene(it) } }
        skybox?.let { runCatching { currentEngine.destroySkybox(it) } }
        skybox = null
        camera?.let { runCatching { currentEngine.destroyCameraComponent(it.entity) } }
        if (cameraEntity != 0) EntityManager.get().destroy(cameraEntity)
        runCatching { currentEngine.destroy() }

        clearReferences()
    }

    private fun clearReferences() {
        engine = null
        renderer = null
        filamentView = null
        scene = null
        camera = null
        displayHelper = null
        uiHelper = null
        materialProvider = null
        assetLoader = null
        resourceLoader = null
        cameraEntity = 0
        selected = null
    }

    private fun synchronizePendingFrames(currentEngine: Engine) {
        val fence = currentEngine.createFence()
        fence.wait(Fence.Mode.FLUSH, Fence.WAIT_FOR_EVER)
        currentEngine.destroyFence(fence)
    }
}