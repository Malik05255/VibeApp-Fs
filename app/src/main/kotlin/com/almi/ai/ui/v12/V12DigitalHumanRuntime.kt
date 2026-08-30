package com.almi.ai.ui.v12

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.google.android.filament.Camera
import com.google.android.filament.Colors
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
import kotlin.math.PI
import kotlin.math.sin

/**
 * Quality-first multi-asset avatar renderer for ALMI v12.
 *
 * The body, FACS head and rigged hair stay as separate authored glTF assets in one Filament Scene.
 * This preserves the Vitruvian PBR materials instead of flattening the character into a single
 * mannequin material. The body carries real skeletal idle/walk/turn clips; the detached face and
 * hair follow the animated Mixamo head bone every frame.
 *
 * Presentation is intentionally independent from BodyProfile measurements. Masculine/feminine
 * presentation applies subtle rig-space silhouette shaping only; stored tailoring dimensions are
 * never modified.
 */
internal class V12DigitalHumanRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    initialPresentation: AvatarPresentation,
    initialAppearance: AvatarAppearance,
    private val onReady: () -> Unit = {},
    private val onFailure: (Throwable) -> Unit = {},
) {
    companion object {
        init {
            Filament.init()
            Utils.init()
        }

        const val BODY_ASSET = "almi3d/digital/vitruvian_body.glb"
        const val HEAD_ASSET = "almi3d/digital/vitruvian_head.glb"
        const val HAIR_ASSET = "almi3d/digital/vitruvian_hair.glb"

        private val HEAD_BONES = arrayOf("mixamorig:Head", "mixamorig_Head", "Head")
        private val HIPS_BONES = arrayOf("mixamorig:Hips", "mixamorig_Hips", "Hips")
        private val SPINE_BONES = arrayOf("mixamorig:Spine", "mixamorig_Spine", "Spine")
        private val LEFT_SHOULDER_BONES = arrayOf("mixamorig:LeftShoulder", "mixamorig_LeftShoulder", "LeftShoulder")
        private val RIGHT_SHOULDER_BONES = arrayOf("mixamorig:RightShoulder", "mixamorig_RightShoulder", "RightShoulder")

        private const val BODY_ENTITY = "cm_vitruvian"
        private const val SHIRT_ENTITY = "Shirt"
        private const val PANTS_ENTITY = "Pants"
        private const val HAIR_ENTITY = "VitHair"
        private const val READY_FRAMES = 3
        private const val OUTFIT_WHITE = "F6F7F8"
    }

    private data class Part(
        val asset: FilamentAsset,
        val rootBase: FloatArray,
    )

    private val lowPowerDevice: Boolean by lazy {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        manager?.isLowRamDevice == true || Runtime.getRuntime().availableProcessors() <= 4
    }

    private var presentation = initialPresentation
    private var appearance = initialAppearance.copy(presentation = initialPresentation)
    private var initialized = false
    private var running = false
    private var framePosted = false
    private var ready = false
    private var destroyed = false
    private var warmupFrames = 0

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var filamentView: View? = null
    private var camera: Camera? = null
    private var cameraEntity: Int = 0
    private var swapChain: SwapChain? = null
    private var displayHelper: DisplayHelper? = null
    private var uiHelper: UiHelper? = null

    private var materialProvider: MaterialProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null

    private var body: Part? = null
    private var head: Part? = null
    private var hair: Part? = null
    private var headBoneEntity: Int = 0
    private var hipsEntity: Int = 0
    private var spineEntity: Int = 0
    private var leftShoulderEntity: Int = 0
    private var rightShoulderEntity: Int = 0
    private var headBoneRestInverse: FloatArray? = null
    private var faceEntity: Int = 0

    private var activeAnimation = -1
    private var animationStartNanos = 0L
    private var yaw = 0.0
    private var targetYaw = 0.0
    private var turntableStartNanos = 0L
    private var turntableDurationNanos = 0L
    private var turntableFromYaw = 0.0

    private val surfaceCallback = object : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            val currentEngine = engine ?: return
            swapChain?.let(currentEngine::destroySwapChain)
            swapChain = currentEngine.createSwapChain(surface)
            val currentRenderer = renderer ?: return
            displayHelper?.attach(currentRenderer, surfaceView.display)
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
            camera?.setLensProjection(40.0, width.toDouble() / height.toDouble(), .03, 50.0)
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

            updateTurntable(frameTimeNanos)
            yaw += (targetYaw - yaw) * .12
            applyAnimation(frameTimeNanos)
            applyPresentationRig()
            body?.asset?.instance?.animator?.updateBoneMatrices()
            applyBodyRootYaw()
            attachHeadAndHairToBody()
            applyFaceDynamics(frameTimeNanos)
            updateCamera()

            if (currentRenderer.beginFrame(currentSwapChain, frameTimeNanos)) {
                currentRenderer.render(currentView)
                currentRenderer.endFrame()
            }

            if (!ready) {
                warmupFrames += 1
                if (warmupFrames >= READY_FRAMES && body != null && head != null) {
                    ready = true
                    resolveRuntimeEntities()
                    applyAppearanceMaterials()
                    applyFaceDynamics(frameTimeNanos)
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
                    strength = .055f
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

            scene?.skybox = Skybox.Builder()
                .color(.925f, .972f, 1f, 1f)
                .build(currentEngine)
            camera?.setExposure(9.4f, 1f / 125f, 100f)
            installLights(currentEngine)

            body = loadPart(BODY_ASSET)
            head = loadPart(HEAD_ASSET)
            hair = runCatching { loadPart(HAIR_ASSET) }.getOrNull()

            val bodyAsset = body?.asset ?: error("Digital human body failed to load")
            headBoneEntity = findFirstEntity(bodyAsset, HEAD_BONES)
            check(headBoneEntity != 0) {
                "Digital human head bone missing; expected one of ${HEAD_BONES.joinToString()}"
            }
            captureRestHeadTransform()
            activeAnimation = findAnimation(bodyAsset, listOf("Idle", "HappyIdle", "Sway"))
            animationStartNanos = 0L

            if (running) postFrame()
        }.onFailure {
            surfaceView.post { onFailure(it) }
            destroy()
        }
    }

    private fun loadPart(path: String): Part {
        val currentEngine = engine ?: error("Engine not initialized")
        val loader = assetLoader ?: error("AssetLoader not initialized")
        val resources = resourceLoader ?: error("ResourceLoader not initialized")
        val bytes = context.assets.open(path).use { it.readBytes() }
        check(bytes.size > 100_000) { "$path is unexpectedly small" }
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            flip()
        }
        val asset = loader.createAsset(buffer) ?: error("Could not parse $path")
        resources.loadResources(asset)
        asset.releaseSourceData()
        scene?.addEntities(asset.renderableEntities)
        if (asset.lightEntities.isNotEmpty()) scene?.addEntities(asset.lightEntities)

        val transformManager = currentEngine.transformManager
        val rootInstance = transformManager.getInstance(asset.root)
        check(rootInstance != 0) { "$path has no root transform" }
        val rootBase = FloatArray(16).also { transformManager.getTransform(rootInstance, it) }
        return Part(asset, rootBase)
    }

    private fun resolveRuntimeEntities() {
        val bodyAsset = body?.asset ?: return
        hipsEntity = findFirstEntity(bodyAsset, HIPS_BONES)
        spineEntity = findFirstEntity(bodyAsset, SPINE_BONES)
        leftShoulderEntity = findFirstEntity(bodyAsset, LEFT_SHOULDER_BONES)
        rightShoulderEntity = findFirstEntity(bodyAsset, RIGHT_SHOULDER_BONES)

        val headAsset = head?.asset ?: return
        faceEntity = headAsset.renderableEntities.firstOrNull { entity ->
            val names = runCatching { headAsset.getMorphTargetNames(entity) }.getOrDefault(emptyArray())
            names.any { it.equals("Happy", true) || it.equals("Jaw_Lower", true) || it.contains("Eyes_Closed", true) }
        } ?: 0
    }

    private fun findFirstEntity(asset: FilamentAsset, names: Array<String>): Int {
        names.forEach { name ->
            val entity = asset.getFirstEntityByName(name)
            if (entity != 0) return entity
        }
        return 0
    }

    private fun captureRestHeadTransform() {
        val currentEngine = engine ?: return
        val instance = currentEngine.transformManager.getInstance(headBoneEntity)
        if (instance == 0) return
        val rest = FloatArray(16).also {
            currentEngine.transformManager.getWorldTransform(instance, it)
        }
        val inverse = FloatArray(16)
        check(Matrix.invertM(inverse, 0, rest, 0)) { "Could not invert rest head transform" }
        headBoneRestInverse = inverse
    }

    private fun applyAnimation(frameTimeNanos: Long) {
        val bodyAsset = body?.asset ?: return
        val animator = bodyAsset.instance.animator
        val index = activeAnimation
        if (index !in 0 until animator.animationCount) return
        if (animationStartNanos == 0L) animationStartNanos = frameTimeNanos
        val elapsed = ((frameTimeNanos - animationStartNanos).toDouble() / 1_000_000_000.0).toFloat()
        val duration = animator.getAnimationDuration(index)
        val time = if (duration > .001f) elapsed % duration else elapsed
        animator.applyAnimation(index, time)
    }

    /**
     * Subtle presentation shaping on top of the current animation pose.
     * These transforms never touch BodyProfile measurements or stored tailoring values.
     */
    private fun applyPresentationRig() {
        when (presentation) {
            AvatarPresentation.MASCULINE -> {
                scaleBone(hipsEntity, .975f, 1f, 1f)
                scaleBone(spineEntity, 1.030f, 1f, 1f)
                scaleBone(leftShoulderEntity, 1.045f, 1.015f, 1.015f)
                scaleBone(rightShoulderEntity, 1.045f, 1.015f, 1.015f)
            }
            AvatarPresentation.FEMININE -> {
                scaleBone(hipsEntity, 1.040f, 1f, 1f)
                scaleBone(spineEntity, .985f, 1f, 1f)
                scaleBone(leftShoulderEntity, .980f, .995f, .995f)
                scaleBone(rightShoulderEntity, .980f, .995f, .995f)
            }
        }
    }

    private fun scaleBone(entity: Int, sx: Float, sy: Float, sz: Float) {
        if (entity == 0) return
        val currentEngine = engine ?: return
        val manager = currentEngine.transformManager
        val instance = manager.getInstance(entity)
        if (instance == 0) return
        val local = FloatArray(16).also { manager.getTransform(instance, it) }
        val scale = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        scale[0] = sx
        scale[5] = sy
        scale[10] = sz
        val out = FloatArray(16)
        Matrix.multiplyMM(out, 0, local, 0, scale, 0)
        manager.setTransform(instance, out)
    }

    private fun attachHeadAndHairToBody() {
        val currentEngine = engine ?: return
        val inverseRest = headBoneRestInverse ?: return
        val boneInstance = currentEngine.transformManager.getInstance(headBoneEntity)
        if (boneInstance == 0) return
        val currentHeadWorld = FloatArray(16).also {
            currentEngine.transformManager.getWorldTransform(boneInstance, it)
        }
        val delta = FloatArray(16)
        Matrix.multiplyMM(delta, 0, currentHeadWorld, 0, inverseRest, 0)

        val headWidth = if (presentation == AvatarPresentation.MASCULINE) 1.018f else .992f
        attachPart(head, delta, headWidth, 1f, 1f)

        val hairScale = hairScale()
        attachPart(hair, delta, hairScale.first, hairScale.second, hairScale.first)
    }

    private fun attachPart(part: Part?, delta: FloatArray, sx: Float, sy: Float, sz: Float) {
        part ?: return
        val currentEngine = engine ?: return
        val scale = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        scale[0] = sx
        scale[5] = sy
        scale[10] = sz
        val scaledBase = FloatArray(16)
        Matrix.multiplyMM(scaledBase, 0, part.rootBase, 0, scale, 0)
        val out = FloatArray(16)
        Matrix.multiplyMM(out, 0, delta, 0, scaledBase, 0)
        val instance = currentEngine.transformManager.getInstance(part.asset.root)
        if (instance != 0) currentEngine.transformManager.setTransform(instance, out)
    }

    private fun hairScale(): Pair<Float, Float> = when (appearance.hairVariant) {
        "shortFlat" -> .90f to .82f
        "shortCurly" -> .96f to .91f
        "bob" -> 1.00f to .98f
        "longButNotTooLong" -> 1.02f to 1.08f
        else -> 1.00f to 1.00f
    }

    private fun applyBodyRootYaw() {
        val part = body ?: return
        val currentEngine = engine ?: return
        val instance = currentEngine.transformManager.getInstance(part.asset.root)
        if (instance == 0) return
        val rotation = FloatArray(16)
        Matrix.setRotateM(rotation, 0, Math.toDegrees(yaw).toFloat(), 0f, 1f, 0f)
        val out = FloatArray(16)
        Matrix.multiplyMM(out, 0, rotation, 0, part.rootBase, 0)
        currentEngine.transformManager.setTransform(instance, out)
    }

    fun update(presentation: AvatarPresentation, value: AvatarAppearance) {
        this.presentation = presentation
        appearance = value.copy(presentation = presentation)
        if (ready) applyAppearanceMaterials()
    }

    private fun applyAppearanceMaterials() {
        val bodyAsset = body?.asset
        val headAsset = head?.asset
        val hairAsset = hair?.asset

        if (bodyAsset != null) {
            setPrimitiveColor(bodyAsset, BODY_ENTITY, 0, appearance.skinColor)
            setPrimitiveColor(bodyAsset, BODY_ENTITY, 1, OUTFIT_WHITE)
            setPrimitiveColor(bodyAsset, SHIRT_ENTITY, 0, OUTFIT_WHITE)
            setPrimitiveColor(bodyAsset, PANTS_ENTITY, 0, OUTFIT_WHITE)
        }
        if (headAsset != null) {
            setPrimitiveColor(headAsset, BODY_ENTITY, 0, appearance.skinColor)
        }
        if (hairAsset != null) {
            setPrimitiveColor(hairAsset, HAIR_ENTITY, 0, appearance.hairColor)
            setAssetVisible(hairAsset, appearance.hairVariant != "bald")
        }
    }

    private fun setPrimitiveColor(asset: FilamentAsset, entityName: String, primitive: Int, hex: String) {
        val entity = asset.getFirstEntityByName(entityName)
        if (entity == 0) return
        val currentEngine = engine ?: return
        val manager = currentEngine.renderableManager
        val instance = manager.getInstance(entity)
        if (instance == 0 || primitive !in 0 until manager.getPrimitiveCount(instance)) return
        val parsed = runCatching { android.graphics.Color.parseColor("#$hex") }.getOrNull() ?: return
        val r = android.graphics.Color.red(parsed) / 255f
        val g = android.graphics.Color.green(parsed) / 255f
        val b = android.graphics.Color.blue(parsed) / 255f
        runCatching {
            manager.getMaterialInstanceAt(instance, primitive)
                .setParameter("baseColorFactor", Colors.RgbaType.SRGB, r, g, b, 1f)
        }
    }

    private fun setAssetVisible(asset: FilamentAsset, visible: Boolean) {
        val currentEngine = engine ?: return
        val manager = currentEngine.renderableManager
        asset.renderableEntities.forEach { entity ->
            val instance = manager.getInstance(entity)
            if (instance != 0) {
                runCatching { manager.setLayerMask(instance, 0xFF, if (visible) 0xFF else 0x00) }
            }
        }
    }

    private fun applyFaceDynamics(frameTimeNanos: Long) {
        val asset = head?.asset ?: return
        val entity = faceEntity
        if (entity == 0) return
        val names = asset.getMorphTargetNames(entity)
        if (names.isEmpty()) return
        val weights = FloatArray(names.size)

        fun set(name: String, value: Float) {
            val index = names.indexOfFirst { it.equals(name, ignoreCase = true) }
            if (index >= 0) weights[index] = value.coerceIn(0f, 1f)
        }

        when (appearance.eyesVariant) {
            "wide" -> {
                set("Eyes_Opened_Max_Left", .52f)
                set("Eyes_Opened_Max_Right", .52f)
            }
            "sharp" -> {
                set("Eyes_Squint", .24f)
                set("Eyebrows_Frown_Left", .14f)
                set("Eyebrows_Frown_Right", .14f)
            }
        }
        if (appearance.eyebrowsVariant == "defined") {
            set("Eyebrows_Raised_Left", .12f)
            set("Eyebrows_Raised_Right", .12f)
        }
        when (appearance.mouthVariant) {
            "smile" -> set("Happy", .48f)
            "full" -> set("Lips_Up_Funnel", .22f)
        }

        // A short organic blink every ~4.6 seconds keeps the face from reading as a static mannequin.
        val seconds = frameTimeNanos.toDouble() / 1_000_000_000.0
        val blinkPhase = seconds % 4.6
        if (blinkPhase < .16) {
            val normalized = (blinkPhase / .16).coerceIn(0.0, 1.0)
            val blink = sin(normalized * PI).toFloat()
            set("Eyes_Closed_Max", blink)
        }

        val currentEngine = engine ?: return
        val renderableManager = currentEngine.renderableManager
        val instance = renderableManager.getInstance(entity)
        if (instance != 0) {
            renderableManager.setMorphWeights(instance, weights, 0)
        }
    }

    fun faceFront() {
        turntableDurationNanos = 0L
        targetYaw = 0.0
    }

    fun playTurntable(durationMs: Long = 2_600L) {
        turntableStartNanos = 0L
        turntableDurationNanos = durationMs.coerceIn(1_400L, 4_200L) * 1_000_000L
        turntableFromYaw = yaw
    }

    private fun updateTurntable(frameTimeNanos: Long) {
        if (turntableDurationNanos <= 0L) return
        if (turntableStartNanos == 0L) turntableStartNanos = frameTimeNanos
        val t = ((frameTimeNanos - turntableStartNanos).toDouble() / turntableDurationNanos).coerceIn(0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t)
        yaw = turntableFromYaw + eased * PI * 2.0
        if (t >= 1.0) {
            turntableDurationNanos = 0L
            turntableStartNanos = 0L
            yaw = 0.0
            targetYaw = 0.0
        }
    }

    private fun updateCamera() {
        val distance = 3.05
        camera?.lookAt(
            0.0,
            1.04,
            distance,
            0.0,
            1.00,
            0.0,
            0.0,
            1.0,
            0.0,
        )
    }

    private fun findAnimation(asset: FilamentAsset, hints: List<String>): Int {
        val animator = asset.instance.animator
        if (animator.animationCount <= 0) return -1
        hints.forEach { hint ->
            for (index in 0 until animator.animationCount) {
                if (animator.getAnimationName(index).contains(hint, ignoreCase = true)) return index
            }
        }
        return 0
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
        }

        directional(82_000f, 1f, .985f, .96f, -.42f, -.76f, -.54f, !lowPowerDevice)
        directional(36_000f, .66f, .89f, 1f, .67f, -.10f, -.73f, false)
        directional(19_000f, 1f, .80f, .89f, -.15f, .26f, .95f, false)
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

        val currentEngine = engine ?: return
        swapChain?.let {
            runCatching { currentEngine.destroySwapChain(it) }
            swapChain = null
        }

        listOfNotNull(body?.asset, head?.asset, hair?.asset).forEach { asset ->
            runCatching { scene?.removeEntities(asset.renderableEntities) }
            runCatching { assetLoader?.destroyAsset(asset) }
        }
        body = null
        head = null
        hair = null

        runCatching { resourceLoader?.destroy() }
        runCatching { materialProvider?.destroyMaterials() }
        runCatching { materialProvider?.destroy() }
        runCatching { assetLoader?.destroy() }

        renderer?.let { runCatching { currentEngine.destroyRenderer(it) } }
        filamentView?.let { runCatching { currentEngine.destroyView(it) } }
        scene?.let { runCatching { currentEngine.destroyScene(it) } }
        camera?.let { runCatching { currentEngine.destroyCameraComponent(it.entity) } }
        if (cameraEntity != 0) EntityManager.get().destroy(cameraEntity)
        runCatching { currentEngine.destroy() }

        engine = null
        renderer = null
        filamentView = null
        scene = null
        camera = null
    }

    private fun synchronizePendingFrames(currentEngine: Engine) {
        val fence = currentEngine.createFence()
        fence.wait(Fence.Mode.FLUSH, Fence.WAIT_FOR_EVER)
        currentEngine.destroyFence(fence)
    }
}
