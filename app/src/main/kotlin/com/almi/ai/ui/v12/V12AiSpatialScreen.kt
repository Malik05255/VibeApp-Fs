package com.almi.ai.ui.v12

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.CustomAiConfig
import com.almi.ai.data.repository.DiscoveredProvider
import com.almi.ai.data.repository.GoogleAiStudioModelInfo
import com.almi.ai.data.repository.GoogleOutputKind
import com.almi.ai.data.repository.ModelCapability
import com.almi.ai.data.repository.OpenRouterModelInfo
import com.almi.ai.ui.settings.SettingsViewModel

private enum class SpatialAiEngine { FREE, ROUTER, GOOGLE, CUSTOM }
private enum class SpatialGoogleTier { FREE, PAID }

private val AiInk = Color(0xFF173B60)
private val AiMint = Color(0xFF4FD5BE)
private val AiBlue = Color(0xFF58BFFF)
private val AiViolet = Color(0xFFA487FA)
private val AiPink = Color(0xFFFF8EB5)
private val AiGlass = Color(0xF2FFFFFF)

@Composable
internal fun V12AiSpatialScreen(
    viewModel: SettingsViewModel,
    language: String,
    onBack: () -> Unit,
) {
    val mode by viewModel.aiMode.collectAsState()
    val googleSettings by viewModel.googleAiStudioSettings.collectAsState()
    var engineName by rememberSaveable {
        mutableStateOf(
            when {
                googleSettings.active -> SpatialAiEngine.GOOGLE.name
                mode == AiMode.FREE_AUTO -> SpatialAiEngine.FREE.name
                mode == AiMode.CUSTOM -> SpatialAiEngine.CUSTOM.name
                else -> SpatialAiEngine.ROUTER.name
            },
        )
    }
    val engine = runCatching { SpatialAiEngine.valueOf(engineName) }.getOrDefault(SpatialAiEngine.FREE)
    val accent = aiEngineAccent(engine)
    val pulse by rememberInfiniteTransition(label = "ai-core")
        .animateFloat(
            initialValue = .96f,
            targetValue = 1.055f,
            animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse),
            label = "ai-core-pulse",
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFEDFFFB),
                        Color(0xFFF1F8FF),
                        Color(0xFFF7F2FF),
                        Color(0xFFFFF5FA),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        AiAtmosphere()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / INTELLIGENCE FIELD", color = AiMint, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.15.sp)
                Text(if (language == "ar") "اختر عقل ALMI" else "CHOOSE THE MIND", color = AiInk, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Surface(
                modifier = Modifier.size(48.dp).clickable(onClick = onBack),
                shape = CircleShape,
                color = AiGlass,
                border = BorderStroke(1.dp, AiBlue.copy(alpha = .30f)),
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(V12GlyphType.BACK, AiInk, Modifier.size(20.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp)
                .fillMaxWidth()
                .height(242.dp),
        ) {
            AiCore(
                modifier = Modifier.align(Alignment.Center).scale(pulse),
                accent = accent,
                label = engine.name,
            )

            AiEngineOrbit(
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 24.dp, y = (-61).dp),
                label = "FREE",
                accent = AiMint,
                active = engine == SpatialAiEngine.FREE,
            ) { engineName = SpatialAiEngine.FREE.name }
            AiEngineOrbit(
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-24).dp, y = (-61).dp),
                label = "ROUTER",
                accent = AiViolet,
                active = engine == SpatialAiEngine.ROUTER,
            ) { engineName = SpatialAiEngine.ROUTER.name }
            AiEngineOrbit(
                modifier = Modifier.align(Alignment.BottomStart).offset(x = 52.dp, y = (-5).dp),
                label = "GOOGLE",
                accent = AiBlue,
                active = engine == SpatialAiEngine.GOOGLE,
            ) { engineName = SpatialAiEngine.GOOGLE.name }
            AiEngineOrbit(
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-52).dp, y = (-5).dp),
                label = "CUSTOM",
                accent = AiPink,
                active = engine == SpatialAiEngine.CUSTOM,
            ) { engineName = SpatialAiEngine.CUSTOM.name }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(.61f)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp, bottomEnd = 30.dp, bottomStart = 30.dp),
            color = AiGlass,
            border = BorderStroke(1.5.dp, accent.copy(alpha = .34f)),
            shadowElevation = 20.dp,
        ) {
            AnimatedContent(
                targetState = engine,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(tween(280), initialScale = .97f)) togetherWith
                        (fadeOut(tween(150)) + scaleOut(tween(190), targetScale = 1.02f))
                },
                label = "ai-engine-workspace",
            ) { selected ->
                when (selected) {
                    SpatialAiEngine.FREE -> SpatialFreeEngine(viewModel, language)
                    SpatialAiEngine.ROUTER -> SpatialRouterEngine(viewModel, language)
                    SpatialAiEngine.GOOGLE -> SpatialGoogleEngine(viewModel, language)
                    SpatialAiEngine.CUSTOM -> SpatialCustomEngine(viewModel, language)
                }
            }
        }
    }
}

@Composable
private fun AiAtmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(AiMint.copy(alpha = .10f), size.minDimension * .60f, Offset(size.width * .04f, size.height * .17f))
        drawCircle(AiBlue.copy(alpha = .08f), size.minDimension * .47f, Offset(size.width * .94f, size.height * .34f))
        drawCircle(AiViolet.copy(alpha = .07f), size.minDimension * .40f, Offset(size.width * .18f, size.height * .80f), style = Stroke(1.3f))
        drawCircle(AiPink.copy(alpha = .065f), size.minDimension * .48f, Offset(size.width * .94f, size.height * .85f))
    }
}

@Composable
private fun AiCore(modifier: Modifier, accent: Color, label: String) {
    Box(modifier.size(128.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = .08f), size.minDimension * .50f)
            drawCircle(accent.copy(alpha = .16f), size.minDimension * .37f, style = Stroke(2f))
            drawCircle(Color.White.copy(alpha = .96f), size.minDimension * .27f)
            drawCircle(accent.copy(alpha = .46f), size.minDimension * .27f, style = Stroke(1.5f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            V12Glyph(V12GlyphType.AI, accent, Modifier.size(25.dp))
            Text(label, modifier = Modifier.padding(top = 3.dp), color = AiInk, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .7.sp)
        }
    }
}

@Composable
private fun AiEngineOrbit(
    modifier: Modifier,
    label: String,
    accent: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(if (active) 84.dp else 70.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .90f) else Color(0xEEFFFFFF),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = .50f)),
        shadowElevation = if (active) 15.dp else 7.dp,
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            V12Glyph(V12GlyphType.AI, if (active) Color.White else accent, Modifier.size(if (active) 23.dp else 19.dp))
            Text(label, modifier = Modifier.padding(top = 4.dp), color = if (active) Color.White else AiInk, fontSize = 6.5.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SpatialEngineHeader(code: String, title: String, subtitle: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(code, color = accent, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.05.sp)
        Text(title, color = AiInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = AiInk.copy(alpha = .45f), fontSize = 8.5.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun SpatialFreeEngine(viewModel: SettingsViewModel, language: String) {
    val state by viewModel.providerDiscoveryState.collectAsState()
    val mode by viewModel.aiMode.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpatialEngineHeader(
            "00 / FREE",
            if (language == "ar") "مسارات بدون مفتاحك" else "KEYLESS ROUTES",
            if (language == "ar") "نفحص المزودين الحقيقيين ونظهر فقط ما يستطيع ALMI تشغيله الآن." else "ALMI scans real providers and exposes only routes it can use now.",
            AiMint,
        )
        SpatialAction(
            text = if (state.isChecking) (if (language == "ar") "نفحص الشبكة…" else "SCANNING FIELD…") else (if (language == "ar") "افحص الشبكة" else "SCAN THE FIELD"),
            accent = AiMint,
            active = state.isChecking,
            onClick = viewModel::discoverFreeProviders,
        )
        if (state.isChecking) CircularProgressIndicator(color = AiMint, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        state.result.providers.forEach { provider ->
            SpatialProviderSignal(
                provider = provider,
                active = mode == AiMode.FREE_AUTO && state.activeProviderId == provider.id,
            ) { viewModel.activateDiscoveredProvider(provider.id) }
        }
        if (state.result.providers.isNotEmpty()) {
            Text("SCANNED ${state.result.scannedCount} • EXCLUDED ${state.result.excludedCount}", color = AiInk.copy(alpha = .38f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = .65.sp)
        }
        if (state.error != null) {
            Text(if (language == "ar") "تعذر فحص المزودين" else "PROVIDER SCAN FAILED", color = AiPink, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SpatialProviderSignal(provider: DiscoveredProvider, active: Boolean, onClick: () -> Unit) {
    val usable = provider.connected && provider.integrated && !provider.requiresPersonalApiKey
    val accent = if (usable) AiMint else AiPink
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = usable, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = if (active) AiMint.copy(alpha = .16f) else Color(0xFFF7FCFF),
        border = BorderStroke(1.dp, if (active) AiMint else accent.copy(alpha = .25f)),
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(37.dp), shape = CircleShape, color = accent.copy(alpha = .12f)) {
                Box(contentAlignment = Alignment.Center) { V12Glyph(V12GlyphType.AI, accent, Modifier.size(18.dp)) }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.width(190.dp)) {
                Text(provider.name, color = AiInk, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (provider.supportsText) append("TXT ")
                        if (provider.supportsImage) append("IMG ")
                        if (provider.supportsVideo) append("VID")
                    }.trim(),
                    color = AiInk.copy(alpha = .40f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .55.sp,
                )
            }
            Text(if (active) "LIVE" else if (usable) "READY" else "BLOCKED", color = accent, fontSize = 7.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SpatialRouterEngine(viewModel: SettingsViewModel, language: String) {
    val config by viewModel.openRouterConfig.collectAsState()
    val state by viewModel.openRouterState.collectAsState()
    val keys by viewModel.apiKeys.collectAsState()
    val oauth by viewModel.oauthState.collectAsState()
    var key by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var capabilityName by rememberSaveable { mutableStateOf(ModelCapability.TEXT.name) }
    val capability = runCatching { ModelCapability.valueOf(capabilityName) }.getOrDefault(ModelCapability.TEXT)
    val catalog = state.catalog.filtered(config.freeOnly)
    val models = when (capability) {
        ModelCapability.TEXT -> catalog.textModels
        ModelCapability.IMAGE -> catalog.imageModels
        ModelCapability.VIDEO -> catalog.videoModels
    }.filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        SpatialEngineHeader(
            "01 / ROUTER",
            "OPENROUTER",
            if (language == "ar") "اتصال تلقائي أو مفتاح يدوي مع كتالوج النماذج الحي." else "OAuth or manual key with the live model universe.",
            AiViolet,
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpatialAction(if (oauth.isConnecting) "CONNECTING…" else "AUTO CONNECT", AiViolet, oauth.isConnecting, viewModel::connectOpenRouterAutomatically)
            SpatialAction(if (config.freeOnly) "FREE ONLY" else "ALL MODELS", AiMint, config.freeOnly) { viewModel.setOpenRouterFreeOnly(!config.freeOnly) }
            SpatialAction("REFRESH", AiBlue, false, viewModel::refreshOpenRouter)
        }

        if (keys.isEmpty()) {
            SpatialSecretField(key, { key = it }, "OPENROUTER API KEY", AiViolet)
            SpatialAction(if (language == "ar") "حفظ المفتاح" else "SAVE KEY", AiViolet, false) {
                viewModel.addManualOpenRouterKey(key, config.freeOnly)
                key = ""
            }
        } else {
            keys.forEach { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFFF8FAFF),
                    border = BorderStroke(1.dp, AiViolet.copy(alpha = .20f)),
                ) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Column(modifier = Modifier.width(190.dp)) {
                            Text(record.label, color = AiInk, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (record.enabled) "KEY / LIVE" else "KEY / PAUSED", color = if (record.enabled) AiMint else AiInk.copy(alpha = .34f), fontSize = 6.8.sp, fontWeight = FontWeight.Black)
                        }
                        SpatialTinyAction(if (record.enabled) "ON" else "OFF", if (record.enabled) AiMint else AiBlue) { viewModel.setApiKeyEnabled(record.id, !record.enabled) }
                        SpatialTinyAction("×", AiPink) { viewModel.removeApiKey(record.id) }
                    }
                }
            }
        }

        CapabilityOrbitRow(
            selected = capability,
            onSelect = { capabilityName = it.name },
            accent = AiViolet,
        )
        SpatialSearchField(query, { query = it }, if (language == "ar") "ابحث عن موديل" else "SEARCH MODEL", AiViolet)
        if (state.isLoading) CircularProgressIndicator(color = AiViolet, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        models.forEach { model ->
            SpatialRouterModelSignal(
                model = model,
                selected = spatialSelectedRouterId(config, capability) == model.id,
            ) { viewModel.selectOpenRouterModel(capability, model.id) }
        }
    }
}

@Composable
private fun CapabilityOrbitRow(selected: ModelCapability, onSelect: (ModelCapability) -> Unit, accent: Color) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ModelCapability.entries.forEach { capability ->
            Surface(
                modifier = Modifier.size(74.dp).clickable { onSelect(capability) },
                shape = CircleShape,
                color = if (selected == capability) accent.copy(alpha = .90f) else Color(0xFFF6FAFF),
                border = BorderStroke(1.dp, accent.copy(alpha = .38f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(capability.name, color = if (selected == capability) Color.White else AiInk, fontSize = 7.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun SpatialRouterModelSignal(model: OpenRouterModelInfo, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) AiViolet.copy(alpha = .14f) else Color(0xFFF8FAFF),
        border = BorderStroke(1.dp, if (selected) AiViolet else AiViolet.copy(alpha = .18f)),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(model.name, modifier = Modifier.width(245.dp), color = AiInk, fontSize = 9.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (model.isFree) "FREE" else "PAID", color = if (model.isFree) AiMint else AiPink, fontSize = 6.8.sp, fontWeight = FontWeight.Black)
            }
            Text(model.id, color = AiInk.copy(alpha = .36f), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SpatialGoogleEngine(viewModel: SettingsViewModel, language: String) {
    val state by viewModel.googleAiStudioState.collectAsState()
    val settings by viewModel.googleAiStudioSettings.collectAsState()
    var key by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var tierName by rememberSaveable { mutableStateOf(SpatialGoogleTier.FREE.name) }
    var kindName by rememberSaveable { mutableStateOf(GoogleOutputKind.TEXT.name) }
    val tier = runCatching { SpatialGoogleTier.valueOf(tierName) }.getOrDefault(SpatialGoogleTier.FREE)
    val kind = runCatching { GoogleOutputKind.valueOf(kindName) }.getOrDefault(GoogleOutputKind.TEXT)
    val source = if (tier == SpatialGoogleTier.FREE) state.catalog.freeModels else state.catalog.paidModels
    val models = source.filter { it.outputKind == kind && (query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true)) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        SpatialEngineHeader(
            "02 / GOOGLE",
            "GOOGLE AI STUDIO",
            if (language == "ar") "مفتاح واحد لمسارات النص والصورة والفيديو." else "One key for explicit text, image and video routes.",
            AiBlue,
        )
        if (!settings.connected && !state.connected) {
            SpatialSecretField(key, { key = it }, "GOOGLE AI STUDIO API KEY", AiBlue)
            SpatialAction(if (state.isConnecting) "CONNECTING…" else "CONNECT", AiBlue, state.isConnecting) { viewModel.connectGoogleAiStudio(key) }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpatialAction("REFRESH", AiBlue, false, viewModel::refreshGoogleAiStudio)
                SpatialAction("DISCONNECT", AiPink, false, viewModel::disconnectGoogleAiStudio)
                SpatialAction(tier.name, if (tier == SpatialGoogleTier.FREE) AiMint else AiPink, true) {
                    tierName = if (tier == SpatialGoogleTier.FREE) SpatialGoogleTier.PAID.name else SpatialGoogleTier.FREE.name
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GoogleOutputKind.entries.forEach { output ->
                    Surface(
                        modifier = Modifier.size(72.dp).clickable { kindName = output.name },
                        shape = CircleShape,
                        color = if (kind == output) AiBlue.copy(alpha = .90f) else Color(0xFFF6FAFF),
                        border = BorderStroke(1.dp, AiBlue.copy(alpha = .36f)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(output.name, color = if (kind == output) Color.White else AiInk, fontSize = 7.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            SpatialSearchField(query, { query = it }, if (language == "ar") "ابحث" else "SEARCH MODELS", AiBlue)
            models.forEach { model ->
                SpatialGoogleModelSignal(
                    model = model,
                    selected = spatialSelectedGoogle(settings, model),
                    tier = tier,
                ) {
                    if (tier == SpatialGoogleTier.FREE) viewModel.selectGoogleFreeModel(model)
                    else viewModel.selectGooglePaidModel(model)
                }
            }
        }
        if (state.isConnecting) CircularProgressIndicator(color = AiBlue, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        if (state.error != null) {
            Text(if (language == "ar") "تعذر الاتصال بـGoogle" else "GOOGLE CONNECTION FAILED", color = AiPink, fontSize = 8.5.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SpatialGoogleModelSignal(
    model: GoogleAiStudioModelInfo,
    selected: Boolean,
    tier: SpatialGoogleTier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) AiBlue.copy(alpha = .14f) else Color(0xFFF8FCFF),
        border = BorderStroke(1.dp, if (selected) AiBlue else AiBlue.copy(alpha = .18f)),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(model.name, modifier = Modifier.width(245.dp), color = AiInk, fontSize = 9.5.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tier.name, color = if (tier == SpatialGoogleTier.FREE) AiMint else AiPink, fontSize = 6.8.sp, fontWeight = FontWeight.Black)
            }
            Text(if (tier == SpatialGoogleTier.PAID) model.paidPriceLabel else model.id, color = AiInk.copy(alpha = .36f), fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SpatialCustomEngine(viewModel: SettingsViewModel, language: String) {
    val saved by viewModel.customAiConfig.collectAsState()
    var provider by remember(saved) { mutableStateOf(saved.providerName) }
    var base by remember(saved) { mutableStateOf(saved.baseUrl) }
    var key by remember(saved) { mutableStateOf(saved.apiKey) }
    var analysisEndpoint by remember(saved) { mutableStateOf(saved.analysisEndpoint) }
    var analysisModel by remember(saved) { mutableStateOf(saved.analysisModel) }
    var imageEndpoint by remember(saved) { mutableStateOf(saved.imageEndpoint) }
    var imageModel by remember(saved) { mutableStateOf(saved.imageModel) }
    var videoEndpoint by remember(saved) { mutableStateOf(saved.videoEndpoint) }
    var videoModel by remember(saved) { mutableStateOf(saved.videoModel) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpatialEngineHeader(
            "03 / CUSTOM",
            if (language == "ar") "محركك الخاص" else "YOUR OWN ENGINE",
            if (language == "ar") "كل endpoint وmodel يبقى واضحًا وقابلًا للتعديل." else "Every endpoint and model remains explicit and editable.",
            AiPink,
        )
        SpatialTextField(provider, { provider = it }, "PROVIDER", AiPink)
        SpatialTextField(base, { base = it }, "BASE URL", AiPink)
        SpatialSecretField(key, { key = it }, "API KEY", AiPink)
        SpatialSection("ANALYSIS", AiViolet)
        SpatialTextField(analysisEndpoint, { analysisEndpoint = it }, "ENDPOINT", AiViolet)
        SpatialTextField(analysisModel, { analysisModel = it }, "MODEL", AiViolet)
        SpatialSection("IMAGE", AiBlue)
        SpatialTextField(imageEndpoint, { imageEndpoint = it }, "ENDPOINT", AiBlue)
        SpatialTextField(imageModel, { imageModel = it }, "MODEL", AiBlue)
        SpatialSection("VIDEO", AiMint)
        SpatialTextField(videoEndpoint, { videoEndpoint = it }, "ENDPOINT", AiMint)
        SpatialTextField(videoModel, { videoModel = it }, "MODEL", AiMint)
        SpatialAction(if (language == "ar") "حفظ وتشغيل" else "SAVE + ACTIVATE", AiPink, true) {
            viewModel.saveAndActivateCustom(
                CustomAiConfig(
                    provider,
                    base,
                    key,
                    analysisEndpoint,
                    analysisModel,
                    imageEndpoint,
                    imageModel,
                    videoEndpoint,
                    videoModel,
                ),
            )
        }
    }
}

@Composable
private fun SpatialAction(text: String, accent: Color, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(43.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (active) accent.copy(alpha = .18f) else Color(0xFFF7FBFF),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) .60f else .30f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, modifier = Modifier.padding(horizontal = 14.dp), color = AiInk, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = .45.sp)
        }
    }
}

@Composable
private fun SpatialTinyAction(text: String, accent: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(35.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = accent.copy(alpha = .12f),
        border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = accent, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SpatialTextField(value: String, onChange: (String) -> Unit, placeholder: String, accent: Color) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(22.dp),
    )
}

@Composable
private fun SpatialSecretField(value: String, onChange: (String) -> Unit, placeholder: String, accent: Color) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(22.dp),
    )
}

@Composable
private fun SpatialSearchField(value: String, onChange: (String) -> Unit, placeholder: String, accent: Color) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(22.dp),
    )
}

@Composable
private fun SpatialSection(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(8.dp), shape = CircleShape, color = accent) {}
        Spacer(Modifier.width(7.dp))
        Text(text, color = accent, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.05.sp)
    }
}

private fun aiEngineAccent(engine: SpatialAiEngine): Color = when (engine) {
    SpatialAiEngine.FREE -> AiMint
    SpatialAiEngine.ROUTER -> AiViolet
    SpatialAiEngine.GOOGLE -> AiBlue
    SpatialAiEngine.CUSTOM -> AiPink
}

private fun spatialSelectedRouterId(
    config: com.almi.ai.data.preferences.OpenRouterConfig,
    capability: ModelCapability,
): String = when (capability) {
    ModelCapability.TEXT -> config.analysisModel
    ModelCapability.IMAGE -> config.imageModel
    ModelCapability.VIDEO -> config.videoModel
}

private fun spatialSelectedGoogle(
    settings: com.almi.ai.data.preferences.GoogleAiStudioSettings,
    model: GoogleAiStudioModelInfo,
): Boolean = when (model.outputKind) {
    GoogleOutputKind.TEXT -> settings.textModelId == model.id
    GoogleOutputKind.IMAGE -> settings.imageModelId == model.id
    GoogleOutputKind.VIDEO -> settings.videoModelId == model.id
}
