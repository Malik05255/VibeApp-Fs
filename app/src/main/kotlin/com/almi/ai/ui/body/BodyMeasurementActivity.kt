package com.almi.ai.ui.body

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import java.util.Locale

private const val CM_PER_INCH = 2.54f
private const val KG_PER_POUND = 0.45359237f

/**
 * Filament-first measurement screen. The 3D renderer owns a real SurfaceView directly and every
 * control above it is a classic Android View. Compose is intentionally not used in this Activity.
 */
class BodyMeasurementActivity : ComponentActivity() {
    private lateinit var runtime: PersistentFilamentRuntime
    private lateinit var countView: TextView
    private lateinit var statusView: TextView
    private lateinit var editor: LinearLayout
    private lateinit var editorTitle: TextView
    private lateinit var editorHint: TextView
    private lateinit var editorInput: EditText
    private lateinit var guideView: MeasurementGuideView
    private lateinit var hotspotLayer: FrameLayout
    private lateinit var weightInput: EditText

    private var selectedTarget: NativeBodyTarget? = null
    private var profile = BodyProfile()
    private var language = "ar"
    private val hotspotViews = linkedMapOf<NativeBodyTarget, Pair<View, TextView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        window.statusBarColor = Color.rgb(4, 16, 30)
        window.navigationBarColor = Color.rgb(4, 16, 30)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        language = BodyMeasurementContract.language(intent)
        profile = BodyMeasurementContract.readProfile(intent)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(4, 16, 30)) }
        val surface = SurfaceView(this).apply {
            setZOrderOnTop(false)
            keepScreenOn = true
            setBackgroundColor(Color.BLACK)
        }
        root.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val top = buildTopBar()
        root.addView(top, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)).apply { gravity = Gravity.TOP })

        hotspotLayer = FrameLayout(this)
        root.addView(
            hotspotLayer,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = dp(132)
                bottomMargin = dp(132)
            },
        )

        statusView = pill(if (language == "ar") "يتم تجهيز Filament…" else "Preparing Filament…")
        hotspotLayer.addView(
            statusView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(12)
            },
        )

        guideView = MeasurementGuideView().apply { visibility = View.GONE }
        hotspotLayer.addView(guideView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        NativeBodyTarget.entries.forEach { addHotspot(it) }

        editor = buildMeasurementEditor().apply { visibility = View.GONE }
        hotspotLayer.addView(
            editor,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                leftMargin = dp(16)
                rightMargin = dp(16)
                topMargin = dp(62)
            },
        )

        val weightDock = buildWeightDock()
        root.addView(
            weightDock,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)).apply { gravity = Gravity.BOTTOM },
        )

        setContentView(root)

        runtime = PersistentFilamentRuntime(
            context = this,
            surfaceView = surface,
            onStateChanged = { state -> runOnUiThread { renderState(state) } },
        )
        runtime.initialize()
        applyShape()
        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        if (::runtime.isInitialized) runtime.start()
    }

    override fun onPause() {
        if (::runtime.isInitialized) runtime.stop()
        super.onPause()
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(26), dp(18), dp(12))
            background = solidBg(0xFF071628.toInt(), 0f)
        }

        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(text("ALMI / FILAMENT", 13f, 0xFF86BCFF.toInt(), true))
        titles.addView(text(if (language == "ar") "قياسات جسمك" else "Your measurements", 29f, Color.WHITE, true))
        bar.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        countView = pill("0/14").apply {
            gravity = Gravity.CENTER
            setTextColor(0xFFB7CBE5.toInt())
        }
        bar.addView(countView, LinearLayout.LayoutParams(dp(76), dp(48)).apply { marginEnd = dp(8) })

        val done = text(if (language == "ar") "تم" else "Done", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener {
                setResult(Activity.RESULT_OK, BodyMeasurementContract.resultIntent(profile))
                finish()
            }
        }
        bar.addView(done, LinearLayout.LayoutParams(dp(54), dp(48)))
        return bar
    }

    private fun buildMeasurementEditor(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(0xF20B1A2C.toInt(), 22f, 0x5586BCFF)
            elevation = dp(12).toFloat()

            val header = LinearLayout(this@BodyMeasurementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val copy = LinearLayout(this@BodyMeasurementActivity).apply { orientation = LinearLayout.VERTICAL }
            editorTitle = text("", 18f, Color.WHITE, true)
            editorHint = text("", 12f, 0xFF91A8C5.toInt(), false)
            copy.addView(editorTitle)
            copy.addView(editorHint)
            header.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val close = text("×", 28f, 0xFF91A8C5.toInt(), false).apply {
                gravity = Gravity.CENTER
                setOnClickListener { closeEditor() }
            }
            header.addView(close, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(header)

            val inputRow = LinearLayout(this@BodyMeasurementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
            }
            editorInput = EditText(this@BodyMeasurementActivity).apply {
                textSize = 22f
                setTextColor(Color.WHITE)
                hint = "cm"
                setHintTextColor(0xFF607B9B.toInt())
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                singleLine = true
                setPadding(dp(14), 0, dp(14), 0)
                background = roundedBg(0xFF071524.toInt(), 16f, 0x446D8FB5)
            }
            inputRow.addView(editorInput, LinearLayout.LayoutParams(0, dp(56), 1f))

            val confirm = text("✓", 28f, 0xFF062017.toInt(), true).apply {
                gravity = Gravity.CENTER
                background = roundedBg(0xFF59D8A6.toInt(), 16f)
                setOnClickListener { saveSelectedMeasurement() }
            }
            inputRow.addView(confirm, LinearLayout.LayoutParams(dp(58), dp(56)).apply { marginStart = dp(10) })
            addView(inputRow)
        }
    }

    private fun buildWeightDock(): LinearLayout {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(18))
            background = roundedBg(0xFF10243B.toInt(), 28f, 0x336D8FB5)
        }
        val label = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        label.addView(text(if (language == "ar") "الوزن" else "Weight", 23f, Color.WHITE, true))
        label.addView(text(if (language == "ar") "يتفاعل حجم الجسم مباشرة" else "Body volume reacts instantly", 12f, 0xFF91A8C5.toInt(), false))
        dock.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        weightInput = EditText(this).apply {
            textSize = 23f
            setTextColor(Color.WHITE)
            hint = "kg"
            setHintTextColor(0xFF607B9B.toInt())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = Gravity.CENTER
            singleLine = true
            background = roundedBg(0xFF071524.toInt(), 16f, 0x446D8FB5)
        }
        dock.addView(weightInput, LinearLayout.LayoutParams(dp(112), dp(58)))

        val confirm = text("✓", 28f, 0xFF062017.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(0xFF59D8A6.toInt(), 16f)
            setOnClickListener {
                weightInput.text.toString().toFloatOrNull()?.takeIf { it in 20f..320f }?.let { kg ->
                    profile = profile.copy(weightPounds = kg / KG_PER_POUND, hasExplicitWeight = true)
                    applyShape()
                    refreshUi()
                }
            }
        }
        dock.addView(confirm, LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginStart = dp(10) })
        return dock
    }

    private fun addHotspot(target: NativeBodyTarget) {
        val holder = FrameLayout(this).apply {
            visibility = View.INVISIBLE
            setOnClickListener { openEditor(target) }
        }
        val dot = View(this).apply {
            background = roundedBg(0xFFFF433D.toInt(), 99f, 0xFFF9C3C3.toInt(), 2)
            elevation = dp(8).toFloat()
        }
        holder.addView(dot, FrameLayout.LayoutParams(dp(17), dp(17)).apply { gravity = Gravity.CENTER })
        val value = text("", 11f, Color.WHITE, true).apply {
            setShadowLayer(5f, 0f, 1f, Color.BLACK)
            gravity = Gravity.CENTER
        }
        holder.addView(value, FrameLayout.LayoutParams(dp(72), dp(24)).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            leftMargin = dp(27)
        })
        hotspotLayer.addView(holder, FrameLayout.LayoutParams(dp(102), dp(46)))
        hotspotViews[target] = holder to value

        ObjectAnimator.ofPropertyValuesHolder(
            dot,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.82f, 1.18f, 0.82f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.82f, 1.18f, 0.82f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.72f, 1f, 0.72f),
        ).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            start()
        }

        hotspotLayer.post { positionHotspot(target, holder) }
    }

    private fun positionHotspot(target: NativeBodyTarget, holder: View) {
        val width = hotspotLayer.width
        val height = hotspotLayer.height
        if (width <= 0 || height <= 0) {
            hotspotLayer.post { positionHotspot(target, holder) }
            return
        }
        holder.x = width * target.x - dp(23).toFloat()
        holder.y = height * target.y - dp(23).toFloat()
    }

    private fun openEditor(target: NativeBodyTarget) {
        selectedTarget = target
        editorTitle.text = target.title(language)
        editorHint.text = target.instruction(language)
        editorInput.setText(target.valueCm(profile)?.let(::formatNumber).orEmpty())
        editor.visibility = View.VISIBLE
        guideView.setTarget(target)
        guideView.visibility = View.VISIBLE
        runtime.focusOn(target.focusY, target.focusDistance)
        editorInput.requestFocus()
    }

    private fun closeEditor() {
        selectedTarget = null
        editor.visibility = View.GONE
        guideView.visibility = View.GONE
        runtime.resetFocus()
    }

    private fun saveSelectedMeasurement() {
        val target = selectedTarget ?: return
        val centimeters = editorInput.text.toString().toFloatOrNull()?.takeIf { it in 1f..280f } ?: return
        profile = if (target == NativeBodyTarget.HEIGHT) {
            profile.copy(heightInches = centimeters / CM_PER_INCH, hasExplicitHeight = true)
        } else {
            val point = target.point ?: return
            profile.copy(measurementsInches = profile.measurementsInches + (point to centimeters / CM_PER_INCH))
        }
        applyShape()
        refreshUi()
        closeEditor()
    }

    private fun refreshUi() {
        val completed = NativeBodyTarget.entries.count { it.valueCm(profile) != null } + if (profile.hasExplicitWeight) 1 else 0
        countView.text = "$completed/14"
        if (profile.hasExplicitWeight) weightInput.setText(formatNumber(profile.weightPounds * KG_PER_POUND))
        hotspotViews.forEach { (target, pair) ->
            pair.second.text = target.valueCm(profile)?.let { "${formatNumber(it)} cm" }.orEmpty()
        }
    }

    private fun renderState(state: BodyRendererState) {
        statusView.text = when (state) {
            BodyRendererState.LOADING -> if (language == "ar") "يتم تجهيز الجسم…" else "Preparing body…"
            BodyRendererState.READY -> if (language == "ar") "اسحب 360° • اضغط النقطة الحمراء" else "Drag 360° • tap a red point"
            BodyRendererState.ERROR -> if (language == "ar") "تعذر تحميل المجسم" else "Could not load the body"
        }
        hotspotViews.values.forEach { it.first.visibility = if (state == BodyRendererState.READY) View.VISIBLE else View.INVISIBLE }
    }

    private fun applyShape() {
        if (!::runtime.isInitialized) return
        val shape = BodyShapeSolver.solve(profile)
        runtime.updateBodyShape(shape.widthScale, shape.heightScale, shape.depthScale)
        runtime.updateProfile(profile)
    }

    private fun pill(value: String): TextView = text(value, 14f, 0xFF91A8C5.toInt(), true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), 0, dp(16), 0)
        background = roundedBg(0xE610243B.toInt(), 99f, 0x336D8FB5)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (language == "ar") textDirection = View.TEXT_DIRECTION_RTL
    }

    private fun solidBg(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun roundedBg(color: Int, radius: Float, stroke: Int? = null, strokeDp: Int = 1): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (stroke != null) setStroke(dp(strokeDp), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

    private inner class MeasurementGuideView : View(this@BodyMeasurementActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF86BCFF.toInt()
            strokeWidth = dp(3).toFloat()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFB9DAFF.toInt()
            style = Paint.Style.FILL
        }
        private var target: NativeBodyTarget? = null
        private var phase = 0f
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 850L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        fun setTarget(value: NativeBodyTarget) {
            target = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val item = target ?: return
            val sx = width * item.guideStartX
            val sy = height * item.guideStartY
            val ex = width * item.guideEndX
            val ey = height * item.guideEndY
            val px = sx + (ex - sx) * phase
            val py = sy + (ey - sy) * phase
            canvas.drawLine(sx, sy, ex, ey, paint)
            canvas.drawCircle(px, py, dp(5).toFloat(), glowPaint)

            val angle = kotlin.math.atan2((ey - sy).toDouble(), (ex - sx).toDouble())
            val len = dp(13).toFloat()
            val path = Path().apply {
                moveTo(ex, ey)
                lineTo((ex - len * kotlin.math.cos(angle - 0.55)).toFloat(), (ey - len * kotlin.math.sin(angle - 0.55)).toFloat())
                moveTo(ex, ey)
                lineTo((ex - len * kotlin.math.cos(angle + 0.55)).toFloat(), (ey - len * kotlin.math.sin(angle + 0.55)).toFloat())
            }
            canvas.drawPath(path, paint)
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }
    }
}

private enum class NativeBodyTarget(
    val point: BodyMeasurePoint?,
    val x: Float,
    val y: Float,
    val focusY: Float,
    val focusDistance: Float,
    val guideStartX: Float,
    val guideStartY: Float,
    val guideEndX: Float,
    val guideEndY: Float,
) {
    HEIGHT(null, .53f, .10f, 0f, 2.45f, .42f, .09f, .42f, .91f),
    NECK(BodyMeasurePoint.NECK, .50f, .18f, .64f, 1.55f, .45f, .18f, .55f, .18f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, .30f, .23f, .52f, 1.65f, .30f, .23f, .70f, .23f),
    CHEST(BodyMeasurePoint.CHEST, .63f, .31f, .34f, 1.65f, .35f, .31f, .65f, .31f),
    WAIST(BodyMeasurePoint.WAIST, .38f, .42f, .10f, 1.55f, .38f, .42f, .62f, .42f),
    HIPS(BodyMeasurePoint.HIPS, .65f, .50f, -.10f, 1.58f, .35f, .50f, .65f, .50f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, .22f, .36f, .29f, 1.48f, .31f, .24f, .18f, .49f),
    WRIST(BodyMeasurePoint.WRIST, .16f, .49f, .05f, 1.35f, .13f, .47f, .20f, .47f),
    HAND(BodyMeasurePoint.HAND, .13f, .56f, -.05f, 1.30f, .15f, .50f, .11f, .58f),
    THIGH(BodyMeasurePoint.THIGH, .64f, .64f, -.38f, 1.48f, .54f, .62f, .67f, .62f),
    INSEAM(BodyMeasurePoint.INSEAM, .48f, .67f, -.36f, 1.65f, .50f, .52f, .50f, .90f),
    CALF(BodyMeasurePoint.CALF, .65f, .79f, -.66f, 1.42f, .58f, .78f, .68f, .78f),
    FOOT(BodyMeasurePoint.FOOT, .61f, .91f, -.82f, 1.30f, .55f, .91f, .70f, .91f),
    ;

    fun title(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "الطول"
        NECK -> "محيط الرقبة"
        SHOULDERS -> "عرض الكتفين"
        CHEST -> "محيط الصدر"
        WAIST -> "محيط الخصر"
        HIPS -> "محيط الورك"
        ARM_LENGTH -> "طول الذراع"
        WRIST -> "محيط المعصم"
        HAND -> "طول اليد"
        THIGH -> "محيط الفخذ"
        INSEAM -> "طول الساق الداخلي"
        CALF -> "محيط الساق"
        FOOT -> "طول القدم"
    } else name.lowercase().replace('_', ' ')

    fun instruction(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "من أعلى الرأس حتى أسفل القدم"
        NECK -> "لف شريط القياس حول قاعدة الرقبة"
        SHOULDERS -> "من نهاية كتف إلى نهاية الكتف الآخر"
        CHEST -> "حول أعرض نقطة في الصدر"
        WAIST -> "حول أضيق نقطة في الخصر"
        HIPS -> "حول أعرض نقطة في الورك"
        ARM_LENGTH -> "من مفصل الكتف حتى نهاية المعصم"
        WRIST -> "حول المعصم مباشرة"
        HAND -> "من بداية راحة اليد حتى نهاية الإصبع الأوسط"
        THIGH -> "حول أعرض نقطة في الفخذ"
        INSEAM -> "من أعلى الفخذ الداخلي حتى الكاحل"
        CALF -> "حول أعرض نقطة في بطة الساق"
        FOOT -> "من مؤخرة الكعب حتى أطول إصبع"
    } else "Follow the animated guide, then enter the measurement in centimeters."

    fun valueCm(profile: BodyProfile): Float? = when (this) {
        HEIGHT -> profile.heightInches.takeIf { profile.hasExplicitHeight }?.times(CM_PER_INCH)
        else -> point?.let { profile.measurementsInches[it]?.times(CM_PER_INCH) }
    }
}
