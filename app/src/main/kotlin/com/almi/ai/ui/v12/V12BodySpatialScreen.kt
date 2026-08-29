package com.almi.ai.ui.v12

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.guidedMeasurementOrder
import java.util.Locale

private const val SPATIAL_INCH_TO_CM = 2.54f
private const val SPATIAL_POUND_TO_KG = 0.45359237f

private val BodyIceInk = Color(0xFF173A60)
private val BodyIceBlue = Color(0xFF53BDF7)
private val BodyIceMint = Color(0xFF55D7C2)
private val BodyIcePink = Color(0xFFFF8CB4)
private val BodyIceViolet = Color(0xFFA98AF8)
private val BodyIceGlass = Color(0xEEFFFFFF)

private data class SpatialBodyMarker(val anchor: String, val point: BodyMeasurePoint)

private val spatialBodyMarkers = listOf(
    SpatialBodyMarker("neck", BodyMeasurePoint.NECK),
    SpatialBodyMarker("shoulderCenter", BodyMeasurePoint.SHOULDERS),
    SpatialBodyMarker("rightShoulder", BodyMeasurePoint.SHOULDER_LENGTH),
    SpatialBodyMarker("chest", BodyMeasurePoint.CHEST),
    SpatialBodyMarker("underbust", BodyMeasurePoint.UNDERBUST),
    SpatialBodyMarker("leftBust", BodyMeasurePoint.BUST_HEIGHT),
    SpatialBodyMarker("rightBust", BodyMeasurePoint.BUST_POINT_DISTANCE),
    SpatialBodyMarker("waist", BodyMeasurePoint.WAIST),
    SpatialBodyMarker("abdomen", BodyMeasurePoint.ABDOMEN),
    SpatialBodyMarker("hips", BodyMeasurePoint.HIPS),
    SpatialBodyMarker("leftShoulder", BodyMeasurePoint.DRESS_LENGTH),
    SpatialBodyMarker("rightElbow", BodyMeasurePoint.ARM_LENGTH),
    SpatialBodyMarker("rightUpperArm", BodyMeasurePoint.UPPER_ARM),
    SpatialBodyMarker("rightHand", BodyMeasurePoint.WRIST),
)

@Composable
internal fun V12BodySpatialScreen(
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
    var rendererState by remember(presentation) { mutableStateOf(V12BodyRendererState.LOADING) }
    var projection by remember(presentation) { mutableStateOf<V12BodyProjection?>(null) }
    var runtime by remember(presentation) { mutableStateOf<V12BodyRuntime?>(null) }
    var selected by rememberSaveable {
        mutableStateOf(profile.nextRecommendedMeasurement ?: BodyMeasurePoint.CHEST)
    }
    val currentInches = profile.measurementsInches[selected]
    var editorText by remember(selected, currentInches) {
        mutableStateOf(currentInches?.let { spatialFormatOne(it * SPATIAL_INCH_TO_CM) } ?: "")
    }

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
                        Color(0xFFEAF8FF),
                        Color(0xFFF7FBFF),
                        Color(0xFFFFF8FC),
                        Color(0xFFF2FFFB),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        BodyIceAtmosphere()

        key(presentation) {
            AndroidView(
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
                modifier = Modifier.fillMaxSize(),
            )
        }

        DisposableEffect(presentation) {
            onDispose {
                runtime?.stop()
                runtime = null
            }
        }

        // Soft wash keeps text readable without tinting the authored skin material.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = .20f),
                            Color.Transparent,
                            Color.Transparent,
                            Color(0xFFF3FBFF).copy(alpha = .32f),
                        ),
                    ),
                ),
        )

        SpatialMeasurementGuide(selected, projection)
        SpatialBodyMarkers(
            selected = selected,
            completed = profile.measurementsInches.keys,
            projection = projection,
            onSelect = { point, screenY ->
                selected = point
                runtime?.focusOn(screenY)
            },
        )

        BodyIceHeader(
            language = language,
            completed = profile.completedMeasurements,
            total = guidedMeasurementOrder.size,
            onBack = onBack,
        )

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 79.dp, end = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BodyGenderOrb(
                label = if (language == "ar") "ذكر" else "M",
                active = presentation == AvatarPresentation.MASCULINE,
                accent = BodyIceBlue,
            ) { onPresentation(AvatarPresentation.MASCULINE) }
            BodyGenderOrb(
                label = if (language == "ar") "أنثى" else "F",
                active = presentation == AvatarPresentation.FEMININE,
                accent = BodyIcePink,
            ) { onPresentation(AvatarPresentation.FEMININE) }
        }

        BodyRendererStateOrb(
            state = rendererState,
            language = language,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 82.dp, start = 16.dp),
        )

        // Previous and next float in the anatomical field instead of a dark side rail.
        BodyNavOrb(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
            symbol = "‹",
            accent = BodyIceViolet,
            onClick = {
                val next = spatialPreviousMeasurement(selected)
                selected = next
                spatialFocusSelected(runtime, projection, next)
            },
        )
        BodyNavOrb(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            symbol = "›",
            accent = BodyIceBlue,
            onClick = {
                val next = spatialNextMeasurement(selected)
                selected = next
                spatialFocusSelected(runtime, projection, next)
            },
        )

        Surface(
            modifier = Modifier.align(Alignment.CenterEnd).offset(y = 76.dp).padding(end = 18.dp).size(50.dp).clickable {
                runtime?.resetView()
            },
            shape = CircleShape,
            color = BodyIceGlass,
            border = BorderStroke(1.dp, BodyIceMint.copy(alpha = .42f)),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                V12Glyph(V12GlyphType.RESET, BodyIceMint, Modifier.size(21.dp))
            }
        }

        BodyMeasurementLens(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 22.dp),
            language = language,
            selected = selected,
            currentInches = currentInches,
            editorText = editorText,
            onTextChange = { raw ->
                val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
                editorText = filtered
                filtered.replace(',', '.').toFloatOrNull()?.let { cm ->
                    if (cm in 5f..250f) onMeasurementChanged(selected, cm / SPATIAL_INCH_TO_CM)
                }
            },
            onClear = {
                editorText = ""
                onMeasurementCleared(selected)
            },
        )

        BodyMetricOrb(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 126.dp),
            label = if (language == "ar") "الطول" else "HEIGHT",
            value = "${spatialFormatZero(profile.heightCentimeters)}",
            unit = "CM",
            accent = BodyIceBlue,
            onMinus = {
                onHeightChanged(((profile.heightCentimeters - 1f).coerceAtLeast(120f)) / SPATIAL_INCH_TO_CM)
            },
            onPlus = {
                onHeightChanged(((profile.heightCentimeters + 1f).coerceAtMost(220f)) / SPATIAL_INCH_TO_CM)
            },
        )
        BodyMetricOrb(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 126.dp),
            label = if (language == "ar") "الوزن" else "WEIGHT",
            value = "${spatialFormatZero(profile.weightKilograms)}",
            unit = "KG",
            accent = BodyIcePink,
            onMinus = {
                onWeightChanged(((profile.weightKilograms - 1f).coerceAtLeast(35f)) / SPATIAL_POUND_TO_KG)
            },
            onPlus = {
                onWeightChanged(((profile.weightKilograms + 1f).coerceAtMost(220f)) / SPATIAL_POUND_TO_KG)
            },
        )
    }
}

@Composable
private fun BodyIceAtmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(BodyIceBlue.copy(alpha = .08f), size.minDimension * .54f, Offset(size.width * .02f, size.height * .19f))
        drawCircle(BodyIcePink.copy(alpha = .065f), size.minDimension * .46f, Offset(size.width * .98f, size.height * .66f))
        drawCircle(BodyIceMint.copy(alpha = .055f), size.minDimension * .38f, Offset(size.width * .25f, size.height * .92f))
        drawCircle(BodyIceViolet.copy(alpha = .055f), size.minDimension * .32f, Offset(size.width * .67f, size.height * .42f), style = Stroke(1.2f))
    }
}

@Composable
private fun BodyIceHeader(
    language: String,
    completed: Int,
    total: Int,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("ALMI / BODY FIELD", color = BodyIceBlue, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
            Text(if (language == "ar") "خريطة جسمك الحيّة" else "LIVING BODY MAP", color = BodyIceInk, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(
                if (language == "ar") "$completed من $total نقطة مكتملة" else "$completed / $total CALIBRATED",
                color = BodyIceInk.copy(alpha = .42f),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .65.sp,
            )
        }
        Surface(
            modifier = Modifier.size(48.dp).clickable(onClick = onBack),
            shape = CircleShape,
            color = BodyIceGlass,
            border = BorderStroke(1.dp, BodyIceBlue.copy(alpha = .30f)),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                V12Glyph(V12GlyphType.BACK, BodyIceInk, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun BodyGenderOrb(
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(if (active) 48.dp else 42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .92f) else BodyIceGlass,
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = .46f)),
        shadowElevation = if (active) 10.dp else 5.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (active) Color.White else BodyIceInk, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BodyRendererStateOrb(
    state: V12BodyRendererState,
    language: String,
    modifier: Modifier,
) {
    AnimatedVisibility(
        visible = state != V12BodyRendererState.READY,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = BodyIceGlass,
            border = BorderStroke(1.dp, if (state == V12BodyRendererState.ERROR) BodyIcePink.copy(alpha = .55f) else BodyIceBlue.copy(alpha = .35f)),
            shadowElevation = 7.dp,
        ) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state == V12BodyRendererState.LOADING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BodyIceBlue, strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    when (state) {
                        V12BodyRendererState.LOADING -> if (language == "ar") "نجهز الجسم" else "BODY SYNC"
                        V12BodyRendererState.ERROR -> if (language == "ar") "تعذر تحميل الجسم" else "BODY OFFLINE"
                        V12BodyRendererState.READY -> ""
                    },
                    color = if (state == V12BodyRendererState.ERROR) BodyIcePink else BodyIceInk,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = .55.sp,
                )
            }
        }
    }
}

@Composable
private fun SpatialMeasurementGuide(
    selected: BodyMeasurePoint,
    projection: V12BodyProjection?,
) {
    Canvas(Modifier.fillMaxSize()) {
        val points = projection?.points ?: return@Canvas

        fun screen(name: String): Offset? {
            val point = points[name] ?: return null
            if (!point.visible) return null
            return Offset(point.x * size.width, point.y * size.height)
        }

        fun line(a: String, b: String) {
            val start = screen(a) ?: return
            val end = screen(b) ?: return
            drawLine(BodyIceBlue.copy(alpha = .94f), start, end, 2.6f)
            drawCircle(Color.White.copy(alpha = .96f), 5.1f, start)
            drawCircle(BodyIceMint.copy(alpha = .82f), 2.8f, start)
            drawCircle(Color.White.copy(alpha = .96f), 5.1f, end)
            drawCircle(BodyIceMint.copy(alpha = .82f), 2.8f, end)
        }

        fun oval(anchor: String, widthFraction: Float, heightFraction: Float) {
            val center = screen(anchor) ?: return
            val width = size.width * widthFraction
            val height = size.height * heightFraction
            drawOval(
                color = Color.White.copy(alpha = .90f),
                topLeft = Offset(center.x - width / 2f - 2.8f, center.y - height / 2f - 2.8f),
                size = Size(width + 5.6f, height + 5.6f),
                style = Stroke(5.2f),
            )
            drawOval(
                color = BodyIceBlue.copy(alpha = .96f),
                topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
                size = Size(width, height),
                style = Stroke(2.5f),
            )
        }

        when (selected) {
            BodyMeasurePoint.NECK -> oval("neck", .17f, .020f)
            BodyMeasurePoint.SHOULDERS -> line("leftShoulder", "rightShoulder")
            BodyMeasurePoint.SHOULDER_LENGTH -> line("shoulderCenter", "rightShoulder")
            BodyMeasurePoint.CHEST -> oval("chest", .36f, .025f)
            BodyMeasurePoint.UNDERBUST -> oval("underbust", .33f, .023f)
            BodyMeasurePoint.BUST_HEIGHT -> line("leftShoulder", "leftBust")
            BodyMeasurePoint.BUST_POINT_DISTANCE -> line("leftBust", "rightBust")
            BodyMeasurePoint.WAIST -> oval("waist", .30f, .023f)
            BodyMeasurePoint.ABDOMEN -> oval("abdomen", .33f, .024f)
            BodyMeasurePoint.HIPS -> oval("hips", .38f, .026f)
            BodyMeasurePoint.DRESS_LENGTH -> line("leftShoulder", "leftThigh")
            BodyMeasurePoint.ARM_LENGTH -> line("rightShoulder", "rightHand")
            BodyMeasurePoint.UPPER_ARM -> oval("rightUpperArm", .13f, .018f)
            BodyMeasurePoint.WRIST -> oval("rightHand", .09f, .015f)
            else -> Unit
        }
    }
}

@Composable
private fun SpatialBodyMarkers(
    selected: BodyMeasurePoint,
    completed: Set<BodyMeasurePoint>,
    projection: V12BodyProjection?,
    onSelect: (BodyMeasurePoint, Float) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val points = projection?.points.orEmpty()
        spatialBodyMarkers.forEach { spec ->
            val projected = points[spec.anchor]
            if (projected != null && projected.visible) {
                val active = spec.point == selected
                val done = spec.point in completed
                val markerSize = when {
                    active -> 22.dp
                    done -> 16.dp
                    else -> 13.dp
                }
                val color = when {
                    active -> BodyIceBlue
                    done -> BodyIceMint
                    else -> BodyIceViolet.copy(alpha = .72f)
                }
                Surface(
                    modifier = Modifier
                        .offset(
                            x = maxWidth * projected.x.coerceIn(0f, 1f) - markerSize / 2,
                            y = maxHeight * projected.y.coerceIn(0f, 1f) - markerSize / 2,
                        )
                        .size(markerSize)
                        .clickable { onSelect(spec.point, projected.y) },
                    shape = CircleShape,
                    color = color,
                    border = BorderStroke(if (active) 3.dp else 2.dp, Color.White.copy(alpha = .96f)),
                    shadowElevation = if (active) 9.dp else 3.dp,
                ) {}
            }
        }
    }
}

@Composable
private fun BodyNavOrb(
    modifier: Modifier,
    symbol: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(58.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = BodyIceGlass,
        border = BorderStroke(1.5.dp, accent.copy(alpha = .42f)),
        shadowElevation = 10.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, color = accent, fontSize = 33.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun BodyMeasurementLens(
    modifier: Modifier,
    language: String,
    selected: BodyMeasurePoint,
    currentInches: Float?,
    editorText: String,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val completed = currentInches != null
    Surface(
        modifier = modifier.fillMaxWidth().height(92.dp),
        shape = RoundedCornerShape(46.dp),
        color = BodyIceGlass,
        border = BorderStroke(1.5.dp, (if (completed) BodyIceMint else BodyIceBlue).copy(alpha = .52f)),
        shadowElevation = 18.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = CircleShape,
                color = (if (completed) BodyIceMint else BodyIceBlue).copy(alpha = .15f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(V12GlyphType.BODY, if (completed) BodyIceMint else BodyIceBlue, Modifier.size(26.dp))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    spatialMeasurementLabel(selected, language),
                    color = BodyIceInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    BasicTextField(
                        value = editorText,
                        onValueChange = onTextChange,
                        modifier = Modifier.width(72.dp),
                        textStyle = TextStyle(
                            color = BodyIceInk,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(BodyIceBlue),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.Center) {
                                if (editorText.isBlank()) {
                                    Text("—", color = BodyIceInk.copy(alpha = .25f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                                inner()
                            }
                        },
                    )
                    Text("CM", color = BodyIceInk.copy(alpha = .42f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
                }
            }

            Surface(
                modifier = Modifier.size(46.dp).clickable(onClick = onClear),
                shape = CircleShape,
                color = BodyIcePink.copy(alpha = if (completed) .15f else .07f),
                border = BorderStroke(1.dp, BodyIcePink.copy(alpha = .28f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = BodyIcePink, fontSize = 21.sp, fontWeight = FontWeight.Light)
                }
            }
        }
    }
}

@Composable
private fun BodyMetricOrb(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    accent: Color,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Surface(
        modifier = modifier.size(108.dp),
        shape = CircleShape,
        color = BodyIceGlass,
        border = BorderStroke(1.2.dp, accent.copy(alpha = .40f)),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = BodyIceInk.copy(alpha = .46f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .6.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = BodyIceInk, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(unit, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp), color = accent, fontSize = 6.5.sp, fontWeight = FontWeight.Black)
            }
            Row(modifier = Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricNudge("−", accent, onMinus)
                MetricNudge("+", accent, onPlus)
            }
        }
    }
}

@Composable
private fun MetricNudge(symbol: String, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(27.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = accent.copy(alpha = .12f),
        border = BorderStroke(1.dp, accent.copy(alpha = .26f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun spatialPreviousMeasurement(point: BodyMeasurePoint): BodyMeasurePoint {
    val index = guidedMeasurementOrder.indexOf(point).takeIf { it >= 0 } ?: 0
    return guidedMeasurementOrder[(index - 1 + guidedMeasurementOrder.size) % guidedMeasurementOrder.size]
}

private fun spatialNextMeasurement(point: BodyMeasurePoint): BodyMeasurePoint {
    val index = guidedMeasurementOrder.indexOf(point).takeIf { it >= 0 } ?: 0
    return guidedMeasurementOrder[(index + 1) % guidedMeasurementOrder.size]
}

private fun spatialFocusSelected(
    runtime: V12BodyRuntime?,
    projection: V12BodyProjection?,
    point: BodyMeasurePoint,
) {
    val anchor = spatialAnchorFor(point)
    val projected = projection?.points?.get(anchor)
    if (projected != null && projected.visible) runtime?.focusOn(projected.y)
}

private fun spatialAnchorFor(point: BodyMeasurePoint): String = when (point) {
    BodyMeasurePoint.NECK -> "neck"
    BodyMeasurePoint.SHOULDERS -> "shoulderCenter"
    BodyMeasurePoint.SHOULDER_LENGTH -> "rightShoulder"
    BodyMeasurePoint.CHEST -> "chest"
    BodyMeasurePoint.UNDERBUST -> "underbust"
    BodyMeasurePoint.BUST_HEIGHT -> "leftBust"
    BodyMeasurePoint.BUST_POINT_DISTANCE -> "rightBust"
    BodyMeasurePoint.WAIST -> "waist"
    BodyMeasurePoint.ABDOMEN -> "abdomen"
    BodyMeasurePoint.HIPS -> "hips"
    BodyMeasurePoint.DRESS_LENGTH -> "leftShoulder"
    BodyMeasurePoint.ARM_LENGTH -> "rightHand"
    BodyMeasurePoint.UPPER_ARM -> "rightUpperArm"
    BodyMeasurePoint.WRIST -> "rightHand"
    else -> "pelvis"
}

private fun spatialMeasurementLabel(point: BodyMeasurePoint, language: String): String {
    val ar = language == "ar"
    return when (point) {
        BodyMeasurePoint.NECK -> if (ar) "محيط الرقبة" else "NECK"
        BodyMeasurePoint.SHOULDERS -> if (ar) "عرض الكتفين" else "SHOULDERS"
        BodyMeasurePoint.SHOULDER_LENGTH -> if (ar) "طول الكتف" else "SHOULDER LENGTH"
        BodyMeasurePoint.CHEST -> if (ar) "محيط الصدر" else "CHEST"
        BodyMeasurePoint.UNDERBUST -> if (ar) "تحت الصدر" else "UNDERBUST"
        BodyMeasurePoint.BUST_HEIGHT -> if (ar) "ارتفاع الصدر" else "BUST HEIGHT"
        BodyMeasurePoint.BUST_POINT_DISTANCE -> if (ar) "المسافة بين نقطتي الصدر" else "BUST DISTANCE"
        BodyMeasurePoint.WAIST -> if (ar) "محيط الخصر" else "WAIST"
        BodyMeasurePoint.ABDOMEN -> if (ar) "محيط البطن" else "ABDOMEN"
        BodyMeasurePoint.HIPS -> if (ar) "محيط الأرداف" else "HIPS"
        BodyMeasurePoint.DRESS_LENGTH -> if (ar) "طول الفستان" else "DRESS LENGTH"
        BodyMeasurePoint.ARM_LENGTH -> if (ar) "طول الذراع" else "ARM LENGTH"
        BodyMeasurePoint.UPPER_ARM -> if (ar) "محيط أعلى الذراع" else "UPPER ARM"
        BodyMeasurePoint.WRIST -> if (ar) "محيط المعصم" else "WRIST"
        BodyMeasurePoint.HAND -> if (ar) "اليد" else "HAND"
        BodyMeasurePoint.THIGH -> if (ar) "الفخذ" else "THIGH"
        BodyMeasurePoint.INSEAM -> if (ar) "طول الساق الداخلي" else "INSEAM"
        BodyMeasurePoint.CALF -> if (ar) "الساق" else "CALF"
        BodyMeasurePoint.FOOT -> if (ar) "القدم" else "FOOT"
    }
}

private fun spatialFormatOne(value: Float): String = String.format(Locale.US, "%.1f", value)
private fun spatialFormatZero(value: Float): String = String.format(Locale.US, "%.0f", value)
