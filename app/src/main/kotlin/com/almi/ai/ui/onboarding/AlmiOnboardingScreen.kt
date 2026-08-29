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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class IntroStage { LANGUAGE, JOURNEY, AVATAR, PHOTO }

private data class BodyPointSpec(
    val point: BodyMeasurePoint,
    val x: Float,
    val y: Float,
)

private val bodyPoints = listOf(
    BodyPointSpec(BodyMeasurePoint.NECK, 0.50f, 0.18f),
    BodyPointSpec(BodyMeasurePoint.SHOULDERS, 0.38f, 0.25f),
    BodyPointSpec(BodyMeasurePoint.CHEST, 0.50f, 0.32f),
    BodyPointSpec(BodyMeasurePoint.WAIST, 0.50f, 0.43f),
    BodyPointSpec(BodyMeasurePoint.HIPS, 0.50f, 0.51f),
    BodyPointSpec(BodyMeasurePoint.ARM_LENGTH, 0.27f, 0.37f),
    BodyPointSpec(BodyMeasurePoint.WRIST, 0.21f, 0.54f),
    BodyPointSpec(BodyMeasurePoint.HAND, 0.18f, 0.60f),
    BodyPointSpec(BodyMeasurePoint.THIGH, 0.42f, 0.65f),
    BodyPointSpec(BodyMeasurePoint.INSEAM, 0.50f, 0.64f),
    BodyPointSpec(BodyMeasurePoint.CALF, 0.42f, 0.81f),
    BodyPointSpec(BodyMeasurePoint.FOOT, 0.43f, 0.94f),
)

@Composable
fun AlmiOnboardingScreen(
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
    var stageName by rememberSaveable { mutableStateOf(IntroStage.LANGUAGE.name) }
    val stage = runCatching { IntroStage.valueOf(stageName) }.getOrDefault(IntroStage.LANGUAGE)

    BackHandler(enabled = stage != IntroStage.LANGUAGE) {
        stageName = when (stage) {
            IntroStage.LANGUAGE -> IntroStage.LANGUAGE.name
            IntroStage.JOURNEY -> IntroStage.LANGUAGE.name
            IntroStage.AVATAR, IntroStage.PHOTO -> IntroStage.JOURNEY.name
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PrecisionBackdrop()
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(240)) + scaleIn(tween(300), initialScale = 0.98f)) togetherWith
                    (fadeOut(tween(150)) + scaleOut(tween(190), targetScale = 1.015f))
            },
            label = "almi-intro",
        ) { current ->
            when (current) {
                IntroStage.LANGUAGE -> LanguagePrompt(
                    onArabic = {
                        onLanguageChange("ar")
                        stageName = IntroStage.JOURNEY.name
                    },
                    onEnglish = {
                        onLanguageChange("en")
                        stageName = IntroStage.JOURNEY.name
                    },
                )

                IntroStage.JOURNEY -> JourneyPrompt(
                    language = language,
                    onAvatar = {
                        onJourneyMode(JourneyMode.AVATAR)
                        stageName = IntroStage.AVATAR.name
                    },
                    onPhoto = {
                        onJourneyMode(JourneyMode.PHOTO)
                        stageName = IntroStage.PHOTO.name
                    },
                )

                IntroStage.AVATAR -> AvatarBodyLab(
                    language = language,
                    profile = profile,
                    onHeightChanged = onHeightChanged,
                    onWeightChanged = onWeightChanged,
                    onMeasurementChanged = onMeasurementChanged,
                    onMeasurementCleared = onMeasurementCleared,
                    onComplete = onComplete,
                )

                IntroStage.PHOTO -> PhotoJourneyGuide(language, onComplete)
            }
        }
    }
}

@Composable
private fun PrecisionBackdrop() {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "precision-grid")
    val drift by motion.animateFloat(
        initialValue = -0.025f,
        targetValue = 0.025f,
        animationSpec = infiniteRepeatable(tween(9_000), RepeatMode.Reverse),
        label = "precision-grid-drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val line = scheme.outlineVariant.copy(alpha = 0.26f)
        val step = size.width / 7f
        var x = -step
        while (x < size.width + step) {
            drawLine(line, Offset(x + drift * size.width, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        val center = Offset(size.width * 0.52f, size.height * 0.42f)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(scheme.primary.copy(alpha = 0.055f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.78f,
            ),
            radius = size.minDimension * 0.78f,
            center = center,
        )
    }
}

@Composable
private fun LanguagePrompt(onArabic: () -> Unit, onEnglish: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TechnicalMark()
                Text(
                    "Choose your language",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "اختر لغتك للبدء",
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurfaceVariant,
                )
                Button(onClick = onArabic, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("العربية", fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = onEnglish, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("English", fontWeight = FontWeight.Black)
                }
                Text(
                    "ALMI / BODY-ACCURATE TRY-ON",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TechnicalMark() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(72.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(scheme.primary.copy(alpha = 0.10f), size.minDimension * 0.46f, c)
        drawCircle(scheme.primary, size.minDimension * 0.30f, c, style = Stroke(2.6f))
        drawLine(scheme.primary, Offset(c.x, size.height * 0.08f), Offset(c.x, size.height * 0.92f), 2f)
        drawLine(scheme.primary, Offset(size.width * 0.08f, c.y), Offset(size.width * 0.92f, c.y), 2f)
        drawCircle(scheme.primary, 4.5f, c)
    }
}

@Composable
private fun JourneyPrompt(language: String, onAvatar: () -> Unit, onPhoto: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ALMI / 01", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        Text(
            tr(language, "كيف تود أن نكمل رحلتك؟", "How should we build your fitting journey?"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            tr(
                language,
                "ابنِ ملف جسم بقياساتك الدقيقة، أو ابدأ مباشرة بصورتك الشخصية.",
                "Build a measurement-aware body profile, or begin directly with your own photo.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        JourneyCard(
            code = "BODY / 360",
            title = tr(language, "مجسمي التفاعلي", "Interactive body model"),
            description = tr(
                language,
                "دوران 360°، نقاط قياس تفاعلية، تكبير تلقائي، وملف جسم محفوظ.",
                "360° rotation, interactive measurement hotspots, auto-focus, and a saved body profile.",
            ),
            emphasized = true,
            onClick = onAvatar,
        )
        JourneyCard(
            code = "PHOTO / LIVE",
            title = tr(language, "صورتي الشخصية", "My personal photo"),
            description = tr(
                language,
                "ابدأ بصورة كاملة للجسم وأضف القياسات لاحقًا متى احتجت.",
                "Start with a full-body photo and add measurements later whenever needed.",
            ),
            emphasized = false,
            onClick = onPhoto,
        )
        Text(
            tr(language, "يمكن تغيير المسار لاحقًا من ملف الجسم.", "You can change the journey later from your body profile."),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JourneyCard(
    code: String,
    title: String,
    description: String,
    emphasized: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.56f) else scheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, if (emphasized) scheme.primary.copy(alpha = 0.55f) else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(code, style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(32.dp).height(2.dp).background(scheme.primary))
                Text("ENTER", style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun AvatarBodyLab(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onComplete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var yaw by rememberSaveable { mutableStateOf(0f) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { BodyMeasurePoint.valueOf(it) }.getOrNull() }
    var heightText by remember(profile.heightInches) { mutableStateOf(number(profile.heightInches)) }
    var weightText by remember(profile.weightPounds) { mutableStateOf(number(profile.weightPounds)) }
    val selectedSpec = selected?.let { point -> bodyPoints.firstOrNull { it.point == point } }
    val focusX = selectedSpec?.x ?: 0.5f
    val focusY = selectedSpec?.y ?: 0.5f
    val zoom by animateFloatAsState(
        targetValue = if (selected == null) 1f else 1.34f,
        animationSpec = tween(320),
        label = "body-focus",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ALMI / BODY LAB", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                Text(
                    tr(language, "ملف جسمك", "Your body profile"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Text(
                    "${profile.completedMeasurements}/${BodyMeasurePoint.entries.size}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        LinearProgressIndicator(
            progress = { profile.completionFraction },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = scheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = heightText,
                        onValueChange = { next ->
                            heightText = next
                            next.toFloatOrNull()?.let(onHeightChanged)
                        },
                        label = tr(language, "الطول (in)", "Height (in)"),
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = weightText,
                        onValueChange = { next ->
                            weightText = next
                            next.toFloatOrNull()?.let(onWeightChanged)
                        },
                        label = tr(language, "الوزن (lb)", "Weight (lb)"),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    tr(
                        language,
                        "اسحب المجسم للدوران. اضغط أي نقطة حمراء ليقترب النظام من الجزء ويشرح طريقة قياسه.",
                        "Drag to rotate. Tap any red hotspot and ALMI will focus on the area and guide the measurement.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("360° BODY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    Text(
                        "${normalizeDegrees(yaw).roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(500.dp)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = TransformOrigin(focusX, focusY)
                        },
                ) {
                    InteractiveMannequin(
                        yawDegrees = yaw,
                        profile = profile,
                        onYawDelta = { delta -> yaw = normalizeDegrees(yaw + delta) },
                        onSelectPoint = { selectedName = it.name },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SnapButton(tr(language, "أمامي", "Front"), Modifier.weight(1f)) { yaw = 0f }
                    SnapButton(tr(language, "جانبي", "Side"), Modifier.weight(1f)) { yaw = 90f }
                    SnapButton(tr(language, "خلفي", "Back"), Modifier.weight(1f)) { yaw = 180f }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = scheme.primaryContainer.copy(alpha = 0.30f),
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.28f)),
        ) {
            Text(
                tr(
                    language,
                    "القياسات تحفظ تلقائيًا على جهازك ويمكن استكمالها لاحقًا. القياسات التي تدخلها تُستخدم للمحافظة على نسب جسمك في طلب الـTry-On.",
                    "Measurements save automatically on your device and can be finished later. Entered values are used to preserve your body proportions in Try-On requests.",
                ),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(tr(language, "حفظ والمتابعة إلى الاستوديو", "Save and continue to studio"), fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
    }

    selected?.let { point ->
        MeasurementDialog(
            language = language,
            point = point,
            existingInches = profile.measurementsInches[point],
            onSave = { inches ->
                onMeasurementChanged(point, inches)
                selectedName = null
            },
            onClear = if (profile.measurementsInches.containsKey(point)) {
                {
                    onMeasurementCleared(point)
                    selectedName = null
                }
            } else null,
            onDismiss = { selectedName = null },
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { char -> char.isDigit() || char == '.' }.take(6)) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun InteractiveMannequin(
    yawDegrees: Float,
    profile: BodyProfile,
    onYawDelta: (Float) -> Unit,
    onSelectPoint: (BodyMeasurePoint) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "body-idle")
    val breath by motion.animateFloat(
        initialValue = 0.994f,
        targetValue = 1.006f,
        animationSpec = infiniteRepeatable(tween(1_900), RepeatMode.Reverse),
        label = "body-breath",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount -> onYawDelta(dragAmount * 0.74f) }
            },
    ) {
        val radians = Math.toRadians(yawDegrees.toDouble())
        val frontness = abs(cos(radians)).toFloat()
        val side = sin(radians).toFloat()
        val horizontalCompression = 0.44f + 0.56f * frontness

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleY = breath },
        ) {
            val centerX = size.width / 2f
            val top = size.height * 0.055f
            val bodyHeight = size.height * 0.84f
            val weightFactor = (profile.weightPounds / 165f).coerceIn(0.72f, 1.55f)
            val widthFactor = 0.90f + (weightFactor - 1f) * 0.20f
            val torsoWidth = size.width * 0.25f * horizontalCompression * widthFactor
            val shoulderY = top + bodyHeight * 0.22f
            val hipY = top + bodyHeight * 0.53f
            val crotchY = top + bodyHeight * 0.59f
            val footY = top + bodyHeight
            val headRadius = bodyHeight * 0.062f
            val headCenter = Offset(centerX + side * size.width * 0.012f, top + bodyHeight * 0.09f)

            val guide = scheme.outlineVariant.copy(alpha = 0.34f)
            repeat(5) { index ->
                val r = size.width * (0.14f + index * 0.07f)
                drawOval(
                    guide,
                    topLeft = Offset(centerX - r, hipY - r * 0.25f),
                    size = Size(r * 2f, r * 0.50f),
                    style = Stroke(1f),
                )
            }
            drawLine(guide, Offset(centerX, 0f), Offset(centerX, size.height), 1f)

            val fill = scheme.onSurface.copy(alpha = 0.84f)
            val softer = scheme.onSurfaceVariant.copy(alpha = 0.72f)
            drawCircle(fill, headRadius, headCenter)
            drawLine(
                fill,
                Offset(centerX, headCenter.y + headRadius * 0.82f),
                Offset(centerX, shoulderY - bodyHeight * 0.02f),
                bodyHeight * 0.052f,
                StrokeCap.Round,
            )
            drawRoundRect(
                color = fill,
                topLeft = Offset(centerX - torsoWidth, shoulderY),
                size = Size(torsoWidth * 2f, bodyHeight * 0.35f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(torsoWidth * 0.35f),
            )
            drawOval(
                color = fill,
                topLeft = Offset(centerX - torsoWidth * 0.92f, hipY - bodyHeight * 0.035f),
                size = Size(torsoWidth * 1.84f, bodyHeight * 0.12f),
            )

            val shoulderSpan = torsoWidth * 0.90f
            val armStroke = bodyHeight * (0.050f + weightFactor * 0.009f)
            drawLine(
                fill,
                Offset(centerX - shoulderSpan, shoulderY + bodyHeight * 0.025f),
                Offset(centerX - shoulderSpan * 1.45f + side * size.width * 0.018f, hipY + bodyHeight * 0.10f),
                armStroke,
                StrokeCap.Round,
            )
            drawLine(
                softer,
                Offset(centerX + shoulderSpan, shoulderY + bodyHeight * 0.025f),
                Offset(centerX + shoulderSpan * 1.45f + side * size.width * 0.018f, hipY + bodyHeight * 0.10f),
                armStroke,
                StrokeCap.Round,
            )

            val legGap = torsoWidth * 0.34f
            val legStroke = bodyHeight * (0.068f + weightFactor * 0.012f)
            drawLine(
                fill,
                Offset(centerX - legGap, crotchY),
                Offset(centerX - legGap * 0.88f + side * size.width * 0.010f, footY - bodyHeight * 0.035f),
                legStroke,
                StrokeCap.Round,
            )
            drawLine(
                softer,
                Offset(centerX + legGap, crotchY),
                Offset(centerX + legGap * 0.88f + side * size.width * 0.010f, footY - bodyHeight * 0.035f),
                legStroke,
                StrokeCap.Round,
            )
            drawLine(
                fill,
                Offset(centerX - legGap * 0.88f, footY - bodyHeight * 0.025f),
                Offset(centerX - legGap * 0.88f - torsoWidth * 0.32f, footY),
                bodyHeight * 0.030f,
                StrokeCap.Round,
            )
            drawLine(
                softer,
                Offset(centerX + legGap * 0.88f, footY - bodyHeight * 0.025f),
                Offset(centerX + legGap * 0.88f + torsoWidth * 0.32f, footY),
                bodyHeight * 0.030f,
                StrokeCap.Round,
            )

            if (cos(radians) < 0) {
                drawLine(
                    scheme.background.copy(alpha = 0.38f),
                    Offset(centerX, shoulderY + bodyHeight * 0.05f),
                    Offset(centerX, hipY - bodyHeight * 0.03f),
                    2f,
                )
            }
            drawLine(
                scheme.primary.copy(alpha = 0.70f),
                Offset(centerX - size.width * 0.15f, size.height * 0.985f),
                Offset(centerX + size.width * 0.15f, size.height * 0.985f),
                2f,
            )
        }

        bodyPoints.forEach { spec ->
            val xFraction = 0.5f + (spec.x - 0.5f) * horizontalCompression
            Hotspot(
                measuredInches = profile.measurementsInches[spec.point],
                modifier = Modifier.offset(
                    x = maxWidth * xFraction - 14.dp,
                    y = maxHeight * spec.y - 14.dp,
                ),
                onClick = { onSelectPoint(spec.point) },
            )
        }
    }
}

@Composable
private fun Hotspot(measuredInches: Float?, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "measure-hotspot")
    val pulse by motion.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "measure-hotspot-pulse",
    )
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer {
                    if (measuredInches == null) {
                        scaleX = pulse
                        scaleY = pulse
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(20.dp).background(scheme.error.copy(alpha = 0.14f), CircleShape))
            Box(Modifier.size(8.dp).background(scheme.error, CircleShape))
        }
        AnimatedVisibility(visible = measuredInches != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = scheme.surface.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, scheme.outlineVariant),
            ) {
                Text(
                    "${number(measuredInches ?: 0f)} in",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun SnapButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(42.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MeasurementDialog(
    language: String,
    point: BodyMeasurePoint,
    existingInches: Float?,
    onSave: (Float) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var useCm by rememberSaveable(point.name) { mutableStateOf(false) }
    var value by remember(point, existingInches, useCm) {
        mutableStateOf(existingInches?.let { number(if (useCm) it * 2.54f else it) }.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pointTitle(point, language), fontWeight = FontWeight.Black)
                Text(
                    "MEASURE / ${point.key.uppercase(Locale.US)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    pointInstruction(point, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MiniRuler(useCm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitButton("in", !useCm, Modifier.weight(1f)) { useCm = false }
                    UnitButton("cm", useCm, Modifier.weight(1f)) { useCm = true }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { char -> char.isDigit() || char == '.' }.take(7) },
                    label = { Text(tr(language, "أدخل القياس", "Enter measurement")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr(
                        language,
                        "ضع شريط القياس ملاصقًا للجسم بدون ضغط أو فراغ. الوحدة الحالية: ${if (useCm) "cm" else "in"}.",
                        "Keep the tape against the body without compression or slack. Current unit: ${if (useCm) "cm" else "in"}.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                onClear?.let { clear ->
                    TextButton(onClick = clear) {
                        Text(tr(language, "حذف القياس", "Remove measurement"))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = value.toFloatOrNull()?.let { it > 0f } == true,
                onClick = {
                    value.toFloatOrNull()?.let { entered ->
                        onSave(if (useCm) entered / 2.54f else entered)
                    }
                },
            ) {
                Text(tr(language, "حفظ", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(language, "إلغاء", "Cancel")) }
        },
    )
}

@Composable
private fun UnitButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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

@Composable
private fun MiniRuler(useCm: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.56f),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            val divisions = if (useCm) 20 else 16
            val spacing = size.width / divisions.toFloat()
            repeat(divisions + 1) { index ->
                val major = index % 4 == 0
                val tick = if (major) size.height * 0.72f else size.height * 0.42f
                val x = index * spacing
                drawLine(
                    scheme.onSurface.copy(alpha = if (major) 0.82f else 0.40f),
                    Offset(x, size.height),
                    Offset(x, size.height - tick),
                    if (major) 2f else 1f,
                )
            }
            drawLine(scheme.primary, Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), 2f)
        }
    }
}

@Composable
private fun PhotoJourneyGuide(language: String, onComplete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ALMI / PHOTO SETUP", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        Text(
            tr(language, "جهّز صورة مناسبة للتجربة", "Prepare a fitting-ready photo"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            tr(
                language,
                "الكاميرا والمعرض موجودان داخل الاستوديو. استخدم صورة واضحة وكاملة للجسم للحصول على نتيجة أفضل.",
                "Camera and gallery are available inside the studio. Use a clear full-body photo for better results.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier.fillMaxWidth().height(330.dp),
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(28.dp)) {
                    val frameWidth = size.width * 0.72f
                    val frameHeight = size.height * 0.84f
                    val frameTopLeft = Offset((size.width - frameWidth) / 2f, (size.height - frameHeight) / 2f)
                    drawRoundRect(
                        color = scheme.outlineVariant,
                        topLeft = frameTopLeft,
                        size = Size(frameWidth, frameHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(26f),
                        style = Stroke(2f),
                    )
                    val cx = size.width / 2f
                    val figure = scheme.onSurface.copy(alpha = 0.68f)
                    drawCircle(figure, 26f, Offset(cx, size.height * 0.24f))
                    drawLine(figure, Offset(cx, size.height * 0.33f), Offset(cx, size.height * 0.66f), 42f, StrokeCap.Round)
                    drawLine(figure, Offset(cx, size.height * 0.42f), Offset(cx - 66f, size.height * 0.55f), 16f, StrokeCap.Round)
                    drawLine(figure, Offset(cx, size.height * 0.42f), Offset(cx + 66f, size.height * 0.55f), 16f, StrokeCap.Round)
                    drawLine(figure, Offset(cx - 10f, size.height * 0.65f), Offset(cx - 34f, size.height * 0.87f), 20f, StrokeCap.Round)
                    drawLine(figure, Offset(cx + 10f, size.height * 0.65f), Offset(cx + 34f, size.height * 0.87f), 20f, StrokeCap.Round)
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = scheme.primaryContainer,
                ) {
                    Text(
                        tr(language, "الجسم كامل داخل الإطار", "Keep your full body in frame"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        GuideLine("01", tr(language, "إضاءة متساوية وبدون ظل قوي", "Even lighting without harsh shadows"))
        GuideLine("02", tr(language, "قف طبيعيًا والذراعان بعيدتان قليلًا", "Stand naturally with arms slightly away"))
        GuideLine("03", tr(language, "الملابس القريبة من الجسم تعطي تقديرًا بصريًا أدق", "Close-fitting clothes give a cleaner visual body estimate"))

        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(tr(language, "فتح الاستوديو", "Open the studio"), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun GuideLine(code: String, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(8.dp), color = scheme.primaryContainer) {
            Text(
                code,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                fontWeight = FontWeight.Black,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
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
    BodyMeasurePoint.NECK -> tr(language, "لف الشريط حول قاعدة الرقبة أعلى عظمة الترقوة بقليل.", "Wrap the tape around the base of the neck, just above the collarbone.")
    BodyMeasurePoint.SHOULDERS -> tr(language, "قس من نهاية كتف إلى نهاية الكتف الآخر عبر أعلى الظهر.", "Measure from one shoulder tip to the other across the upper back.")
    BodyMeasurePoint.CHEST -> tr(language, "لف الشريط حول أعرض نقطة من الصدر مع إبقائه أفقيًا.", "Wrap the tape around the fullest part of the chest and keep it level.")
    BodyMeasurePoint.WAIST -> tr(language, "قس حول الخصر الطبيعي، عادةً أضيق جزء من الجذع، بدون شد البطن.", "Measure around the natural waist, usually the narrowest torso point, without sucking in.")
    BodyMeasurePoint.HIPS -> tr(language, "لف الشريط حول أعرض نقطة من الوركين والمؤخرة بشكل أفقي.", "Wrap the tape around the fullest part of the hips and seat, keeping it level.")
    BodyMeasurePoint.ARM_LENGTH -> tr(language, "ابدأ من نقطة الكتف، مر فوق كوع مثني قليلًا، وانته عند عظمة المعصم.", "Start at the shoulder point, pass over a slightly bent elbow, and finish at the wrist bone.")
    BodyMeasurePoint.WRIST -> tr(language, "لف الشريط حول المعصم عند العظمة البارزة بدون ضغط.", "Wrap the tape around the wrist at the wrist bone without compressing it.")
    BodyMeasurePoint.HAND -> tr(language, "لف الشريط حول أعرض جزء من راحة اليد عند مفاصل الأصابع مع استثناء الإبهام.", "Wrap the tape around the widest part of the hand at the knuckles, excluding the thumb.")
    BodyMeasurePoint.THIGH -> tr(language, "قس محيط أعرض جزء من أعلى الفخذ أثناء الوقوف طبيعيًا.", "Measure around the fullest part of the upper thigh while standing naturally.")
    BodyMeasurePoint.INSEAM -> tr(language, "قس من أعلى نقطة داخل الساق عند المنشعب إلى الأرض أو طول البنطال المطلوب.", "Measure from the top of the inner leg at the crotch to the floor or desired trouser length.")
    BodyMeasurePoint.CALF -> tr(language, "لف الشريط حول أعرض نقطة من عضلة الساق.", "Wrap the tape around the fullest part of the calf.")
    BodyMeasurePoint.FOOT -> tr(language, "قف على ورقة وقس من مؤخرة الكعب إلى نهاية أطول إصبع.", "Stand on paper and measure from the back of the heel to the longest toe.")
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

private fun number(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else "%.1f".format(Locale.US, value)

private fun normalizeDegrees(value: Float): Float {
    var normalized = value % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}
