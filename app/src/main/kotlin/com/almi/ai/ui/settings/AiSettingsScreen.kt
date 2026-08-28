package com.almi.ai.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.material3.Text
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
    val freeKey by viewModel.freeOpenRouterApiKey.collectAsState()
    val freeStatus by viewModel.freeAiStatus.collectAsState()

    var customExpanded by remember { mutableStateOf(false) }
    var providerName by remember(config) { mutableStateOf(config.providerName) }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl) }
    var customApiKey by remember(config) { mutableStateOf(config.apiKey) }
    var imageEndpoint by remember(config) { mutableStateOf(config.imageEndpoint) }
    var imageModel by remember(config) { mutableStateOf(config.imageModel) }
    var videoEndpoint by remember(config) { mutableStateOf(config.videoEndpoint) }
    var videoModel by remember(config) { mutableStateOf(config.videoModel) }
    var customSaved by remember { mutableStateOf(false) }

    var freeKeyInput by remember(freeKey) { mutableStateOf(freeKey) }
    var freeKeySaved by remember { mutableStateOf(false) }

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
            AiSettingsCard(
                icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                title = stringResource(R.string.ai_custom_title),
                subtitle = stringResource(R.string.ai_custom_hint),
                active = mode == AiMode.CUSTOM,
            ) {
                OutlinedButton(
                    onClick = { customExpanded = !customExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (customExpanded) stringResource(R.string.ai_custom_hide)
                        else stringResource(R.string.ai_custom_configure)
                    )
                }

                if (customExpanded) {
                    AiTextField(
                        value = providerName,
                        onValueChange = { providerName = it; customSaved = false },
                        label = stringResource(R.string.ai_custom_provider_name),
                    )
                    AiTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it; customSaved = false },
                        label = stringResource(R.string.ai_custom_base_url),
                        keyboardType = KeyboardType.Uri,
                    )
                    OutlinedTextField(
                        value = customApiKey,
                        onValueChange = { customApiKey = it; customSaved = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.ai_custom_api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        singleLine = true,
                    )
                    AiTextField(
                        value = imageEndpoint,
                        onValueChange = { imageEndpoint = it; customSaved = false },
                        label = stringResource(R.string.ai_custom_image_endpoint),
                    )
                    AiTextField(
                        value = imageModel,
                        onValueChange = { imageModel = it; customSaved = false },
                        label = stringResource(R.string.ai_custom_image_model),
                    )
                    AiTextField(
                        value = videoEndpoint,
                        onValueChange = { videoEndpoint = it; customSaved = false },
                        label = stringResource(R.string.ai_custom_video_endpoint),
                    )
                    AiTextField(
                        value = videoModel,
                        onValueChange = { videoModel = it; customSaved = false },
                        label = stringResource(R.string.ai_custom_video_model),
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.ai_custom_compatibility_note),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

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
                            customSaved = true
                        },
                        enabled = pendingConfig.isUsable,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(stringResource(R.string.ai_custom_save_activate), fontWeight = FontWeight.SemiBold)
                    }
                    if (customSaved) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.ai_custom_saved), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            AiSettingsCard(
                icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                title = stringResource(R.string.ai_free_title),
                subtitle = stringResource(R.string.ai_free_hint),
                active = mode == AiMode.FREE_AUTO,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeButton(
                        selected = mode == AiMode.FREE_AUTO,
                        text = stringResource(R.string.ai_free_on),
                        onClick = { viewModel.setFreeMode(true) },
                        modifier = Modifier.weight(1f),
                    )
                    ModeButton(
                        selected = mode != AiMode.FREE_AUTO,
                        text = stringResource(R.string.ai_free_off),
                        onClick = { viewModel.setFreeMode(false) },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (mode == AiMode.FREE_AUTO) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            stringResource(R.string.ai_free_exclusive_note),
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                OutlinedTextField(
                    value = freeKeyInput,
                    onValueChange = { freeKeyInput = it; freeKeySaved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_free_key_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.ai_free_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        viewModel.saveFreeOpenRouterApiKey(freeKeyInput)
                        freeKeySaved = true
                    },
                    enabled = freeKeyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.ai_free_key_save))
                }
                if (freeKeySaved) {
                    Text(stringResource(R.string.ai_free_key_saved), color = MaterialTheme.colorScheme.primary)
                }

                FilledTonalButton(
                    onClick = viewModel::refreshFreeCatalog,
                    enabled = !freeStatus.isChecking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (freeStatus.isChecking) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(stringResource(R.string.ai_free_checking))
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(stringResource(R.string.ai_free_refresh))
                    }
                }

                FreeCatalogSummary(freeStatus)

                Text(
                    stringResource(R.string.ai_free_fallback_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FreeCatalogSummary(status: FreeAiStatus) {
    if (status.error != null) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                stringResource(R.string.ai_free_catalog_error),
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CountTile(
            title = stringResource(R.string.ai_free_image_count, status.imageModels.size),
            modifier = Modifier.weight(1f),
        )
        CountTile(
            title = stringResource(R.string.ai_free_video_count, status.videoModels.size),
            modifier = Modifier.weight(1f),
        )
    }

    if (!status.isChecking && status.imageModels.isEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                stringResource(R.string.ai_free_no_image),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

    if (!status.isChecking && status.videoModels.isEmpty()) {
        Text(
            stringResource(R.string.ai_free_no_video),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    CandidateList(stringResource(R.string.ai_free_image_models), status.imageModels)
    CandidateList(stringResource(R.string.ai_free_video_models), status.videoModels)
}

@Composable
private fun CandidateList(title: String, items: List<FreeAiCandidate>) {
    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    if (items.isEmpty()) {
        Text(
            stringResource(R.string.ai_free_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        items.take(8).forEachIndexed { index, item ->
            Text(
                "${index + 1}. ${item.id}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (items.size > 8) {
            Text(
                stringResource(R.string.ai_free_more_models, items.size - 8),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CountTile(title: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
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

@Composable
private fun AiSettingsCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
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
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        if (active) stringResource(R.string.ai_status_active) else stringResource(R.string.ai_status_inactive),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            content()
        }
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
