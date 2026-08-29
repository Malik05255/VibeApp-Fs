package com.almi.ai.ui.body

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.ui.theme.AlmiTheme

/** Dedicated process host for the Filament body-measurement session. */
class BodyMeasurementActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        val language = BodyMeasurementContract.language(intent)
        val initialProfile = BodyMeasurementContract.readProfile(intent)

        setContent {
            val profileState = remember { mutableStateOf(initialProfile) }
            val direction = if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AlmiTheme(themeMode = AppThemeMode.DARK) {
                    OfficialFilamentBodyScreen(
                        language = language,
                        profile = profileState.value,
                        onProfileChanged = { profileState.value = it.sanitizedAgainst(initialProfile) },
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
    }
}

private fun BodyProfile.sanitizedAgainst(fallback: BodyProfile): BodyProfile = copy(
    heightInches = heightInches.takeIf { it.isFinite() && it in 36f..96f } ?: fallback.heightInches,
    weightPounds = weightPounds.takeIf { it.isFinite() && it in 45f..700f } ?: fallback.weightPounds,
    measurementsInches = measurementsInches.filterValues { it.isFinite() && it in 1f..120f },
)
