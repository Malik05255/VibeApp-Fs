package com.almi.ai.ui.body

import android.app.ActivityManager
import android.content.Context
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.essentialBodyMeasurements
import com.almi.ai.data.preferences.guidedMeasurementOrder
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

/**
 * ALMI v7 measurement-aware digital human.
 *
 * Stability rule: the measurement screen intentionally does not load the heavy rigged hair mesh
 * and does not keep a readable/mirrored swap-chain alive. Both are unnecessary while measuring
 * the body and can substantially increase native/GPU memory on older Android devices. Appearance
 * assets are loaded later by Create Your Avatar.
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
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val deviceProfile = remember(context) { DigitalTwinDeviceProfile.from(context) }

    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { BodyMeasurePoint.valueOf(it) }.getOrNull() }
    var targetYaw by rememberSaveable { mutableStateOf(0f) }
    var showBasics by rememberSaveable { mutableStateOf(false) }

    val solvedShape = remember(profile) { BodyShapeSolver.solve(profile) }
    val shapeWidth by animateFloatAsState(solvedShape.widthScale, tween(420), label = "twin-width")
    val shapeHeight by animateFloatAsState(solvedShape.heightScale, tween(420), label = "twin-height")
    val shapeDepth by animateFloatAsState(solvedShape.depthScale, tween(420), label = "twin-depth")
    val renderedShape = solvedShape.copy(
        widthScale = shapeWidth,
        heightScale = shapeHeight,
        depthScale = shapeDepth,
        headWidthCompensation = if (shapeWidth == 0f) 1f else 1f / shapeWidth,
        headDepthCompensation = if (shapeDepth == 0f) 1f else 1f / shapeDepth,
    )

    val yaw by animateFloatAsState(targetYaw, tween(360), label = "twin-yaw")
    val focusScale by animateFloatAsState(if (selected == null) 1f else 1.18f, tween(300), label = "twin-focus")
    val focusY by animateFloatAsState(selected?.let(::focusOffsetY) ?: 0f, tween(300), label = "twin-focus-y")

    fun select(point: BodyMeasurePoint) {
        selectedName = point.name
        targetYaw = nearestYaw(yaw, preferredYaw(point))
    }

    Box(modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Header(language, profile, solvedShape, deviceProfile) { showBasics = !showBasics }
            LinearProgressIndicator(
                progress = { profile.essentialCompletionFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )

            AnimatedVisibility(showBasics && selected == null) {
                BasicsEditor(language, profile, onHeightChanged, onWeightChanged)
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                SafeHumanViewport(
                    selected = selected,
                    completed = profile.measurementsInches.keys,
                    shape = renderedShape,
                    bodyYaw = yaw,
                    focusScale = focusScale,
                    bodyOffsetY = focusY,
                    deviceProfile = deviceProfile,
                    onPointSelected = ::select,
                    modifier = Modifier.fillMaxSize(),
                )

                if (selected == null) {
                    HintPill(
                        text = tr(language, "اسحب للدوران • قرّب بإصبعين • اضغط نقطة للقياس", "Drag to orbit • pinch to zoom • tap a point to measure"),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )

                    profile.nextRecommendedMeasurement?.let { next ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp).clickable { select(next) },
                            shape = RoundedCornerShape(999.dp),
                            color = scheme.surface.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(8.dp).background(scheme.error, CircleShape))
                                Text(
                                    tr(language, "القياس التالي: ${title(next, language)}", "Next: ${title(next, language)}"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    AngleBar(
                        language = language,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 12.dp),
                        onFront = { targetYaw = nearestYaw(yaw, 0f) },
                        onSide = { targetYaw = nearestYaw(yaw, 90f) },
                        onBack = { targetYaw = nearestYaw(yaw, 180f) },
                    )
                }
            }

            if (selected == null) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text(
                            if (profile.isFitReady) {
                                tr(language, "حفظ التوأم والانتقال للأفاتار", "Save twin & create avatar")
                            } else {
                                tr(language, "حفظ وإكمال القياسات لاحقًا", "Save and finish measurements later")
                            },
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        tr(
                            language,
                            "الطول والوزن والقياسات تغيّر جسم التوأم مباشرة. تم تفعيل وضع 3D مستقر لتقليل ضغط الذاكرة على الجهاز.",
                            "Height, weight and measurements reshape the twin live. Stable 3D mode reduces native/GPU memory pressure.",
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        selected?.let { point ->
            MeasurementSheet(
                language = language,
                point = point,
                existingInches = profile.measurementsInches[point],
                essential = point in essentialBodyMeasurements,
                onSave = { inches ->
                    onMeasurementChanged(point, inches)
                    val next = guidedMeasurementOrder.firstOrNull {
                        candidate -> candidate != point && candidate !in profile.measurementsInches
                    }
                    if (next == null) selectedName = null else select(next)
                },
                onClear = if (point in profile.measurementsInches) ({ onMeasurementCleared(point) }) else null,
                onDismiss = { selectedName = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private data class DigitalTwinDeviceProfile(
    val lowRam: Boolean,
    val memoryClassMb: Int,
    val allowDetailedHead: Boolean,
) {
    companion object {
        fun from(context: Context): DigitalTwinDeviceProfile {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val lowRam = manager.isLowRamDevice
            val memory = manager.memoryClass
            return DigitalTwinDeviceProfile(
                lowRam = lowRam,
                memoryClassMb = memory,
                allowDetailedHead = !lowRam && memory >= 192,
            )
        }
    }
}

@Composable
private fun SafeHumanViewport(
    selected: BodyMeasurePoint?,
    completed: Set<BodyMeasurePoint>,
    shape: DigitalTwinShape,
    bodyYaw: Float,
    focusScale: Float,
    bodyOffsetY: Float,
    deviceProfile: DigitalTwinDeviceProfile,
    onPointSelected: (BodyMeasurePoint) -> Unit,
    modifier: Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)

    val activeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFE33A2E), metallic = 0.02f, roughness = 0.42f, reflectance = 0.42f)
    }
    val pendingMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFF1EEE8), metallic = 0.02f, roughness = 0.46f, reflectance = 0.45f)
    }
    val completedMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF436B5C), metallic = 0.02f, roughness = 0.44f, reflectance = 0.42f)
    }

    val gestureListener = rememberOnGestureListener(
        onSingleTapUp = { _: MotionEvent, node: SceneNode? ->
            val point = node?.name
                ?.takeIf { it.startsWith(HOTSPOT_PREFIX) }
                ?.removePrefix(HOTSPOT_PREFIX)
                ?.let { runCatching { BodyMeasurePoint.valueOf(it) }.getOrNull() }
            if (point != null) onPointSelected(point)
        },
    )

    var loadFailure by remember { mutableStateOf(false) }

    Box(modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            renderQuality = RenderQuality.Performance,
            autoCenterContent = true,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(x = 0f, y = 0.05f, z = 3.0f),
                targetPosition = Position(x = 0f, y = 0.05f, z = 0f),
            ),
            onGestureListener = gestureListener,
        ) {
            var body by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }
            var head by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }

            LaunchedEffect(modelLoader, deviceProfile.allowDetailedHead) {
                loadFailure = false
                body = runCatching { modelLoader.loadModelInstance(VITRUVIAN_BODY_ASSET) }
                    .onFailure { loadFailure = true }
                    .getOrNull()

                // Give Filament at least a few frames to upload the body before allocating the head.
                if (body != null && deviceProfile.allowDetailedHead) {
                    delay(650)
                    head = runCatching { modelLoader.loadModelInstance(VITRUVIAN_HEAD_ASSET) }
                        .onFailure { loadFailure = true }
                        .getOrNull()
                }
            }

            Node(
                position = Position(x = 0f, y = bodyOffsetY, z = 0f),
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
                        apply = { name = "almi_v7_body"; isHittable = false },
                    )
                }
                head?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        scale = Scale(shape.headWidthCompensation, 1f, shape.headDepthCompensation),
                        apply = { name = "almi_v7_head"; isHittable = false },
                    )
                }

                landmarks.forEach { marker ->
                    val chosen = selected == marker.point
                    SphereNode(
                        radius = if (chosen) 0.034f else 0.020f,
                        position = marker.position,
                        materialInstance = when {
                            chosen -> activeMaterial
                            marker.point in completed -> completedMaterial
                            else -> pendingMaterial
                        },
                        apply = { name = "$HOTSPOT_PREFIX${marker.point.name}"; isHittable = true },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier.size(6.dp).background(
                        if (loadFailure) MaterialTheme.colorScheme.error
                        else if (shape.isPersonalized) Color(0xFF436B5C)
                        else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                )
                Text(
                    when {
                        loadFailure -> "3D SAFE MODE"
                        shape.isPersonalized -> "DIGITAL TWIN • ${(shape.confidence * 100).roundToInt()}%"
                        else -> "DIGITAL TWIN • SAFE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Header(
    language: String,
    profile: BodyProfile,
    shape: DigitalTwinShape,
    deviceProfile: DigitalTwinDeviceProfile,
    onBasics: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("ALMI / HUMAN DIGITAL TWIN", style = MaterialTheme.typography.labelSmall, color = scheme.error)
            Text(tr(language, "توأمك الرقمي", "Your digital twin"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                if (shape.isPersonalized) {
                    tr(language, "${shape.enteredShapeFacts}/6 بيانات تغيّر الجسم الآن", "${shape.enteredShapeFacts}/6 shape facts are active")
                } else if (deviceProfile.lowRam) {
                    tr(language, "وضع 3D خفيف متوافق مع جهازك", "Memory-safe 3D mode for this device")
                } else {
                    tr(language, "ابدأ بإدخال الطول والوزن والقياسات", "Start with height, weight and measurements")
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (profile.isFitReady) scheme.primaryContainer else scheme.surface,
                border = BorderStroke(1.dp, if (profile.isFitReady) scheme.primary else scheme.outlineVariant),
            ) {
                Text(
                    if (profile.isFitReady) tr(language, "جاهز", "FIT READY") else "${profile.essentialCompletedMeasurements}/${essentialBodyMeasurements.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
            TextButton(onClick = onBasics) { Text(tr(language, "الطول/الوزن", "Height/weight")) }
        }
    }
}

@Composable
private fun BasicsEditor(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
) {
    var metric by rememberSaveable { mutableStateOf(language == "ar") }
    var height by remember(profile.heightInches, metric) {
        mutableStateOf(formatNumber(if (metric) profile.heightInches * 2.54f else profile.heightInches))
    }
    var weight by remember(profile.weightPounds, metric) {
        mutableStateOf(formatNumber(if (metric) profile.weightPounds * 0.45359237f else profile.weightPounds))
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (metric) {
                    Button(onClick = {}, modifier = Modifier.weight(1f).height(40.dp)) { Text("cm / kg") }
                    OutlinedButton(onClick = { metric = false }, modifier = Modifier.weight(1f).height(40.dp)) { Text("in / lb") }
                } else {
                    OutlinedButton(onClick = { metric = true }, modifier = Modifier.weight(1f).height(40.dp)) { Text("cm / kg") }
                    Button(onClick = {}, modifier = Modifier.weight(1f).height(40.dp)) { Text("in / lb") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = height,
                    onValueChange = { raw ->
                        height = numeric(raw, 6)
                        height.toFloatOrNull()?.let { value -> onHeightChanged(if (metric) value / 2.54f else value) }
                    },
                    label = { Text(if (metric) tr(language, "الطول (cm)", "Height (cm)") else tr(language, "الطول (in)", "Height (in)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { raw ->
                        weight = numeric(raw, 6)
                        weight.toFloatOrNull()?.let { value -> onWeightChanged(if (metric) value / 0.45359237f else value) }
                    },
                    label = { Text(if (metric) tr(language, "الوزن (kg)", "Weight (kg)") else tr(language, "الوزن (lb)", "Weight (lb)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                tr(language, "مثال: اكتب 80 kg وسيتغير حجم التوأم الرقمي مباشرة.", "Example: enter 80 kg and the digital-twin volume updates immediately."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HintPill(text: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AngleBar(
    language: String,
    modifier: Modifier,
    onFront: () -> Unit,
    onSide: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onFront, modifier = Modifier.weight(1f)) { Text(tr(language, "أمامي", "Front")) }
            OutlinedButton(onClick = onSide, modifier = Modifier.weight(1f)) { Text(tr(language, "جانبي", "Side")) }
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text(tr(language, "خلفي", "Back")) }
        }
    }
}

@Composable
private fun MeasurementSheet(
    language: String,
    point: BodyMeasurePoint,
    existingInches: Float?,
    essential: Boolean,
    onSave: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var useCm by rememberSaveable(point.name) { mutableStateOf(language == "ar") }
    var value by remember(point, existingInches, useCm) {
        mutableStateOf(existingInches?.let { formatNumber(if (useCm) it * 2.54f else it) }.orEmpty())
    }
    val parsed = value.toFloatOrNull()

    Surface(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface.copy(alpha = 0.99f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title(point, language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        if (essential) tr(language, "قياس أساسي لدقة الملابس", "Essential fit measurement")
                        else tr(language, "تفصيل إضافي لزيادة الدقة", "Optional precision detail"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (essential) scheme.error else scheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text(tr(language, "لاحقًا", "Later")) }
            }

            Text(instruction(point, language), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                UnitChip("in", !useCm, Modifier.weight(1f)) {
                    if (useCm) {
                        value = value.toFloatOrNull()?.let { formatNumber(it / 2.54f) }.orEmpty()
                        useCm = false
                    }
                }
                UnitChip("cm", useCm, Modifier.weight(1f)) {
                    if (!useCm) {
                        value = value.toFloatOrNull()?.let { formatNumber(it * 2.54f) }.orEmpty()
                        useCm = true
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = { value = numeric(it, 7) },
                label = { Text(tr(language, "أدخل القياس", "Enter measurement")) },
                suffix = { Text(if (useCm) "cm" else "in") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onClear?.let { clear ->
                    OutlinedButton(onClick = clear, modifier = Modifier.weight(0.42f).height(50.dp)) {
                        Text(tr(language, "حذف", "Clear"))
                    }
                }
                Button(
                    enabled = parsed?.let { it > 0f } == true,
                    onClick = { parsed?.let { onSave(if (useCm) it / 2.54f else it) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Text(tr(language, "حفظ والانتقال للتالي", "Save & next"), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun UnitChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Text(label, modifier = Modifier.padding(vertical = 9.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Black)
    }
}

private data class Landmark(val point: BodyMeasurePoint, val position: Position)

private val landmarks = listOf(
    Landmark(BodyMeasurePoint.NECK, Position(0f, 1.53f, 0.10f)),
    Landmark(BodyMeasurePoint.SHOULDERS, Position(-0.27f, 1.45f, 0.08f)),
    Landmark(BodyMeasurePoint.CHEST, Position(0f, 1.30f, 0.18f)),
    Landmark(BodyMeasurePoint.WAIST, Position(0f, 1.05f, 0.15f)),
    Landmark(BodyMeasurePoint.HIPS, Position(0f, 0.88f, 0.14f)),
    Landmark(BodyMeasurePoint.ARM_LENGTH, Position(-0.37f, 1.15f, 0.06f)),
    Landmark(BodyMeasurePoint.WRIST, Position(-0.43f, 0.83f, 0.05f)),
    Landmark(BodyMeasurePoint.HAND, Position(-0.46f, 0.73f, 0.05f)),
    Landmark(BodyMeasurePoint.THIGH, Position(-0.13f, 0.65f, 0.10f)),
    Landmark(BodyMeasurePoint.INSEAM, Position(0f, 0.73f, 0.08f)),
    Landmark(BodyMeasurePoint.CALF, Position(-0.12f, 0.36f, 0.07f)),
    Landmark(BodyMeasurePoint.FOOT, Position(-0.12f, 0.08f, 0.18f)),
)

private fun preferredYaw(point: BodyMeasurePoint): Float = when (point) {
    BodyMeasurePoint.ARM_LENGTH, BodyMeasurePoint.WRIST, BodyMeasurePoint.HAND -> 325f
    BodyMeasurePoint.FOOT -> 70f
    BodyMeasurePoint.SHOULDERS -> 345f
    else -> 0f
}

private fun focusOffsetY(point: BodyMeasurePoint): Float = when (point) {
    BodyMeasurePoint.NECK, BodyMeasurePoint.SHOULDERS, BodyMeasurePoint.CHEST -> -0.26f
    BodyMeasurePoint.WAIST, BodyMeasurePoint.HIPS, BodyMeasurePoint.ARM_LENGTH,
    BodyMeasurePoint.WRIST, BodyMeasurePoint.HAND -> 0f
    BodyMeasurePoint.THIGH, BodyMeasurePoint.INSEAM -> 0.20f
    BodyMeasurePoint.CALF, BodyMeasurePoint.FOOT -> 0.44f
}

private fun nearestYaw(current: Float, preferred: Float): Float {
    val normalized = ((current % 360f) + 360f) % 360f
    var delta = preferred - normalized
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return current + delta
}

private fun title(point: BodyMeasurePoint, language: String): String = when (point) {
    BodyMeasurePoint.NECK -> tr(language, "محيط الرقبة", "Neck circumference")
    BodyMeasurePoint.SHOULDERS -> tr(language, "عرض الكتفين", "Shoulder width")
    BodyMeasurePoint.CHEST -> tr(language, "محيط الصدر", "Chest circumference")
    BodyMeasurePoint.WAIST -> tr(language, "محيط الخصر", "Waist circumference")
    BodyMeasurePoint.HIPS -> tr(language, "محيط الورك", "Hip circumference")
    BodyMeasurePoint.ARM_LENGTH -> tr(language, "طول الذراع", "Arm length")
    BodyMeasurePoint.WRIST -> tr(language, "محيط المعصم", "Wrist circumference")
    BodyMeasurePoint.HAND -> tr(language, "محيط اليد", "Hand circumference")
    BodyMeasurePoint.THIGH -> tr(language, "محيط الفخذ", "Thigh circumference")
    BodyMeasurePoint.INSEAM -> tr(language, "طول الساق الداخلي", "Inseam")
    BodyMeasurePoint.CALF -> tr(language, "محيط الساق", "Calf circumference")
    BodyMeasurePoint.FOOT -> tr(language, "طول القدم", "Foot length")
}

private fun instruction(point: BodyMeasurePoint, language: String): String = when (point) {
    BodyMeasurePoint.NECK -> tr(language, "لف شريط القياس حول قاعدة الرقبة أعلى عظمة الترقوة بقليل، بدون شد.", "Wrap the tape around the base of the neck just above the collarbone without tightening.")
    BodyMeasurePoint.SHOULDERS -> tr(language, "قس بخط مستقيم من نهاية كتف إلى نهاية الكتف الآخر عبر أعلى الظهر.", "Measure straight from one shoulder tip to the other across the upper back.")
    BodyMeasurePoint.CHEST -> tr(language, "لف الشريط حول أعرض نقطة من الصدر واجعله أفقيًا وموازيًا للأرض.", "Wrap the tape around the fullest chest point and keep it level with the floor.")
    BodyMeasurePoint.WAIST -> tr(language, "قس حول الخصر الطبيعي عند أضيق نقطة بدون شفط البطن أو ضغط الشريط.", "Measure the natural waist at its narrowest point without sucking in or compressing the tape.")
    BodyMeasurePoint.HIPS -> tr(language, "لف الشريط حول أعرض نقطة من الورك والمؤخرة مع إبقائه أفقيًا.", "Wrap the tape around the fullest part of the hips and seat while keeping it level.")
    BodyMeasurePoint.ARM_LENGTH -> tr(language, "ابدأ من نقطة الكتف، مر فوق كوع مثني قليلًا، وانته عند عظمة المعصم.", "Start at the shoulder point, pass over a slightly bent elbow, and end at the wrist bone.")
    BodyMeasurePoint.WRIST -> tr(language, "لف الشريط حول المعصم عند العظمة البارزة بدون ضغط.", "Wrap the tape around the wrist bone without compression.")
    BodyMeasurePoint.HAND -> tr(language, "لف الشريط حول أعرض جزء من راحة اليد عند مفاصل الأصابع مع استثناء الإبهام.", "Wrap the tape around the widest hand area at the knuckles, excluding the thumb.")
    BodyMeasurePoint.THIGH -> tr(language, "قس محيط أعرض جزء من أعلى الفخذ وأنت واقف طبيعيًا.", "Measure around the fullest upper thigh while standing naturally.")
    BodyMeasurePoint.INSEAM -> tr(language, "قس من أعلى نقطة داخل الساق عند المنشعب إلى الأرض أو إلى طول البنطال المطلوب.", "Measure from the top of the inner leg at the crotch to the floor or desired trouser length.")
    BodyMeasurePoint.CALF -> tr(language, "لف الشريط حول أعرض نقطة من عضلة الساق بدون ضغط.", "Wrap the tape around the fullest calf point without compression.")
    BodyMeasurePoint.FOOT -> tr(language, "قف على ورقة وقس من مؤخرة الكعب إلى نهاية أطول إصبع.", "Stand on paper and measure from the back of the heel to the longest toe.")
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
private fun numeric(value: String, maxLength: Int): String = value.filter { it.isDigit() || it == '.' }.take(maxLength)
private fun formatNumber(value: Float): String = if (abs(value - value.roundToInt()) < 0.05f) {
    value.roundToInt().toString()
} else {
    "%.1f".format(Locale.US, value)
}

private const val HOTSPOT_PREFIX = "almi_measure_"
private const val VITRUVIAN_BODY_ASSET = "almi3d/vitruvian_body.glb"
private const val VITRUVIAN_HEAD_ASSET = "almi3d/vitruvian_head.glb"
