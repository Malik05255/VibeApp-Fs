package com.almi.ai.ui.tryon

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoCameraBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.almi.ai.data.repository.MotionDirection
import com.almi.ai.data.repository.VideoGenerationStatus
import java.io.File

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
            persistPermissionV7(context, it)
            viewModel.setPersonImage(it.toString())
        }
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistPermissionV7(context, it)
            viewModel.setGarmentImage(it.toString())
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.setPersonImage(it.toString()) }
    }

    if (state.generatedImage != null) {
        V7ResultScreen(
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        V7StudioHeader(language)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                trStudio(language, "ابنِ الإطلالة قبل أن تلبسها", "Build the look before you wear it"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                trStudio(
                    language,
                    "صورتك + القطعة. الذكاء الاصطناعي يتولى الباقي.",
                    "Your photo + the garment. AI handles the rest.",
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        V7StudioCanvas(
            state = state,
            language = language,
            onCamera = {
                createCameraUriV7(context)?.let {
                    pendingCameraUri = it
                    cameraLauncher.launch(it)
                }
            },
            onPersonGallery = { personPicker.launch(arrayOf("image/*")) },
            onGarmentGallery = { garmentPicker.launch(arrayOf("image/*")) },
        )

        ProductComposer(
            state = state,
            language = language,
            onUrlChanged = viewModel::setProductUrl,
            onImport = viewModel::loadProduct,
            onUpload = { garmentPicker.launch(arrayOf("image/*")) },
        )

        if (state.isGeneratingImage) {
            GenerationProgress(state, language)
        } else {
            Button(
                onClick = viewModel::generateImage,
                enabled = state.canGenerate,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.size(9.dp))
                Text(
                    when {
                        state.personImage == null -> trStudio(language, "أضف صورتك أولًا", "Add your photo first")
                        state.effectiveGarmentImage == null -> trStudio(language, "أضف القطعة", "Add a garment")
                        else -> trStudio(language, "أنشئ إطلالتي", "Create my look")
                    },
                    fontWeight = FontWeight.Black,
                )
            }
        }

        when (state.imageError) {
            GenerationError.API_KEY_MISSING -> StudioError(
                text = trStudio(language, "يحتاج مركز الذكاء الاصطناعي إلى الإعداد.", "AI Center needs setup."),
                action = trStudio(language, "فتح مركز AI", "Open AI Center"),
                onClick = onOpenAi,
            )
            GenerationError.REQUEST_FAILED -> StudioError(
                text = trStudio(language, "فشل إنشاء الإطلالة. راجع النموذج أو المزوّد.", "Look generation failed. Review the model or provider."),
                action = trStudio(language, "إعدادات AI", "AI settings"),
                onClick = onOpenAi,
            )
            GenerationError.NONE -> Unit
        }

        Text(
            trStudio(
                language,
                "صورك لا تُرسل إلى خدمة التوليد إلا عند الضغط على إنشاء إطلالتي.",
                "Your images are not sent to generation until you tap Create my look.",
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun V7StudioHeader(language: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("ALMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("AI FIT STUDIO / V7", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text(trStudio(language, "جاهز", "Ready"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun V7StudioCanvas(
    state: TryOnUiState,
    language: String,
    onCamera: () -> Unit,
    onPersonGallery: () -> Unit,
    onGarmentGallery: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ImageSlot(
                    label = trStudio(language, "أنت", "YOU"),
                    image = state.personImage,
                    emptyTitle = trStudio(language, "أضف صورتك", "Add your photo"),
                    modifier = Modifier.weight(1f).height(360.dp),
                )
                ImageSlot(
                    label = trStudio(language, "القطعة", "GARMENT"),
                    image = state.effectiveGarmentImage,
                    emptyTitle = trStudio(language, "أضف القطعة", "Add garment"),
                    modifier = Modifier.weight(0.62f).height(360.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onCamera, modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(trStudio(language, "كاميرا", "Camera"))
                }
                OutlinedButton(onClick = onPersonGallery, modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(trStudio(language, "صورتي", "My photo"))
                }
                OutlinedButton(onClick = onGarmentGallery, modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Outlined.Checkroom, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(trStudio(language, "قطعة", "Garment"))
                }
            }
        }
    }
}

@Composable
private fun ImageSlot(
    label: String,
    image: String?,
    emptyTitle: String,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = scheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            color = scheme.surfaceVariant.copy(alpha = 0.58f),
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                        Text(emptyTitle, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductComposer(
    state: TryOnUiState,
    language: String,
    onUrlChanged: (String) -> Unit,
    onImport: () -> Unit,
    onUpload: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(trStudio(language, "استيراد قطعة من المتجر", "Import from a store"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            OutlinedTextField(
                value = state.productUrl,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(trStudio(language, "الصق رابط المنتج", "Paste product URL")) },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onImport,
                    enabled = !state.isLoadingProduct,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (state.isLoadingProduct) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Link, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(trStudio(language, "استخراج", "Import"), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onUpload, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(trStudio(language, "رفع صورة", "Upload image"), fontWeight = FontWeight.Bold)
                }
            }

            AnimatedVisibility(visible = state.productTitle.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.productTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                        if (state.merchant.isNotBlank()) Text(state.merchant, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                    if (state.displayProductPrice.isNotBlank()) {
                        Text(state.displayProductPrice, style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.Black)
                    }
                }
            }

            when (state.productError) {
                ProductError.EMPTY_URL -> ErrorLine(trStudio(language, "أدخل رابط المنتج.", "Enter a product URL."))
                ProductError.UNAVAILABLE -> ErrorLine(trStudio(language, "تعذر قراءة هذا الرابط.", "This URL could not be read."))
                ProductError.IMAGE_NOT_FOUND -> ErrorLine(trStudio(language, "تمت قراءة المنتج لكن لم نجد صورة مناسبة.", "Product loaded but no suitable image was found."))
                ProductError.NONE -> Unit
            }
        }
    }
}

@Composable
private fun GenerationProgress(state: TryOnUiState, language: String) {
    val percent = (state.imageProgress.coerceIn(0f, 1f) * 100).toInt()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trStudio(language, "ALMI يبني الإطلالة", "ALMI is building the look"), fontWeight = FontWeight.Black)
                Text("$percent%", fontWeight = FontWeight.Black)
            }
            LinearProgressIndicator(
                progress = { state.imageProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape),
            )
            Text(
                trStudio(language, "مطابقة الجسم والقطعة • الحفاظ على الهوية • تحسين النتيجة", "Body/garment fit • identity preservation • result refinement"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StudioError(text: String, action: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun V7ResultScreen(
    state: TryOnUiState,
    language: String,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onOpenAi: () -> Unit,
    onMotion: (MotionDirection) -> Unit,
    onVideo: () -> Unit,
) {
    val generated = state.generatedImage ?: return
    var before by remember(generated) { mutableStateOf(false) }
    val shown = if (before) state.personImage ?: generated else generated

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ALMI / RESULT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                Text(trStudio(language, "إطلالتك جاهزة", "Your look is ready"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CircleAction(Icons.Outlined.Close, onBack)
                CircleAction(Icons.Outlined.Tune, onOpenAi)
                CircleAction(Icons.Outlined.Refresh, onReset)
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box {
                AsyncImage(
                    model = shown,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    contentScale = ContentScale.Crop,
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    onClick = { before = !before },
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.56f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Compare, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Text(
                            if (before) trStudio(language, "قبل", "Before") else trStudio(language, "بعد", "After"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(trStudio(language, "حوّل الصورة إلى حركة", "Bring the look to life"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MotionChip(MotionDirection.TURN, state.motion, trStudio(language, "دوران", "Turn"), onMotion, Modifier.weight(1f))
                    MotionChip(MotionDirection.WALK, state.motion, trStudio(language, "مشي", "Walk"), onMotion, Modifier.weight(1f))
                    MotionChip(MotionDirection.DETAIL, state.motion, trStudio(language, "تفاصيل", "Detail"), onMotion, Modifier.weight(1f))
                }
                Button(
                    onClick = onVideo,
                    enabled = !state.isGeneratingVideo,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (state.isGeneratingVideo) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(videoStatus(state.videoStatus, language))
                    } else {
                        Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(trStudio(language, "إنشاء فيديو", "Create video"), fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        state.generatedVideo?.let { V7Video(it, language) }
        if (state.videoError) ErrorLine(trStudio(language, "تعذر إنشاء الفيديو.", "Video generation failed."))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CircleAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(46.dp)) {
            Icon(icon, contentDescription = null)
        }
    }
}

@Composable
private fun MotionChip(
    direction: MotionDirection,
    selected: MotionDirection,
    label: String,
    onClick: (MotionDirection) -> Unit,
    modifier: Modifier,
) {
    if (direction == selected) {
        Button(onClick = { onClick(direction) }, modifier = modifier.height(46.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = { onClick(direction) }, modifier = modifier.height(46.dp)) { Text(label) }
    }
}

@Composable
private fun V7Video(uri: String, language: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trStudio(language, "الفيديو جاهز", "Video ready"), fontWeight = FontWeight.Black)
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.parse(uri))
                        setOnPreparedListener { player -> player.isLooping = true; start() }
                    }
                },
                update = { view ->
                    if (view.tag != uri) {
                        view.tag = uri
                        view.setVideoURI(Uri.parse(uri))
                        view.start()
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp)),
            )
        }
    }
}

private fun videoStatus(status: VideoGenerationStatus, language: String): String = when (status) {
    VideoGenerationStatus.SUBMITTING -> trStudio(language, "إرسال", "Submitting")
    VideoGenerationStatus.PROCESSING -> trStudio(language, "معالجة", "Processing")
    VideoGenerationStatus.DOWNLOADING -> trStudio(language, "تنزيل", "Downloading")
    VideoGenerationStatus.IDLE -> trStudio(language, "فيديو", "Video")
}

private fun createCameraUriV7(context: Context): Uri? = runCatching {
    val directory = File(context.filesDir, "tryon_camera").apply { mkdirs() }
    val file = File(directory, "person_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun persistPermissionV7(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun trStudio(language: String, ar: String, en: String): String = if (language == "ar") ar else en
