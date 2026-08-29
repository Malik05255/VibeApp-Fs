package com.almi.ai.ui.tryon

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.almi.ai.data.repository.MotionDirection
import com.almi.ai.data.repository.VideoGenerationStatus
import java.io.File

private enum class MirrorGate { PERSON, GARMENT, SIZE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V11MirrorScreen(
    viewModel: TryOnViewModel,
    language: String,
    onOpenAi: () -> Unit,
    onOpenAvatar: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var gate by rememberSaveable { mutableStateOf<MirrorGate?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val personPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistPermission(context, it)
            viewModel.setPersonImage(it.toString())
        }
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistPermission(context, it)
            viewModel.setGarmentImage(it.toString())
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.setPersonImage(it.toString()) }
    }

    LaunchedEffect(state.personImage) {
        if (gate == MirrorGate.PERSON && state.personImage != null) gate = MirrorGate.GARMENT
    }
    LaunchedEffect(state.effectiveGarmentImage, state.isLoadingProduct) {
        if (gate == MirrorGate.GARMENT && !state.isLoadingProduct && state.effectiveGarmentImage != null) {
            gate = if (state.selectedGarmentSize == null) MirrorGate.SIZE else null
        }
    }
    LaunchedEffect(state.selectedGarmentSize) {
        if (gate == MirrorGate.SIZE && state.selectedGarmentSize != null) gate = null
    }

    if (state.generatedImage != null) {
        MirrorResult(
            state = state,
            language = language,
            onBack = viewModel::returnToStudio,
            onReset = viewModel::reset,
            onOpenAi = onOpenAi,
            onMotion = viewModel::setMotion,
            onVideo = viewModel::generateVideo,
        )
        return
    }

    val hasPerson = state.personImage != null
    val hasGarment = state.effectiveGarmentImage != null
    val needsSize = state.productUrl.isNotBlank() || state.availableGarmentSizes.isNotEmpty()
    val hasSize = state.selectedGarmentSize != null || !needsSize
    val readyCount = listOf(hasPerson, hasGarment, hasSize).count { it }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        MirrorHeader(language = language, readyCount = readyCount)

        MirrorStage(
            modifier = Modifier.weight(1f),
            personImage = state.personImage,
            garmentImage = state.effectiveGarmentImage,
            productTitle = state.productTitle,
            language = language,
            generating = state.isGeneratingImage,
            progress = state.imageProgress,
            onPerson = { gate = MirrorGate.PERSON },
            onGarment = { gate = MirrorGate.GARMENT },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GateCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PersonOutline,
                label = tr(language, "أنت", "You"),
                value = if (hasPerson) tr(language, "جاهز", "Ready") else tr(language, "ابدأ", "Start"),
                ready = hasPerson,
                onClick = { gate = MirrorGate.PERSON },
            )
            GateCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Checkroom,
                label = tr(language, "القطعة", "Garment"),
                value = state.productTitle.ifBlank { if (hasGarment) tr(language, "جاهزة", "Ready") else tr(language, "أضف", "Add") },
                ready = hasGarment,
                onClick = { gate = MirrorGate.GARMENT },
            )
            GateCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Straighten,
                label = tr(language, "المقاس", "Size"),
                value = state.selectedGarmentSize?.label ?: "—",
                ready = hasSize,
                onClick = { gate = MirrorGate.SIZE },
            )
        }

        Button(
            onClick = {
                when {
                    !hasPerson -> gate = MirrorGate.PERSON
                    !hasGarment -> gate = MirrorGate.GARMENT
                    !hasSize -> gate = MirrorGate.SIZE
                    else -> viewModel.generateImage()
                }
            },
            enabled = !state.isGeneratingImage,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    !hasPerson -> tr(language, "عرّف نفسك", "Set yourself")
                    !hasGarment -> tr(language, "أضف القطعة", "Add garment")
                    !hasSize -> tr(language, "اختر المقاس", "Choose size")
                    else -> tr(language, "شغّل المرآة", "Run Mirror")
                },
                fontWeight = FontWeight.Bold,
            )
        }

        when (state.imageError) {
            GenerationError.API_KEY_MISSING -> MirrorError(tr(language, "AI غير مهيأ بعد.", "AI is not configured yet."), tr(language, "فتح AI", "Open AI"), onOpenAi)
            GenerationError.REQUEST_FAILED -> MirrorError(tr(language, "فشل إنشاء النتيجة.", "Generation failed."), tr(language, "فحص AI", "Check AI"), onOpenAi)
            GenerationError.NONE -> Unit
        }
    }

    when (gate) {
        MirrorGate.PERSON -> ModalBottomSheet(onDismissRequest = { gate = null }) {
            GateSheet(
                eyebrow = "01 / YOU",
                title = tr(language, "من في المرآة؟", "Who is in the Mirror?"),
                subtitle = tr(language, "كاميرا، صورة، أو شخصيتك ثلاثية الأبعاد.", "Camera, photo, or your 3D character."),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LargeChoice(Icons.Outlined.AddAPhoto, tr(language, "كاميرا", "Camera"), Modifier.weight(1f)) {
                        cameraUri(context)?.let {
                            pendingCameraUri = it
                            camera.launch(it)
                        }
                    }
                    LargeChoice(Icons.Outlined.PhotoLibrary, tr(language, "المعرض", "Gallery"), Modifier.weight(1f)) {
                        personPicker.launch(arrayOf("image/*"))
                    }
                }
                LargeChoice(Icons.Outlined.PersonOutline, tr(language, "شخصيتي / Digital Twin", "My Avatar / Digital Twin"), Modifier.fillMaxWidth()) {
                    gate = null
                    onOpenAvatar()
                }
            }
        }
        MirrorGate.GARMENT -> ModalBottomSheet(onDismissRequest = { gate = null }) {
            GateSheet(
                eyebrow = "02 / GARMENT",
                title = tr(language, "ما القطعة؟", "What are you trying on?"),
                subtitle = tr(language, "استخدم رابط متجر أو صورة واحدة فقط.", "Use a store link or one garment image."),
            ) {
                if (state.effectiveGarmentImage != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.size(86.dp), RoundedCornerShape(18.dp)) {
                            AsyncImage(state.effectiveGarmentImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(state.productTitle.ifBlank { tr(language, "صورة القطعة", "Garment image") }, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (state.displayProductPrice.isNotBlank()) Text(state.displayProductPrice, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                OutlinedTextField(
                    value = state.productUrl,
                    onValueChange = viewModel::setProductUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(17.dp),
                    placeholder = { Text(tr(language, "الصق رابط المنتج", "Paste product link")) },
                    leadingIcon = { Icon(Icons.Outlined.Link, null) },
                )
                Button(
                    onClick = viewModel::loadProduct,
                    enabled = !state.isLoadingProduct && state.productUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isLoadingProduct) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Link, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr(language, "استيراد", "Import"), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(tr(language, "  أو  ", "  or  "), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f))
                }
                OutlinedButton(
                    onClick = { garmentPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr(language, "رفع صورة", "Upload image"), fontWeight = FontWeight.Bold)
                }
                when (state.productError) {
                    ProductError.EMPTY_URL -> SheetError(tr(language, "ألصق رابطًا أولًا.", "Paste a link first."))
                    ProductError.UNAVAILABLE -> SheetError(tr(language, "هذا الرابط لم يُقرأ.", "This link could not be read."))
                    ProductError.IMAGE_NOT_FOUND -> SheetError(tr(language, "لم نجد صورة مناسبة للمنتج.", "No usable product image was found."))
                    ProductError.NONE -> Unit
                }
            }
        }
        MirrorGate.SIZE -> ModalBottomSheet(onDismissRequest = { gate = null }) {
            GateSheet(
                eyebrow = "03 / SIZE",
                title = tr(language, "المقاس الحقيقي", "The real size"),
                subtitle = tr(language, "اختر نفس مقاس المتجر؛ المرآة لا تغيّر جسمك لتجمّل النتيجة.", "Use the retailer size. Mirror will not reshape your body to flatter the result."),
            ) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableGarmentSizes.forEach { size ->
                        SizeChoice(size.label, state.selectedGarmentSize == size) { viewModel.setGarmentSize(size) }
                    }
                }
                state.fitSimulation?.let { FitTruth(it, language) }
            }
        }
        null -> Unit
    }
}

@Composable
private fun MirrorHeader(language: String, readyCount: Int) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("ALMI / MIRROR", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
            Text(tr(language, "شوفها عليك", "See it on you"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Text("$readyCount / 3", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MirrorStage(
    modifier: Modifier,
    personImage: String?,
    garmentImage: String?,
    productTitle: String,
    language: String,
    generating: Boolean,
    progress: Float,
    onPerson: () -> Unit,
    onGarment: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color(0xFF11100F),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        shadowElevation = 8.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (personImage != null) {
                AsyncImage(personImage, null, Modifier.fillMaxSize().clickable(onClick = onPerson), contentScale = ContentScale.Crop)
            } else {
                Column(
                    modifier = Modifier.align(Alignment.Center).clickable(onClick = onPerson).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = .07f)) {
                        Icon(Icons.Outlined.PersonOutline, null, Modifier.padding(17.dp).size(31.dp), tint = Color.White.copy(alpha = .92f))
                    }
                    Text(tr(language, "ضع نفسك هنا", "Put yourself here"), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(tr(language, "صورة كاملة أو Digital Twin", "Full-body photo or Digital Twin"), color = Color.White.copy(alpha = .48f), style = MaterialTheme.typography.bodySmall)
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(11.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = .50f),
            ) {
                Text("MIRROR / LIVE", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall)
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(11.dp).width(110.dp).height(146.dp).clickable(onClick = onGarment),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF4EEE7),
                border = BorderStroke(2.dp, Color.White.copy(alpha = .18f)),
                shadowElevation = 9.dp,
            ) {
                if (garmentImage != null) {
                    Box {
                        AsyncImage(garmentImage, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (productTitle.isNotBlank()) {
                            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color.Black.copy(alpha = .58f)) {
                                Text(productTitle, Modifier.padding(6.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Checkroom, null, tint = Color(0xFF5B514A))
                        Spacer(Modifier.height(5.dp))
                        Text(tr(language, "القطعة", "Garment"), color = Color(0xFF5B514A), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            AnimatedVisibility(generating, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
                Surface(shape = RoundedCornerShape(22.dp), color = Color.Black.copy(alpha = .82f), border = BorderStroke(1.dp, Color.White.copy(alpha = .10f))) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, color = Color(0xFFF4EEE7), trackColor = Color.White.copy(alpha = .12f))
                        Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(tr(language, "نبني الإطلالة", "Building the fit"), color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun GateCard(modifier: Modifier, icon: ImageVector, label: String, value: String, ready: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(70.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (ready) scheme.surface else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (ready) scheme.tertiary.copy(alpha = .30f) else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(9.dp), Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, Modifier.size(14.dp), tint = if (ready) scheme.tertiary else scheme.onSurfaceVariant)
                Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GateSheet(eyebrow: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(eyebrow, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        content()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LargeChoice(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(68.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) { Icon(icon, null, Modifier.padding(8.dp).size(18.dp)) }
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SizeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(50.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.padding(horizontal = 17.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FitTruth(fit: FitSimulation, language: String) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .72f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${fit.size.label} • ${pressureLabel(fit.overallPressure, language)}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Text(tr(language, "هذا تقدير ضغط المقاس، وليس تغييرًا لشكل جسمك.", "This estimates fit pressure; it does not reshape your body."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MirrorResult(
    state: TryOnUiState,
    language: String,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onOpenAi: () -> Unit,
    onMotion: (MotionDirection) -> Unit,
    onVideo: () -> Unit,
) {
    val generated = state.generatedImage ?: return
    var before by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null) }
                Column {
                    Text("ALMI / MIRROR RESULT", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                    Text(tr(language, "النتيجة", "The fit"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Row {
                IconButton(onClick = onOpenAi) { Icon(Icons.Outlined.Tune, null) }
                IconButton(onClick = onReset) { Icon(Icons.Outlined.Refresh, null) }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(30.dp),
            color = Color(0xFF11100F),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (state.generatedVideo != null && !before) {
                    AndroidView(
                        factory = { ctx -> VideoView(ctx).apply { setOnPreparedListener { media -> media.isLooping = true; start() } } },
                        update = { view ->
                            if (view.tag != state.generatedVideo) {
                                view.tag = state.generatedVideo
                                view.setVideoURI(Uri.parse(state.generatedVideo))
                                view.start()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(if (before) state.personImage else generated, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Surface(Modifier.align(Alignment.TopCenter).padding(10.dp), RoundedCornerShape(999.dp), color = Color.Black.copy(alpha = .56f)) {
                    Row {
                        TextButton(onClick = { before = true }) { Text(tr(language, "قبل", "Before"), color = if (before) Color.White else Color.White.copy(alpha = .45f)) }
                        TextButton(onClick = { before = false }) { Text(tr(language, "بعد", "After"), color = if (!before) Color.White else Color.White.copy(alpha = .45f)) }
                    }
                }
            }
        }

        Surface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.fitSimulation?.let { fit ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(tr(language, "ضغط المقاس", "Fit pressure"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        Text("${fit.size.label} • ${pressureLabel(fit.overallPressure, language)}", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MotionChoice(MotionDirection.TURN, state.motion, tr(language, "دوران", "Turn"), onMotion, Modifier.weight(1f))
                    MotionChoice(MotionDirection.WALK, state.motion, tr(language, "مشي", "Walk"), onMotion, Modifier.weight(1f))
                    MotionChoice(MotionDirection.DETAIL, state.motion, tr(language, "تفاصيل", "Detail"), onMotion, Modifier.weight(1f))
                }
                Button(
                    onClick = onVideo,
                    enabled = !state.isGeneratingVideo && state.generatedVideo == null,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isGeneratingVideo) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.SmartDisplay, null)
                    Spacer(Modifier.width(6.dp))
                    Text(videoLabel(state, language), fontWeight = FontWeight.Bold)
                }
                if (state.videoError) SheetError(tr(language, "فشل إنشاء الفيديو.", "Video generation failed."))
            }
        }
    }
}

@Composable
private fun MotionChoice(value: MotionDirection, current: MotionDirection, label: String, onSelect: (MotionDirection) -> Unit, modifier: Modifier) {
    val active = value == current
    Surface(modifier.height(40.dp).clickable { onSelect(value) }, RoundedCornerShape(13.dp), color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
        Box(contentAlignment = Alignment.Center) { Text(label, color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun MirrorError(text: String, action: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun SheetError(text: String) { Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

private fun pressureLabel(value: FitPressure, language: String): String = when (value) {
    FitPressure.VERY_TIGHT -> tr(language, "شديد الضيق", "Very tight")
    FitPressure.TIGHT -> tr(language, "ضيق", "Tight")
    FitPressure.CLOSE -> tr(language, "ملاصق", "Close")
    FitPressure.REGULAR -> tr(language, "مناسب", "Regular")
    FitPressure.LOOSE -> tr(language, "واسع", "Loose")
    FitPressure.UNKNOWN -> tr(language, "تقديري", "Approximate")
}

private fun videoLabel(state: TryOnUiState, language: String): String = when {
    state.generatedVideo != null -> tr(language, "الفيديو جاهز", "Video ready")
    state.videoStatus == VideoGenerationStatus.SUBMITTING -> tr(language, "إرسال…", "Submitting…")
    state.videoStatus == VideoGenerationStatus.PROCESSING -> tr(language, "معالجة…", "Processing…")
    state.videoStatus == VideoGenerationStatus.DOWNLOADING -> tr(language, "تجهيز…", "Preparing…")
    else -> tr(language, "حرّك الإطلالة", "Animate fit")
}

private fun cameraUri(context: Context): Uri? = runCatching {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "almi_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun persistPermission(context: Context, uri: Uri) {
    runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
