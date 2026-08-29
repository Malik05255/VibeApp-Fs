package com.almi.ai.ui.v12

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.AppThemeMode
import com.almi.ai.ui.settings.SettingsViewModel

private val ControlInk = Color(0xFF17365D)
private val ControlBlue = Color(0xFF5EBEFF)
private val ControlPink = Color(0xFFFF91BA)
private val ControlMint = Color(0xFF63D7C6)
private val ControlViolet = Color(0xFFA98AFF)

@Composable
internal fun V12ControlScreen(
    viewModel: SettingsViewModel,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEFF9FF), Color(0xFFF8F4FF), Color(0xFFFFF7FB)),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ControlBlue.copy(alpha = .09f), size.minDimension * .50f, androidx.compose.ui.geometry.Offset(size.width * .10f, size.height * .20f))
            drawCircle(ControlPink.copy(alpha = .08f), size.minDimension * .48f, androidx.compose.ui.geometry.Offset(size.width * .90f, size.height * .74f))
            drawCircle(ControlViolet.copy(alpha = .05f), size.minDimension * .34f, androidx.compose.ui.geometry.Offset(size.width * .55f, size.height * .48f), style = Stroke(1.5f))
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / COMMAND DECK", color = ControlBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(if (language == "ar") "مركز التحكم" else "CONTROL CORE", color = ControlInk, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            Surface(
                modifier = Modifier.size(50.dp).clickable(onClick = onBack),
                shape = CircleShape,
                color = Color(0xEFFFFFFF),
                border = BorderStroke(1.dp, ControlBlue.copy(alpha = .28f)),
                shadowElevation = 10.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×", color = ControlInk, fontSize = 26.sp, fontWeight = FontWeight.Light)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CommandOrb(
                    modifier = Modifier.fillMaxWidth(.47f),
                    accent = ControlMint,
                    glyph = V12GlyphType.BODY,
                    title = if (language == "ar") "الجسم" else "BODY",
                    status = if (bodyReady) "CALIBRATED" else "SETUP",
                    onClick = onBody,
                )
                CommandOrb(
                    modifier = Modifier.fillMaxWidth(.47f),
                    accent = ControlPink,
                    glyph = V12GlyphType.AVATAR,
                    title = if (language == "ar") "الهوية" else "IDENTITY",
                    status = if (avatarReady) "LIVE" else "CREATE",
                    onClick = onAvatar,
                )
            }
            CommandOrb(
                modifier = Modifier.fillMaxWidth(),
                accent = ControlViolet,
                glyph = V12GlyphType.AI,
                title = if (language == "ar") "الذكاء" else "INTELLIGENCE",
                status = if (google.active) "GOOGLE / ACTIVE" else when (aiMode) {
                    AiMode.OPENROUTER -> "OPENROUTER / ACTIVE"
                    AiMode.CUSTOM -> "CUSTOM / ACTIVE"
                    AiMode.FREE_AUTO -> "FREE AUTO / ACTIVE"
                },
                onClick = onAi,
                wide = true,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(34.dp),
                color = Color(0xEFFFFFFF),
                border = BorderStroke(1.dp, Color.White),
                shadowElevation = 14.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = ControlBlue.copy(alpha = .12f)) {
                            V12Glyph(V12GlyphType.CONTROL, ControlBlue, Modifier.padding(10.dp).size(22.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(if (language == "ar") "واجهة النظام" else "INTERFACE PRISM", color = ControlInk, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text(if (language == "ar") "اللغة والمظهر" else "LANGUAGE + APPEARANCE", color = ControlInk.copy(alpha = .45f), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        PrismChoice("العربية", language == "ar", ControlBlue, Modifier.fillMaxWidth(.47f)) { viewModel.setLanguage("ar") }
                        PrismChoice("English", language == "en", ControlBlue, Modifier.fillMaxWidth(.47f)) { viewModel.setLanguage("en") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        PrismChoice(if (language == "ar") "تلقائي" else "SYSTEM", theme == AppThemeMode.SYSTEM, ControlMint, Modifier.fillMaxWidth(.30f)) { viewModel.setThemeMode(AppThemeMode.SYSTEM) }
                        PrismChoice(if (language == "ar") "نهاري" else "AURORA", theme == AppThemeMode.LIGHT, ControlBlue, Modifier.fillMaxWidth(.30f)) { viewModel.setThemeMode(AppThemeMode.LIGHT) }
                        PrismChoice(if (language == "ar") "ليلي" else "DUSK", theme == AppThemeMode.DARK, ControlViolet, Modifier.fillMaxWidth(.30f)) { viewModel.setThemeMode(AppThemeMode.DARK) }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("LOCAL FIRST • V12", color = ControlInk.copy(alpha = .42f), fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
            Surface(shape = CircleShape, color = ControlMint.copy(alpha = .16f)) {
                Text("12", Modifier.padding(11.dp), color = ControlMint, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CommandOrb(
    modifier: Modifier,
    accent: Color,
    glyph: V12GlyphType,
    title: String,
    status: String,
    onClick: () -> Unit,
    wide: Boolean = false,
) {
    Surface(
        modifier = modifier.height(if (wide) 116.dp else 150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(if (wide) 38.dp else 44.dp),
        color = Color(0xEFFFFFFF),
        border = BorderStroke(1.dp, accent.copy(alpha = .34f)),
        shadowElevation = 15.dp,
    ) {
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(66.dp), shape = CircleShape, color = accent.copy(alpha = .14f)) {
                    Box(contentAlignment = Alignment.Center) { V12Glyph(glyph, accent, Modifier.size(32.dp)) }
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.fillMaxWidth(.68f)) {
                    Text(title, color = ControlInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text(status, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                }
                Text("↗", color = accent, fontSize = 25.sp)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(modifier = Modifier.size(66.dp), shape = CircleShape, color = accent.copy(alpha = .14f)) {
                    Box(contentAlignment = Alignment.Center) { V12Glyph(glyph, accent, Modifier.size(32.dp)) }
                }
                Spacer(Modifier.height(10.dp))
                Text(title, color = ControlInk, fontSize = 16.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(status, color = accent, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            }
        }
    }
}

@Composable
private fun PrismChoice(
    text: String,
    active: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(43.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (active) accent.copy(alpha = .18f) else Color(0xFFF6FAFF),
        border = BorderStroke(1.dp, if (active) accent.copy(alpha = .65f) else Color(0xFFDDEAF6)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (active) ControlInk else ControlInk.copy(alpha = .58f), fontSize = 9.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}
