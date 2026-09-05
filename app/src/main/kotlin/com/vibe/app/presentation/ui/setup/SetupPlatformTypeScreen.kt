package com.vibe.app.presentation.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vibe.app.R
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.ai.FreeAiProviderPreset

data class PlatformTypeInfo(
    val key: String,
    val clientType: ClientType? = null,
    val preset: FreeAiProviderPreset? = null,
    val titleResId: Int,
    val descriptionResId: Int,
    val icon: ImageVector,
)

private val platformTypes = listOf(
    PlatformTypeInfo(
        key = "openrouter",
        clientType = ClientType.OPEN_ROUTER,
        titleResId = R.string.openrouter,
        descriptionResId = R.string.openrouter_description,
        icon = Icons.Outlined.CloudQueue,
    ),
    PlatformTypeInfo(
        key = "gemini",
        clientType = ClientType.GOOGLE_AI_STUDIO,
        titleResId = R.string.google_ai_studio,
        descriptionResId = R.string.google_ai_studio_description,
        icon = Icons.Outlined.AutoAwesome,
    ),
    PlatformTypeInfo(
        key = FreeAiProviderPreset.GROQ.code,
        preset = FreeAiProviderPreset.GROQ,
        titleResId = R.string.groq,
        descriptionResId = R.string.groq_description,
        icon = Icons.Outlined.AutoAwesome,
    ),
    PlatformTypeInfo(
        key = FreeAiProviderPreset.MISTRAL.code,
        preset = FreeAiProviderPreset.MISTRAL,
        titleResId = R.string.mistral_ai,
        descriptionResId = R.string.mistral_ai_description,
        icon = Icons.Outlined.CloudQueue,
    ),
    PlatformTypeInfo(
        key = FreeAiProviderPreset.CLOUDFLARE.code,
        preset = FreeAiProviderPreset.CLOUDFLARE,
        titleResId = R.string.cloudflare_workers_ai,
        descriptionResId = R.string.cloudflare_workers_ai_description,
        icon = Icons.Outlined.CloudQueue,
    ),
    PlatformTypeInfo(
        key = "custom",
        clientType = ClientType.CUSTOM,
        titleResId = R.string.custom_api,
        descriptionResId = R.string.custom_api_description,
        icon = Icons.Outlined.Tune,
    ),
)

@Composable
fun SetupPlatformTypeScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModelV2 = hiltViewModel(),
    onPlatformTypeSelected: () -> Unit,
    onBackAction: () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SetupAppBar(onBackAction) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            PlatformTypeHeader()

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = platformTypes,
                    key = { it.key },
                ) { platformTypeInfo ->
                    PlatformTypeCard(
                        platformTypeInfo = platformTypeInfo,
                        onClick = {
                            when {
                                platformTypeInfo.preset != null ->
                                    setupViewModel.selectProviderPreset(platformTypeInfo.preset)

                                platformTypeInfo.clientType != null ->
                                    setupViewModel.selectClientType(platformTypeInfo.clientType)
                            }
                            onPlatformTypeSelected()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformTypeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.choose_platform_type),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.choose_platform_type_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlatformTypeCard(
    platformTypeInfo: PlatformTypeInfo,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = platformTypeInfo.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(platformTypeInfo.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(platformTypeInfo.descriptionResId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
