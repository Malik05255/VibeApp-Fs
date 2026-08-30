package com.almi.ai.ui.v12

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.almi.ai.ui.tryon.GenerationError
import com.almi.ai.ui.tryon.ProductError
import com.almi.ai.ui.tryon.TryOnUiState
import com.almi.ai.ui.tryon.TryOnViewModel
import java.io.File

private enum class FutureFitStep { YOU, GARMENT, SIZE }

private val PortalInk = Color(0xFF123657)
private val PortalBlue = Color(0xFF38B7F3)
private val PortalCyan = Color(0xFF57E4F1)
private val PortalPink = Color(0xFFFF7EA9)
private val PortalMint = Color(0xFF54D9C2)
private val PortalViolet = Color(0xFF9D8BFF)
private val PortalGlass = Color(0xEEFFFFFF)

@Composable
internal fun V12FutureFitScreen(
    viewModel: TryOnViewModel,
    language: String,
    onBack: () -> Unit,
    onAvatar: () -> Unit,
    onAi: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var activeStepName by rememberSaveable { mutableStateOf<String?>(null) }
    val activeStep = activeStepName?.let { runCatching { FutureFitStep.valueOf(it) }.getOrNull() }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val personPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            futurePersistPermission(context, it)
            viewModel.setPersonImage(it.toString())
            activeStepName = null
        }
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            futurePersistPermission(context, it)
            viewModel.setGarmentImage(it.toString())
            activeStepName = null
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(viewModel::setPersonImage)
        if (ok) activeStepName = null
    }

    val hasPerson = state.personImage != null
    val hasGarment = state.effectiveGarmentImage != null
    val hasSize = state.selectedGarmentSize != null
    val ready = hasPerson && hasGarment && hasSize

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFE7F8FF),
                        Color(0xFFF7FCFF),
                        Color(0xFFFFF8FC),
                        Color(0xFFF0FBFF),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        FuturePortalField()

        FutureFitHeader(language = language, onBack = onBack)

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-34).dp)
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .fillMaxHeight(.58f),
            shape = RoundedCornerShape(38.dp),
            color = Color.White.copy(alpha = .42f),
            border = BorderStroke(1.5.dp, PortalBlue.copy(alpha = .28f)),
            shadowElevation = 18.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                if (hasPerson) {
                    AsyncImage(
                        model = state.personImage,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = .08f),
                                        Color.Transparent,
                                        Color(0xFFEDF9FF).copy(alpha = .28f),
                                    ),
                                ),
                            ),
                    )
                } else {
                    FutureEmptyPortal(language)
                }

                FuturePortalScanOverlay()

                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = .78f),
                    border = BorderStroke(1.dp, PortalCyan.copy(alpha = .25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).background(if (hasPerson) PortalMint else PortalBlue, RoundedCornerShape(99.dp)))
                        Text(
                            if (hasPerson) "HUMAN LINK / LIVE" else "HUMAN LINK / WAITING",
                            color = PortalInk.copy(alpha = .66f),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .7.sp,
                        )
                    }
                }

                if (hasGarment) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(14.dp).width(108.dp).height(132.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = PortalGlass,
                        border = BorderStroke(1.dp, PortalPink.copy(alpha = .38f)),
                        shadowElevation = 12.dp,
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = state.effectiveGarmentImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(34.dp)
                                    .background(Color.White.copy(alpha = .82f)),
                            ) {
                                Text(
                                    state.selectedGarmentSize?.label ?: "GARMENT",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = PortalInk,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }
        }

        FutureStepRail(
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-92).dp),
            language = language,
            state = state,
            activeStep = activeStep,
            onStep = { step -> activeStepName = if (activeStep == step) null else step.name },
        )

        FutureIgnitionBar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 14.dp),
            language = language,
            state = state,
            ready = ready,
            onClick = {
                when {
                    !hasPerson -> activeStepName = FutureFitStep.YOU.name
                    !hasGarment -> activeStepName = FutureFitStep.GARMENT.name
                    !hasSize -> activeStepName = FutureFitStep.SIZE.name
                    else -> viewModel.generateImage()
                }
            },
        )

        AnimatedVisibility(
            visible = activeStep != null,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-90).dp),
            enter = fadeIn(tween(180)) + slideInVertically(tween(280)) { it / 3 },
            exit = fadeOut(tween(130)) + slideOutVertically(tween(220)) { it / 3 },
        ) {
            when (activeStep) {
                FutureFitStep.YOU -> FutureYouPanel(
                    language = language,
                    onCamera = {
                        futureCameraTarget(context)?.let { uri ->
                            cameraUri = uri
                            camera.launch(uri)
                        }
                    },
                    onGallery = { personPicker.launch(arrayOf("image/*")) },
                    onAvatar = onAvatar,
                    onClose = { activeStepName = null },
                )

                FutureFitStep.GARMENT -> FutureGarmentPanel(
                    state = state,
                    language = language,
                    viewModel = viewModel,
                    onGallery = { garmentPicker.launch(arrayOf("image/*")) },
                    onClose = { activeStepName = null },
                )

                FutureFitStep.SIZE -> FutureSizePanel(
                    state = state,
                    language = language,
                    viewModel = viewModel,
                    onClose = { activeStepName = null },
                )

                null -> Unit
            }
        }

        when (state.imageError) {
            GenerationError.API_KEY_MISSING -> FutureFitError(
                text = if (language == "ar") "محرك الذكاء يحتاج إعداد" else "AI ENGINE NEEDS SETUP",
                onClick = onAi,
            )
            GenerationError.REQUEST_FAILED -> FutureFitError(
                text = if (language == "ar") "تعذر التوليد — افتح الذكاء" else "GENERATION FAILED — OPEN AI",
                onClick = onAi,
            )
            GenerationError.NONE -> Unit
        }
    }
}

@Composable
private fun FutureFitHeader(language: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text("ALMI // FIT PORTAL", color = PortalBlue, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.25.sp)
            Text(
                if (language == "ar") "مرآة الاحتمال" else "PROBABILITY MIRROR",
                color = PortalInk,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                if (language == "ar") "أنت + القطعة + المقاس = تجربة حيّة" else "YOU + GARMENT + SIZE = LIVE SYNTHESIS",
                color = PortalInk.copy(alpha = .48f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .35.sp,
            )
        }
        Surface(
            modifier = Modifier.width(76.dp).height(42.dp).clickable(onClick = onBack),
            shape = RoundedCornerShape(16.dp),
            color = PortalGlass,
            border = BorderStroke(1.dp, PortalBlue.copy(alpha = .28f)),
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (language == "ar") "رجوع" else "BACK", color = PortalInk, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun FutureEmptyPortal(language: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(PortalBlue.copy(alpha = .08f), size.minDimension * .31f, Offset(size.width * .50f, size.height * .47f))
            drawCircle(PortalCyan.copy(alpha = .11f), size.minDimension * .23f, Offset(size.width * .50f, size.height * .47f))
            val top = size.height * .20f
            val bottom = size.height * .77f
            val cx = size.width * .50f
            drawCircle(PortalBlue.copy(alpha = .18f), size.minDimension * .065f, Offset(cx, top))
            drawRoundRect(
                color = PortalBlue.copy(alpha = .14f),
                topLeft = Offset(cx - size.width * .075f, top + size.minDimension * .07f),
                size = Size(size.width * .15f, bottom - top - size.minDimension * .08f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * .08f),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ALMI HUMAN PORTAL", color = PortalBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text(
                if (language == "ar") "أدخل نسختك" else "ENTER YOUR TWIN",
                modifier = Modifier.padding(top = 6.dp),
                color = PortalInk,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun FuturePortalField() {
    val sweep by rememberInfiniteTransition(label = "fit-portal-field")
        .animateFloat(
            initialValue = -.12f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(3900), RepeatMode.Restart),
            label = "fit-portal-field-sweep",
        )
    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFF8FCDEB).copy(alpha = .13f)
        val step = 48f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .16f), Offset(x, size.height * .91f), 1f)
            x += step
        }
        var y = size.height * .16f
        while (y <= size.height * .91f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(PortalBlue.copy(alpha = .07f), size.minDimension * .60f, Offset(size.width * .08f, size.height * .28f))
        drawCircle(PortalPink.copy(alpha = .055f), size.minDimension * .48f, Offset(size.width * .94f, size.height * .69f))
        val yBeam = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, PortalCyan.copy(alpha = .07f), Color.White.copy(alpha = .19f), PortalCyan.copy(alpha = .06f), Color.Transparent),
                startY = yBeam - 70f,
                endY = yBeam + 70f,
            ),
            topLeft = Offset(0f, yBeam - 70f),
            size = Size(size.width, 140f),
        )
    }
}

@Composable
private fun FuturePortalScanOverlay() {
    val sweep by rememberInfiniteTransition(label = "fit-portal-scan")
        .animateFloat(
            initialValue = .08f,
            targetValue = .92f,
            animationSpec = infiniteRepeatable(tween(2500), RepeatMode.Reverse),
            label = "fit-portal-scan-value",
        )
    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, PortalCyan.copy(alpha = .07f), PortalCyan.copy(alpha = .16f), Color.White.copy(alpha = .14f), Color.Transparent),
                startY = y - 55f,
                endY = y + 55f,
            ),
            topLeft = Offset(0f, y - 55f),
            size = Size(size.width, 110f),
        )
        drawLine(PortalCyan.copy(alpha = .42f), Offset(size.width * .04f, y), Offset(size.width * .96f, y), 1.2f)
    }
}

@Composable
private fun FutureStepRail(
    modifier: Modifier,
    language: String,
    state: TryOnUiState,
    activeStep: FutureFitStep?,
    onStep: (FutureFitStep) -> Unit,
) {
    Surface(
        modifier = modifier.padding(horizontal = 18.dp).fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(24.dp),
        color = PortalGlass,
        border = BorderStroke(1.dp, Color.White),
        shadowElevation = 14.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FutureStepCell(
                modifier = Modifier.weight(1f),
                index = "01",
                title = if (language == "ar") "أنت" else "YOU",
                value = if (state.personImage != null) "LINKED" else "WAITING",
                accent = PortalBlue,
                active = activeStep == FutureFitStep.YOU,
                ready = state.personImage != null,
            ) { onStep(FutureFitStep.YOU) }
            FutureStepCell(
                modifier = Modifier.weight(1f),
                index = "02",
                title = if (language == "ar") "القطعة" else "GARMENT",
                value = if (state.effectiveGarmentImage != null) "READY" else "EMPTY",
                accent = PortalPink,
                active = activeStep == FutureFitStep.GARMENT,
                ready = state.effectiveGarmentImage != null,
            ) { onStep(FutureFitStep.GARMENT) }
            FutureStepCell(
                modifier = Modifier.weight(1f),
                index = "03",
                title = if (language == "ar") "المقاس" else "SIZE",
                value = state.selectedGarmentSize?.label ?: "—",
                accent = PortalMint,
                active = activeStep == FutureFitStep.SIZE,
                ready = state.selectedGarmentSize != null,
            ) { onStep(FutureFitStep.SIZE) }
        }
    }
}

@Composable
private fun FutureStepCell(
    modifier: Modifier,
    index: String,
    title: String,
    value: String,
    accent: Color,
    active: Boolean,
    ready: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxHeight().clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (active) accent.copy(alpha = .16f) else Color(0xFFF7FBFE),
        border = BorderStroke(1.dp, accent.copy(alpha = if (active) .58f else .18f)),
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(index, color = accent, fontSize = 7.sp, fontWeight = FontWeight.Black)
                Text(if (ready) "●" else "○", color = if (ready) PortalMint else accent.copy(alpha = .42f), fontSize = 8.sp)
            }
            Text(title, color = PortalInk, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(value, color = accent, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .45.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FutureIgnitionBar(
    modifier: Modifier,
    language: String,
    state: TryOnUiState,
    ready: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(66.dp).clickable(enabled = !state.isGeneratingImage, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (ready) PortalBlue else PortalGlass,
        border = BorderStroke(1.2.dp, if (ready) PortalCyan.copy(alpha = .72f) else PortalBlue.copy(alpha = .22f)),
        shadowElevation = 18.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (state.isGeneratingImage) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(state.imageProgress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(PortalCyan),
                )
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.isGeneratingImage) {
                    CircularProgressIndicator(
                        progress = { state.imageProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(28.dp),
                        color = Color.White,
                        strokeWidth = 2.3.dp,
                    )
                } else {
                    Text("ALMI", color = if (ready) Color.White else PortalBlue, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            state.personImage == null -> if (language == "ar") "اربط هويتك" else "LINK YOUR IDENTITY"
                            state.effectiveGarmentImage == null -> if (language == "ar") "أدخل القطعة" else "LOAD THE GARMENT"
                            state.selectedGarmentSize == null -> if (language == "ar") "ثبّت المقاس" else "LOCK THE SIZE"
                            state.isGeneratingImage -> if (language == "ar") "نبني الاحتمال" else "SYNTHESIZING"
                            else -> if (language == "ar") "افتح الاحتمال" else "OPEN THE POSSIBILITY"
                        },
                        color = if (ready) Color.White else PortalInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (state.isGeneratingImage) "${(state.imageProgress * 100).toInt()}%" else "FIT / SYNTHESIS / LIVE",
                        color = if (ready) Color.White.copy(alpha = .62f) else PortalInk.copy(alpha = .34f),
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .65.sp,
                    )
                }
                Text("→", color = if (ready) Color.White else PortalBlue, fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun FuturePanelFrame(
    accent: Color,
    code: String,
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
        color = Color(0xFAFFFFFF),
        border = BorderStroke(1.5.dp, accent.copy(alpha = .42f)),
        shadowElevation = 28.dp,
    ) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(code, color = accent, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    Text(title, color = PortalInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
                Surface(
                    modifier = Modifier.width(58.dp).height(34.dp).clickable(onClick = onClose),
                    shape = RoundedCornerShape(13.dp),
                    color = accent.copy(alpha = .10f),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("CLOSE", color = accent, fontSize = 7.sp, fontWeight = FontWeight.Black) }
                }
            }
            content()
        }
    }
}

@Composable
private fun FutureYouPanel(
    language: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onAvatar: () -> Unit,
    onClose: () -> Unit,
) {
    FuturePanelFrame(
        accent = PortalBlue,
        code = "01 / HUMAN LINK",
        title = if (language == "ar") "من يدخل المرآة؟" else "WHO ENTERS THE MIRROR?",
        onClose = onClose,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FutureActionTile(Modifier.weight(1f), "CAMERA", if (language == "ar") "كاميرا" else "Camera", PortalBlue, onCamera)
            FutureActionTile(Modifier.weight(1f), "PHOTO", if (language == "ar") "صورة" else "Photo", PortalMint, onGallery)
            FutureActionTile(Modifier.weight(1f), "TWIN", if (language == "ar") "شخصيتي" else "Avatar", PortalPink, onAvatar)
        }
    }
}

@Composable
private fun FutureGarmentPanel(
    state: TryOnUiState,
    language: String,
    viewModel: TryOnViewModel,
    onGallery: () -> Unit,
    onClose: () -> Unit,
) {
    FuturePanelFrame(
        accent = PortalPink,
        code = "02 / GARMENT INPUT",
        title = if (language == "ar") "أدخل القطعة" else "LOAD THE GARMENT",
        onClose = onClose,
    ) {
        OutlinedTextField(
            value = state.productUrl,
            onValueChange = viewModel::setProductUrl,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            placeholder = { Text(if (language == "ar") "ألصق رابط المنتج" else "PASTE PRODUCT LINK") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FutureActionTile(Modifier.weight(1f), "IMPORT", if (language == "ar") "استيراد" else "Import", PortalPink) { viewModel.loadProduct() }
            FutureActionTile(Modifier.weight(1f), "IMAGE", if (language == "ar") "صورة" else "Image", PortalViolet, onGallery)
        }
        if (state.isLoadingProduct) {
            Text(if (language == "ar") "ALMI يقرأ المنتج…" else "ALMI IS READING THE PRODUCT…", color = PortalPink, fontSize = 8.sp, fontWeight = FontWeight.Black)
        }
        if (state.productTitle.isNotBlank()) {
            Text(state.productTitle, color = PortalInk, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (state.productError) {
            ProductError.EMPTY_URL -> Text(if (language == "ar") "أدخل رابطًا أولًا" else "ADD A LINK FIRST", color = PortalPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            ProductError.UNAVAILABLE -> Text(if (language == "ar") "تعذر قراءة المنتج" else "PRODUCT COULD NOT BE READ", color = PortalPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            ProductError.IMAGE_NOT_FOUND -> Text(if (language == "ar") "لم نجد صورة؛ أضفها يدويًا" else "NO IMAGE FOUND — ADD ONE", color = PortalPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            ProductError.NONE -> Unit
        }
    }
}

@Composable
private fun FutureSizePanel(
    state: TryOnUiState,
    language: String,
    viewModel: TryOnViewModel,
    onClose: () -> Unit,
) {
    FuturePanelFrame(
        accent = PortalMint,
        code = "03 / SIZE LOCK",
        title = if (language == "ar") "ثبّت المقاس" else "LOCK THE SIZE",
        onClose = onClose,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.availableGarmentSizes.forEach { size ->
                val selected = state.selectedGarmentSize == size
                Surface(
                    modifier = Modifier.width(74.dp).height(52.dp).clickable {
                        viewModel.setGarmentSize(size)
                        onClose()
                    },
                    shape = RoundedCornerShape(17.dp),
                    color = if (selected) PortalMint.copy(alpha = .20f) else Color(0xFFF5FBFA),
                    border = BorderStroke(if (selected) 2.dp else 1.dp, PortalMint.copy(alpha = if (selected) .80f else .24f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(size.label, color = PortalInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        state.fitSimulation?.let { fit ->
            Text(
                "${fit.size.label} • ${fit.confidence.name} • ${fit.overallPressure.name}",
                color = PortalInk.copy(alpha = .48f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .35.sp,
            )
        }
    }
}

@Composable
private fun FutureActionTile(
    modifier: Modifier,
    code: String,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.height(74.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = accent.copy(alpha = .10f),
        border = BorderStroke(1.dp, accent.copy(alpha = .30f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(code, color = accent, fontSize = 6.5.sp, fontWeight = FontWeight.Black, letterSpacing = .65.sp)
            Text(label, color = PortalInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
            Box(Modifier.fillMaxWidth().height(2.dp).background(accent.copy(alpha = .38f), RoundedCornerShape(99.dp)))
        }
    }
}

@Composable
private fun FutureFitError(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(top = 78.dp, start = 18.dp, end = 18.dp).fillMaxWidth().height(42.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = PortalPink.copy(alpha = .94f),
        shadowElevation = 10.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

private fun futureCameraTarget(context: Context): Uri? = runCatching {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(dir, "almi_v12_future_${System.currentTimeMillis()}.jpg"),
    )
}.getOrNull()

private fun futurePersistPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
