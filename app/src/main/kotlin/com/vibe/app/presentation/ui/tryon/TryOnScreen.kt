package com.vibe.app.presentation.ui.tryon

import android.content.Context
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryOnScreen(
    onOpenSettings: () -> Unit,
    onOpenProjects: () -> Unit,
    viewModel: TryOnViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val personPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.onPersonImageSelected(it.toString()) }
    }

    val garmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.onGarmentImageSelected(it.toString()) }
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
            IntroCard()
            StageIndicator(stage = uiState.stage)

            PersonPhotoSection(
                imageUri = uiState.personImageUri,
                onCameraClick = {
                    runCatching { createCameraUri(context) }
                        .onSuccess { uri ->
                            pendingCameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                },
                onGalleryClick = { personPicker.launch("image/*") },
            )

            ProductSection(
                uiState = uiState,
                onUrlChanged = viewModel::onProductUrlChanged,
                onFetchProduct = viewModel::loadProductPreview,
                onPickGarment = { garmentPicker.launch("image/*") },
            )

            MotionSection(
                selected = uiState.motionPreset,
                onSelected = viewModel::onMotionPresetSelected,
            )

            PrepareSection(
                canPrepare = uiState.canPrepare,
                onPrepare = viewModel::preparePrototype,
            )

            if (uiState.prototypePrepared) {
                ResultSection(
                    uiState = uiState,
                    onReset = viewModel::reset,
                )
            }

            PrivacyCard()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
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
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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
private fun StageIndicator(stage: TryOnStage) {
    val activeIndex = when (stage) {
        TryOnStage.PERSON -> 0
        TryOnStage.PRODUCT -> 1
        TryOnStage.REVIEW, TryOnStage.RESULT -> 2
    }
    val labels = listOf(
        stringResource(R.string.tryon_step_person),
        stringResource(R.string.tryon_step_product),
        stringResource(R.string.tryon_step_review),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index <= activeIndex
            Surface(
                modifier = Modifier.weight(1f),
                color = if (active) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (active) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = "${index + 1} · $label",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PersonPhotoSection(
    imageUri: String?,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    SectionCard(
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
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.tryon_person_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onCameraClick,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_camera))
            }
            OutlinedButton(
                onClick = onGalleryClick,
                modifier = Modifier.weight(1f),
            ) {
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
private fun ProductSection(
    uiState: TryOnUiState,
    onUrlChanged: (String) -> Unit,
    onFetchProduct: () -> Unit,
    onPickGarment: () -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.tryon_product_title),
        subtitle = stringResource(R.string.tryon_product_hint),
    ) {
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
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
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
            ProductPreviewCard(uiState)
        }

        OutlinedButton(
            onClick = onPickGarment,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tryon_manual_image))
        }
    }
}

@Composable
private fun ProductPreviewCard(uiState: TryOnUiState) {
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
                model = uiState.effectiveGarmentImage,
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.tryon_product_preview),
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
}

@Composable
private fun MotionSection(
    selected: MotionPreset,
    onSelected: (MotionPreset) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.tryon_review_title),
        subtitle = stringResource(R.string.tryon_review_hint),
    ) {
        MotionOption(
            label = stringResource(R.string.tryon_motion_turn),
            selected = selected == MotionPreset.TURN,
            onClick = { onSelected(MotionPreset.TURN) },
        )
        MotionOption(
            label = stringResource(R.string.tryon_motion_walk),
            selected = selected == MotionPreset.WALK,
            onClick = { onSelected(MotionPreset.WALK) },
        )
        MotionOption(
            label = stringResource(R.string.tryon_motion_detail),
            selected = selected == MotionPreset.DETAIL,
            onClick = { onSelected(MotionPreset.DETAIL) },
        )
    }
}

@Composable
private fun MotionOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val buttonModifier = Modifier.fillMaxWidth()
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = buttonModifier) {
            Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier) {
            Icon(Icons.Outlined.VideoCameraBack, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PrepareSection(
    canPrepare: Boolean,
    onPrepare: () -> Unit,
) {
    Button(
        onClick = onPrepare,
        enabled = canPrepare,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.tryon_prepare),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    if (!canPrepare) {
        Text(
            text = stringResource(R.string.tryon_missing_inputs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultSection(
    uiState: TryOnUiState,
    onReset: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
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
                PreviewTile(
                    image = uiState.personImageUri,
                    modifier = Modifier.weight(1f),
                )
                PreviewTile(
                    image = uiState.effectiveGarmentImage,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = stringResource(R.string.tryon_result_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            StatusLine(stringResource(R.string.tryon_result_person), ready = true)
            StatusLine(stringResource(R.string.tryon_result_garment), ready = true)
            StatusLine(stringResource(R.string.tryon_result_motion), ready = true)
            StatusLine(stringResource(R.string.tryon_result_ai), ready = false)

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tryon_start_over))
            }
        }
    }
}

@Composable
private fun PreviewTile(
    image: String?,
    modifier: Modifier = Modifier,
) {
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
private fun StatusLine(
    text: String,
    ready: Boolean,
) {
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
private fun PrivacyCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
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
private fun SectionCard(
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

private fun createCameraUri(context: Context): Uri {
    val directory = File(context.filesDir, "tryon_photos").apply { mkdirs() }
    val file = File(directory, "person_${System.currentTimeMillis()}.jpg").apply { createNewFile() }
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}
