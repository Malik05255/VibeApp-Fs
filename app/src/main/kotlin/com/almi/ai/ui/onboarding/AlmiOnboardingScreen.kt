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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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

private enum class IntroStage {
    LANGUAGE,
    JOURNEY,
    AVATAR,
    PHOTO,
}

private data class BodyPointSpec(
    val point: BodyMeasurePoint,
    val x: Float,
    val y: Float,
)

private val bodyPoints = listOf(
    BodyPointSpec(BodyMeasurePoint.NECK, 0.50f, 0.19f),
    BodyPointSpec(BodyMeasurePoint.SHOULDERS, 0.37f, 0.25f),
    BodyPointSpec(BodyMeasurePoint.CHEST, 0.50f, 0.32f),
    BodyPointSpec(BodyMeasurePoint.WAIST, 0.50f, 0.44f),
    BodyPointSpec(BodyMeasurePoint.HIPS, 0.50f, 0.52f),
    BodyPointSpec(BodyMeasurePoint.ARM_LENGTH, 0.27f, 0.37f),
    BodyPointSpec(BodyMeasurePoint.WRIST, 0.21f, 0.55f),
    BodyPointSpec(BodyMeasurePoint.HAND, 0.18f, 0.61f),
    BodyPointSpec(BodyMeasurePoint.THIGH, 0.42f, 0.66f),
    BodyPointSpec(BodyMeasurePoint.INSEAM, 0.50f, 0.65f),
    BodyPointSpec(BodyMeasurePoint.CALF, 0.42f, 0.82f),
    BodyPointSpec(BodyMeasurePoint.FOOT, 0.43f, 0.95f),
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        AtelierBackdrop()
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(260)) + scaleIn(tween(330), initialScale = 0.975f)) togetherWith
                    (fadeOut(tween(170)) + scaleOut(tween(220), targetScale = 1.018f))
            },
            label = "almi-onboarding",
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

                IntroStage.PHOTO -> PhotoJourneyGuide(
                    language = language,
                    onComplete = onComplete,
                )
            }
        }
    }
}

@Composable
private fun AtelierBackdrop() {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "atelier-lines")
    val drift by motion.animateFloat(
        initialValue = -0.04f,
        targetValue = 0.04f,
        animationSpec = infiniteRepeatable(tween(8_500), RepeatMode.Reverse),
        label = "atelier-drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val grid = scheme.outlineVariant.copy(alpha = 0.28f)
        val major = scheme.primary.copy(alpha = 0.08f)
        val step = size.width / 7f
        var x = -step
        while (x < size.width + step) {
            drawLine(grid, Offset(x + drift * size.width, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = step
        while (y < size.height) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(major, Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.minDimension * 0.75f,
            ),
            radius = size.minDimension * 0.75f,
            center = Offset(size.width * 0.5f, size.height * 0.42f),
        )
    }
}

@Composable
private fun LanguagePrompt(
    onArabic: () -> Unit,
    onEnglish: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TechnicalMark()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        textAlign = TextAlign.Center,
                    )
                }
                Button(onClick = onArabic, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("العربية", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onEnglish, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("English", fontWeight = FontWeight.Bold)
                }
                Text(
                    "ALMI / BODY-ACCURATE TRY-ON",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TechnicalMark() {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.size(78.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(scheme.primary.copy(alpha = 0.12f), size.minDimension * 0.46f, c)
        drawCircle(scheme.primary, size.minDimension * 0.31f, c, style = Stroke(2.8f))
        drawLine(scheme.primary, Offset(c.x, size.height * 0.08f), Offset(c.x, size.height * 0.92f), 2f)
        drawLine(scheme.primary, Offset(size.width * 0.08f, c.y), Offset(size.width * 0.92f, c.y), 2f)
        drawCircle(scheme.primary, 5f, c)
    }
}

@Composable
private fun JourneyPrompt(
    language: String,
    onAvatar: () -> Unit,
    onPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ALMI / 01", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            tr(language, "كيف تود أن نكمل رحلتك؟", "How should we build your fitting journey?"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            tr(
                language,
                "يمكنك بناء مجسم بقياساتك الدقيقة، أو البدء مباشرة بصورتك الشخصية.",
                "Build a measurement-aware body model, or continue directly with your own photo.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        JourneyCard(
            code = "BODY / 360",
            title = tr(language, "مجسمي التفاعلي", "Interactive body model"),
            description = tr(
                language,
                "دوران 360°، نقاط قياس، ملف جسم محفوظ، وتعليمات دقيقة لكل قياس.",
                "360° rotation, measurement hotspots, a saved body profile, and guided measuring.",
            ),
            emphasized = true,
            onClick = onAvatar,
        )
        JourneyCard(
            code = "PHOTO / LIVE",
            title = tr(language, "صورتي الشخصية", "My personal photo"),
            description = tr(
                language,
                "ابدأ بصورة كاملة للجسم، ويمكنك إضافة القياسات لاحقًا في أي وقت.",
                "Start with a full-body photo and add measurements later whenever you want.",
            ),
            emphasized = false,
            onClick = onPhoto,
        )

        Spacer(Modifier.height(12.dp))
        Text(
            tr(language, "يمكن تغيير المسار لاحقًا من ملف الجسم.", "You can change this later from your body profile."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = RoundedCornerShape(22.dp),
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.62f) else scheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, if (emphasized) scheme.primary.copy(alpha = 0.62f) else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(code, style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(34.dp).height(2.dp).background(scheme.primary))
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
    var yaw by rememberSaveable { mutableFloatStateOf(0f) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { BodyMeasurePoint.valueOf(it) }.getOrNull() }
    var heightText by remember(profile.heightInches) { mutableStateOf(number(profile.heightInches)) }
    var weightText by remember(profile.weightPounds) { mutableStateOf(number(profile.weightPounds)) }

    val selectedSpec = selected?.let { point -> bodyPoints.firstOrNull { it.point == point } }
    val focusX = selectedSpec?.x ?: 0.5f
    val focusY = selectedSpec?.y ?: 0.5f
    val zoom by animateFloatAsState(if (selected == null) 1f else 1.34f, tween(340), label = "body-focus")

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
                Text("ALMI / BODY LAB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    tr(language, "ملف جسمك", "Your body profile"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactNumberField(
                        value = heightText,
                        onValueChange = { next ->
                            heightText = next
                            next.toFloatOrNull()?.let(onHeightChanged)
                        },
                        label = tr(language, "الطول", "Height"),
                        suffix = "in",
                        modifier = Modifier.weight(1f),
                    )
                    CompactNumberField(
                        value = weightText,
                        onValueChange = { next ->
                            weightText = next
                            next.toFloatOrNull()?.let(onWeightChanged)
                        },
                        label = tr(language, "الوزن", "Weight"),
                        suffix = "lb",
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    tr(
                        language,
                        "اسحب المجسم يمينًا ويسارًا للدوران. اضغط النقطة الحمراء لقياس الجزء المحدد.",
                        "Drag the model left or right to rotate. Tap a red hotspot to measure that area.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("360° BODY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    Text(
                        "${normalizeDegrees(yaw).roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(510.dp)
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            transformOrigin = TransformOrigin(focusX, focusY)
                        }
                ) {
                    InteractiveMannequin(
                        yawDegrees = yaw,
                        profile = profile,
                        onYawDelta = { delta -> yaw = normalizeDegrees(yaw + delta) },
                        onSelectPoint = { point -> selectedName = point.name },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ViewSnapButton(tr(language, "أمامي", "Front"), Modifier.weight(1f)) { yaw = 0f }
                    ViewSnapButton(tr(language, "جانبي", "Side"), Modifier.weight(1f)) { yaw = 90f }
                    ViewSnapButton(tr(language, "خلفي", "Back"), Modifier.weight(1f)) { yaw = 180f }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
        ) {
            Text(
                tr(
                    language,
                    "كل قيمة تحفظ تلقائيًا على جهازك. يمكنك إكمال القياسات الآن أو متابعة الاستوديو والعودة لها لاحقًا.",
                    "Every value is saved locally. Finish the profile now or continue to the studio and return later.",
                ),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(
                if (profile.completedMeasurements == BodyMeasurePoint.entries.size) {
                    tr(language, "الانتقال إلى الاستوديو", "Enter the studio")
                } else {
                    tr(language, "حفظ والمتابعة إلى الاستوديو", "Save and continue to studio")
                },
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
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
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            val filtered = next.filter { it.isDigit() || it == '.' }.take(6)
            onValueChange(filtered)
        },
        label = { Text(label) },
        suffix = { Text(suffix, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
        initialValue = 0.992f,
        targetValue = 1.008f,
        animationSpec = infiniteRepeatable(tween(1_900), RepeatMode.Reverse),
        label = "body-breath",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    onYawDelta(dragAmount * 0.72f)
                }
            }
    ) {
        val radians = Math.toRadians(yawDegrees.toDouble())
        val face = abs(cos(radians)).toFloat()
        val side = sin(radians).toFloat()
        val horizontalCompression = 0.46f + 0.54f * face

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleY = breath }
        ) {
            val centerX = size.width / 2f
            val h = size.height * 0.84f
            val top = size.height * 0.055f
            val heightFactor = (profile.heightInches / 68f).coerceIn(0.84f, 1.16f)
            val weightFactor = (profile.weightPounds / 165f).coerceIn(0.70f, 1.55f)
            val torsoWidth = size.width * 0.29f * horizontalCompression * (0.86f + weightFactor * 0.14f)
            val hipWidth = torsoWidth * (0.88f + weightFactor * 0.08f)
            val shoulderY = top + h * 0.22f
            val waistY = top + h * 0.44f
            val hipY = top + h * 0.52f
            val crotchY = top + h * 0.59f
            val kneeY = top + h * 0.78f
            val footY = top + h * 1.00f
            val headCenter = Offset(centerX + side * size.width * 0.015f, top + h * 0.09f)
            val headRadius = h * 0.065f * (0.98f + (heightFactor - 1f) * 0.08f)

            val grid = scheme.outlineVariant.copy(alpha = 0.35f)
            repeat(5) { index ->
                val radius = size.width * (0.16f + index * 0.075f)
                drawOval(
                    color = grid,
                    topLeft = Offset(centerX - radius, hipY - radius * 0.28f),
                    size = Size(radius * 2f, radius * 0.56f),
                    style = Stroke(1f),
                )
            }
            drawLine(grid, Offset(centerX, 0f), Offset(centerX, size.height), 1f)

            val bodyFill = Brush.linearGradient(
                listOf(
                    scheme.onSurface.copy(alpha = 0.92f),
                    scheme.onSurfaceVariant.copy(alpha = 0.62f),
                    scheme.onSurface.copy(alpha = 0.88f),
                ),
                start = Offset(centerX - torsoWidth, 0f),
                end = Offset(centerX + torsoWidth, 0f),
            )

            drawCircle(bodyFill, headRadius, headCenter)
            drawLine(
                color = scheme.onSurface.copy(alpha = 0.82f),
                start = Offset(centerX, headCenter.y + headRadius * 0.85f),
                end = Offset(centerX, shoulderY - h * 0.025f),
                strokeWidth = h * 0.055f,
                cap = StrokeCap.Round,
            )

            val torso = Path().apply {
                moveTo(centerX - torsoWidth, shoulderY)
                cubicTo(
                    centerX - torsoWidth * 0.92f,
                    shoulderY + h * 0.10f,
                    centerX - torsoWidth * 0.67f,
                    waistY - h * 0.04f,
                    centerX - torsoWidth * 0.62f,
                    waistY,
                )
                cubicTo(
                    centerX - hipWidth,
                    hipY - h * 0.015f,
                    centerX - hipWidth * 0.90f,
                    crotchY,
                    centerX,
                    crotchY,
                )
                cubicTo(
                    centerX + hipWidth * 0.90f,
                    crotchY,
                    centerX + hipWidth,
                    hipY - h * 0.015f,
                    centerX + torsoWidth * 0.62f,
                    waistY,
                )
                cubicTo(
                    centerX + torsoWidth * 0.67f,
                    waistY - h * 0.04f,
                    centerX + torsoWidth * 0.92f,
                    shoulderY + h * 0.10f,
                    centerX + torsoWidth,
                    shoulderY,
                )
                close()
            }
            drawPath(torso, bodyFill)

            val armStroke = h * (0.055f + weightFactor * 0.010f)
            val shoulderSpan = torsoWidth * 0.93f
            val handShift = side * size.width * 0.025f
            drawLine(
                scheme.onSurface.copy(alpha = 0.86f),
                Offset(centerX - shoulderSpan, shoulderY + h * 0.02f),
                Offset(centerX - shoulderSpan * 1.42f + handShift, waistY + h * 0.19f),
                armStroke,
                StrokeCap.Round,
            )
            drawLine(
                scheme.onSurface.copy(alpha = 0.76f),
                Offset(centerX + shoulderSpan, shoulderY + h * 0.02f),
                Offset(centerX + shoulderSpan * 1.42f + handShift, waistY + h * 0.19f),
                armStroke,
                StrokeCap.Round,
            )

            val legGap = hipWidth * 0.37f
            val legStroke = h * (0.078f + weightFactor * 0.012f)
            drawLine(
                scheme.onSurface.copy(alpha = 0.88f),
                Offset(centerX - legGap, crotchY),
                Offset(centerX - legGap * 0.86f + side * size.width * 0.015f, footY - h * 0.04f),
                legStroke,
                StrokeCap.Round,
            )
            drawLine(
                scheme.onSurface.copy(alpha = 0.78f),
                Offset(centerX + legGap, crotchY),
                Offset(centerX + legGap * 0.86f + side * size.width * 0.015f, footY - h * 0.04f),
                legStroke,
                StrokeCap.Round,
            )
            drawLine(
                scheme.onSurface.copy(alpha = 0.74f),
                Offset(centerX - legGap * 0.90f, footY - h * 0.03f),
                Offset(centerX - legGap * 0.90f - torsoWidth * 0.30f, footY),
                h * 0.035f,
                StrokeCap.Round,
            )
            drawLine(
                scheme.onSurface.copy(alpha = 0.68f),
                Offset(centerX + legGap * 0.90f, footY - h * 0.03f),
                Offset(centerX + legGap * 0.90f + torsoWidth * 0.30f, footY),
                h * 0.035f,
                StrokeCap.Round,
            )

            val backness = if (cos(radians) < 0) 1f else 0f
            if (backness > 0f) {
                drawLine(
                    scheme.background.copy(alpha = 0.38f),
                    Offset(centerX, shoulderY + h * 0.05f),
                    Offset(centerX, waistY + h * 0.02f),
                    2f,
                )
            } else {
                drawLine(
                    scheme.background.copy(alpha = 0.28f),
                    Offset(centerX - torsoWidth * 0.60f, shoulderY + h * 0.12f),
                    Offset(centerX + torsoWidth * 0.60f, shoulderY + h * 0.12f),
                    2f,
                )
            }

            drawLine(
                scheme.primary.copy(alpha = 0.72f),
                Offset(centerX - size.width * 0.15f, size.height * 0.985f),
                Offset(centerX + size.width * 0.15f, size.height * 0.985f),
                2f,
            )
        }

        bodyPoints.forEach { spec ->
            val compressedX = 0.5f + (spec.x - 0.5f) * horizontalCompression
            val x = maxWidth * compressedX
            val y = maxHeight * spec.y
            val measured = profile.measurementsInches[spec.point]
            Hotspot(
                measuredInches = measured,
                modifier = Modifier.offset(x = x - 14.dp, y = y - 14.dp),
                onClick = { onSelectPoint(spec.point) },
            )
        }
    }
}

@Composable
private fun Hotspot(
    measuredInches: Float?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = rememberInfiniteTransition(label = "hotspot")
    val pulse by motion.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "hotspot-pulse",
    )
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer {
                    scaleX = if (measuredInches == null) pulse else 1f
                    scaleY = if (measuredInches == null) pulse else 1f
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .background(scheme.error.copy(alpha = 0.15f), CircleShape)
            )
            Box(
                Modifier
                    .size(8.dp)
                    .background(scheme.error, CircleShape)
            )
        }
        AnimatedVisibility(visible = measuredInches != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = scheme.surface.copy(alpha = 0.94f),
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
private fun ViewSnapButton(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
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
    var useCm by rememberSaveable(point) { mutableStateOf(false) }
    var value by remember(point, existingInches, useCm) {
        mutableStateOf(
            existingInches?.let { inches ->
                number(if (useCm) inches * 2.54f else inches)
            }.orEmpty()
        )
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
                MiniRuler(useCm = useCm)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitButton("in", selected = !useCm, Modifier.weight(1f)) {
                        if (useCm) {
                            value = value.toFloatOrNull()?.let { number(it / 2.54f) }.orEmpty()
                        }
                        useCm = false
                    }
                    UnitButton("cm", selected = useCm, Modifier.weight(1f)) {
                        if (!useCm) {
                            value = value.toFloatOrNull()?.let { number(it * 2.54f) }.orEmpty()
                        }
                        useCm = true
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { next -> value = next.filter { it.isDigit() || it == '.' }.take(7) },
                    label = { Text(tr(language, "أدخل القياس", "Enter measurement")) },
                    suffix = { Text(if (useCm) "cm" else "in") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr(
                        language,
                        "اجعل شريط القياس ملاصقًا للجسم بدون ضغط على الجلد أو ترك فراغ.",
                        "Keep the tape against the body without compressing the skin or leaving slack.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                onClear?.let { clear ->
                    TextButton(onClick = clear) {
                        Text(tr(language, "حذف هذا القياس", "Remove this measurement"))
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
                Text(tr(language, "حفظ القياس", "Save measurement"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(language, "إلغاء", "Cancel"))
            }
        },
    )
}

@Composable
private fun UnitButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
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
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            val divisions = if (useCm) 20 else 16
            val spacing = size.width / divisions
            repeat(divisions + 1) { index ->
                val major = index % 4 == 0
                val tick = if (major) size.height * 0.72f else size.height * 0.42f
                val x = index * spacing
                drawLine(
                    scheme.onSurface.copy(alpha = if (major) 0.82f else 0.42f),
                    Offset(x, size.height),
                    Offset(x, size.height - tick),
                    if (major) 2f else 1f,
                )
            }
            drawLine(
                scheme.primary,
                Offset(0f, size.height - 2f),
                Offset(size.width, size.height - 2f),
                2f,
            )
        }
    }
}

@Composable
private fun PhotoJourneyGuide(
    language: String,
    onComplete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("ALMI / PHOTO SETUP", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            tr(language, "جهّز صورة مناسبة للتجربة", "Prepare a fitting-ready photo"),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            tr(
                language,
                "سنفتح لك الكاميرا أو المعرض داخل الاستوديو. أفضل نتيجة تبدأ بصورة واضحة وكاملة للجسم.",
                "Camera and gallery options are available in the studio. The best result starts with a clear full-body image.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            modifier = Modifier.fillMaxWidth().height(340.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(28.dp)) {
                    val frame = schemeFrame(this.size, MaterialTheme.colorScheme.primary)
                    drawRoundRect(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        topLeft = frame.first,
                        size = frame.second,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(28f, 28f),
                        style = Stroke(2f),
                    )
                    val cx = size.width / 2f
                    drawCircle(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f), 28f, Offset(cx, size.height * 0.25f))
                    drawLine(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f),
                        Offset(cx, size.height * 0.33f),
                        Offset(cx, size.height * 0.67f),
                        44f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        Offset(cx, size.height * 0.42f),
                        Offset(cx - 70f, size.height * 0.56f),
                        18f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        Offset(cx, size.height * 0.42f),
                        Offset(cx + 70f, size.height * 0.56f),
                        18f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        Offset(cx - 12f, size.height * 0.66f),
                        Offset(cx - 35f, size.height * 0.87f),
                        22f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        Offset(cx + 12f, size.height * 0.66f),
                        Offset(cx + 35f, size.height * 0.87f),
                        22f,
                        StrokeCap.Round,
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
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
        GuideLine("02", tr(language, "قف بشكل طبيعي والذراعان بعيدتان قليلًا", "Stand naturally with arms slightly away"))
        GuideLine("03", tr(language, "ملابس قريبة من الجسم تعطي دقة أفضل", "Close-fitting clothes improve body-shape accuracy"))

        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(tr(language, "فتح الاستوديو", "Open the studio"), fontWeight = FontWeight.Bold)
        }
    }
}

private fun schemeFrame(size: Size, @Suppress("UNUSED_PARAMETER") accent: Color): Pair<Offset, Size> {
    val w = size.width * 0.72f
    val h = size.height * 0.82f
    return Offset((size.width - w) / 2f, (size.height - h) / 2f) to Size(w, h)
}

@Composable
private fun GuideLine(code: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                code,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
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
    BodyMeasurePoint.NECK -> tr(language, "لف شريط القياس حول قاعدة الرقبة، أعلى عظمة الترقوة بقليل.", "Wrap the tape around the base of the neck, just above the collarbone.")
    BodyMeasurePoint.SHOULDERS -> tr(language, "قس من نقطة نهاية الكتف إلى نقطة نهاية الكتف الآخر عبر أعلى الظهر.", "Measure from one shoulder tip to the other across the upper back.")
    BodyMeasurePoint.CHEST -> tr(language, "لف الشريط حول أعرض نقطة من الصدر مع إبقائه أفقيًا.", "Wrap the tape around the fullest part of the chest and keep it level.")
    BodyMeasurePoint.WAIST -> tr(language, "قس حول الخصر الطبيعي؛ عادةً أضيق جزء من الجذع، بدون شد البطن.", "Measure around the natural waist, usually the narrowest part of the torso, without sucking in.")
    BodyMeasurePoint.HIPS -> tr(language, "لف الشريط حول أعرض نقطة من الوركين والمؤخرة مع إبقائه أفقيًا.", "Wrap the tape around the fullest part of the hips and seat, keeping it level.")
    BodyMeasurePoint.ARM_LENGTH -> tr(language, "ابدأ من نقطة الكتف، مرّ فوق الكوع المثني قليلًا، وانتهِ عند عظمة المعصم.", "Start at the shoulder point, pass over a slightly bent elbow, and finish at the wrist bone.")
    BodyMeasurePoint.WRIST -> tr(language, "لف الشريط حول المعصم عند العظمة البارزة بدون ضغط.", "Wrap the tape around the wrist at the wrist bone without compressing it.")
    BodyMeasurePoint.HAND -> tr(language, "لف الشريط حول أعرض جزء من راحة اليد عند مفاصل الأصابع، مع استثناء الإبهام.", "Wrap the tape around the widest part of the hand at the knuckles, excluding the thumb.")
    BodyMeasurePoint.THIGH -> tr(language, "قس محيط أعرض جزء من أعلى الفخذ مع الوقوف بشكل طبيعي.", "Measure around the fullest part of the upper thigh while standing naturally.")
    BodyMeasurePoint.INSEAM -> tr(language, "قس من أعلى نقطة داخل الساق عند المنشعب نزولًا حتى الأرض أو الطول المطلوب للبنطال.", "Measure from the top of the inner leg at the crotch down to the floor or desired trouser length.")
    BodyMeasurePoint.CALF -> tr(language, "لف الشريط حول أعرض نقطة من عضلة الساق.", "Wrap the tape around the fullest part of the calf.")
    BodyMeasurePoint.FOOT -> tr(language, "قف على ورقة وقس من مؤخرة الكعب إلى نهاية أطول إصبع.", "Stand on a sheet of paper and measure from the back of the heel to the longest toe.")
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
