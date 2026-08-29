package com.almi.ai.ui.body

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.github.sceneview.node.Node as SceneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberOnGestureListener
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * ALMI v7 body experience.
 *
 * This replaces the old Canvas/procedural mannequin with a real glTF scene rendered by
 * Google Filament through SceneView. The digital human is composed from CC0 Vitruvian assets.
 * Measurement hotspots are real 3D nodes, so they rotate, zoom and occlude with the body.
 */
@Composable
fun RealHuman3DBodyScreen(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { BodyMeasurePoint.valueOf(it) }.getOrNull() }
    var requestedYaw by rememberSaveable { mutableStateOf(0f) }
    var showBasics by rememberSaveable { mutableStateOf(false) }

    val animatedYaw by animateFloatAsState(
        targetValue = requestedYaw,
        animationSpec = tween(420),
        label = "v7-body-yaw",
    )
    val sceneScale by animateFloatAsState(
        targetValue = if (selected == null) 1f else 1.23f,
        animationSpec = tween(360),
        label = "v7-body-focus-scale",
    )
    val verticalFocus by animateFloatAsState(
        targetValue = selected?.let(::focusOffsetY) ?: 0f,
        animationSpec = tween(360),
        label = "v7-body-focus-y",
    )

    fun select(point: BodyMeasurePoint) {
        selectedName = point.name
        requestedYaw = nearestYaw(animatedYaw, preferredYaw(point))
    }

    Box(modifier = modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxSize()) {
            BodyHeader(
                language = language,
                profile = profile,
                onBasics = { showBasics = !showBasics },
            )

            LinearProgressIndicator(
                progress = { profile.essentialCompletionFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )

            AnimatedVisibility(visible = showBasics && selected == null) {
                BodyBasics(
                    language = language,
                    profile = profile,
                    onHeightChanged = onHeightChanged,
                    onWeightChanged = onWeightChanged,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                RealHumanViewport(
                    selected = selected,
                    completed = profile.measurementsInches.keys,
                    bodyYaw = animatedYaw,
                    bodyScale = sceneScale,
                    bodyOffsetY = verticalFocus,
                    onPointSelected = ::select,
                    modifier = Modifier.fillMaxSize(),
                )

                if (selected == null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = scheme.surface.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                    ) {
                        Text(
                            tr(
                                language,
                                "اسحب للدوران • قرّب بإصبعين • اضغط نقطة للقياس",
                                "Drag to orbit • pinch to zoom • tap a point to measure",
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }

                if (selected == null && profile.nextRecommendedMeasurement != null) {
                    val next = profile.nextRecommendedMeasurement!!
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 78.dp)
                            .clickable { select(next) },
                        shape = RoundedCornerShape(999.dp),
                        color = scheme.surface.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.35f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.size(8.dp).background(scheme.error, CircleShape))
                            Text(
                                tr(language, "القياس التالي: ${pointTitle(next, language)}", "Next: ${pointTitle(next, language)}"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                if (selected == null) {
                    AngleBar(
                        language = language,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 14.dp),
                        onFront = { requestedYaw = nearestYaw(animatedYaw, 0f) },
                        onSide = { requestedYaw = nearestYaw(animatedYaw, 90f) },
                        onBack = { requestedYaw = nearestYaw(animatedYaw, 180f) },
                    )
                }
            }

            if (selected == null) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text(
                            if (profile.isFitReady) {
                                tr(language, "متابعة إلى تجربة الملابس", "Continue to try-on")
                            } else {
                                tr(language, "المتابعة وإكمال القياسات لاحقًا", "Continue and finish measurements later")
                            },
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        tr(
                            language,
                            "المجسم ثلاثي الأبعاد للقياس والتخصيص. لا يتم تخمين أي قياس لم تدخله بنفسك.",
                            "The 3D body is used for measurement and fitting context. Missing measurements are never invented.",
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
                isEssential = point in essentialBodyMeasurements,
                onSave = { inches ->
                    onMeasurementChanged(point, inches)
                    val next = guidedMeasurementOrder.firstOrNull { candidate ->
                        candidate != point && candidate !in profile.measurementsInches
                    }
                    if (next == null) {
                        selectedName = null
                    } else {
                        select(next)
                    }
                },
                onClear = if (point in profile.measurementsInches) {
                    { onMeasurementCleared(point) }
                } else null,
                onDismiss = { selectedName = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun RealHumanViewport(
    selected: BodyMeasurePoint?,
    completed: Set<BodyMeasurePoint>,
    bodyYaw: Float,
    bodyScale: Float,
    bodyOffsetY: Float,
    onPointSelected: (BodyMeasurePoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val activeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFE33A2E), metallic = 0.05f, roughness = 0.28f, reflectance = 0.55f)
    }
    val pendingMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFFF3F0EA), metallic = 0.15f, roughness = 0.30f, reflectance = 0.72f)
    }
    val completedMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF436B5C), metallic = 0.10f, roughness = 0.32f, reflectance = 0.58f)
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

    Box(modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            renderQuality = RenderQuality.Cinematic,
            autoCenterContent = true,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(x = 0f, y = 0.05f, z = 3.0f),
                targetPosition = Position(x = 0f, y = 0.05f, z = 0f),
            ),
            onGestureListener = gestureListener,
        ) {
            val body = rememberModelInstance(modelLoader, VITRUVIAN_BODY_URL)
            val head = rememberModelInstance(modelLoader, VITRUVIAN_HEAD_URL)
            val hair = rememberModelInstance(modelLoader, VITRUVIAN_HAIR_URL)

            Node(
                position = Position(x = 0f, y = bodyOffsetY, z = 0f),
                rotation = Rotation(y = bodyYaw),
                scale = Scale(bodyScale),
            ) {
                body?.let {
                    ModelNode(
                        modelInstance = it,
                        autoAnimate = false,
                        apply = {
                            name = "almi_v7_body"
                            isHittable = false
                        },
                    )
                }
                head?.let {
                    ModelNode(
                        modelInstance = it,
                        autoAnimate = false,
                        apply = {
                            name = "almi_v7_head"
                            isHittable = false
                        },
                    )
                }
                hair?.let {
                    ModelNode(
                        modelInstance = it,
                        autoAnimate = false,
                        apply = {
                            name = "almi_v7_hair"
                            isHittable = false
                        },
                    )
                }

                bodyHotspots.forEach { hotspot ->
                    val isSelected = selected == hotspot.point
                    val material = when {
                        isSelected -> activeMaterial
                        hotspot.point in completed -> completedMaterial
                        else -> pendingMaterial
                    }
                    SphereNode(
                        radius = if (isSelected) 0.036f else 0.021f,
                        position = hotspot.position,
                        materialInstance = material,
                        apply = {
                            name = "$HOTSPOT_PREFIX${hotspot.point.name}"
                            isHittable = true
                        },
                    )
                }
            }
        }

        // The model downloads asynchronously. This overlay deliberately stays subtle; the
        // renderer replaces it as soon as its first real frame appears.
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(6.dp).background(Color(0xFF436B5C), CircleShape))
                Text("FILAMENT / REAL 3D", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BodyHeader(
    language: String,
    profile: BodyProfile,
    onBasics: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("ALMI / HUMAN DIGITAL TWIN", style = MaterialTheme.typography.labelSmall, color = scheme.error)
            Text(
                tr(language, "جسمك ثلاثي الأبعاد", "Your 3D body"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (profile.isFitReady) scheme.primaryContainer else scheme.surface,
                border = BorderStroke(1.dp, if (profile.isFitReady) scheme.primary else scheme.outlineVariant),
            ) {
                Text(
                    if (profile.isFitReady) tr(language, "جاهز", "FIT READY")
                    else "${profile.essentialCompletedMeasurements}/${essentialBodyMeasurements.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
            TextButton(onClick = onBasics) {
                Text(tr(language, "الطول/الوزن", "Height/weight"))
            }
        }
    }
}

@Composable
private fun BodyBasics(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
) {
    var height by remember(profile.heightInches) { mutableStateOf(formatNumber(profile.heightInches)) }
    var weight by remember(profile.weightPounds) { mutableStateOf(formatNumber(profile.weightPounds)) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = height,
                onValueChange = {
                    height = numeric(it, 6)
                    height.toFloatOrNull()?.let(onHeightChanged)
                },
                label = { Text(tr(language, "الطول (in)", "Height (in)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = weight,
                onValueChange = {
                    weight = numeric(it, 6)
                    weight.toFloatOrNull()?.let(onWeightChanged)
                },
                label = { Text(tr(language, "الوزن (lb)", "Weight (lb)")) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
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
    isEssential: Boolean,
    onSave: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var useCm by rememberSaveable(point.name) { mutableStateOf(false) }
    var value by remember(point, existingInches, useCm) {
        mutableStateOf(existingInches?.let { formatNumber(if (useCm) it * 2.54f else it) }.orEmpty())
    }
    val parsed = value.toFloatOrNull()

    Surface(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pointTitle(point, language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        if (isEssential) tr(language, "قياس أساسي لدقة الملابس", "Essential fit measurement")
                        else tr(language, "تفصيل إضافي لزيادة الدقة", "Optional precision detail"),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isEssential) scheme.error else scheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text(tr(language, "لاحقًا", "Later")) }
            }

            Text(
                pointInstruction(point, language),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                UnitChip("in", selected = !useCm, modifier = Modifier.weight(1f)) {
                    if (useCm) {
                        value = value.toFloatOrNull()?.let { formatNumber(it / 2.54f) }.orEmpty()
                        useCm = false
                    }
                }
                UnitChip("cm", selected = useCm, modifier = Modifier.weight(1f)) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                onClear?.let { clear ->
                    OutlinedButton(onClick = clear, modifier = Modifier.weight(0.42f).height(50.dp)) {
                        Text(tr(language, "حذف", "Clear"))
                    }
                }
                Button(
                    enabled = parsed?.let { it > 0f } == true,
                    onClick = {
                        parsed?.let { entered -> onSave(if (useCm) entered / 2.54f else entered) }
                    },
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
        Text(
            label,
            modifier = Modifier.padding(vertical = 9.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black,
        )
    }
}

private data class BodyHotspot(
    val point: BodyMeasurePoint,
    val position: Position,
)

/**
 * Approximate landmarks in a 1.8 m standing human coordinate system (+Y up, +Z front).
 * These are intentionally kept in one catalog so a future parametric body solver can update them
 * from skeleton joints rather than changing the interaction/UI layer.
 */
private val bodyHotspots = listOf(
    BodyHotspot(BodyMeasurePoint.NECK, Position(0f, 1.53f, 0.10f)),
    BodyHotspot(BodyMeasurePoint.SHOULDERS, Position(-0.27f, 1.45f, 0.08f)),
    BodyHotspot(BodyMeasurePoint.CHEST, Position(0f, 1.30f, 0.18f)),
    BodyHotspot(BodyMeasurePoint.WAIST, Position(0f, 1.05f, 0.15f)),
    BodyHotspot(BodyMeasurePoint.HIPS, Position(0f, 0.88f, 0.14f)),
    BodyHotspot(BodyMeasurePoint.ARM_LENGTH, Position(-0.37f, 1.15f, 0.06f)),
    BodyHotspot(BodyMeasurePoint.WRIST, Position(-0.43f, 0.83f, 0.05f)),
    BodyHotspot(BodyMeasurePoint.HAND, Position(-0.46f, 0.73f, 0.05f)),
    BodyHotspot(BodyMeasurePoint.THIGH, Position(-0.13f, 0.65f, 0.10f)),
    BodyHotspot(BodyMeasurePoint.INSEAM, Position(0f, 0.73f, 0.08f)),
    BodyHotspot(BodyMeasurePoint.CALF, Position(-0.12f, 0.36f, 0.07f)),
    BodyHotspot(BodyMeasurePoint.FOOT, Position(-0.12f, 0.08f, 0.18f)),
)

private fun preferredYaw(point: BodyMeasurePoint): Float = when (point) {
    BodyMeasurePoint.ARM_LENGTH, BodyMeasurePoint.WRIST, BodyMeasurePoint.HAND -> 325f
    BodyMeasurePoint.FOOT -> 70f
    BodyMeasurePoint.SHOULDERS -> 345f
    else -> 0f
}

private fun focusOffsetY(point: BodyMeasurePoint): Float = when (point) {
    BodyMeasurePoint.NECK, BodyMeasurePoint.SHOULDERS, BodyMeasurePoint.CHEST -> -0.26f
    BodyMeasurePoint.WAIST, BodyMeasurePoint.HIPS, BodyMeasurePoint.ARM_LENGTH, BodyMeasurePoint.WRIST, BodyMeasurePoint.HAND -> 0f
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

private fun pointTitle(point: BodyMeasurePoint, language: String): String = when (point) {
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

private fun pointInstruction(point: BodyMeasurePoint, language: String): String = when (point) {
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

private fun formatNumber(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else "%.1f".format(Locale.US, value)

private const val HOTSPOT_PREFIX = "almi_measure_"

// Fully CC0 / EULA-free Vitruvian digital human. SceneView resolves external textures relative
// to these HTTPS model URLs. Assets are kept swappable so ALMI can later bundle a higher-resolution
// or user-generated parametric digital twin without rewriting the measurement UX.
private const val VITRUVIAN_BODY_URL =
    "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/vitruvian_body.glb"
private const val VITRUVIAN_HEAD_URL =
    "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/vitruvian_head.glb"
private const val VITRUVIAN_HAIR_URL =
    "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/vitruvian_hair_rigged.glb"
