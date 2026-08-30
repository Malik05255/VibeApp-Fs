package com.almi.ai.ui.v12

import android.view.SurfaceView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.guidedMeasurementOrder
import kotlin.math.roundToInt

private const val REF_INCH_TO_CM = 2.54f
private const val REF_POUND_TO_KG = 0.45359237f

private val MeasureInk = Color(0xFF173657)
private val MeasureBlue = Color(0xFF45A9F5)
private val MeasureIce = Color(0xFFF2FAFF)
private val MeasureRed = Color(0xFFFF3C57)
private val MeasurePink = Color(0xFFFF86AA)

private data class RefMarker(val anchor: String, val point: BodyMeasurePoint)

private val refMarkers = listOf(
    RefMarker("neck", BodyMeasurePoint.NECK),
    RefMarker("shoulderCenter", BodyMeasurePoint.SHOULDERS),
    RefMarker("rightShoulder", BodyMeasurePoint.SHOULDER_LENGTH),
    RefMarker("chest", BodyMeasurePoint.CHEST),
    RefMarker("underbust", BodyMeasurePoint.UNDERBUST),
    RefMarker("leftBust", BodyMeasurePoint.BUST_HEIGHT),
    RefMarker("rightBust", BodyMeasurePoint.BUST_POINT_DISTANCE),
    RefMarker("waist", BodyMeasurePoint.WAIST),
    RefMarker("abdomen", BodyMeasurePoint.ABDOMEN),
    RefMarker("hips", BodyMeasurePoint.HIPS),
    RefMarker("leftShoulder", BodyMeasurePoint.DRESS_LENGTH),
    RefMarker("rightElbow", BodyMeasurePoint.ARM_LENGTH),
    RefMarker("rightUpperArm", BodyMeasurePoint.UPPER_ARM),
    RefMarker("rightHand", BodyMeasurePoint.WRIST),
)

@Composable
internal fun V12ReferenceBodyScreen(
    language: String,
    profile: BodyProfile,
    presentation: AvatarPresentation,
    onPresentation: (AvatarPresentation) -> Unit,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onBack: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        var rendererState by remember(presentation) { mutableStateOf(V12BodyRendererState.LOADING) }
        var projection by remember(presentation) { mutableStateOf<V12BodyProjection?>(null) }
        var runtime by remember(presentation) { mutableStateOf<V12BodyRuntime?>(null) }
        var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = selectedName?.let { name -> BodyMeasurePoint.entries.firstOrNull { it.name == name } }

        LaunchedEffect(presentation) {
            rendererState = V12BodyRendererState.LOADING
            projection = null
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFEAF7FF), Color(0xFFFAFDFF), Color(0xFFF0FAFF)),
                    ),
                )
                .statusBarsPadding(),
        ) {
            ReferenceMeasurementBackdrop()

            key(presentation) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        SurfaceView(context).also { surface ->
                            runtime = V12BodyRuntime(
                                context = context,
                                surfaceView = surface,
                                presentation = presentation,
                                onStateChanged = { state -> surface.post { rendererState = state } },
                                onProjectionChanged = { value -> surface.post { projection = value } },
                            ).also {
                                it.initialize()
                                it.start()
                            }
                        }
                    },
                )
            }

            DisposableEffect(presentation) {
                onDispose {
                    runtime?.stop()
                    runtime = null
                }
            }

            // Reference-style blue holographic wash over the real interactive Filament human.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF60C8FF).copy(alpha = .22f),
                            Color(0xFF8ED9FF).copy(alpha = .10f),
                            Color.Transparent,
                        ),
                        center = Offset.Unspecified,
                        radius = 950f,
                    ),
                ),
            )

            ReferenceBodyHeader(
                language = language,
                completed = profile.completedMeasurements,
                total = guidedMeasurementOrder.size,
                onBack = onBack,
            )

            val currentText = if (selected == null) {
                if (language == "ar") "القياس الحالي: ${format0(profile.weightKilograms)} كجم" else "CURRENT: ${format0(profile.weightKilograms)} kg"
            } else {
                val value = profile.measurementsInches[selected]
                val label = measureLabel(selected, language)
                if (value == null) label else "$label: ${format1(value * REF_INCH_TO_CM)} cm"
            }
            Text(
                currentText,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 154.dp),
                color = MeasureInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            ReferenceGenderSwitch(
                presentation = presentation,
                language = language,
                onPresentation = onPresentation,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 150.dp, end = 16.dp),
            )

            projection?.let { value ->
                refMarkers.forEach { marker ->
                    val point = value.points[marker.anchor] ?: return@forEach
                    if (!point.visible) return@forEach
                    val active = selected == marker.point
                    val done = marker.point in profile.measurementsInches
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(point.x.roundToInt() - 15, point.y.roundToInt() - 15) }
                            .size(if (active) 30.dp else 24.dp)
                            .clickable {
                                selectedName = marker.point.name
                                runtime?.focusOn(point.y)
                            },
                        shape = CircleShape,
                        color = MeasureRed,
                        border = BorderStroke(if (active) 4.dp else 2.dp, Color.White),
                        shadowElevation = if (active) 10.dp else 5.dp,
                    ) {
                        if (done) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            when (rendererState) {
                V12BodyRendererState.LOADING -> Surface(
                    modifier = Modifier.align(Alignment.Center).size(76.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = .85f),
                    border = BorderStroke(1.dp, MeasureBlue.copy(alpha = .25f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeasureBlue, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                V12BodyRendererState.ERROR -> Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White.copy(alpha = .92f),
                    border = BorderStroke(1.dp, MeasureRed.copy(alpha = .35f)),
                ) {
                    Text(
                        if (language == "ar") "تعذر تحميل المجسم" else "BODY LOAD FAILED",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        color = MeasureRed,
                        fontWeight = FontWeight.Bold,
                    )
                }
                V12BodyRendererState.READY -> Unit
            }

            ReferenceMeasurementEditor(
                language = language,
                profile = profile,
                selected = selected,
                onWeightChanged = onWeightChanged,
                onMeasurementChanged = onMeasurementChanged,
                onMeasurementCleared = onMeasurementCleared,
                onHeightChanged = onHeightChanged,
                onSelectWeight = {
                    selectedName = null
                    runtime?.resetView()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ReferenceBodyHeader(language: String, completed: Int, total: Int, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.TopStart).clickable(onClick = onBack),
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = .80f),
            border = BorderStroke(1.dp, MeasureBlue.copy(alpha = .25f)),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (language == "ar") "تم" else "Done", color = MeasureInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("✓", color = MeasureInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI / FILAMENT", color = Color(0xFF79B2DD), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .9.sp)
            Text(if (language == "ar") "قياسات جسمك" else "YOUR BODY", color = MeasureInk, fontSize = 31.sp, fontWeight = FontWeight.Black)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(190.dp).height(6.dp).background(Color(0xFFDDEFFC), RoundedCornerShape(99.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth((completed.toFloat() / total.toFloat()).coerceIn(.04f, 1f))
                            .height(6.dp)
                            .background(MeasureBlue, RoundedCornerShape(99.dp)),
                    )
                }
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE3F2FE)) {
                    Text("$completed/$total", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MeasureInk, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ReferenceGenderSwitch(
    presentation: AvatarPresentation,
    language: String,
    onPresentation: (AvatarPresentation) -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = .84f),
        border = BorderStroke(1.dp, MeasureBlue.copy(alpha = .20f)),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            RefGenderMini(if (language == "ar") "ذكر" else "M", presentation == AvatarPresentation.MASCULINE, MeasureBlue) { onPresentation(AvatarPresentation.MASCULINE) }
            RefGenderMini(if (language == "ar") "أنثى" else "F", presentation == AvatarPresentation.FEMININE, MeasurePink) { onPresentation(AvatarPresentation.FEMININE) }
        }
    }
}

@Composable
private fun RefGenderMini(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (active) accent else Color.Transparent,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = if (active) Color.White else MeasureInk, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReferenceMeasurementEditor(
    language: String,
    profile: BodyProfile,
    selected: BodyMeasurePoint?,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onHeightChanged: (Float) -> Unit,
    onSelectWeight: () -> Unit,
    modifier: Modifier,
) {
    val initialValue = if (selected == null) profile.weightKilograms else profile.measurementsInches[selected]?.times(REF_INCH_TO_CM)
    var text by remember(selected, initialValue) { mutableStateOf(initialValue?.let(::format1) ?: "") }
    val title = if (selected == null) {
        if (language == "ar") "الوزن" else "WEIGHT"
    } else measureLabel(selected, language)
    val unit = if (selected == null) "kg" else "cm"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .92f),
        border = BorderStroke(1.dp, MeasureBlue.copy(alpha = .24f)),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(52.dp).clickable {
                        val value = text.toFloatOrNull() ?: return@clickable
                        if (selected == null) onWeightChanged(value / REF_POUND_TO_KG)
                        else onMeasurementChanged(selected, value / REF_INCH_TO_CM)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MeasureBlue,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    }
                }
                Surface(
                    modifier = Modifier.size(52.dp).clickable {
                        if (selected == null) {
                            text = format1(profile.weightKilograms)
                        } else {
                            onMeasurementCleared(selected)
                            text = ""
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF7FBFE),
                    border = BorderStroke(1.dp, MeasureBlue.copy(alpha = .22f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", color = MeasureInk, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF7FBFE),
                    border = BorderStroke(1.dp, MeasureBlue.copy(alpha = .24f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(unit, color = MeasureInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        BasicTextField(
                            value = text,
                            onValueChange = { value -> text = value.filter { it.isDigit() || it == '.' }.take(6) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(color = MeasureInk, fontSize = 25.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(MeasureBlue),
                            modifier = Modifier.width(70.dp),
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(title, color = MeasureInk, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                Text(
                    if (selected == null) {
                        if (language == "ar") "يتفاعل الجسم مباشرة" else "BODY UPDATES LIVE"
                    } else {
                        if (language == "ar") "اضغط النقطة الحمراء لتغيير القياس" else "TAP A RED POINT TO SWITCH"
                    },
                    color = MeasureInk.copy(alpha = .62f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.End,
                )
                if (selected != null) {
                    Text(
                        if (language == "ar") "الوزن ${format0(profile.weightKilograms)} كجم" else "WEIGHT ${format0(profile.weightKilograms)} kg",
                        modifier = Modifier.padding(top = 3.dp).clickable(onClick = onSelectWeight),
                        color = MeasureBlue,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    // Keep height callback reachable without adding another visual rail.
    @Suppress("UNUSED_VARIABLE")
    val keepHeightCallback = onHeightChanged
}

private fun measureLabel(point: BodyMeasurePoint, language: String): String {
    if (language != "ar") return point.name.replace('_', ' ')
    return when (point) {
        BodyMeasurePoint.NECK -> "محيط الرقبة"
        BodyMeasurePoint.SHOULDERS -> "عرض الكتفين"
        BodyMeasurePoint.SHOULDER_LENGTH -> "طول الكتف"
        BodyMeasurePoint.CHEST -> "محيط الصدر"
        BodyMeasurePoint.UNDERBUST -> "تحت الصدر"
        BodyMeasurePoint.BUST_HEIGHT -> "ارتفاع الصدر"
        BodyMeasurePoint.BUST_POINT_DISTANCE -> "المسافة بين الصدر"
        BodyMeasurePoint.WAIST -> "محيط الخصر"
        BodyMeasurePoint.ABDOMEN -> "محيط البطن"
        BodyMeasurePoint.HIPS -> "محيط الحوض"
        BodyMeasurePoint.DRESS_LENGTH -> "طول اللباس"
        BodyMeasurePoint.ARM_LENGTH -> "طول الذراع"
        BodyMeasurePoint.UPPER_ARM -> "محيط العضد"
        BodyMeasurePoint.WRIST -> "محيط المعصم"
        BodyMeasurePoint.HAND -> "طول اليد"
        BodyMeasurePoint.THIGH -> "محيط الفخذ"
        BodyMeasurePoint.INSEAM -> "طول الساق الداخلي"
        BodyMeasurePoint.CALF -> "محيط الساق"
        BodyMeasurePoint.FOOT -> "طول القدم"
    }
}

@Composable
private fun ReferenceMeasurementBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFFB7D9EF).copy(alpha = .23f)
        val step = 42f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .16f), Offset(x, size.height * .80f), 1f)
            x += step
        }
        var y = size.height * .16f
        while (y <= size.height * .80f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(MeasureBlue.copy(alpha = .08f), radius = size.minDimension * .60f, center = Offset(size.width * .50f, size.height * .50f))
        drawCircle(Color.White.copy(alpha = .75f), radius = size.minDimension * .36f, center = Offset(size.width * .50f, size.height * .52f), style = Stroke(1.2f))
    }
}

private fun format0(value: Float): String = value.roundToInt().toString()
private fun format1(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)
