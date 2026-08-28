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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.almi.ai.R
import com.almi.ai.data.repository.MotionDirection
import com.almi.ai.data.repository.VideoGenerationStatus
import com.almi.ai.ui.components.AlmiBrandMark
import com.almi.ai.ui.components.AlmiWordmark
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnScreen(
    viewModel: TryOnViewModel,
    onOpenSettings: () -> Unit,
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
        ResultExperience(
            state = state,
            onSettings = onOpenSettings,
            onMotionChanged = viewModel::setMotion,
            onGenerateVideo = viewModel::generateVideo,
            onNewLook = viewModel::reset,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { AlmiWordmark(compact = true) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.studio_settings))
                    }
                },
            )
        },
        bottomBar = {
            CreateDock(
                state = state,
                onGenerate = viewModel::generateImage,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StudioIntro()

            StudioSectionHeader(
                eyebrow = stringResource(R.string.studio_you),
                title = stringResource(R.string.studio_person_title),
                subtitle = stringResource(R.string.studio_person_hint),
                ready = state.personImage != null,
            )

            PersonCard(
                image = state.personImage,
                onCamera = {
                    createCameraUri(context)?.let {
                        pendingCameraUri = it
                        cameraLauncher.launch(it)
                    }
                },
                onGallery = { personPicker.launch(arrayOf("image/*")) },
            )

            StudioSectionHeader(
                eyebrow = stringResource(R.string.studio_look),
                title = stringResource(R.string.studio_product_title),
                subtitle = stringResource(R.string.studio_product_hint),
                ready = state.effectiveGarmentImage != null,
            )

            ProductCard(
                state = state,
                onUrlChanged = viewModel::setProductUrl,
                onReadLink = viewModel::loadProduct,
                onUpload = { garmentPicker.launch(arrayOf("image/*")) },
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AlmiBrandMark(size = 32.dp)
                    Text(
                        stringResource(R.string.studio_private_note),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StudioIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = stringResource(R.string.studio_eyebrow),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.studio_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = stringResource(R.string.studio_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StudioSectionHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    ready: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (ready) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (ready) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                } else {
                    Text(eyebrow.take(1), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonCard(
    image: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MediaPreview(
                image = image,
                emptyTitle = stringResource(R.string.studio_add_photo),
                readyLabel = stringResource(R.string.studio_photo_ready),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCamera, modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.studio_camera))
                }
                OutlinedButton(onClick = onGallery, modifier = Modifier.weight(1f).height(50.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.studio_gallery))
                }
            }
        }
    }
}

@Composable
private fun ProductCard(
    state: TryOnUiState,
    onUrlChanged: (String) -> Unit,
    onReadLink: () -> Unit,
    onUpload: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(28.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.productUrl,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.studio_product_url)) },
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )

            Button(
                onClick = onReadLink,
                enabled = !state.isLoadingProduct,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.isLoadingProduct) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.studio_scanning_link))
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.studio_scan_link))
                }
            }

            ProductErrorMessage(state.productError)

            state.effectiveGarmentImage?.let { image ->
                MediaPreview(
                    image = image,
                    emptyTitle = "",
                    readyLabel = stringResource(R.string.studio_piece_ready),
                )
            }

            if (state.productImage != null && state.productTitle.isNotBlank()) {
                ProductSummary(state)
            }

            OutlinedButton(
                onClick = onUpload,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.studio_upload_piece))
            }
        }
    }
}

@Composable
private fun ProductSummary(state: TryOnUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        state.productTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.merchant.isNotBlank()) {
                        Text(
                            state.merchant,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state.displayProductPrice.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            state.displayProductPrice,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (state.productDescription.isNotBlank()) {
                Text(
                    state.productDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            MetadataLine(stringResource(R.string.studio_brand), state.productBrand)
            MetadataLine(stringResource(R.string.studio_color), state.productColor)
            MetadataLine(stringResource(R.string.studio_sku), state.productSku)
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MediaPreview(
    image: String?,
    emptyTitle: String,
    readyLabel: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(readyLabel, style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(emptyTitle, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ProductErrorMessage(error: ProductError) {
    val text = when (error) {
        ProductError.EMPTY_URL -> stringResource(R.string.studio_error_empty_url)
        ProductError.UNAVAILABLE -> stringResource(R.string.studio_error_product)
        ProductError.IMAGE_NOT_FOUND -> stringResource(R.string.studio_error_product_image)
        ProductError.NONE -> null
    } ?: return

    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun CreateDock(
    state: TryOnUiState,
    onGenerate: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.isGeneratingImage) {
                GenerationProgress(state.imageProgress)
            } else {
                val hint = when {
                    state.personImage == null -> stringResource(R.string.studio_create_hint_person)
                    state.effectiveGarmentImage == null -> stringResource(R.string.studio_create_hint_product)
                    else -> stringResource(R.string.studio_create_hint_ready)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.studio_create_title), style = MaterialTheme.typography.titleMedium)
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onGenerate,
                        enabled = state.canGenerate,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(52.dp),
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.studio_create_action))
                    }
                }
            }

            when (state.imageError) {
                GenerationError.API_KEY_MISSING -> CompactError(
                    text = stringResource(R.string.studio_error_key),
                    action = onOpenSettings,
                )
                GenerationError.REQUEST_FAILED -> CompactError(text = stringResource(R.string.studio_error_generation))
                GenerationError.NONE -> Unit
            }
        }
    }
}

@Composable
private fun GenerationProgress(progress: Float) {
    val normalized = progress.coerceIn(0f, 1f)
    val percent = (normalized * 100f).roundToInt().coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.studio_creating), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.studio_generation_percent, percent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(normalized)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun CompactError(text: String, action: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            if (action != null) {
                IconButton(onClick = action, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultExperience(
    state: TryOnUiState,
    onSettings: () -> Unit,
    onMotionChanged: (MotionDirection) -> Unit,
    onGenerateVideo: () -> Unit,
    onNewLook: () -> Unit,
) {
    val image = state.generatedImage ?: return
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { AlmiWordmark(compact = true) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.result_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.result_eyebrow), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.result_title_new), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.result_subtitle_new), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Card(shape = RoundedCornerShape(30.dp)) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                    contentScale = ContentScale.Crop,
                )
            }

            Text(stringResource(R.string.result_motion_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotionChoice(MotionDirection.TURN, state.motion, stringResource(R.string.result_motion_turn), onMotionChanged, Modifier.weight(1f))
                MotionChoice(MotionDirection.WALK, state.motion, stringResource(R.string.result_motion_walk), onMotionChanged, Modifier.weight(1f))
                MotionChoice(MotionDirection.DETAIL, state.motion, stringResource(R.string.result_motion_detail), onMotionChanged, Modifier.weight(1f))
            }

            Button(
                onClick = onGenerateVideo,
                enabled = !state.isGeneratingVideo,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (state.isGeneratingVideo) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(videoStatusText(state.videoStatus))
                } else {
                    Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.result_generate_video))
                }
            }

            if (state.videoError) {
                CompactError(stringResource(R.string.studio_error_video))
            }

            state.generatedVideo?.let { VideoResultCard(it) }

            OutlinedButton(
                onClick = onNewLook,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_new_look))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MotionChoice(
    direction: MotionDirection,
    selected: MotionDirection,
    label: String,
    onClick: (MotionDirection) -> Unit,
    modifier: Modifier,
) {
    if (direction == selected) {
        FilledTonalButton(onClick = { onClick(direction) }, modifier = modifier) { Text(label, maxLines = 1) }
    } else {
        OutlinedButton(onClick = { onClick(direction) }, modifier = modifier) { Text(label, maxLines = 1) }
    }
}

@Composable
private fun videoStatusText(status: VideoGenerationStatus): String = when (status) {
    VideoGenerationStatus.SUBMITTING -> stringResource(R.string.result_video_submitting)
    VideoGenerationStatus.PROCESSING -> stringResource(R.string.result_video_processing)
    VideoGenerationStatus.DOWNLOADING -> stringResource(R.string.result_video_downloading)
    VideoGenerationStatus.IDLE -> stringResource(R.string.result_generate_video)
}

@Composable
private fun VideoResultCard(uri: String) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.result_video_ready), style = MaterialTheme.typography.titleMedium)
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.parse(uri))
                        setOnPreparedListener { player ->
                            player.isLooping = true
                            start()
                        }
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
