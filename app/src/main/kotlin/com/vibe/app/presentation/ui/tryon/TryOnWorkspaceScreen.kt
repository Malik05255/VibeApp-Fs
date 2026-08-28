package com.vibe.app.presentation.ui.tryon

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.outlined.Save
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
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vibe.app.R
import com.vibe.app.data.model.SavedTryOnGarment
import com.vibe.app.data.model.SavedTryOnHistory
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnWorkspaceScreen(
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
            persistReadPermission(context, it)
            viewModel.onPersonImageSelected(it.toString())
        }
    }

    val garmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            persistReadPermission(context, it)
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
                            text = stringResource(R.string.tryon_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.tryon_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenProjects) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = stringResource(R.string.tryon_projects))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.tryon_settings))
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
            WorkspaceIntroCard()

            WorkspacePersonSection(
                imageUri = uiState.personImageUri,
                onCameraClick = {
                    runCatching { createWorkspaceCameraUri(context) }
                        .onSuccess { uri ->
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                },
                onGalleryClick = { personPicker.launch(arrayOf("image/*")) },
            )

            WorkspaceGarmentSection(
                uiState = uiState,
                onUrlChanged = viewModel::onProductUrlChanged,
                onFetchProduct = viewModel::loadProductPreview,
                onPickGarment = { garmentPicker.launch(arrayOf("image/*")) },
                onCategorySelected = viewModel::onCategorySelected,
                onAddToOutfit = viewModel::addCurrentGarmentToOutfit,
                onSaveToWardrobe = viewModel::saveCurrentGarmentToWardrobe,
            )

            WorkspaceOutfitSection(
                garments = uiState.outfitGarments,
                hasCandidate = uiState.candidateGarment != null,
                onRemove = viewModel::removeOutfitGarment,
            )

            WorkspaceWardrobeSection(
                items = uiState.wardrobe,
                onUse = viewModel::useWardrobeGarment,
                onRemove = viewModel::removeWardrobeGarment,
            )

            WorkspaceMotionSection(
                selected = uiState.motionPreset,
                onSelected = viewModel::onMotionPresetSelected,
            )

            Button(
                onClick = viewModel::preparePrototype,
                enabled = uiState.canPrepare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.tryon_prepare), style = MaterialTheme.typography.titleMedium)
            }
            if (!uiState.canPrepare) {
                Text(
                    text = stringResource(R.string.tryon_missing_inputs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (uiState.prototypePrepared) {
                WorkspaceResultSection(
                    uiState = uiState,
                    onReset = viewModel::reset,
                )
            }

            WorkspaceHistorySection(
                items = uiState.history,
                onRestore = viewModel::restoreHistory,
                onClear = viewModel::clearHistory,
            )

            WorkspacePrivacyCard()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WorkspaceIntroCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = stringResource(R.string.tryon_mvp_badge),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                text = stringResource(R.string.tryon_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun WorkspacePersonSection(
    imageUri: String?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    WorkspaceSectionCard(
        title = stringResource(R.string.tryon_person_title),
        subtitle = stringResource(R.string.tryon_person_hint),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(18.dp))
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
                    Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(44.dp))
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
                Text(stringResource(R.string.tryon_camera))
            }
            OutlinedButton(onClick = onGalleryClick, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (imageUri == null) stringResource(R.string.tryon_gallery)
                    else stringResource(R.string.tryon_replace_photo),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceGarmentSection(
    uiState: TryOnUiState,
    onUrlChanged: (String) -> Unit,
    onFetchProduct: () -> Unit,
    onPickGarment: () -> Unit,
    onCategorySelected: (GarmentCategory) -> Unit,
    onAddToOutfit: () -> Unit,
    onSaveToWardrobe: () -> Unit,
) {
    WorkspaceSectionCard(
        title = stringResource(R.string.tryon_product_title),
        subtitle = stringResource(R.string.tryon_product_hint),
    ) {
        Text(
            text = stringResource(R.string.tryon_category_title),
            style = MaterialTheme.typography.labelLarge,
        )
        CategoryRow(
            categories = listOf(GarmentCategory.TOP, GarmentCategory.BOTTOM, GarmentCategory.OUTERWEAR),
            selected = uiState.selectedCategory,
            onSelected = onCategorySelected,
        )
        CategoryRow(
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
                Text(stringResource(R.string.tryon_fetching_product))
            } else {
                Icon(Icons.Outlined.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_fetch_product))
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

        if (uiState.effectiveGarmentImage != null) {
            WorkspaceGarmentPreview(
                image = uiState.effectiveGarmentImage,
                title = uiState.productTitle,
                merchant = uiState.merchant,
                category = uiState.selectedCategory,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onAddToOutfit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tryon_add_to_outfit), maxLines = 1)
                }
                OutlinedButton(onClick = onSaveToWardrobe, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tryon_save_wardrobe), maxLines = 1)
                }
            }
        }

        OutlinedButton(onClick = onPickGarment, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tryon_manual_image))
        }
    }
}

@Composable
private fun CategoryRow(
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
                    Text(categoryLabel(category), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelected(category) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(categoryLabel(category), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceOutfitSection(
    garments: List<OutfitGarment>,
    hasCandidate: Boolean,
    onRemove: (String) -> Unit,
) {
    WorkspaceSectionCard(
        title = stringResource(R.string.tryon_outfit_title),
        subtitle = stringResource(R.string.tryon_outfit_hint),
    ) {
        if (garments.isEmpty()) {
            Text(
                text = stringResource(R.string.tryon_outfit_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (hasCandidate) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        } else {
            garments.forEach { garment ->
                CompactGarmentRow(
                    image = garment.image,
                    title = garment.title,
                    category = garment.category,
                    trailing = {
                        IconButton(onClick = { onRemove(garment.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.tryon_remove_item))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceWardrobeSection(
    items: List<SavedTryOnGarment>,
    onUse: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    WorkspaceSectionCard(
        title = stringResource(R.string.tryon_wardrobe_title),
        subtitle = stringResource(R.string.tryon_wardrobe_hint),
    ) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.tryon_wardrobe_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.take(6).forEach { item ->
                CompactGarmentRow(
                    image = item.image,
                    title = item.title,
                    category = runCatching { GarmentCategory.valueOf(item.category) }.getOrDefault(GarmentCategory.TOP),
                    trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            OutlinedButton(onClick = { onUse(item.id) }) {
                                Text(stringResource(R.string.tryon_use_item))
                            }
                            IconButton(onClick = { onRemove(item.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.tryon_remove_item))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WorkspaceMotionSection(
    selected: MotionPreset,
    onSelected: (MotionPreset) -> Unit,
) {
    WorkspaceSectionCard(
        title = stringResource(R.string.tryon_review_title),
        subtitle = stringResource(R.string.tryon_review_hint),
    ) {
        MotionPreset.entries.forEach { preset ->
            val label = when (preset) {
                MotionPreset.TURN -> stringResource(R.string.tryon_motion_turn)
                MotionPreset.WALK -> stringResource(R.string.tryon_motion_walk)
                MotionPreset.DETAIL -> stringResource(R.string.tryon_motion_detail)
            }
            if (preset == selected) {
                FilledTonalButton(onClick = { onSelected(preset) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                }
            } else {
                OutlinedButton(onClick = { onSelected(preset) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceResultSection(
    uiState: TryOnUiState,
    onReset: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.tryon_result_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WorkspacePreviewTile(uiState.personImageUri, Modifier.weight(1f))
                WorkspacePreviewTile(uiState.activeGarments.firstOrNull()?.image, Modifier.weight(1f))
            }
            Text(stringResource(R.string.tryon_result_body), style = MaterialTheme.typography.bodyMedium)
            ResultStatus(stringResource(R.string.tryon_result_person), true)
            ResultStatus(stringResource(R.string.tryon_result_garment), true)
            ResultStatus(stringResource(R.string.tryon_result_motion), true)
            ResultStatus(stringResource(R.string.tryon_result_ai), false)
            OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_start_over))
            }
        }
    }
}

@Composable
private fun WorkspaceHistorySection(
    items: List<SavedTryOnHistory>,
    onRestore: (String) -> Unit,
    onClear: () -> Unit,
) {
    WorkspaceSectionCard(
        title = stringResource(R.string.tryon_history_title),
        subtitle = stringResource(R.string.tryon_history_hint),
    ) {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.tryon_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.take(5).forEach { item ->
                OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = item.personImage,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.garmentTitles.filter { it.isNotBlank() }.joinToString(" + ")
                                    .ifBlank { stringResource(R.string.tryon_product_unknown) },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(item.createdAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { onRestore(item.id) }) {
                            Text(stringResource(R.string.tryon_restore))
                        }
                    }
                }
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_clear_history))
            }
        }
    }
}

@Composable
private fun CompactGarmentRow(
    image: String,
    title: String,
    category: GarmentCategory,
    trailing: @Composable () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { stringResource(R.string.tryon_product_unknown) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = categoryLabel(category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            trailing()
        }
    }
}

@Composable
private fun WorkspaceGarmentPreview(
    image: String?,
    title: String,
    merchant: String,
    category: GarmentCategory,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = image,
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(categoryLabel(category), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = title.ifBlank { stringResource(R.string.tryon_product_unknown) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (merchant.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.tryon_source, merchant),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspacePreviewTile(image: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = image,
        contentDescription = null,
        modifier = modifier
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun ResultStatus(text: String, ready: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WorkspacePrivacyCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.tryon_privacy_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.tryon_privacy_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WorkspaceSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
private fun categoryLabel(category: GarmentCategory): String = when (category) {
    GarmentCategory.TOP -> stringResource(R.string.tryon_category_top)
    GarmentCategory.BOTTOM -> stringResource(R.string.tryon_category_bottom)
    GarmentCategory.OUTERWEAR -> stringResource(R.string.tryon_category_outerwear)
    GarmentCategory.SHOES -> stringResource(R.string.tryon_category_shoes)
    GarmentCategory.ACCESSORY -> stringResource(R.string.tryon_category_accessory)
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun createWorkspaceCameraUri(context: Context): Uri {
    val directory = File(context.filesDir, "tryon_photos").apply { mkdirs() }
    val file = File(directory, "person_${System.currentTimeMillis()}.jpg").apply { createNewFile() }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
