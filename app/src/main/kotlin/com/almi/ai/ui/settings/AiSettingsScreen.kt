package com.almi.ai.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.R
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.ui.components.AlmiWordmark
import com.almi.ai.ui.components.GlassIconTile
import com.almi.ai.ui.components.GlassSurface
import com.almi.ai.ui.components.LuxeBackdrop
import com.almi.ai.ui.components.LuxeBottomBar
import com.almi.ai.ui.components.LuxeNavDestination
import com.almi.ai.ui.components.StatusPill

private enum class AiHubPage {
    HOME,
    OPENROUTER,
    CUSTOM,
    FREE,
}

@Composable
fun AiSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val mode by viewModel.aiMode.collectAsState()
    var page by rememberSaveable { mutableStateOf(AiHubPage.HOME) }

    BackHandler {
        if (page == AiHubPage.HOME) onBack() else page = AiHubPage.HOME
    }

    when (page) {
        AiHubPage.OPENROUTER -> OpenRouterSettingsScreen(
            viewModel = viewModel,
            onBack = { page = AiHubPage.HOME },
        )
        AiHubPage.CUSTOM -> CustomApiSettingsScreen(
            viewModel = viewModel,
            onBack = { page = AiHubPage.HOME },
        )
        AiHubPage.FREE -> FreeAiDiscoveryScreen(
            viewModel = viewModel,
            onBack = { page = AiHubPage.HOME },
        )
        AiHubPage.HOME -> LuxeAiHubHome(
            mode = mode,
            onBack = onBack,
            onOpenRouter = { page = AiHubPage.OPENROUTER },
            onCustom = { page = AiHubPage.CUSTOM },
            freeEnabled = mode == AiMode.FREE_AUTO,
            onFreeToggle = viewModel::setFreeMode,
            onFreeOpen = { page = AiHubPage.FREE },
            onOpenHome = onOpenHome,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun LuxeAiHubHome(
    mode: AiMode,
    onBack: () -> Unit,
    onOpenRouter: () -> Unit,
    onCustom: () -> Unit,
    freeEnabled: Boolean,
    onFreeToggle: (Boolean) -> Unit,
    onFreeOpen: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    LuxeBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { AiHubTopHeader(onBack) },
            bottomBar = {
                LuxeBottomBar(
                    selected = LuxeNavDestination.AI,
                    homeLabel = stringResource(R.string.luxe_nav_home),
                    aiLabel = stringResource(R.string.luxe_nav_ai),
                    settingsLabel = stringResource(R.string.luxe_nav_settings),
                    onHome = onOpenHome,
                    onAi = {},
                    onSettings = onOpenSettings,
                )
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
                    Text(stringResource(R.string.luxe_ai_title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.luxe_ai_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AiCoreHero(mode)

                GatewayTile(
                    icon = Icons.Outlined.Route,
                    title = "OpenRouter",
                    subtitle = stringResource(R.string.ai_hub_openrouter_body),
                    active = mode == AiMode.OPENROUTER,
                    onClick = onOpenRouter,
                )

                GatewayTile(
                    icon = Icons.Outlined.Key,
                    title = stringResource(R.string.ai_hub_custom_title),
                    subtitle = stringResource(R.string.ai_hub_custom_body),
                    active = mode == AiMode.CUSTOM,
                    onClick = onCustom,
                )

                FreeGatewayTile(
                    enabled = freeEnabled,
                    onToggle = onFreeToggle,
                    onOpen = onFreeOpen,
                )

                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.ai_hub_security_note),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AiHubTopHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
            AlmiWordmark(compact = true)
        }
        StatusPill(stringResource(R.string.luxe_ai_ready))
    }
}

@Composable
private fun AiCoreHero(mode: AiMode) {
    val active = when (mode) {
        AiMode.OPENROUTER -> stringResource(R.string.luxe_engine_openrouter)
        AiMode.CUSTOM -> stringResource(R.string.luxe_engine_custom)
        AiMode.FREE_AUTO -> stringResource(R.string.luxe_engine_free)
    }

    GlassSurface(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.96f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                                Color.Transparent,
                            )
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(66.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    shadowElevation = 10.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.luxe_engine_live), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(active, style = MaterialTheme.typography.headlineSmall)
                Text(
                    when (mode) {
                        AiMode.OPENROUTER -> stringResource(R.string.ai_hub_active_openrouter)
                        AiMode.CUSTOM -> stringResource(R.string.ai_hub_active_custom)
                        AiMode.FREE_AUTO -> stringResource(R.string.ai_hub_active_free)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GatewayTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        emphasized = active,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconTile(icon, active = active)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (active) StatusPill(stringResource(R.string.ai_hub_active))
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FreeGatewayTile(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        emphasized = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconTile(Icons.Outlined.AutoAwesome, active = enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_hub_free_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ai_hub_free_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (enabled) stringResource(R.string.ai_hub_free_on) else stringResource(R.string.ai_hub_free_off),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}
