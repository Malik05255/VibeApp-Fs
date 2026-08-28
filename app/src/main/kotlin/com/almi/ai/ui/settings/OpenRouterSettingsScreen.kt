package com.almi.ai.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.almi.ai.R
import com.almi.ai.data.preferences.ApiKeyRecord
import com.almi.ai.data.repository.ModelCapability
import com.almi.ai.data.repository.OpenRouterKeyStatus
import com.almi.ai.data.repository.OpenRouterModelInfo
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenRouterSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.openRouterConfig.collectAsState()
    val keys by viewModel.apiKeys.collectAsState()
    val state by viewModel.openRouterState.collectAsState()
    val oauth by viewModel.oauthState.collectAsState()
    var connectionMode by rememberSaveable { mutableStateOf("DIRECT") }
    var manualKey by rememberSaveable { mutableStateOf("") }
    var manualFreeOnly by rememberSaveable { mutableStateOf(config.freeOnly) }
    var capability by rememberSaveable { mutableStateOf(ModelCapability.IMAGE.name) }
    var search by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshOpenRouter() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("OpenRouter", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.or_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.or_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectionTile(
                    selected = connectionMode == "DIRECT",
                    title = stringResource(R.string.or_direct),
                    body = stringResource(R.string.or_direct_body),
                    icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { connectionMode = "DIRECT" },
                )
                ConnectionTile(
                    selected = connectionMode == "MANUAL",
                    title = stringResource(R.string.or_manual),
                    body = stringResource(R.string.or_manual_body),
                    icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    onClick = { connectionMode = "MANUAL" },
                )
            }

            if (connectionMode == "DIRECT") {
                DirectConnectionCard(
                    oauth = oauth,
                    keyStatus = state.keyStatus,
                    onConnect = viewModel::connectOpenRouterAutomatically,
                )
            } else {
                ManualConnectionCard(
                    manualKey = manualKey,
                    onManualKey = { manualKey = it },
                    freeOnly = manualFreeOnly,
                    onFreeOnly = { manualFreeOnly = it },
                    onSave = {
                        viewModel.addManualOpenRouterKey(manualKey, manualFreeOnly)
                        manualKey = ""
                    },
                )
            }

            if (keys.isNotEmpty()) {
                KeyVaultCard(
                    keys = keys,
                    onToggle = viewModel::setApiKeyEnabled,
                    onDelete = viewModel::removeApiKey,
                )
            }

            AccountStatusCard(state.keyStatus)

            ModelBrowser(
                isLoading = state.isLoading,
                capability = ModelCapability.valueOf(capability),
                onCapability = { capability = it.name },
                search = search,
                onSearch = { search = it },
                models = when (ModelCapability.valueOf(capability)) {
                    ModelCapability.TEXT -> state.catalog.textModels
                    ModelCapability.IMAGE -> state.catalog.imageModels
                    ModelCapability.VIDEO -> state.catalog.videoModels
                }.let { list ->
                    val freeOnly = if (connectionMode == "DIRECT") true else config.freeOnly
                    list.filter { !freeOnly || it.isFree }
                },
                selectedModel = when (ModelCapability.valueOf(capability)) {
                    ModelCapability.TEXT -> config.analysisModel
                    ModelCapability.IMAGE -> config.imageModel
                    ModelCapability.VIDEO -> config.videoModel
                },
                keyStatus = state.keyStatus,
                onSelect = { model ->
                    viewModel.selectOpenRouterModel(ModelCapability.valueOf(capability), model.id)
                },
                onRefresh = viewModel::refreshOpenRouter,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    stringResource(R.string.or_fallback_note),
                    modifier = Modifier.padding(15.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun ConnectionTile(
    selected: Boolean,
    title: String,
    body: String,
    icon: @Composable () -> Unit,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.weight(1f))
                RadioButton(selected = selected, onClick = onClick)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DirectConnectionCard(
    oauth: OAuthConnectionState,
    keyStatus: OpenRouterKeyStatus?,
    onConnect: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.or_direct_title), fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.or_direct_free_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onConnect,
                enabled = !oauth.isConnecting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (oauth.isConnecting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.or_connecting))
                } else {
                    Icon(Icons.Outlined.Key, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (keyStatus?.connected == true) stringResource(R.string.or_reconnect) else stringResource(R.string.or_connect))
                }
            }
            when {
                keyStatus?.connected == true -> StatusLine(true, stringResource(R.string.or_connected))
                oauth.error != null -> StatusLine(false, stringResource(R.string.or_connect_failed))
            }
        }
    }
}

@Composable
private fun ManualConnectionCard(
    manualKey: String,
    onManualKey: (String) -> Unit,
    freeOnly: Boolean,
    onFreeOnly: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.or_manual_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = manualKey,
                onValueChange = onManualKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenRouter API key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Text(stringResource(R.string.or_access_type), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = freeOnly,
                    onClick = { onFreeOnly(true) },
                    label = { Text(stringResource(R.string.or_free_only)) },
                    leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.size(17.dp)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = !freeOnly,
                    onClick = { onFreeOnly(false) },
                    label = { Text(stringResource(R.string.or_paid_allowed)) },
                    leadingIcon = { Icon(Icons.Outlined.Paid, contentDescription = null, Modifier.size(17.dp)) },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = onSave,
                enabled = manualKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.or_save_key), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun KeyVaultCard(
    keys: List<ApiKeyRecord>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.or_saved_keys), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            keys.forEach { key ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(key.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(key.masked, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = key.enabled, onCheckedChange = { onToggle(key.id, it) })
                        IconButton(onClick = { onDelete(key.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountStatusCard(status: OpenRouterKeyStatus?) {
    if (status == null) return
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.or_account_status), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        if (status.freeTier) stringResource(R.string.or_free_tier) else stringResource(R.string.or_paid_tier),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            status.remainingUsd?.let {
                Text(stringResource(R.string.or_remaining_credit, formatUsd(it)), fontWeight = FontWeight.SemiBold)
            }
            status.usageUsd?.let {
                Text(stringResource(R.string.or_usage, formatUsd(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status.remainingUsd == null) {
                Text(
                    stringResource(R.string.or_no_numeric_remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModelBrowser(
    isLoading: Boolean,
    capability: ModelCapability,
    onCapability: (ModelCapability) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    models: List<OpenRouterModelInfo>,
    selectedModel: String,
    keyStatus: OpenRouterKeyStatus?,
    onSelect: (OpenRouterModelInfo) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.or_models_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.or_models_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = null)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                CapabilityFilter(ModelCapability.TEXT, capability, onCapability, Modifier.weight(1f))
                CapabilityFilter(ModelCapability.IMAGE, capability, onCapability, Modifier.weight(1f))
                CapabilityFilter(ModelCapability.VIDEO, capability, onCapability, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.or_search_models)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )

            val visible = remember(models, search) {
                val query = search.trim().lowercase(Locale.ROOT)
                if (query.isBlank()) models else models.filter {
                    it.name.lowercase(Locale.ROOT).contains(query) || it.id.lowercase(Locale.ROOT).contains(query)
                }
            }.take(40)

            if (!isLoading && visible.isEmpty()) {
                Text(
                    stringResource(R.string.or_no_models),
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            visible.forEach { model ->
                ModelCard(
                    model = model,
                    capability = capability,
                    selected = model.id == selectedModel,
                    remainingUsd = keyStatus?.remainingUsd,
                    onClick = { onSelect(model) },
                )
            }
        }
    }
}

@Composable
private fun CapabilityFilter(
    value: ModelCapability,
    selected: ModelCapability,
    onSelect: (ModelCapability) -> Unit,
    modifier: Modifier,
) {
    val icon = when (value) {
        ModelCapability.TEXT -> Icons.Outlined.ChatBubbleOutline
        ModelCapability.IMAGE -> Icons.Outlined.Image
        ModelCapability.VIDEO -> Icons.Outlined.VideoCameraBack
    }
    val label = when (value) {
        ModelCapability.TEXT -> stringResource(R.string.or_text)
        ModelCapability.IMAGE -> stringResource(R.string.or_image)
        ModelCapability.VIDEO -> stringResource(R.string.or_video)
    }
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null, Modifier.size(16.dp)) },
        modifier = modifier,
    )
}

@Composable
private fun ModelCard(
    model: OpenRouterModelInfo,
    capability: ModelCapability,
    selected: Boolean,
    remainingUsd: Double?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(model.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (model.isFree) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            if (model.isFree) stringResource(R.string.or_free_badge) else stringResource(R.string.or_paid_badge),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(modelPriceText(model, capability), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                modelEstimateText(model, capability, remainingUsd)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun modelPriceText(model: OpenRouterModelInfo, capability: ModelCapability): String {
    if (model.isFree) return stringResource(R.string.or_price_free)
    return when (capability) {
        ModelCapability.TEXT -> {
            val input = model.inputUsdPerMillion
            val output = model.outputUsdPerMillion
            if (input != null && output != null) {
                stringResource(R.string.or_price_text, formatUsd(input), formatUsd(output))
            } else stringResource(R.string.or_price_unknown)
        }
        ModelCapability.IMAGE -> model.imageUsdPerUnit?.let {
            stringResource(R.string.or_price_image, formatUsd(it))
        } ?: stringResource(R.string.or_price_unknown)
        ModelCapability.VIDEO -> model.videoUsdPerSecond?.let {
            stringResource(R.string.or_price_video, formatUsd(it))
        } ?: stringResource(R.string.or_price_unknown)
    }
}

@Composable
private fun modelEstimateText(
    model: OpenRouterModelInfo,
    capability: ModelCapability,
    remainingUsd: Double?,
): String? {
    if (model.isFree) return stringResource(R.string.or_estimate_free_limits)
    val count = when (capability) {
        ModelCapability.TEXT -> model.estimatedTextTurnCount(remainingUsd)
        ModelCapability.IMAGE -> model.estimatedImageCount(remainingUsd)
        ModelCapability.VIDEO -> model.estimatedFourSecondVideoCount(remainingUsd)
    } ?: return null
    return when (capability) {
        ModelCapability.TEXT -> stringResource(R.string.or_estimate_text, count)
        ModelCapability.IMAGE -> stringResource(R.string.or_estimate_images, count)
        ModelCapability.VIDEO -> stringResource(R.string.or_estimate_videos, count)
    }
}

@Composable
private fun StatusLine(success: Boolean, text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (success) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatUsd(value: Double): String = when {
    value >= 1.0 -> "$" + String.format(Locale.US, "%.2f", value)
    value >= 0.01 -> "$" + String.format(Locale.US, "%.3f", value)
    else -> "$" + String.format(Locale.US, "%.5f", value)
}
