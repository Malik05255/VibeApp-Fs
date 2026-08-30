package com.almi.ai.ui.v12

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.settings.SettingsViewModel
import com.almi.ai.update.AlmiUpdateManagementDialog
import com.almi.ai.update.AlmiUpdateManager
import kotlinx.coroutines.launch

private val MatrixInk = Color(0xFF123657)
private val MatrixBlue = Color(0xFF39B8F4)
private val MatrixCyan = Color(0xFF59E4F1)
private val MatrixPink = Color(0xFFFF7EA9)
private val MatrixMint = Color(0xFF54D9C2)
private val MatrixViolet = Color(0xFF9C8BFF)
private val MatrixGlass = Color(0xF0FFFFFF)

@Composable
internal fun V12FutureControlScreen(
    viewModel: SettingsViewModel,
    updateManager: AlmiUpdateManager,
    language: String,
    bodyReady: Boolean,
    avatarReady: Boolean,
    onBack: () -> Unit,
    onBody: () -> Unit,
    onAvatar: () -> Unit,
    onAi: () -> Unit,
) {
    val theme by viewModel.themeMode.collectAsState()
    val aiMode by viewModel.aiMode.collectAsState()
    val google by viewModel.googleAiStudioSettings.collectAsState()
    val scope = rememberCoroutineScope()
    var showUpdateManagement by remember { mutableStateOf(false) }
    val sweep by rememberInfiniteTransition(label = "control-matrix-sweep")
        .animateFloat(
            initialValue = -.08f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Restart),
            label = "control-matrix-sweep-value",
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE9F8FF),
                        Color(0xFFF8FCFF),
                        Color(0xFFFFF8FC),
                        Color(0xFFF1FAFF),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        MatrixField(sweep)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("ALMI // SYSTEM MATRIX", color = MatrixBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.25.sp)
                Text(if (language == "ar") "نواة التحكم" else "CONTROL NUCLEUS", color = MatrixInk, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text(
                    if (language == "ar") "كل أنظمة ALMI في طبقة واحدة" else "EVERY ALMI SYSTEM IN ONE LAYER",
                    color = MatrixInk.copy(alpha = .46f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .45.sp,
                )
            }
            Surface(
                modifier = Modifier.width(74.dp).height(42.dp).clickable(onClick = onBack),
                shape = RoundedCornerShape(15.dp),
                color = MatrixGlass,
                border = BorderStroke(1.dp, MatrixBlue.copy(alpha = .28f)),
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (language == "ar") "إغلاق" else "CLOSE", color = MatrixInk, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MatrixGlass,
                border = BorderStroke(1.dp, Color.White),
                shadowElevation = 14.dp,
            ) {
                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LIVE SYSTEM CHANNELS", color = MatrixInk.copy(alpha = .44f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    MatrixChannel(
                        code = "01 / BODY",
                        title = if (language == "ar") "بصمة الجسم" else "BODY SIGNATURE",
                        status = if (bodyReady) "CALIBRATED / LIVE" else "CALIBRATION REQUIRED",
                        accent = MatrixMint,
                        tilt = -0.45f,
                        onClick = onBody,
                    )
                    MatrixChannel(
                        code = "02 / IDENTITY",
                        title = if (language == "ar") "النسخة الرقمية" else "DIGITAL TWIN",
                        status = if (avatarReady) "IDENTITY LINKED" else "IDENTITY NOT LINKED",
                        accent = MatrixPink,
                        tilt = 0.35f,
                        onClick = onAvatar,
                    )
                    MatrixChannel(
                        code = "03 / INTELLIGENCE",
                        title = if (language == "ar") "العقل النشط" else "ACTIVE MIND",
                        status = if (google.active) {
                            "GOOGLE / ACTIVE"
                        } else {
                            when (aiMode) {
                                AiMode.OPENROUTER -> "OPENROUTER / ACTIVE"
                                AiMode.CUSTOM -> "CUSTOM / ACTIVE"
                                AiMode.FREE_AUTO -> "FREE AUTO / ACTIVE"
                            }
                        },
                        accent = MatrixViolet,
                        tilt = -0.28f,
                        onClick = onAi,
                    )
                    MatrixChannel(
                        code = "04 / UPDATE",
                        title = if (language == "ar") "إدارة التحديث" else "UPDATE MANAGEMENT",
                        status = if (language == "ar") "الأحدث فقط / تحديث فرق" else "LATEST ONLY / DELTA",
                        accent = MatrixBlue,
                        tilt = 0.22f,
                        onClick = { showUpdateManagement = true },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MatrixGlass,
                border = BorderStroke(1.dp, MatrixBlue.copy(alpha = .22f)),
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("INTERFACE PRISM", color = MatrixBlue, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                            Text(if (language == "ar") "لغة النظام" else "SYSTEM LANGUAGE", color = MatrixInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Text(if (language == "ar") "لحظي" else "LIVE", color = MatrixMint, fontSize = 8.sp, fontWeight = FontWeight.Black)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatrixChoice(Modifier.weight(1f), "AR", "العربية", language == "ar", MatrixBlue) { viewModel.setLanguage("ar") }
                        MatrixChoice(Modifier.weight(1f), "EN", "English", language == "en", MatrixBlue) { viewModel.setLanguage("en") }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MatrixGlass,
                border = BorderStroke(1.dp, MatrixCyan.copy(alpha = .20f)),
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("VISUAL SPECTRUM", color = MatrixCyan, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .9.sp)
                        Text(if (language == "ar") "طيف الواجهة" else "INTERFACE SPECTRUM", color = MatrixInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        MatrixChoice(
                            Modifier.weight(1f),
                            "AUTO",
                            if (language == "ar") "تلقائي" else "System",
                            theme == AppThemeMode.SYSTEM,
                            MatrixMint,
                        ) { viewModel.setThemeMode(AppThemeMode.SYSTEM) }
                        MatrixChoice(
                            Modifier.weight(1f),
                            "AURORA",
                            if (language == "ar") "مضيء" else "Light",
                            theme == AppThemeMode.LIGHT,
                            MatrixBlue,
                        ) { viewModel.setThemeMode(AppThemeMode.LIGHT) }
                        MatrixChoice(
                            Modifier.weight(1f),
                            "DUSK",
                            if (language == "ar") "ليلي" else "Dark",
                            theme == AppThemeMode.DARK,
                            MatrixViolet,
                        ) { viewModel.setThemeMode(AppThemeMode.DARK) }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 14.dp).fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(17.dp),
            color = Color.White.copy(alpha = .70f),
            border = BorderStroke(1.dp, MatrixCyan.copy(alpha = .18f)),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("LOCAL FIRST / V12", color = MatrixInk.copy(alpha = .44f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .75.sp)
                Text("SYSTEM NOMINAL", color = MatrixMint, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .75.sp)
            }
        }
    }

    if (showUpdateManagement) {
        AlmiUpdateManagementDialog(
            language = language,
            onCheckLatest = {
                showUpdateManagement = false
                scope.launch { updateManager.check(manual = true) }
            },
            onRollback = {
                showUpdateManagement = false
                scope.launch { updateManager.rollbackPrevious() }
            },
            onClose = { showUpdateManagement = false },
        )
    }
}

@Composable
private fun MatrixChannel(
    code: String,
    title: String,
    status: String,
    accent: Color,
    tilt: Float,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .graphicsLayer(rotationZ = tilt)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(23.dp),
        color = accent.copy(alpha = .08f),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(48.dp).background(accent, RoundedCornerShape(99.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(code, color = accent, fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                Text(title, color = MatrixInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(status, color = MatrixInk.copy(alpha = .43f), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = .45.sp)
            }
            Text("→", color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MatrixChoice(
    modifier: Modifier,
    code: String,
    label: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(60.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (active) accent.copy(alpha = .16f) else Color(0xFFF7FBFE),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) .62f else .16f)),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(code, color = accent, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .55.sp)
                Text(if (active) "●" else "○", color = if (active) MatrixMint else accent.copy(alpha = .35f), fontSize = 7.sp)
            }
            Text(label, color = MatrixInk, fontSize = 10.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Start)
        }
    }
}

@Composable
private fun MatrixField(sweep: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFF8DCAE8).copy(alpha = .12f)
        val step = 50f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .13f), Offset(x, size.height * .92f), 1f)
            x += step
        }
        var y = size.height * .13f
        while (y <= size.height * .92f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(MatrixBlue.copy(alpha = .07f), size.minDimension * .58f, Offset(size.width * .08f, size.height * .30f))
        drawCircle(MatrixPink.copy(alpha = .05f), size.minDimension * .46f, Offset(size.width * .94f, size.height * .70f))
        val beamY = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, MatrixCyan.copy(alpha = .07f), Color.White.copy(alpha = .18f), MatrixCyan.copy(alpha = .06f), Color.Transparent),
                startY = beamY - 70f,
                endY = beamY + 70f,
            ),
            topLeft = Offset(0f, beamY - 70f),
            size = Size(size.width, 140f),
        )
    }
}
