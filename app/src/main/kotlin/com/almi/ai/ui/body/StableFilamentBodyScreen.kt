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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.Node as SceneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
 * The only Filament surface in ALMI.
 *
 * Stability rules:
 * - one Engine / ModelLoader / MaterialLoader per Activity composition
 * - model loading only through rememberModelInstance
 * - body first, head staged later to avoid a native/GPU allocation spike
 * - no SurfaceMirrorer, readback, snapshots, hair, runtime model downloads or per-frame node rebuilds
 * - no Filament object is stored outside this composition
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
    var preferredYaw by rememberSaveable { mutableStateOf(0f) }

    val solved = remember(profile) { BodyShapeSolver.solve(profile) }
    val width by animateFloatAsState(solved.widthScale, tween(420), label = "stable-width")
    val height by animateFloatAsState(solved.heightScale, tween(420), label = "stable-height")
    val depth by animateFloatAsState(solved.depthScale, tween(420), label = "stable-depth")
    val yaw by animateFloatAsState(preferredYaw, tween(420), label = "stable-yaw")
    val zoom by animateFloatAsState(selected?.zoom ?: 1f, tween(430), label = "stable-zoom")
    val shiftX by animateFloatAsState(selected?.focusX ?: 0f, tween(430), label = "stable-x")
    val shiftY by animateFloatAsState(selected?.focusY ?: 0f, tween(430), label = "stable-y")

    val currentShape = solved.copy(
        widthScale = width,
        heightScale = height,
        depthScale = depth,
        headWidthCompensation = if (width == 0f) 1f else 1f / width,
        headDepthCompensation = if (depth == 0f) 1f else 1f / depth,
    )

    fun open(target: BodyTarget) {
        selectedName = target.name
        preferredYaw = target.yaw
    }

    fun close() {
        selectedName = null
        preferredYaw = 0f
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
                Text("ALMI / FILAMENT BODY", color = BodyBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(if (language == "ar") "قياسات جسمك" else "Your measurements", color = BodyText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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

        Box(Modifier.fillMaxWidth().weight(1f)) {
            ManagedFilamentViewport(
                selected = selected,
                shape = currentShape,
                bodyYaw = yaw,
                focusScale = zoom,
                offsetX = shiftX,
                offsetY = shiftY,
                onTargetSelected = ::open,
                modifier = Modifier.fillMaxSize(),
            )

            if (selected == null) {
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = BodySurface.copy(alpha = .92f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                ) {
                    Text(
                        if (language == "ar") "اسحب 360°  •  اضغط النقطة الحمراء" else "Drag 360°  •  tap a red point",
                        Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                        color = BodyMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
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
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
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

private data class Device3dProfile(val loadHead: Boolean) {
    companion object {
        fun from(context: Context): Device3dProfile {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return Device3dProfile(loadHead = !manager.isLowRamDevice && manager.memoryClass >= 192)
        }
    }
}

@Composable
private fun ManagedFilamentViewport(
    selected: BodyTarget?,
    shape: DigitalTwinShape,
    bodyYaw: Float,
    focusScale: Float,
    offsetX: Float,
    offsetY: Float,
    onTargetSelected: (BodyTarget) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val device = remember(context) { Device3dProfile.from(context) }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val bodyInstance = rememberModelInstance(modelLoader, BODY_MODEL)
    var headRequested by remember(device.loadHead) { mutableStateOf(false) }
    LaunchedEffect(bodyInstance, device.loadHead) {
        headRequested = false
        if (bodyInstance != null && device.loadHead) {
            delay(900)
            headRequested = true
        }
    }
    val headInstance = if (headRequested) rememberModelInstance(modelLoader, HEAD_MODEL) else null

    val redMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(BodyRed, metallic = .02f, roughness = .31f, reflectance = .58f)
    }
    val activeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFFF9A94), metallic = .02f, roughness = .22f, reflectance = .65f)
    }

    val gestureListener = rememberOnGestureListener(
        onSingleTapUp = { _: MotionEvent, node: SceneNode? ->
            node?.name
                ?.takeIf { it.startsWith(HOTSPOT_PREFIX) }
                ?.removePrefix(HOTSPOT_PREFIX)
                ?.let { runCatching { BodyTarget.valueOf(it) }.getOrNull() }
                ?.let(onTargetSelected)
        },
    )

    Box(modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            renderQuality = RenderQuality.Performance,
            autoCenterContent = false,
            autoFitContent = false,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(x = 0f, y = .88f, z = 3.05f),
                targetPosition = Position(x = 0f, y = .88f, z = 0f),
            ),
            onGestureListener = gestureListener,
        ) {
            Node(
                position = Position(x = offsetX, y = offsetY, z = 0f),
                rotation = Rotation(y = bodyYaw),
                scale = Scale(
                    shape.widthScale * focusScale,
                    shape.heightScale * focusScale,
                    shape.depthScale * focusScale,
                ),
            ) {
                bodyInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        apply = { name = "almi_managed_body"; isHittable = false },
                    )
                }
                headInstance?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        scale = Scale(shape.headWidthCompensation, 1f, shape.headDepthCompensation),
                        apply = { name = "almi_managed_head"; isHittable = false },
                    )
                }
                bodyTargets.forEach { marker ->
                    val active = marker.target == selected
                    SphereNode(
                        radius = if (active) .034f else .021f,
                        position = marker.position,
                        materialInstance = if (active) activeMaterial else redMaterial,
                        apply = { name = "$HOTSPOT_PREFIX${marker.target.name}"; isHittable = true },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            shape = RoundedCornerShape(999.dp),
            color = BodySurface.copy(alpha = .85f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
        ) {
            Text(
                when {
                    bodyInstance == null -> "FILAMENT • LOADING"
                    device.loadHead && headRequested && headInstance == null -> "FILAMENT • BODY READY"
                    else -> "FILAMENT • STABLE 360°"
                },
                Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                color = BodyMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
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
                TextButton(onClick = clear) { Text(if (language == "ar") "حذف القياس" else "Clear measurement", color = BodyRed) }
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
    val position: Position,
    val yaw: Float,
    val zoom: Float,
    val focusX: Float,
    val focusY: Float,
) {
    HEIGHT(null, Position(.15f, 1.73f, .08f), 0f, 1.05f, 0f, 0f),
    NECK(BodyMeasurePoint.NECK, Position(0f, 1.53f, .10f), 0f, 1.38f, 0f, -.28f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, Position(-.27f, 1.45f, .08f), 345f, 1.30f, 0f, -.28f),
    CHEST(BodyMeasurePoint.CHEST, Position(0f, 1.30f, .18f), 0f, 1.28f, 0f, -.28f),
    WAIST(BodyMeasurePoint.WAIST, Position(0f, 1.05f, .15f), 0f, 1.30f, 0f, -.03f),
    HIPS(BodyMeasurePoint.HIPS, Position(0f, .88f, .14f), 0f, 1.30f, 0f, -.03f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, Position(-.37f, 1.15f, .06f), 325f, 1.40f, .25f, -.03f),
    WRIST(BodyMeasurePoint.WRIST, Position(-.43f, .83f, .05f), 325f, 1.48f, .25f, .04f),
    HAND(BodyMeasurePoint.HAND, Position(-.46f, .73f, .05f), 325f, 1.58f, .25f, .04f),
    THIGH(BodyMeasurePoint.THIGH, Position(-.13f, .65f, .10f), 0f, 1.42f, .10f, .23f),
    INSEAM(BodyMeasurePoint.INSEAM, Position(0f, .73f, .08f), 0f, 1.34f, 0f, .23f),
    CALF(BodyMeasurePoint.CALF, Position(-.12f, .36f, .07f), 0f, 1.48f, .10f, .42f),
    FOOT(BodyMeasurePoint.FOOT, Position(-.12f, .08f, .18f), 70f, 1.60f, .10f, .55f),
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

private data class TargetPosition(val target: BodyTarget, val position: Position)
private val bodyTargets = BodyTarget.entries.map { TargetPosition(it, it.position) }

private fun arEn(language: String, ar: String, en: String) = if (language == "ar") ar else en
private fun formatBodyNumber(value: Float): String = if (abs(value - value.roundToInt()) < .05f) {
    value.roundToInt().toString()
} else {
    "%.1f".format(Locale.US, value)
}

private const val HOTSPOT_PREFIX = "almi_measure_"
private const val BODY_MODEL = "almi3d/vitruvian_body.glb"
private const val HEAD_MODEL = "almi3d/vitruvian_head.glb"
