package com.almi.ai.ui.v12

import android.app.ActivityManager
import android.content.Context
import android.graphics.PixelFormat
import android.opengl.Matrix
import android.view.Choreographer
import android.view.MotionEvent
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Quality-first multi-asset digital human renderer for ALMI v12.
 *
 * Avatar mode renders the living editor. Measurement mode reuses the same 4K PBR body/head but
 * freezes the skeletal idle at a stable phase, hides hair for unobstructed anatomical landmarks,
 * and projects Mixamo rig landmarks into screen space for Body Map.
 */
internal class V12DigitalHumanRuntime(
    private val context: Context,
    private val surfaceView: SurfaceView,
    initialPresentation: AvatarPresentation,
    initialAppearance: AvatarAppearance,
    private val measurementMode: Boolean = false,
    private val onProjectionChanged: ((V12BodyProjection) -> Unit)? = null,
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
        private val NECK_BONES = arrayOf("mixamorig:Neck", "mixamorig_Neck", "Neck")
        private val HIPS_BONES = arrayOf("mixamorig:Hips", "mixamorig_Hips", "Hips")
        private val SPINE_BONES = arrayOf("mixamorig:Spine", "mixamorig_Spine", "Spine")
        private val SPINE1_BONES = arrayOf("mixamorig:Spine1", "mixamorig_Spine1", "Spine1")
        private val SPINE2_BONES = arrayOf("mixamorig:Spine2", "mixamorig_Spine2", "Spine2")
        private val LEFT_SHOULDER_BONES = arrayOf("mixamorig:LeftShoulder", "mixamorig_LeftShoulder", "LeftShoulder")
        private val RIGHT_SHOULDER_BONES = arrayOf("mixamorig:RightShoulder", "mixamorig_RightShoulder", "RightShoulder")
        private val LEFT_ARM_BONES = arrayOf("mixamorig:LeftArm", "mixamorig_LeftArm", "LeftArm")
        private val RIGHT_ARM_BONES = arrayOf("mixamorig:RightArm", "mixamorig_RightArm", "RightArm")
        private val LEFT_FOREARM_BONES = arrayOf("mixamorig:LeftForeArm", "mixamorig_LeftForeArm", "LeftForeArm")
        private val RIGHT_FOREARM_BONES = arrayOf("mixamorig:RightForeArm", "mixamorig_RightForeArm", "RightForeArm")
        private val LEFT_HAND_BONES = arrayOf("mixamorig:LeftHand", "mixamorig_LeftHand", "LeftHand")
        private val RIGHT_HAND_BONES = arrayOf("mixamorig:RightHand", "mixamorig_RightHand", "RightHand")
        private val LEFT_UPLEG_BONES = arrayOf("mixamorig:LeftUpLeg", "mixamorig_LeftUpLeg", "LeftUpLeg")
        private val RIGHT_UPLEG_BONES = arrayOf("mixamorig:RightUpLeg", "mixamorig_RightUpLeg", "RightUpLeg")
        private val LEFT_LEG_BONES = arrayOf("mixamorig:LeftLeg", "mixamorig_LeftLeg", "LeftLeg")
        private val RIGHT_LEG_BONES = arrayOf("mixamorig:RightLeg", "mixamorig_RightLeg", "RightLeg")
        private val LEFT_FOOT_BONES = arrayOf("mixamorig:LeftFoot", "mixamorig_LeftFoot", "LeftFoot")
        private val RIGHT_FOOT_BONES = arrayOf("mixamorig:RightFoot", "mixamorig_RightFoot", "RightFoot")

        private const val BODY_ENTITY = "cm_vitruvian"
        private const val SHIRT_ENTITY = "Shirt"
        private const val PANTS_ENTITY = "Pants"
        private const val HAIR_ENTITY = "VitHair"
        private const val READY_FRAMES = 3
        private const val OUTFIT_WHITE = "F6F7F8"
        private const val AVATAR_DISTANCE = 3.05
        private const val MEASURE_DISTANCE = 3.05
    }

    private data class Part(
        val asset: FilamentAsset,
        val rootBase: FloatArray,
    )

    private data class WorldPoint(val x: Float, val y: Float, val z: Float)

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
    private var projectionFrame = 0

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

    private var cameraDistance = if (measurementMode) MEASURE_DISTANCE else AVATAR_DISTANCE
    private var targetCameraDistance = cameraDistance
    private var cameraTargetY = 1.0
    private var targetCameraY = 1.0
    private var lastX = 0f
    private var downX = 0f
    private var downY = 0f
    private var pinchDistance = 0f
    private var lastTapUpMs = 0L

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
            camera?.setLensProjection(if (measurementMode) 38.0 else 40.0, width.toDouble() / height.toDouble(), .03, 50.0)
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

            if (measurementMode) {
                cameraDistance += (targetCameraDistance - cameraDistance) * .13
                cameraTargetY += (targetCameraY - cameraTargetY) * .13
            } else {
                updateTurntable(frameTimeNanos)
            }
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
                    if (measurementMode) dispatchProjection()
                    surfaceView.post(onReady)
                }
            } else if (measurementMode) {
                projectionFrame += 1
                if (projectionFrame % 2 == 0) dispatchProjection()
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
                    enabled = !lowPowerDevice && !measurementMode
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
            camera?.setExposure(if (measurementMode) 9.1f else 9.4f, 1f / 125f, 100f)
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

            if (measurementMode) {
                surfaceView.setOnTouchListener { _, event -> handleMeasurementTouch(event) }
            }
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
        val time = if (measurementMode && duration > .001f) {
            (duration * .18f).coerceAtMost(.38f)
        } else if (duration > .001f) {
            elapsed % duration
        } else {
            elapsed
        }
        animator.applyAnimation(index, time)
    }

    /** Stored tailoring measurements are never changed by presentation shaping. */
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
            setAssetVisible(hairAsset, !measurementMode && appearance.hairVariant != "bald")
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

        if (!measurementMode) {
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
        }

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
        if (instance != 0) renderableManager.setMorphWeights(instance, weights, 0)
    }

    fun faceFront() {
        turntableDurationNanos = 0L
        targetYaw = 0.0
    }

    fun playTurntable(durationMs: Long = 2_600L) {
        if (measurementMode) return
        turntableStartNanos = 0L
        turntableDurationNanos = durationMs.coerceIn(1_400L, 4_200L) * 1_000_000L
        turntableFromYaw = yaw
    }

    fun resetMeasurementView() {
        if (!measurementMode) return
        targetYaw = 0.0
        yaw = 0.0
        targetCameraDistance = MEASURE_DISTANCE
        targetCameraY = 1.0
    }

    fun focusOn(anchorY: Float, distance: Float = 2.35f) {
        if (!measurementMode) return
        targetCameraY = (1.0 + (.5f - anchorY) * 1.18f).coerceIn(.48f, 1.52f).toDouble()
        targetCameraDistance = distance.coerceIn(1.78f, 4.1f).toDouble()
    }

    private fun handleMeasurementTouch(event: MotionEvent): Boolean {
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
                        targetCameraDistance = (targetCameraDistance * (pinchDistance / now)).coerceIn(1.58, 4.25)
                    }
                    pinchDistance = now
                } else {
                    val dx = event.x - lastX
                    if (abs(dx) > .15f) targetYaw += dx * .0105
                    lastX = event.x
                }
            }
            MotionEvent.ACTION_UP -> {
                val travel = hypot(event.x - downX, event.y - downY)
                val threshold = context.resources.displayMetrics.density * 18f
                if (travel <= threshold) {
                    val now = event.eventTime
                    if (now - lastTapUpMs in 40L..300L) resetMeasurementView()
                    lastTapUpMs = now
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
        if (measurementMode) {
            camera?.lookAt(
                0.0,
                cameraTargetY + .04,
                cameraDistance,
                0.0,
                cameraTargetY,
                0.0,
                0.0,
                1.0,
                0.0,
            )
        } else {
            camera?.lookAt(
                0.0,
                1.04,
                AVATAR_DISTANCE,
                0.0,
                1.00,
                0.0,
                0.0,
                1.0,
                0.0,
            )
        }
    }

    private fun dispatchProjection() {
        val callback = onProjectionChanged ?: return
        val bodyAsset = body?.asset ?: return
        val currentEngine = engine ?: return
        val currentCamera = camera ?: return
        val manager = currentEngine.transformManager
        val viewMatrix = currentCamera.getViewMatrix(FloatArray(16))
        val projectionMatrix = currentCamera.getProjectionMatrix(DoubleArray(16))

        fun world(aliases: Array<String>): WorldPoint? {
            val entity = findFirstEntity(bodyAsset, aliases)
            if (entity == 0) return null
            val instance = manager.getInstance(entity)
            if (instance == 0) return null
            val transform = manager.getWorldTransform(instance, FloatArray(16))
            return WorldPoint(transform[12], transform[13], transform[14])
        }

        val pelvis = world(HIPS_BONES)
        val spine1 = world(SPINE_BONES)
        val spine2 = world(SPINE1_BONES)
        val spine3 = world(SPINE2_BONES)
        val neck = world(NECK_BONES)
        val headPoint = world(HEAD_BONES)
        val leftShoulder = world(LEFT_SHOULDER_BONES)
        val rightShoulder = world(RIGHT_SHOULDER_BONES)
        val leftUpperArm = world(LEFT_ARM_BONES)
        val rightUpperArm = world(RIGHT_ARM_BONES)
        val leftLowerArm = world(LEFT_FOREARM_BONES)
        val rightLowerArm = world(RIGHT_FOREARM_BONES)
        val leftHand = world(LEFT_HAND_BONES)
        val rightHand = world(RIGHT_HAND_BONES)
        val leftThigh = world(LEFT_UPLEG_BONES)
        val rightThigh = world(RIGHT_UPLEG_BONES)
        val leftCalf = world(LEFT_LEG_BONES)
        val rightCalf = world(RIGHT_LEG_BONES)
        val leftFoot = world(LEFT_FOOT_BONES)
        val rightFoot = world(RIGHT_FOOT_BONES)

        val anatomical = linkedMapOf<String, WorldPoint>()
        fun put(name: String, point: WorldPoint?) {
            if (point != null) anatomical[name] = point
        }
        put("pelvis", pelvis)
        put("spine1", spine1)
        put("spine2", spine2)
        put("spine3", spine3)
        put("neck", neck)
        put("head", headPoint)
        put("leftShoulder", leftShoulder)
        put("rightShoulder", rightShoulder)
        put("leftUpperArm", leftUpperArm)
        put("rightUpperArm", rightUpperArm)
        put("leftElbow", leftLowerArm)
        put("rightElbow", rightLowerArm)
        put("leftHand", leftHand)
        put("rightHand", rightHand)
        put("leftThigh", leftThigh)
        put("rightThigh", rightThigh)
        put("leftCalf", leftCalf)
        put("rightCalf", rightCalf)
        put("leftFoot", leftFoot)
        put("rightFoot", rightFoot)

        if (neck != null && pelvis != null) {
            val axis = subtract(pelvis, neck)
            anatomical["chest"] = add(neck, scale(axis, .28f))
            anatomical["underbust"] = add(neck, scale(axis, .40f))
            anatomical["waist"] = add(neck, scale(axis, .61f))
            anatomical["abdomen"] = add(neck, scale(axis, .75f))
            anatomical["hips"] = add(neck, scale(axis, .93f))
        }
        if (leftShoulder != null && rightShoulder != null) {
            val center = midpoint(leftShoulder, rightShoulder)
            anatomical["shoulderCenter"] = center
            val half = subtract(rightShoulder, center)
            anatomical["chest"]?.let { chest ->
                anatomical["leftBust"] = add(chest, scale(half, -.40f))
                anatomical["rightBust"] = add(chest, scale(half, .40f))
            }
        }
        if (headPoint != null && neck != null) {
            val vector = subtract(headPoint, neck)
            anatomical["crown"] = add(headPoint, scale(vector, .62f))
        }

        val mapped = linkedMapOf<String, V12ProjectedPoint>()
        anatomical.forEach { (name, point) ->
            projectWorld(point, viewMatrix, projectionMatrix)?.let { mapped[name] = it }
        }
        if (mapped.isNotEmpty()) callback(V12BodyProjection(mapped, yaw, cameraDistance))
    }

    private fun projectWorld(point: WorldPoint, view: FloatArray, projection: DoubleArray): V12ProjectedPoint? {
        val vx = view[0] * point.x + view[4] * point.y + view[8] * point.z + view[12]
        val vy = view[1] * point.x + view[5] * point.y + view[9] * point.z + view[13]
        val vz = view[2] * point.x + view[6] * point.y + view[10] * point.z + view[14]
        val vw = view[3] * point.x + view[7] * point.y + view[11] * point.z + view[15]
        val cx = projection[0] * vx + projection[4] * vy + projection[8] * vz + projection[12] * vw
        val cy = projection[1] * vx + projection[5] * vy + projection[9] * vz + projection[13] * vw
        val cw = projection[3] * vx + projection[7] * vy + projection[11] * vz + projection[15] * vw
        if (!cw.isFinite() || abs(cw) < 1e-7) return null
        val ndcX = cx / cw
        val ndcY = cy / cw
        if (!ndcX.isFinite() || !ndcY.isFinite()) return null
        val sx = ((ndcX + 1.0) * .5).toFloat()
        val sy = ((1.0 - ndcY) * .5).toFloat()
        return V12ProjectedPoint(sx, sy, sx in -.12f..1.12f && sy in -.12f..1.12f)
    }

    private fun midpoint(a: WorldPoint, b: WorldPoint) = WorldPoint((a.x + b.x) * .5f, (a.y + b.y) * .5f, (a.z + b.z) * .5f)
    private fun add(a: WorldPoint, b: WorldPoint) = WorldPoint(a.x + b.x, a.y + b.y, a.z + b.z)
    private fun subtract(a: WorldPoint, b: WorldPoint) = WorldPoint(a.x - b.x, a.y - b.y, a.z - b.z)
    private fun scale(a: WorldPoint, factor: Float) = WorldPoint(a.x * factor, a.y * factor, a.z * factor)

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

        directional(if (measurementMode) 78_000f else 82_000f, 1f, .985f, .96f, -.42f, -.76f, -.54f, !lowPowerDevice)
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
