package com.vibe.app.presentation.ui.tryon

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibe.app.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnAiScreen(
    onOpenSettings: () -> Unit,
    onOpenProjects: () -> Unit,
    viewModel: TryOnViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val personPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            persistAiReadPermission(context, it)
            viewModel.onPersonImageSelected(it.toString())
        }
    }

    val garmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            persistAiReadPermission(context, it)
            viewModel.onGarmentImageSelected(it.toString())
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            pendingCameraUri?.let { viewModel.onPersonImageSelected(it.toString()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.tryon_ai_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.tryon_ai_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenProjects) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = stringResource(R.string.tryon_projects),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.tryon_settings),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AiHeroCard()

            AiPersonSection(
                imageUri = uiState.personImageUri,
                onCameraClick = {
                    runCatching { createAiCameraUri(context) }
                        .onSuccess { uri ->
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                },
                onGalleryClick = { personPicker.launch(arrayOf("image/*")) },
            )

            AiGarmentSection(
                uiState = uiState,
                onUrlChanged = viewModel::onProductUrlChanged,
                onFetchProduct = viewModel::loadProductPreview,
                onPickGarment = { garmentPicker.launch(arrayOf("image/*")) },
                onCategorySelected = viewModel::onCategorySelected,
                onAddToOutfit = viewModel::addCurrentGarmentToOutfit,
            )

            AiOutfitSection(
                garments = uiState.activeGarments,
                committedGarmentIds = uiState.outfitGarments.map { it.id }.toSet(),
                onRemove = viewModel::removeOutfitGarment,
            )

            AiMotionSection(
                selected = uiState.motionPreset,
                onSelected = viewModel::onMotionPresetSelected,
            )

            Button(
                onClick = viewModel::generateTryOn,
                enabled = uiState.canPrepare && !uiState.isGeneratingImage && !uiState.isGeneratingVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (uiState.isGeneratingImage) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.tryon_ai_generating))
                } else {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.tryon_ai_generate),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (!uiState.canPrepare) {
                Text(
                    text = stringResource(R.string.tryon_missing_inputs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (
                uiState.stage == TryOnStage.RESULT ||
                uiState.isGeneratingImage ||
                uiState.imageGenerationError != null ||
                uiState.generatedImageUri != null
            ) {
                AiGenerationResultSection(
                    uiState = uiState,
                    onGenerateAgain = viewModel::generateTryOn,
                    onGenerateVideo = viewModel::generateVideo,
                    onOpenSettings = onOpenSettings,
                    onReset = viewModel::reset,
                )
            }

            AiAccuracyCard()
            AiPrivacyCard()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AiHeroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.tryon_ai_intro_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.tryon_ai_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AiPersonSection(
    imageUri: String?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    AiSectionCard(
        title = stringResource(R.string.tryon_ai_person_title),
        subtitle = stringResource(R.string.tryon_ai_person_body),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(stringResource(R.string.tryon_person_empty))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onCameraClick, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_camera), maxLines = 1)
            }
            OutlinedButton(onClick = onGalleryClick, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (imageUri == null) {
                        stringResource(R.string.tryon_gallery)
                    } else {
                        stringResource(R.string.tryon_replace_photo)
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AiGarmentSection(
    uiState: TryOnUiState,
    onUrlChanged: (String) -> Unit,
    onFetchProduct: () -> Unit,
    onPickGarment: () -> Unit,
    onCategorySelected: (GarmentCategory) -> Unit,
    onAddToOutfit: () -> Unit,
) {
    AiSectionCard(
        title = stringResource(R.string.tryon_ai_garment_title),
        subtitle = stringResource(R.string.tryon_ai_garment_body),
    ) {
        Text(
            text = stringResource(R.string.tryon_category_title),
            style = MaterialTheme.typography.labelLarge,
        )
        AiCategoryRow(
            categories = listOf(GarmentCategory.TOP, GarmentCategory.BOTTOM, GarmentCategory.OUTERWEAR),
            selected = uiState.selectedCategory,
            onSelected = onCategorySelected,
        )
        AiCategoryRow(
            categories = listOf(GarmentCategory.SHOES, GarmentCategory.ACCESSORY),
            selected = uiState.selectedCategory,
            onSelected = onCategorySelected,
        )

        OutlinedTextField(
            value = uiState.productUrl,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.tryon_product_url_label)) },
            placeholder = { Text(stringResource(R.string.tryon_product_url_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        Button(
            onClick = onFetchProduct,
            enabled = !uiState.isLoadingProduct,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (uiState.isLoadingProduct) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_ai_product_fetching))
            } else {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_ai_product_fetch))
            }
        }

        uiState.productError?.let { error ->
            Text(
                text = when (error) {
                    ProductLoadError.EMPTY_URL -> stringResource(R.string.tryon_url_empty)
                    ProductLoadError.UNAVAILABLE -> stringResource(R.string.tryon_url_unavailable)
                    ProductLoadError.IMAGE_NOT_FOUND -> stringResource(R.string.tryon_image_not_found)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        uiState.effectiveGarmentImage?.let { image ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = aiCategoryLabel(uiState.selectedCategory),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = uiState.productTitle.ifBlank {
                                stringResource(R.string.tryon_product_unknown)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (uiState.merchant.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.tryon_source, uiState.merchant),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Button(onClick = onAddToOutfit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_ai_add_garment))
            }
        }

        OutlinedButton(onClick = onPickGarment, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tryon_ai_upload_garment))
        }
    }
}

@Composable
private fun AiCategoryRow(
    categories: List<GarmentCategory>,
    selected: GarmentCategory,
    onSelected: (GarmentCategory) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            if (category == selected) {
                FilledTonalButton(
                    onClick = { onSelected(category) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = aiCategoryLabel(category),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { onSelected(category) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = aiCategoryLabel(category),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiOutfitSection(
    garments: List<OutfitGarment>,
    committedGarmentIds: Set<String>,
    onRemove: (String) -> Unit,
) {
    AiSectionCard(
        title = stringResource(R.string.tryon_ai_outfit_title),
        subtitle = stringResource(R.string.tryon_ai_outfit_empty),
    ) {
        if (garments.isEmpty()) {
            Text(
                text = stringResource(R.string.tryon_ai_outfit_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            garments.forEach { garment ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = garment.image,
                            contentDescription = null,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = garment.title.ifBlank {
                                    stringResource(R.string.tryon_product_unknown)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = aiCategoryLabel(garment.category),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (committedGarmentIds.contains(garment.id)) {
                            IconButton(onClick = { onRemove(garment.id) }) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.tryon_ai_remove),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMotionSection(
    selected: MotionPreset,
    onSelected: (MotionPreset) -> Unit,
) {
    AiSectionCard(
        title = stringResource(R.string.tryon_ai_motion_title),
        subtitle = stringResource(R.string.tryon_ai_motion_body),
    ) {
        MotionPreset.entries.forEach { preset ->
            val label = when (preset) {
                MotionPreset.TURN -> stringResource(R.string.tryon_motion_turn)
                MotionPreset.WALK -> stringResource(R.string.tryon_motion_walk)
                MotionPreset.DETAIL -> stringResource(R.string.tryon_motion_detail)
            }
            if (selected == preset) {
                FilledTonalButton(
                    onClick = { onSelected(preset) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelected(preset) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AiGenerationResultSection(
    uiState: TryOnUiState,
    onGenerateAgain: () -> Unit,
    onGenerateVideo: () -> Unit,
    onOpenSettings: () -> Unit,
    onReset: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.tryon_ai_result_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            if (uiState.isGeneratingImage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tryon_ai_generating),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.tryon_ai_generating_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            uiState.imageGenerationError?.let { error ->
                AiErrorBox(
                    error = error,
                    onOpenSettings = onOpenSettings,
                )
            }

            uiState.generatedImageUri?.let { generatedImage ->
                AsyncImage(
                    model = generatedImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = stringResource(R.string.tryon_ai_result_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                uiState.generatedImageModel?.let { model ->
                    Text(
                        text = stringResource(R.string.tryon_ai_model_used, model),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                uiState.generatedImageCostUsd?.let { cost ->
                    Text(
                        text = stringResource(R.string.tryon_ai_cost, cost),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onGenerateAgain,
                        enabled = !uiState.isGeneratingVideo,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tryon_ai_regenerate), maxLines = 1)
                    }
                    Button(
                        onClick = onGenerateVideo,
                        enabled = !uiState.isGeneratingVideo,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tryon_ai_video_generate), maxLines = 1)
                    }
                }
            }

            if (uiState.isGeneratingVideo) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    Text(
                        text = when (uiState.videoGenerationStatus) {
                            MediaGenerationStatus.SUBMITTING -> stringResource(R.string.tryon_ai_video_submitting)
                            MediaGenerationStatus.DOWNLOADING -> stringResource(R.string.tryon_ai_video_downloading)
                            else -> stringResource(R.string.tryon_ai_video_processing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            uiState.videoGenerationError?.let { error ->
                AiErrorBox(
                    error = error,
                    onOpenSettings = onOpenSettings,
                )
            }

            uiState.generatedVideoUri?.let { videoUri ->
                Text(
                    text = stringResource(R.string.tryon_ai_video_ready),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                key(videoUri) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(20.dp)),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val controller = MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(Uri.parse(videoUri))
                                setOnPreparedListener { player ->
                                    player.isLooping = true
                                    start()
                                }
                            }
                        },
                    )
                }
                uiState.generatedVideoModel?.let { model ->
                    Text(
                        text = stringResource(R.string.tryon_ai_model_used, model),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                uiState.generatedVideoCostUsd?.let { cost ->
                    Text(
                        text = stringResource(R.string.tryon_ai_cost, cost),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_ai_start_over))
            }
        }
    }
}

@Composable
private fun AiErrorBox(
    error: String,
    onOpenSettings: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.tryon_ai_error_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_ai_open_settings))
            }
        }
    }
}

@Composable
private fun AiAccuracyCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.tryon_ai_accuracy_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tryon_ai_accuracy_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiPrivacyCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(R.string.tryon_ai_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.tryon_ai_privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AiSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun aiCategoryLabel(category: GarmentCategory): String = when (category) {
    GarmentCategory.TOP -> stringResource(R.string.tryon_category_top)
    GarmentCategory.BOTTOM -> stringResource(R.string.tryon_category_bottom)
    GarmentCategory.OUTERWEAR -> stringResource(R.string.tryon_category_outerwear)
    GarmentCategory.SHOES -> stringResource(R.string.tryon_category_shoes)
    GarmentCategory.ACCESSORY -> stringResource(R.string.tryon_category_accessory)
}

private fun persistAiReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun createAiCameraUri(context: Context): Uri {
    val directory = File(context.filesDir, "tryon_photos").apply { mkdirs() }
    val file = File(directory, "person_${System.currentTimeMillis()}.jpg").apply { createNewFile() }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
