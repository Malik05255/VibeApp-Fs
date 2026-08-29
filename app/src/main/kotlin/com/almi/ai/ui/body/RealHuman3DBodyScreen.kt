package com.almi.ai.ui.body

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile

/**
 * Main-process gateway to the isolated Filament measurement Activity.
 *
 * Filament never exists in ALMI's navigation composition anymore. That prevents an Engine/Surface
 * from being created or destroyed during Crossfade/navigation and protects the main app from a
 * vendor-native GPU crash. The actual 3D renderer remains Filament in BodyMeasurementActivity.
 */
@Composable
fun RealHuman3DBodyScreen(
    language: String,
    profile: BodyProfile,
    onHeightChanged: (Float) -> Unit,
    onWeightChanged: (Float) -> Unit,
    onMeasurementChanged: (BodyMeasurePoint, Float) -> Unit,
    onMeasurementCleared: (BodyMeasurePoint) -> Unit,
    onSnapshotReady: (String) -> Unit = {},
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var sessionActive by rememberSaveable { mutableStateOf(false) }
    var attemptedOnce by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun applyResult(updated: BodyProfile) {
        if (updated.hasExplicitHeight) onHeightChanged(updated.heightInches)
        if (updated.hasExplicitWeight) onWeightChanged(updated.weightPounds)

        BodyMeasurePoint.entries.forEach { point ->
            val before = profile.measurementsInches[point]
            val after = updated.measurementsInches[point]
            when {
                after != null && after != before -> onMeasurementChanged(point, after)
                after == null && before != null -> onMeasurementCleared(point)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        sessionActive = false
        if (result.resultCode == Activity.RESULT_OK) {
            val updated = BodyMeasurementContract.readProfile(result.data)
            applyResult(updated)
            onComplete()
        } else {
            status = if (language == "ar") {
                "تم إغلاق جلسة القياس بأمان. يمكنك فتحها مرة أخرى."
            } else {
                "The measurement session closed safely. You can open it again."
            }
        }
    }

    fun launchSession() {
        if (sessionActive) return
        attemptedOnce = true
        sessionActive = true
        status = null
        launcher.launch(BodyMeasurementContract.createIntent(context, language, profile))
    }

    LaunchedEffect(Unit) {
        if (!attemptedOnce) launchSession()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04101E))
            .statusBarsPadding()
            .padding(20.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "ALMI / FILAMENT",
                color = Color(0xFF86BCFF),
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (language == "ar") "جلسة القياسات ثلاثية الأبعاد" else "3D measurement session",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                status ?: if (sessionActive) {
                    if (language == "ar") "يتم فتح محرك Filament المعزول…" else "Opening the isolated Filament renderer…"
                } else {
                    if (language == "ar") "Filament يعمل في عملية مستقلة لحماية التطبيق من كراشات GPU." else "Filament runs in an isolated process to protect the app from GPU-native crashes."
                },
                color = Color(0xFF91A8C5),
                textAlign = TextAlign.Center,
            )
            if (!sessionActive) {
                Button(onClick = ::launchSession, modifier = Modifier.fillMaxWidth()) {
                    Text(if (language == "ar") "فتح القياسات 3D" else "Open 3D measurements")
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0B1A2C),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        ) {
            Text(
                if (language == "ar") {
                    "المحرك: Filament • التحميل: Managed • العملية: :body3d"
                } else {
                    "Engine: Filament • Loading: managed • Process: :body3d"
                },
                modifier = Modifier.padding(12.dp),
                color = Color(0xFF91A8C5),
                textAlign = TextAlign.Center,
            )
        }
    }
}
