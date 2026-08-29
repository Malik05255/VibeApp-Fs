package com.almi.ai.ui.body

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private val LabBackground = Color(0xFF04101E)
private val LabSurface = Color(0xFF0B1A2C)
private val LabSurfaceRaised = Color(0xFF10243B)
private val LabText = Color(0xFFF6FAFF)
private val LabMuted = Color(0xFF91A8C5)
private val LabBlue = Color(0xFF86BCFF)
private val LabBlueBright = Color(0xFFC6E1FF)
private val LabRed = Color(0xFFFF433D)
private val LabGreen = Color(0xFF59D8A6)
private const val INCH_TO_CM = 2.54f
private const val POUND_TO_KG = 0.45359237f

/**
 * ALMI v8 Body Map.
 *
 * This screen deliberately uses only Compose Canvas. There is no SceneView, Filament, GLB loader,
 * JNI surface, GPU readback or native 3D lifecycle. The body still rotates through a perspective
 * projection, focuses smoothly on measurement regions and reacts to entered anthropometrics, but
 * a renderer failure can no longer terminate the Android process through a native graphics stack.
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
    val selected = selectedName?.let { raw -> runCatching { MeasureTarget.valueOf(raw) }.getOrNull() }
    var targetYaw by rememberSaveable { mutableStateOf(0f) }
    var guideVisible by remember(selectedName) { mutableStateOf(false) }

    LaunchedEffect(selectedName) {
        guideVisible = false
        if (selectedName != null) {
            delay(180)
            guideVisible = true
        }
    }

    val solved = remember(profile) { BodyShapeSolver.solve(profile) }
    val bodyWidth by animateFloatAsState(solved.widthScale, tween(420), label = "body-width")
    val bodyHeight by animateFloatAsState(solved.heightScale, tween(420), label = "body-height")
    val bodyDepth by animateFloatAsState(solved.depthScale, tween(420), label = "body-depth")
    val yaw by animateFloatAsState(targetYaw, tween(330), label = "body-yaw")
    val focusZoom by animateFloatAsState(selected?.zoom ?: 1f, tween(480), label = "focus-zoom")
    val guideProgress by animateFloatAsState(if (guideVisible) 1f else 0f, tween(620), label = "guide")

    val shape = solved.copy(
        widthScale = bodyWidth,
        heightScale = bodyHeight,
        depthScale = bodyDepth,
    )

    fun openTarget(target: MeasureTarget) {
        selectedName = target.name
        targetYaw = nearestYaw(yaw, target.preferredYaw)
    }

    fun closeTarget() {
        selectedName = null
        targetYaw = nearestYaw(yaw, 0f)
    }

    val completed = MeasureTarget.entries.count { it.valueCm(profile) != null } +
        if (profile.hasExplicitWeight) 1 else 0
    val total = MeasureTarget.entries.size + 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LabBackground)
            .statusBarsPadding(),
    ) {
        BodyMapHeader(
            language = language,
            completed = completed,
            total = total,
            onDone = onComplete,
        )
        LinearProgressIndicator(
            progress = { completed.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = LabBlue,
            trackColor = Color.White.copy(alpha = 0.07f),
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AnatomicalBodyViewport(
                profile = profile,
                selected = selected,
                shape = shape,
                yaw = yaw,
                zoom = focusZoom,
                guideProgress = guideProgress,
                onYawDelta = { delta -> if (selected == null) targetYaw += delta },
                onSelect = ::openTarget,
                modifier = Modifier.fillMaxSize(),
            )

            if (selected == null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = LabSurface.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Text(
                        tr(language, "اسحب 360°  •  اضغط النقطة الحمراء", "Drag 360°  •  tap a red point"),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = LabMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                MeasurementEditor(
                    language = language,
                    target = selected,
                    existingCm = selected.valueCm(profile),
                    onConfirm = { centimeters ->
                        if (selected == MeasureTarget.HEIGHT) {
                            onHeightChanged(centimeters / INCH_TO_CM)
                        } else {
                            selected.point?.let { onMeasurementChanged(it, centimeters / INCH_TO_CM) }
                        }
                        closeTarget()
                    },
                    onClear = selected.point
                        ?.takeIf { it in profile.measurementsInches }
                        ?.let { point -> ({ onMeasurementCleared(point) }) },
                    onClose = ::closeTarget,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        WeightDock(
            language = language,
            profile = profile,
            onWeightChanged = onWeightChanged,
        )
    }
}

@Composable
private fun BodyMapHeader(
    language: String,
    completed: Int,
    total: Int,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(999.dp), color = LabSurfaceRaised) {
                Text(
                    "$completed/$total",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    color = LabMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onDone) {
                Text(tr(language, "تم", "Done"), color = LabText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AnatomicalBodyViewport(
    profile: BodyProfile,
    selected: MeasureTarget?,
    shape: DigitalTwinShape,
    yaw: Float,
    zoom: Float,
    guideProgress: Float,
    onYawDelta: (Float) -> Unit,
    onSelect: (MeasureTarget) -> Unit,
    modifier: Modifier,
) {
    var pixelSize by remember { mutableStateOf(IntSize.Zero) }
    val pulseTransition = rememberInfiniteTransition(label = "hotspot-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(920), RepeatMode.Reverse),
        label = "hotspot-pulse-value",
    )
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LabText.toArgb()
            textSize = 25f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val inputModifier = Modifier
        .onSizeChanged { pixelSize = it }
        .pointerInput(selected, yaw, shape, pixelSize) {
            detectTapGestures { tap ->
                if (selected != null || pixelSize.width <= 0) return@detectTapGestures
                val canvasSize = Size(pixelSize.width.toFloat(), pixelSize.height.toFloat())
                val nearest = MeasureTarget.entries
                    .map { target -> target to project(target.marker, canvasSize, yaw, shape, 1f, null) }
                    .minByOrNull { (_, point) -> (point - tap).length() }
                nearest?.let { (target, point) ->
                    if ((point - tap).length() <= 62f) onSelect(target)
                }
            }
        }
        .pointerInput(selected) {
            detectDragGestures { change, drag ->
                change.consume()
                if (selected == null) onYawDelta(drag.x * 0.72f)
            }
        }

    Box(modifier = modifier.then(inputModifier)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawClinicalGrid()
            drawAnatomicalHuman(shape = shape, yaw = yaw, selected = selected, zoom = zoom)

            MeasureTarget.entries.forEach { target ->
                val point = project(target.marker, size, yaw, shape, zoom, selected?.marker)
                val value = target.valueCm(profile)
                val active = target == selected
                val radius = when {
                    active -> 20f + pulse * 8f
                    value != null -> 14f + pulse * 4f
                    else -> 12f + pulse * 4f
                }
                drawCircle(LabRed.copy(alpha = 0.08f), radius * 1.55f, point)
                drawCircle(LabRed.copy(alpha = 0.20f), radius, point)
                drawCircle(Color(0xFFFF817A), if (active) 6.8f else 4.8f, point)
                drawCircle(Color.White.copy(alpha = 0.70f), if (active) 1.9f else 1.2f, point)

                if (value != null && selected == null && isFrontEnough(yaw)) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${formatCm(value)} cm",
                        point.x + 15f,
                        point.y - 9f,
                        labelPaint,
                    )
                }
            }

            selected?.let { target ->
                val animatedEnd = lerp(target.guideStart, target.guideEnd, guideProgress)
                val start = project(target.guideStart, size, yaw, shape, zoom, target.marker)
                val end = project(animatedEnd, size, yaw, shape, zoom, target.marker)
                drawMeasurementGuide(start, end, guideProgress)
            }
        }

        if (selected == null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                shape = RoundedCornerShape(999.dp),
                color = LabSurface.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            ) {
                Text(
                    "360°  •  DRAG",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = LabMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun DrawScope.drawClinicalGrid() {
    val step = size.width / 7f
    var x = 0f
    while (x <= size.width) {
        drawLine(Color.White.copy(alpha = 0.018f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(Color.White.copy(alpha = 0.016f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF2F78C5).copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.50f, size.height * 0.46f),
            radius = size.width * 0.62f,
        ),
        center = Offset(size.width * 0.50f, size.height * 0.46f),
        radius = size.width * 0.62f,
    )
}

private fun DrawScope.drawAnatomicalHuman(
    shape: DigitalTwinShape,
    yaw: Float,
    selected: MeasureTarget?,
    zoom: Float,
) {
    fun p(x: Float, y: Float, z: Float = 0f): Offset =
        project(Vec3(x, y, z), size, yaw, shape, zoom, selected?.marker)

    val radians = Math.toRadians(yaw.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val frontAmount = abs(c)
    val showingBack = c < 0f
    val rightNear = s <= 0f

    val bodyGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF0D2344).copy(alpha = 0.80f),
            Color(0xFF376498).copy(alpha = 0.83f),
            Color(0xFFCFE4FF).copy(alpha = 0.95f),
            Color(0xFF5E8FC7).copy(alpha = 0.86f),
            Color(0xFF102947).copy(alpha = 0.78f),
        ),
        startX = size.width * 0.27f,
        endX = size.width * 0.73f,
    )
    val edge = Color(0xFFDDEEFF).copy(alpha = 0.78f)
    val anatomy = Color(0xFFB9D8FF).copy(alpha = 0.26f)
    val bone = Color(0xFFD8EAFF).copy(alpha = 0.15f)

    if (rightNear) {
        drawArm(right = false, alpha = 0.58f, shape, yaw, selected, zoom, bodyGradient, edge)
        drawLeg(right = false, alpha = 0.62f, shape, yaw, selected, zoom, bodyGradient, edge)
    } else {
        drawArm(right = true, alpha = 0.58f, shape, yaw, selected, zoom, bodyGradient, edge)
        drawLeg(right = true, alpha = 0.62f, shape, yaw, selected, zoom, bodyGradient, edge)
    }

    val shoulderL = p(-0.305f, 0.215f, 0.11f)
    val upperL = p(-0.286f, 0.294f, 0.13f)
    val waistL = p(-0.190f, 0.455f, 0.105f)
    val hipL = p(-0.252f, 0.555f, 0.14f)
    val crotchL = p(-0.052f, 0.607f, 0.10f)
    val crotchR = p(0.052f, 0.607f, -0.10f)
    val hipR = p(0.252f, 0.555f, -0.14f)
    val waistR = p(0.190f, 0.455f, -0.105f)
    val upperR = p(0.286f, 0.294f, -0.13f)
    val shoulderR = p(0.305f, 0.215f, -0.11f)

    val torso = Path().apply {
        moveTo(shoulderL.x, shoulderL.y)
        cubicTo(
            p(-0.335f, 0.255f, 0.13f).x,
            p(-0.335f, 0.255f, 0.13f).y,
            upperL.x,
            upperL.y,
            waistL.x,
            waistL.y,
        )
        cubicTo(waistL.x, waistL.y, hipL.x, hipL.y, crotchL.x, crotchL.y)
        lineTo(crotchR.x, crotchR.y)
        cubicTo(hipR.x, hipR.y, waistR.x, waistR.y, waistR.x, waistR.y)
        cubicTo(
            upperR.x,
            upperR.y,
            p(0.335f, 0.255f, -0.13f).x,
            p(0.335f, 0.255f, -0.13f).y,
            shoulderR.x,
            shoulderR.y,
        )
        close()
    }
    drawPath(torso, bodyGradient)
    drawPath(torso, edge.copy(alpha = 0.14f), style = Stroke(9f * zoom))
    drawPath(torso, edge, style = Stroke(1.45f * zoom))

    val neck = Path().apply {
        val n1 = p(-0.074f, 0.135f, 0.065f)
        val n2 = p(-0.108f, 0.196f, 0.08f)
        val n3 = p(0.108f, 0.196f, -0.08f)
        val n4 = p(0.074f, 0.135f, -0.065f)
        moveTo(n1.x, n1.y)
        lineTo(n2.x, n2.y)
        lineTo(n3.x, n3.y)
        lineTo(n4.x, n4.y)
        close()
    }
    drawPath(neck, bodyGradient)
    drawPath(neck, edge, style = Stroke(1.35f * zoom))

    val head = Path().apply {
        val top = p(0f, 0.027f)
        val leftTemple = p(-0.086f, 0.062f, 0.058f)
        val leftJaw = p(-0.064f, 0.124f, 0.050f)
        val chin = p(0f, 0.148f)
        val rightJaw = p(0.064f, 0.124f, -0.050f)
        val rightTemple = p(0.086f, 0.062f, -0.058f)
        moveTo(top.x, top.y)
        cubicTo(
            p(-0.055f, 0.018f, 0.035f).x,
            p(-0.055f, 0.018f, 0.035f).y,
            leftTemple.x,
            leftTemple.y,
            leftJaw.x,
            leftJaw.y,
        )
        cubicTo(leftJaw.x, leftJaw.y, chin.x, chin.y, chin.x, chin.y)
        cubicTo(chin.x, chin.y, rightJaw.x, rightJaw.y, rightJaw.x, rightJaw.y)
        cubicTo(
            rightTemple.x,
            rightTemple.y,
            p(0.055f, 0.018f, -0.035f).x,
            p(0.055f, 0.018f, -0.035f).y,
            top.x,
            top.y,
        )
        close()
    }
    drawPath(head, bodyGradient)
    drawPath(head, edge.copy(alpha = 0.15f), style = Stroke(9f * zoom))
    drawPath(head, edge, style = Stroke(1.45f * zoom))

    if (frontAmount > 0.24f) {
        if (!showingBack) {
            drawLine(anatomy, p(0f, 0.205f), p(0f, 0.458f), 1.2f * zoom)
            drawLine(anatomy, p(-0.245f, 0.284f, 0.095f), p(-0.020f, 0.326f, 0.018f), 1.2f * zoom)
            drawLine(anatomy, p(0.245f, 0.284f, -0.095f), p(0.020f, 0.326f, -0.018f), 1.2f * zoom)
            repeat(3) { index ->
                val y = 0.358f + index * 0.043f
                drawLine(anatomy.copy(alpha = 0.70f), p(-0.116f, y, 0.06f), p(0.116f, y, -0.06f), 1f * zoom)
            }
            drawLine(bone, p(-0.235f, 0.214f, 0.075f), p(0.235f, 0.214f, -0.075f), 1.1f * zoom)
            drawLine(anatomy, p(-0.043f, 0.083f, 0.035f), p(0.043f, 0.083f, -0.035f), 1f * zoom)
            drawLine(anatomy.copy(alpha = 0.65f), p(0f, 0.075f), p(0f, 0.118f), 1f * zoom)
        } else {
            drawLine(anatomy, p(0f, 0.166f), p(0f, 0.586f), 1.35f * zoom)
            drawLine(anatomy, p(-0.252f, 0.260f, 0.10f), p(-0.08f, 0.36f, 0.04f), 1.2f * zoom)
            drawLine(anatomy, p(0.252f, 0.260f, -0.10f), p(0.08f, 0.36f, -0.04f), 1.2f * zoom)
        }
    }

    if (rightNear) {
        drawArm(right = true, alpha = 0.98f, shape, yaw, selected, zoom, bodyGradient, edge)
        drawLeg(right = true, alpha = 0.98f, shape, yaw, selected, zoom, bodyGradient, edge)
    } else {
        drawArm(right = false, alpha = 0.98f, shape, yaw, selected, zoom, bodyGradient, edge)
        drawLeg(right = false, alpha = 0.98f, shape, yaw, selected, zoom, bodyGradient, edge)
    }

    val ground = p(0f, 0.993f)
    drawOval(
        color = Color.Black.copy(alpha = 0.30f),
        topLeft = Offset(ground.x - 72f * zoom, ground.y - 4f),
        size = Size(144f * zoom, 13f * zoom),
    )
}

private fun DrawScope.drawArm(
    right: Boolean,
    alpha: Float,
    shape: DigitalTwinShape,
    yaw: Float,
    selected: MeasureTarget?,
    zoom: Float,
    fill: Brush,
    edge: Color,
) {
    val side = if (right) 1f else -1f
    val depth = if (right) -1f else 1f
    fun p(x: Float, y: Float, z: Float = 0f): Offset = project(Vec3(x, y, z), size, yaw, shape, zoom, selected?.marker)

    val shoulderOuter = p(side * 0.318f, 0.222f, depth * 0.105f)
    val shoulderInner = p(side * 0.265f, 0.246f, depth * 0.060f)
    val elbowOuter = p(side * 0.465f, 0.405f, depth * 0.070f)
    val elbowInner = p(side * 0.414f, 0.405f, depth * 0.040f)
    val wristOuter = p(side * 0.555f, 0.575f, depth * 0.055f)
    val wristInner = p(side * 0.520f, 0.575f, depth * 0.030f)
    val handOuter = p(side * 0.592f, 0.648f, depth * 0.045f)
    val handInner = p(side * 0.552f, 0.653f, depth * 0.024f)

    val arm = Path().apply {
        moveTo(shoulderOuter.x, shoulderOuter.y)
        cubicTo(
            p(side * 0.388f, 0.300f, depth * 0.090f).x,
            p(side * 0.388f, 0.300f, depth * 0.090f).y,
            elbowOuter.x,
            elbowOuter.y,
            elbowOuter.x,
            elbowOuter.y,
        )
        cubicTo(
            p(side * 0.523f, 0.490f, depth * 0.060f).x,
            p(side * 0.523f, 0.490f, depth * 0.060f).y,
            wristOuter.x,
            wristOuter.y,
            handOuter.x,
            handOuter.y,
        )
        lineTo(handInner.x, handInner.y)
        cubicTo(wristInner.x, wristInner.y, p(side * 0.470f, 0.490f, depth * 0.035f).x, p(side * 0.470f, 0.490f, depth * 0.035f).y, elbowInner.x, elbowInner.y)
        cubicTo(elbowInner.x, elbowInner.y, shoulderInner.x, shoulderInner.y, shoulderInner.x, shoulderInner.y)
        close()
    }
    drawPath(arm, fill, alpha = alpha)
    drawPath(arm, edge.copy(alpha = 0.12f * alpha), style = Stroke(7f * zoom))
    drawPath(arm, edge.copy(alpha = 0.74f * alpha), style = Stroke(1.25f * zoom))

    val elbow = p(side * 0.441f, 0.405f, depth * 0.052f)
    drawCircle(Color(0xFFD9EBFF).copy(alpha = 0.20f * alpha), 8f * zoom, elbow)
    drawLine(Color(0xFFD2E7FF).copy(alpha = 0.16f * alpha), p(side * 0.305f, 0.248f, depth * 0.075f), elbow, 1f * zoom)
    drawLine(Color(0xFFD2E7FF).copy(alpha = 0.16f * alpha), elbow, p(side * 0.538f, 0.570f, depth * 0.040f), 1f * zoom)

    val fingertip = p(side * 0.610f, 0.670f, depth * 0.040f)
    repeat(4) { index ->
        val offset = (index - 1.5f) * 0.010f
        drawLine(
            Color(0xFFD6E9FF).copy(alpha = 0.25f * alpha),
            p(side * (0.570f + offset), 0.627f + abs(offset) * 0.30f, depth * 0.030f),
            Offset(fingertip.x + side * offset * 22f, fingertip.y + abs(offset) * 30f),
            0.8f * zoom,
        )
    }
}

private fun DrawScope.drawLeg(
    right: Boolean,
    alpha: Float,
    shape: DigitalTwinShape,
    yaw: Float,
    selected: MeasureTarget?,
    zoom: Float,
    fill: Brush,
    edge: Color,
) {
    val side = if (right) 1f else -1f
    val depth = if (right) -1f else 1f
    fun p(x: Float, y: Float, z: Float = 0f): Offset = project(Vec3(x, y, z), size, yaw, shape, zoom, selected?.marker)

    val hipOuter = p(side * 0.250f, 0.548f, depth * 0.130f)
    val hipInner = p(side * 0.060f, 0.606f, depth * 0.070f)
    val kneeOuter = p(side * 0.190f, 0.760f, depth * 0.075f)
    val kneeInner = p(side * 0.075f, 0.760f, depth * 0.040f)
    val ankleOuter = p(side * 0.145f, 0.935f, depth * 0.050f)
    val ankleInner = p(side * 0.085f, 0.935f, depth * 0.030f)
    val toeOuter = p(side * 0.175f, 0.982f, depth * 0.085f)
    val toeInner = p(side * 0.052f, 0.982f, depth * 0.055f)

    val leg = Path().apply {
        moveTo(hipOuter.x, hipOuter.y)
        cubicTo(
            p(side * 0.245f, 0.650f, depth * 0.110f).x,
            p(side * 0.245f, 0.650f, depth * 0.110f).y,
            kneeOuter.x,
            kneeOuter.y,
            kneeOuter.x,
            kneeOuter.y,
        )
        cubicTo(
            p(side * 0.188f, 0.850f, depth * 0.065f).x,
            p(side * 0.188f, 0.850f, depth * 0.065f).y,
            ankleOuter.x,
            ankleOuter.y,
            toeOuter.x,
            toeOuter.y,
        )
        lineTo(toeInner.x, toeInner.y)
        cubicTo(ankleInner.x, ankleInner.y, p(side * 0.095f, 0.850f, depth * 0.035f).x, p(side * 0.095f, 0.850f, depth * 0.035f).y, kneeInner.x, kneeInner.y)
        cubicTo(kneeInner.x, kneeInner.y, hipInner.x, hipInner.y, hipInner.x, hipInner.y)
        close()
    }
    drawPath(leg, fill, alpha = alpha)
    drawPath(leg, edge.copy(alpha = 0.12f * alpha), style = Stroke(8f * zoom))
    drawPath(leg, edge.copy(alpha = 0.76f * alpha), style = Stroke(1.3f * zoom))

    val knee = p(side * 0.133f, 0.758f, depth * 0.052f)
    drawCircle(Color(0xFFD9EBFF).copy(alpha = 0.22f * alpha), 10f * zoom, knee)
    drawCircle(Color(0xFF87B8E8).copy(alpha = 0.16f * alpha), 5f * zoom, knee)
    drawLine(Color(0xFFD2E7FF).copy(alpha = 0.15f * alpha), p(side * 0.152f, 0.608f, depth * 0.085f), knee, 1f * zoom)
    drawLine(Color(0xFFD2E7FF).copy(alpha = 0.15f * alpha), knee, p(side * 0.113f, 0.932f, depth * 0.040f), 1f * zoom)

    repeat(4) { index ->
        val shift = (index - 1.5f) * 0.017f
        drawLine(
            Color(0xFFD6E9FF).copy(alpha = 0.21f * alpha),
            p(side * (0.105f + shift), 0.965f, depth * 0.055f),
            p(side * (0.125f + shift), 0.987f, depth * 0.074f),
            0.8f * zoom,
        )
    }
}

private fun DrawScope.drawMeasurementGuide(start: Offset, end: Offset, progress: Float) {
    if (progress <= 0f) return
    drawLine(LabBlue.copy(alpha = 0.24f), start, end, 10f, StrokeCap.Round)
    drawLine(LabBlueBright, start, end, 3.2f, StrokeCap.Round)
    drawArrowHead(end, start)
    if (progress > 0.24f) drawArrowHead(start, end)
    drawCircle(Color.White.copy(alpha = 0.90f), 3.1f, start)
    drawCircle(Color.White.copy(alpha = 0.90f), 3.1f, end)
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val ux = dx / length
    val uy = dy / length
    val perpendicularX = -uy
    val perpendicularY = ux
    val back = Offset(tip.x - ux * 18f, tip.y - uy * 18f)
    drawLine(LabBlueBright, tip, Offset(back.x + perpendicularX * 8f, back.y + perpendicularY * 8f), 3f, StrokeCap.Round)
    drawLine(LabBlueBright, tip, Offset(back.x - perpendicularX * 8f, back.y - perpendicularY * 8f), 3f, StrokeCap.Round)
}

@Composable
private fun MeasurementEditor(
    language: String,
    target: MeasureTarget,
    existingCm: Float?,
    onConfirm: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var raw by rememberSaveable(target.name, existingCm) {
        mutableStateOf(existingCm?.let(::formatCm).orEmpty())
    }
    val parsed = raw.replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed in target.minCm..target.maxCm

    Surface(
        modifier = modifier.padding(top = 10.dp, start = 14.dp, end = 14.dp),
        shape = RoundedCornerShape(24.dp),
        color = LabSurfaceRaised.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, LabBlue.copy(alpha = 0.32f)),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        target.label(language),
                        color = LabText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { next -> raw = next.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    modifier = Modifier.weight(1f),
                    suffix = { Text("cm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = raw.isNotBlank() && !valid,
                    shape = RoundedCornerShape(17.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LabText,
                        unfocusedTextColor = LabText,
                        focusedBorderColor = if (valid) LabGreen else LabBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        cursorColor = LabBlue,
                        focusedContainerColor = LabSurface,
                        unfocusedContainerColor = LabSurface,
                        focusedSuffixColor = LabMuted,
                        unfocusedSuffixColor = LabMuted,
                    ),
                )
                Button(
                    onClick = { parsed?.let(onConfirm) },
                    enabled = valid,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LabGreen, contentColor = Color(0xFF052319)),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                }
            }

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(
                    tr(
                        language,
                        "النطاق: ${target.minCm.roundToInt()}–${target.maxCm.roundToInt()} سم",
                        "Range: ${target.minCm.roundToInt()}–${target.maxCm.roundToInt()} cm",
                    ),
                    color = if (raw.isNotBlank() && !valid) Color(0xFFFF8A83) else LabMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (onClear != null) {
                    TextButton(onClick = { onClear(); onClose() }) {
                        Text(tr(language, "حذف القياس", "Clear"), color = Color(0xFFFF8A83))
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
    var raw by rememberSaveable(profile.hasExplicitWeight, profile.weightPounds) {
        mutableStateOf(if (profile.hasExplicitWeight) formatCm(profile.weightKilograms) else "")
    }
    val kg = raw.replace(',', '.').toFloatOrNull()
    val valid = kg != null && kg in 20f..320f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        shape = RoundedCornerShape(26.dp),
        color = LabSurfaceRaised,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tr(language, "الوزن", "Weight"),
                    color = LabText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    tr(language, "يتفاعل حجم الجسم مباشرة", "Body volume reacts immediately"),
                    color = LabMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = raw,
                onValueChange = { next -> raw = next.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                modifier = Modifier.width(118.dp),
                suffix = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                isError = raw.isNotBlank() && !valid,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LabText,
                    unfocusedTextColor = LabText,
                    focusedContainerColor = LabSurface,
                    unfocusedContainerColor = LabSurface,
                    focusedBorderColor = if (valid) LabGreen else LabBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = LabBlue,
                    focusedSuffixColor = LabMuted,
                    unfocusedSuffixColor = LabMuted,
                ),
            )
            Button(
                onClick = { kg?.let { onWeightChanged(it / POUND_TO_KG) } },
                enabled = valid,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LabGreen, contentColor = Color(0xFF052319)),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null)
            }
        }
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float)

private enum class MeasureTarget(
    val point: BodyMeasurePoint?,
    val marker: Vec3,
    val guideStart: Vec3,
    val guideEnd: Vec3,
    val preferredYaw: Float,
    val zoom: Float,
    val minCm: Float,
    val maxCm: Float,
) {
    HEIGHT(null, Vec3(-0.12f, 0.04f, 0.02f), Vec3(-0.22f, 0.985f, 0f), Vec3(-0.22f, 0.030f, 0f), 0f, 1.05f, 90f, 245f),
    NECK(BodyMeasurePoint.NECK, Vec3(-0.095f, 0.167f, 0.055f), Vec3(-0.085f, 0.168f, 0.03f), Vec3(0.085f, 0.168f, -0.03f), 0f, 1.72f, 20f, 70f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, Vec3(-0.31f, 0.225f, 0.08f), Vec3(-0.305f, 0.220f, 0.03f), Vec3(0.305f, 0.220f, -0.03f), 0f, 1.40f, 25f, 80f),
    CHEST(BodyMeasurePoint.CHEST, Vec3(-0.285f, 0.325f, 0.12f), Vec3(-0.270f, 0.325f, 0.08f), Vec3(0.270f, 0.325f, -0.08f), 0f, 1.46f, 50f, 180f),
    WAIST(BodyMeasurePoint.WAIST, Vec3(-0.205f, 0.455f, 0.10f), Vec3(-0.195f, 0.455f, 0.07f), Vec3(0.195f, 0.455f, -0.07f), 0f, 1.48f, 45f, 180f),
    HIPS(BodyMeasurePoint.HIPS, Vec3(-0.260f, 0.555f, 0.12f), Vec3(-0.255f, 0.555f, 0.08f), Vec3(0.255f, 0.555f, -0.08f), 0f, 1.42f, 55f, 190f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, Vec3(-0.430f, 0.405f, 0.06f), Vec3(-0.305f, 0.235f, 0.08f), Vec3(-0.545f, 0.580f, 0.035f), 330f, 1.52f, 35f, 100f),
    WRIST(BodyMeasurePoint.WRIST, Vec3(-0.545f, 0.580f, 0.04f), Vec3(-0.560f, 0.570f, 0.02f), Vec3(-0.515f, 0.588f, -0.01f), 325f, 1.85f, 10f, 35f),
    HAND(BodyMeasurePoint.HAND, Vec3(-0.585f, 0.642f, 0.04f), Vec3(-0.540f, 0.575f, 0.03f), Vec3(-0.610f, 0.670f, 0.03f), 325f, 1.92f, 10f, 30f),
    THIGH(BodyMeasurePoint.THIGH, Vec3(-0.205f, 0.650f, 0.10f), Vec3(-0.215f, 0.655f, 0.07f), Vec3(-0.065f, 0.655f, 0.02f), 8f, 1.58f, 30f, 100f),
    INSEAM(BodyMeasurePoint.INSEAM, Vec3(-0.070f, 0.655f, 0.03f), Vec3(-0.055f, 0.615f, 0.02f), Vec3(-0.110f, 0.935f, 0.02f), 4f, 1.47f, 40f, 120f),
    CALF(BodyMeasurePoint.CALF, Vec3(-0.150f, 0.840f, 0.05f), Vec3(-0.175f, 0.845f, 0.04f), Vec3(-0.085f, 0.845f, 0.01f), 8f, 1.72f, 20f, 70f),
    FOOT(BodyMeasurePoint.FOOT, Vec3(-0.145f, 0.968f, 0.07f), Vec3(-0.070f, 0.978f, 0.055f), Vec3(-0.190f, 0.980f, 0.09f), 45f, 1.82f, 15f, 40f),
    ;

    fun valueCm(profile: BodyProfile): Float? = when (this) {
        HEIGHT -> profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
        else -> point?.let { profile.measurementsInches[it] }?.times(INCH_TO_CM)
    }

    fun label(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "الطول"
        NECK -> "محيط الرقبة"
        SHOULDERS -> "عرض الكتفين"
        CHEST -> "محيط الصدر"
        WAIST -> "محيط الخصر"
        HIPS -> "محيط الورك"
        ARM_LENGTH -> "طول الذراع"
        WRIST -> "محيط المعصم"
        HAND -> "طول اليد"
        THIGH -> "محيط الفخذ"
        INSEAM -> "طول الساق الداخلي"
        CALF -> "محيط الساق"
        FOOT -> "طول القدم"
    } else when (this) {
        HEIGHT -> "Height"
        NECK -> "Neck circumference"
        SHOULDERS -> "Shoulder width"
        CHEST -> "Chest circumference"
        WAIST -> "Waist circumference"
        HIPS -> "Hip circumference"
        ARM_LENGTH -> "Arm length"
        WRIST -> "Wrist circumference"
        HAND -> "Hand length"
        THIGH -> "Thigh circumference"
        INSEAM -> "Inseam"
        CALF -> "Calf circumference"
        FOOT -> "Foot length"
    }

    fun instruction(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "من أعلى الرأس إلى الأرض وأنت واقف بشكل مستقيم."
        NECK -> "مرر شريط القياس حول قاعدة الرقبة دون شد."
        SHOULDERS -> "من نهاية كتف إلى نهاية الكتف الآخر."
        CHEST -> "حول أوسع نقطة في الصدر مع إبقاء الشريط أفقيًا."
        WAIST -> "حول أضيق نقطة طبيعية من الخصر دون ضغط."
        HIPS -> "حول أوسع نقطة من الورك والأرداف."
        ARM_LENGTH -> "من نقطة الكتف إلى عظمة المعصم والذراع مرتخية."
        WRIST -> "حول المعصم مباشرة فوق عظمة اليد."
        HAND -> "من ثنية المعصم إلى نهاية الإصبع الأوسط."
        THIGH -> "حول أوسع نقطة في أعلى الفخذ."
        INSEAM -> "من أعلى داخل الفخذ إلى عظمة الكاحل."
        CALF -> "حول أوسع نقطة من بطة الساق."
        FOOT -> "من مؤخرة الكعب إلى نهاية أطول إصبع."
    } else when (this) {
        HEIGHT -> "Measure from the top of the head to the floor while standing straight."
        NECK -> "Wrap the tape around the base of the neck without tightening it."
        SHOULDERS -> "Measure from one shoulder edge to the other."
        CHEST -> "Wrap the tape around the fullest chest point, keeping it level."
        WAIST -> "Measure around the natural narrowest waist point without compression."
        HIPS -> "Measure around the fullest hip and seat point."
        ARM_LENGTH -> "Measure from the shoulder point to the wrist bone with the arm relaxed."
        WRIST -> "Wrap the tape around the wrist just above the hand."
        HAND -> "Measure from the wrist crease to the tip of the middle finger."
        THIGH -> "Wrap the tape around the fullest upper-thigh point."
        INSEAM -> "Measure from the upper inner thigh to the ankle bone."
        CALF -> "Wrap the tape around the fullest calf point."
        FOOT -> "Measure from the back of the heel to the longest toe."
    }
}

private fun project(
    value: Vec3,
    canvas: Size,
    yaw: Float,
    shape: DigitalTwinShape,
    zoom: Float,
    focus: Vec3?,
): Offset {
    val radians = Math.toRadians(yaw.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()

    fun rotate(v: Vec3): Vec3 {
        val scaledX = v.x * shape.widthScale
        val scaledZ = v.z * shape.depthScale
        return Vec3(
            x = scaledX * cosine + scaledZ * sine,
            y = v.y * shape.heightScale,
            z = -scaledX * sine + scaledZ * cosine,
        )
    }

    val rotated = rotate(value)
    val rotatedFocus = focus?.let(::rotate)
    val perspective = (1f - rotated.z * 0.22f).coerceIn(0.82f, 1.18f)
    val bodyHeight = canvas.height * 0.88f
    val bodyWidth = min(canvas.width * 0.86f, bodyHeight * 0.72f)

    val focusX = rotatedFocus?.x ?: 0f
    val focusY = rotatedFocus?.y ?: 0.50f * shape.heightScale
    val centerY = if (focus == null) canvas.height * 0.49f else canvas.height * 0.47f

    return Offset(
        x = canvas.width * 0.50f + (rotated.x - focusX) * bodyWidth * zoom * perspective,
        y = centerY + (rotated.y - focusY) * bodyHeight * zoom * perspective,
    )
}

private fun nearestYaw(current: Float, desired: Float): Float {
    val normalizedCurrent = ((current % 360f) + 360f) % 360f
    var delta = desired - normalizedCurrent
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return current + delta
}

private fun lerp(start: Vec3, end: Vec3, progress: Float): Vec3 = Vec3(
    x = start.x + (end.x - start.x) * progress,
    y = start.y + (end.y - start.y) * progress,
    z = start.z + (end.z - start.z) * progress,
)

private fun isFrontEnough(yaw: Float): Boolean {
    val normalized = ((yaw % 360f) + 360f) % 360f
    return normalized <= 50f || normalized >= 310f
}

private fun Offset.length(): Float = sqrt(x * x + y * y)

private fun formatCm(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else String.format(Locale.US, "%.1f", value)

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
