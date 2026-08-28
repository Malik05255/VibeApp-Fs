package com.almi.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.almi.ai.R
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.ApiKeyRecord
import com.almi.ai.data.preferences.CustomAiConfig
import com.almi.ai.ui.components.AlmiWordmark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val mode by viewModel.aiMode.collectAsState()
    val config by viewModel.customAiConfig.collectAsState()
    val keys by viewModel.apiKeys.collectAsState()
    val freeStatus by viewModel.freeAiStatus.collectAsState()
    val oauthState by viewModel.oauthState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { AlmiWordmark(compact = true) },
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.ai_v2_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(
                    stringResource(R.string.ai_v2_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeButton(
                    selected = mode == AiMode.FREE_AUTO,
                    label = stringResource(R.string.ai_v2_auto),
                    onClick = { viewModel.setFreeMode(true) },
                    modifier = Modifier.weight(1f),
                )
                ModeButton(
                    selected = mode == AiMode.CUSTOM,
                    label = stringResource(R.string.ai_v2_custom),
                    onClick = { viewModel.setFreeMode(false) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (mode == AiMode.FREE_AUTO) {
                AutomaticEngine(
                    viewModel = viewModel,
                    keys = keys,
                    status = freeStatus,
                    oauthState = oauthState,
                )
            } else {
                CustomEngine(
                    viewModel = viewModel,
                    config = config,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(R.string.ai_v2_secure),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AutomaticEngine(
    viewModel: SettingsViewModel,
    keys: List<ApiKeyRecord>,
    status: FreeAiStatus,
    oauthState: OAuthConnectionState,
) {
    var showManualKey by remember { mutableStateOf(false) }
    var manualKey by remember { mutableStateOf("") }

    EngineCard(
        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
        title = stringResource(R.string.ai_v2_auto_title),
        subtitle = stringResource(R.string.ai_v2_auto_body),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricChip(stringResource(R.string.ai_v2_key_count, keys.count { it.enabled }), Modifier.weight(1f))
            MetricChip(stringResource(R.string.ai_v2_image_count, status.imageModels.size), Modifier.weight(1f))
            MetricChip(stringResource(R.string.ai_v2_video_count, status.videoModels.size), Modifier.weight(1f))
        }

        Button(
            onClick = viewModel::connectOpenRouterAutomatically,
            enabled = !oauthState.isConnecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (oauthState.isConnecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_v2_connecting))
            } else {
                Icon(Icons.Outlined.Key, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_v2_connect))
            }
        }

        when {
            oauthState.connected -> InlineStatus(success = true, stringResource(R.string.ai_v2_connected))
            oauthState.error != null -> InlineStatus(success = false, stringResource(R.string.ai_v2_connection_failed))
        }

        if (keys.isNotEmpty()) {
            Text(stringResource(R.string.ai_v2_keys), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { key ->
                    KeyRow(
                        key = key,
                        onToggle = { enabled -> viewModel.setApiKeyEnabled(key.id, enabled) },
                        onDelete = { viewModel.removeApiKey(key.id) },
                    )
                }
            }
        }

        TextButton(onClick = { showManualKey = !showManualKey }) {
            Text(stringResource(R.string.ai_v2_add_key))
        }

        if (showManualKey) {
            OutlinedTextField(
                value = manualKey,
                onValueChange = { manualKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ai_v2_key_placeholder)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            OutlinedButton(
                onClick = {
                    viewModel.addManualOpenRouterKey(manualKey)
                    manualKey = ""
                    showManualKey = false
                },
                enabled = manualKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.ai_v2_save_key))
            }
        }

        FilledTonalButton(
            onClick = viewModel::refreshFreeCatalog,
            enabled = !status.isChecking,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (status.isChecking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_v2_refreshing))
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_v2_refresh))
            }
        }

        if (status.error != null) {
            InlineStatus(success = false, stringResource(R.string.ai_v2_catalog_error))
        }
        if (!status.isChecking && status.imageModels.isEmpty()) {
            InlineStatus(success = false, stringResource(R.string.ai_v2_no_image))
        }
    }
}

@Composable
private fun CustomEngine(
    viewModel: SettingsViewModel,
    config: CustomAiConfig,
) {
    var providerName by remember(config) { mutableStateOf(config.providerName) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey) }
    var analysisEndpoint by remember(config) { mutableStateOf(config.analysisEndpoint) }
    var analysisModel by remember(config) { mutableStateOf(config.analysisModel) }
    var imageEndpoint by remember(config) { mutableStateOf(config.imageEndpoint) }
    var imageModel by remember(config) { mutableStateOf(config.imageModel) }
    var videoEndpoint by remember(config) { mutableStateOf(config.videoEndpoint) }
    var videoModel by remember(config) { mutableStateOf(config.videoModel) }
    var saved by remember { mutableStateOf(false) }

    EngineCard(
        icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
        title = stringResource(R.string.ai_v2_custom_title),
        subtitle = stringResource(R.string.ai_v2_custom_body),
    ) {
        LabeledField(
            value = providerName,
            onValueChange = { providerName = it; saved = false },
            label = stringResource(R.string.ai_v2_provider),
        )
        LabeledField(
            value = baseUrl,
            onValueChange = { baseUrl = it; saved = false },
            label = stringResource(R.string.ai_v2_base_url),
            keyboardType = KeyboardType.Uri,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ai_v2_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        CapabilityBlock(
            icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            title = stringResource(R.string.ai_v2_analysis),
            endpoint = analysisEndpoint,
            model = analysisModel,
            onEndpoint = { analysisEndpoint = it; saved = false },
            onModel = { analysisModel = it; saved = false },
        )
        CapabilityBlock(
            icon = { Icon(Icons.Outlined.Image, contentDescription = null) },
            title = stringResource(R.string.ai_v2_image),
            endpoint = imageEndpoint,
            model = imageModel,
            onEndpoint = { imageEndpoint = it; saved = false },
            onModel = { imageModel = it; saved = false },
        )
        CapabilityBlock(
            icon = { Icon(Icons.Outlined.VideoCameraBack, contentDescription = null) },
            title = stringResource(R.string.ai_v2_video),
            endpoint = videoEndpoint,
            model = videoModel,
            onEndpoint = { videoEndpoint = it; saved = false },
            onModel = { videoModel = it; saved = false },
        )

        val pendingConfig = CustomAiConfig(
            providerName = providerName,
            baseUrl = baseUrl,
            apiKey = apiKey,
            analysisEndpoint = analysisEndpoint,
            analysisModel = analysisModel,
            imageEndpoint = imageEndpoint,
            imageModel = imageModel,
            videoEndpoint = videoEndpoint,
            videoModel = videoModel,
        )

        Button(
            onClick = {
                viewModel.saveAndActivateCustom(pendingConfig)
                saved = true
            },
            enabled = pendingConfig.isUsable,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.ai_v2_save), fontWeight = FontWeight.SemiBold)
        }

        if (saved) {
            InlineStatus(success = true, stringResource(R.string.ai_v2_saved))
        }
    }
}

@Composable
private fun CapabilityBlock(
    icon: @Composable () -> Unit,
    title: String,
    endpoint: String,
    model: String,
    onEndpoint: (String) -> Unit,
    onModel: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon()
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            LabeledField(endpoint, onEndpoint, stringResource(R.string.ai_v2_endpoint), KeyboardType.Uri)
            LabeledField(model, onModel, stringResource(R.string.ai_v2_model))
        }
    }
}

@Composable
private fun EngineCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(10.dp)) { icon() }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun KeyRow(
    key: ApiKeyRecord,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(key.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    key.masked,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = key.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.ai_v2_remove_key))
            }
        }
    }
}

@Composable
private fun MetricChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun InlineStatus(success: Boolean, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (success) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) { Text(label) }
    }
}

@Composable
private fun LabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
}
