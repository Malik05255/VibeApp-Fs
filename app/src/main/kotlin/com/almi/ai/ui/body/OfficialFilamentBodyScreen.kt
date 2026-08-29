package com.almi.ai.ui.body

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.google.android.filament.Engine
import com.google.android.filament.Skybox
import com.google.android.filament.View
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SafeBg = Color(0xFF04101E)
private val SafeSurface = Color(0xFF0B1A2C)
private val SafeRaised = Color(0xFF10243B)
private val SafeText = Color(0xFFF6FAFF)
private val SafeMuted = Color(0xFF91A8C5)
private val SafeBlue = Color(0xFF86BCFF)
private val SafeRed = Color(0xFFFF433D)
private val SafeGreen = Color(0xFF59D8A6)
private const val CM_PER_INCH_SAFE = 2.54f
private const val KG_PER_POUND_SAFE = 0.45359237f

/**
 * Crash-resistant measurement surface built on Google's official ModelViewer.
 *
 * Filament remains the renderer. We deliberately do not own SwapChain, ResourceLoader,
 * AssetLoader, frame pacing, or surface detach cleanup ourselves; ModelViewer owns all of that.
 * One GLB is loaded per session and the native renderer never crosses Compose navigation.
 */
@Composable
fun OfficialFilamentBodyScreen(
    language: String,
    profile: BodyProfile,
    onProfileChanged: (BodyProfile) -> Unit,
    onDone: () -> Unit,
) {
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = selectedName?.let { runCatching { SafeBodyTarget.valueOf(it) }.getOrNull() }
    var rendererState by remember { mutableStateOf(RendererState.LOADING) }

    val solved = remember(profile) { BodyShapeSolver.solve(profile) }
    val visualScale by animateFloatAsState(
        targetValue = ((solved.widthScale + solved.depthScale) * 0.5f).coerceIn(.80f, 1.28f),
        animationSpec = tween(380),
        label = "body-mass-visual",
    )

    val completed = SafeBodyTarget.entries.count { it.valueCm(profile) != null } + if (profile.hasExplicitWeight) 1 else 0
    val total = SafeBodyTarget.entries.size + 1

    Column(
        Modifier
            .fillMaxSize()
            .background(SafeBg)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / FILAMENT", color = SafeBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (language == "ar") "قياسات جسمك" else "Your measurements",
                    color = SafeText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(999.dp), color = SafeRaised) {
                    Text("$completed/$total", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = SafeMuted, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onDone) {
                    Text(if (language == "ar") "تم" else "Done", color = SafeText, fontWeight = FontWeight.Bold)
                }
            }
        }

        LinearProgressIndicator(
            progress = { completed.toFloat() / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = SafeBlue,
            trackColor = Color.White.copy(alpha = .07f),
        )

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                factory = { context ->
                    OfficialModelViewerSurface(
                        context = context,
                        onStateChanged = { rendererState = it },
                    )
                },
                modifier = Modifier.fillMaxSize(),
                update = { it.setBodyMassHint(visualScale) },
            )

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = SafeSurface.copy(alpha = .90f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
            ) {
                Text(
                    when (rendererState) {
                        RendererState.LOADING -> if (language == "ar") "يتم تجهيز Filament…" else "Preparing Filament…"
                        RendererState.READY -> if (language == "ar") "اسحب 360°  •  اضغط النقطة الحمراء" else "Drag 360°  •  tap a red point"
                        RendererState.ERROR -> if (language == "ar") "تعذر تحميل المجسم — أعد فتح الجلسة" else "Model could not load — reopen the session"
                    },
                    Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
                    color = if (rendererState == RendererState.ERROR) SafeRed else SafeMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            if (rendererState == RendererState.READY) {
                SafeBodyTarget.entries.forEach { target ->
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
                            color = SafeRed.copy(alpha = if (active) 1f else .92f),
                            border = BorderStroke(2.dp, Color.White.copy(alpha = .78f)),
                            shadowElevation = if (active) 9.dp else 5.dp,
                        ) {}
                    }
                }
            }

            selected?.let { target ->
                SafeMeasurementArrow(target, Modifier.fillMaxSize())
                SafeMeasureCard(
                    language = language,
                    target = target,
                    existingCm = target.valueCm(profile),
                    onConfirm = { centimeters ->
                        val next = if (target == SafeBodyTarget.HEIGHT) {
                            profile.copy(heightInches = centimeters / CM_PER_INCH_SAFE, hasExplicitHeight = true)
                        } else {
                            target.point?.let { point ->
                                profile.copy(measurementsInches = profile.measurementsInches + (point to (centimeters / CM_PER_INCH_SAFE)))
                            } ?: profile
                        }
                        onProfileChanged(next)
                        selectedName = null
                    },
                    onClose = { selectedName = null },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 58.dp),
                )
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
                shape = RoundedCornerShape(999.dp),
                color = SafeSurface.copy(alpha = .84f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
            ) {
                Text(
                    "FILAMENT • MODELVIEWER • OPENGL",
                    Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    color = SafeMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        SafeWeightDock(language, profile) { kilograms ->
            onProfileChanged(profile.copy(weightPounds = kilograms / KG_PER_POUND_SAFE, hasExplicitWeight = true))
        }
    }
}

private enum class RendererState { LOADING, READY, ERROR }

private class OfficialModelViewerSurface(
    context: Context,
    private val onStateChanged: (RendererState) -> Unit,
) : SurfaceView(context) {
    companion object {
        init { Utils.init() }
        private const val BODY_MODEL = "almi3d/vitruvian_body.glb"
    }

    private var viewer: ModelViewer? = null
    private var framePosted = false
    private var massHint = 1f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val current = viewer ?: return
            current.render(frameTimeNanos)
            if (framePosted && isAttachedToWindow) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    init {
        setZOrderOnTop(false)
        isFocusable = true
        post { initializeViewer() }
    }

    private fun initializeViewer() {
        if (!isAttachedToWindow || viewer != null) return
        onStateChanged(RendererState.LOADING)
        try {
            val engine = Engine.create(Engine.Backend.OPENGL)
            val modelViewer = ModelViewer(this, engine = engine)
            viewer = modelViewer

            modelViewer.scene.skybox = Skybox.Builder()
                .color(0.008f, 0.025f, 0.055f, 1f)
                .build(modelViewer.engine)

            modelViewer.view.renderQuality = modelViewer.view.renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.MEDIUM
            }
            modelViewer.view.dynamicResolutionOptions = modelViewer.view.dynamicResolutionOptions.apply {
                enabled = true
                quality = View.QualityLevel.LOW
            }
            modelViewer.view.antiAliasing = View.AntiAliasing.FXAA
            modelViewer.view.ambientOcclusionOptions = modelViewer.view.ambientOcclusionOptions.apply {
                enabled = false
            }
            modelViewer.view.bloomOptions = modelViewer.view.bloomOptions.apply {
                enabled = false
            }

            val bytes = context.assets.open(BODY_MODEL).use { input -> input.readBytes() }
            modelViewer.loadModelGlb(ByteBuffer.wrap(bytes))
            modelViewer.transformToUnitCube()
            applyMassHint()
            onStateChanged(RendererState.READY)
            startFrames()
        } catch (_: Throwable) {
            onStateChanged(RendererState.ERROR)
            stopFrames()
            viewer?.destroy()
            viewer = null
        }
    }

    fun setBodyMassHint(value: Float) {
        massHint = value.coerceIn(.80f, 1.28f)
        applyMassHint()
    }

    private fun applyMassHint() {
        // Keep ModelViewer's normalized root transform intact for stability. The value is retained
        // here as the single place where a morph-enabled GLB can later consume the body mass hint.
        // Current Vitruvian GLB has no safe public parametric morph contract.
        @Suppress("UNUSED_VARIABLE") val stableMassHint = massHint
    }

    private fun startFrames() {
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopFrames() {
        framePosted = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        viewer?.onTouchEvent(event)
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewer == null) post { initializeViewer() } else startFrames()
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        // ModelViewer installs its own detach listener and destroys the underlying Engine exactly
        // once. Do not duplicate native cleanup here.
        viewer = null
        super.onDetachedFromWindow()
    }
}

@Composable
private fun SafeMeasureCard(
    language: String,
    target: SafeBodyTarget,
    existingCm: Float?,
    onConfirm: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    var value by remember(target, existingCm) { mutableStateOf(existingCm?.let(::safeNumber).orEmpty()) }
    val parsed = value.toFloatOrNull()
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = SafeSurface.copy(alpha = .97f),
        border = BorderStroke(1.dp, SafeBlue.copy(alpha = .25f)),
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(target.title(language), color = SafeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(target.instruction(language), color = SafeMuted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = SafeMuted) }
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
                        focusedTextColor = SafeText,
                        unfocusedTextColor = SafeText,
                        focusedBorderColor = SafeBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = .16f),
                    ),
                )
                Button(
                    onClick = { parsed?.takeIf { it > 0f }?.let(onConfirm) },
                    enabled = parsed?.let { it > 0f } == true,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen, contentColor = Color(0xFF062017)),
                ) { Icon(Icons.Rounded.Check, null) }
            }
        }
    }
}

@Composable
private fun SafeWeightDock(language: String, profile: BodyProfile, onKilograms: (Float) -> Unit) {
    var value by remember(profile.weightPounds, profile.hasExplicitWeight) {
        mutableStateOf(if (profile.hasExplicitWeight) safeNumber(profile.weightKilograms) else "")
    }
    val parsed = value.toFloatOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).navigationBarsPadding(),
        shape = RoundedCornerShape(26.dp),
        color = SafeRaised,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (language == "ar") "الوزن" else "Weight", color = SafeText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (language == "ar") "يتفاعل ملف الجسم مع القياسات" else "Body profile reacts to measurements", color = SafeMuted, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                modifier = Modifier.width(132.dp),
                singleLine = true,
                suffix = { Text("kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SafeText,
                    unfocusedTextColor = SafeText,
                    focusedBorderColor = SafeBlue,
                    unfocusedBorderColor = Color.White.copy(alpha = .15f),
                ),
            )
            Button(
                onClick = { parsed?.takeIf { it > 0f }?.let(onKilograms) },
                enabled = parsed?.let { it > 0f } == true,
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen, contentColor = Color(0xFF062017)),
            ) { Icon(Icons.Rounded.Check, null) }
        }
    }
}

@Composable
private fun SafeMeasurementArrow(target: SafeBodyTarget, modifier: Modifier) {
    Canvas(modifier) {
        val (a, b) = target.guide(size.width, size.height)
        drawLine(SafeBlue.copy(alpha = .22f), a, b, 9f, StrokeCap.Round)
        drawLine(SafeBlue, a, b, 3f, StrokeCap.Round)
        safeArrowHead(b, a)
        safeArrowHead(a, b)
    }
}

private fun DrawScope.safeArrowHead(tip: Offset, from: Offset) {
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
    drawPath(path, SafeBlue)
}

private enum class SafeBodyTarget(
    val point: BodyMeasurePoint?,
    val screenX: Float,
    val screenY: Float,
) {
    HEIGHT(null, .58f, .15f),
    NECK(BodyMeasurePoint.NECK, .50f, .25f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, .34f, .31f),
    CHEST(BodyMeasurePoint.CHEST, .50f, .39f),
    WAIST(BodyMeasurePoint.WAIST, .50f, .50f),
    HIPS(BodyMeasurePoint.HIPS, .39f, .58f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, .22f, .47f),
    WRIST(BodyMeasurePoint.WRIST, .18f, .57f),
    HAND(BodyMeasurePoint.HAND, .16f, .65f),
    THIGH(BodyMeasurePoint.THIGH, .42f, .67f),
    INSEAM(BodyMeasurePoint.INSEAM, .50f, .65f),
    CALF(BodyMeasurePoint.CALF, .41f, .79f),
    FOOT(BodyMeasurePoint.FOOT, .42f, .91f),
    ;

    fun valueCm(profile: BodyProfile): Float? = if (this == HEIGHT) {
        profile.heightCentimeters.takeIf { profile.hasExplicitHeight }
    } else point?.let { profile.measurementsInches[it]?.times(CM_PER_INCH_SAFE) }

    fun title(language: String): String = when (this) {
        HEIGHT -> safeTr(language, "الطول", "Height")
        NECK -> safeTr(language, "محيط الرقبة", "Neck")
        SHOULDERS -> safeTr(language, "عرض الكتفين", "Shoulders")
        CHEST -> safeTr(language, "محيط الصدر", "Chest")
        WAIST -> safeTr(language, "محيط الخصر", "Waist")
        HIPS -> safeTr(language, "محيط الورك", "Hips")
        ARM_LENGTH -> safeTr(language, "طول الذراع", "Arm length")
        WRIST -> safeTr(language, "محيط المعصم", "Wrist")
        HAND -> safeTr(language, "طول اليد", "Hand length")
        THIGH -> safeTr(language, "محيط الفخذ", "Thigh")
        INSEAM -> safeTr(language, "طول الساق الداخلي", "Inseam")
        CALF -> safeTr(language, "محيط الساق", "Calf")
        FOOT -> safeTr(language, "طول القدم", "Foot length")
    }

    fun instruction(language: String): String = when (this) {
        HEIGHT -> safeTr(language, "من أعلى الرأس إلى أسفل القدم.", "Top of head to the floor.")
        NECK -> safeTr(language, "حول قاعدة الرقبة بدون شد.", "Around the base of the neck.")
        SHOULDERS -> safeTr(language, "من نهاية كتف إلى نهاية الكتف الآخر.", "Shoulder tip to shoulder tip.")
        CHEST -> safeTr(language, "حول أعرض نقطة من الصدر.", "Around the fullest chest point.")
        WAIST -> safeTr(language, "حول أضيق نقطة من الخصر الطبيعي.", "Around the natural waist.")
        HIPS -> safeTr(language, "حول أعرض نقطة من الورك.", "Around the fullest hips.")
        ARM_LENGTH -> safeTr(language, "من نقطة الكتف إلى عظمة المعصم.", "Shoulder point to wrist bone.")
        WRIST -> safeTr(language, "حول عظمة المعصم.", "Around the wrist bone.")
        HAND -> safeTr(language, "من بداية راحة اليد إلى نهاية أطول إصبع.", "Wrist crease to longest fingertip.")
        THIGH -> safeTr(language, "حول أعرض جزء من أعلى الفخذ.", "Around the fullest upper thigh.")
        INSEAM -> safeTr(language, "من أعلى داخل الساق إلى الأرض.", "Crotch to floor along the inner leg.")
        CALF -> safeTr(language, "حول أعرض نقطة من عضلة الساق.", "Around the fullest calf point.")
        FOOT -> safeTr(language, "من مؤخرة الكعب إلى أطول إصبع.", "Heel to longest toe.")
    }

    fun guide(w: Float, h: Float): Pair<Offset, Offset> = when (this) {
        HEIGHT -> Offset(w * .50f, h * .16f) to Offset(w * .50f, h * .86f)
        NECK -> Offset(w * .43f, h * .25f) to Offset(w * .57f, h * .25f)
        SHOULDERS -> Offset(w * .30f, h * .31f) to Offset(w * .70f, h * .31f)
        CHEST -> Offset(w * .30f, h * .39f) to Offset(w * .70f, h * .39f)
        WAIST -> Offset(w * .36f, h * .50f) to Offset(w * .64f, h * .50f)
        HIPS -> Offset(w * .33f, h * .58f) to Offset(w * .67f, h * .58f)
        ARM_LENGTH -> Offset(w * .31f, h * .31f) to Offset(w * .18f, h * .57f)
        WRIST -> Offset(w * .15f, h * .55f) to Offset(w * .23f, h * .55f)
        HAND -> Offset(w * .18f, h * .57f) to Offset(w * .16f, h * .66f)
        THIGH -> Offset(w * .36f, h * .62f) to Offset(w * .49f, h * .62f)
        INSEAM -> Offset(w * .50f, h * .60f) to Offset(w * .45f, h * .88f)
        CALF -> Offset(w * .38f, h * .78f) to Offset(w * .48f, h * .78f)
        FOOT -> Offset(w * .35f, h * .90f) to Offset(w * .49f, h * .90f)
    }
}

private fun safeTr(language: String, ar: String, en: String) = if (language == "ar") ar else en
private fun safeNumber(value: Float): String = if (abs(value - value.roundToInt()) < .05f) {
    value.roundToInt().toString()
} else {
    "%.1f".format(Locale.US, value)
}
