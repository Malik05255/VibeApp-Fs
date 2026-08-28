package com.almi.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.R
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.repository.DiscoveredProvider
import com.almi.ai.data.repository.ProviderDiscoveryRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeAiDiscoveryScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenOpenRouter: () -> Unit,
    onOpenCustom: () -> Unit,
) {
    val mode by viewModel.aiMode.collectAsState()
    val state by viewModel.providerDiscoveryState.collectAsState()
    val enabled = mode == AiMode.FREE_AUTO

    LaunchedEffect(Unit) { viewModel.discoverFreeProviders() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.free_ai_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
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
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text(
                stringResource(R.string.free_ai_heading),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.free_ai_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.free_ai_switch_title), fontWeight = FontWeight.Bold)
                        Text(
                            if (enabled) stringResource(R.string.free_ai_enabled) else stringResource(R.string.free_ai_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = viewModel::setFreeMode)
                }
            }

            Button(
                onClick = viewModel::discoverFreeProviders,
                enabled = !state.isChecking,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                if (state.isChecking) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.free_ai_searching))
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.free_ai_search_now), fontWeight = FontWeight.SemiBold)
                }
            }

            if (state.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(stringResource(R.string.free_ai_search_error), modifier = Modifier.padding(12.dp))
                }
            }

            if (state.result.providers.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.free_ai_top_five), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                state.result.providers.forEachIndexed { index, provider ->
                    ProviderCard(
                        rank = index + 1,
                        provider = provider,
                        active = enabled && provider.connected && provider.integrated,
                        onUse = { viewModel.activateDiscoveredProvider(provider.id) },
                        onConnect = if (provider.id == ProviderDiscoveryRepository.OPENROUTER_ID) onOpenOpenRouter else onOpenCustom,
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.free_ai_truth_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.free_ai_truth_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    rank: Int,
    provider: DiscoveredProvider,
    active: Boolean,
    onUse: () -> Unit,
    onConnect: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.connected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Text(rank.toString(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(provider.freeOffer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ProviderStatus(provider)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (provider.supportsText) CapabilityTag(Icons.Outlined.TextFields, stringResource(R.string.free_ai_text))
                if (provider.supportsImage) CapabilityTag(Icons.Outlined.Image, stringResource(R.string.free_ai_image))
                if (provider.supportsVideo) CapabilityTag(Icons.Outlined.VideoCameraBack, stringResource(R.string.free_ai_video))
            }

            when {
                provider.connected && provider.integrated -> Button(
                    onClick = onUse,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(if (active) stringResource(R.string.free_ai_using) else stringResource(R.string.free_ai_use_provider))
                }
                provider.reachable -> OutlinedButton(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        if (provider.id == ProviderDiscoveryRepository.OPENROUTER_ID) {
                            stringResource(R.string.free_ai_connect_provider)
                        } else {
                            stringResource(R.string.free_ai_setup_provider)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderStatus(provider: DiscoveredProvider) {
    val (text, color) = when {
        provider.connected -> stringResource(R.string.free_ai_connected) to MaterialTheme.colorScheme.tertiaryContainer
        provider.reachable -> stringResource(R.string.free_ai_available) to MaterialTheme.colorScheme.secondaryContainer
        else -> stringResource(R.string.free_ai_offline) to MaterialTheme.colorScheme.errorContainer
    }
    Surface(shape = RoundedCornerShape(999.dp), color = color) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CapabilityTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
