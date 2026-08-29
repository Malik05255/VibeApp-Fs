package com.almi.ai.ui.body

import android.view.MotionEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
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
 * The measurement route deliberately owns only one GLB. Head, hair and avatar assets are not
 * created here. SceneView owns the model through rememberModelInstance so Filament resources are
 * created and destroyed on the UI lifecycle instead of being manually retained across screens.
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
    val selected = selectedName?.let { name ->
        runCatching { MeasureTarget.valueOf(name) }.getOrNull()
    }
    var targetYaw by rememberSaveable { mutableStateOf(0f) }
    var guideReady by remember(selectedName) { mutableStateOf(false) }

    LaunchedEffect(selectedName) {
        guideReady = false
        if (selectedName != null) {
            delay(260)
            guideReady = true
        }
    }

    val solved = remember(profile) { BodyShapeSolver.solve(profile) }
    val width by animateFloatAsState(solved.widthScale, tween(420), label = "body-map-width")
    val height by animateFloatAsState(solved.heightScale, tween(420), label = "body-map-height")
    val depth by animateFloatAsState(solved.depthScale, tween(420), label = "body-map-depth")
    val yaw by animateFloatAsState(targetYaw, tween(480), label = "body-map-yaw")
    val focusScale by animateFloatAsState(selected?.focusScale ?: 1f, tween(520), label = "body-map-focus")
    val focusY by animateFloatAsState(selected?.focusY ?: 0f, tween(520), label = "body-map-focus-y")
    val guideProgress by animateFloatAsState(
        if (guideReady) 1f else 0f,
        tween(720),
        label = "body-map-guide",
    )

    fun open(target: MeasureTarget) {
        selectedName = target.name
        targetYaw = nearestYaw(yaw, target.focusYaw)
    }

    fun closeMeasurement() {
        selectedName = null
        targetYaw = nearestYaw(yaw, 0f)
    }

    val totalFacts = MeasureTarget.entries.size + 1
    val completedFacts = MeasureTarget.entries.count { it.valueCm(profile) != null } +
        if (profile.hasExplicitWeight) 1 else 0

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
                    shape = solved.copy(
                        widthScale = width,
                        heightScale = height,
                        depthScale = depth,
                    ),
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
                        color = LabSurface.copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Text(
                            tr(language, "اسحب 360° • اضغط النقطة الحمراء", "Drag 360° • tap a red point"),
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            color = LabMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                } else {
                    selected?.let { target ->
                        MeasurementInputCard(
                            language = language,
                            target = target,
                            existingCm = target.valueCm(profile),
                            onConfirm = { centimeters ->
                                if (target == MeasureTarget.HEIGHT) {
                                    onHeightChanged(centimeters / 2.54f)
                                } else {
                                    target.point?.let { point ->
                                        onMeasurementChanged(point, centimeters / 2.54f)
                                    }
                                }
                                closeMeasurement()
                            },
                            onClear = target.point
                                ?.takeIf { it in profile.measurementsInches }
                                ?.let { point -> ({ onMeasurementCleared(point) }) },
                            onClose = ::closeMeasurement,
                            modifier = Modifier.align(Alignment.TopCenter),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            metallic = 0.02f,
            roughness = 0.30f,
            reflectance = 0.72f,
        )
    }
    val haloMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            io.github.sceneview.math.Color(0.72f, 0.018f, 0.012f, 1f),
            metallic = 0f,
            roughness = 0.52f,
            reflectance = 0.42f,
        )
    }
    val guideMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            io.github.sceneview.math.Color(0.42f, 0.72f, 1f, 1f),
            metallic = 0f,
            roughness = 0.34f,
            reflectance = 0.70f,
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
                        radius = radius + 0.009f + pulse * 0.005f,
                        position = target.marker,
                        materialInstance = haloMaterial,
                        apply = {
                            name = "${HOTSPOT_PREFIX}${target.name}_halo"
                            isHittable = false
                        },
                    )
                    SphereNode(
                        radius = radius,
                        position = target.marker,
                        materialInstance = hotspotMaterial,
                        apply = {
                            name = "$HOTSPOT_PREFIX${target.name}"
                            isHittable = true
                        },
                    )
                }

                selected?.let { target ->
                    val start = target.guideStart
                    val fullEnd = target.guideEnd
                    val animatedEnd = lerp(start, fullEnd, guideProgress)
                    val arrowA = arrowHead(animatedEnd, start, 1f)
                    val arrowB = arrowHead(animatedEnd, start, -1f)

                    LineNode(
                        start = start,
                        end = animatedEnd,
                        materialInstance = guideMaterial,
                    )
                    LineNode(
                        start = animatedEnd,
                        end = arrowA,
                        materialInstance = guideMaterial,
                    )
                    LineNode(
                        start = animatedEnd,
                        end = arrowB,
                        materialInstance = guideMaterial,
                    )
                    SphereNode(
                        radius = 0.011f + pulse * 0.003f,
                        position = animatedEnd,
                        materialInstance = guideMaterial,
                        apply = { isHittable = false },
                    )
                    SphereNode(
                        radius = 0.009f,
                        position = start,
                        materialInstance = guideMaterial,
                        apply = { isHittable = false },
                    )
                }
            }
        }

        if (selected == null && isFrontFacing(yaw)) {
            CompletedMeasurementLabels(profile = profile)
        }

        if (selected == null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = LabSurface.copy(alpha = 0.84f),
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
private fun CompletedMeasurementLabels(profile: BodyProfile) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        MeasureTarget.entries.forEach { target ->
            target.valueCm(profile)?.let { value ->
                Surface(
                    modifier = Modifier.offset(
                        x = (maxWidth * target.labelX) - 28.dp,
                        y = (maxHeight * target.labelY) - 10.dp,
                    ),
                    shape = RoundedCornerShape(999.dp),
                    color = LabSurfaceRaised.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, LabRed.copy(alpha = 0.35f)),
                ) {
                    Text(
                        "${formatCm(value)} cm",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        color = LabText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
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
    modifier: Modifier = Modifier,
) {
    var raw by remember(target, existingCm) {
        mutableStateOf(existingCm?.let(::formatCm).orEmpty())
    }
    var attempted by remember(target) { mutableStateOf(false) }
    val value = raw.toFloatOrNull()
    val valid = value?.let { target.validCm(it) } == true

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        shape = RoundedCornerShape(22.dp),
        color = LabSurfaceRaised.copy(alpha = 0.98f),
        border = BorderStroke(
            1.dp,
            when {
                attempted && !valid -> LabRed
                valid -> LabGreen.copy(alpha = 0.65f)
                else -> Color.White.copy(alpha = 0.09f)
            },
        ),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        target.title(language),
                        color = LabText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        target.instruction(language),
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
                    onValueChange = { raw = numeric(it, 6) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LabText,
                        unfocusedTextColor = LabText,
                        focusedBorderColor = LabBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedLabelColor = LabBlue,
                        unfocusedLabelColor = LabMuted,
                        cursorColor = LabBlue,
                    ),
                )

                Button(
                    onClick = {
                        attempted = true
                        if (valid && value != null) onConfirm(value)
                    },
                    modifier = Modifier.size(54.dp),
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ContentPadding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (valid) LabGreen else LabBlue,
                        contentColor = LabBackground,
                    ),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (attempted && !valid) {
                        tr(language, "القيمة خارج النطاق المتوقع", "Value is outside the expected range")
                    } else {
                        tr(language, "أدخل الرقم ثم اضغط ✓", "Enter the number, then tap ✓")
                    },
                    color = if (attempted && !valid) LabRed else LabMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                onClear?.let { clear ->
                    TextButton(onClick = clear) {
                        Text(tr(language, "مسح", "Clear"), color = LabRed)
                    }
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
    var raw by remember(profile.weightPounds, profile.hasExplicitWeight) {
        mutableStateOf(
            if (profile.hasExplicitWeight) formatCm(profile.weightKilograms) else ""
        )
    }
    val value = raw.toFloatOrNull()
    val valid = value != null && value in 25f..300f

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
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
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(
                        language,
                        "أدخل الوزن؛ حجم الجسم يتغير مباشرة.",
                        "Enter weight; body volume updates live.",
                    ),
                    color = LabMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = raw,
                onValueChange = { next ->
                    raw = numeric(next, 6)
                    raw.toFloatOrNull()?.takeIf { it in 25f..300f }?.let { kg ->
                        onWeightChanged(kg / 0.45359237f)
                    }
                },
                modifier = Modifier.width(118.dp),
                singleLine = true,
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LabText,
                    unfocusedTextColor = LabText,
                    focusedBorderColor = if (valid) LabGreen else LabBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    cursorColor = LabBlue,
                    focusedSuffixColor = LabMuted,
                    unfocusedSuffixColor = LabMuted,
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
    val labelX: Float,
    val labelY: Float,
) {
    HEIGHT(
        point = null,
        marker = Position(0.20f, 1.64f, 0.03f),
        guideStart = Position(0.42f, 0.03f, 0.02f),
        guideEnd = Position(0.42f, 1.69f, 0.02f),
        focusYaw = 0f,
        focusScale = 1.03f,
        focusY = 0f,
        labelX = 0.72f,
        labelY = 0.14f,
    ),
    NECK(
        BodyMeasurePoint.NECK,
        Position(0.12f, 1.52f, 0.10f),
        Position(-0.10f, 1.52f, 0.12f),
        Position(0.10f, 1.52f, 0.12f),
        0f,
        1.42f,
        -0.33f,
        0.62f,
        0.23f,
    ),
    SHOULDERS(
        BodyMeasurePoint.SHOULDERS,
        Position(-0.29f, 1.43f, 0.08f),
        Position(-0.29f, 1.43f, 0.10f),
        Position(0.29f, 1.43f, 0.10f),
        0f,
        1.32f,
        -0.28f,
        0.25f,
        0.27f,
    ),
    CHEST(
        BodyMeasurePoint.CHEST,
        Position(0.18f, 1.28f, 0.17f),
        Position(-0.20f, 1.28f, 0.18f),
        Position(0.20f, 1.28f, 0.18f),
        0f,
        1.34f,
        -0.16f,
        0.66f,
        0.35f,
    ),
    WAIST(
        BodyMeasurePoint.WAIST,
        Position(0.17f, 1.04f, 0.15f),
        Position(-0.17f, 1.04f, 0.17f),
        Position(0.17f, 1.04f, 0.17f),
        0f,
        1.38f,
        0.02f,
        0.66f,
        0.47f,
    ),
    HIPS(
        BodyMeasurePoint.HIPS,
        Position(0.18f, 0.87f, 0.14f),
        Position(-0.20f, 0.87f, 0.16f),
        Position(0.20f, 0.87f, 0.16f),
        0f,
        1.38f,
        0.12f,
        0.66f,
        0.55f,
    ),
    ARM_LENGTH(
        BodyMeasurePoint.ARM_LENGTH,
        Position(-0.39f, 1.13f, 0.06f),
        Position(-0.32f, 1.40f, 0.07f),
        Position(-0.44f, 0.82f, 0.06f),
        330f,
        1.55f,
        -0.12f,
        0.16f,
        0.46f,
    ),
    HAND(
        BodyMeasurePoint.HAND,
        Position(-0.46f, 0.72f, 0.05f),
        Position(-0.45f, 0.80f, 0.06f),
        Position(-0.46f, 0.67f, 0.06f),
        325f,
        1.78f,
        0.08f,
        0.13f,
        0.61f,
    ),
    INSEAM(
        BodyMeasurePoint.INSEAM,
        Position(0.08f, 0.72f, 0.08f),
        Position(0.02f, 0.78f, 0.09f),
        Position(0.02f, 0.06f, 0.09f),
        0f,
        1.42f,
        0.30f,
        0.61f,
        0.71f,
    ),
    FOOT(
        BodyMeasurePoint.FOOT,
        Position(-0.13f, 0.08f, 0.18f),
        Position(-0.13f, 0.07f, -0.04f),
        Position(-0.13f, 0.07f, 0.22f),
        70f,
        1.75f,
        0.58f,
        0.39f,
        0.89f,
    );

    fun valueCm(profile: BodyProfile): Float? = when (this) {
        HEIGHT -> profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
        else -> point?.let { profile.measurementsInches[it] }?.times(2.54f)
    }

    fun validCm(value: Float): Boolean = when (this) {
        HEIGHT -> value in 100f..230f
        NECK -> value in 20f..70f
        SHOULDERS -> value in 25f..80f
        CHEST, WAIST, HIPS -> value in 45f..180f
        ARM_LENGTH -> value in 35f..100f
        HAND -> value in 8f..35f
        INSEAM -> value in 45f..125f
        FOOT -> value in 15f..40f
    }

    fun title(language: String): String = when (this) {
        HEIGHT -> tr(language, "الطول", "Height")
        NECK -> tr(language, "محيط الرقبة", "Neck")
        SHOULDERS -> tr(language, "عرض الكتفين", "Shoulders")
        CHEST -> tr(language, "محيط الصدر", "Chest")
        WAIST -> tr(language, "عرض / محيط الخصر", "Waist")
        HIPS -> tr(language, "محيط الورك", "Hips")
        ARM_LENGTH -> tr(language, "طول الذراع / الكم", "Arm / sleeve length")
        HAND -> tr(language, "طول اليد", "Hand length")
        INSEAM -> tr(language, "طول الساق الداخلي", "Inseam")
        FOOT -> tr(language, "طول القدم", "Foot length")
    }

    fun instruction(language: String): String = when (this) {
        HEIGHT -> tr(language, "من الأرض إلى أعلى الرأس.", "From the floor to the top of the head.")
        NECK -> tr(language, "حول قاعدة الرقبة بدون شد الشريط.", "Around the base of the neck without tightening.")
        SHOULDERS -> tr(language, "من نهاية كتف إلى نهاية الكتف الآخر.", "From one shoulder tip to the other.")
        CHEST -> tr(language, "حول أعرض نقطة من الصدر.", "Around the fullest part of the chest.")
        WAIST -> tr(language, "حول الخصر الطبيعي عند أضيق نقطة.", "Around the natural waist at its narrowest point.")
        HIPS -> tr(language, "حول أعرض نقطة من الورك.", "Around the fullest part of the hips.")
        ARM_LENGTH -> tr(language, "من نقطة الكتف حتى عظمة المعصم.", "From shoulder point to wrist bone.")
        HAND -> tr(language, "من بداية راحة اليد عند المعصم حتى نهاية أطول إصبع.", "From the wrist crease to the tip of the longest finger.")
        INSEAM -> tr(language, "من أعلى الساق الداخلي حتى الأرض.", "From the top of the inner leg to the floor.")
        FOOT -> tr(language, "من مؤخرة الكعب حتى نهاية أطول إصبع.", "From the back of the heel to the longest toe.")
    }
}

private fun lerp(start: Position, end: Position, t: Float): Position = Position(
    x = start.x + (end.x - start.x) * t,
    y = start.y + (end.y - start.y) * t,
    z = start.z + (end.z - start.z) * t,
)

private fun arrowHead(end: Position, start: Position, side: Float): Position {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dz = end.z - start.z
    val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.0001f)
    val ux = dx / length
    val uy = dy / length
    val uz = dz / length
    return Position(
        x = end.x - ux * 0.045f + side * 0.018f,
        y = end.y - uy * 0.045f + side * if (abs(ux) > abs(uy)) 0.018f else 0f,
        z = end.z - uz * 0.045f,
    )
}

private fun nearestYaw(current: Float, preferred: Float): Float {
    val normalized = ((current % 360f) + 360f) % 360f
    var delta = preferred - normalized
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return current + delta
}

private fun isFrontFacing(yaw: Float): Boolean {
    val normalized = ((yaw % 360f) + 360f) % 360f
    return normalized < 30f || normalized > 330f
}

private fun numeric(value: String, maxLength: Int): String =
    value.filter { it.isDigit() || it == '.' }.take(maxLength)

private fun formatCm(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else "%.1f".format(Locale.US, value)

private fun tr(language: String, ar: String, en: String): String =
    if (language == "ar") ar else en

private const val HOTSPOT_PREFIX = "almi_measure_"
private const val BODY_ASSET = "almi3d/vitruvian_body.glb"
