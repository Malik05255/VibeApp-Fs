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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.almi.ai.ui.components.AlmiWordmark
import com.almi.ai.ui.components.GlassSurface
import com.almi.ai.ui.components.LuxeBackdrop
import com.almi.ai.ui.components.LuxeBottomBar
import com.almi.ai.ui.components.LuxeNavDestination
import com.almi.ai.ui.components.StatusPill
import java.io.File
import kotlin.math.roundToInt

@Composable
fun TryOnScreen(
    viewModel: TryOnViewModel,
    onOpenSettings: () -> Unit,
    onOpenAiSettings: () -> Unit,
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
            onMotionChanged = viewModel::setMotion,
            onGenerateVideo = viewModel::generateVideo,
            onNewLook = viewModel::reset,
            onHome = viewModel::returnToStudio,
            onOpenAiSettings = onOpenAiSettings,
            onOpenSettings = onOpenSettings,
        )
        return
    }

    LuxeBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { LuxeTopHeader() },
            bottomBar = {
                StudioBottomDock(
                    state = state,
                    onGenerate = viewModel::generateImage,
                    onHome = {},
                    onOpenAiSettings = onOpenAiSettings,
                    onOpenSettings = onOpenSettings,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = stringResource(R.string.studio_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = stringResource(R.string.studio_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LookStage(
                    state = state,
                    onCamera = {
                        createCameraUri(context)?.let {
                            pendingCameraUri = it
                            cameraLauncher.launch(it)
                        }
                    },
                    onGallery = { personPicker.launch(arrayOf("image/*")) },
                )

                ProductSourceCard(
                    state = state,
                    onUrlChanged = viewModel::setProductUrl,
                    onReadLink = viewModel::loadProduct,
                    onUpload = { garmentPicker.launch(arrayOf("image/*")) },
                )

                PrivacyNote()
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LuxeTopHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AlmiWordmark(compact = true)
        StatusPill(stringResource(R.string.luxe_ai_ready))
    }
}

@Composable
private fun LookStage(
    state: TryOnUiState,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.luxe_stage_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.luxe_stage_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.personImage != null && state.effectiveGarmentImage != null) {
                    StatusPill(stringResource(R.string.studio_create_hint_ready))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(365.dp),
            ) {
                StagePerson(
                    image = state.personImage,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd),
                )

                StagePiece(
                    image = state.effectiveGarmentImage,
                    modifier = Modifier
                        .width(138.dp)
                        .height(170.dp)
                        .align(Alignment.BottomStart),
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shadowElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StageAction(Icons.Outlined.PhotoCamera, stringResource(R.string.studio_camera), onCamera)
                        StageAction(Icons.Outlined.PhotoLibrary, stringResource(R.string.studio_gallery), onGallery)
                    }
                }
            }
        }
    }
}

@Composable
private fun StagePerson(
    image: String?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f)),
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
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Text(stringResource(R.string.luxe_person_ready), style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.padding(18.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(stringResource(R.string.studio_add_photo), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun StagePiece(
    image: String?,
    modifier: Modifier,
) {
    GlassSurface(modifier = modifier, shape = RoundedCornerShape(24.dp)) {
        Box(Modifier.fillMaxSize().padding(7.dp), contentAlignment = Alignment.Center) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop,
                )
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.91f),
                ) {
                    Text(
                        stringResource(R.string.luxe_piece_ready),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.studio_product_title), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StageAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.width(114.dp).height(42.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun ProductSourceCard(
    state: TryOnUiState,
    onUrlChanged: (String) -> Unit,
    onReadLink: () -> Unit,
    onUpload: () -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.luxe_product_source), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.luxe_product_source_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onReadLink,
                    enabled = !state.isLoadingProduct,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (state.isLoadingProduct) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.studio_scanning_link), maxLines = 1)
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.studio_scan_link), maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = onUpload,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.studio_upload_piece), maxLines = 1)
                }
            }

            ProductErrorMessage(state.productError)

            if (state.productTitle.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.productTitle,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
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
                            Text(state.displayProductPrice, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                stringResource(R.string.studio_private_note),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun StudioBottomDock(
    state: TryOnUiState,
    onGenerate: () -> Unit,
    onHome: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        if (state.isGeneratingImage) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                emphasized = true,
            ) {
                GenerationProgress(state.imageProgress)
            }
        } else {
            GlassSurface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                emphasized = state.canGenerate,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.canGenerate) stringResource(R.string.luxe_ready_title) else stringResource(R.string.studio_create_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                when {
                                    state.personImage == null -> stringResource(R.string.studio_create_hint_person)
                                    state.effectiveGarmentImage == null -> stringResource(R.string.studio_create_hint_product)
                                    else -> stringResource(R.string.luxe_ready_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(
                            onClick = onGenerate,
                            enabled = state.canGenerate,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.studio_create_action))
                        }
                    }
                    when (state.imageError) {
                        GenerationError.API_KEY_MISSING -> CompactError(stringResource(R.string.studio_error_key), onOpenSettings)
                        GenerationError.REQUEST_FAILED -> CompactError(stringResource(R.string.studio_error_generation))
                        GenerationError.NONE -> Unit
                    }
                }
            }
        }

        LuxeBottomBar(
            selected = LuxeNavDestination.HOME,
            homeLabel = stringResource(R.string.luxe_nav_home),
            aiLabel = stringResource(R.string.luxe_nav_ai),
            settingsLabel = stringResource(R.string.luxe_nav_settings),
            onHome = onHome,
            onAi = onOpenAiSettings,
            onSettings = onOpenSettings,
        )
    }
}

@Composable
private fun GenerationProgress(progress: Float) {
    val normalized = progress.coerceIn(0f, 1f)
    val percent = (normalized * 100f).roundToInt().coerceIn(0, 100)
    Column(
        modifier = Modifier.padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.studio_creating), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.studio_generation_percent, percent),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(normalized)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.tertiary),
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
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (action != null) {
                FilledTonalButton(onClick = action, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                    Text(stringResource(R.string.luxe_open))
                }
            }
        }
    }
}

@Composable
private fun ResultExperience(
    state: TryOnUiState,
    onMotionChanged: (MotionDirection) -> Unit,
    onGenerateVideo: () -> Unit,
    onNewLook: () -> Unit,
    onHome: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val image = state.generatedImage ?: return
    LuxeBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { LuxeTopHeader() },
            bottomBar = {
                LuxeBottomBar(
                    selected = LuxeNavDestination.HOME,
                    homeLabel = stringResource(R.string.luxe_nav_home),
                    aiLabel = stringResource(R.string.luxe_nav_ai),
                    settingsLabel = stringResource(R.string.luxe_nav_settings),
                    onHome = onHome,
                    onAi = onOpenAiSettings,
                    onSettings = onOpenSettings,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.luxe_result_title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.luxe_result_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                GlassSurface(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(8.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(27.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                GlassSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text(stringResource(R.string.result_motion_title), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            MotionChoice(MotionDirection.TURN, state.motion, stringResource(R.string.result_motion_turn), onMotionChanged, Modifier.weight(1f))
                            MotionChoice(MotionDirection.WALK, state.motion, stringResource(R.string.result_motion_walk), onMotionChanged, Modifier.weight(1f))
                            MotionChoice(MotionDirection.DETAIL, state.motion, stringResource(R.string.result_motion_detail), onMotionChanged, Modifier.weight(1f))
                        }
                        Button(
                            onClick = onGenerateVideo,
                            enabled = !state.isGeneratingVideo,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
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
                    }
                }

                if (state.videoError) CompactError(stringResource(R.string.studio_error_video))
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
                Spacer(Modifier.height(10.dp))
            }
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
        FilledTonalButton(onClick = { onClick(direction) }, modifier = modifier, shape = RoundedCornerShape(15.dp)) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick = { onClick(direction) }, modifier = modifier, shape = RoundedCornerShape(15.dp)) {
            Text(label, maxLines = 1)
        }
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
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
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
