package com.almi.ai.ui.body

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.google.android.filament.Colors
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.Node as SceneNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberOnGestureListener
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val LabBackground = Color(0xFF07111F)
private val LabSurface = Color(0xFF0D1A2B)
private val LabSurfaceRaised = Color(0xFF12233A)
private val LabText = Color(0xFFF4F8FF)
private val LabMuted = Color(0xFF8FA5C2)
private val LabBlue = Color(0xFF80B8FF)
private val LabRed = Color(0xFFFF4D43)
private val LabGreen = Color(0xFF58D6A7)

/**
 * ALMI Body Map.
 *
 * Crash-prevention architecture:
 * - one GLB only (body); no head/hair/avatar meshes in the measurement route
 * - SceneView's rememberModelInstance owns model lifecycle and disposal
 * - Performance renderer; no mirrored/readable swap-chain and no off-screen capture
 * - procedural markers/measurement guides are tiny unlit meshes
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
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { name -> runCatching { MeasureTarget.valueOf(name) }.getOrNull() }
    var targetYaw by rememberSaveable { mutableStateOf(0f) }
    var guideReady by remember(selectedName) { mutableStateOf(false) }

    LaunchedEffect(selectedName) {
        guideReady = false
        if (selectedName != null) {
            delay(260)
            guideReady = true
        }
    }

    val shape = remember(profile) { BodyShapeSolver.solve(profile) }
    val width by animateFloatAsState(shape.widthScale, tween(420), label = "body-map-width")
    val height by animateFloatAsState(shape.heightScale, tween(420), label = "body-map-height")
    val depth by animateFloatAsState(shape.depthScale, tween(420), label = "body-map-depth")
    val yaw by animateFloatAsState(targetYaw, tween(480), label = "body-map-yaw")
    val focusScale by animateFloatAsState(selected?.focusScale ?: 1f, tween(520), label = "body-map-focus")
    val focusY by animateFloatAsState(selected?.focusY ?: 0f, tween(520), label = "body-map-focus-y")
    val guideProgress by animateFloatAsState(if (guideReady) 1f else 0f, tween(720), label = "body-map-guide")

    fun open(target: MeasureTarget) {
        selectedName = target.name
        targetYaw = nearestYaw(yaw, target.focusYaw)
    }

    val totalFacts = MeasureTarget.entries.size + 1 // + persistent weight
    val completedFacts = MeasureTarget.entries.count { it.valueCm(profile) != null } + if (profile.hasExplicitWeight) 1 else 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LabBackground),
    ) {
        Column(Modifier.fillMaxSize()) {
            LabHeader(
                language = language,
                completed = completedFacts,
                total = totalFacts,
                onDone = onComplete,
            )
            LinearProgressIndicator(
                progress = { completedFacts.toFloat() / totalFacts.toFloat() },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = LabBlue,
                trackColor = Color.White.copy(alpha = 0.08f),
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                BodyMapViewport(
                    profile = profile,
                    selected = selected,
                    shape = shape.copy(widthScale = width, heightScale = height, depthScale = depth),
                    yaw = yaw,
                    focusScale = focusScale,
                    focusY = focusY,
                    guideProgress = guideProgress,
                    onSelected = ::open,
                    modifier = Modifier.fillMaxSize(),
                )

                if (selected == null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = LabSurface.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Text(
                            tr(language, "اسحب 360° • اضغط النقطة الحمراء", "Drag 360° • tap a red point"),
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            color = LabMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selected != null,
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    selected?.let { target ->
                        MeasurementInputCard(
                            language = language,
                            target = target,
                            existingCm = target.valueCm(profile),
                            onConfirm = { centimeters ->
                                if (target == MeasureTarget.HEIGHT) {
                                    onHeightChanged(centimeters / 2.54f)
                                } else {
                                    target.point?.let { point -> onMeasurementChanged(point, centimeters / 2.54f) }
                                }
                                selectedName = null
                                targetYaw = nearestYaw(yaw, 0f)
                            },
                            onClear = target.point
                                ?.takeIf { it in profile.measurementsInches }
                                ?.let { point -> ({ onMeasurementCleared(point) }) },
                            onClose = {
                                selectedName = null
                                targetYaw = nearestYaw(yaw, 0f)
                            },
                        )
                    }
                }
            }

            WeightDock(
                language = language,
                profile = profile,
                onWeightChanged = onWeightChanged,
            )
        }
    }
}

@Composable
private fun LabHeader(
    language: String,
    completed: Int,
    total: Int,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "ALMI / BODY MAP",
                color = LabBlue,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                tr(language, "قياسات جسمك", "Your measurements"),
                color = LabText,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$completed/$total", color = LabMuted, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onDone) {
                Text(tr(language, "تم", "Done"), color = LabText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BodyMapViewport(
    profile: BodyProfile,
    selected: MeasureTarget?,
    shape: DigitalTwinShape,
    yaw: Float,
    focusScale: Float,
    focusY: Float,
    guideProgress: Float,
    onSelected: (MeasureTarget) -> Unit,
    modifier: Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val pulseTransition = rememberInfiniteTransition(label = "body-map-hotspots")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_050), RepeatMode.Reverse),
        label = "body-map-hotspot-pulse",
    )

    val hotspotMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            io.github.sceneview.math.Color(1f, 0.055f, 0.035f, 1f),
            unlit = true,
        )
    }
    val guideMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            io.github.sceneview.math.Color(0.42f, 0.72f, 1f, 1f),
            unlit = true,
        )
    }
    val labelMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            io.github.sceneview.math.Color(0.86f, 0.93f, 1f, 1f),
            unlit = true,
        )
    }

    val gestures = rememberOnGestureListener(
        onSingleTapUp = { _: MotionEvent, node: SceneNode? ->
            val target = node?.name
                ?.takeIf { it.startsWith(HOTSPOT_PREFIX) }
                ?.removePrefix(HOTSPOT_PREFIX)
                ?.let { raw -> runCatching { MeasureTarget.valueOf(raw) }.getOrNull() }
            if (target != null) onSelected(target)
        },
    )

    Box(modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            renderQuality = RenderQuality.Performance,
            autoCenterContent = true,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(x = 0f, y = 0.03f, z = 3.05f),
                targetPosition = Position(x = 0f, y = 0.05f, z = 0f),
            ),
            onGestureListener = gestures,
        ) {
            val body = rememberModelInstance(modelLoader, BODY_ASSET)

            LaunchedEffect(body) {
                body?.tintForBodyMap()
            }

            Node(
                position = Position(x = 0f, y = focusY, z = 0f),
                rotation = Rotation(y = yaw),
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
                            name = "almi_body_map_human"
                            isHittable = false
                        },
                    )
                }

                MeasureTarget.entries.forEach { target ->
                    val active = selected == target
                    val value = target.valueCm(profile)
                    val radius = when {
                        active -> 0.027f + pulse * 0.010f
                        value != null -> 0.019f + pulse * 0.004f
                        else -> 0.017f + pulse * 0.003f
                    }
                    SphereNode(
                        radius = radius,
                        position = target.marker,
                        materialInstance = hotspotMaterial,
                        apply = {
                            name = "$HOTSPOT_PREFIX${target.name}"
                            isHittable = true
                        },
                    )

                    if (value != null && !active) {
                        val p = target.marker
                        BillboardNode(
                            position = Position(x = p.x + 0.060f, y = p.y + 0.025f, z = p.z),
                        ) {
                            TextNode(
                                text = "${formatCm(value)} cm",
                                size = 0.031f,
                                materialInstance = labelMaterial,
                            )
                        }
                    }
                }

                selected?.let { target ->
                    val start = target.guideStart
                    val fullEnd = target.guideEnd
                    val animatedEnd = lerp(start, fullEnd, guideProgress)
                    LineNode(
                        start = start,
                        end = animatedEnd,
                        materialInstance = guideMaterial,
                    )
                    SphereNode(
                        radius = 0.013f + pulse * 0.004f,
                        position = animatedEnd,
                        materialInstance = guideMaterial,
                        apply = { isHittable = false },
                    )
                    SphereNode(
                        radius = 0.010f,
                        position = start,
                        materialInstance = guideMaterial,
                        apply = { isHittable = false },
                    )
                }
            }
        }

        if (selected == null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = LabSurface.copy(alpha = 0.82f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            ) {
                Text(
                    "360°  •  DRAG  •  PINCH",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = LabMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun MeasurementInputCard(
    language: String,
    target: MeasureTarget,
    existingCm: Float?,
    onConfirm: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onClose: () -> Unit,
) {
    var raw by remember(target, existingCm) {
        mutableStateOf(existingCm?.let(::formatCm).orEmpty())
    }
    var attempted by remember(target) { mutableStateOf(false) }
    val value = raw.toFloatOrNull()
    val valid = value?.let { target.validCm(it) } == true

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        shape = RoundedCornerShape(22.dp),
        color = LabSurfaceRaised.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, if (attempted && !valid) LabRed.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f)),
        shadowElevation = 14.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        target.title(language),
                        color = LabText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        target.hint(language),
                        color = LabMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = LabMuted)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = raw,
                    onValueChange = {
                        raw = numeric(it, 6)
                        attempted = false
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(target.exampleCm(), color = LabMuted.copy(alpha = 0.55f)) },
                    suffix = { Text("cm", color = LabMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LabText,
                        unfocusedTextColor = LabText,
                        cursorColor = LabBlue,
                        focusedBorderColor = if (attempted && !valid) LabRed else LabBlue,
                        unfocusedBorderColor = if (attempted && !valid) LabRed else Color.White.copy(alpha = 0.14f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
                Surface(
                    modifier = Modifier.size(52.dp).clickable {
                        attempted = true
                        if (valid && value != null) onConfirm(value)
                    },
                    shape = CircleShape,
                    color = if (valid) LabGreen else LabSurface,
                    border = BorderStroke(1.dp, if (valid) LabGreen else Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (valid) LabBackground else LabMuted,
                        )
                    }
                }
            }

            if (attempted && !valid) {
                Text(
                    tr(language, "تحقق من الرقم ثم حاول مرة أخرى.", "Check the value and try again."),
                    color = LabRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            onClear?.let { clear ->
                TextButton(onClick = {
                    clear()
                    raw = ""
                    attempted = false
                }) {
                    Text(tr(language, "مسح القياس المحفوظ", "Clear saved measurement"), color = LabMuted)
                }
            }
        }
    }
}

@Composable
private fun WeightDock(
    language: String,
    profile: BodyProfile,
    onWeightChanged: (Float) -> Unit,
) {
    var raw by rememberSaveable(profile.hasExplicitWeight) {
        mutableStateOf(if (profile.hasExplicitWeight) formatCm(profile.weightKilograms) else "")
    }
    val kg = raw.toFloatOrNull()
    val valid = kg != null && kg in 25f..320f

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = LabSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tr(language, "الوزن", "Weight"),
                    color = LabText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    tr(language, "يتفاعل حجم الجسم مباشرة مع الوزن", "Body volume reacts to weight live"),
                    color = LabMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = raw,
                onValueChange = {
                    raw = numeric(it, 6)
                    it.toFloatOrNull()?.takeIf { value -> value in 25f..320f }?.let { value ->
                        onWeightChanged(value / 0.45359237f)
                    }
                },
                modifier = Modifier.size(width = 128.dp, height = 56.dp),
                suffix = { Text("kg", color = LabMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LabText,
                    unfocusedTextColor = LabText,
                    cursorColor = LabBlue,
                    focusedBorderColor = if (valid) LabGreen else LabBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
        }
    }
}

private enum class MeasureTarget(
    val point: BodyMeasurePoint?,
    val marker: Position,
    val guideStart: Position,
    val guideEnd: Position,
    val focusYaw: Float,
    val focusScale: Float,
    val focusY: Float,
    val minCm: Float,
    val maxCm: Float,
) {
    HEIGHT(
        point = null,
        marker = Position(0.36f, 1.72f, 0.05f),
        guideStart = Position(-0.48f, 0.05f, 0.12f),
        guideEnd = Position(-0.48f, 1.78f, 0.12f),
        focusYaw = 0f,
        focusScale = 1.08f,
        focusY = 0f,
        minCm = 120f,
        maxCm = 230f,
    ),
    NECK(BodyMeasurePoint.NECK, Position(0.13f, 1.53f, 0.13f), Position(-0.12f, 1.53f, 0.18f), Position(0.12f, 1.53f, 0.18f), 0f, 1.48f, -0.32f, 25f, 60f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, Position(-0.29f, 1.43f, 0.10f), Position(-0.30f, 1.43f, 0.14f), Position(0.30f, 1.43f, 0.14f), 0f, 1.34f, -0.22f, 30f, 70f),
    CHEST(BodyMeasurePoint.CHEST, Position(0.23f, 1.28f, 0.18f), Position(-0.23f, 1.28f, 0.21f), Position(0.23f, 1.28f, 0.21f), 0f, 1.36f, -0.15f, 60f, 180f),
    WAIST(BodyMeasurePoint.WAIST, Position(0.19f, 1.04f, 0.16f), Position(-0.18f, 1.04f, 0.20f), Position(0.18f, 1.04f, 0.20f), 0f, 1.42f, 0.02f, 45f, 180f),
    HIPS(BodyMeasurePoint.HIPS, Position(0.25f, 0.88f, 0.15f), Position(-0.24f, 0.88f, 0.19f), Position(0.24f, 0.88f, 0.19f), 0f, 1.38f, 0.10f, 55f, 190f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, Position(-0.43f, 0.84f, 0.08f), Position(-0.29f, 1.42f, 0.12f), Position(-0.45f, 0.78f, 0.12f), 338f, 1.45f, -0.02f, 35f, 95f),
    WRIST(BodyMeasurePoint.WRIST, Position(-0.45f, 0.76f, 0.08f), Position(-0.48f, 0.78f, 0.12f), Position(-0.42f, 0.78f, 0.12f), 338f, 1.65f, 0.06f, 10f, 35f),
    HAND(BodyMeasurePoint.HAND, Position(-0.46f, 0.66f, 0.09f), Position(-0.46f, 0.75f, 0.12f), Position(-0.46f, 0.61f, 0.12f), 338f, 1.72f, 0.12f, 12f, 30f),
    THIGH(BodyMeasurePoint.THIGH, Position(-0.15f, 0.66f, 0.13f), Position(-0.24f, 0.66f, 0.16f), Position(-0.06f, 0.66f, 0.16f), 0f, 1.50f, 0.22f, 30f, 100f),
    INSEAM(BodyMeasurePoint.INSEAM, Position(0.04f, 0.75f, 0.08f), Position(0.02f, 0.78f, 0.12f), Position(-0.10f, 0.07f, 0.12f), 0f, 1.30f, 0.24f, 45f, 125f),
    CALF(BodyMeasurePoint.CALF, Position(-0.14f, 0.35f, 0.10f), Position(-0.20f, 0.35f, 0.13f), Position(-0.08f, 0.35f, 0.13f), 0f, 1.62f, 0.42f, 20f, 70f),
    FOOT(BodyMeasurePoint.FOOT, Position(-0.11f, 0.07f, 0.18f), Position(-0.11f, 0.08f, 0.08f), Position(-0.11f, 0.08f, 0.30f), 70f, 1.75f, 0.56f, 18f, 35f),
    ;

    fun valueCm(profile: BodyProfile): Float? = when (this) {
        HEIGHT -> profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
        else -> point?.let { p -> profile.measurementsInches[p]?.times(2.54f) }
    }

    fun validCm(value: Float): Boolean = value.isFinite() && value in minCm..maxCm

    fun title(language: String): String = when (this) {
        HEIGHT -> tr(language, "الطول", "Height")
        NECK -> tr(language, "محيط الرقبة", "Neck")
        SHOULDERS -> tr(language, "عرض الكتفين", "Shoulder width")
        CHEST -> tr(language, "محيط الصدر", "Chest")
        WAIST -> tr(language, "محيط الخصر", "Waist")
        HIPS -> tr(language, "محيط الورك", "Hips")
        ARM_LENGTH -> tr(language, "طول الذراع / الكم", "Arm / sleeve length")
        WRIST -> tr(language, "محيط المعصم", "Wrist")
        HAND -> tr(language, "طول اليد", "Hand length")
        THIGH -> tr(language, "محيط الفخذ", "Thigh")
        INSEAM -> tr(language, "طول الساق الداخلي", "Inseam")
        CALF -> tr(language, "محيط الساق", "Calf")
        FOOT -> tr(language, "طول القدم", "Foot length")
    }

    fun hint(language: String): String = when (this) {
        HEIGHT -> tr(language, "من الأرض حتى أعلى الرأس", "Floor to the top of the head")
        NECK -> tr(language, "حول قاعدة الرقبة بدون شد", "Around the base of the neck")
        SHOULDERS -> tr(language, "من نهاية كتف إلى نهاية الكتف الآخر", "Shoulder tip to shoulder tip")
        CHEST -> tr(language, "حول أعرض نقطة من الصدر", "Around the fullest chest point")
        WAIST -> tr(language, "حول أضيق نقطة من الخصر الطبيعي", "Around the natural waist")
        HIPS -> tr(language, "حول أعرض نقطة من الورك", "Around the fullest hip point")
        ARM_LENGTH -> tr(language, "من نقطة الكتف حتى عظمة المعصم", "Shoulder point to wrist bone")
        WRIST -> tr(language, "حول عظمة المعصم", "Around the wrist bone")
        HAND -> tr(language, "من بداية الكف حتى نهاية أطول إصبع", "Palm base to longest fingertip")
        THIGH -> tr(language, "حول أعرض جزء من أعلى الفخذ", "Around the fullest upper thigh")
        INSEAM -> tr(language, "من أعلى داخل الساق حتى الأرض", "Crotch to floor")
        CALF -> tr(language, "حول أعرض نقطة من الساق", "Around the fullest calf")
        FOOT -> tr(language, "من مؤخرة الكعب حتى أطول إصبع", "Heel to longest toe")
    }

    fun exampleCm(): String = when (this) {
        HEIGHT -> "175"
        NECK -> "38"
        SHOULDERS -> "46"
        CHEST -> "98"
        WAIST -> "82"
        HIPS -> "100"
        ARM_LENGTH -> "61"
        WRIST -> "17"
        HAND -> "19"
        THIGH -> "56"
        INSEAM -> "82"
        CALF -> "38"
        FOOT -> "27"
    }
}

private fun ModelInstance.tintForBodyMap() {
    materialInstances.forEach { material ->
        runCatching {
            material.setParameter(
                "baseColorFactor",
                Colors.RgbaType.SRGB,
                0.24f,
                0.43f,
                0.70f,
                1f,
            )
        }
    }
}

private fun lerp(start: Position, end: Position, progress: Float): Position {
    val t = progress.coerceIn(0f, 1f)
    return Position(
        x = start.x + (end.x - start.x) * t,
        y = start.y + (end.y - start.y) * t,
        z = start.z + (end.z - start.z) * t,
    )
}

private fun nearestYaw(current: Float, preferred: Float): Float {
    val normalized = ((current % 360f) + 360f) % 360f
    var delta = preferred - normalized
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return current + delta
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
private fun numeric(value: String, maxLength: Int): String = value.filter { it.isDigit() || it == '.' }.take(maxLength)
private fun formatCm(value: Float): String = if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString() else "%.1f".format(Locale.US, value)

private const val HOTSPOT_PREFIX = "almi_measure_"
private const val BODY_ASSET = "almi3d/vitruvian_body.glb"
