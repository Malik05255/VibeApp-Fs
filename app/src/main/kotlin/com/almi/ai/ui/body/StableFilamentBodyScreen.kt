package com.almi.ai.ui.body

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val BodyBg = Color(0xFF04101E)
private val BodySurface = Color(0xFF0B1A2C)
private val BodyRaised = Color(0xFF10243B)
private val BodyText = Color(0xFFF6FAFF)
private val BodyMuted = Color(0xFF91A8C5)
private val BodyBlue = Color(0xFF86BCFF)
private val BodyRed = Color(0xFFFF433D)
private val BodyGreen = Color(0xFF59D8A6)
private const val CM_PER_INCH = 2.54f
private const val KG_PER_POUND = 0.45359237f

/**
 * ALMI BODY MAP — direct Google Filament implementation.
 *
 * SceneView is intentionally not used here. The renderer owns exactly one OPENGL Filament Engine,
 * one SurfaceView, one Scene, and two staged glTF assets (body then head). Measurement hotspots are
 * Compose overlays, so no custom Filament materials or geometry are created for UI chrome.
 */
@Composable
fun StableFilamentBodyScreen(
    language: String,
    profile: BodyProfile,
    onProfileChanged: (BodyProfile) -> Unit,
    onDone: () -> Unit,
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { BodyTarget.valueOf(it) }.getOrNull() }
    var yaw by rememberSaveable { mutableFloatStateOf(0f) }

    val solved = remember(profile) { BodyShapeSolver.solve(profile) }
    val width by animateFloatAsState(solved.widthScale, tween(380), label = "body-width")
    val height by animateFloatAsState(solved.heightScale, tween(380), label = "body-height")
    val depth by animateFloatAsState(solved.depthScale, tween(380), label = "body-depth")
    val zoom by animateFloatAsState(selected?.zoom ?: 1f, tween(420), label = "body-zoom")
    val focusX by animateFloatAsState(selected?.focusX ?: 0f, tween(420), label = "body-focus-x")
    val focusY by animateFloatAsState(selected?.focusY ?: 0f, tween(420), label = "body-focus-y")

    fun open(target: BodyTarget) {
        selectedName = target.name
        yaw = target.yaw
    }

    fun close() {
        selectedName = null
        yaw = 0f
    }

    val completed = BodyTarget.entries.count { it.valueCm(profile) != null } + if (profile.hasExplicitWeight) 1 else 0
    val total = BodyTarget.entries.size + 1

    Column(
        Modifier
            .fillMaxSize()
            .background(BodyBg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / FILAMENT", color = BodyBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (language == "ar") "قياسات جسمك" else "Your measurements",
                    color = BodyText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = BodyRaised) {
                    Text("$completed/$total", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = BodyMuted, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDone) {
                    Text(if (language == "ar") "تم" else "Done", color = BodyText, fontWeight = FontWeight.Bold)
                }
            }
        }

        LinearProgressIndicator(
            progress = { completed.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = BodyBlue,
            trackColor = Color.White.copy(alpha = .07f),
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (selected == null) yaw += dragAmount * .42f
                    }
                },
        ) {
            AndroidView(
                factory = { context -> DirectFilamentBodyView(context) },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.updateTransform(
                        width = width,
                        height = height,
                        depth = depth,
                        yawDegrees = yaw,
                        focusScale = zoom,
                        offsetX = focusX,
                        offsetY = focusY,
                    )
                },
            )

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = BodySurface.copy(alpha = .90f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
            ) {
                Text(
                    if (language == "ar") "اسحب 360°  •  اضغط النقطة الحمراء" else "Drag 360°  •  tap a red point",
                    Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                    color = BodyMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            val density = LocalDensity.current
            BodyTarget.entries.forEach { target ->
                val x = maxWidth * target.screenX
                val y = maxHeight * target.screenY
                val active = target == selected
                Box(
                    modifier = Modifier
                        .offset(x = x - 21.dp, y = y - 21.dp)
                        .size(42.dp)
                        .clickable { open(target) },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(if (active) 19.dp else 15.dp),
                        shape = CircleShape,
                        color = BodyRed.copy(alpha = if (active) 1f else .92f),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = .78f)),
                        shadowElevation = if (active) 9.dp else 5.dp,
                    ) {}
                }
            }

            if (selected != null) {
                MeasurementArrow(target = selected, modifier = Modifier.fillMaxSize())
                StableMeasureCard(
                    language = language,
                    target = selected,
                    existingCm = selected.valueCm(profile),
                    onConfirm = { centimeters ->
                        val next = when (selected) {
                            BodyTarget.HEIGHT -> profile.copy(
                                heightInches = centimeters / CM_PER_INCH,
                                hasExplicitHeight = true,
                            )
                            else -> selected.point?.let { point ->
                                profile.copy(measurementsInches = profile.measurementsInches + (point to (centimeters / CM_PER_INCH)))
                            } ?: profile
                        }
                        onProfileChanged(next)
                        close()
                    },
                    onClear = selected.point?.takeIf { it in profile.measurementsInches }?.let { point ->
                        { onProfileChanged(profile.copy(measurementsInches = profile.measurementsInches - point)); close() }
                    },
                    onClose = ::close,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp),
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                shape = RoundedCornerShape(999.dp),
                color = BodySurface.copy(alpha = .84f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
            ) {
                Text(
                    "FILAMENT • OPENGL • DIRECT",
                    Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = BodyMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        StableWeightDock(language = language, profile = profile) { kilograms ->
            onProfileChanged(
                profile.copy(
                    weightPounds = kilograms / KG_PER_POUND,
                    hasExplicitWeight = true,
                )
            )
        }
    }
}

private class DirectFilamentBodyView(context: Context) : SurfaceView(context) {
    companion object {
        init { Utils.init() }
        private const val BODY_MODEL = "almi3d/vitruvian_body.glb"
        private const val HEAD_MODEL = "almi3d/vitruvian_head.glb"
    }

    private val engine: Engine = Engine.create(Engine.Backend.OPENGL)
    private val renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val filamentView: View = engine.createView()
    private val entityManager = EntityManager.get()
    private val cameraEntity = entityManager.create()
    private val camera = engine.createCamera(cameraEntity)
    private val materialProvider = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, materialProvider, entityManager)
    private val resourceLoader = ResourceLoader(engine)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val readyRenderables = IntArray(256)

    private var swapChain: SwapChain? = null
    private var bodyAsset: FilamentAsset? = null
    private var headAsset: FilamentAsset? = null
    private var bodyRequested = false
    private var headRequested = false
    private var destroyed = false
    private var rendering = false

    private var currentWidth = 1f
    private var currentHeight = 1f
    private var currentDepth = 1f
    private var currentYaw = 0f
    private var currentFocus = 1f
    private var currentOffsetX = 0f
    private var currentOffsetY = 0f

    private val skybox: Skybox = Skybox.Builder()
        .color(0.008f, 0.025f, 0.055f, 1f)
        .build(engine)

    private val lightEntity = entityManager.create()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed || !rendering) return
            resourceLoader.asyncUpdateLoad()
            assetLoader.gc()
            bodyAsset?.let(::populateReadyEntities)
            headAsset?.let(::populateReadyEntities)

            val chain = swapChain
            if (chain != null && uiHelper.isReadyToRender && renderer.beginFrame(chain, frameTimeNanos)) {
                renderer.render(filamentView)
                renderer.endFrame()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setZOrderOnTop(false)
        scene.skybox = skybox
        filamentView.scene = scene
        filamentView.camera = camera

        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.76f, 0.86f, 1.0f)
            .intensity(75_000f)
            .direction(-0.35f, -0.85f, -0.30f)
            .castShadows(false)
            .build(engine, lightEntity)
        scene.addEntity(lightEntity)

        camera.lookAt(
            0.0, 0.90, 3.10,
            0.0, 0.90, 0.0,
            0.0, 1.0, 0.0,
        )

        uiHelper.isOpaque = true
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let(engine::destroySwapChain)
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                if (width <= 0 || height <= 0) return
                filamentView.viewport = Viewport(0, 0, width, height)
                camera.setProjection(
                    43.0,
                    width.toDouble() / height.toDouble(),
                    0.05,
                    20.0,
                    Camera.Fov.VERTICAL,
                )
            }
        }
        uiHelper.attachTo(this)

        post {
            if (!destroyed) {
                requestBody()
                postDelayed({ if (!destroyed) requestHead() }, 1_500L)
            }
        }
    }

    fun updateTransform(
        width: Float,
        height: Float,
        depth: Float,
        yawDegrees: Float,
        focusScale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        currentWidth = width
        currentHeight = height
        currentDepth = depth
        currentYaw = yawDegrees
        currentFocus = focusScale
        currentOffsetX = offsetX
        currentOffsetY = offsetY
        applyTransforms()
    }

    private fun requestBody() {
        if (bodyRequested) return
        bodyRequested = true
        bodyAsset = loadAsset(BODY_MODEL)
        applyTransforms()
    }

    private fun requestHead() {
        if (headRequested || bodyAsset == null) return
        headRequested = true
        headAsset = loadAsset(HEAD_MODEL)
        applyTransforms()
    }

    private fun loadAsset(path: String): FilamentAsset? = runCatching {
        val bytes = context.assets.open(path).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                flip()
            }
        assetLoader.createAsset(buffer)?.also { asset ->
            resourceLoader.asyncBeginLoad(asset)
            asset.releaseSourceData()
            if (asset.lightEntities.isNotEmpty()) scene.addEntities(asset.lightEntities)
        }
    }.getOrNull()

    private fun populateReadyEntities(asset: FilamentAsset) {
        while (true) {
            val count = asset.popRenderables(readyRenderables)
            if (count <= 0) break
            scene.addEntities(readyRenderables.copyOf(count))
        }
    }

    private fun applyTransforms() {
        bodyAsset?.let { asset ->
            setRootTransform(
                asset = asset,
                sx = currentWidth * currentFocus,
                sy = currentHeight * currentFocus,
                sz = currentDepth * currentFocus,
            )
        }
        headAsset?.let { asset ->
            // Keep head width/depth natural while still following overall height/focus and rotation.
            setRootTransform(
                asset = asset,
                sx = currentFocus,
                sy = currentHeight * currentFocus,
                sz = currentFocus,
            )
        }
    }

    private fun setRootTransform(asset: FilamentAsset, sx: Float, sy: Float, sz: Float) {
        val instance = engine.transformManager.getInstance(asset.root)
        if (instance == 0) return
        val radians = Math.toRadians(currentYaw.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val tx = currentOffsetX
        val ty = currentOffsetY

        val matrix = floatArrayOf(
            c * sx, 0f, -s * sx, 0f,
            0f, sy, 0f, 0f,
            s * sz, 0f, c * sz, 0f,
            tx, ty, 0f, 1f,
        )
        engine.transformManager.setTransform(instance, matrix)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!destroyed && !rendering) {
            rendering = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun onDetachedFromWindow() {
        rendering = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        destroyFilament()
        super.onDetachedFromWindow()
    }

    private fun destroyFilament() {
        if (destroyed) return
        destroyed = true
        rendering = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        uiHelper.detach()
        resourceLoader.asyncCancelLoad()
        resourceLoader.evictResourceData()

        bodyAsset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        headAsset?.let {
            scene.removeEntities(it.entities)
            assetLoader.destroyAsset(it)
        }
        bodyAsset = null
        headAsset = null

        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        resourceLoader.destroy()
        scene.removeEntity(lightEntity)
        engine.destroyEntity(lightEntity)
        entityManager.destroy(lightEntity)
        engine.destroySkybox(skybox)
        engine.destroyRenderer(renderer)
        engine.destroyView(filamentView)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        entityManager.destroy(cameraEntity)
        engine.destroy()
    }
}

@Composable
private fun StableMeasureCard(
    language: String,
    target: BodyTarget,
    existingCm: Float?,
    onConfirm: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var value by remember(target, existingCm) { mutableStateOf(existingCm?.let(::formatBodyNumber).orEmpty()) }
    val parsed = value.toFloatOrNull()

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = BodySurface.copy(alpha = .97f),
        border = BorderStroke(1.dp, BodyBlue.copy(alpha = .25f)),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(target.title(language), color = BodyText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(target.instruction(language), color = BodyMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = BodyMuted) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BodyText,
                        unfocusedTextColor = BodyText,
                        focusedBorderColor = BodyBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = .16f),
                        focusedContainerColor = BodyBg.copy(alpha = .55f),
                        unfocusedContainerColor = BodyBg.copy(alpha = .55f),
                    ),
                )
                Button(
                    onClick = { parsed?.takeIf { it > 0f }?.let(onConfirm) },
                    enabled = parsed?.let { it > 0f } == true,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BodyGreen, contentColor = Color(0xFF062017)),
                ) { Icon(Icons.Rounded.Check, null) }
            }
            onClear?.let { clear ->
                TextButton(onClick = clear) {
                    Text(if (language == "ar") "حذف القياس" else "Clear measurement", color = BodyRed)
                }
            }
        }
    }
}

@Composable
private fun StableWeightDock(language: String, profile: BodyProfile, onKilograms: (Float) -> Unit) {
    var value by remember(profile.weightPounds, profile.hasExplicitWeight) {
        mutableStateOf(if (profile.hasExplicitWeight) formatBodyNumber(profile.weightKilograms) else "")
    }
    val parsed = value.toFloatOrNull()

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(26.dp),
        color = BodyRaised,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (language == "ar") "الوزن" else "Weight", color = BodyText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (language == "ar") "يتفاعل حجم الجسم مباشرة" else "Body volume reacts live", color = BodyMuted, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                modifier = Modifier.width(132.dp),
                singleLine = true,
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BodyText,
                    unfocusedTextColor = BodyText,
                    focusedBorderColor = BodyBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = .15f),
                ),
            )
            Button(
                onClick = { parsed?.takeIf { it > 0f }?.let(onKilograms) },
                enabled = parsed?.let { it > 0f } == true,
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BodyGreen, contentColor = Color(0xFF062017)),
            ) { Icon(Icons.Rounded.Check, null) }
        }
    }
}

@Composable
private fun MeasurementArrow(target: BodyTarget, modifier: Modifier) {
    Canvas(modifier) {
        val (a, b) = target.guide(size.width, size.height)
        drawLine(BodyBlue.copy(alpha = .22f), a, b, 9f, StrokeCap.Round)
        drawLine(BodyBlue, a, b, 3f, StrokeCap.Round)
        drawArrowHead(b, a)
        drawArrowHead(a, b)
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val ux = dx / length
    val uy = dy / length
    val px = -uy
    val py = ux
    val back = Offset(tip.x - ux * 18f, tip.y - uy * 18f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(back.x + px * 8f, back.y + py * 8f)
        lineTo(back.x - px * 8f, back.y - py * 8f)
        close()
    }
    drawPath(path, BodyBlue)
}

private enum class BodyTarget(
    val point: BodyMeasurePoint?,
    val screenX: Float,
    val screenY: Float,
    val yaw: Float,
    val zoom: Float,
    val focusX: Float,
    val focusY: Float,
) {
    HEIGHT(null, .58f, .15f, 0f, 1.05f, 0f, 0f),
    NECK(BodyMeasurePoint.NECK, .50f, .25f, 0f, 1.38f, 0f, -.28f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, .34f, .31f, -15f, 1.30f, 0f, -.28f),
    CHEST(BodyMeasurePoint.CHEST, .50f, .39f, 0f, 1.28f, 0f, -.28f),
    WAIST(BodyMeasurePoint.WAIST, .50f, .50f, 0f, 1.30f, 0f, -.03f),
    HIPS(BodyMeasurePoint.HIPS, .39f, .58f, 0f, 1.30f, 0f, -.03f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, .22f, .47f, -35f, 1.40f, .25f, -.03f),
    WRIST(BodyMeasurePoint.WRIST, .18f, .57f, -35f, 1.48f, .25f, .04f),
    HAND(BodyMeasurePoint.HAND, .16f, .65f, -35f, 1.58f, .25f, .04f),
    THIGH(BodyMeasurePoint.THIGH, .42f, .67f, 0f, 1.42f, .10f, .23f),
    INSEAM(BodyMeasurePoint.INSEAM, .50f, .65f, 0f, 1.34f, 0f, .23f),
    CALF(BodyMeasurePoint.CALF, .41f, .79f, 0f, 1.48f, .10f, .42f),
    FOOT(BodyMeasurePoint.FOOT, .42f, .91f, 70f, 1.60f, .10f, .55f),
    ;

    fun valueCm(profile: BodyProfile): Float? = if (this == HEIGHT) {
        profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
    } else point?.let { profile.measurementsInches[it]?.times(CM_PER_INCH) }

    fun title(language: String): String = when (this) {
        HEIGHT -> arEn(language, "الطول", "Height")
        NECK -> arEn(language, "محيط الرقبة", "Neck")
        SHOULDERS -> arEn(language, "عرض الكتفين", "Shoulders")
        CHEST -> arEn(language, "محيط الصدر", "Chest")
        WAIST -> arEn(language, "محيط الخصر", "Waist")
        HIPS -> arEn(language, "محيط الورك", "Hips")
        ARM_LENGTH -> arEn(language, "طول الذراع", "Arm length")
        WRIST -> arEn(language, "محيط المعصم", "Wrist")
        HAND -> arEn(language, "طول اليد", "Hand length")
        THIGH -> arEn(language, "محيط الفخذ", "Thigh")
        INSEAM -> arEn(language, "طول الساق الداخلي", "Inseam")
        CALF -> arEn(language, "محيط الساق", "Calf")
        FOOT -> arEn(language, "طول القدم", "Foot length")
    }

    fun instruction(language: String): String = when (this) {
        HEIGHT -> arEn(language, "من أعلى الرأس إلى أسفل القدم.", "Top of head to the floor.")
        NECK -> arEn(language, "حول قاعدة الرقبة بدون شد.", "Around the base of the neck without tightening.")
        SHOULDERS -> arEn(language, "من نهاية كتف إلى نهاية الكتف الآخر.", "Shoulder tip to shoulder tip.")
        CHEST -> arEn(language, "حول أعرض نقطة من الصدر.", "Around the fullest chest point.")
        WAIST -> arEn(language, "حول أضيق نقطة من الخصر الطبيعي.", "Around the natural waist.")
        HIPS -> arEn(language, "حول أعرض نقطة من الورك.", "Around the fullest hips.")
        ARM_LENGTH -> arEn(language, "من نقطة الكتف إلى عظمة المعصم.", "Shoulder point to wrist bone.")
        WRIST -> arEn(language, "حول عظمة المعصم.", "Around the wrist bone.")
        HAND -> arEn(language, "من بداية راحة اليد إلى نهاية أطول إصبع.", "Wrist crease to the longest fingertip.")
        THIGH -> arEn(language, "حول أعرض جزء من أعلى الفخذ.", "Around the fullest upper thigh.")
        INSEAM -> arEn(language, "من أعلى داخل الساق إلى الأرض.", "Crotch to the floor along the inner leg.")
        CALF -> arEn(language, "حول أعرض نقطة من عضلة الساق.", "Around the fullest calf point.")
        FOOT -> arEn(language, "من مؤخرة الكعب إلى أطول إصبع.", "Back of heel to longest toe.")
    }

    fun guide(w: Float, h: Float): Pair<Offset, Offset> = when (this) {
        HEIGHT -> Offset(w * .50f, h * .16f) to Offset(w * .50f, h * .86f)
        NECK -> Offset(w * .43f, h * .25f) to Offset(w * .57f, h * .25f)
        SHOULDERS -> Offset(w * .30f, h * .31f) to Offset(w * .70f, h * .31f)
        CHEST -> Offset(w * .30f, h * .39f) to Offset(w * .70f, h * .39f)
        WAIST -> Offset(w * .36f, h * .50f) to Offset(w * .64f, h * .50f)
        HIPS -> Offset(w * .33f, h * .58f) to Offset(w * .67f, h * .58f)
        ARM_LENGTH -> Offset(w * .31f, h * .31f) to Offset(w * .18f, h * .57f)
        WRIST -> Offset(w * .15f, h * .55f) to Offset(w * .23f, h * .55f)
        HAND -> Offset(w * .18f, h * .57f) to Offset(w * .16f, h * .66f)
        THIGH -> Offset(w * .36f, h * .62f) to Offset(w * .49f, h * .62f)
        INSEAM -> Offset(w * .50f, h * .60f) to Offset(w * .45f, h * .88f)
        CALF -> Offset(w * .38f, h * .78f) to Offset(w * .48f, h * .78f)
        FOOT -> Offset(w * .35f, h * .90f) to Offset(w * .49f, h * .90f)
    }
}

private fun arEn(language: String, ar: String, en: String) = if (language == "ar") ar else en
private fun formatBodyNumber(value: Float): String = if (abs(value - value.roundToInt()) < .05f) {
    value.roundToInt().toString()
} else {
    "%.1f".format(Locale.US, value)
}
