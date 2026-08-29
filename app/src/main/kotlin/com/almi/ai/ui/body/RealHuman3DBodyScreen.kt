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
 * Main-process gateway for the Filament measurement Activity.
 *
 * A high-fidelity Filament session is attempted first. If Android kills the renderer process during
 * Engine/gltfio work, ALMI automatically reopens the measurement Activity in the lighter Filament
 * compatibility path rather than leaving the user on a dead screen.
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
    var launchedCompatibility by rememberSaveable { mutableStateOf(false) }
    var retryCompatibility by rememberSaveable { mutableStateOf(false) }
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
        } else if (!launchedCompatibility) {
            retryCompatibility = true
            status = if (language == "ar") {
                "يتم التحويل تلقائيًا إلى وضع Filament المتوافق…"
            } else {
                "Recovering with Filament compatibility mode…"
            }
        } else {
            val stage = PersistentFilamentRuntime.lastStage(context)
            status = if (language == "ar") {
                "تعذر تشغيل Filament على هذا الجهاز. آخر مرحلة: $stage"
            } else {
                "Filament could not start on this device. Last stage: $stage"
            }
        }
    }

    fun launchSession(compatibility: Boolean) {
        if (sessionActive) return
        attemptedOnce = true
        launchedCompatibility = compatibility
        sessionActive = true
        status = null
        launcher.launch(
            BodyMeasurementContract.createIntent(
                context = context,
                language = language,
                profile = profile,
                compatibilityMode = compatibility,
            )
        )
    }

    LaunchedEffect(Unit) {
        if (!attemptedOnce) {
            val previousStage = PersistentFilamentRuntime.lastStage(context)
            val previousNativeFailure = previousStage in setOf(
                "ENGINE_CREATE",
                "MODELVIEWER_CREATE",
                "EMPTY_RENDERER_READY",
                "MODEL_READ",
                "MODEL_LOAD",
                "MODEL_STREAMING",
            )
            launchSession(previousNativeFailure)
        }
    }

    LaunchedEffect(retryCompatibility) {
        if (retryCompatibility && !sessionActive) {
            retryCompatibility = false
            launchSession(true)
        }
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
            Text("ALMI / FILAMENT", color = Color(0xFF86BCFF), fontWeight = FontWeight.Bold)
            Text(
                if (language == "ar") "جلسة القياسات ثلاثية الأبعاد" else "3D measurement session",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                status ?: if (sessionActive) {
                    if (language == "ar") "يتم فتح Filament…" else "Opening Filament…"
                } else {
                    if (language == "ar") "Filament جاهز للمحاولة من جديد." else "Filament is ready to retry."
                },
                color = Color(0xFF91A8C5),
                textAlign = TextAlign.Center,
            )
            if (!sessionActive) {
                Button(onClick = { launchSession(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (language == "ar") "فتح Filament بالوضع المتوافق" else "Open Filament compatibility mode")
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
                    "Filament • OpenGL • استعادة تلقائية"
                } else {
                    "Filament • OpenGL • automatic recovery"
                },
                modifier = Modifier.padding(12.dp),
                color = Color(0xFF91A8C5),
                textAlign = TextAlign.Center,
            )
        }
    }
}
