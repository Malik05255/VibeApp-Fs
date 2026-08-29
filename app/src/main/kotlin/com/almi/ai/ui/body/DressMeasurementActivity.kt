package com.almi.ai.ui.body

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DRESS_CM_PER_INCH = 2.54f
private const val DRESS_KG_PER_POUND = 0.45359237f
private const val DRESS_HEADER = 0xFF102F59.toInt()
private const val DRESS_SURFACE = 0xFF163D70.toInt()
private const val DRESS_PANEL = 0xF21A416C.toInt()
private const val DRESS_PANEL_SOFT = 0xF01A3C65.toInt()
private const val DRESS_BLUE = 0xFF68B2FF.toInt()
private const val DRESS_RED = 0xFFFF3C48.toInt()
private const val DRESS_TEXT_SOFT = 0xFFD0E3FA.toInt()

/**
 * Front-facing dressmaker measurement surface.
 *
 * The interaction is intentionally locked to the calibrated front view after the intro spin. The
 * red markers therefore stay anatomically attached to the rendered body instead of drifting while
 * a separate 2D overlay remains static over a rotated 3D model.
 */
@AndroidEntryPoint
class DressMeasurementActivity : ComponentActivity() {
    @Inject lateinit var bodyProfileStore: BodyProfileStore

    private lateinit var runtime: PersistentFilamentRuntime
    private lateinit var countView: TextView
    private lateinit var progressView: DressProgressView
    private lateinit var editor: LinearLayout
    private lateinit var editorTitle: TextView
    private lateinit var editorInput: EditText
    private lateinit var editorSecondaryInput: EditText
    private lateinit var editorPrimaryLabel: TextView
    private lateinit var editorSecondaryLabel: TextView
    private lateinit var editorSecondaryRow: LinearLayout
    private lateinit var guideView: DressMeasurementGuideView
    private lateinit var annotationsView: DressAnnotationsView
    private lateinit var hotspotLayer: FrameLayout
    private lateinit var weightInput: EditText
    private lateinit var topBar: View
    private lateinit var weightDock: View

    private var selectedTarget: TailorTarget? = null
    private var profile = BodyProfile()
    private var language = "ar"
    private var introCompleted = false
    private val hotspotViews = linkedMapOf<TailorTarget, View>()

    private val layoutScale: Float by lazy {
        val width = resources.configuration.screenWidthDp
        val height = resources.configuration.screenHeightDp
        when {
            width < 350 || height < 650 -> 0.84f
            width < 380 || height < 720 -> 0.89f
            width < 420 || height < 800 -> 0.94f
            else -> 1f
        }
    }

    private val typeScale: Float by lazy {
        val width = resources.configuration.screenWidthDp
        when {
            width < 350 -> 0.88f
            width < 390 -> 0.93f
            width < 430 -> 0.97f
            else -> 1f
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        window.statusBarColor = DRESS_HEADER
        window.navigationBarColor = DRESS_HEADER
        window.isNavigationBarContrastEnforced = false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0

        language = BodyMeasurementContract.language(intent)
        profile = BodyMeasurementContract.readProfile(intent)

        val root = FrameLayout(this).apply { setBackgroundColor(DRESS_SURFACE) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        val surface = SurfaceView(this).apply {
            setZOrderOnTop(false)
            keepScreenOn = true
            background = null
        }
        root.addView(
            surface,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val topHeight = dp(118)
        val dockHeight = dp(78)

        topBar = buildTopBar().apply {
            visibility = View.INVISIBLE
            alpha = 0f
        }
        root.addView(
            topBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, topHeight).apply {
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
                topMargin = topHeight
                bottomMargin = dockHeight + dp(8)
            },
        )

        annotationsView = DressAnnotationsView()
        hotspotLayer.addView(
            annotationsView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        guideView = DressMeasurementGuideView().apply { visibility = View.GONE }
        hotspotLayer.addView(
            guideView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        TailorTarget.entries.forEach(::addHotspot)

        editor = buildMeasurementEditor().apply { visibility = View.GONE }
        hotspotLayer.addView(
            editor,
            FrameLayout.LayoutParams(dp(144), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                rightMargin = dp(8)
                topMargin = dp(170)
            },
        )

        weightDock = buildWeightDock().apply {
            visibility = View.INVISIBLE
            alpha = 0f
        }
        root.addView(
            weightDock,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dockHeight).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(10)
                rightMargin = dp(10)
                bottomMargin = dp(5)
            },
        )

        setContentView(root)
        WindowInsetsControllerCompat(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.requestApplyInsets(root)

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
        val bar = FrameLayout(this).apply {
            setPadding(dp(12), dp(6), dp(12), dp(5))
            background = solidBg(DRESS_HEADER, 0f)
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        titleBlock.addView(
            text("ALMI / FILAMENT", 12f, 0xFF8FC7FF.toInt(), true).apply { gravity = Gravity.CENTER },
        )
        titleBlock.addView(
            text(if (language == "ar") "قياسات جسمك" else "Your measurements", 24f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(1), 0, 0)
            },
        )
        bar.addView(
            titleBlock,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = dp(5)
            },
        )

        val done = text(if (language == "ar") "✓  تم" else "✓  Done", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(0xD81B416E.toInt(), 99f, 0x668CB5DF)
            setOnClickListener {
                persistSideMeasurements()
                setResult(Activity.RESULT_OK, BodyMeasurementContract.resultIntent(profile))
                finish()
            }
        }
        bar.addView(
            done,
            FrameLayout.LayoutParams(dp(69), dp(37)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(4)
            },
        )

        val progressRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        countView = text("0/${TailorTarget.entries.size}", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(0xE8244C7A.toInt(), 12f)
        }
        progressRow.addView(countView, LinearLayout.LayoutParams(dp(49), dp(29)))

        progressView = DressProgressView()
        progressRow.addView(
            progressView,
            LinearLayout.LayoutParams(dp(116), dp(13)).apply { marginStart = dp(9) },
        )

        bar.addView(
            progressRow,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(31)).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(1)
            },
        )
        return bar
    }

    private fun buildMeasurementEditor(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = roundedBg(DRESS_PANEL, 14f, 0x6689ACD0)
            elevation = dp(9).toFloat()

            editorTitle = text("", 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER }
            addView(editorTitle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)))

            val primaryRow = measurementRow().also { row ->
                editorPrimaryLabel = text("", 8.5f, DRESS_TEXT_SOFT, true).apply { gravity = Gravity.CENTER }
                row.addView(editorPrimaryLabel, LinearLayout.LayoutParams(dp(31), dp(38)))
                editorInput = measurementEditText()
                row.addView(editorInput, LinearLayout.LayoutParams(0, dp(38), 1f))
                row.addView(
                    text("cm", 9f, 0xFFE3F0FF.toInt(), false).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(dp(27), dp(38)),
                )
            }
            addView(
                primaryRow,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(5) },
            )

            editorSecondaryRow = measurementRow().apply { visibility = View.GONE }
            editorSecondaryLabel = text("", 8.5f, DRESS_TEXT_SOFT, true).apply { gravity = Gravity.CENTER }
            editorSecondaryRow.addView(editorSecondaryLabel, LinearLayout.LayoutParams(dp(31), dp(38)))
            editorSecondaryInput = measurementEditText()
            editorSecondaryRow.addView(editorSecondaryInput, LinearLayout.LayoutParams(0, dp(38), 1f))
            editorSecondaryRow.addView(
                text("cm", 9f, 0xFFE3F0FF.toInt(), false).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(dp(27), dp(38)),
            )
            addView(
                editorSecondaryRow,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(4) },
            )

            val actions = LinearLayout(this@DressMeasurementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val cancel = text("×", 19f, 0xFFE6F1FF.toInt(), false).apply {
                gravity = Gravity.CENTER
                background = roundedBg(0xCC15375E.toInt(), 10f, 0x557A9FC5)
                setOnClickListener { closeEditor() }
            }
            actions.addView(cancel, LinearLayout.LayoutParams(0, dp(34), 1f))
            val confirm = text("✓", 19f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = roundedBg(DRESS_BLUE, 10f)
                setOnClickListener { saveSelectedMeasurement() }
            }
            actions.addView(
                confirm,
                LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginStart = dp(5) },
            )
            addView(
                actions,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(5) },
            )
        }
    }

    private fun measurementRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundedBg(0xDD12345A.toInt(), 10f, 0x557A9FC5)
    }

    private fun measurementEditText(): EditText = EditText(this).apply {
        textSize = scaledText(16f)
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF8EA8C8.toInt())
        hint = "0"
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        imeOptions = EditorInfo.IME_ACTION_DONE
        gravity = Gravity.CENTER
        setSingleLine(true)
        setPadding(dp(2), 0, dp(2), 0)
        background = null
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveSelectedMeasurement()
                true
            } else false
        }
    }

    private fun buildWeightDock(): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = roundedBg(DRESS_PANEL_SOFT, 18f, 0x6684A8CE)
            elevation = dp(6).toFloat()
        }

        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        label.addView(text(if (language == "ar") "الوزن" else "Weight", 15f, Color.WHITE, true))
        label.addView(
            text(
                if (language == "ar") "يتفاعل الجسم مباشرة" else "Body reacts immediately",
                8.5f,
                DRESS_TEXT_SOFT,
                false,
            ),
        )
        dock.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val inputShell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(0xDD12345A.toInt(), 11f, 0x667FA4C9)
        }
        weightInput = EditText(this).apply {
            textSize = scaledText(19f)
            setTextColor(Color.WHITE)
            hint = "80"
            setHintTextColor(0xFFA4BCD8.toInt())
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            gravity = Gravity.CENTER
            setSingleLine(true)
            setPadding(dp(3), 0, dp(1), 0)
            background = null
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitWeight()
                    true
                } else false
            }
        }
        inputShell.addView(weightInput, LinearLayout.LayoutParams(dp(54), dp(42)))
        inputShell.addView(
            text("kg", 9.5f, 0xFFE4F0FF.toInt(), true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(28), dp(42)),
        )
        dock.addView(inputShell, LinearLayout.LayoutParams(dp(82), dp(42)).apply { marginStart = dp(6) })

        val cancel = text("×", 18f, 0xFFE7F2FF.toInt(), false).apply {
            gravity = Gravity.CENTER
            background = roundedBg(0xCC15375E.toInt(), 11f, 0x557A9FC5)
            setOnClickListener { cancelWeightEdit() }
        }
        dock.addView(cancel, LinearLayout.LayoutParams(dp(36), dp(42)).apply { marginStart = dp(5) })

        val confirm = text("✓", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = roundedBg(DRESS_BLUE, 11f)
            setOnClickListener { commitWeight() }
        }
        dock.addView(confirm, LinearLayout.LayoutParams(dp(39), dp(42)).apply { marginStart = dp(5) })
        return dock
    }

    private fun addHotspot(target: TailorTarget) {
        val hit = View(this).apply {
            visibility = View.INVISIBLE
            background = null
            setOnClickListener { openEditor(target) }
        }
        hotspotLayer.addView(hit, FrameLayout.LayoutParams(dp(30), dp(30)))
        hotspotViews[target] = hit
        hotspotLayer.post { positionHotspot(target, hit) }
    }

    private fun positionHotspot(target: TailorTarget, holder: View) {
        val width = hotspotLayer.width
        val height = hotspotLayer.height
        if (width <= 0 || height <= 0) {
            hotspotLayer.post { positionHotspot(target, holder) }
            return
        }
        holder.x = width * target.x - dp(15).toFloat()
        holder.y = height * target.y - dp(15).toFloat()
    }

    private fun openEditor(target: TailorTarget) {
        if (!introCompleted) return
        selectedTarget = target
        editorTitle.text = target.title(language)

        val sideKeys = target.sideKeys()
        if (sideKeys != null) {
            val (rightKey, leftKey) = sideKeys
            editorPrimaryLabel.visibility = View.VISIBLE
            editorSecondaryRow.visibility = View.VISIBLE
            editorPrimaryLabel.text = if (language == "ar") "يمين" else "R"
            editorSecondaryLabel.text = if (language == "ar") "يسار" else "L"
            val fallback = target.valueCm(profile)
            editorInput.setText(
                profile.sideMeasurementsInches[rightKey]?.times(DRESS_CM_PER_INCH)?.let(::formatNumber)
                    ?: fallback?.let(::formatNumber).orEmpty(),
            )
            editorSecondaryInput.setText(
                profile.sideMeasurementsInches[leftKey]?.times(DRESS_CM_PER_INCH)?.let(::formatNumber)
                    ?: fallback?.let(::formatNumber).orEmpty(),
            )
        } else {
            editorPrimaryLabel.visibility = View.GONE
            editorSecondaryRow.visibility = View.GONE
            editorInput.setText(target.valueCm(profile)?.let(::formatNumber).orEmpty())
        }

        editor.visibility = View.VISIBLE
        guideView.setTarget(target)
        guideView.visibility = View.VISIBLE
        annotationsView.selectedTarget = target
        positionEditor(target)
    }

    private fun positionEditor(target: TailorTarget) {
        hotspotLayer.post {
            val desired = (hotspotLayer.height * target.y - dp(48)).toInt()
            val extraHeight = if (target.sideKeys() != null) dp(44) else 0
            val maxTop = (hotspotLayer.height - dp(118) - extraHeight).coerceAtLeast(dp(16))
            val lp = editor.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.TOP or if (target.x >= .54f) Gravity.START else Gravity.END
            lp.leftMargin = dp(8)
            lp.rightMargin = dp(8)
            lp.topMargin = desired.coerceIn(dp(14), maxTop)
            editor.layoutParams = lp
        }
    }

    private fun closeEditor() {
        selectedTarget = null
        editor.visibility = View.GONE
        guideView.visibility = View.GONE
        annotationsView.selectedTarget = null
        editorInput.clearFocus()
        editorSecondaryInput.clearFocus()
        hideKeyboard()
    }

    private fun saveSelectedMeasurement() {
        val target = selectedTarget ?: return
        val sideKeys = target.sideKeys()
        if (sideKeys != null) {
            val rightCm = editorInput.text.toString().toFloatOrNull()?.takeIf { it in 1f..300f } ?: return
            val leftCm = editorSecondaryInput.text.toString().toFloatOrNull()?.takeIf { it in 1f..300f } ?: return
            val (rightKey, leftKey) = sideKeys
            val point = target.point ?: return
            profile = profile.copy(
                measurementsInches = profile.measurementsInches + (point to ((rightCm + leftCm) / 2f / DRESS_CM_PER_INCH)),
                sideMeasurementsInches = profile.sideMeasurementsInches + mapOf(
                    rightKey to rightCm / DRESS_CM_PER_INCH,
                    leftKey to leftCm / DRESS_CM_PER_INCH,
                ),
            )
        } else {
            val centimeters = editorInput.text.toString().toFloatOrNull()?.takeIf { it in 1f..300f } ?: return
            profile = if (target == TailorTarget.HEIGHT) {
                profile.copy(heightInches = centimeters / DRESS_CM_PER_INCH, hasExplicitHeight = true)
            } else {
                val point = target.point ?: return
                profile.copy(measurementsInches = profile.measurementsInches + (point to centimeters / DRESS_CM_PER_INCH))
            }
        }
        applyShape()
        refreshUi()
        closeEditor()
    }

    private fun cancelWeightEdit() {
        if (profile.hasExplicitWeight) {
            weightInput.setText(formatNumber(profile.weightPounds * DRESS_KG_PER_POUND))
        } else {
            weightInput.text?.clear()
        }
        weightInput.clearFocus()
        hideKeyboard()
    }

    private fun commitWeight() {
        val kg = weightInput.text.toString().toFloatOrNull()?.takeIf { it in 20f..320f } ?: return
        profile = profile.copy(weightPounds = kg / DRESS_KG_PER_POUND, hasExplicitWeight = true)
        weightInput.clearFocus()
        hideKeyboard()
        applyShape()
        refreshUi()
    }

    private fun persistSideMeasurements() {
        val before = bodyProfileStore.profile.value.sideMeasurementsInches
        BodySideMeasurement.entries.forEach { point ->
            val value = profile.sideMeasurementsInches[point]
            when {
                value != null && value != before[point] -> bodyProfileStore.setSideMeasurement(point, value)
                value == null && before[point] != null -> bodyProfileStore.clearSideMeasurement(point)
            }
        }
    }

    private fun refreshUi() {
        val total = TailorTarget.entries.size
        val completed = TailorTarget.entries.count { it.valueCm(profile) != null }
        countView.text = "$completed/$total"
        progressView.progress = completed.toFloat() / total.toFloat()
        if (profile.hasExplicitWeight && !weightInput.hasFocus()) {
            weightInput.setText(formatNumber(profile.weightPounds * DRESS_KG_PER_POUND))
        }
        annotationsView.profile = profile
        annotationsView.invalidate()
    }

    private fun renderState(state: BodyRendererState) {
        when (state) {
            BodyRendererState.LOADING -> Unit
            BodyRendererState.READY -> if (!introCompleted) {
                runtime.playIntroSpin(2_100L) { revealInteractiveUi() }
            }
            BodyRendererState.ERROR -> Toast.makeText(
                this,
                if (language == "ar") "تعذر عرض المجسم ثلاثي الأبعاد" else "Unable to render the 3D body",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun revealInteractiveUi() {
        if (introCompleted || isFinishing) return
        introCompleted = true
        topBar.visibility = View.VISIBLE
        weightDock.visibility = View.VISIBLE
        topBar.animate().alpha(1f).setDuration(220L).start()
        weightDock.animate().alpha(1f).setDuration(220L).start()
        annotationsView.bodyReady = true
        annotationsView.revealedCount = 0
        hotspotViews.values.forEach { it.visibility = View.INVISIBLE }

        TailorTarget.entries.forEachIndexed { index, target ->
            hotspotLayer.postDelayed({
                if (!isFinishing) {
                    annotationsView.revealedCount = index + 1
                    hotspotViews[target]?.visibility = View.VISIBLE
                }
            }, 150L + index * 85L)
        }
    }

    private fun applyShape() {
        if (!::runtime.isInitialized) return
        val shape = BodyShapeSolver.solve(profile)
        runtime.updateBodyShape(shape.widthScale, shape.heightScale, shape.depthScale)
        runtime.updateProfile(profile)
        hotspotLayer.post {
            hotspotViews.forEach(::positionHotspot)
            annotationsView.invalidate()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = scaledText(size)
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density * layoutScale).roundToInt().coerceAtLeast(1)

    private fun scaledText(value: Float): Float = value * typeScale

    private fun formatNumber(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)

    private inner class DressProgressView : View(this@DressMeasurementActivity) {
        var progress: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF244C76.toInt() }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DRESS_BLUE }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = height / 2f
            canvas.drawRoundRect(0f, height * .29f, width.toFloat(), height * .71f, radius, radius, track)
            val end = width * progress
            if (end > 0f) canvas.drawRoundRect(0f, height * .29f, end, height * .71f, radius, radius, fill)
        }
    }

    private inner class DressAnnotationsView : View(this@DressMeasurementActivity) {
        var profile: BodyProfile = this@DressMeasurementActivity.profile
        var bodyReady: Boolean = false
        var revealedCount: Int = 0
            set(value) {
                field = value.coerceIn(0, TailorTarget.entries.size)
                invalidate()
            }
        var selectedTarget: TailorTarget? = null
            set(value) {
                field = value
                invalidate()
            }

        private var pulse = 0f
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val dotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFD2D6.toInt()
            strokeWidth = dp(1).toFloat()
            style = Paint.Style.STROKE
        }
        private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F8FF.toInt()
            textSize = dp(8).toFloat()
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_100L
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

            TailorTarget.entries.take(revealedCount).forEach { target ->
                val cx = width * target.x
                val cy = height * target.y
                val selected = selectedTarget == target
                glow.color = if (selected) 0x78FF3C48 else 0x32FF3C48
                val glowRadius = dp(if (selected) 9 else 5).toFloat() + pulse * dp(if (selected) 2 else 1)
                canvas.drawCircle(cx, cy, glowRadius, glow)
                dot.color = DRESS_RED
                canvas.drawCircle(cx, cy, dp(if (selected) 4 else 3).toFloat(), dot)
                canvas.drawCircle(cx, cy, dp(if (selected) 5 else 4).toFloat(), dotStroke)

                val measured = target.valueCm(profile)
                if (measured != null) {
                    val number = formatNumber(measured)
                    val drawRight = target.x <= .53f
                    value.textAlign = if (drawRight) Paint.Align.LEFT else Paint.Align.RIGHT
                    val tx = cx + if (drawRight) dp(8) else -dp(8)
                    canvas.drawText(number, tx.toFloat(), cy + dp(3), value)
                }
            }
        }

        override fun onDetachedFromWindow() {
            animator.cancel()
            super.onDetachedFromWindow()
        }
    }

    private inner class DressMeasurementGuideView : View(this@DressMeasurementActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DRESS_BLUE
            strokeWidth = dp(2).toFloat()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val travelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F8FF.toInt()
            style = Paint.Style.FILL
        }
        private var target: TailorTarget? = null
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

        fun setTarget(value: TailorTarget) {
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

            if (item.guideShape == DressGuideShape.OVAL) {
                val rect = RectF(minOf(sx, ex), minOf(sy, ey), maxOf(sx, ex), maxOf(sy, ey))
                canvas.drawOval(rect, paint)
                val angle = phase * Math.PI * 2.0
                val px = rect.centerX() + cos(angle).toFloat() * rect.width() / 2f
                val py = rect.centerY() + sin(angle).toFloat() * rect.height() / 2f
                canvas.drawCircle(px, py, dp(3).toFloat(), travelPaint)
            } else {
                val px = sx + (ex - sx) * phase
                val py = sy + (ey - sy) * phase
                canvas.drawLine(sx, sy, ex, ey, paint)
                canvas.drawCircle(px, py, dp(3).toFloat(), travelPaint)
                drawArrowHead(canvas, sx, sy, ex, ey)
                drawArrowHead(canvas, ex, ey, sx, sy)
            }
        }

        private fun drawArrowHead(canvas: Canvas, tipX: Float, tipY: Float, fromX: Float, fromY: Float) {
            val angle = atan2((tipY - fromY).toDouble(), (tipX - fromX).toDouble())
            val len = dp(8).toFloat()
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

private enum class DressGuideShape { LINE, OVAL }

/**
 * Only core dressmaker measurements are displayed as hotspots. Hand, thigh, inseam, calf and foot
 * are intentionally absent from this front screen because they are not primary inputs for a dress.
 */
private enum class TailorTarget(
    val point: BodyMeasurePoint?,
    val x: Float,
    val y: Float,
    val guideShape: DressGuideShape,
    val guideStartX: Float,
    val guideStartY: Float,
    val guideEndX: Float,
    val guideEndY: Float,
) {
    HEIGHT(null, .50f, .050f, DressGuideShape.LINE, .285f, .050f, .285f, .905f),
    NECK(BodyMeasurePoint.NECK, .50f, .170f, DressGuideShape.OVAL, .465f, .163f, .535f, .184f),
    SHOULDERS(BodyMeasurePoint.SHOULDERS, .655f, .215f, DressGuideShape.LINE, .345f, .215f, .655f, .215f),
    CHEST(BodyMeasurePoint.CHEST, .50f, .285f, DressGuideShape.OVAL, .375f, .273f, .625f, .302f),
    UNDERBUST(BodyMeasurePoint.UNDERBUST, .50f, .326f, DressGuideShape.OVAL, .392f, .316f, .608f, .340f),
    WAIST(BodyMeasurePoint.WAIST, .50f, .405f, DressGuideShape.OVAL, .405f, .395f, .595f, .419f),
    ABDOMEN(BodyMeasurePoint.ABDOMEN, .50f, .448f, DressGuideShape.OVAL, .395f, .438f, .605f, .463f),
    HIPS(BodyMeasurePoint.HIPS, .50f, .495f, DressGuideShape.OVAL, .370f, .483f, .630f, .510f),
    DRESS_LENGTH(BodyMeasurePoint.DRESS_LENGTH, .405f, .218f, DressGuideShape.LINE, .405f, .205f, .405f, .885f),
    ARM_LENGTH(BodyMeasurePoint.ARM_LENGTH, .705f, .345f, DressGuideShape.LINE, .650f, .220f, .795f, .475f),
    UPPER_ARM(BodyMeasurePoint.UPPER_ARM, .690f, .286f, DressGuideShape.OVAL, .655f, .270f, .722f, .307f),
    WRIST(BodyMeasurePoint.WRIST, .790f, .468f, DressGuideShape.OVAL, .765f, .453f, .812f, .482f),
    ;

    fun title(language: String): String = if (language == "ar") when (this) {
        HEIGHT -> "الطول الكامل"
        NECK -> "محيط الرقبة"
        SHOULDERS -> "عرض الكتفين"
        CHEST -> "محيط الصدر"
        UNDERBUST -> "محيط أسفل الصدر"
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
        CHEST -> "Bust circumference"
        UNDERBUST -> "Underbust circumference"
        WAIST -> "Waist circumference"
        ABDOMEN -> "Abdomen circumference"
        HIPS -> "Hip circumference"
        DRESS_LENGTH -> "Dress length"
        ARM_LENGTH -> "Arm length"
        UPPER_ARM -> "Upper-arm circumference"
        WRIST -> "Wrist circumference"
    }

    fun sideKeys(): Pair<BodySideMeasurement, BodySideMeasurement>? = when (this) {
        ARM_LENGTH -> BodySideMeasurement.RIGHT_ARM_LENGTH to BodySideMeasurement.LEFT_ARM_LENGTH
        UPPER_ARM -> BodySideMeasurement.RIGHT_UPPER_ARM to BodySideMeasurement.LEFT_UPPER_ARM
        WRIST -> BodySideMeasurement.RIGHT_WRIST to BodySideMeasurement.LEFT_WRIST
        else -> null
    }

    fun valueCm(profile: BodyProfile): Float? = when (this) {
        HEIGHT -> profile.heightInches.takeIf { profile.hasExplicitHeight }?.times(DRESS_CM_PER_INCH)
        else -> point?.let { profile.measurementsInches[it]?.times(DRESS_CM_PER_INCH) }
    }
}
