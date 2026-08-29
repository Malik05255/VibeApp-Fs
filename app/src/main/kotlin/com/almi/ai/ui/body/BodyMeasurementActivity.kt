package com.almi.ai.ui.body

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
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val CM_PER_INCH = 2.54f
private const val KG_PER_POUND = 0.45359237f
private const val NAVY = 0xFF04101E.toInt()
private const val PANEL = 0xF20B1A2C.toInt()
private const val PANEL_SOFT = 0xED10243B.toInt()
private const val BLUE = 0xFF62A9FF.toInt()
private const val TEXT_SOFT = 0xFF9CB4D0.toInt()

/**
 * Pixel-directed ALMI body-measurement screen.
 *
 * Filament owns only the 3D SurfaceView. All measurement labels, hotspots, progress, editor card
 * and weight dock are classic Android Views layered above it, which keeps the native renderer
 * isolated while matching the product reference closely.
 */
class BodyMeasurementActivity : ComponentActivity() {
    private lateinit var runtime: PersistentFilamentRuntime
    private lateinit var countView: TextView
    private lateinit var progressView: BodyProgressView
    private lateinit var statusView: TextView
    private lateinit var editor: LinearLayout
    private lateinit var editorTitle: TextView
    private lateinit var editorInput: EditText
    private lateinit var guideView: MeasurementGuideView
    private lateinit var annotationsView: MeasurementAnnotationsView
    private lateinit var hotspotLayer: FrameLayout
    private lateinit var weightInput: EditText

    private var selectedTarget: NativeBodyTarget? = null
    private var profile = BodyProfile()
    private var language = "ar"
    private val hotspotViews = linkedMapOf<NativeBodyTarget, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        window.statusBarColor = NAVY
        window.navigationBarColor = NAVY
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        language = BodyMeasurementContract.language(intent)
        profile = BodyMeasurementContract.readProfile(intent)

        val root = FrameLayout(this).apply { setBackgroundColor(NAVY) }

        val surface = SurfaceView(this).apply {
            setZOrderOnTop(false)
            keepScreenOn = true
            setBackgroundColor(NAVY)
        }
        root.addView(
            surface,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val top = buildTopBar()
        root.addView(
            top,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(154)).apply {
                gravity = Gravity.TOP
            },
        )

        hotspotLayer = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        root.addView(
            hotspotLayer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply {
                topMargin = dp(154)
                bottomMargin = dp(116)
            },
        )

        statusView = pill(
            if (language == "ar") "☝  اسحب 360°  •  اضغط النقطة الحمراء"
            else "☝  Drag 360°  •  tap a red point",
        ).apply {
            textSize = 14f
            setTextColor(0xFFD4E5FA.toInt())
            background = roundedBg(0xE80C1E32.toInt(), 99f, 0x496987AB)
        }
        hotspotLayer.addView(
            statusView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            },
        )

        annotationsView = MeasurementAnnotationsView()
        hotspotLayer.addView(
            annotationsView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        guideView = MeasurementGuideView().apply { visibility = View.GONE }
        hotspotLayer.addView(
            guideView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        NativeBodyTarget.entries.forEach(::addHotspot)

        editor = buildMeasurementEditor().apply { visibility = View.GONE }
        hotspotLayer.addView(
            editor,
            FrameLayout.LayoutParams(dp(176), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = dp(12)
                topMargin = dp(250)
            },
        )

        val weightDock = buildWeightDock()
        root.addView(
            weightDock,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(104)).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(8)
            },
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
        commitWeight()
        if (::runtime.isInitialized) runtime.stop()
        super.onPause()
    }

    private fun buildTopBar(): View {
        val bar = FrameLayout(this).apply {
            setPadding(dp(16), dp(10), dp(16), dp(8))
            background = solidBg(NAVY, 0f)
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        titleBlock.addView(
            text("ALMI / FILAMENT", 15f, 0xFF87BFFF.toInt(), true).apply {
                gravity = Gravity.CENTER
            },
        )
        titleBlock.addView(
            text(if (language == "ar") "قياسات جسمك" else "Your measurements", 30f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            },
        )
        bar.addView(
            titleBlock,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
            },
        )

        val done = text(if (language == "ar") "✓  تم" else "✓  Done", 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(0xD8102237.toInt(), 99f, 0x5E6A86A6)
            setOnClickListener {
                commitWeight()
                setResult(Activity.RESULT_OK, BodyMeasurementContract.resultIntent(profile))
                finish()
            }
        }
        bar.addView(
            done,
            FrameLayout.LayoutParams(dp(82), dp(46)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(7)
            },
        )

        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        countView = text("0/14", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(0xE8172940.toInt(), 14f)
        }
        progressRow.addView(countView, LinearLayout.LayoutParams(dp(55), dp(34)))

        progressView = BodyProgressView()
        progressRow.addView(
            progressView,
            LinearLayout.LayoutParams(dp(132), dp(16)).apply {
                marginStart = dp(12)
            },
        )

        bar.addView(
            progressRow,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(3)
            },
        )

        return bar
    }

    private fun buildMeasurementEditor(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBg(PANEL, 20f, 0x5C6C87A6)
            elevation = dp(14).toFloat()

            editorTitle = text("", 18f, Color.WHITE, false).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            addView(
                editorTitle,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )

            val inputShell = LinearLayout(this@BodyMeasurementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = roundedBg(0xFF081625.toInt(), 12f, 0x50627C99)
            }
            editorInput = EditText(this@BodyMeasurementActivity).apply {
                textSize = 22f
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF69809A.toInt())
                hint = "0"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                imeOptions = EditorInfo.IME_ACTION_DONE
                gravity = Gravity.CENTER
                setSingleLine(true)
                setPadding(dp(8), 0, dp(4), 0)
                background = null
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        saveSelectedMeasurement()
                        true
                    } else {
                        false
                    }
                }
            }
            inputShell.addView(editorInput, LinearLayout.LayoutParams(0, dp(54), 1f))
            inputShell.addView(
                text("cm", 16f, 0xFFD4E4F8.toInt(), false).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(dp(48), dp(54)),
            )
            addView(
                inputShell,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply {
                    topMargin = dp(9)
                },
            )

            val actions = LinearLayout(this@BodyMeasurementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val cancel = text("×", 26f, 0xFFD4E4F8.toInt(), false).apply {
                gravity = Gravity.CENTER
                background = roundedBg(0xFF101F31.toInt(), 12f, 0x405D7897)
                setOnClickListener { closeEditor() }
            }
            actions.addView(cancel, LinearLayout.LayoutParams(0, dp(48), 1f))

            val confirm = text("✓", 26f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = roundedBg(BLUE, 12f)
                setOnClickListener { saveSelectedMeasurement() }
            }
            actions.addView(
                confirm,
                LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(9) },
            )
            addView(
                actions,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                    topMargin = dp(9)
                },
            )
        }
    }

    private fun buildWeightDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBg(PANEL_SOFT, 24f, 0x5A5F7A98)
            elevation = dp(10).toFloat()
        }

        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        label.addView(text(if (language == "ar") "الوزن" else "Weight", 19f, Color.WHITE, true))
        label.addView(
            text(
                if (language == "ar") "يتفاعل حجم الجسم مباشرة" else "Body volume reacts instantly",
                11f,
                TEXT_SOFT,
                false,
            ),
        )
        dock.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        weightInput = EditText(this).apply {
            textSize = 27f
            setTextColor(Color.WHITE)
            hint = "80"
            setHintTextColor(0xFF6B829E.toInt())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(dp(10), 0, dp(10), 0)
            background = roundedBg(0xFF071524.toInt(), 14f, 0x70667F9B)
            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commitWeight() }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitWeight()
                    clearFocus()
                    true
                } else {
                    false
                }
            }
        }
        dock.addView(weightInput, LinearLayout.LayoutParams(dp(136), dp(60)).apply { marginStart = dp(10) })

        val unit = text("kg   ▾", 16f, 0xFFD7E6F8.toInt(), true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(0xFF0A192A.toInt(), 14f, 0x70667F9B)
        }
        dock.addView(unit, LinearLayout.LayoutParams(dp(76), dp(60)).apply { marginStart = dp(8) })

        return dock
    }

    private fun addHotspot(target: NativeBodyTarget) {
        val hit = View(this).apply {
            visibility = View.INVISIBLE
            background = null
            setOnClickListener { openEditor(target) }
        }
        hotspotLayer.addView(hit, FrameLayout.LayoutParams(dp(48), dp(48)))
        hotspotViews[target] = hit
        hotspotLayer.post { positionHotspot(target, hit) }
    }

    private fun positionHotspot(target: NativeBodyTarget, holder: View) {
        val width = hotspotLayer.width
        val height = hotspotLayer.height
        if (width <= 0 || height <= 0) {
            hotspotLayer.post { positionHotspot(target, holder) }
            return
        }
        holder.x = width * target.x - dp(24).toFloat()
        holder.y = height * target.y - dp(24).toFloat()
    }

    private fun openEditor(target: NativeBodyTarget) {
        selectedTarget = target
        editorTitle.text = target.title(language)
        editorInput.setText(target.valueCm(profile)?.let(::formatNumber).orEmpty())
        editor.visibility = View.VISIBLE
        guideView.setTarget(target)
        guideView.visibility = View.VISIBLE
        annotationsView.selectedTarget = target
        positionEditor(target)
        runtime.focusOn(target.focusY, target.focusDistance)
        editorInput.requestFocus()
    }

    private fun positionEditor(target: NativeBodyTarget) {
        hotspotLayer.post {
            val desired = (hotspotLayer.height * target.y - dp(78)).toInt()
            val maxTop = (hotspotLayer.height - dp(184)).coerceAtLeast(dp(70))
            val lp = editor.layoutParams as FrameLayout.LayoutParams
            lp.topMargin = desired.coerceIn(dp(70), maxTop)
            editor.layoutParams = lp
        }
    }

    private fun closeEditor() {
        selectedTarget = null
        editor.visibility = View.GONE
        guideView.visibility = View.GONE
        annotationsView.selectedTarget = null
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

    private fun commitWeight() {
        if (!::weightInput.isInitialized) return
        val kg = weightInput.text.toString().toFloatOrNull()?.takeIf { it in 20f..320f } ?: return
        val existing = profile.weightPounds * KG_PER_POUND
        if (!profile.hasExplicitWeight || kotlin.math.abs(existing - kg) > 0.05f) {
            profile = profile.copy(weightPounds = kg / KG_PER_POUND, hasExplicitWeight = true)
            applyShape()
            refreshUi()
        }
    }

    private fun refreshUi() {
        val completed = NativeBodyTarget.entries.count { it.valueCm(profile) != null } +
            if (profile.hasExplicitWeight) 1 else 0
        countView.text = "$completed/14"
        progressView.progress = completed / 14f
        if (profile.hasExplicitWeight && !weightInput.hasFocus()) {
            weightInput.setText(formatNumber(profile.weightPounds * KG_PER_POUND))
        }
        annotationsView.profile = profile
        annotationsView.invalidate()
    }

    private fun renderState(state: BodyRendererState) {
        statusView.text = when (state) {
            BodyRendererState.LOADING -> if (language == "ar") "يتم تجهيز الجسم ثلاثي الأبعاد…" else "Preparing 3D body…"
            BodyRendererState.READY -> if (language == "ar") "☝  اسحب 360°  •  اضغط النقطة الحمراء" else "☝  Drag 360°  •  tap a red point"
            BodyRendererState.ERROR -> if (language == "ar") "تعذر تحميل المجسم ثلاثي الأبعاد" else "Could not load the 3D body"
        }
        val visible = state == BodyRendererState.READY
        annotationsView.bodyReady = visible
        annotationsView.invalidate()
        hotspotViews.values.forEach { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE }
    }

    private fun applyShape() {
        if (!::runtime.isInitialized) return
        val shape = BodyShapeSolver.solve(profile)
        runtime.updateBodyShape(shape.widthScale, shape.heightScale, shape.depthScale)
        runtime.updateProfile(profile)
    }

    private fun pill(value: String): TextView = text(value, 14f, TEXT_SOFT, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(18), 0, dp(18), 0)
        background = roundedBg(0xE610243B.toInt(), 99f, 0x336D8FB5)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (language == "ar") textDirection = View.TEXT_DIRECTION_RTL
    }

    private fun solidBg(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
    }

    private fun roundedBg(
        color: Int,
        radius: Float,
        stroke: Int? = null,
        strokeDp: Int = 1,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius.toInt()).toFloat()
        if (stroke != null) setStroke(dp(strokeDp), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)

    private inner class BodyProgressView : View(this@BodyMeasurementActivity) {
        var progress: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF132238.toInt() }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = height / 2f
            canvas.drawRoundRect(0f, height * .27f, width.toFloat(), height * .73f, radius, radius, track)
            val end = width * progress
            if (end > 0f) {
                canvas.drawRoundRect(0f, height * .27f, end, height * .73f, radius, radius, fill)
            }
        }
    }

    private inner class MeasurementAnnotationsView : View(this@BodyMeasurementActivity) {
        var profile: BodyProfile = this@BodyMeasurementActivity.profile
        var bodyReady: Boolean = false
        var selectedTarget: NativeBodyTarget? = null
            set(value) {
                field = value
                invalidate()
            }

        private var pulse = 0f
        private val connector = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF5E7692.toInt()
            strokeWidth = dp(1).toFloat()
            style = Paint.Style.STROKE
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFC0C0.toInt()
            strokeWidth = dp(1).toFloat()
            style = Paint.Style.STROKE
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF0F6FF.toInt()
            textSize = dp(11).toFloat()
        }
        private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD9E8F8.toInt()
            textSize = dp(10).toFloat()
        }

        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!bodyReady) return

            NativeBodyTarget.entries.forEach { target ->
                val cx = width * target.x
                val cy = height * target.y
                val placement = labelPlacement(target)
                val ex = cx + dp(placement.first).toFloat()
                val ey = cy + dp(placement.second).toFloat()

                connector.color = if (selectedTarget == target) BLUE else 0xFF5E7692.toInt()
                canvas.drawLine(cx, cy, ex, ey, connector)
                canvas.drawCircle(ex, ey, dp(2).toFloat(), connector)

                val rightSide = placement.first >= 0
                label.textAlign = if (rightSide) Paint.Align.LEFT else Paint.Align.RIGHT
                value.textAlign = label.textAlign
                val textX = ex + if (rightSide) dp(5) else -dp(5)
                canvas.drawText(target.title(language), textX.toFloat(), ey - dp(3), label)
                val measured = target.valueCm(profile)
                if (measured != null) {
                    canvas.drawText("${formatNumber(measured)} cm", textX.toFloat(), ey + dp(11), value)
                }

                val selected = selectedTarget == target
                glow.color = if (selected) 0x5062A9FF else 0x55FF3434
                val glowRadius = dp(if (selected) 13 else 10).toFloat() + pulse * dp(2)
                canvas.drawCircle(cx, cy, glowRadius, glow)
                dot.color = if (selected) BLUE else 0xFFFF3434.toInt()
                canvas.drawCircle(cx, cy, dp(6).toFloat(), dot)
                canvas.drawCircle(cx, cy, dp(7).toFloat(), dotStroke)
            }
        }

        private fun labelPlacement(target: NativeBodyTarget): Pair<Int, Int> = when (target) {
            NativeBodyTarget.HEIGHT -> -56 to -10
            NativeBodyTarget.NECK -> -42 to -16
            NativeBodyTarget.SHOULDERS -> 38 to -14
            NativeBodyTarget.CHEST -> 16 to 25
            NativeBodyTarget.WAIST -> 24 to 6
            NativeBodyTarget.HIPS -> -38 to 12
            NativeBodyTarget.ARM_LENGTH -> 34 to -8
            NativeBodyTarget.WRIST -> 36 to 4
            NativeBodyTarget.HAND -> 34 to 15
            NativeBodyTarget.THIGH -> -34 to 12
            NativeBodyTarget.INSEAM -> 32 to 12
            NativeBodyTarget.CALF -> -34 to 7
            NativeBodyTarget.FOOT -> -34 to -5
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }
    }

    private inner class MeasurementGuideView : View(this@BodyMeasurementActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLUE
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
            drawArrowHead(canvas, sx, sy, ex, ey)
            drawArrowHead(canvas, ex, ey, sx, sy)
        }

        private fun drawArrowHead(canvas: Canvas, tipX: Float, tipY: Float, fromX: Float, fromY: Float) {
            val angle = atan2((tipY - fromY).toDouble(), (tipX - fromX).toDouble())
            val len = dp(13).toFloat()
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(
                    (tipX - len * cos(angle - 0.55)).toFloat(),
                    (tipY - len * sin(angle - 0.55)).toFloat(),
                )
                moveTo(tipX, tipY)
                lineTo(
                    (tipX - len * cos(angle + 0.55)).toFloat(),
                    (tipY - len * sin(angle + 0.55)).toFloat(),
                )
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
    HEIGHT(null, .50f, .12f, 0f, 2.20f, .42f, .10f, .42f, .91f),
    NECK(BodyMeasurePoint.NECK, .50f, .22f, .64f, 1.55f, .45f, .22f, .55f, .22f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, .64f, .26f, .52f, 1.65f, .34f, .26f, .66f, .26f),
    CHEST(BodyMeasurePoint.CHEST, .50f, .34f, .34f, 1.65f, .36f, .34f, .64f, .34f),
    WAIST(BodyMeasurePoint.WAIST, .50f, .44f, .10f, 1.55f, .39f, .44f, .61f, .44f),
    HIPS(BodyMeasurePoint.HIPS, .40f, .51f, -.10f, 1.58f, .36f, .51f, .64f, .51f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, .75f, .44f, .29f, 1.48f, .66f, .27f, .76f, .50f),
    WRIST(BodyMeasurePoint.WRIST, .76f, .52f, .05f, 1.35f, .72f, .51f, .80f, .51f),
    HAND(BodyMeasurePoint.HAND, .72f, .58f, -.05f, 1.30f, .73f, .52f, .72f, .61f),
    THIGH(BodyMeasurePoint.THIGH, .40f, .64f, -.38f, 1.48f, .37f, .64f, .52f, .64f),
    INSEAM(BodyMeasurePoint.INSEAM, .52f, .68f, -.36f, 1.65f, .52f, .53f, .52f, .89f),
    CALF(BodyMeasurePoint.CALF, .41f, .80f, -.66f, 1.42f, .38f, .80f, .50f, .80f),
    FOOT(BodyMeasurePoint.FOOT, .42f, .91f, -.82f, 1.30f, .38f, .91f, .50f, .91f),
    ;

    fun title(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "الطول"
        NECK -> "محيط الرقبة"
        SHOULDERS -> "عرض الكتفين"
        CHEST -> "محيط الصدر"
        WAIST -> "محيط الخصر"
        HIPS -> "محيط الحوض"
        ARM_LENGTH -> "طول الذراع"
        WRIST -> "محيط المعصم"
        HAND -> "طول اليد"
        THIGH -> "محيط الفخذ"
        INSEAM -> "طول الساق الداخلي"
        CALF -> "محيط الساق"
        FOOT -> "محيط الكاحل"
    } else when (this) {
        HEIGHT -> "Height"
        NECK -> "Neck circumference"
        SHOULDERS -> "Shoulder width"
        CHEST -> "Chest circumference"
        WAIST -> "Waist circumference"
        HIPS -> "Hip circumference"
        ARM_LENGTH -> "Arm length"
        WRIST -> "Wrist circumference"
        HAND -> "Hand length"
        THIGH -> "Thigh circumference"
        INSEAM -> "Inseam"
        CALF -> "Calf circumference"
        FOOT -> "Ankle / foot"
    }

    fun instruction(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "من أعلى الرأس حتى أسفل القدم"
        NECK -> "لف شريط القياس حول قاعدة الرقبة"
        SHOULDERS -> "من نهاية كتف إلى نهاية الكتف الآخر"
        CHEST -> "حول أعرض نقطة في الصدر"
        WAIST -> "حول أضيق نقطة في الخصر"
        HIPS -> "حول أعرض نقطة في الحوض"
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
