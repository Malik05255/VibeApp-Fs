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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.R
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.repository.DiscoveredProvider
import com.almi.ai.data.repository.ProviderDiscoveryRepository
import com.almi.ai.ui.components.GlassIconTile
import com.almi.ai.ui.components.GlassSurface
import com.almi.ai.ui.components.LuxeBackdrop
import com.almi.ai.ui.components.StatusPill

@Composable
fun FreeAiDiscoveryScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val mode by viewModel.aiMode.collectAsState()
    val state by viewModel.providerDiscoveryState.collectAsState()
    val enabled = mode == AiMode.FREE_AUTO
    val activeProvider = state.result.providers.firstOrNull { it.id == state.activeProviderId && it.connected }

    LaunchedEffect(Unit) { viewModel.discoverFreeProviders() }

    LuxeBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.free_ai_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.free_ai_no_key_providers),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(
                        if (activeProvider != null) stringResource(R.string.free_ai_connected_now) else stringResource(R.string.free_ai_no_connection),
                        positive = activeProvider != null,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.free_ai_heading), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.free_ai_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FreeModeHero(
                    enabled = enabled,
                    provider = activeProvider,
                    checking = state.isChecking,
                    onToggle = viewModel::setFreeMode,
                )

                Button(
                    onClick = viewModel::discoverFreeProviders,
                    enabled = !state.isChecking,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    if (state.isChecking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.free_ai_searching))
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.free_ai_search_now), fontWeight = FontWeight.SemiBold)
                    }
                }

                if (state.error != null) {
                    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Text(
                            stringResource(R.string.free_ai_search_error),
                            modifier = Modifier.padding(13.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    GlassIconTile(Icons.Outlined.CloudQueue)
                    Column {
                        Text(stringResource(R.string.free_ai_no_key_providers), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.free_ai_no_personal_key),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!state.isChecking && state.result.providers.isEmpty()) {
                    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            stringResource(R.string.free_ai_none_available),
                            modifier = Modifier.padding(15.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                state.result.providers.forEachIndexed { index, provider ->
                    ProviderGlassCard(
                        rank = index + 1,
                        provider = provider,
                        active = enabled && provider.id == state.activeProviderId && provider.connected,
                    )
                }

                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
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
}

@Composable
private fun FreeModeHero(
    enabled: Boolean,
    provider: DiscoveredProvider?,
    checking: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), emphasized = enabled) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = if (enabled) 0.65f else 0.20f),
                                    Color.Transparent,
                                )
                            ),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        color = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = if (enabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.free_ai_switch_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (enabled) stringResource(R.string.free_ai_enabled) else stringResource(R.string.free_ai_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (checking && provider == null) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        if (provider != null) Icons.Outlined.CheckCircle else Icons.Outlined.CloudQueue,
                        contentDescription = null,
                        tint = if (provider != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (provider != null) stringResource(R.string.free_ai_connected_now) else stringResource(R.string.free_ai_no_connection),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        provider?.name ?: stringResource(R.string.free_ai_waiting_provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderGlassCard(
    rank: Int,
    provider: DiscoveredProvider,
    active: Boolean,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), emphasized = active) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        Text(rank.toString(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        providerOffer(provider.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProviderStatus(provider = provider, active = active)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                if (provider.supportsText) CapabilityTag(Icons.Outlined.TextFields, stringResource(R.string.free_ai_text))
                if (provider.supportsImage) CapabilityTag(Icons.Outlined.Image, stringResource(R.string.free_ai_image))
                if (provider.supportsVideo) CapabilityTag(Icons.Outlined.VideoCameraBack, stringResource(R.string.free_ai_video))
            }

            if (active) {
                Text(
                    stringResource(R.string.free_ai_auto_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun providerOffer(providerId: String): String = when (providerId) {
    ProviderDiscoveryRepository.AI_HORDE_ID -> stringResource(R.string.free_ai_horde_offer)
    else -> stringResource(R.string.free_ai_no_personal_key)
}

@Composable
private fun ProviderStatus(provider: DiscoveredProvider, active: Boolean) {
    val text = when {
        active -> stringResource(R.string.free_ai_connected_now)
        provider.connected -> stringResource(R.string.free_ai_ready)
        else -> stringResource(R.string.free_ai_offline)
    }
    StatusPill(text = text, positive = active || provider.connected)
}

@Composable
private fun CapabilityTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)) {
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
