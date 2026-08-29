package com.almi.ai.ui.body

import android.graphics.Paint
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

private val LabBackground = Color(0xFF07111F)
private val LabSurface = Color(0xFF0D1A2B)
private val LabSurfaceRaised = Color(0xFF12233A)
private val LabText = Color(0xFFF4F8FF)
private val LabMuted = Color(0xFF8FA5C2)
private val LabBlue = Color(0xFF80B8FF)
private val LabRed = Color(0xFFFF4D43)
private val LabGreen = Color(0xFF58D6A7)
private const val INCH_TO_CM = 2.54f
private const val POUND_TO_KG = 0.45359237f

/**
 * Stable ALMI body-map implementation.
 *
 * This screen intentionally uses only Jetpack Compose Canvas. There is no Filament, SceneView,
 * GLB loading, JNI or native 3D renderer in this route. The body still rotates continuously through
 * a projected 360-degree model, zooms to selected measurement areas and reacts to entered body
 * dimensions, while removing the native renderer crash surface entirely.
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
            delay(180)
            guideReady = true
        }
    }

    val solved = remember(profile) { BodyShapeSolver.solve(profile) }
    val width by animateFloatAsState(solved.widthScale, tween(360), label = "body-width")
    val height by animateFloatAsState(solved.heightScale, tween(360), label = "body-height")
    val depth by animateFloatAsState(solved.depthScale, tween(360), label = "body-depth")
    val yaw by animateFloatAsState(targetYaw, tween(280), label = "body-yaw")
    val focusScale by animateFloatAsState(if (selected == null) 1f else 1.72f, tween(420), label = "body-focus")
    val guideProgress by animateFloatAsState(if (guideReady) 1f else 0f, tween(620), label = "guide")

    fun open(target: MeasureTarget) {
        selectedName = target.name
        targetYaw = nearestYaw(yaw, target.focusYaw)
    }

    fun close() {
        selectedName = null
        targetYaw = nearestYaw(yaw, 0f)
    }

    val totalFacts = MeasureTarget.entries.size + 1
    val completedFacts = MeasureTarget.entries.count { it.valueCm(profile) != null } +
        if (profile.hasExplicitWeight) 1 else 0

    Box(modifier.fillMaxSize().background(LabBackground)) {
        Column(Modifier.fillMaxSize()) {
            LabHeader(language, completedFacts, totalFacts, onComplete)
            LinearProgressIndicator(
                progress = { completedFacts.toFloat() / totalFacts.toFloat() },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = LabBlue,
                trackColor = Color.White.copy(alpha = 0.08f),
            )

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                StableBodyViewport(
                    profile = profile,
                    selected = selected,
                    shape = solved.copy(widthScale = width, heightScale = height, depthScale = depth),
                    yaw = yaw,
                    focusScale = focusScale,
                    guideProgress = guideProgress,
                    onYawChanged = { delta ->
                        if (selected == null) targetYaw += delta
                    },
                    onSelected = ::open,
                    modifier = Modifier.fillMaxSize(),
                )

                if (selected == null) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = LabSurface.copy(alpha = 0.92f),
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
                                    onHeightChanged(centimeters / INCH_TO_CM)
                                } else {
                                    target.point?.let { point -> onMeasurementChanged(point, centimeters / INCH_TO_CM) }
                                }
                                close()
                            },
                            onClear = target.point
                                ?.takeIf { it in profile.measurementsInches }
                                ?.let { point -> ({ onMeasurementCleared(point) }) },
                            onClose = ::close,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }

            WeightDock(language, profile, onWeightChanged)
        }
    }
}

@Composable
private fun LabHeader(language: String, completed: Int, total: Int, onDone: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("$completed/$total", color = LabMuted, style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onDone) {
                Text(tr(language, "تم", "Done"), color = LabText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StableBodyViewport(
    profile: BodyProfile,
    selected: MeasureTarget?,
    shape: DigitalTwinShape,
    yaw: Float,
    focusScale: Float,
    guideProgress: Float,
    onYawChanged: (Float) -> Unit,
    onSelected: (MeasureTarget) -> Unit,
    modifier: Modifier,
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val pulseTransition = rememberInfiniteTransition(label = "stable-hotspot-pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(980), RepeatMode.Reverse),
        label = "pulse",
    )
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LabText.toArgb()
            textSize = 28f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    val gestureModifier = Modifier
        .onSizeChanged { viewportSize = it }
        .pointerInput(selected, shape, yaw, viewportSize) {
            detectTapGestures { tap ->
                if (selected != null || viewportSize.width <= 0 || viewportSize.height <= 0) return@detectTapGestures
                val size = Size(viewportSize.width.toFloat(), viewportSize.height.toFloat())
                val nearest = MeasureTarget.entries
                    .map { target -> target to projected(target.marker, size, yaw, shape, 1f, null) }
                    .minByOrNull { (_, position) -> (position - tap).getDistance() }
                if (nearest != null && (nearest.second - tap).getDistance() <= 48f) onSelected(nearest.first)
            }
        }
        .pointerInput(selected) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                if (selected == null) onYawChanged(dragAmount.x * 0.72f)
            }
        }

    Box(modifier.then(gestureModifier)) {
        Canvas(Modifier.fillMaxSize()) {
            drawBodyGrid()
            drawProjectedHuman(shape, yaw, selected, focusScale)

            MeasureTarget.entries.forEach { target ->
                val p = projected(target.marker, size, yaw, shape, focusScale, selected?.marker)
                val value = target.valueCm(profile)
                val active = target == selected
                val halo = when {
                    active -> 18f + pulse * 7f
                    value != null -> 13f + pulse * 4f
                    else -> 11f + pulse * 3f
                }
                drawCircle(LabRed.copy(alpha = 0.13f), radius = halo, center = p)
                drawCircle(LabRed.copy(alpha = 0.35f), radius = halo * 0.62f, center = p)
                drawCircle(LabRed, radius = if (active) 6.8f else 5.2f, center = p)

                if (value != null && selected == null) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "${formatCm(value)} cm",
                        p.x + 15f,
                        p.y - 10f,
                        labelPaint,
                    )
                }
            }

            selected?.let { target ->
                val startRaw = target.guideStart
                val endRaw = target.guideEnd
                val animated = Vec3(
                    x = startRaw.x + (endRaw.x - startRaw.x) * guideProgress,
                    y = startRaw.y + (endRaw.y - startRaw.y) * guideProgress,
                    z = startRaw.z + (endRaw.z - startRaw.z) * guideProgress,
                )
                val start = projected(startRaw, size, yaw, shape, focusScale, selected.marker)
                val end = projected(animated, size, yaw, shape, focusScale, selected.marker)
                drawLine(LabBlue, start, end, strokeWidth = 4f, cap = StrokeCap.Round)
                drawCircle(LabBlue, 6f, start)
                drawCircle(LabBlue, 7f, end)
                drawArrowHead(start, end)
            }
        }

        if (selected == null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = LabSurface.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
            ) {
                Text(
                    "360°  •  DRAG",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = LabMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun DrawScope.drawBodyGrid() {
    val step = size.width / 7f
    var x = 0f
    while (x <= size.width) {
        drawLine(Color.White.copy(alpha = 0.025f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(Color.White.copy(alpha = 0.022f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
}

private fun DrawScope.drawProjectedHuman(
    shape: DigitalTwinShape,
    yaw: Float,
    selected: MeasureTarget?,
    focusScale: Float,
) {
    fun p(x: Float, y: Float, z: Float = 0f): Offset =
        projected(Vec3(x, y, z), size, yaw, shape, focusScale, selected?.marker)

    val radians = Math.toRadians(yaw.toDouble())
    val front = abs(cos(radians)).toFloat()
    val side = abs(sin(radians)).toFloat()
    val bodyAlpha = 0.84f + front * 0.12f
    val bodyBrush = Brush.horizontalGradient(
        listOf(
            Color(0xFF7D91B0).copy(alpha = 0.48f),
            Color(0xFFF1F5FB).copy(alpha = bodyAlpha),
            Color(0xFF9DAFC8).copy(alpha = 0.62f),
        )
    )
    val outline = Color(0xFFD9E5F5).copy(alpha = 0.48f)

    val head = p(0f, 0.085f)
    val headWidth = size.width * (0.060f * front + 0.045f * side) * focusScale
    val headHeight = size.height * 0.095f * shape.heightScale * focusScale
    drawOval(
        brush = bodyBrush,
        topLeft = Offset(head.x - headWidth, head.y - headHeight * 0.50f),
        size = Size(headWidth * 2f, headHeight),
    )
    drawOval(
        color = outline,
        topLeft = Offset(head.x - headWidth, head.y - headHeight * 0.50f),
        size = Size(headWidth * 2f, headHeight),
        style = Stroke(1.6f),
    )

    val shoulderL = p(-0.29f, 0.205f, 0.01f)
    val shoulderR = p(0.29f, 0.205f, -0.01f)
    val waistR = p(0.20f, 0.47f)
    val hipR = p(0.245f, 0.555f)
    val hipL = p(-0.245f, 0.555f)
    val waistL = p(-0.20f, 0.47f)
    val torso = Path().apply {
        moveTo(shoulderL.x, shoulderL.y)
        cubicTo(p(-0.33f, 0.29f).x, p(-0.33f, 0.29f).y, waistL.x, waistL.y, hipL.x, hipL.y)
        lineTo(hipR.x, hipR.y)
        cubicTo(waistR.x, waistR.y, p(0.33f, 0.29f).x, p(0.33f, 0.29f).y, shoulderR.x, shoulderR.y)
        close()
    }
    drawPath(torso, bodyBrush)
    drawPath(torso, outline, style = Stroke(1.6f))

    val neckL = p(-0.08f, 0.145f)
    val neckR = p(0.08f, 0.145f)
    drawLine(outline.copy(alpha = 0.7f), neckL, shoulderL, 9f * focusScale, StrokeCap.Round)
    drawLine(outline.copy(alpha = 0.7f), neckR, shoulderR, 9f * focusScale, StrokeCap.Round)

    val leftElbow = p(-0.47f, 0.39f, 0.025f)
    val leftHand = p(-0.56f, 0.59f, 0.045f)
    val rightElbow = p(0.47f, 0.39f, -0.025f)
    val rightHand = p(0.56f, 0.59f, -0.045f)
    val nearRight = sin(radians) <= 0
    drawLimb(shoulderL, leftElbow, leftHand, bodyBrush, outline, focusScale, if (nearRight) 0.62f else 0.94f)
    drawLimb(shoulderR, rightElbow, rightHand, bodyBrush, outline, focusScale, if (nearRight) 0.94f else 0.62f)

    val crotch = p(0f, 0.59f)
    val leftKnee = p(-0.13f, 0.76f, 0.018f)
    val leftFoot = p(-0.14f, 0.96f, 0.035f)
    val rightKnee = p(0.13f, 0.76f, -0.018f)
    val rightFoot = p(0.14f, 0.96f, -0.035f)
    drawLimb(Offset(crotch.x - 10f * focusScale, crotch.y), leftKnee, leftFoot, bodyBrush, outline, focusScale, if (nearRight) 0.76f else 0.96f, leg = true)
    drawLimb(Offset(crotch.x + 10f * focusScale, crotch.y), rightKnee, rightFoot, bodyBrush, outline, focusScale, if (nearRight) 0.96f else 0.76f, leg = true)

    val groundCenter = p(0f, 0.985f)
    drawOval(
        Color.Black.copy(alpha = 0.24f),
        topLeft = Offset(groundCenter.x - 90f * focusScale, groundCenter.y - 8f),
        size = Size(180f * focusScale, 18f * focusScale),
    )
}

private fun DrawScope.drawLimb(
    start: Offset,
    mid: Offset,
    end: Offset,
    brush: Brush,
    outline: Color,
    scale: Float,
    alpha: Float,
    leg: Boolean = false,
) {
    val thickness = (if (leg) 30f else 22f) * scale
    drawLine(outline.copy(alpha = alpha * 0.58f), start, mid, thickness + 4f, StrokeCap.Round)
    drawLine(outline.copy(alpha = alpha * 0.58f), mid, end, thickness * 0.82f + 4f, StrokeCap.Round)
    drawLine(brush = brush, start = start, end = mid, strokeWidth = thickness, cap = StrokeCap.Round, alpha = alpha)
    drawLine(brush = brush, start = mid, end = end, strokeWidth = thickness * 0.82f, cap = StrokeCap.Round, alpha = alpha)
}

private fun DrawScope.drawArrowHead(start: Offset, end: Offset) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val ux = dx / len
    val uy = dy / len
    val px = -uy
    val py = ux
    val back = 17f
    val side = 8f
    val a = Offset(end.x - ux * back + px * side, end.y - uy * back + py * side)
    val b = Offset(end.x - ux * back - px * side, end.y - uy * back - py * side)
    drawLine(LabBlue, end, a, 4f, StrokeCap.Round)
    drawLine(LabBlue, end, b, 4f, StrokeCap.Round)
}

private fun projected(
    point: Vec3,
    size: Size,
    yaw: Float,
    shape: DigitalTwinShape,
    focusScale: Float,
    focusPoint: Vec3?,
): Offset {
    val radians = Math.toRadians(yaw.toDouble())
    val c = cos(radians).toFloat()
    val s = sin(radians).toFloat()
    val frontWidth = shape.widthScale
    val depthWidth = shape.depthScale * 0.72f
    val rotatedX = point.x * c * frontWidth - point.z * s * depthWidth
    val centerX = size.width * 0.5f
    val top = size.height * 0.075f
    val bodyHeight = size.height * 0.82f * shape.heightScale.coerceIn(0.82f, 1.18f)
    val raw = Offset(
        centerX + rotatedX * size.width * 0.46f,
        top + point.y * bodyHeight,
    )
    if (focusPoint == null || focusScale == 1f) return raw
    val focusRaw = projected(focusPoint, size, yaw, shape, 1f, null)
    val target = Offset(size.width * 0.5f, size.height * 0.56f)
    return Offset(
        target.x + (raw.x - focusRaw.x) * focusScale,
        target.y + (raw.y - focusRaw.y) * focusScale,
    )
}

@Composable
private fun MeasurementInputCard(
    language: String,
    target: MeasureTarget,
    existingCm: Float?,
    onConfirm: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var raw by remember(target, existingCm) { mutableStateOf(existingCm?.let(::formatCm).orEmpty()) }
    var attempted by remember(target) { mutableStateOf(false) }
    val value = raw.replace(',', '.').toFloatOrNull()
    val valid = value?.let(target::validCm) == true

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        shape = RoundedCornerShape(22.dp),
        color = LabSurfaceRaised.copy(alpha = 0.985f),
        border = BorderStroke(1.dp, if (attempted && !valid) LabRed.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.10f)),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(target.title(language), color = LabText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            OutlinedTextField(
                value = raw,
                onValueChange = { next -> raw = next.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tr(language, "القياس بالسنتيمتر", "Measurement in cm")) },
                suffix = { Text("cm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LabText,
                    unfocusedTextColor = LabText,
                    focusedBorderColor = if (attempted && !valid) LabRed else LabBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                    focusedLabelColor = LabBlue,
                    unfocusedLabelColor = LabMuted,
                    cursorColor = LabBlue,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        attempted = true
                        if (valid && value != null) onConfirm(value)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (valid) LabGreen else LabBlue),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(tr(language, "حفظ", "Save"), fontWeight = FontWeight.Bold)
                }
                if (onClear != null) {
                    TextButton(onClick = { onClear(); onClose() }, modifier = Modifier.height(48.dp)) {
                        Text(tr(language, "مسح", "Clear"), color = LabMuted)
                    }
                }
            }
            if (attempted && !valid) {
                Text(
                    tr(language, "أدخل رقمًا منطقيًا لهذا القياس.", "Enter a realistic number for this measurement."),
                    color = LabRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun WeightDock(language: String, profile: BodyProfile, onWeightChanged: (Float) -> Unit) {
    var raw by remember(profile.weightPounds, profile.hasExplicitWeight) {
        mutableStateOf(if (profile.hasExplicitWeight) formatCm(profile.weightPounds * POUND_TO_KG) else "")
    }
    val kg = raw.replace(',', '.').toFloatOrNull()
    val valid = kg != null && kg in 20f..320f

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = LabSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr(language, "الوزن", "Weight"), color = LabText, fontWeight = FontWeight.Bold)
                Text(
                    tr(language, "يتفاعل حجم الجسم مباشرة", "Body volume reacts immediately"),
                    color = LabMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = raw,
                onValueChange = { next -> raw = next.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                modifier = Modifier.weight(0.72f),
                singleLine = true,
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = LabText,
                    unfocusedTextColor = LabText,
                    focusedBorderColor = LabBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                    cursorColor = LabBlue,
                ),
            )
            IconButton(
                onClick = { if (valid && kg != null) onWeightChanged(kg / POUND_TO_KG) },
                enabled = valid,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = if (valid) LabGreen else LabMuted)
            }
        }
    }
}

private data class Vec3(val x: Float, val y: Float, val z: Float = 0f)

private enum class MeasureTarget(
    val point: BodyMeasurePoint?,
    val marker: Vec3,
    val guideStart: Vec3,
    val guideEnd: Vec3,
    val focusYaw: Float,
    val minCm: Float,
    val maxCm: Float,
    val ar: String,
    val en: String,
    val arInstruction: String,
    val enInstruction: String,
) {
    HEIGHT(null, Vec3(-0.48f, 0.48f), Vec3(-0.48f, 0.03f), Vec3(-0.48f, 0.97f), 0f, 90f, 240f, "الطول", "Height", "من أعلى الرأس إلى أسفل القدم.", "From the top of the head to the floor."),
    NECK(BodyMeasurePoint.NECK, Vec3(0.13f, 0.16f), Vec3(-0.10f, 0.16f), Vec3(0.10f, 0.16f), 0f, 20f, 70f, "محيط الرقبة", "Neck", "لف شريط القياس حول قاعدة الرقبة.", "Measure around the base of the neck."),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, Vec3(0.31f, 0.21f), Vec3(-0.29f, 0.21f), Vec3(0.29f, 0.21f), 0f, 25f, 80f, "عرض الكتفين", "Shoulders", "من نهاية كتف إلى نهاية الكتف الآخر.", "From one shoulder edge to the other."),
    CHEST(BodyMeasurePoint.CHEST, Vec3(0.30f, 0.31f), Vec3(-0.30f, 0.31f), Vec3(0.30f, 0.31f), 0f, 45f, 180f, "محيط الصدر", "Chest", "حول أعرض نقطة في الصدر.", "Around the fullest part of the chest."),
    WAIST(BodyMeasurePoint.WAIST, Vec3(0.25f, 0.45f), Vec3(-0.25f, 0.45f), Vec3(0.25f, 0.45f), 0f, 40f, 180f, "محيط الخصر", "Waist", "حول الخصر الطبيعي بدون شد الشريط.", "Around the natural waist without pulling tight."),
    HIPS(BodyMeasurePoint.HIPS, Vec3(0.29f, 0.55f), Vec3(-0.29f, 0.55f), Vec3(0.29f, 0.55f), 0f, 50f, 190f, "محيط الورك", "Hips", "حول أعرض نقطة في الورك.", "Around the fullest part of the hips."),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, Vec3(0.53f, 0.43f), Vec3(0.28f, 0.22f), Vec3(0.56f, 0.59f), -12f, 35f, 90f, "طول الذراع", "Arm length", "من بداية الكتف إلى نهاية الرسغ.", "From the shoulder point to the wrist."),
    WRIST(BodyMeasurePoint.WRIST, Vec3(0.57f, 0.56f), Vec3(0.49f, 0.56f), Vec3(0.60f, 0.56f), -16f, 10f, 35f, "محيط الرسغ", "Wrist", "لف الشريط حول مفصل الرسغ.", "Measure around the wrist joint."),
    HAND(BodyMeasurePoint.HAND, Vec3(0.59f, 0.61f), Vec3(0.53f, 0.57f), Vec3(0.61f, 0.63f), -18f, 12f, 30f, "طول اليد", "Hand length", "من بداية الكف عند الرسغ إلى نهاية أطول إصبع.", "From the wrist crease to the tip of the longest finger."),
    THIGH(BodyMeasurePoint.THIGH, Vec3(0.20f, 0.67f), Vec3(0.08f, 0.66f), Vec3(0.27f, 0.66f), 8f, 25f, 100f, "محيط الفخذ", "Thigh", "حول أعرض نقطة في أعلى الفخذ.", "Around the fullest part of the upper thigh."),
    INSEAM(BodyMeasurePoint.INSEAM, Vec3(0.04f, 0.76f), Vec3(0.03f, 0.58f), Vec3(0.03f, 0.95f), 0f, 45f, 110f, "طول الساق الداخلي", "Inseam", "من أعلى الفخذ الداخلي إلى الأرض.", "From the inner crotch seam down to the floor."),
    CALF(BodyMeasurePoint.CALF, Vec3(0.18f, 0.83f), Vec3(0.10f, 0.83f), Vec3(0.25f, 0.83f), 8f, 20f, 70f, "محيط الساق", "Calf", "حول أعرض نقطة في بطة الساق.", "Around the widest part of the calf."),
    FOOT(BodyMeasurePoint.FOOT, Vec3(0.19f, 0.96f), Vec3(0.08f, 0.96f), Vec3(0.28f, 0.96f), 8f, 15f, 40f, "طول القدم", "Foot length", "من مؤخرة الكعب إلى نهاية أطول إصبع.", "From the back of the heel to the longest toe."),
    ;

    fun title(language: String): String = if (language == "ar") ar else en
    fun instruction(language: String): String = if (language == "ar") arInstruction else enInstruction
    fun validCm(value: Float): Boolean = value.isFinite() && value in minCm..maxCm

    fun valueCm(profile: BodyProfile): Float? {
        if (this == HEIGHT) return profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
        return point?.let { profile.measurementsInches[it] }?.times(INCH_TO_CM)
    }
}

private fun nearestYaw(current: Float, preferred: Float): Float {
    val normalizedCurrent = ((current % 360f) + 360f) % 360f
    var delta = preferred - normalizedCurrent
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return current + delta
}

private fun formatCm(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else String.format(Locale.US, "%.1f", value)

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
