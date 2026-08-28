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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import com.almi.ai.data.repository.FreeAiCandidate

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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_settings_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
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
                stringResource(R.string.ai_mode_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.ai_mode_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeButton(
                    selected = mode == AiMode.FREE_AUTO,
                    text = stringResource(R.string.ai_mode_auto),
                    onClick = { viewModel.setFreeMode(true) },
                    modifier = Modifier.weight(1f),
                )
                ModeButton(
                    selected = mode == AiMode.CUSTOM,
                    text = stringResource(R.string.ai_mode_custom),
                    onClick = { viewModel.setFreeMode(false) },
                    modifier = Modifier.weight(1f),
                )
            }

            if (mode == AiMode.FREE_AUTO) {
                AutomaticModeContent(
                    viewModel = viewModel,
                    keys = keys,
                    status = freeStatus,
                    oauthState = oauthState,
                )
            } else {
                CustomModeContent(
                    viewModel = viewModel,
                    config = config,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AutomaticModeContent(
    viewModel: SettingsViewModel,
    keys: List<ApiKeyRecord>,
    status: FreeAiStatus,
    oauthState: OAuthConnectionState,
) {
    var showManualKey by remember { mutableStateOf(false) }
    var manualKey by remember { mutableStateOf("") }

    SectionCard(
        icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
        title = stringResource(R.string.ai_auto_title),
        subtitle = stringResource(R.string.ai_auto_hint),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile(stringResource(R.string.ai_auto_keys_count, keys.count { it.enabled }), Modifier.weight(1f))
            MetricTile(stringResource(R.string.ai_free_image_count, status.imageModels.size), Modifier.weight(1f))
            MetricTile(stringResource(R.string.ai_free_video_count, status.videoModels.size), Modifier.weight(1f))
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                stringResource(R.string.ai_auto_pipeline_body),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    SectionCard(
        icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
        title = stringResource(R.string.ai_auto_connect_title),
        subtitle = stringResource(R.string.ai_auto_connect_hint),
    ) {
        Button(
            onClick = viewModel::connectOpenRouterAutomatically,
            enabled = !oauthState.isConnecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (oauthState.isConnecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_auto_connecting))
            } else {
                Icon(Icons.Outlined.Key, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_auto_connect_button), fontWeight = FontWeight.SemiBold)
            }
        }

        when {
            oauthState.connected -> StatusMessage(
                success = true,
                text = stringResource(R.string.ai_auto_connected),
            )
            oauthState.error != null -> StatusMessage(
                success = false,
                text = stringResource(R.string.ai_auto_connection_failed),
            )
        }

        Text(
            stringResource(R.string.ai_auto_secure_storage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (keys.isNotEmpty()) {
            HorizontalDivider()
            Text(
                stringResource(R.string.ai_auto_keys_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            keys.forEach { key ->
                KeyRow(
                    key = key,
                    onToggle = { enabled -> viewModel.setApiKeyEnabled(key.id, enabled) },
                    onDelete = { viewModel.removeApiKey(key.id) },
                )
            }
        }

        TextButton(onClick = { showManualKey = !showManualKey }) {
            Text(
                if (showManualKey) stringResource(R.string.ai_auto_manual_hide)
                else stringResource(R.string.ai_auto_manual_show)
            )
        }

        if (showManualKey) {
            OutlinedTextField(
                value = manualKey,
                onValueChange = { manualKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ai_auto_manual_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    viewModel.addManualOpenRouterKey(manualKey)
                    manualKey = ""
                },
                enabled = manualKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.ai_auto_manual_add))
            }
        }
    }

    SectionCard(
        icon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
        title = stringResource(R.string.ai_catalog_title),
        subtitle = stringResource(R.string.ai_catalog_hint),
    ) {
        FilledTonalButton(
            onClick = viewModel::refreshFreeCatalog,
            enabled = !status.isChecking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (status.isChecking) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_free_checking))
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ai_free_refresh))
            }
        }

        if (status.error != null) {
            StatusMessage(success = false, text = stringResource(R.string.ai_free_catalog_error))
        }

        if (!status.isChecking && status.imageModels.isEmpty()) {
            StatusMessage(success = false, text = stringResource(R.string.ai_free_no_image))
        }
        if (!status.isChecking && status.videoModels.isNotEmpty()) {
            StatusMessage(
                success = true,
                text = stringResource(R.string.ai_auto_video_ready, status.videoModels.first().id),
            )
        }

        ModelPreview(stringResource(R.string.ai_free_image_models), status.imageModels)
        ModelPreview(stringResource(R.string.ai_free_video_models), status.videoModels)
    }
}

@Composable
private fun CustomModeContent(
    viewModel: SettingsViewModel,
    config: CustomAiConfig,
) {
    var providerName by remember(config) { mutableStateOf(config.providerName) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var customApiKey by remember(config) { mutableStateOf(config.apiKey) }
    var imageEndpoint by remember(config) { mutableStateOf(config.imageEndpoint) }
    var imageModel by remember(config) { mutableStateOf(config.imageModel) }
    var videoEndpoint by remember(config) { mutableStateOf(config.videoEndpoint) }
    var videoModel by remember(config) { mutableStateOf(config.videoModel) }
    var saved by remember { mutableStateOf(false) }

    SectionCard(
        icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
        title = stringResource(R.string.ai_custom_title),
        subtitle = stringResource(R.string.ai_custom_hint),
    ) {
        Text(stringResource(R.string.ai_custom_group_provider), fontWeight = FontWeight.SemiBold)
        AiTextField(providerName, { providerName = it; saved = false }, stringResource(R.string.ai_custom_provider_name))
        AiTextField(
            baseUrl,
            { baseUrl = it; saved = false },
            stringResource(R.string.ai_custom_base_url),
            KeyboardType.Uri,
        )
        OutlinedTextField(
            value = customApiKey,
            onValueChange = { customApiKey = it; saved = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.ai_custom_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
        )

        HorizontalDivider()
        Text(stringResource(R.string.ai_custom_group_image), fontWeight = FontWeight.SemiBold)
        AiTextField(imageEndpoint, { imageEndpoint = it; saved = false }, stringResource(R.string.ai_custom_image_endpoint))
        AiTextField(imageModel, { imageModel = it; saved = false }, stringResource(R.string.ai_custom_image_model))

        HorizontalDivider()
        Text(stringResource(R.string.ai_custom_group_video), fontWeight = FontWeight.SemiBold)
        AiTextField(videoEndpoint, { videoEndpoint = it; saved = false }, stringResource(R.string.ai_custom_video_endpoint))
        AiTextField(videoModel, { videoModel = it; saved = false }, stringResource(R.string.ai_custom_video_model))

        val pendingConfig = CustomAiConfig(
            providerName = providerName,
            baseUrl = baseUrl,
            apiKey = customApiKey,
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
        ) {
            Text(stringResource(R.string.ai_custom_save_activate), fontWeight = FontWeight.SemiBold)
        }
        if (saved) {
            StatusMessage(success = true, text = stringResource(R.string.ai_custom_saved))
        }
        Text(
            stringResource(R.string.ai_custom_compatibility_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(key.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                key.masked,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = key.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.ai_auto_delete_key))
        }
    }
}

@Composable
private fun ModelPreview(title: String, models: List<FreeAiCandidate>) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    if (models.isEmpty()) {
        Text(
            stringResource(R.string.ai_free_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        models.take(4).forEachIndexed { index, model ->
            Text(
                "${index + 1}. ${model.id}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (models.size > 4) {
            Text(
                stringResource(R.string.ai_free_more_models, models.size - 4),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusMessage(success: Boolean, text: String) {
    val container = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (success) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = content)
            Text(text, style = MaterialTheme.typography.bodySmall, color = content, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(text: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ModeButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        FilledTonalButton(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun AiTextField(
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
    )
}
