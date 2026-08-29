package com.almi.ai.ui.body

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.BodyProfileStore
import com.almi.ai.data.preferences.BodySideMeasurement
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val CM_PER_INCH = 2.54f
private const val KG_PER_POUND = 0.45359237f
private const val V11_BG = 0xFF111113.toInt()
private const val V11_PANEL = 0xF21C1C20.toInt()
private const val V11_PANEL_2 = 0xFA252529.toInt()
private const val V11_RED = 0xFFFF3B43.toInt()
private const val V11_IVORY = 0xFFF4EEE7.toInt()
private const val V11_MUTED = 0xFFAAA5A0.toInt()

/** Precision-first v11 body map. */
@AndroidEntryPoint
class V11MeasurementActivity : ComponentActivity() {
    @Inject lateinit var bodyProfileStore: BodyProfileStore

    private lateinit var runtime: V11BodyRuntime
    private lateinit var surfaceView: SurfaceView
    private lateinit var overlay: LandmarkOverlay
    private lateinit var progressText: TextView
    private lateinit var editorDock: LinearLayout
    private lateinit var editorTitle: TextView
    private lateinit var primaryInput: EditText
    private lateinit var secondaryInput: EditText
    private lateinit var secondaryRow: LinearLayout
    private lateinit var weightInput: EditText

    private var profile = BodyProfile()
    private var language = "ar"
    private var projection: V11BodyProjection? = null
    private var selected: V11Target? = null
    private var rendererReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        language = BodyMeasurementContract.language(intent)
        profile = BodyMeasurementContract.readProfile(intent)

        window.statusBarColor = V11_BG
        window.navigationBarColor = V11_BG
        window.isNavigationBarContrastEnforced = false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        val root = FrameLayout(this).apply { setBackgroundColor(V11_BG) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        surfaceView = SurfaceView(this).apply {
            keepScreenOn = true
            setZOrderOnTop(false)
            background = null
        }
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))

        overlay = LandmarkOverlay()
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        root.addView(buildTopBar(), FrameLayout.LayoutParams(-1, dp(86)).apply { gravity = Gravity.TOP })

        editorDock = buildEditorDock().apply { visibility = View.GONE }
        root.addView(
            editorDock,
            FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(12)
                rightMargin = dp(12)
                bottomMargin = dp(10)
            },
        )

        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.requestApplyInsets(root)

        runtime = V11BodyRuntime(
            context = this,
            surfaceView = surfaceView,
            onStateChanged = { state -> runOnUiThread { onRendererState(state) } },
            onProjectionChanged = { value ->
                projection = value
                runOnUiThread { overlay.invalidate() }
            },
        )
        runtime.initialize()
        applyShape()
        refreshProgress()
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
        val bar = FrameLayout(this).apply {
            setPadding(dp(14), dp(8), dp(14), dp(6))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xF50B0B0D.toInt(), 0xD80B0B0D.toInt(), 0x000B0B0D),
            )
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        titles.addView(text("ALMI / BODY MAP", 10f, V11_RED, true))
        titles.addView(text(if (language == "ar") "القياس الدقيق" else "Precision map", 22f, Color.WHITE, true))
        bar.addView(titles, FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })

        progressText = text("0/${V11Target.entries.size}", 11f, V11_IVORY, true).apply {
            gravity = Gravity.CENTER
            background = rounded(0xB52A2A2E.toInt(), 999f, 0x443F3F44)
        }
        bar.addView(progressText, FrameLayout.LayoutParams(dp(58), dp(32)).apply { gravity = Gravity.CENTER })

        val done = text(if (language == "ar") "تم" else "Done", 12f, V11_BG, true).apply {
            gravity = Gravity.CENTER
            background = rounded(V11_IVORY, 999f)
            setOnClickListener { finishSuccess() }
        }
        bar.addView(done, FrameLayout.LayoutParams(dp(68), dp(38)).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL })
        return bar
    }

    private fun buildEditorDock(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(11), dp(14), dp(12))
        background = rounded(V11_PANEL, 24f, 0x50444449)
        elevation = dp(10).toFloat()

        val head = LinearLayout(this@V11MeasurementActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        editorTitle = text("", 15f, Color.WHITE, true)
        head.addView(editorTitle, LinearLayout.LayoutParams(0, dp(30), 1f))
        val close = text("×", 22f, V11_MUTED, false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { closeEditor() }
        }
        head.addView(close, LinearLayout.LayoutParams(dp(36), dp(32)))
        addView(head, LinearLayout.LayoutParams(-1, dp(32)))

        primaryInput = numericInput()
        addView(primaryInput, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })

        secondaryRow = LinearLayout(this@V11MeasurementActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        secondaryInput = numericInput()
        secondaryRow.addView(secondaryInput, LinearLayout.LayoutParams(0, dp(50), 1f))
        addView(secondaryRow, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(7) })

        val actions = LinearLayout(this@V11MeasurementActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val weightLabel = text(if (language == "ar") "الوزن" else "Weight", 10f, V11_MUTED, true)
        actions.addView(weightLabel, LinearLayout.LayoutParams(-2, dp(42)))
        weightInput = numericInput().apply {
            hint = "kg"
            if (profile.hasExplicitWeight) setText(format(profile.weightPounds * KG_PER_POUND))
        }
        actions.addView(weightInput, LinearLayout.LayoutParams(dp(80), dp(42)).apply { marginStart = dp(7) })
        val save = text(if (language == "ar") "حفظ" else "Save", 12f, V11_BG, true).apply {
            gravity = Gravity.CENTER
            background = rounded(V11_IVORY, 15f)
            setOnClickListener { saveSelection() }
        }
        actions.addView(save, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(8) })
        addView(actions, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(9) })
    }

    private fun numericInput(): EditText = EditText(this).apply {
        textSize = 15f
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF777477.toInt())
        hint = "cm"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        imeOptions = EditorInfo.IME_ACTION_DONE
        setSingleLine(true)
        gravity = Gravity.CENTER
        setPadding(dp(10), 0, dp(10), 0)
        background = rounded(V11_PANEL_2, 14f, 0x50434347)
        setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) {
                saveSelection()
                true
            } else false
        }
    }

    private fun onRendererState(state: V11BodyState) {
        when (state) {
            V11BodyState.LOADING -> Unit
            V11BodyState.READY -> {
                rendererReady = true
                overlay.invalidate()
            }
            V11BodyState.ERROR -> Toast.makeText(
                this,
                if (language == "ar") "تعذر تشغيل المجسم. لم يتم حفظ أي قياس." else "3D renderer failed. No measurement was saved.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun openEditor(target: V11Target) {
        if (!rendererReady) return
        selected = target
        overlay.selected = target
        editorTitle.text = target.title(language)
        val sides = target.sideKeys()
        val fallback = target.valueCm(profile)
        if (sides != null) {
            primaryInput.hint = if (language == "ar") "يمين cm" else "Right cm"
            secondaryInput.hint = if (language == "ar") "يسار cm" else "Left cm"
            primaryInput.setText(profile.sideMeasurementsInches[sides.first]?.times(CM_PER_INCH)?.let(::format) ?: fallback?.let(::format).orEmpty())
            secondaryInput.setText(profile.sideMeasurementsInches[sides.second]?.times(CM_PER_INCH)?.let(::format) ?: fallback?.let(::format).orEmpty())
            secondaryRow.visibility = View.VISIBLE
        } else {
            primaryInput.hint = "cm"
            primaryInput.setText(fallback?.let(::format).orEmpty())
            secondaryInput.text?.clear()
            secondaryRow.visibility = View.GONE
        }
        if (profile.hasExplicitWeight) weightInput.setText(format(profile.weightPounds * KG_PER_POUND))
        editorDock.visibility = View.VISIBLE
        runtime.focusOn(target.focusY, target.focusDistance)
        overlay.invalidate()
    }

    private fun closeEditor() {
        selected = null
        overlay.selected = null
        editorDock.visibility = View.GONE
        primaryInput.clearFocus()
        secondaryInput.clearFocus()
        weightInput.clearFocus()
        hideKeyboard()
        runtime.resetFocus()
        overlay.invalidate()
    }

    private fun saveSelection() {
        val target = selected ?: return
        val sides = target.sideKeys()
        if (sides != null) {
            val right = primaryInput.text.toString().toFloatOrNull()?.takeIf { it in 1f..300f } ?: return
            val left = secondaryInput.text.toString().toFloatOrNull()?.takeIf { it in 1f..300f } ?: return
            val point = target.point ?: return
            profile = profile.copy(
                measurementsInches = profile.measurementsInches + (point to ((right + left) / 2f / CM_PER_INCH)),
                sideMeasurementsInches = profile.sideMeasurementsInches + mapOf(
                    sides.first to right / CM_PER_INCH,
                    sides.second to left / CM_PER_INCH,
                ),
            )
        } else {
            val cm = primaryInput.text.toString().toFloatOrNull()?.takeIf { it in 1f..300f } ?: return
            profile = if (target == V11Target.HEIGHT) {
                profile.copy(heightInches = cm / CM_PER_INCH, hasExplicitHeight = true)
            } else {
                val point = target.point ?: return
                profile.copy(measurementsInches = profile.measurementsInches + (point to cm / CM_PER_INCH))
            }
        }

        weightInput.text.toString().toFloatOrNull()?.takeIf { it in 20f..320f }?.let { kg ->
            profile = profile.copy(weightPounds = kg / KG_PER_POUND, hasExplicitWeight = true)
        }
        applyShape()
        refreshProgress()
        closeEditor()
    }

    private fun applyShape() {
        if (!::runtime.isInitialized) return
        val solved = BodyShapeSolver.solve(profile)
        runtime.updateBodyShape(solved.widthScale, solved.heightScale, solved.depthScale)
        runtime.updateProfile(profile)
    }

    private fun refreshProgress() {
        if (!::progressText.isInitialized) return
        val complete = V11Target.entries.count { it.valueCm(profile) != null }
        progressText.text = "$complete/${V11Target.entries.size}"
        overlay.invalidate()
    }

    private fun finishSuccess() {
        val oldSides = bodyProfileStore.profile.value.sideMeasurementsInches
        BodySideMeasurement.entries.forEach { key ->
            val current = profile.sideMeasurementsInches[key]
            when {
                current != null && current != oldSides[key] -> bodyProfileStore.setSideMeasurement(key, current)
                current == null && oldSides[key] != null -> bodyProfileStore.clearSideMeasurement(key)
            }
        }
        setResult(Activity.RESULT_OK, BodyMeasurementContract.resultIntent(profile))
        finish()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    // ---------------- anatomy ---------------------------------------------------------------

    private fun point(name: String): V11ScreenPoint? = projection?.get(name)?.takeIf { it.visible }

    private fun mix(a: V11ScreenPoint?, b: V11ScreenPoint?, t: Float): V11ScreenPoint? {
        if (a == null) return b
        if (b == null) return a
        return V11ScreenPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.visible || b.visible)
    }

    private fun frontAlpha(): Float {
        val yaw = projection?.yawRadians ?: 0.0
        val normalized = ((yaw + PI) % (PI * 2.0)) - PI
        return ((cos(normalized) - .18) / .68).coerceIn(0.0, 1.0).toFloat()
    }

    private fun frame(): V11Frame {
        val leftShoulder = mix(point("LeftShoulder"), point("LeftUpperArm"), .16f) ?: V11ScreenPoint(.37f, .25f)
        val rightShoulder = mix(point("RightShoulder"), point("RightUpperArm"), .16f) ?: V11ScreenPoint(.63f, .25f)
        val neck = point("AnatomyNeck") ?: V11ScreenPoint(.50f, .20f)
        val crown = point("AnatomyCrown") ?: V11ScreenPoint(.50f, .08f)
        val chest = point("AnatomyChest") ?: V11ScreenPoint(.50f, .32f)
        val underBust = point("AnatomyUnderbust") ?: V11ScreenPoint(.50f, .36f)
        val waist = point("AnatomyWaist") ?: V11ScreenPoint(.50f, .43f)
        val abdomen = point("AnatomyAbdomen") ?: V11ScreenPoint(.50f, .47f)
        val hip = point("AnatomyHip") ?: V11ScreenPoint(.50f, .53f)
        val bustL = point("AnatomyBustLeft") ?: V11ScreenPoint(.46f, chest.y)
        val bustR = point("AnatomyBustRight") ?: V11ScreenPoint(.54f, chest.y)
        val upperArm = mix(point("RightUpperArm"), point("RightForeArm"), .38f) ?: V11ScreenPoint(.72f, .34f)
        val wrist = mix(point("RightForeArm"), point("RightHand"), .82f) ?: V11ScreenPoint(.79f, .47f)
        val armMid = mix(rightShoulder, wrist, .52f) ?: upperArm
        val highShoulder = mix(neck, leftShoulder, .76f) ?: leftShoulder
        val feet = mix(point("LeftFoot"), point("RightFoot"), .50f) ?: V11ScreenPoint(.50f, .91f)
        val centerX = (leftShoulder.x + rightShoulder.x) * .5f
        val span = abs(rightShoulder.x - leftShoulder.x).coerceAtLeast(.13f)
        val torso = abs(hip.y - neck.y).coerceAtLeast(.28f)
        return V11Frame(leftShoulder, rightShoulder, neck, crown, chest, underBust, waist, abdomen, hip, bustL, bustR, upperArm, wrist, armMid, highShoulder, feet, centerX, span, torso)
    }

    private fun anchor(target: V11Target): V11ScreenPoint {
        val f = frame()
        fun right(level: V11ScreenPoint, fraction: Float) = V11ScreenPoint(f.centerX + f.span * fraction, level.y)
        fun left(level: V11ScreenPoint, fraction: Float) = V11ScreenPoint(f.centerX - f.span * fraction, level.y)
        val p = when (target) {
            V11Target.HEIGHT -> f.crown.copy(x = f.centerX - f.span * .60f)
            V11Target.NECK -> right(f.neck, .11f)
            V11Target.SHOULDERS -> f.rightShoulder
            V11Target.SHOULDER_LENGTH -> f.leftShoulder
            V11Target.CHEST -> right(f.chest, .34f)
            V11Target.UNDERBUST -> left(f.underBust, .31f)
            V11Target.BUST_HEIGHT -> f.bustRight
            V11Target.BUST_POINT_DISTANCE -> f.bustLeft
            V11Target.WAIST -> right(f.waist, .28f)
            V11Target.ABDOMEN -> left(f.abdomen, .31f)
            V11Target.HIPS -> right(f.hip, .37f)
            V11Target.DRESS_LENGTH -> f.highShoulder
            V11Target.ARM_LENGTH -> f.armMid
            V11Target.UPPER_ARM -> f.upperArm
            V11Target.WRIST -> f.wrist
        }
        return p.copy(x = p.x.coerceIn(.04f, .96f), y = p.y.coerceIn(.05f, .94f), visible = p.visible && frontAlpha() > .12f)
    }

    private fun guide(target: V11Target): V11Guide {
        val f = frame()
        fun oval(c: V11ScreenPoint, w: Float, h: Float) = V11Guide(V11GuideShape.OVAL, V11ScreenPoint(c.x - w / 2f, c.y - h / 2f), V11ScreenPoint(c.x + w / 2f, c.y + h / 2f))
        return when (target) {
            V11Target.HEIGHT -> V11Guide(V11GuideShape.LINE, V11ScreenPoint(f.centerX - f.span * .60f, f.crown.y), V11ScreenPoint(f.centerX - f.span * .60f, f.feet.y + .012f))
            V11Target.NECK -> oval(f.neck.copy(x = f.centerX), f.span * .22f, f.torso * .045f)
            V11Target.SHOULDERS -> V11Guide(V11GuideShape.LINE, f.leftShoulder, f.rightShoulder)
            V11Target.SHOULDER_LENGTH -> V11Guide(V11GuideShape.LINE, mix(f.neck, f.leftShoulder, .16f) ?: f.neck, f.leftShoulder)
            V11Target.CHEST -> oval(f.chest, f.span * .74f, f.torso * .065f)
            V11Target.UNDERBUST -> oval(f.underBust, f.span * .66f, f.torso * .056f)
            V11Target.BUST_HEIGHT -> V11Guide(V11GuideShape.LINE, mix(f.neck, f.rightShoulder, .36f) ?: f.neck, f.bustRight)
            V11Target.BUST_POINT_DISTANCE -> V11Guide(V11GuideShape.LINE, f.bustLeft, f.bustRight)
            V11Target.WAIST -> oval(f.waist, f.span * .55f, f.torso * .050f)
            V11Target.ABDOMEN -> oval(f.abdomen, f.span * .61f, f.torso * .054f)
            V11Target.HIPS -> oval(f.hip, f.span * .75f, f.torso * .066f)
            V11Target.DRESS_LENGTH -> V11Guide(V11GuideShape.LINE, f.highShoulder, V11ScreenPoint(f.highShoulder.x, f.feet.y + .008f))
            V11Target.ARM_LENGTH -> V11Guide(V11GuideShape.LINE, f.rightShoulder, f.wrist)
            V11Target.UPPER_ARM -> oval(f.upperArm, f.span * .15f, f.torso * .058f)
            V11Target.WRIST -> oval(f.wrist, f.span * .085f, f.torso * .040f)
        }
    }

    private inner class LandmarkOverlay : View(this@V11MeasurementActivity) {
        var selected: V11Target? = null
        private var downX = 0f
        private var downY = 0f
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = V11_RED; style = Paint.Style.FILL }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE8FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = dpF(1.4f) }
        private val guide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = V11_IVORY; style = Paint.Style.STROKE; strokeWidth = dpF(1.55f); strokeCap = Paint.Cap.ROUND }
        private val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE5222225.toInt(); style = Paint.Style.FILL }
        private val chipStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x554D4D52; style = Paint.Style.STROKE; strokeWidth = dpF(1f) }
        private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = spF(11f); typeface = android.graphics.Typeface.DEFAULT_BOLD }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!rendererReady) return
            selected?.let { if (frontAlpha() > .12f) drawGuide(canvas, guide(it)) }
            val alpha = (255 * frontAlpha()).roundToInt().coerceIn(30, 255)
            V11Target.entries.forEach { target ->
                val a = anchor(target)
                if (!a.visible) return@forEach
                val x = width * a.x
                val y = height * a.y
                val active = selected == target
                dot.alpha = alpha
                halo.alpha = alpha
                canvas.drawCircle(x, y, dpF(if (active) 5.2f else 3.8f), dot)
                canvas.drawCircle(x, y, dpF(if (active) 7.1f else 5.5f), halo)
                target.valueCm(profile)?.let { drawValue(canvas, x, y, a.x, it) }
            }
        }

        private fun drawValue(canvas: Canvas, x: Float, y: Float, nx: Float, cm: Float) {
            val label = "${format(cm)} cm"
            val textW = valuePaint.measureText(label)
            val w = textW + dpF(14f)
            val h = dpF(22f)
            val left = if (nx < .55f) x + dpF(10f) else x - dpF(10f) - w
            val rect = RectF(left, y - h / 2f, left + w, y + h / 2f)
            canvas.drawRoundRect(rect, dpF(8f), dpF(8f), chip)
            canvas.drawRoundRect(rect, dpF(8f), dpF(8f), chipStroke)
            valuePaint.textAlign = Paint.Align.CENTER
            val baseline = rect.centerY() - (valuePaint.ascent() + valuePaint.descent()) / 2f
            canvas.drawText(label, rect.centerX(), baseline, valuePaint)
        }

        private fun drawGuide(canvas: Canvas, g: V11Guide) {
            val sx = width * g.start.x
            val sy = height * g.start.y
            val ex = width * g.end.x
            val ey = height * g.end.y
            if (g.shape == V11GuideShape.OVAL) {
                canvas.drawOval(RectF(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey)), guide)
            } else {
                canvas.drawLine(sx, sy, ex, ey, guide)
                arrow(canvas, sx, sy, ex, ey)
                arrow(canvas, ex, ey, sx, sy)
            }
        }

        private fun arrow(canvas: Canvas, tipX: Float, tipY: Float, fromX: Float, fromY: Float) {
            val angle = atan2((tipY - fromY).toDouble(), (tipX - fromX).toDouble())
            val len = dpF(6f)
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo((tipX - len * cos(angle - .55)).toFloat(), (tipY - len * sin(angle - .55)).toFloat())
                moveTo(tipX, tipY)
                lineTo((tipX - len * cos(angle + .55)).toFloat(), (tipY - len * sin(angle + .55)).toFloat())
            }
            canvas.drawPath(path, guide)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!::runtime.isInitialized) return true
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                downX = event.x
                downY = event.y
            }
            runtime.onViewportTouch(event)
            if (event.actionMasked == MotionEvent.ACTION_UP && rendererReady) {
                val travel = hypot(event.x - downX, event.y - downY)
                if (travel <= dpF(15f)) {
                    nearest(event.x, event.y)?.let {
                        openEditor(it)
                        return true
                    }
                }
            }
            return true
        }

        private fun nearest(x: Float, y: Float): V11Target? {
            val radius = dpF(27f)
            return V11Target.entries.mapNotNull { target ->
                val a = anchor(target)
                if (!a.visible) null else target to hypot(width * a.x - x, height * a.y - y)
            }.filter { it.second <= radius }.minByOrNull { it.second }?.first
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        if (language == "ar") textDirection = View.TEXT_DIRECTION_RTL
    }

    private fun rounded(color: Int, radius: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dpF(radius)
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    private fun dpF(value: Float): Float = value * resources.displayMetrics.density
    private fun spF(value: Float): Float = value * resources.displayMetrics.scaledDensity
    private fun format(value: Float): String = if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}

private data class V11Frame(
    val leftShoulder: V11ScreenPoint,
    val rightShoulder: V11ScreenPoint,
    val neck: V11ScreenPoint,
    val crown: V11ScreenPoint,
    val chest: V11ScreenPoint,
    val underBust: V11ScreenPoint,
    val waist: V11ScreenPoint,
    val abdomen: V11ScreenPoint,
    val hip: V11ScreenPoint,
    val bustLeft: V11ScreenPoint,
    val bustRight: V11ScreenPoint,
    val upperArm: V11ScreenPoint,
    val wrist: V11ScreenPoint,
    val armMid: V11ScreenPoint,
    val highShoulder: V11ScreenPoint,
    val feet: V11ScreenPoint,
    val centerX: Float,
    val span: Float,
    val torso: Float,
)

private enum class V11GuideShape { LINE, OVAL }
private data class V11Guide(val shape: V11GuideShape, val start: V11ScreenPoint, val end: V11ScreenPoint)

private enum class V11Target(
    val point: BodyMeasurePoint?,
    val focusY: Float,
    val focusDistance: Float,
) {
    HEIGHT(null, .00f, 2.86f),
    NECK(BodyMeasurePoint.NECK, .60f, 2.18f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, .49f, 2.28f),
    SHOULDER_LENGTH(BodyMeasurePoint.SHOULDER_LENGTH, .50f, 2.18f),
    CHEST(BodyMeasurePoint.CHEST, .33f, 2.10f),
    UNDERBUST(BodyMeasurePoint.UNDERBUST, .25f, 2.08f),
    BUST_HEIGHT(BodyMeasurePoint.BUST_HEIGHT, .34f, 2.02f),
    BUST_POINT_DISTANCE(BodyMeasurePoint.BUST_POINT_DISTANCE, .34f, 2.02f),
    WAIST(BodyMeasurePoint.WAIST, .10f, 2.08f),
    ABDOMEN(BodyMeasurePoint.ABDOMEN, .00f, 2.08f),
    HIPS(BodyMeasurePoint.HIPS, -.12f, 2.12f),
    DRESS_LENGTH(BodyMeasurePoint.DRESS_LENGTH, .00f, 2.64f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, .22f, 2.02f),
    UPPER_ARM(BodyMeasurePoint.UPPER_ARM, .34f, 1.94f),
    WRIST(BodyMeasurePoint.WRIST, .05f, 1.90f),
    ;

    fun sideKeys(): Pair<BodySideMeasurement, BodySideMeasurement>? = when (this) {
        ARM_LENGTH -> BodySideMeasurement.RIGHT_ARM_LENGTH to BodySideMeasurement.LEFT_ARM_LENGTH
        UPPER_ARM -> BodySideMeasurement.RIGHT_UPPER_ARM to BodySideMeasurement.LEFT_UPPER_ARM
        WRIST -> BodySideMeasurement.RIGHT_WRIST to BodySideMeasurement.LEFT_WRIST
        else -> null
    }

    fun valueCm(profile: BodyProfile): Float? = when (this) {
        HEIGHT -> profile.heightInches.takeIf { profile.hasExplicitHeight }?.times(CM_PER_INCH)
        else -> point?.let { profile.measurementsInches[it]?.times(CM_PER_INCH) }
    }

    fun title(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "الطول الكامل"
        NECK -> "محيط الرقبة"
        SHOULDERS -> "عرض الكتفين"
        SHOULDER_LENGTH -> "طول الكتف"
        CHEST -> "محيط الصدر"
        UNDERBUST -> "محيط أسفل الصدر"
        BUST_HEIGHT -> "ارتفاع الصدر"
        BUST_POINT_DISTANCE -> "المسافة بين نقطتي الصدر"
        WAIST -> "محيط الخصر"
        ABDOMEN -> "محيط البطن"
        HIPS -> "محيط الأرداف"
        DRESS_LENGTH -> "طول الفستان"
        ARM_LENGTH -> "طول الذراع"
        UPPER_ARM -> "محيط العضد"
        WRIST -> "محيط المعصم"
    } else when (this) {
        HEIGHT -> "Full height"
        NECK -> "Neck circumference"
        SHOULDERS -> "Shoulder width"
        SHOULDER_LENGTH -> "Shoulder length"
        CHEST -> "Bust circumference"
        UNDERBUST -> "Underbust circumference"
        BUST_HEIGHT -> "Bust height"
        BUST_POINT_DISTANCE -> "Bust-point distance"
        WAIST -> "Waist circumference"
        ABDOMEN -> "Abdomen circumference"
        HIPS -> "Hip circumference"
        DRESS_LENGTH -> "Dress length"
        ARM_LENGTH -> "Arm length"
        UPPER_ARM -> "Upper-arm circumference"
        WRIST -> "Wrist circumference"
    }
}
