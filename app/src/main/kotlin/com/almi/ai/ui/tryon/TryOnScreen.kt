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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
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
import java.io.File

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ALMI_AI", fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeroCard()
            StepHeader(number = "1", title = stringResource(R.string.person_title), subtitle = stringResource(R.string.person_hint))
            ImageInputCard(
                image = state.personImage,
                emptyText = stringResource(R.string.person_empty),
                primaryText = stringResource(R.string.camera),
                secondaryText = stringResource(R.string.gallery),
                onPrimary = {
                    createCameraUri(context)?.let {
                        pendingCameraUri = it
                        cameraLauncher.launch(it)
                    }
                },
                onSecondary = { personPicker.launch(arrayOf("image/*")) },
            )

            StepHeader(number = "2", title = stringResource(R.string.product_title), subtitle = stringResource(R.string.product_hint))
            ProductInputCard(
                state = state,
                onUrlChanged = viewModel::setProductUrl,
                onLoad = viewModel::loadProduct,
                onPickImage = { garmentPicker.launch(arrayOf("image/*")) },
            )

            StepHeader(number = "3", title = stringResource(R.string.generate_title), subtitle = stringResource(R.string.generate_hint))
            Button(
                onClick = viewModel::generateImage,
                enabled = state.canGenerate,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (state.isGeneratingImage) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.generating_image))
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.generate_action), fontWeight = FontWeight.SemiBold)
                }
            }

            when (state.imageError) {
                GenerationError.API_KEY_MISSING -> ErrorCard(
                    text = stringResource(R.string.error_api_key),
                    action = stringResource(R.string.open_settings),
                    onAction = onOpenSettings,
                )
                GenerationError.REQUEST_FAILED -> ErrorCard(text = stringResource(R.string.error_generation))
                else -> Unit
            }

            state.generatedImage?.let { generated ->
                ResultCard(
                    image = generated,
                    state = state,
                    onMotionChanged = viewModel::setMotion,
                    onGenerateVideo = viewModel::generateVideo,
                )
            }

            if (state.videoError) {
                ErrorCard(text = stringResource(R.string.error_video))
            }

            state.generatedVideo?.let { VideoResultCard(it) }

            if (state.personImage != null || state.effectiveGarmentImage != null || state.generatedImage != null) {
                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start_over))
                }
            }

            PrivacyCard()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun HeroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                Text(
                    stringResource(R.string.ai_tryon_badge),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(stringResource(R.string.hero_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.hero_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepHeader(number: String, title: String, subtitle: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary) {
            Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                Text(number, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImageInputCard(
    image: String?,
    emptyText: String,
    primaryText: String,
    secondaryText: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreviewBox(image = image, emptyText = emptyText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPrimary, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(primaryText, maxLines = 1)
                }
                OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(secondaryText, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ProductInputCard(
    state: TryOnUiState,
    onUrlChanged: (String) -> Unit,
    onLoad: () -> Unit,
    onPickImage: () -> Unit,
) {
    OutlinedCard(shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.productUrl,
                onValueChange = onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.product_url)) },
                placeholder = { Text("https://…") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                singleLine = true,
            )
            Button(onClick = onLoad, enabled = !state.isLoadingProduct, modifier = Modifier.fillMaxWidth()) {
                if (state.isLoadingProduct) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.product_loading))
                } else {
                    Text(stringResource(R.string.product_fetch))
                }
            }

            when (state.productError) {
                ProductError.EMPTY_URL -> Text(stringResource(R.string.error_empty_url), color = MaterialTheme.colorScheme.error)
                ProductError.UNAVAILABLE -> Text(stringResource(R.string.error_product_unavailable), color = MaterialTheme.colorScheme.error)
                ProductError.IMAGE_NOT_FOUND -> Text(stringResource(R.string.error_product_image), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }

            if (state.effectiveGarmentImage != null) {
                PreviewBox(state.effectiveGarmentImage, stringResource(R.string.product_empty))
                if (state.productTitle.isNotBlank()) {
                    Text(state.productTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (state.merchant.isNotBlank()) {
                    Text(state.merchant, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.product_upload))
            }
        }
    }
}

@Composable
private fun PreviewBox(image: String?, emptyText: String) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.dp))
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
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(38.dp))
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ResultCard(
    image: String,
    state: TryOnUiState,
    onMotionChanged: (MotionDirection) -> Unit,
    onGenerateVideo: () -> Unit,
) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            AsyncImage(
                model = image,
                contentDescription = stringResource(R.string.result_title),
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(stringResource(R.string.result_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.video_motion), fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MotionButton(MotionDirection.TURN, state.motion, stringResource(R.string.motion_turn), onMotionChanged, Modifier.weight(1f))
                MotionButton(MotionDirection.WALK, state.motion, stringResource(R.string.motion_walk), onMotionChanged, Modifier.weight(1f))
                MotionButton(MotionDirection.DETAIL, state.motion, stringResource(R.string.motion_detail), onMotionChanged, Modifier.weight(1f))
            }
            Button(
                onClick = onGenerateVideo,
                enabled = !state.isGeneratingVideo,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (state.isGeneratingVideo) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(videoStatusText(state.videoStatus))
                } else {
                    Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.generate_video))
                }
            }
        }
    }
}

@Composable
private fun MotionButton(
    direction: MotionDirection,
    selected: MotionDirection,
    text: String,
    onClick: (MotionDirection) -> Unit,
    modifier: Modifier,
) {
    if (direction == selected) {
        FilledTonalButton(onClick = { onClick(direction) }, modifier = modifier) { Text(text, maxLines = 1) }
    } else {
        OutlinedButton(onClick = { onClick(direction) }, modifier = modifier) { Text(text, maxLines = 1) }
    }
}

@Composable
private fun videoStatusText(status: VideoGenerationStatus): String = when (status) {
    VideoGenerationStatus.SUBMITTING -> stringResource(R.string.video_submitting)
    VideoGenerationStatus.PROCESSING -> stringResource(R.string.video_processing)
    VideoGenerationStatus.DOWNLOADING -> stringResource(R.string.video_downloading)
    else -> stringResource(R.string.generate_video)
}

@Composable
private fun VideoResultCard(uri: String) {
    Card(shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.video_result_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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

@Composable
private fun ErrorCard(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            if (action != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
