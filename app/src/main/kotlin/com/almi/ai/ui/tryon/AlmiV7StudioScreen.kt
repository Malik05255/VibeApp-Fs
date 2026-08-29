package com.almi.ai.ui.tryon

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** v9 Fit Room — a compact digital changing-room surface over the existing generation pipeline. */
@Composable
fun AlmiV7StudioScreen(
    viewModel: TryOnViewModel,
    language: String,
    onOpenAi: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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

    if (state.generatedImage != null) {
        V9ResultRoom(
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        V9StudioHeader(language = language, state = state)

        Text(
            tr(language, "غرفة القياس الرقمية", "Your digital fitting room"),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            tr(
                language,
                "جهّز جسمك والقطعة والمقاس؛ ALMI يحافظ على شكل جسمك ويعرض ضغط المقاس بدل تزوير النتيجة.",
                "Load your body, garment and store size. ALMI keeps your body shape and shows fit pressure instead of faking a better fit.",
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        RunwayStage(
            personImage = state.personImage,
            garmentImage = state.effectiveGarmentImage,
            language = language,
            onCamera = {
                cameraUri(context)?.let {
                    pendingCameraUri = it
                    camera.launch(it)
                }
            },
            onPerson = { personPicker.launch(arrayOf("image/*")) },
            onGarment = { garmentPicker.launch(arrayOf("image/*")) },
        )

        StoreImportDock(
            state = state,
            language = language,
            onUrlChange = viewModel::setProductUrl,
            onImport = viewModel::loadProduct,
            onUpload = { garmentPicker.launch(arrayOf("image/*")) },
        )

        FitSizeDeck(state = state, language = language, onSize = viewModel::setGarmentSize)

        if (state.isGeneratingImage) {
            GenerationConsole(state = state, language = language)
        } else {
            Button(
                onClick = viewModel::generateImage,
                enabled = state.canGenerate && (state.productUrl.isBlank() || state.selectedGarmentSize != null),
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(generateLabel(state, language), fontWeight = FontWeight.Bold)
            }
        }

        when (state.imageError) {
            GenerationError.API_KEY_MISSING -> ErrorCard(
                text = tr(language, "محرك الذكاء الاصطناعي غير جاهز.", "AI Core is not ready."),
                action = tr(language, "فتح المحرك", "Open AI Core"),
                onClick = onOpenAi,
            )
            GenerationError.REQUEST_FAILED -> ErrorCard(
                text = tr(language, "فشل التوليد. راجع المزوّد أو النموذج ثم أعد المحاولة.", "Generation failed. Review the provider or model and retry."),
                action = tr(language, "فحص المحرك", "Inspect Core"),
                onClick = onOpenAi,
            )
            GenerationError.NONE -> Unit
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                tr(
                    language,
                    "FIT TRUTH • إذا كان المقاس ضيقًا فستظهر النتيجة ضيقه، ولن يغيّر ALMI جسمك لجعل القطعة مناسبة.",
                    "FIT TRUTH • If the size is tight, the result should look tight. ALMI will not reshape your body just to make the garment fit.",
                ),
                modifier = Modifier.padding(13.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun V9StudioHeader(language: String, state: TryOnUiState) {
    val readyCount = listOf(
        state.personImage != null,
        state.effectiveGarmentImage != null,
        state.productUrl.isBlank() || state.selectedGarmentSize != null,
    ).count { it }
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("ALMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("FIT ROOM / V9", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(if (readyCount == 3) Color(0xFF62D8A1) else MaterialTheme.colorScheme.tertiary, CircleShape),
                )
                Text(
                    if (readyCount == 3) tr(language, "جاهز للتوليد", "READY") else "$readyCount / 3",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RunwayStage(
    personImage: String?,
    garmentImage: String?,
    language: String,
    onCamera: () -> Unit,
    onPerson: () -> Unit,
    onGarment: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFF0B0D13),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.fillMaxWidth().height(430.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF151923),
                ) {
                    if (personImage != null) {
                        AsyncImage(
                            model = personImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        EmptyStage(
                            icon = Icons.Outlined.AutoAwesome,
                            title = tr(language, "مرجع الجسم", "BODY REFERENCE"),
                            message = tr(language, "صورة كاملة وواضحة أو الأفاتار المحفوظ", "Use a clear full-body photo or your saved avatar"),
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .width(122.dp)
                        .height(166.dp),
                    shape = RoundedCornerShape(23.dp),
                    color = Color(0xFFF7F4EF),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = .22f)),
                    shadowElevation = 8.dp,
                ) {
                    if (garmentImage != null) {
                        AsyncImage(
                            model = garmentImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Outlined.Checkroom, contentDescription = null, tint = Color(0xFF6B6570))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tr(language, "القطعة", "GARMENT"),
                                color = Color(0xFF6B6570),
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = .52f),
                ) {
                    Text(
                        tr(language, "LIVE STAGE", "LIVE STAGE"),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StageAction(Icons.Outlined.AddAPhoto, tr(language, "كاميرا", "Camera"), onCamera, Modifier.weight(1f))
                StageAction(Icons.Outlined.PhotoLibrary, tr(language, "صورة", "Photo"), onPerson, Modifier.weight(1f))
                StageAction(Icons.Outlined.Checkroom, tr(language, "قطعة", "Garment"), onGarment, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyStage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = Color.White.copy(alpha = .07f)) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(15.dp).size(28.dp), tint = Color.White.copy(alpha = .70f))
        }
        Spacer(Modifier.height(13.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(message, color = Color.White.copy(alpha = .48f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = .08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .09f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp), tint = Color.White)
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun StoreImportDock(
    state: TryOnUiState,
    language: String,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    onUpload: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface.copy(alpha = .94f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(tr(language, "اسحب القطعة من المتجر", "Pull garment from store"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tr(language, "الرابط أو صورة مباشرة", "Product URL or direct image"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.Outlined.Link, contentDescription = null, tint = scheme.tertiary)
            }
            OutlinedTextField(
                value = state.productUrl,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(tr(language, "الصق رابط المنتج", "Paste product URL")) },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImport,
                    enabled = !state.isLoadingProduct,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isLoadingProduct) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Link, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr(language, "استيراد", "Import"))
                }
                OutlinedButton(onClick = onUpload, modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr(language, "رفع", "Upload"))
                }
            }
            if (state.productTitle.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.productTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        if (state.merchant.isNotBlank()) Text(state.merchant, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.displayProductPrice.isNotBlank()) Text(state.displayProductPrice, color = scheme.tertiary, style = MaterialTheme.typography.labelLarge)
                }
            }
            when (state.productError) {
                ProductError.EMPTY_URL -> ErrorLine(tr(language, "أدخل رابطًا أولًا.", "Enter a URL first."))
                ProductError.UNAVAILABLE -> ErrorLine(tr(language, "تعذر قراءة الرابط.", "The URL could not be read."))
                ProductError.IMAGE_NOT_FOUND -> ErrorLine(tr(language, "تمت قراءة المنتج لكن لم نجد صورة مناسبة.", "Product loaded but no suitable image was found."))
                ProductError.NONE -> Unit
            }
        }
    }
}

@Composable
private fun FitSizeDeck(state: TryOnUiState, language: String, onSize: (GarmentSize) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface.copy(alpha = .94f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(tr(language, "اختبار المقاس", "Fit test"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(tr(language, "اختر نفس مقاس المتجر", "Use the exact retailer size"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Surface(shape = CircleShape, color = scheme.tertiaryContainer) {
                    Text(
                        state.selectedGarmentSize?.label ?: "—",
                        modifier = Modifier.padding(12.dp),
                        color = scheme.tertiary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                state.availableGarmentSizes.forEach { size ->
                    SizeChip(size = size, selected = state.selectedGarmentSize == size, onClick = { onSize(size) })
                }
            }
            state.fitSimulation?.let { FitSummary(it, language) }
        }
    }
}

@Composable
private fun SizeChip(size: GarmentSize, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primary else scheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Text(
            size.label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FitSummary(fit: FitSimulation, language: String) {
    val scheme = MaterialTheme.colorScheme
    val pressure = when (fit.overallPressure) {
        FitPressure.VERY_TIGHT -> tr(language, "شديد الضيق", "Very tight")
        FitPressure.TIGHT -> tr(language, "ضيق", "Tight")
        FitPressure.CLOSE -> tr(language, "ملاصق", "Close")
        FitPressure.REGULAR -> tr(language, "اعتيادي", "Regular")
        FitPressure.LOOSE -> tr(language, "واسع", "Loose")
        FitPressure.UNKNOWN -> tr(language, "تقديري", "Approximate")
    }
    val confidence = when (fit.confidence) {
        FitConfidence.HIGH -> tr(language, "ثقة مرتفعة", "High confidence")
        FitConfidence.MEDIUM -> tr(language, "ثقة متوسطة", "Medium confidence")
        FitConfidence.LOW -> tr(language, "بدون جدول موثوق", "No reliable chart")
    }
    Surface(shape = RoundedCornerShape(18.dp), color = scheme.tertiaryContainer.copy(alpha = .70f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("${fit.size.label}  ·  $pressure", fontWeight = FontWeight.Bold)
                Text(confidence, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                if (fit.confidence == FitConfidence.LOW) {
                    tr(language, "حرف المقاس ليس معيارًا عالميًا؛ النتيجة تقريبية حتى يتوفر جدول المتجر.", "Letter sizes are not universal; fit stays approximate until a retailer chart is available.")
                } else {
                    tr(language, "تمت مقارنة بيانات المقاس المتاحة بقياسات جسمك.", "Available size data was compared with your body measurements.")
                },
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GenerationConsole(state: TryOnUiState, language: String) {
    val p = state.imageProgress.coerceIn(0f, 1f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(tr(language, "ALMI يصنع الإطلالة", "ALMI is building the fit"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                Text("${(p * 100).toInt()}%", color = MaterialTheme.colorScheme.onPrimary)
            }
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = .16f),
            )
        }
    }
}

@Composable
private fun V9ResultRoom(
    state: TryOnUiState,
    language: String,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onOpenAi: () -> Unit,
    onMotion: (MotionDirection) -> Unit,
    onVideo: () -> Unit,
) {
    val generated = state.generatedImage ?: return
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("ALMI / FIT RESULT", color = scheme.tertiary, style = MaterialTheme.typography.labelSmall)
                Text(
                    state.selectedGarmentSize?.let { tr(language, "إطلالة ${it.label}", "Size ${it.label} fit") }
                        ?: tr(language, "إطلالتك", "Your fit"),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, contentDescription = null) }
                IconButton(onClick = onOpenAi) { Icon(Icons.Outlined.Tune, contentDescription = null) }
                IconButton(onClick = onReset) { Icon(Icons.Outlined.Refresh, contentDescription = null) }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = Color(0xFF0B0D13),
            border = BorderStroke(1.dp, scheme.outlineVariant),
            shadowElevation = 8.dp,
        ) {
            Box {
                AsyncImage(
                    model = generated,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(.73f),
                    contentScale = ContentScale.Crop,
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = .52f),
                ) {
                    Text("ALMI GENERATED", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        state.fitSimulation?.let { FitSummary(it, language) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = scheme.surface.copy(alpha = .94f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text(tr(language, "حرك الإطلالة", "Animate the fit"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tr(language, "الفيديو مرحلة مستقلة عن الصورة", "Video stays independent from your still result"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Outlined.SmartDisplay, contentDescription = null, tint = scheme.tertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MotionChip(MotionDirection.TURN, state.motion, tr(language, "دوران", "Turn"), onMotion, Modifier.weight(1f))
                    MotionChip(MotionDirection.WALK, state.motion, tr(language, "مشي", "Walk"), onMotion, Modifier.weight(1f))
                    MotionChip(MotionDirection.DETAIL, state.motion, tr(language, "تفاصيل", "Detail"), onMotion, Modifier.weight(1f))
                }
                if (state.generatedVideo == null) {
                    Button(
                        onClick = onVideo,
                        enabled = !state.isGeneratingVideo,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        if (state.isGeneratingVideo) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.SmartDisplay, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(videoLabel(state, language))
                    }
                } else {
                    AndroidView(
                        factory = { ctx -> VideoView(ctx).apply { setOnPreparedListener { media -> media.isLooping = true; start() } } },
                        update = { view ->
                            if (view.tag != state.generatedVideo) {
                                view.tag = state.generatedVideo
                                view.setVideoURI(Uri.parse(state.generatedVideo))
                                view.start()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().aspectRatio(.75f).clip(RoundedCornerShape(20.dp)),
                    )
                }
                if (state.videoError) ErrorLine(tr(language, "تعذر إنشاء الفيديو. راجع إعدادات المزوّد.", "Video generation failed. Review provider settings."))
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun MotionChip(
    value: MotionDirection,
    current: MotionDirection,
    label: String,
    onSelect: (MotionDirection) -> Unit,
    modifier: Modifier,
) {
    val selected = value == current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable { onSelect(value) },
        shape = RoundedCornerShape(15.dp),
        color = if (selected) scheme.primary else scheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = TextAlign.Center,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ErrorCard(text: String, action: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = scheme.errorContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, modifier = Modifier.weight(1f), color = scheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

private fun generateLabel(state: TryOnUiState, language: String): String = when {
    state.personImage == null -> tr(language, "أضف مرجع الجسم", "Add body reference")
    state.effectiveGarmentImage == null -> tr(language, "أضف القطعة", "Add garment")
    state.productUrl.isNotBlank() && state.selectedGarmentSize == null -> tr(language, "اختر المقاس", "Choose a size")
    else -> tr(language, "ابدأ المحاكاة", "Run fit simulation")
}

private fun videoLabel(state: TryOnUiState, language: String): String = when (state.videoStatus) {
    VideoGenerationStatus.IDLE -> tr(language, "إنشاء فيديو", "Create video")
    VideoGenerationStatus.SUBMITTING -> tr(language, "إرسال الطلب…", "Submitting…")
    VideoGenerationStatus.PROCESSING -> tr(language, "معالجة الفيديو…", "Processing…")
    VideoGenerationStatus.DOWNLOADING -> tr(language, "تجهيز الفيديو…", "Preparing…")
}

private fun cameraUri(context: Context): Uri? = runCatching {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "almi_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun persistPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en
