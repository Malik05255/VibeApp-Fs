package com.almi.ai.ui.v12

import android.view.SurfaceView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

private const val HERO_INCH_TO_CM = 2.54f
private const val HERO_POUND_TO_KG = 0.45359237f

private val BodyInk = Color(0xFF153552)
private val BodyBlue = Color(0xFF46AEF3)
private val BodyCyan = Color(0xFF59DFF0)
private val BodyRed = Color(0xFFFF405B)
private val BodyMint = Color(0xFF58D7C5)
private val BodyGlass = Color(0xF2FFFFFF)

private data class HeroBodyMarker(val anchor: String, val point: BodyMeasurePoint)

private val heroBodyMarkers = listOf(
    HeroBodyMarker("neck", BodyMeasurePoint.NECK),
    HeroBodyMarker("shoulderCenter", BodyMeasurePoint.SHOULDERS),
    HeroBodyMarker("rightShoulder", BodyMeasurePoint.SHOULDER_LENGTH),
    HeroBodyMarker("chest", BodyMeasurePoint.CHEST),
    HeroBodyMarker("underbust", BodyMeasurePoint.UNDERBUST),
    HeroBodyMarker("leftBust", BodyMeasurePoint.BUST_HEIGHT),
    HeroBodyMarker("rightBust", BodyMeasurePoint.BUST_POINT_DISTANCE),
    HeroBodyMarker("waist", BodyMeasurePoint.WAIST),
    HeroBodyMarker("abdomen", BodyMeasurePoint.ABDOMEN),
    HeroBodyMarker("hips", BodyMeasurePoint.HIPS),
    HeroBodyMarker("leftShoulder", BodyMeasurePoint.DRESS_LENGTH),
    HeroBodyMarker("rightElbow", BodyMeasurePoint.ARM_LENGTH),
    HeroBodyMarker("rightUpperArm", BodyMeasurePoint.UPPER_ARM),
    HeroBodyMarker("rightHand", BodyMeasurePoint.WRIST),
)

@Composable
internal fun V12HeroBodyScreen(
    language: String,
    profile: BodyProfile,
    presentation: AvatarPresentation,
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
                        listOf(
                            Color(0xFFE8F7FF),
                            Color(0xFFF9FDFF),
                            Color(0xFFF1FAFF),
                        ),
                    ),
                )
                .statusBarsPadding(),
        ) {
            HeroBodyBackdrop()

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

            // A cool translucent wash keeps the textured Digital Human in the same visual family
            // as the approved luminous-blue reference while preserving real anatomy and interaction.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF5CC9FF).copy(alpha = .19f),
                                Color(0xFF9DE7FF).copy(alpha = .09f),
                                Color.Transparent,
                            ),
                            center = Offset.Unspecified,
                            radius = 900f,
                        ),
                    ),
            )

            HeroBodyHeader(
                language = language,
                completed = profile.completedMeasurements,
                total = guidedMeasurementOrder.size,
                onBack = onBack,
            )

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 132.dp),
                shape = RoundedCornerShape(21.dp),
                color = Color.White.copy(alpha = .68f),
                border = BorderStroke(1.dp, BodyCyan.copy(alpha = .25f)),
            ) {
                Text(
                    if (language == "ar") "اسحب 360°  •  اضغط النقطة الحمراء" else "DRAG 360°  •  TAP A RED POINT",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = BodyInk.copy(alpha = .62f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .15.sp,
                )
            }

            val currentLabel = if (selected == null) {
                if (language == "ar") "القياس الحالي: ${heroFormat0(profile.weightKilograms)} كجم" else "CURRENT: ${heroFormat0(profile.weightKilograms)} kg"
            } else {
                val value = profile.measurementsInches[selected]
                val label = heroMeasureLabel(selected, language)
                if (value == null) label else "$label: ${heroFormat1(value * HERO_INCH_TO_CM)} cm"
            }
            Text(
                currentLabel,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 177.dp, start = 24.dp, end = 24.dp),
                color = BodyInk,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )

            projection?.let { value ->
                heroBodyMarkers.forEach { marker ->
                    val point = value.points[marker.anchor] ?: return@forEach
                    if (!point.visible) return@forEach
                    val active = selected == marker.point
                    val done = marker.point in profile.measurementsInches
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(point.x.roundToInt() - if (active) 18 else 14, point.y.roundToInt() - if (active) 18 else 14) }
                            .size(if (active) 36.dp else 28.dp)
                            .clickable {
                                selectedName = marker.point.name
                                runtime?.focusOn(point.y)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (active) {
                                drawCircle(BodyRed.copy(alpha = .17f), radius = size.minDimension * .50f)
                                drawCircle(BodyRed.copy(alpha = .34f), radius = size.minDimension * .41f, style = Stroke(2f))
                            }
                            drawCircle(BodyRed, radius = size.minDimension * .27f)
                            drawCircle(Color.White, radius = size.minDimension * .27f, style = Stroke(if (active) 4f else 2.5f))
                        }
                        if (done) {
                            Text("✓", color = Color.White, fontSize = if (active) 10.sp else 8.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            when (rendererState) {
                V12BodyRendererState.LOADING -> Surface(
                    modifier = Modifier.align(Alignment.Center).size(78.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = .88f),
                    border = BorderStroke(1.dp, BodyBlue.copy(alpha = .27f)),
                    shadowElevation = 12.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BodyBlue, strokeWidth = 2.5.dp, modifier = Modifier.size(29.dp))
                    }
                }

                V12BodyRendererState.ERROR -> Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(23.dp),
                    color = Color.White.copy(alpha = .94f),
                    border = BorderStroke(1.dp, BodyRed.copy(alpha = .35f)),
                ) {
                    Text(
                        if (language == "ar") "تعذر تحميل الجسم الرقمي" else "DIGITAL BODY LOAD FAILED",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        color = BodyRed,
                        fontWeight = FontWeight.Bold,
                    )
                }

                V12BodyRendererState.READY -> Unit
            }

            HeroMeasurementDock(
                language = language,
                profile = profile,
                selected = selected,
                onWeightChanged = onWeightChanged,
                onMeasurementChanged = onMeasurementChanged,
                onMeasurementCleared = onMeasurementCleared,
                onWeightMode = {
                    selectedName = null
                    runtime?.resetView()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 16.dp),
            )

            @Suppress("UNUSED_VARIABLE")
            val keepHeightCallback = onHeightChanged
        }
    }
}

@Composable
private fun HeroBodyHeader(language: String, completed: Int, total: Int, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.TopStart).clickable(onClick = onBack),
            shape = RoundedCornerShape(25.dp),
            color = BodyGlass,
            border = BorderStroke(1.dp, BodyBlue.copy(alpha = .24f)),
            shadowElevation = 9.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (language == "ar") "تم" else "Done", color = BodyInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text("✓", color = BodyInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(start = 86.dp, end = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI / FILAMENT", color = Color(0xFF80BCE0), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Text(
                if (language == "ar") "قياسات جسمك" else "YOUR BODY",
                color = BodyInk,
                fontSize = 31.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(152.dp).height(5.dp).background(Color(0xFFDCEFFA), RoundedCornerShape(99.dp))) {
                    Box(
                        Modifier
                            .fillMaxWidth((completed.toFloat() / total.toFloat()).coerceIn(.035f, 1f))
                            .height(5.dp)
                            .background(BodyBlue, RoundedCornerShape(99.dp)),
                    )
                }
                Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFE1F2FE)) {
                    Text(
                        "$completed/$total",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = BodyInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMeasurementDock(
    language: String,
    profile: BodyProfile,
    selected: BodyMeasurePoint?,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onWeightMode: () -> Unit,
    modifier: Modifier,
) {
    val initial = if (selected == null) profile.weightKilograms else profile.measurementsInches[selected]?.times(HERO_INCH_TO_CM)
    var text by remember(selected, initial) { mutableStateOf(initial?.let(::heroFormat1) ?: "") }
    val title = if (selected == null) {
        if (language == "ar") "الوزن" else "WEIGHT"
    } else {
        heroMeasureLabel(selected, language)
    }
    val unit = if (selected == null) "kg" else "cm"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(29.dp),
        color = BodyGlass,
        border = BorderStroke(1.2.dp, BodyBlue.copy(alpha = .24f)),
        shadowElevation = 16.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(title, color = BodyInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (selected == null) {
                            if (language == "ar") "يتفاعل الجسم مباشرة" else "BODY REACTS LIVE"
                        } else {
                            if (language == "ar") "أدخل القياس ثم ثبّته" else "ENTER AND LOCK MEASUREMENT"
                        },
                        color = BodyInk.copy(alpha = .52f),
                        fontSize = 9.5.sp,
                    )
                }
                if (selected != null) {
                    Surface(
                        modifier = Modifier.clickable(onClick = onWeightMode),
                        shape = RoundedCornerShape(16.dp),
                        color = BodyBlue.copy(alpha = .10f),
                        border = BorderStroke(1.dp, BodyBlue.copy(alpha = .22f)),
                    ) {
                        Text(
                            if (language == "ar") "الوزن" else "WEIGHT",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            color = BodyBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = Color(0xFFF6FBFF),
                    border = BorderStroke(1.dp, BodyBlue.copy(alpha = .22f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { value -> text = value.filter { it.isDigit() || it == '.' }.take(6) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = TextStyle(color = BodyInk, fontSize = 25.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(BodyBlue),
                            modifier = Modifier.width(90.dp),
                        )
                        Text(unit, color = BodyInk.copy(alpha = .70f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }
                }
                Surface(
                    modifier = Modifier.size(52.dp).clickable {
                        if (selected == null) {
                            text = heroFormat1(profile.weightKilograms)
                        } else {
                            onMeasurementCleared(selected)
                            text = ""
                        }
                    },
                    shape = RoundedCornerShape(17.dp),
                    color = Color(0xFFF6FBFF),
                    border = BorderStroke(1.dp, BodyBlue.copy(alpha = .22f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("×", color = BodyInk, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(
                    modifier = Modifier.size(52.dp).clickable {
                        val value = text.toFloatOrNull() ?: return@clickable
                        if (selected == null) onWeightChanged(value / HERO_POUND_TO_KG)
                        else onMeasurementChanged(selected, value / HERO_INCH_TO_CM)
                    },
                    shape = RoundedCornerShape(17.dp),
                    color = BodyBlue,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .8f)),
                    shadowElevation = 7.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBodyBackdrop() {
    val sweep by rememberInfiniteTransition(label = "hero-body-scan")
        .animateFloat(
            initialValue = .18f,
            targetValue = .84f,
            animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
            label = "hero-body-scan-value",
        )
    val pulse by rememberInfiniteTransition(label = "hero-body-pulse")
        .animateFloat(
            initialValue = .45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
            label = "hero-body-pulse-value",
        )

    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFF8ACDEB).copy(alpha = .15f)
        val step = 44f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .15f), Offset(x, size.height * .88f), 1f)
            x += step
        }
        var y = size.height * .15f
        while (y <= size.height * .88f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(BodyBlue.copy(alpha = .075f), size.minDimension * .62f, Offset(size.width * .5f, size.height * .52f))
        drawCircle(Color.White.copy(alpha = .58f), size.minDimension * .36f, Offset(size.width * .5f, size.height * .52f), style = Stroke(1.2f))

        val beamY = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, BodyCyan.copy(alpha = .06f), BodyCyan.copy(alpha = .16f), Color.White.copy(alpha = .18f), Color.Transparent),
                beamY - 85f,
                beamY + 85f,
            ),
            topLeft = Offset(0f, beamY - 85f),
            size = Size(size.width, 170f),
        )
        drawLine(BodyCyan.copy(alpha = .34f * pulse), Offset(size.width * .06f, beamY), Offset(size.width * .94f, beamY), 1.2f)
    }
}

private fun heroMeasureLabel(point: BodyMeasurePoint, language: String): String {
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

private fun heroFormat0(value: Float): String = value.roundToInt().toString()
private fun heroFormat1(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)
