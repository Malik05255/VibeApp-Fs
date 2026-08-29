package com.almi.ai.ui.body

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.ui.theme.AlmiTheme

/**
 * Dedicated full-screen host for Filament measurements.
 *
 * Critical stability rule: SurfaceView is a normal Android child created once in onCreate and kept
 * attached for the Activity's whole lifetime. Compose only draws transparent UI above it.
 */
class BodyMeasurementActivity : ComponentActivity() {
    private var runtime: PersistentFilamentRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        val language = BodyMeasurementContract.language(intent)
        val initialProfile = BodyMeasurementContract.readProfile(intent)
        val profileState = mutableStateOf(initialProfile)
        val rendererState = mutableStateOf(BodyRendererState.LOADING)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(4, 16, 30))
        }
        val surfaceView = SurfaceView(this).apply {
            setZOrderOnTop(false)
        }
        val overlay = ComposeView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        setContentView(root)

        val filamentRuntime = PersistentFilamentRuntime(
            context = this,
            surfaceView = surfaceView,
            onStateChanged = { rendererState.value = it },
        )
        runtime = filamentRuntime

        // Feed gestures to ModelViewer even though the transparent Compose overlay is above it.
        overlay.setOnTouchListener { _, event ->
            filamentRuntime.onOverlayTouch(event)
            false
        }

        overlay.setContent {
            val direction = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AlmiTheme(themeMode = AppThemeMode.DARK) {
                    BodyMeasurementOverlay(
                        language = language,
                        profile = profileState.value,
                        rendererState = rendererState.value,
                        onProfileChanged = { updated ->
                            val safe = updated.sanitizedAgainst(initialProfile)
                            profileState.value = safe
                            val shape = BodyShapeSolver.solve(safe)
                            filamentRuntime.updateBodyShape(
                                width = shape.widthScale,
                                height = shape.heightScale,
                                depth = shape.depthScale,
                            )
                        },
                        onDone = {
                            setResult(
                                Activity.RESULT_OK,
                                BodyMeasurementContract.resultIntent(profileState.value),
                            )
                            finish()
                        },
                    )
                }
            }
        }

        // Wait until the SurfaceView has been attached and measured before creating ModelViewer.
        surfaceView.post {
            if (!isFinishing && !isDestroyed) {
                filamentRuntime.initialize()
                val shape = BodyShapeSolver.solve(profileState.value)
                filamentRuntime.updateBodyShape(
                    width = shape.widthScale,
                    height = shape.heightScale,
                    depth = shape.depthScale,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        runtime?.start()
    }

    override fun onPause() {
        runtime?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        runtime?.stop()
        runtime = null
        super.onDestroy()
    }
}

private fun BodyProfile.sanitizedAgainst(fallback: BodyProfile): BodyProfile = copy(
    heightInches = heightInches.takeIf { it.isFinite() && it in 36f..96f } ?: fallback.heightInches,
    weightPounds = weightPounds.takeIf { it.isFinite() && it in 45f..700f } ?: fallback.weightPounds,
    measurementsInches = measurementsInches.filterValues { it.isFinite() && it in 1f..120f },
)
