package com.almi.ai.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.draw.clip
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

private enum class InteractiveIntroStage { LANGUAGE, JOURNEY, BODY, PHOTO }

private data class InteractiveBodyPoint(
    val point: BodyMeasurePoint,
    val x: Float,
    val y: Float,
    val preferredYaw: Float,
)

private val interactiveBodyPoints = listOf(
    InteractiveBodyPoint(BodyMeasurePoint.NECK, 0.50f, 0.18f, 18f),
    InteractiveBodyPoint(BodyMeasurePoint.SHOULDERS, 0.38f, 0.25f, 340f),
    InteractiveBodyPoint(BodyMeasurePoint.CHEST, 0.50f, 0.32f, 18f),
    InteractiveBodyPoint(BodyMeasurePoint.WAIST, 0.50f, 0.43f, 22f),
    InteractiveBodyPoint(BodyMeasurePoint.HIPS, 0.50f, 0.51f, 25f),
    InteractiveBodyPoint(BodyMeasurePoint.ARM_LENGTH, 0.25f, 0.38f, 330f),
    InteractiveBodyPoint(BodyMeasurePoint.WRIST, 0.20f, 0.54f, 325f),
    InteractiveBodyPoint(BodyMeasurePoint.HAND, 0.17f, 0.60f, 320f),
    InteractiveBodyPoint(BodyMeasurePoint.THIGH, 0.41f, 0.65f, 18f),
    InteractiveBodyPoint(BodyMeasurePoint.INSEAM, 0.50f, 0.64f, 0f),
    InteractiveBodyPoint(BodyMeasurePoint.CALF, 0.42f, 0.81f, 15f),
    InteractiveBodyPoint(BodyMeasurePoint.FOOT, 0.43f, 0.94f, 70f),
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
    var stageName by rememberSaveable { mutableStateOf(InteractiveIntroStage.LANGUAGE.name) }
    val stage = runCatching { InteractiveIntroStage.valueOf(stageName) }
        .getOrDefault(InteractiveIntroStage.LANGUAGE)

    BackHandler(enabled = stage != InteractiveIntroStage.LANGUAGE) {
        stageName = when (stage) {
            InteractiveIntroStage.LANGUAGE -> InteractiveIntroStage.LANGUAGE.name
            InteractiveIntroStage.JOURNEY -> InteractiveIntroStage.LANGUAGE.name
            InteractiveIntroStage.BODY, InteractiveIntroStage.PHOTO -> InteractiveIntroStage.JOURNEY.name
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        InteractiveBackdrop()
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.985f)) togetherWith
                    (fadeOut(tween(170)) + scaleOut(tween(220), targetScale = 1.015f))
            },
            label = "interactive-onboarding",
        ) { current ->
            when (current) {
                InteractiveIntroStage.LANGUAGE -> InteractiveLanguagePrompt(
                    onArabic = {
                        onLanguageChange("ar")
                        stageName = InteractiveIntroStage.JOURNEY.name
                    },
                    onEnglish = {
                        onLanguageChange("en")
                        stageName = InteractiveIntroStage.JOURNEY.name
                    },
                )

                InteractiveIntroStage.JOURNEY -> InteractiveJourneyPrompt(
                    language = language,
                    onBody = {
                        onJourneyMode(JourneyMode.AVATAR)
                        stageName = InteractiveIntroStage.BODY.name
                    },
                    onPhoto = {
                        onJourneyMode(JourneyMode.PHOTO)
                        stageName = InteractiveIntroStage.PHOTO.name
                    },
                )

                InteractiveIntroStage.BODY -> InteractiveBodyLab(
                    language = language,
                    profile = profile,
                    onHeightChanged = onHeightChanged,
                    onWeightChanged = onWeightChanged,
                    onMeasurementChanged = onMeasurementChanged,
                    onMeasurementCleared = onMeasurementCleared,
                    onComplete = onComplete,
                )

                InteractiveIntroStage.PHOTO -> InteractivePhotoPath(language, onComplete)
            }
        }
    }
}

@Composable
private fun InteractiveBackdrop() {
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
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(scheme.primary.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.44f),
                radius = size.minDimension * 0.78f,
            ),
            radius = size.minDimension * 0.78f,
            center = Offset(size.width * 0.5f, size.height * 0.44f),
        )
    }
}

@Composable
private fun InteractiveLanguagePrompt(onArabic: () -> Unit, onEnglish: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BodyScanMark()
                Text(
                    "Choose your language",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "اختر لغتك للبدء",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onArabic, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("العربية", fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = onEnglish, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("English", fontWeight = FontWeight.Black)
                }
                Text(
                    "ALMI / INTERACTIVE BODY FIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BodyScanMark() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(82.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(scheme.primary.copy(alpha = 0.10f), size.minDimension * 0.46f, center)
        drawCircle(scheme.primary, size.minDimension * 0.31f, center, style = Stroke(2.5f))
        drawLine(scheme.primary, Offset(center.x, 4f), Offset(center.x, size.height - 4f), 2f)
        drawLine(scheme.primary, Offset(4f, center.y), Offset(size.width - 4f, center.y), 2f)
        drawCircle(scheme.error, 5f, center)
    }
}

@Composable
private fun InteractiveJourneyPrompt(
    language: String,
    onBody: () -> Unit,
    onPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ALMI / START", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            trI(language, "كيف تريد أن نعرف جسمك؟", "How should ALMI understand your body?"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            trI(
                language,
                "اختر المجسم لتبني ملف قياسات تفاعلي دقيق، أو استخدم صورتك مباشرة.",
                "Choose the body model for an interactive measurement profile, or start directly with your photo.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        JourneyChoice(
            code = "BODY / LIVE 360",
            title = trI(language, "مجسم تفاعلي", "Interactive body model"),
            description = trI(
                language,
                "المس أجزاء الجسم، حرّك المجسم 360°، أدخل القياس، وشاهد القيم تثبت على جسمك.",
                "Touch body regions, rotate 360°, enter measurements, and see values stay attached to the model.",
            ),
            primary = true,
            onClick = onBody,
        )
        JourneyChoice(
            code = "PHOTO / DIRECT",
            title = trI(language, "صورتي الشخصية", "My personal photo"),
            description = trI(
                language,
                "انتقل مباشرة للاستوديو بصورة كاملة للجسم، ويمكنك إنشاء ملف القياسات لاحقًا.",
                "Go directly to the studio with a full-body photo; you can create a measurement profile later.",
            ),
            primary = false,
            onClick = onPhoto,
        )
    }
}

@Composable
private fun JourneyChoice(
    code: String,
    title: String,
    description: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (primary) scheme.primaryContainer.copy(alpha = 0.48f) else scheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, if (primary) scheme.primary.copy(alpha = 0.52f) else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(code, style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Text(
                trI("en", "", "TAP TO CONTINUE →"),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun InteractiveBodyLab(
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
    var showBodyBasics by rememberSaveable { mutableStateOf(false) }
    val selectedPoint = selectedName?.let { runCatching { BodyMeasurePoint.valueOf(it) }.getOrNull() }
    val selectedSpec = selectedPoint?.let { point -> interactiveBodyPoints.firstOrNull { it.point == point } }
    val animatedYaw by animateFloatAsState(
        targetValue = yawTarget,
        animationSpec = tween(420),
        label = "auto-body-yaw",
    )
    val zoom by animateFloatAsState(
        targetValue = if (selectedPoint == null) 1f else 1.42f,
        animationSpec = tween(360),
        label = "body-region-zoom",
    )
    val focusX = selectedSpec?.x ?: 0.5f
    val focusY = selectedSpec?.y ?: 0.5f
    val recommended = profile.nextRecommendedMeasurement

    fun select(point: BodyMeasurePoint) {
        val spec = interactiveBodyPoints.first { it.point == point }
        selectedName = point.name
        yawTarget = nearestYawTarget(animatedYaw, spec.preferredYaw)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("ALMI / BODY LIVE", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                    Text(
                        trI(language, "المجسم التفاعلي", "Interactive body"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (profile.isFitReady) scheme.primaryContainer else scheme.surface,
                    border = BorderStroke(1.dp, if (profile.isFitReady) scheme.primary else scheme.outlineVariant),
                ) {
                    Text(
                        if (profile.isFitReady) trI(language, "جاهز للتجربة", "FIT READY")
                        else "${profile.essentialCompletedMeasurements}/${essentialBodyMeasurements.size}",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            LinearProgressIndicator(
                progress = { profile.essentialCompletionFraction },
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            trI(language, "اسحب للدوران • اضغط نقطة للقياس", "Drag to rotate • tap a point to measure"),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            "${normalizeI(animatedYaw).roundToInt()}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                            fontWeight = FontWeight.Black,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    transformOrigin = TransformOrigin(focusX, focusY)
                                },
                        ) {
                            LivingBodyModel(
                                yawDegrees = animatedYaw,
                                profile = profile,
                                selectedPoint = selectedPoint,
                                recommendedPoint = recommended,
                                onYawDelta = { delta ->
                                    selectedName = null
                                    yawTarget += delta
                                },
                                onSelectPoint = ::select,
                            )
                        }

                        if (selectedPoint == null && recommended != null) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                                shape = RoundedCornerShape(999.dp),
                                color = scheme.surface.copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, scheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier.clickable { select(recommended) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(Modifier.size(7.dp).background(scheme.error, CircleShape))
                                    Text(
                                        trI(language, "ابدأ بـ ${pointTitleI(recommended, language)}", "Start with ${pointTitleI(recommended, language)}"),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AngleButton(trI(language, "أمامي", "Front"), Modifier.weight(1f)) {
                            selectedName = null
                            yawTarget = nearestYawTarget(animatedYaw, 0f)
                        }
                        AngleButton(trI(language, "جانبي", "Side"), Modifier.weight(1f)) {
                            selectedName = null
                            yawTarget = nearestYawTarget(animatedYaw, 90f)
                        }
                        AngleButton(trI(language, "خلفي", "Back"), Modifier.weight(1f)) {
                            selectedName = null
                            yawTarget = nearestYawTarget(animatedYaw, 180f)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedPoint == null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { showBodyBasics = !showBodyBasics }) {
                        Text(
                            if (showBodyBasics) trI(language, "إخفاء الطول والوزن", "Hide height & weight")
                            else trI(language, "تعديل الطول والوزن", "Edit height & weight"),
                        )
                    }
                    AnimatedVisibility(visible = showBodyBasics) {
                        BodyBasicsEditor(
                            language = language,
                            profile = profile,
                            onHeightChanged = onHeightChanged,
                            onWeightChanged = onWeightChanged,
                        )
                    }
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text(
                            if (profile.isFitReady) trI(language, "متابعة إلى تجربة الملابس", "Continue to try-on")
                            else trI(language, "المتابعة الآن وإكمال القياسات لاحقًا", "Continue now and finish measurements later"),
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }

        selectedPoint?.let { point ->
            MeasurementCoachPanel(
                modifier = Modifier.align(Alignment.BottomCenter),
                language = language,
                point = point,
                existingInches = profile.measurementsInches[point],
                isEssential = point in essentialBodyMeasurements,
                onSave = { inches ->
                    onMeasurementChanged(point, inches)
                    val next = guidedMeasurementOrder.firstOrNull {
                        it != point && it !in profile.measurementsInches
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
                onLater = { selectedName = null },
            )
        }
    }
}

@Composable
private fun BodyBasicsEditor(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
) {
    var heightText by remember(profile.heightInches) { mutableStateOf(numberI(profile.heightInches)) }
    var weightText by remember(profile.weightPounds) { mutableStateOf(numberI(profile.weightPounds)) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = heightText,
            onValueChange = { next ->
                heightText = numericI(next, 6)
                heightText.toFloatOrNull()?.let(onHeightChanged)
            },
            label = { Text(trI(language, "الطول (in)", "Height (in)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = weightText,
            onValueChange = { next ->
                weightText = numericI(next, 6)
                weightText.toFloatOrNull()?.let(onWeightChanged)
            },
            label = { Text(trI(language, "الوزن (lb)", "Weight (lb)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LivingBodyModel(
    yawDegrees: Float,
    profile: BodyProfile,
    selectedPoint: BodyMeasurePoint?,
    recommendedPoint: BodyMeasurePoint?,
    onYawDelta: (Float) -> Unit,
    onSelectPoint: (BodyMeasurePoint) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val idle = rememberInfiniteTransition(label = "living-body")
    val breath by idle.animateFloat(
        initialValue = 0.994f,
        targetValue = 1.007f,
        animationSpec = infiniteRepeatable(tween(1_900), RepeatMode.Reverse),
        label = "body-breath",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount -> onYawDelta(dragAmount * 0.72f) }
            },
    ) {
        val radians = Math.toRadians(normalizeI(yawDegrees).toDouble())
        val frontness = abs(cos(radians)).toFloat()
        val side = sin(radians).toFloat()
        val horizontalCompression = 0.42f + 0.58f * frontness

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleY = breath },
        ) {
            val centerX = size.width / 2f
            val top = size.height * 0.045f
            val bodyHeight = size.height * 0.86f
            val weightFactor = (profile.weightPounds / 165f).coerceIn(0.72f, 1.55f)
            val heightFactor = (profile.heightInches / 68f).coerceIn(0.82f, 1.18f)
            val widthFactor = 0.90f + (weightFactor - 1f) * 0.21f
            val torsoWidth = size.width * 0.235f * horizontalCompression * widthFactor
            val shoulderY = top + bodyHeight * 0.22f
            val hipY = top + bodyHeight * 0.53f
            val crotchY = top + bodyHeight * 0.59f
            val footY = (top + bodyHeight * heightFactor).coerceAtMost(size.height * 0.94f)
            val headRadius = bodyHeight * 0.060f
            val headCenter = Offset(centerX + side * size.width * 0.018f, top + bodyHeight * 0.09f)

            val guide = scheme.outlineVariant.copy(alpha = 0.28f)
            repeat(4) { index ->
                val r = size.width * (0.17f + index * 0.075f)
                drawOval(
                    guide,
                    topLeft = Offset(centerX - r, hipY - r * 0.21f),
                    size = Size(r * 2f, r * 0.42f),
                    style = Stroke(1f),
                )
            }
            drawLine(guide, Offset(centerX, 0f), Offset(centerX, size.height), 1f)

            val body = scheme.onSurface.copy(alpha = 0.88f)
            val farSide = scheme.onSurfaceVariant.copy(alpha = 0.64f)
            drawCircle(body, headRadius, headCenter)
            drawLine(
                body,
                Offset(centerX, headCenter.y + headRadius * 0.84f),
                Offset(centerX, shoulderY - bodyHeight * 0.018f),
                bodyHeight * 0.050f,
                StrokeCap.Round,
            )
            drawRoundRect(
                color = body,
                topLeft = Offset(centerX - torsoWidth, shoulderY),
                size = Size(torsoWidth * 2f, bodyHeight * 0.35f),
                cornerRadius = CornerRadius(torsoWidth * 0.38f),
            )
            drawOval(
                body,
                topLeft = Offset(centerX - torsoWidth * 0.93f, hipY - bodyHeight * 0.035f),
                size = Size(torsoWidth * 1.86f, bodyHeight * 0.12f),
            )

            val shoulderSpan = torsoWidth * 0.92f
            val armStroke = bodyHeight * (0.050f + weightFactor * 0.009f)
            drawLine(
                body,
                Offset(centerX - shoulderSpan, shoulderY + bodyHeight * 0.025f),
                Offset(centerX - shoulderSpan * 1.48f + side * size.width * 0.02f, hipY + bodyHeight * 0.10f),
                armStroke,
                StrokeCap.Round,
            )
            drawLine(
                farSide,
                Offset(centerX + shoulderSpan, shoulderY + bodyHeight * 0.025f),
                Offset(centerX + shoulderSpan * 1.48f + side * size.width * 0.02f, hipY + bodyHeight * 0.10f),
                armStroke,
                StrokeCap.Round,
            )

            val legGap = torsoWidth * 0.34f
            val legStroke = bodyHeight * (0.068f + weightFactor * 0.012f)
            drawLine(
                body,
                Offset(centerX - legGap, crotchY),
                Offset(centerX - legGap * 0.88f + side * size.width * 0.012f, footY - bodyHeight * 0.035f),
                legStroke,
                StrokeCap.Round,
            )
            drawLine(
                farSide,
                Offset(centerX + legGap, crotchY),
                Offset(centerX + legGap * 0.88f + side * size.width * 0.012f, footY - bodyHeight * 0.035f),
                legStroke,
                StrokeCap.Round,
            )
            drawLine(
                body,
                Offset(centerX - legGap * 0.88f, footY - bodyHeight * 0.025f),
                Offset(centerX - legGap * 0.88f - torsoWidth * 0.34f, footY),
                bodyHeight * 0.030f,
                StrokeCap.Round,
            )
            drawLine(
                farSide,
                Offset(centerX + legGap * 0.88f, footY - bodyHeight * 0.025f),
                Offset(centerX + legGap * 0.88f + torsoWidth * 0.34f, footY),
                bodyHeight * 0.030f,
                StrokeCap.Round,
            )

            selectedPoint?.let { point ->
                val spec = interactiveBodyPoints.first { it.point == point }
                val y = size.height * spec.y
                val isLength = point == BodyMeasurePoint.ARM_LENGTH ||
                    point == BodyMeasurePoint.INSEAM || point == BodyMeasurePoint.FOOT ||
                    point == BodyMeasurePoint.SHOULDERS
                if (isLength) {
                    val x = size.width * (0.5f + (spec.x - 0.5f) * horizontalCompression)
                    drawLine(
                        scheme.error.copy(alpha = 0.62f),
                        Offset(x, y - size.height * 0.07f),
                        Offset(x, y + size.height * 0.07f),
                        3f,
                        StrokeCap.Round,
                    )
                } else {
                    drawOval(
                        scheme.error.copy(alpha = 0.66f),
                        topLeft = Offset(centerX - torsoWidth * 1.12f, y - bodyHeight * 0.018f),
                        size = Size(torsoWidth * 2.24f, bodyHeight * 0.036f),
                        style = Stroke(3f),
                    )
                }
            }
        }

        interactiveBodyPoints.forEach { spec ->
            val xFraction = 0.5f + (spec.x - 0.5f) * horizontalCompression
            BodyHotspot(
                language = "en",
                point = spec.point,
                measuredInches = profile.measurementsInches[spec.point],
                active = selectedPoint == spec.point,
                recommended = recommendedPoint == spec.point,
                modifier = Modifier.offset(
                    x = maxWidth * xFraction - 15.dp,
                    y = maxHeight * spec.y - 15.dp,
                ),
                onClick = { onSelectPoint(spec.point) },
            )
        }
    }
}

@Composable
private fun BodyHotspot(
    language: String,
    point: BodyMeasurePoint,
    measuredInches: Float?,
    active: Boolean,
    recommended: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pulseTransition = rememberInfiniteTransition(label = "hotspot-${point.name}")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "hotspot-pulse-${point.name}",
    )

    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .graphicsLayer {
                    if (active || recommended || measuredInches == null) {
                        scaleX = pulse
                        scaleY = pulse
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (active) {
                Box(Modifier.size(28.dp).background(scheme.error.copy(alpha = 0.13f), CircleShape))
                Box(Modifier.size(20.dp).background(scheme.error.copy(alpha = 0.18f), CircleShape))
            } else if (recommended) {
                Box(Modifier.size(22.dp).background(scheme.error.copy(alpha = 0.13f), CircleShape))
            }
            Box(
                Modifier.size(if (active) 10.dp else 8.dp)
                    .background(if (measuredInches == null) scheme.error else scheme.primary, CircleShape),
            )
        }

        AnimatedVisibility(visible = active || measuredInches != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = scheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, if (active) scheme.error.copy(alpha = 0.45f) else scheme.outlineVariant),
            ) {
                Text(
                    text = when {
                        measuredInches != null -> "${numberI(measuredInches)} in"
                        active -> pointTitleI(point, language)
                        else -> ""
                    },
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun MeasurementCoachPanel(
    modifier: Modifier,
    language: String,
    point: BodyMeasurePoint,
    existingInches: Float?,
    isEssential: Boolean,
    onSave: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onLater: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var useCm by rememberSaveable(point.name) { mutableStateOf(false) }
    var value by remember(point, existingInches, useCm) {
        mutableStateOf(existingInches?.let { numberI(if (useCm) it * 2.54f else it) }.orEmpty())
    }
    val entered = value.toFloatOrNull()
    val valid = entered != null && entered > 0f

    Surface(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pointTitleI(point, language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (isEssential) trI(language, "قياس أساسي للدقة", "Essential fit measurement")
                        else trI(language, "تحسين إضافي للدقة", "Optional precision refinement"),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEssential) scheme.primary else scheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = onLater) {
                    Text(trI(language, "لاحقًا", "Later"))
                }
            }

            Text(
                pointInstructionI(point, language),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )

            MeasurementRuler(useCm = useCm)

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                UnitChip("in", selected = !useCm, modifier = Modifier.weight(1f)) {
                    if (useCm) {
                        value = value.toFloatOrNull()?.let { numberI(it / 2.54f) }.orEmpty()
                        useCm = false
                    }
                }
                UnitChip("cm", selected = useCm, modifier = Modifier.weight(1f)) {
                    if (!useCm) {
                        value = value.toFloatOrNull()?.let { numberI(it * 2.54f) }.orEmpty()
                        useCm = true
                    }
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = { value = numericI(it, 7) },
                label = { Text(trI(language, "اكتب القياس", "Enter measurement")) },
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
                    OutlinedButton(onClick = clear, modifier = Modifier.weight(0.44f).height(50.dp)) {
                        Text(trI(language, "حذف", "Clear"))
                    }
                }
                Button(
                    enabled = valid,
                    onClick = {
                        entered?.let { raw -> onSave(if (useCm) raw / 2.54f else raw) }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Text(
                        trI(language, "حفظ والقياس التالي", "Save & next"),
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementRuler(useCm: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.48f),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            val divisions = if (useCm) 20 else 16
            val spacing = size.width / divisions.toFloat()
            repeat(divisions + 1) { index ->
                val major = index % 4 == 0
                val tickHeight = if (major) size.height * 0.78f else size.height * 0.44f
                val x = index * spacing
                drawLine(
                    color = scheme.onSurface.copy(alpha = if (major) 0.78f else 0.36f),
                    start = Offset(x, size.height),
                    end = Offset(x, size.height - tickHeight),
                    strokeWidth = if (major) 2f else 1f,
                )
            }
            drawLine(
                scheme.error.copy(alpha = 0.72f),
                Offset(size.width * 0.5f, 0f),
                Offset(size.width * 0.5f, size.height),
                2f,
            )
        }
    }
}

@Composable
private fun UnitChip(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) scheme.primaryContainer else scheme.surface,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun AngleButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(40.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InteractivePhotoPath(language: String, onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ALMI / PHOTO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            trI(language, "استخدم صورتك مباشرة", "Use your photo directly"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            trI(
                language,
                "داخل الاستوديو ستختار صورة واضحة وكاملة للجسم. يمكنك العودة للمجسم التفاعلي من الإعدادات في أي وقت.",
                "Inside the studio, choose a clear full-body photo. You can return to the interactive body model from Settings at any time.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().height(330.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(30.dp)) {
                    val frameWidth = size.width * 0.70f
                    val frameHeight = size.height * 0.84f
                    val left = (size.width - frameWidth) / 2f
                    val top = (size.height - frameHeight) / 2f
                    drawRoundRect(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        topLeft = Offset(left, top),
                        size = Size(frameWidth, frameHeight),
                        cornerRadius = CornerRadius(28f),
                        style = Stroke(2f),
                    )
                    val cx = size.width / 2f
                    val figure = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)
                    drawCircle(figure, 27f, Offset(cx, size.height * 0.24f))
                    drawLine(figure, Offset(cx, size.height * 0.34f), Offset(cx, size.height * 0.64f), 42f, StrokeCap.Round)
                    drawLine(figure, Offset(cx, size.height * 0.42f), Offset(cx - 64f, size.height * 0.56f), 16f, StrokeCap.Round)
                    drawLine(figure, Offset(cx, size.height * 0.42f), Offset(cx + 64f, size.height * 0.56f), 16f, StrokeCap.Round)
                    drawLine(figure, Offset(cx - 10f, size.height * 0.64f), Offset(cx - 34f, size.height * 0.87f), 20f, StrokeCap.Round)
                    drawLine(figure, Offset(cx + 10f, size.height * 0.64f), Offset(cx + 34f, size.height * 0.87f), 20f, StrokeCap.Round)
                }
            }
        }
        Text(
            trI(language, "• الجسم كامل داخل الإطار\n• إضاءة متساوية\n• وقفة طبيعية\n• ملابس قريبة من الجسم", "• Full body inside frame\n• Even lighting\n• Natural stance\n• Close-fitting clothes"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(trI(language, "فتح الاستوديو", "Open studio"), fontWeight = FontWeight.Black)
        }
    }
}

private fun pointTitleI(point: BodyMeasurePoint, language: String): String = when (point) {
    BodyMeasurePoint.NECK -> trI(language, "الرقبة", "Neck")
    BodyMeasurePoint.SHOULDERS -> trI(language, "الكتفين", "Shoulders")
    BodyMeasurePoint.CHEST -> trI(language, "الصدر", "Chest")
    BodyMeasurePoint.WAIST -> trI(language, "الخصر", "Waist")
    BodyMeasurePoint.HIPS -> trI(language, "الورك", "Hips")
    BodyMeasurePoint.ARM_LENGTH -> trI(language, "طول الذراع", "Arm length")
    BodyMeasurePoint.WRIST -> trI(language, "المعصم", "Wrist")
    BodyMeasurePoint.HAND -> trI(language, "اليد", "Hand")
    BodyMeasurePoint.THIGH -> trI(language, "الفخذ", "Thigh")
    BodyMeasurePoint.INSEAM -> trI(language, "طول الساق الداخلي", "Inseam")
    BodyMeasurePoint.CALF -> trI(language, "الساق", "Calf")
    BodyMeasurePoint.FOOT -> trI(language, "القدم", "Foot")
}

private fun pointInstructionI(point: BodyMeasurePoint, language: String): String = when (point) {
    BodyMeasurePoint.NECK -> trI(language, "لف شريط القياس حول قاعدة الرقبة أعلى عظمة الترقوة بقليل، بدون شد.", "Wrap the tape around the base of the neck just above the collarbone without tightening.")
    BodyMeasurePoint.SHOULDERS -> trI(language, "قس بخط مستقيم من نهاية كتف إلى نهاية الكتف الآخر عبر أعلى الظهر.", "Measure straight from one shoulder tip to the other across the upper back.")
    BodyMeasurePoint.CHEST -> trI(language, "لف الشريط حول أعرض نقطة من الصدر، واجعله أفقيًا وموازيًا للأرض.", "Wrap the tape around the fullest part of the chest and keep it level with the floor.")
    BodyMeasurePoint.WAIST -> trI(language, "قس حول الخصر الطبيعي عند أضيق نقطة بدون شد البطن أو ضغط الشريط.", "Measure the natural waist at its narrowest point without sucking in or compressing the tape.")
    BodyMeasurePoint.HIPS -> trI(language, "لف الشريط حول أعرض نقطة من الورك والمؤخرة مع إبقائه أفقيًا.", "Wrap the tape around the fullest part of the hips and seat while keeping it level.")
    BodyMeasurePoint.ARM_LENGTH -> trI(language, "ابدأ من نقطة الكتف، مر فوق كوع مثني قليلًا، وانته عند عظمة المعصم.", "Start at the shoulder point, pass over a slightly bent elbow, and end at the wrist bone.")
    BodyMeasurePoint.WRIST -> trI(language, "لف الشريط حول المعصم عند العظمة البارزة بدون ضغط.", "Wrap the tape around the wrist at the wrist bone without compression.")
    BodyMeasurePoint.HAND -> trI(language, "لف الشريط حول أعرض جزء من راحة اليد عند مفاصل الأصابع مع استثناء الإبهام.", "Wrap the tape around the widest part of the hand at the knuckles, excluding the thumb.")
    BodyMeasurePoint.THIGH -> trI(language, "قس محيط أعرض جزء من أعلى الفخذ وأنت واقف بشكل طبيعي.", "Measure around the fullest part of the upper thigh while standing naturally.")
    BodyMeasurePoint.INSEAM -> trI(language, "قس من أعلى نقطة داخل الساق عند المنشعب إلى الأرض أو إلى طول البنطال المطلوب.", "Measure from the top of the inner leg at the crotch to the floor or desired trouser length.")
    BodyMeasurePoint.CALF -> trI(language, "لف الشريط حول أعرض نقطة من عضلة الساق بدون ضغط.", "Wrap the tape around the fullest part of the calf without compression.")
    BodyMeasurePoint.FOOT -> trI(language, "قف على ورقة وقس من مؤخرة الكعب إلى نهاية أطول إصبع.", "Stand on paper and measure from the back of the heel to the longest toe.")
}

private fun trI(language: String, ar: String, en: String): String = if (language == "ar") ar else en

private fun numberI(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else "%.1f".format(Locale.US, value)

private fun numericI(value: String, maxLength: Int): String =
    value.filter { it.isDigit() || it == '.' }.take(maxLength)

private fun normalizeI(value: Float): Float {
    var normalized = value % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

private fun nearestYawTarget(current: Float, preferredNormalized: Float): Float {
    val currentNormalized = normalizeI(current)
    var delta = preferredNormalized - currentNormalized
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return current + delta
}
