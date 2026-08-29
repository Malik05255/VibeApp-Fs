package com.almi.ai.ui.body

import android.app.ActivityManager
import android.content.Context
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.Node as SceneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val LabBackground = Color(0xFF04101E)
private val LabSurface = Color(0xFF0B1A2C)
private val LabSurfaceRaised = Color(0xFF10243B)
private val LabText = Color(0xFFF6FAFF)
private val LabMuted = Color(0xFF91A8C5)
private val LabBlue = Color(0xFF86BCFF)
private val LabRed = Color(0xFFFF433D)
private val LabGreen = Color(0xFF59D8A6)
private const val INCH_TO_CM = 2.54f
private const val POUND_TO_KG = 0.45359237f

/**
 * ALMI v8 BODY MAP.
 *
 * Filament is intentionally isolated to this one screen. The scene contains only the body/head,
 * measurement hotspots and one camera. There is no hair, avatar renderer, SurfaceMirrorer,
 * readable swap-chain, snapshot capture, or runtime model download.
 */
@Composable
fun RealHuman3DBodyScreen(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onSnapshotReady: (String) -> Unit = {},
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val deviceProfile = remember(context) { BodyDeviceProfile.from(context) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { MeasureTarget.valueOf(it) }.getOrNull() }
    var targetYaw by rememberSaveable { mutableStateOf(0f) }
    var guideVisible by remember(selectedName) { mutableStateOf(false) }

    LaunchedEffect(selectedName) {
        guideVisible = false
        if (selectedName != null) {
            delay(150)
            guideVisible = true
        }
    }

    val solved = remember(profile) { BodyShapeSolver.solve(profile) }
    val width by animateFloatAsState(solved.widthScale, tween(420), label = "body-width")
    val height by animateFloatAsState(solved.heightScale, tween(420), label = "body-height")
    val depth by animateFloatAsState(solved.depthScale, tween(420), label = "body-depth")
    val yaw by animateFloatAsState(targetYaw, tween(360), label = "body-yaw")
    val zoom by animateFloatAsState(selected?.zoom ?: 1f, tween(430), label = "body-focus")
    val focusX by animateFloatAsState(selected?.let(::focusOffsetX) ?: 0f, tween(430), label = "focus-x")
    val focusY by animateFloatAsState(selected?.let(::focusOffsetY) ?: 0f, tween(430), label = "focus-y")
    val guide by animateFloatAsState(if (guideVisible) 1f else 0f, tween(650), label = "guide")

    val shape = solved.copy(
        widthScale = width,
        heightScale = height,
        depthScale = depth,
        headWidthCompensation = if (width == 0f) 1f else 1f / width,
        headDepthCompensation = if (depth == 0f) 1f else 1f / depth,
    )

    fun open(target: MeasureTarget) {
        selectedName = target.name
        targetYaw = nearestYaw(yaw, target.preferredYaw)
    }

    fun close() {
        selectedName = null
        targetYaw = nearestYaw(yaw, 0f)
    }

    val completed = MeasureTarget.entries.count { it.valueCm(profile) != null } + if (profile.hasExplicitWeight) 1 else 0
    val total = MeasureTarget.entries.size + 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LabBackground)
            .statusBarsPadding(),
    ) {
        Header(language, completed, total, onComplete)
        LinearProgressIndicator(
            progress = { completed.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = LabBlue,
            trackColor = Color.White.copy(alpha = 0.07f),
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            FilamentBodyViewport(
                profile = profile,
                selected = selected,
                shape = shape,
                bodyYaw = yaw,
                focusScale = zoom,
                bodyOffsetX = focusX,
                bodyOffsetY = focusY,
                deviceProfile = deviceProfile,
                onTargetSelected = ::open,
                modifier = Modifier.fillMaxSize(),
            )

            if (selected == null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = LabSurface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Text(
                        tr(language, "اسحب 360°  •  اضغط النقطة الحمراء", "Drag 360°  •  tap a red point"),
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                        color = LabMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                MeasurementGuideOverlay(selected, guide, Modifier.fillMaxSize())
                MeasurementCard(
                    language = language,
                    target = selected,
                    existingCm = selected.valueCm(profile),
                    onConfirm = { cm ->
                        if (selected == MeasureTarget.HEIGHT) {
                            onHeightChanged(cm / INCH_TO_CM)
                        } else {
                            selected.point?.let { onMeasurementChanged(it, cm / INCH_TO_CM) }
                        }
                        close()
                    },
                    onClear = selected.point
                        ?.takeIf { it in profile.measurementsInches }
                        ?.let { point -> ({ onMeasurementCleared(point) }) },
                    onClose = ::close,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                )
            }
        }

        WeightDock(language, profile, onWeightChanged)
    }
}

@Composable
private fun Header(language: String, completed: Int, total: Int, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ALMI / BODY MAP", color = LabBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(
                tr(language, "قياسات جسمك", "Your measurements"),
                color = LabText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(999.dp), color = LabSurfaceRaised) {
                Text("$completed/$total", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = LabMuted, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onDone) { Text(tr(language, "تم", "Done"), color = LabText, fontWeight = FontWeight.Bold) }
        }
    }
}

private data class BodyDeviceProfile(
    val lowRam: Boolean,
    val memoryClassMb: Int,
    val loadHead: Boolean,
) {
    companion object {
        fun from(context: Context): BodyDeviceProfile {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val low = manager.isLowRamDevice
            val memory = manager.memoryClass
            return BodyDeviceProfile(low, memory, !low && memory >= 192)
        }
    }
}

@Composable
private fun FilamentBodyViewport(
    profile: BodyProfile,
    selected: MeasureTarget?,
    shape: DigitalTwinShape,
    bodyYaw: Float,
    focusScale: Float,
    bodyOffsetX: Float,
    bodyOffsetY: Float,
    deviceProfile: BodyDeviceProfile,
    onTargetSelected: (MeasureTarget) -> Unit,
    modifier: Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val redMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(LabRed, metallic = 0.04f, roughness = 0.30f, reflectance = 0.56f)
    }
    val selectedMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFFF8A82), metallic = 0.03f, roughness = 0.24f, reflectance = 0.62f)
    }

    val gestureListener = rememberOnGestureListener(
        onSingleTapUp = { _: MotionEvent, node: SceneNode? ->
            node?.name
                ?.takeIf { it.startsWith(HOTSPOT_PREFIX) }
                ?.removePrefix(HOTSPOT_PREFIX)
                ?.let { runCatching { MeasureTarget.valueOf(it) }.getOrNull() }
                ?.let(onTargetSelected)
        },
    )

    var loadFailure by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    Box(modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            renderQuality = RenderQuality.Performance,
            autoCenterContent = true,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(x = 0f, y = 0.88f, z = 3.05f),
                targetPosition = Position(x = 0f, y = 0.88f, z = 0f),
            ),
            onGestureListener = gestureListener,
        ) {
            var body by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }
            var head by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }

            LaunchedEffect(modelLoader, deviceProfile.loadHead) {
                loadFailure = false
                loaded = false
                body = runCatching { modelLoader.loadModelInstance(BODY_ASSET) }
                    .onFailure { loadFailure = true }
                    .getOrNull()
                loaded = body != null

                if (body != null && deviceProfile.loadHead) {
                    delay(550)
                    head = runCatching { modelLoader.loadModelInstance(HEAD_ASSET) }
                        .onFailure { loadFailure = true }
                        .getOrNull()
                }
            }

            Node(
                position = Position(x = bodyOffsetX, y = bodyOffsetY, z = 0f),
                rotation = Rotation(y = bodyYaw),
                scale = Scale(
                    shape.widthScale * focusScale,
                    shape.heightScale * focusScale,
                    shape.depthScale * focusScale,
                ),
            ) {
                body?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        apply = {
                            name = "almi_body"
                            isHittable = false
                        },
                    )
                }
                head?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        scale = Scale(shape.headWidthCompensation, 1f, shape.headDepthCompensation),
                        apply = {
                            name = "almi_head"
                            isHittable = false
                        },
                    )
                }

                landmarks.forEach { landmark ->
                    SphereNode(
                        radius = if (selected == landmark.target) 0.032f else 0.021f,
                        position = landmark.position,
                        materialInstance = if (selected == landmark.target) selectedMaterial else redMaterial,
                        apply = {
                            name = "$HOTSPOT_PREFIX${landmark.target.name}"
                            isHittable = true
                        },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            shape = RoundedCornerShape(999.dp),
            color = LabSurface.copy(alpha = 0.84f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
        ) {
            Text(
                if (loadFailure) "FILAMENT • SAFE MODE" else if (loaded) "FILAMENT • 360°" else "FILAMENT • LOADING",
                Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                color = if (loadFailure) LabRed else LabMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!deviceProfile.loadHead) {
            Text(
                tr(language = "en", ar = "", en = ""),
                modifier = Modifier.size(0.dp),
            )
        }
    }
}

@Composable
private fun MeasurementGuideOverlay(target: MeasureTarget, progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        val pair = guideCoordinates(target, size.width, size.height)
        val start = pair.first
        val end = Offset(
            start.x + (pair.second.x - start.x) * progress,
            start.y + (pair.second.y - start.y) * progress,
        )
        drawLine(LabBlue.copy(alpha = 0.25f), start, end, 9f, StrokeCap.Round)
        drawLine(LabBlue, start, end, 3f, StrokeCap.Round)
        if (progress > 0.15f) {
            drawArrowHead(end, start)
            drawCircle(LabBlue, 4.5f, start)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(tip: Offset, from: Offset) {
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
    drawPath(path, LabBlue)
}

private fun guideCoordinates(target: MeasureTarget, width: Float, height: Float): Pair<Offset, Offset> = when (target) {
    MeasureTarget.HEIGHT -> Offset(width * .50f, height * .16f) to Offset(width * .50f, height * .86f)
    MeasureTarget.NECK -> Offset(width * .43f, height * .25f) to Offset(width * .57f, height * .25f)
    MeasureTarget.SHOULDERS -> Offset(width * .30f, height * .31f) to Offset(width * .70f, height * .31f)
    MeasureTarget.CHEST -> Offset(width * .30f, height * .39f) to Offset(width * .70f, height * .39f)
    MeasureTarget.WAIST -> Offset(width * .36f, height * .50f) to Offset(width * .64f, height * .50f)
    MeasureTarget.HIPS -> Offset(width * .33f, height * .58f) to Offset(width * .67f, height * .58f)
    MeasureTarget.ARM_LENGTH -> Offset(width * .31f, height * .31f) to Offset(width * .18f, height * .57f)
    MeasureTarget.WRIST -> Offset(width * .15f, height * .55f) to Offset(width * .23f, height * .55f)
    MeasureTarget.HAND -> Offset(width * .18f, height * .57f) to Offset(width * .16f, height * .66f)
    MeasureTarget.THIGH -> Offset(width * .36f, height * .62f) to Offset(width * .49f, height * .62f)
    MeasureTarget.INSEAM -> Offset(width * .50f, height * .60f) to Offset(width * .45f, height * .88f)
    MeasureTarget.CALF -> Offset(width * .38f, height * .78f) to Offset(width * .48f, height * .78f)
    MeasureTarget.FOOT -> Offset(width * .35f, height * .90f) to Offset(width * .49f, height * .90f)
}

@Composable
private fun MeasurementCard(
    language: String,
    target: MeasureTarget,
    existingCm: Float?,
    onConfirm: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var value by remember(target, existingCm) { mutableStateOf(existingCm?.let(::format).orEmpty()) }
    val parsed = value.toFloatOrNull()
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = LabSurface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, LabBlue.copy(alpha = 0.25f)),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(target.title(language), color = LabText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(target.instruction(language), color = LabMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = LabMuted) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = numeric(it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LabText,
                        unfocusedTextColor = LabText,
                        focusedBorderColor = LabBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                        focusedContainerColor = LabBackground.copy(alpha = 0.55f),
                        unfocusedContainerColor = LabBackground.copy(alpha = 0.55f),
                    ),
                )
                Button(
                    onClick = { parsed?.takeIf { it > 0f }?.let(onConfirm) },
                    enabled = parsed?.let { it > 0f } == true,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LabGreen, contentColor = Color(0xFF062017)),
                ) { Icon(Icons.Rounded.Check, null) }
            }
            onClear?.let {
                TextButton(onClick = it) { Text(tr(language, "حذف القياس", "Clear measurement"), color = LabRed) }
            }
        }
    }
}

@Composable
private fun WeightDock(language: String, profile: BodyProfile, onWeightChanged: (Float) -> Unit) {
    var value by remember(profile.weightPounds, profile.hasExplicitWeight) {
        mutableStateOf(if (profile.hasExplicitWeight) format(profile.weightKilograms) else "")
    }
    val parsed = value.toFloatOrNull()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(26.dp),
        color = LabSurfaceRaised,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr(language, "الوزن", "Weight"), color = LabText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(tr(language, "يتفاعل حجم الجسم مباشرة", "Body volume reacts live"), color = LabMuted, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = numeric(it) },
                modifier = Modifier.width(132.dp),
                singleLine = true,
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LabText,
                    unfocusedTextColor = LabText,
                    focusedBorderColor = LabBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                ),
            )
            Button(
                onClick = { parsed?.takeIf { it > 0f }?.let { onWeightChanged(it / POUND_TO_KG) } },
                enabled = parsed?.let { it > 0f } == true,
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LabGreen, contentColor = Color(0xFF062017)),
            ) { Icon(Icons.Rounded.Check, null) }
        }
    }
}

private enum class MeasureTarget(
    val point: BodyMeasurePoint?,
    val preferredYaw: Float,
    val zoom: Float,
) {
    HEIGHT(null, 0f, 1.05f),
    NECK(BodyMeasurePoint.NECK, 0f, 1.38f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, 345f, 1.30f),
    CHEST(BodyMeasurePoint.CHEST, 0f, 1.28f),
    WAIST(BodyMeasurePoint.WAIST, 0f, 1.30f),
    HIPS(BodyMeasurePoint.HIPS, 0f, 1.30f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, 325f, 1.40f),
    WRIST(BodyMeasurePoint.WRIST, 325f, 1.48f),
    HAND(BodyMeasurePoint.HAND, 325f, 1.58f),
    THIGH(BodyMeasurePoint.THIGH, 0f, 1.42f),
    INSEAM(BodyMeasurePoint.INSEAM, 0f, 1.34f),
    CALF(BodyMeasurePoint.CALF, 0f, 1.48f),
    FOOT(BodyMeasurePoint.FOOT, 70f, 1.60f),
    ;

    fun valueCm(profile: BodyProfile): Float? = if (this == HEIGHT) {
        profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
    } else {
        point?.let { profile.measurementsInches[it]?.times(INCH_TO_CM) }
    }

    fun title(language: String): String = when (this) {
        HEIGHT -> tr(language, "الطول", "Height")
        NECK -> tr(language, "محيط الرقبة", "Neck")
        SHOULDERS -> tr(language, "عرض الكتفين", "Shoulders")
        CHEST -> tr(language, "محيط الصدر", "Chest")
        WAIST -> tr(language, "محيط الخصر", "Waist")
        HIPS -> tr(language, "محيط الورك", "Hips")
        ARM_LENGTH -> tr(language, "طول الذراع", "Arm length")
        WRIST -> tr(language, "محيط المعصم", "Wrist")
        HAND -> tr(language, "طول اليد", "Hand length")
        THIGH -> tr(language, "محيط الفخذ", "Thigh")
        INSEAM -> tr(language, "طول الساق الداخلي", "Inseam")
        CALF -> tr(language, "محيط الساق", "Calf")
        FOOT -> tr(language, "طول القدم", "Foot length")
    }

    fun instruction(language: String): String = when (this) {
        HEIGHT -> tr(language, "من أعلى الرأس إلى أسفل القدم.", "Top of head to the floor.")
        NECK -> tr(language, "حول قاعدة الرقبة بدون شد.", "Around the base of the neck without tightening.")
        SHOULDERS -> tr(language, "من نهاية كتف إلى نهاية الكتف الآخر.", "Shoulder tip to shoulder tip.")
        CHEST -> tr(language, "حول أعرض نقطة من الصدر.", "Around the fullest chest point.")
        WAIST -> tr(language, "حول أضيق نقطة من الخصر الطبيعي.", "Around the natural waist.")
        HIPS -> tr(language, "حول أعرض نقطة من الورك.", "Around the fullest hips.")
        ARM_LENGTH -> tr(language, "من نقطة الكتف إلى عظمة المعصم.", "Shoulder point to wrist bone.")
        WRIST -> tr(language, "حول عظمة المعصم.", "Around the wrist bone.")
        HAND -> tr(language, "من بداية راحة اليد إلى نهاية أطول إصبع.", "Wrist crease to the longest fingertip.")
        THIGH -> tr(language, "حول أعرض جزء من أعلى الفخذ.", "Around the fullest upper thigh.")
        INSEAM -> tr(language, "من أعلى داخل الساق إلى الأرض.", "Crotch to the floor along the inner leg.")
        CALF -> tr(language, "حول أعرض نقطة من عضلة الساق.", "Around the fullest calf point.")
        FOOT -> tr(language, "من مؤخرة الكعب إلى أطول إصبع.", "Back of heel to longest toe.")
    }
}

private data class Landmark(val target: MeasureTarget, val position: Position)

private val landmarks = listOf(
    Landmark(MeasureTarget.HEIGHT, Position(0.15f, 1.73f, 0.08f)),
    Landmark(MeasureTarget.NECK, Position(0f, 1.53f, 0.10f)),
    Landmark(MeasureTarget.SHOULDERS, Position(-0.27f, 1.45f, 0.08f)),
    Landmark(MeasureTarget.CHEST, Position(0f, 1.30f, 0.18f)),
    Landmark(MeasureTarget.WAIST, Position(0f, 1.05f, 0.15f)),
    Landmark(MeasureTarget.HIPS, Position(0f, 0.88f, 0.14f)),
    Landmark(MeasureTarget.ARM_LENGTH, Position(-0.37f, 1.15f, 0.06f)),
    Landmark(MeasureTarget.WRIST, Position(-0.43f, 0.83f, 0.05f)),
    Landmark(MeasureTarget.HAND, Position(-0.46f, 0.73f, 0.05f)),
    Landmark(MeasureTarget.THIGH, Position(-0.13f, 0.65f, 0.10f)),
    Landmark(MeasureTarget.INSEAM, Position(0f, 0.73f, 0.08f)),
    Landmark(MeasureTarget.CALF, Position(-0.12f, 0.36f, 0.07f)),
    Landmark(MeasureTarget.FOOT, Position(-0.12f, 0.08f, 0.18f)),
)

private fun focusOffsetX(target: MeasureTarget): Float = when (target) {
    MeasureTarget.ARM_LENGTH, MeasureTarget.WRIST, MeasureTarget.HAND -> 0.25f
    MeasureTarget.FOOT, MeasureTarget.CALF, MeasureTarget.THIGH -> 0.10f
    else -> 0f
}

private fun focusOffsetY(target: MeasureTarget): Float = when (target) {
    MeasureTarget.HEIGHT -> 0f
    MeasureTarget.NECK, MeasureTarget.SHOULDERS, MeasureTarget.CHEST -> -0.28f
    MeasureTarget.WAIST, MeasureTarget.HIPS, MeasureTarget.ARM_LENGTH -> -0.03f
    MeasureTarget.WRIST, MeasureTarget.HAND -> 0.04f
    MeasureTarget.THIGH, MeasureTarget.INSEAM -> 0.23f
    MeasureTarget.CALF -> 0.42f
    MeasureTarget.FOOT -> 0.55f
}

private fun nearestYaw(current: Float, preferred: Float): Float {
    val normalized = ((current % 360f) + 360f) % 360f
    var delta = preferred - normalized
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return current + delta
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
private fun numeric(value: String): String = value.filter { it.isDigit() || it == '.' }.take(7)
private fun format(value: Float): String = if (abs(value - value.roundToInt()) < 0.05f) {
    value.roundToInt().toString()
} else {
    "%.1f".format(Locale.US, value)
}

private const val HOTSPOT_PREFIX = "almi_measure_"
private const val BODY_ASSET = "almi3d/vitruvian_body.glb"
private const val HEAD_ASSET = "almi3d/vitruvian_head.glb"
