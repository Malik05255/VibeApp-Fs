package com.almi.ai.ui.v12

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.almi.ai.ui.tryon.GarmentSize
import com.almi.ai.ui.tryon.GenerationError
import com.almi.ai.ui.tryon.ProductError
import com.almi.ai.ui.tryon.TryOnUiState
import com.almi.ai.ui.tryon.TryOnViewModel
import java.io.File

private enum class SpatialFitLens { YOU, GARMENT, SIZE }

private val FitInk = Color(0xFF173A60)
private val FitBlue = Color(0xFF57BFFF)
private val FitPink = Color(0xFFFF8FB9)
private val FitMint = Color(0xFF54D9C0)
private val FitViolet = Color(0xFFA68AFF)
private val FitIce = Color(0xFFF2FBFF)

@Composable
internal fun V12FitSpatialScreen(
    viewModel: TryOnViewModel,
    language: String,
    onBack: () -> Unit,
    onAvatar: () -> Unit,
    onAi: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var lens by rememberSaveable { mutableStateOf<SpatialFitLens?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    if (state.generatedImage != null) {
        V12FitScreen(
            viewModel = viewModel,
            language = language,
            onBack = onBack,
            onAvatar = onAvatar,
            onAi = onAi,
        )
        return
    }

    val personPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            spatialPersistPermission(context, it)
            viewModel.setPersonImage(it.toString())
            lens = null
        }
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            spatialPersistPermission(context, it)
            viewModel.setGarmentImage(it.toString())
            lens = null
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            cameraUri?.let { viewModel.setPersonImage(it.toString()) }
            lens = null
        }
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
                        Color(0xFFE9F8FF),
                        Color(0xFFF6F3FF),
                        Color(0xFFFFF5FA),
                        Color(0xFFF2FFFB),
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        SpatialFitAtmosphere()

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
                                Color(0xFFEAF8FF).copy(alpha = .42f),
                            ),
                        ),
                    ),
            )
        } else {
            EmptyFitStage(language)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ALMI / FIT FIELD", color = FitBlue, fontSize = 8.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.25.sp)
                Text(if (language == "ar") "مرآتك الحيّة" else "LIVE FIT", color = FitInk, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
            Surface(
                modifier = Modifier.size(48.dp).clickable(onClick = onBack),
                shape = CircleShape,
                color = Color(0xEFFFFFFF),
                border = BorderStroke(1.dp, FitBlue.copy(alpha = .32f)),
                shadowElevation = 9.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    V12Glyph(V12GlyphType.BACK, FitInk, Modifier.size(20.dp))
                }
            }
        }

        // Three floating lenses replace the old edge rail / form.
        SpatialLensOrb(
            modifier = Modifier.align(Alignment.CenterStart).offset(x = 18.dp, y = (-34).dp),
            accent = FitBlue,
            glyph = V12GlyphType.AVATAR,
            label = if (language == "ar") "أنت" else "YOU",
            ready = hasPerson,
            active = lens == SpatialFitLens.YOU,
        ) { lens = if (lens == SpatialFitLens.YOU) null else SpatialFitLens.YOU }

        SpatialLensOrb(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-18).dp, y = 54.dp),
            accent = FitPink,
            glyph = V12GlyphType.FIT,
            label = if (language == "ar") "القطعة" else "ITEM",
            ready = hasGarment,
            active = lens == SpatialFitLens.GARMENT,
        ) { lens = if (lens == SpatialFitLens.GARMENT) null else SpatialFitLens.GARMENT }

        SpatialLensOrb(
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 28.dp, y = (-142).dp),
            accent = FitMint,
            glyph = V12GlyphType.SIZE,
            label = state.selectedGarmentSize?.label ?: if (language == "ar") "المقاس" else "SIZE",
            ready = hasSize,
            active = lens == SpatialFitLens.SIZE,
        ) { lens = if (lens == SpatialFitLens.SIZE) null else SpatialFitLens.SIZE }

        if (hasGarment) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-24).dp, y = (-138).dp)
                    .size(106.dp)
                    .clickable { lens = SpatialFitLens.GARMENT },
                shape = CircleShape,
                color = Color(0xEFFFFFFF),
                border = BorderStroke(2.dp, FitPink.copy(alpha = .45f)),
                shadowElevation = 15.dp,
            ) {
                AsyncImage(
                    model = state.effectiveGarmentImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        FitIgnitionControl(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 18.dp),
            state = state,
            language = language,
            ready = ready,
            onClick = {
                when {
                    !hasPerson -> lens = SpatialFitLens.YOU
                    !hasGarment -> lens = SpatialFitLens.GARMENT
                    !hasSize -> lens = SpatialFitLens.SIZE
                    else -> viewModel.generateImage()
                }
            },
        )

        AnimatedVisibility(
            visible = lens != null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(initialScale = .88f),
            exit = fadeOut() + scaleOut(targetScale = .88f),
        ) {
            when (lens) {
                SpatialFitLens.YOU -> SpatialYouLens(
                    language = language,
                    onCamera = {
                        spatialCameraTarget(context)?.let { uri ->
                            cameraUri = uri
                            camera.launch(uri)
                        }
                    },
                    onGallery = { personPicker.launch(arrayOf("image/*")) },
                    onAvatar = onAvatar,
                    onClose = { lens = null },
                )
                SpatialFitLens.GARMENT -> SpatialGarmentLens(
                    state = state,
                    language = language,
                    viewModel = viewModel,
                    onGallery = { garmentPicker.launch(arrayOf("image/*")) },
                    onClose = { lens = null },
                )
                SpatialFitLens.SIZE -> SpatialSizeLens(
                    state = state,
                    language = language,
                    viewModel = viewModel,
                    onClose = { lens = null },
                )
                null -> Unit
            }
        }

        when (state.imageError) {
            GenerationError.API_KEY_MISSING -> SpatialErrorSignal(
                if (language == "ar") "محرك الذكاء يحتاج إعداد" else "AI ENGINE NEEDS SETUP",
                onAi,
            )
            GenerationError.REQUEST_FAILED -> SpatialErrorSignal(
                if (language == "ar") "تعذر التوليد — افتح AI" else "GENERATION FAILED — OPEN AI",
                onAi,
            )
            GenerationError.NONE -> Unit
        }
    }
}

@Composable
private fun EmptyFitStage(language: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(292.dp)) {
            drawCircle(FitBlue.copy(alpha = .09f), size.minDimension * .49f)
            drawCircle(FitViolet.copy(alpha = .10f), size.minDimension * .37f, style = Stroke(2f))
            drawCircle(FitPink.copy(alpha = .08f), size.minDimension * .25f)
            drawCircle(Color.White.copy(alpha = .96f), size.minDimension * .15f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = FitBlue.copy(alpha = .13f)) {
                V12Glyph(V12GlyphType.AVATAR, FitBlue, Modifier.padding(16.dp).size(40.dp))
            }
            Text(
                if (language == "ar") "أدخل المشهد" else "STEP INTO THE FIELD",
                modifier = Modifier.padding(top = 16.dp),
                color = FitInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Text(
                if (language == "ar") "صورتك أو شخصيتك تصبح مركز التجربة" else "YOUR PHOTO OR DIGITAL TWIN BECOMES THE INTERFACE",
                modifier = Modifier.padding(top = 5.dp),
                color = FitInk.copy(alpha = .42f),
                fontSize = 8.dp.value.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .6.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SpatialFitAtmosphere() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(FitBlue.copy(alpha = .10f), size.minDimension * .58f, Offset(size.width * .02f, size.height * .18f))
        drawCircle(FitPink.copy(alpha = .08f), size.minDimension * .48f, Offset(size.width * .94f, size.height * .68f))
        drawCircle(FitMint.copy(alpha = .06f), size.minDimension * .40f, Offset(size.width * .25f, size.height * .92f))
        drawCircle(FitViolet.copy(alpha = .07f), size.minDimension * .33f, Offset(size.width * .70f, size.height * .40f), style = Stroke(1.4f))
    }
}

@Composable
private fun SpatialLensOrb(
    modifier: Modifier,
    accent: Color,
    glyph: V12GlyphType,
    label: String,
    ready: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(if (active) 104.dp else 88.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = if (active) accent.copy(alpha = .22f) else Color(0xEFFFFFFF),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = if (active) .86f else .42f)),
        shadowElevation = if (active) 20.dp else 10.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            V12Glyph(glyph, accent, Modifier.size(if (active) 29.dp else 24.dp))
            Text(label, modifier = Modifier.padding(top = 5.dp), color = FitInk, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            if (ready) Text("LIVE", color = accent, fontSize = 6.sp, fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        }
    }
}

@Composable
private fun FitIgnitionControl(
    modifier: Modifier,
    state: TryOnUiState,
    language: String,
    ready: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(72.dp).clickable(enabled = !state.isGeneratingImage, onClick = onClick),
        shape = RoundedCornerShape(36.dp),
        color = if (ready) FitBlue.copy(alpha = .94f) else Color(0xEFFFFFFF),
        border = BorderStroke(1.dp, if (ready) FitBlue else FitBlue.copy(alpha = .25f)),
        shadowElevation = 18.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (state.isGeneratingImage) {
                CircularProgressIndicator(
                    progress = { state.imageProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(30.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                )
            } else {
                Surface(shape = CircleShape, color = if (ready) Color.White.copy(alpha = .18f) else FitBlue.copy(alpha = .12f)) {
                    V12Glyph(V12GlyphType.FIT, if (ready) Color.White else FitBlue, Modifier.padding(10.dp).size(24.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        state.personImage == null -> if (language == "ar") "أدخل أنت" else "ENTER YOU"
                        state.effectiveGarmentImage == null -> if (language == "ar") "أدخل القطعة" else "BRING THE GARMENT"
                        state.selectedGarmentSize == null -> if (language == "ar") "حدد المقاس" else "LOCK THE SIZE"
                        state.isGeneratingImage -> if (language == "ar") "نبني المرآة" else "BUILDING MIRROR"
                        else -> if (language == "ar") "شغّل المرآة" else "IGNITE MIRROR"
                    },
                    color = if (ready) Color.White else FitInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (state.isGeneratingImage) "${(state.imageProgress * 100).toInt()}%" else "YOU • GARMENT • SIZE",
                    color = if (ready) Color.White.copy(alpha = .62f) else FitInk.copy(alpha = .35f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = .7.sp,
                )
            }
            Text("↗", color = if (ready) Color.White else FitBlue, fontSize = 25.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun SpatialLensFrame(
    accent: Color,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(.88f),
        shape = RoundedCornerShape(42.dp),
        color = Color(0xF5FFFFFF),
        border = BorderStroke(1.5.dp, accent.copy(alpha = .45f)),
        shadowElevation = 28.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(subtitle, color = accent, fontSize = 7.5.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    Text(title, color = FitInk, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Surface(modifier = Modifier.size(38.dp).clickable(onClick = onClose), shape = CircleShape, color = accent.copy(alpha = .12f)) {
                    Box(contentAlignment = Alignment.Center) { Text("×", color = FitInk, fontSize = 20.sp) }
                }
            }
            content()
        }
    }
}

@Composable
private fun SpatialYouLens(
    language: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onAvatar: () -> Unit,
    onClose: () -> Unit,
) {
    SpatialLensFrame(
        accent = FitBlue,
        title = if (language == "ar") "من يدخل المرآة؟" else "WHO ENTERS THE MIRROR?",
        subtitle = "01 / YOU",
        onClose = onClose,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SpatialActionOrb(if (language == "ar") "كاميرا" else "CAMERA", V12GlyphType.CAMERA, FitBlue, onCamera)
            SpatialActionOrb(if (language == "ar") "صورة" else "PHOTO", V12GlyphType.IMAGE, FitMint, onGallery)
            SpatialActionOrb(if (language == "ar") "شخصيتي" else "AVATAR", V12GlyphType.AVATAR, FitPink, onAvatar)
        }
    }
}

@Composable
private fun SpatialGarmentLens(
    state: TryOnUiState,
    language: String,
    viewModel: TryOnViewModel,
    onGallery: () -> Unit,
    onClose: () -> Unit,
) {
    SpatialLensFrame(
        accent = FitPink,
        title = if (language == "ar") "أدخل القطعة إلى المجال" else "BRING THE GARMENT INTO THE FIELD",
        subtitle = "02 / GARMENT",
        onClose = onClose,
    ) {
        OutlinedTextField(
            value = state.productUrl,
            onValueChange = viewModel::setProductUrl,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(22.dp),
            placeholder = { Text(if (language == "ar") "ألصق رابط المنتج" else "PASTE PRODUCT LINK") },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SpatialActionOrb(if (language == "ar") "استيراد" else "IMPORT", V12GlyphType.LINK, FitPink) { viewModel.loadProduct() }
            SpatialActionOrb(if (language == "ar") "صورة" else "PHOTO", V12GlyphType.IMAGE, FitViolet, onGallery)
        }
        if (state.isLoadingProduct) {
            Text(if (language == "ar") "نقرأ المنتج…" else "READING PRODUCT…", color = FitPink, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        if (state.productTitle.isNotBlank()) {
            Text(state.productTitle, color = FitInk, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (state.productError) {
            ProductError.EMPTY_URL -> Text(if (language == "ar") "أدخل رابطًا أولًا" else "ADD A LINK FIRST", color = FitPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            ProductError.UNAVAILABLE -> Text(if (language == "ar") "تعذر قراءة المنتج" else "PRODUCT COULD NOT BE READ", color = FitPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            ProductError.IMAGE_NOT_FOUND -> Text(if (language == "ar") "لم نجد صورة؛ أضفها يدويًا" else "NO IMAGE FOUND — ADD ONE", color = FitPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            ProductError.NONE -> Unit
        }
    }
}

@Composable
private fun SpatialSizeLens(
    state: TryOnUiState,
    language: String,
    viewModel: TryOnViewModel,
    onClose: () -> Unit,
) {
    SpatialLensFrame(
        accent = FitMint,
        title = if (language == "ar") "ثبّت المقاس" else "LOCK THE SIZE",
        subtitle = "03 / SIZE",
        onClose = onClose,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            state.availableGarmentSizes.forEach { size ->
                val selected = state.selectedGarmentSize == size
                Surface(
                    modifier = Modifier.size(64.dp).clickable {
                        viewModel.setGarmentSize(size)
                        onClose()
                    },
                    shape = CircleShape,
                    color = if (selected) FitMint.copy(alpha = .24f) else FitIce,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) FitMint else FitMint.copy(alpha = .25f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(size.label, color = FitInk, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        state.fitSimulation?.let { fit ->
            Text(
                "${fit.size.label} • ${fit.confidence.name} • ${fit.overallPressure.name}",
                color = FitInk.copy(alpha = .48f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .45.sp,
            )
        }
    }
}

@Composable
private fun SpatialActionOrb(
    label: String,
    glyph: V12GlyphType,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(92.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = accent.copy(alpha = .12f),
        border = BorderStroke(1.dp, accent.copy(alpha = .40f)),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            V12Glyph(glyph, accent, Modifier.size(27.dp))
            Text(label, modifier = Modifier.padding(top = 7.dp), color = FitInk, fontSize = 8.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SpatialErrorSignal(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.offset(y = 78.dp).padding(horizontal = 18.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = FitPink.copy(alpha = .92f),
        shadowElevation = 10.dp,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

private fun spatialCameraTarget(context: Context): Uri? = runCatching {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(dir, "almi_v12_spatial_${System.currentTimeMillis()}.jpg"),
    )
}.getOrNull()

private fun spatialPersistPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
