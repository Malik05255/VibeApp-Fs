package com.almi.ai.ui.body

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val OverlaySurface = Color(0xE60B1A2C)
private val OverlayRaised = Color(0xF010243B)
private val OverlayText = Color(0xFFF6FAFF)
private val OverlayMuted = Color(0xFF91A8C5)
private val OverlayBlue = Color(0xFF86BCFF)
private val OverlayRed = Color(0xFFFF433D)
private val OverlayGreen = Color(0xFF59D8A6)
private const val CM_PER_INCH_OVERLAY = 2.54f
private const val KG_PER_POUND_OVERLAY = 0.45359237f

@Composable
internal fun BodyMeasurementOverlay(
    language: String,
    profile: BodyProfile,
    rendererState: BodyRendererState,
    onProfileChanged: (BodyProfile) -> Unit,
    onDone: () -> Unit,
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { OverlayBodyTarget.valueOf(it) }.getOrNull() }
    val completed = OverlayBodyTarget.entries.count { it.valueCm(profile) != null } + if (profile.hasExplicitWeight) 1 else 0
    val total = OverlayBodyTarget.entries.size + 1

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = OverlaySurface) {
                Column(Modifier.statusBarsPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("ALMI / FILAMENT", color = OverlayBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (language == "ar") "قياسات جسمك" else "Your measurements",
                                color = OverlayText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(999.dp), color = OverlayRaised) {
                                Text("$completed/$total", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = OverlayMuted, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = onDone) {
                                Text(if (language == "ar") "تم" else "Done", color = OverlayText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { completed.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = OverlayBlue,
                        trackColor = Color.White.copy(alpha = .07f),
                    )
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = OverlaySurface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                ) {
                    Text(
                        when (rendererState) {
                            BodyRendererState.LOADING -> if (language == "ar") "يتم تجهيز Filament…" else "Preparing Filament…"
                            BodyRendererState.READY -> if (language == "ar") "اسحب 360° • اضغط النقطة الحمراء" else "Drag 360° • tap a red point"
                            BodyRendererState.ERROR -> if (language == "ar") "تعذر تشغيل المجسم" else "The 3D model could not start"
                        },
                        Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                        color = if (rendererState == BodyRendererState.ERROR) OverlayRed else OverlayMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                if (rendererState == BodyRendererState.READY) {
                    OverlayBodyTarget.entries.forEach { target ->
                        val active = target == selected
                        Box(
                            modifier = Modifier
                                .offset(x = maxWidth * target.screenX - 21.dp, y = maxHeight * target.screenY - 21.dp)
                                .size(42.dp)
                                .clickable { selectedName = target.name },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier.size(if (active) 19.dp else 15.dp),
                                shape = CircleShape,
                                color = OverlayRed,
                                border = BorderStroke(2.dp, Color.White.copy(alpha = .80f)),
                                shadowElevation = if (active) 10.dp else 6.dp,
                            ) {}
                        }
                    }
                }

                selected?.let { target ->
                    MeasurementGuide(target, Modifier.fillMaxSize())
                    MeasurementEditor(
                        language = language,
                        target = target,
                        existingCm = target.valueCm(profile),
                        onConfirm = { centimeters ->
                            val next = if (target == OverlayBodyTarget.HEIGHT) {
                                profile.copy(heightInches = centimeters / CM_PER_INCH_OVERLAY, hasExplicitHeight = true)
                            } else {
                                target.point?.let { point ->
                                    profile.copy(measurementsInches = profile.measurementsInches + (point to (centimeters / CM_PER_INCH_OVERLAY)))
                                } ?: profile
                            }
                            onProfileChanged(next)
                            selectedName = null
                        },
                        onClose = { selectedName = null },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp),
                    )
                }
            }

            WeightDock(language, profile) { kilograms ->
                onProfileChanged(profile.copy(weightPounds = kilograms / KG_PER_POUND_OVERLAY, hasExplicitWeight = true))
            }
        }
    }
}

@Composable
private fun MeasurementEditor(
    language: String,
    target: OverlayBodyTarget,
    existingCm: Float?,
    onConfirm: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var value by remember(target, existingCm) { mutableStateOf(existingCm?.let(::formatOverlayNumber).orEmpty()) }
    val parsed = value.toFloatOrNull()

    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = OverlaySurface,
        border = BorderStroke(1.dp, OverlayBlue.copy(alpha = .28f)),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(target.title(language), color = OverlayText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(target.instruction(language), color = OverlayMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = OverlayMuted) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    suffix = { Text("cm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OverlayText,
                        unfocusedTextColor = OverlayText,
                        focusedBorderColor = OverlayBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = .16f),
                    ),
                )
                Button(
                    onClick = { parsed?.takeIf { it > 0f }?.let(onConfirm) },
                    enabled = parsed?.let { it > 0f } == true,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OverlayGreen, contentColor = Color(0xFF062017)),
                ) { Icon(Icons.Rounded.Check, null) }
            }
        }
    }
}

@Composable
private fun WeightDock(language: String, profile: BodyProfile, onKilograms: (Float) -> Unit) {
    var value by remember(profile.weightPounds, profile.hasExplicitWeight) {
        mutableStateOf(if (profile.hasExplicitWeight) formatOverlayNumber(profile.weightKilograms) else "")
    }
    val parsed = value.toFloatOrNull()

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(26.dp),
        color = OverlayRaised,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (language == "ar") "الوزن" else "Weight", color = OverlayText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (language == "ar") "يتفاعل حجم الجسم مباشرة" else "Body volume reacts live", color = OverlayMuted, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                modifier = Modifier.width(132.dp),
                singleLine = true,
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = OverlayText,
                    unfocusedTextColor = OverlayText,
                    focusedBorderColor = OverlayBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = .15f),
                ),
            )
            Button(
                onClick = { parsed?.takeIf { it > 0f }?.let(onKilograms) },
                enabled = parsed?.let { it > 0f } == true,
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OverlayGreen, contentColor = Color(0xFF062017)),
            ) { Icon(Icons.Rounded.Check, null) }
        }
    }
}

@Composable
private fun MeasurementGuide(target: OverlayBodyTarget, modifier: Modifier) {
    Canvas(modifier) {
        val (a, b) = target.guide(size.width, size.height)
        drawLine(OverlayBlue.copy(alpha = .22f), a, b, 9f, StrokeCap.Round)
        drawLine(OverlayBlue, a, b, 3f, StrokeCap.Round)
        drawArrowHead(b, a)
        drawArrowHead(a, b)
    }
}

private fun DrawScope.drawArrowHead(tip: Offset, from: Offset) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    val ux = dx / length
    val uy = dy / length
    val px = -uy
    val py = ux
    val back = Offset(tip.x - ux * 18f, tip.y - uy * 18f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(back.x + px * 8f, back.y + py * 8f)
        lineTo(back.x - px * 8f, back.y - py * 8f)
        close()
    }
    drawPath(path, OverlayBlue)
}

private enum class OverlayBodyTarget(
    val point: BodyMeasurePoint?,
    val screenX: Float,
    val screenY: Float,
) {
    HEIGHT(null, .53f, .10f),
    NECK(BodyMeasurePoint.NECK, .50f, .23f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, .34f, .29f),
    CHEST(BodyMeasurePoint.CHEST, .50f, .37f),
    WAIST(BodyMeasurePoint.WAIST, .50f, .48f),
    HIPS(BodyMeasurePoint.HIPS, .39f, .56f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, .22f, .45f),
    WRIST(BodyMeasurePoint.WRIST, .18f, .55f),
    HAND(BodyMeasurePoint.HAND, .16f, .63f),
    THIGH(BodyMeasurePoint.THIGH, .42f, .66f),
    INSEAM(BodyMeasurePoint.INSEAM, .50f, .63f),
    CALF(BodyMeasurePoint.CALF, .41f, .78f),
    FOOT(BodyMeasurePoint.FOOT, .42f, .90f),
    ;

    fun valueCm(profile: BodyProfile): Float? = if (this == HEIGHT) {
        profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
    } else point?.let { profile.measurementsInches[it]?.times(CM_PER_INCH_OVERLAY) }

    fun title(language: String): String = when (this) {
        HEIGHT -> arEnOverlay(language, "الطول", "Height")
        NECK -> arEnOverlay(language, "محيط الرقبة", "Neck")
        SHOULDERS -> arEnOverlay(language, "عرض الكتفين", "Shoulders")
        CHEST -> arEnOverlay(language, "محيط الصدر", "Chest")
        WAIST -> arEnOverlay(language, "محيط الخصر", "Waist")
        HIPS -> arEnOverlay(language, "محيط الورك", "Hips")
        ARM_LENGTH -> arEnOverlay(language, "طول الذراع", "Arm length")
        WRIST -> arEnOverlay(language, "محيط المعصم", "Wrist")
        HAND -> arEnOverlay(language, "طول اليد", "Hand length")
        THIGH -> arEnOverlay(language, "محيط الفخذ", "Thigh")
        INSEAM -> arEnOverlay(language, "طول الساق الداخلي", "Inseam")
        CALF -> arEnOverlay(language, "محيط الساق", "Calf")
        FOOT -> arEnOverlay(language, "طول القدم", "Foot length")
    }

    fun instruction(language: String): String = when (this) {
        HEIGHT -> arEnOverlay(language, "من أعلى الرأس إلى أسفل القدم.", "Top of head to floor.")
        NECK -> arEnOverlay(language, "حول قاعدة الرقبة بدون شد.", "Around the base of the neck.")
        SHOULDERS -> arEnOverlay(language, "من نهاية كتف إلى نهاية الكتف الآخر.", "Shoulder tip to shoulder tip.")
        CHEST -> arEnOverlay(language, "حول أعرض نقطة من الصدر.", "Around the fullest chest point.")
        WAIST -> arEnOverlay(language, "حول أضيق نقطة من الخصر الطبيعي.", "Around the natural waist.")
        HIPS -> arEnOverlay(language, "حول أعرض نقطة من الورك.", "Around the fullest hips.")
        ARM_LENGTH -> arEnOverlay(language, "من نقطة الكتف إلى عظمة المعصم.", "Shoulder point to wrist bone.")
        WRIST -> arEnOverlay(language, "حول عظمة المعصم.", "Around the wrist bone.")
        HAND -> arEnOverlay(language, "من بداية راحة اليد إلى نهاية أطول إصبع.", "Wrist crease to longest fingertip.")
        THIGH -> arEnOverlay(language, "حول أعرض جزء من أعلى الفخذ.", "Around the fullest upper thigh.")
        INSEAM -> arEnOverlay(language, "من أعلى داخل الساق إلى الأرض.", "Crotch to floor along the inner leg.")
        CALF -> arEnOverlay(language, "حول أعرض نقطة من عضلة الساق.", "Around the fullest calf point.")
        FOOT -> arEnOverlay(language, "من مؤخرة الكعب إلى أطول إصبع.", "Heel to longest toe.")
    }

    fun guide(w: Float, h: Float): Pair<Offset, Offset> = when (this) {
        HEIGHT -> Offset(w * .50f, h * .14f) to Offset(w * .50f, h * .86f)
        NECK -> Offset(w * .43f, h * .23f) to Offset(w * .57f, h * .23f)
        SHOULDERS -> Offset(w * .30f, h * .29f) to Offset(w * .70f, h * .29f)
        CHEST -> Offset(w * .30f, h * .37f) to Offset(w * .70f, h * .37f)
        WAIST -> Offset(w * .36f, h * .48f) to Offset(w * .64f, h * .48f)
        HIPS -> Offset(w * .33f, h * .56f) to Offset(w * .67f, h * .56f)
        ARM_LENGTH -> Offset(w * .31f, h * .29f) to Offset(w * .18f, h * .55f)
        WRIST -> Offset(w * .15f, h * .53f) to Offset(w * .23f, h * .53f)
        HAND -> Offset(w * .18f, h * .55f) to Offset(w * .16f, h * .64f)
        THIGH -> Offset(w * .36f, h * .61f) to Offset(w * .49f, h * .61f)
        INSEAM -> Offset(w * .50f, h * .58f) to Offset(w * .45f, h * .87f)
        CALF -> Offset(w * .38f, h * .77f) to Offset(w * .48f, h * .77f)
        FOOT -> Offset(w * .35f, h * .89f) to Offset(w * .49f, h * .89f)
    }
}

private fun arEnOverlay(language: String, ar: String, en: String) = if (language == "ar") ar else en
private fun formatOverlayNumber(value: Float): String = if (abs(value - value.roundToInt()) < .05f) {
    value.roundToInt().toString()
} else {
    "%.1f".format(Locale.US, value)
}
