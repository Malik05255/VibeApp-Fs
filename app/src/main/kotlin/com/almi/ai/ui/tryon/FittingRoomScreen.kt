package com.almi.ai.ui.tryon

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
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
import com.almi.ai.ui.components.ConnectionPill
import com.almi.ai.ui.components.DimensionCard
import com.almi.ai.ui.components.Glossy3DIcon
import java.io.File

@Composable
fun FittingRoomScreen(
    viewModel: TryOnViewModel,
    language: String,
    onOpenAi: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val personPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.setPersonImage(it.toString())
        }
    }
    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistReadPermission(context, it)
            viewModel.setGarmentImage(it.toString())
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let { viewModel.setPersonImage(it.toString()) }
    }

    if (state.generatedImage != null) {
        ResultRoom(
            state = state,
            language = language,
            onBack = viewModel::returnToStudio,
            onMotion = viewModel::setMotion,
            onVideo = viewModel::generateVideo,
            onReset = viewModel::reset,
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeHeader(language)

        FittingStage(
            state = state,
            language = language,
            onCamera = {
                createCameraUri(context)?.let {
                    pendingCameraUri = it
                    cameraLauncher.launch(it)
                }
            },
            onGallery = { personPicker.launch(arrayOf("image/*")) },
            onGarment = { garmentPicker.launch(arrayOf("image/*")) },
        )

        ProductSource(
            state = state,
            language = language,
            onUrlChanged = viewModel::setProductUrl,
            onRead = viewModel::loadProduct,
            onUpload = { garmentPicker.launch(arrayOf("image/*")) },
        )

        GeneratePanel(
            state = state,
            language = language,
            onGenerate = viewModel::generateImage,
            onOpenAi = onOpenAi,
        )

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun HomeHeader(language: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ALMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(
                if (language == "ar") "Fitting Room" else "Fitting Room",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ConnectionPill(if (language == "ar") "الذكاء جاهز" else "AI ready")
    }
}

@Composable
private fun FittingStage(
    state: TryOnUiState,
    language: String,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onGarment: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (language == "ar") "جرّب اللوك عليك" else "Try the look on you",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )

        DimensionCard(emphasized = true) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.88f)
                    .padding(12.dp)
            ) {
                if (state.personImage != null) {
                    AsyncImage(
                        model = state.personImage,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    EmptyPersonStage(language)
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FloatingAction(Icons.Outlined.PhotoCamera, onCamera)
                    FloatingAction(Icons.Outlined.PhotoLibrary, onGallery)
                }

                GarmentBubble(
                    image = state.effectiveGarmentImage,
                    language = language,
                    onClick = onGarment,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                )

                if (state.personImage != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text(if (language == "ar") "صورتك" else "You", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPersonStage(language: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(124.dp)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Glossy3DIcon(Icons.Outlined.PhotoCamera, active = true)
            }
            Text(
                if (language == "ar") "أضف صورتك" else "Add your photo",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun FloatingAction(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 8.dp,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = null)
        }
    }
}

@Composable
private fun GarmentBubble(
    image: String?,
    language: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                if (image != null) {
                    if (language == "ar") "القطعة جاهزة" else "Item ready"
                } else {
                    if (language == "ar") "اختر القطعة" else "Choose item"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProductSource(
    state: TryOnUiState,
    language: String,
    onUrlChanged: (String) -> Unit,
    onRead: () -> Unit,
    onUpload: () -> Unit,
) {
    DimensionCard {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.productUrl,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (language == "ar") "رابط المنتج" else "Product link") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRead,
                    enabled = !state.isLoadingProduct,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isLoadingProduct) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Link, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ar") "قراءة" else "Read")
                }
                OutlinedButton(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == "ar") "رفع صورة" else "Upload")
                }
            }

            if (state.productTitle.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(state.productTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.merchant.isNotBlank()) {
                            Text(state.merchant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.displayProductPrice.isNotBlank()) {
                        Text(state.displayProductPrice, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            ProductErrorText(state.productError, language)
        }
    }
}

@Composable
private fun ProductErrorText(error: ProductError, language: String) {
    val text = when (error) {
        ProductError.EMPTY_URL -> if (language == "ar") "أدخل رابط المنتج" else "Enter a product link"
        ProductError.UNAVAILABLE -> if (language == "ar") "تعذر قراءة الرابط" else "Could not read the link"
        ProductError.IMAGE_NOT_FOUND -> if (language == "ar") "لم أجد صورة مناسبة" else "No usable image found"
        ProductError.NONE -> null
    } ?: return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun GeneratePanel(
    state: TryOnUiState,
    language: String,
    onGenerate: () -> Unit,
    onOpenAi: () -> Unit,
) {
    DimensionCard(emphasized = state.canGenerate || state.isGeneratingImage) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isGeneratingImage) {
                val percent = (state.imageProgress.coerceIn(0f, 1f) * 100f).toInt()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (language == "ar") "نبني اللوك" else "Creating look", fontWeight = FontWeight.Bold)
                    Text("$percent%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(progress = { state.imageProgress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            } else {
                Button(
                    onClick = onGenerate,
                    enabled = state.canGenerate,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (language == "ar") "جرّبها عليك" else "Try it on", fontWeight = FontWeight.Bold)
                }
            }

            when (state.imageError) {
                GenerationError.API_KEY_MISSING -> TextButtonLine(
                    text = if (language == "ar") "إعداد الذكاء مطلوب" else "AI setup required",
                    onClick = onOpenAi,
                )
                GenerationError.REQUEST_FAILED -> Text(
                    if (language == "ar") "فشل التوليد. جرّب نموذجًا آخر." else "Generation failed. Try another model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                GenerationError.NONE -> Unit
            }
        }
    }
}

@Composable
private fun TextButtonLine(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Text(text, modifier = Modifier.fillMaxWidth().padding(10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun ResultRoom(
    state: TryOnUiState,
    language: String,
    onBack: () -> Unit,
    onMotion: (MotionDirection) -> Unit,
    onVideo: () -> Unit,
    onReset: () -> Unit,
) {
    val image = state.generatedImage ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("ALMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(if (language == "ar") "النتيجة" else "Result", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onBack) { Text(if (language == "ar") "تعديل" else "Edit") }
        }

        DimensionCard(emphasized = true) {
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionButton(MotionDirection.TURN, state.motion, if (language == "ar") "دوران" else "Turn", onMotion, Modifier.weight(1f))
            MotionButton(MotionDirection.WALK, state.motion, if (language == "ar") "مشي" else "Walk", onMotion, Modifier.weight(1f))
            MotionButton(MotionDirection.DETAIL, state.motion, if (language == "ar") "تفاصيل" else "Detail", onMotion, Modifier.weight(1f))
        }

        Button(
            onClick = onVideo,
            enabled = !state.isGeneratingVideo,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            if (state.isGeneratingVideo) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(videoStatusText(state.videoStatus, language))
            } else {
                Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (language == "ar") "حوّلها إلى فيديو" else "Create video")
            }
        }

        state.generatedVideo?.let { VideoCard(it, language) }

        if (state.videoError) {
            Text(if (language == "ar") "تعذر إنشاء الفيديو" else "Video generation failed", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (language == "ar") "تجربة جديدة" else "New try-on")
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MotionButton(
    direction: MotionDirection,
    selected: MotionDirection,
    label: String,
    onClick: (MotionDirection) -> Unit,
    modifier: Modifier,
) {
    if (direction == selected) {
        Button(onClick = { onClick(direction) }, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = { onClick(direction) }, modifier = modifier, shape = RoundedCornerShape(14.dp)) { Text(label) }
    }
}

private fun videoStatusText(status: VideoGenerationStatus, language: String): String = when (status) {
    VideoGenerationStatus.SUBMITTING -> if (language == "ar") "إرسال" else "Submitting"
    VideoGenerationStatus.PROCESSING -> if (language == "ar") "معالجة" else "Processing"
    VideoGenerationStatus.DOWNLOADING -> if (language == "ar") "تنزيل" else "Downloading"
    VideoGenerationStatus.IDLE -> if (language == "ar") "فيديو" else "Video"
}

@Composable
private fun VideoCard(uri: String, language: String) {
    DimensionCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (language == "ar") "الفيديو جاهز" else "Video ready", fontWeight = FontWeight.Bold)
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
                modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(22.dp)),
            )
        }
    }
}

private fun createCameraUri(context: Context): Uri? = runCatching {
    val directory = File(context.filesDir, "tryon_camera").apply { mkdirs() }
    val file = File(directory, "person_${System.currentTimeMillis()}.jpg")
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.getOrNull()

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
