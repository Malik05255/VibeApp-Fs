package com.almi.ai.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.JourneyMode
import com.almi.ai.data.preferences.essentialBodyMeasurements
import com.almi.ai.data.preferences.guidedMeasurementOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class Stage { LANGUAGE, JOURNEY, BODY, PHOTO }

private data class HotspotSpec(
    val point: BodyMeasurePoint,
    val x: Float,
    val y: Float,
    val preferredYaw: Float,
)

private val hotspotSpecs = listOf(
    HotspotSpec(BodyMeasurePoint.NECK, 0.50f, 0.18f, 15f),
    HotspotSpec(BodyMeasurePoint.SHOULDERS, 0.38f, 0.25f, 340f),
    HotspotSpec(BodyMeasurePoint.CHEST, 0.50f, 0.32f, 15f),
    HotspotSpec(BodyMeasurePoint.WAIST, 0.50f, 0.43f, 20f),
    HotspotSpec(BodyMeasurePoint.HIPS, 0.50f, 0.51f, 20f),
    HotspotSpec(BodyMeasurePoint.ARM_LENGTH, 0.25f, 0.38f, 330f),
    HotspotSpec(BodyMeasurePoint.WRIST, 0.20f, 0.54f, 325f),
    HotspotSpec(BodyMeasurePoint.HAND, 0.17f, 0.60f, 320f),
    HotspotSpec(BodyMeasurePoint.THIGH, 0.41f, 0.65f, 15f),
    HotspotSpec(BodyMeasurePoint.INSEAM, 0.50f, 0.64f, 0f),
    HotspotSpec(BodyMeasurePoint.CALF, 0.42f, 0.81f, 15f),
    HotspotSpec(BodyMeasurePoint.FOOT, 0.43f, 0.94f, 70f),
)

@Composable
fun AlmiInteractiveOnboardingScreen(
    language: String,
    profile: BodyProfile,
    onLanguageChange: (String) -> Unit,
    onJourneyMode: (JourneyMode) -> Unit,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onComplete: () -> Unit,
) {
    var stageName by rememberSaveable { mutableStateOf(Stage.LANGUAGE.name) }
    val stage = runCatching { Stage.valueOf(stageName) }.getOrDefault(Stage.LANGUAGE)

    BackHandler(enabled = stage != Stage.LANGUAGE) {
        stageName = when (stage) {
            Stage.LANGUAGE -> Stage.LANGUAGE.name
            Stage.JOURNEY -> Stage.LANGUAGE.name
            Stage.BODY, Stage.PHOTO -> Stage.JOURNEY.name
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        GridBackdrop()
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(280), initialScale = 0.985f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(tween(190), targetScale = 1.015f))
            },
            label = "almi-first-run",
        ) { current ->
            when (current) {
                Stage.LANGUAGE -> LanguageStep(
                    onArabic = {
                        onLanguageChange("ar")
                        stageName = Stage.JOURNEY.name
                    },
                    onEnglish = {
                        onLanguageChange("en")
                        stageName = Stage.JOURNEY.name
                    },
                )

                Stage.JOURNEY -> JourneyStep(
                    language = language,
                    onBody = {
                        onJourneyMode(JourneyMode.AVATAR)
                        stageName = Stage.BODY.name
                    },
                    onPhoto = {
                        onJourneyMode(JourneyMode.PHOTO)
                        stageName = Stage.PHOTO.name
                    },
                )

                Stage.BODY -> BodyLiveStep(
                    language = language,
                    profile = profile,
                    onHeightChanged = onHeightChanged,
                    onWeightChanged = onWeightChanged,
                    onMeasurementChanged = onMeasurementChanged,
                    onMeasurementCleared = onMeasurementCleared,
                    onComplete = onComplete,
                )

                Stage.PHOTO -> PhotoStep(language, onComplete)
            }
        }
    }
}

@Composable
private fun GridBackdrop() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        val line = scheme.outlineVariant.copy(alpha = 0.18f)
        val step = size.width / 8f
        var x = 0f
        while (x <= size.width) {
            drawLine(line, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = step
        while (y <= size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        val center = Offset(size.width * 0.5f, size.height * 0.44f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(scheme.primary.copy(alpha = 0.06f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.8f,
            ),
            center = center,
            radius = size.minDimension * 0.8f,
        )
    }
}

@Composable
private fun LanguageStep(onArabic: () -> Unit, onEnglish: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                ScanMark()
                Text("Choose your language", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("اختر لغتك للبدء", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onArabic, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("العربية", fontWeight = FontWeight.Black) }
                OutlinedButton(onClick = onEnglish, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("English", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun ScanMark() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(82.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(scheme.primary.copy(alpha = 0.1f), size.minDimension * 0.46f, c)
        drawCircle(scheme.primary, size.minDimension * 0.31f, c, style = Stroke(2.5f))
        drawLine(scheme.primary, Offset(c.x, 4f), Offset(c.x, size.height - 4f), 2f)
        drawLine(scheme.primary, Offset(4f, c.y), Offset(size.width - 4f, c.y), 2f)
        drawCircle(scheme.error, 5f, c)
    }
}

@Composable
private fun JourneyStep(language: String, onBody: () -> Unit, onPhoto: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ALMI / START", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(tr(language, "كيف تريد أن نعرف جسمك؟", "How should ALMI understand your body?"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(
            tr(language, "اختر المجسم لبناء ملف قياسات تفاعلي، أو انتقل مباشرة بصورتك الشخصية.", "Choose the body model for an interactive measurement profile, or continue directly with your photo."),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceCard(
            code = "BODY / LIVE 360",
            title = tr(language, "مجسم تفاعلي", "Interactive body model"),
            description = tr(language, "المس أجزاء الجسم، دوّر المجسم 360°، واحفظ القياسات مباشرة عليه.", "Touch body regions, rotate 360°, and save measurements directly on the model."),
            action = tr(language, "ابدأ بالمجسم ←", "START BODY MODEL →"),
            emphasized = true,
            onClick = onBody,
        )
        ChoiceCard(
            code = "PHOTO / DIRECT",
            title = tr(language, "صورتي الشخصية", "My personal photo"),
            description = tr(language, "ابدأ بصورتك الكاملة ويمكنك إضافة القياسات لاحقًا.", "Start with your full-body photo and add measurements later."),
            action = tr(language, "استخدم صورتي ←", "USE MY PHOTO →"),
            emphasized = false,
            onClick = onPhoto,
        )
    }
}

@Composable
private fun ChoiceCard(
    code: String,
    title: String,
    description: String,
    action: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.48f) else scheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, if (emphasized) scheme.primary.copy(alpha = 0.52f) else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(code, style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(action, style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BodyLiveStep(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onComplete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var yawTarget by rememberSaveable { mutableStateOf(0f) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var showBasics by rememberSaveable { mutableStateOf(false) }
    val selected = selectedName?.let { runCatching { BodyMeasurePoint.valueOf(it) }.getOrNull() }
    val selectedSpec = selected?.let { point -> hotspotSpecs.firstOrNull { it.point == point } }
    val yaw by animateFloatAsState(yawTarget, tween(420), label = "body-yaw")
    val zoom by animateFloatAsState(if (selected == null) 1f else 1.42f, tween(340), label = "body-zoom")
    val recommended = profile.nextRecommendedMeasurement

    fun select(point: BodyMeasurePoint) {
        val spec = hotspotSpecs.first { it.point == point }
        selectedName = point.name
        yawTarget = nearestYawTarget(yaw, spec.preferredYaw)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("ALMI / BODY LIVE", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                    Text(tr(language, "المجسم التفاعلي", "Interactive body"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (profile.isFitReady) scheme.primaryContainer else scheme.surface,
                    border = BorderStroke(1.dp, if (profile.isFitReady) scheme.primary else scheme.outlineVariant),
                ) {
                    Text(
                        if (profile.isFitReady) tr(language, "جاهز", "FIT READY") else "${profile.essentialCompletedMeasurements}/${essentialBodyMeasurements.size}",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            LinearProgressIndicator(progress = { profile.essentialCompletionFraction }, modifier = Modifier.fillMaxWidth().height(3.dp))

            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(tr(language, "اسحب للدوران • اضغط نقطة", "Drag to rotate • tap a point"), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    Text("${normalize(yaw).roundToInt()}°", style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
                }

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = TransformOrigin(selectedSpec?.x ?: 0.5f, selectedSpec?.y ?: 0.5f)
                        },
                    ) {
                        BodyModel(
                            language = language,
                            yawDegrees = yaw,
                            profile = profile,
                            selected = selected,
                            recommended = recommended,
                            onDrag = { delta ->
                                selectedName = null
                                yawTarget += delta
                            },
                            onSelect = ::select,
                        )
                    }
                    if (selected == null && recommended != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clickable { select(recommended) },
                            shape = RoundedCornerShape(999.dp),
                            color = scheme.surface.copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, scheme.outlineVariant),
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(7.dp).background(scheme.error, CircleShape))
                                Text(tr(language, "القياس التالي: ${pointTitle(recommended, language)}", "Next: ${pointTitle(recommended, language)}"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ViewButton(tr(language, "أمامي", "Front"), Modifier.weight(1f)) { selectedName = null; yawTarget = nearestYawTarget(yaw, 0f) }
                    ViewButton(tr(language, "جانبي", "Side"), Modifier.weight(1f)) { selectedName = null; yawTarget = nearestYawTarget(yaw, 90f) }
                    ViewButton(tr(language, "خلفي", "Back"), Modifier.weight(1f)) { selectedName = null; yawTarget = nearestYawTarget(yaw, 180f) }
                }
            }

            AnimatedVisibility(visible = selected == null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { showBasics = !showBasics }) {
                        Text(if (showBasics) tr(language, "إخفاء الطول والوزن", "Hide height & weight") else tr(language, "تعديل الطول والوزن", "Edit height & weight"))
                    }
                    AnimatedVisibility(visible = showBasics) {
                        BasicsEditor(language, profile, onHeightChanged, onWeightChanged)
                    }
                    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        Text(
                            if (profile.isFitReady) tr(language, "متابعة لتجربة الملابس", "Continue to try-on") else tr(language, "المتابعة وإكمال القياسات لاحقًا", "Continue and finish later"),
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }

        selected?.let { point ->
            MeasurePanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                language = language,
                point = point,
                existingInches = profile.measurementsInches[point],
                essential = point in essentialBodyMeasurements,
                onSave = { inches ->
                    onMeasurementChanged(point, inches)
                    val next = guidedMeasurementOrder.firstOrNull { it != point && it !in profile.measurementsInches }
                    if (next == null) selectedName = null else select(next)
                },
                onClear = if (point in profile.measurementsInches) ({ onMeasurementCleared(point) }) else null,
                onLater = { selectedName = null },
            )
        }
    }
}

@Composable
private fun BasicsEditor(language: String, profile: BodyProfile, onHeightChanged: (Float) -> Unit, onWeightChanged: (Float) -> Unit) {
    var height by remember(profile.heightInches) { mutableStateOf(number(profile.heightInches)) }
    var weight by remember(profile.weightPounds) { mutableStateOf(number(profile.weightPounds)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = height,
            onValueChange = { height = numeric(it, 6); height.toFloatOrNull()?.let(onHeightChanged) },
            label = { Text(tr(language, "الطول (in)", "Height (in)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = numeric(it, 6); weight.toFloatOrNull()?.let(onWeightChanged) },
            label = { Text(tr(language, "الوزن (lb)", "Weight (lb)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BodyModel(
    language: String,
    yawDegrees: Float,
    profile: BodyProfile,
    selected: BodyMeasurePoint?,
    recommended: BodyMeasurePoint?,
    onDrag: (Float) -> Unit,
    onSelect: (BodyMeasurePoint) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val idle = rememberInfiniteTransition(label = "body-idle")
    val breathe by idle.animateFloat(
        initialValue = 0.994f,
        targetValue = 1.007f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "body-breathe",
    )

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectHorizontalDragGestures { _, dragAmount -> onDrag(dragAmount * 0.72f) }
        },
    ) {
        val radians = Math.toRadians(normalize(yawDegrees).toDouble())
        val frontness = abs(cos(radians)).toFloat()
        val side = sin(radians).toFloat()
        val compression = 0.42f + 0.58f * frontness

        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleY = breathe }) {
            val cx = size.width / 2f
            val top = size.height * 0.045f
            val bodyH = size.height * 0.86f
            val weightFactor = (profile.weightPounds / 165f).coerceIn(0.72f, 1.55f)
            val heightFactor = (profile.heightInches / 68f).coerceIn(0.82f, 1.18f)
            val torso = size.width * 0.235f * compression * (0.9f + (weightFactor - 1f) * 0.21f)
            val shoulderY = top + bodyH * 0.22f
            val hipY = top + bodyH * 0.53f
            val crotchY = top + bodyH * 0.59f
            val footY = (top + bodyH * heightFactor).coerceAtMost(size.height * 0.94f)
            val headR = bodyH * 0.06f
            val head = Offset(cx + side * size.width * 0.018f, top + bodyH * 0.09f)
            val body = scheme.onSurface.copy(alpha = 0.88f)
            val far = scheme.onSurfaceVariant.copy(alpha = 0.64f)
            val guide = scheme.outlineVariant.copy(alpha = 0.26f)

            repeat(4) { index ->
                val radius = size.width * (0.17f + index * 0.075f)
                drawOval(guide, Offset(cx - radius, hipY - radius * 0.21f), Size(radius * 2f, radius * 0.42f), style = Stroke(1f))
            }
            drawLine(guide, Offset(cx, 0f), Offset(cx, size.height), 1f)
            drawCircle(body, headR, head)
            drawLine(body, Offset(cx, head.y + headR * 0.84f), Offset(cx, shoulderY - bodyH * 0.018f), bodyH * 0.05f, StrokeCap.Round)
            drawRoundRect(body, Offset(cx - torso, shoulderY), Size(torso * 2f, bodyH * 0.35f), CornerRadius(torso * 0.38f))
            drawOval(body, Offset(cx - torso * 0.93f, hipY - bodyH * 0.035f), Size(torso * 1.86f, bodyH * 0.12f))

            val shoulderSpan = torso * 0.92f
            val armStroke = bodyH * (0.05f + weightFactor * 0.009f)
            drawLine(body, Offset(cx - shoulderSpan, shoulderY + bodyH * 0.025f), Offset(cx - shoulderSpan * 1.48f + side * size.width * 0.02f, hipY + bodyH * 0.10f), armStroke, StrokeCap.Round)
            drawLine(far, Offset(cx + shoulderSpan, shoulderY + bodyH * 0.025f), Offset(cx + shoulderSpan * 1.48f + side * size.width * 0.02f, hipY + bodyH * 0.10f), armStroke, StrokeCap.Round)

            val legGap = torso * 0.34f
            val legStroke = bodyH * (0.068f + weightFactor * 0.012f)
            drawLine(body, Offset(cx - legGap, crotchY), Offset(cx - legGap * 0.88f + side * size.width * 0.012f, footY - bodyH * 0.035f), legStroke, StrokeCap.Round)
            drawLine(far, Offset(cx + legGap, crotchY), Offset(cx + legGap * 0.88f + side * size.width * 0.012f, footY - bodyH * 0.035f), legStroke, StrokeCap.Round)
            drawLine(body, Offset(cx - legGap * 0.88f, footY - bodyH * 0.025f), Offset(cx - legGap * 0.88f - torso * 0.34f, footY), bodyH * 0.03f, StrokeCap.Round)
            drawLine(far, Offset(cx + legGap * 0.88f, footY - bodyH * 0.025f), Offset(cx + legGap * 0.88f + torso * 0.34f, footY), bodyH * 0.03f, StrokeCap.Round)

            selected?.let { point ->
                val spec = hotspotSpecs.first { it.point == point }
                val y = size.height * spec.y
                if (point in listOf(BodyMeasurePoint.ARM_LENGTH, BodyMeasurePoint.INSEAM, BodyMeasurePoint.FOOT, BodyMeasurePoint.SHOULDERS)) {
                    val x = size.width * (0.5f + (spec.x - 0.5f) * compression)
                    drawLine(scheme.error.copy(alpha = 0.65f), Offset(x, y - size.height * 0.07f), Offset(x, y + size.height * 0.07f), 3f, StrokeCap.Round)
                } else {
                    drawOval(scheme.error.copy(alpha = 0.65f), Offset(cx - torso * 1.12f, y - bodyH * 0.018f), Size(torso * 2.24f, bodyH * 0.036f), style = Stroke(3f))
                }
            }
        }

        hotspotSpecs.forEach { spec ->
            val xFraction = 0.5f + (spec.x - 0.5f) * compression
            Hotspot(
                language = language,
                point = spec.point,
                measured = profile.measurementsInches[spec.point],
                active = selected == spec.point,
                recommended = recommended == spec.point,
                modifier = Modifier.offset(x = maxWidth * xFraction - 15.dp, y = maxHeight * spec.y - 15.dp),
                onClick = { onSelect(spec.point) },
            )
        }
    }
}

@Composable
private fun Hotspot(
    language: String,
    point: BodyMeasurePoint,
    measured: Float?,
    active: Boolean,
    recommended: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "point-${point.name}")
    val pulse by motion.animateFloat(0.9f, 1.16f, infiniteRepeatable(tween(760), RepeatMode.Reverse), label = "point-pulse-${point.name}")
    Row(modifier.clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(30.dp).graphicsLayer {
                if (active || recommended || measured == null) { scaleX = pulse; scaleY = pulse }
            },
            contentAlignment = Alignment.Center,
        ) {
            if (active) Box(Modifier.size(28.dp).background(scheme.error.copy(alpha = 0.13f), CircleShape))
            if (recommended && !active) Box(Modifier.size(22.dp).background(scheme.error.copy(alpha = 0.13f), CircleShape))
            Box(Modifier.size(if (active) 10.dp else 8.dp).background(if (measured == null) scheme.error else scheme.primary, CircleShape))
        }
        AnimatedVisibility(visible = active || measured != null) {
            Surface(shape = RoundedCornerShape(999.dp), color = scheme.surface.copy(alpha = 0.96f), border = BorderStroke(1.dp, if (active) scheme.error.copy(alpha = 0.45f) else scheme.outlineVariant)) {
                Text(
                    if (measured != null) "${number(measured)} in" else pointTitle(point, language),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun MeasurePanel(
    modifier: Modifier,
    language: String,
    point: BodyMeasurePoint,
    existingInches: Float?,
    essential: Boolean,
    onSave: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onLater: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var cm by rememberSaveable(point.name) { mutableStateOf(false) }
    var value by remember(point, existingInches, cm) { mutableStateOf(existingInches?.let { number(if (cm) it * 2.54f else it) }.orEmpty()) }
    val entered = value.toFloatOrNull()

    Surface(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(pointTitle(point, language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(if (essential) tr(language, "قياس أساسي", "Essential measurement") else tr(language, "قياس إضافي", "Optional refinement"), style = MaterialTheme.typography.labelSmall, color = if (essential) scheme.primary else scheme.onSurfaceVariant)
                }
                TextButton(onClick = onLater) { Text(tr(language, "لاحقًا", "Later")) }
            }
            Text(pointInstruction(point, language), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            MiniRuler(cm)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                UnitButton("in", !cm, Modifier.weight(1f)) {
                    if (cm) { value = value.toFloatOrNull()?.let { number(it / 2.54f) }.orEmpty(); cm = false }
                }
                UnitButton("cm", cm, Modifier.weight(1f)) {
                    if (!cm) { value = value.toFloatOrNull()?.let { number(it * 2.54f) }.orEmpty(); cm = true }
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = numeric(it, 7) },
                label = { Text(tr(language, "اكتب القياس", "Enter measurement")) },
                supportingText = { Text(if (cm) "cm" else "in") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onClear?.let { clear ->
                    OutlinedButton(onClick = clear, modifier = Modifier.weight(0.44f).height(50.dp)) { Text(tr(language, "حذف", "Clear")) }
                }
                Button(
                    enabled = entered != null && entered > 0f,
                    onClick = { entered?.let { onSave(if (cm) it / 2.54f else it) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) { Text(tr(language, "حفظ والقياس التالي", "Save & next"), fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
private fun MiniRuler(cm: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(10.dp), color = scheme.surfaceVariant.copy(alpha = 0.48f)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            val divisions = if (cm) 20 else 16
            val spacing = size.width / divisions.toFloat()
            repeat(divisions + 1) { index ->
                val major = index % 4 == 0
                val tick = if (major) size.height * 0.78f else size.height * 0.44f
                val x = index * spacing
                drawLine(scheme.onSurface.copy(alpha = if (major) 0.78f else 0.36f), Offset(x, size.height), Offset(x, size.height - tick), if (major) 2f else 1f)
            }
            drawLine(scheme.error.copy(alpha = 0.72f), Offset(size.width * 0.5f, 0f), Offset(size.width * 0.5f, size.height), 2f)
        }
    }
}

@Composable
private fun UnitButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(10.dp), color = if (selected) scheme.primaryContainer else scheme.surface, border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant)) {
        Text(label, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ViewButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(40.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PhotoStep(language: String, onComplete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("ALMI / PHOTO", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        Text(tr(language, "استخدم صورتك مباشرة", "Use your photo directly"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text(tr(language, "اختر صورة واضحة وكاملة للجسم داخل الاستوديو. يمكنك العودة للمجسم التفاعلي من الإعدادات لاحقًا.", "Choose a clear full-body photo inside the studio. You can return to the interactive body model from Settings later."), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(tr(language, "فتح الاستوديو", "Open studio"), fontWeight = FontWeight.Black) }
    }
}

private fun pointTitle(point: BodyMeasurePoint, language: String): String = when (point) {
    BodyMeasurePoint.NECK -> tr(language, "الرقبة", "Neck")
    BodyMeasurePoint.SHOULDERS -> tr(language, "الكتفين", "Shoulders")
    BodyMeasurePoint.CHEST -> tr(language, "الصدر", "Chest")
    BodyMeasurePoint.WAIST -> tr(language, "الخصر", "Waist")
    BodyMeasurePoint.HIPS -> tr(language, "الورك", "Hips")
    BodyMeasurePoint.ARM_LENGTH -> tr(language, "طول الذراع", "Arm length")
    BodyMeasurePoint.WRIST -> tr(language, "المعصم", "Wrist")
    BodyMeasurePoint.HAND -> tr(language, "اليد", "Hand")
    BodyMeasurePoint.THIGH -> tr(language, "الفخذ", "Thigh")
    BodyMeasurePoint.INSEAM -> tr(language, "طول الساق الداخلي", "Inseam")
    BodyMeasurePoint.CALF -> tr(language, "الساق", "Calf")
    BodyMeasurePoint.FOOT -> tr(language, "القدم", "Foot")
}

private fun pointInstruction(point: BodyMeasurePoint, language: String): String = when (point) {
    BodyMeasurePoint.NECK -> tr(language, "لف الشريط حول قاعدة الرقبة بدون شد.", "Wrap the tape around the base of the neck without tightening.")
    BodyMeasurePoint.SHOULDERS -> tr(language, "قس من نهاية كتف إلى نهاية الكتف الآخر عبر أعلى الظهر.", "Measure from one shoulder tip to the other across the upper back.")
    BodyMeasurePoint.CHEST -> tr(language, "لف الشريط حول أعرض نقطة من الصدر وأبقه أفقيًا.", "Wrap the tape around the fullest part of the chest and keep it level.")
    BodyMeasurePoint.WAIST -> tr(language, "قس حول الخصر الطبيعي عند أضيق نقطة بدون ضغط.", "Measure around the natural waist at its narrowest point without compression.")
    BodyMeasurePoint.HIPS -> tr(language, "لف الشريط حول أعرض نقطة من الورك والمؤخرة.", "Wrap the tape around the fullest part of the hips and seat.")
    BodyMeasurePoint.ARM_LENGTH -> tr(language, "ابدأ من الكتف، مر فوق كوع مثني قليلًا، وانته عند المعصم.", "Start at the shoulder, pass over a slightly bent elbow, and end at the wrist.")
    BodyMeasurePoint.WRIST -> tr(language, "لف الشريط حول عظمة المعصم بدون ضغط.", "Wrap the tape around the wrist bone without compression.")
    BodyMeasurePoint.HAND -> tr(language, "قس حول أعرض جزء من اليد عند مفاصل الأصابع مع استثناء الإبهام.", "Measure around the widest part of the hand at the knuckles, excluding the thumb.")
    BodyMeasurePoint.THIGH -> tr(language, "قس حول أعرض جزء من أعلى الفخذ.", "Measure around the fullest part of the upper thigh.")
    BodyMeasurePoint.INSEAM -> tr(language, "قس من أعلى نقطة داخل الساق عند المنشعب إلى الأرض.", "Measure from the top of the inner leg at the crotch to the floor.")
    BodyMeasurePoint.CALF -> tr(language, "لف الشريط حول أعرض نقطة من عضلة الساق.", "Wrap the tape around the fullest part of the calf.")
    BodyMeasurePoint.FOOT -> tr(language, "قس من مؤخرة الكعب إلى نهاية أطول إصبع.", "Measure from the back of the heel to the longest toe.")
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
private fun number(value: Float): String = if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString() else "%.1f".format(Locale.US, value)
private fun numeric(value: String, maxLength: Int): String = value.filter { it.isDigit() || it == '.' }.take(maxLength)
private fun normalize(value: Float): Float { var result = value % 360f; if (result < 0f) result += 360f; return result }
private fun nearestYawTarget(current: Float, preferred: Float): Float {
    val currentNormalized = normalize(current)
    var delta = preferred - currentNormalized
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return current + delta
}
