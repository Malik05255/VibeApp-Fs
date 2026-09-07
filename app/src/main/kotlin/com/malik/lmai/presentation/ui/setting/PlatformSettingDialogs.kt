package com.malik.lmai.presentation.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.util.isValidUrl
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlatformNameDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    initialValue: String,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isPlatformNameDialogOpen) {
        PlatformNameEditorDialog(
            initialValue = initialValue,
            onDismissRequest = settingViewModel::closePlatformNameDialog,
            onConfirmRequest = settingViewModel::updatePlatformName,
        )
    }
}

@Composable
fun APIUrlDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    initialValue: String,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isApiUrlDialogOpen) {
        APIUrlEditorDialog(
            initialValue = initialValue,
            onDismissRequest = settingViewModel::closeApiUrlDialog,
            onConfirmRequest = settingViewModel::updateApiUrl,
        )
    }
}

@Composable
fun APIKeyDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isApiTokenDialogOpen) {
        val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
        APIKeyEditorDialog(
            allowEmpty = platform?.compatibleType == ClientType.CUSTOM,
            onDismissRequest = settingViewModel::closeApiTokenDialog,
            onConfirmRequest = settingViewModel::updateApiToken,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    model: String,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isApiModelDialogOpen) {
        ModelEditorDialog(
            initModel = model,
            settingViewModel = settingViewModel,
            onDismissRequest = settingViewModel::closeApiModelDialog,
            onConfirmRequest = settingViewModel::updateApiModel,
        )
    }
}

@Composable
fun TemperatureDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    temperature: Float?,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isTemperatureDialogOpen) {
        TemperatureEditorDialog(
            temperature = temperature,
            onDismissRequest = settingViewModel::closeTemperatureDialog,
            onConfirmRequest = settingViewModel::updateTemperature,
        )
    }
}

@Composable
fun TopPDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    topP: Float?,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isTopPDialogOpen) {
        TopPEditorDialog(
            topP = topP,
            onDismissRequest = settingViewModel::closeTopPDialog,
            onConfirmRequest = settingViewModel::updateTopP,
        )
    }
}

@Composable
fun SystemPromptDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    systemPrompt: String,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isSystemPromptDialogOpen) {
        SystemPromptEditorDialog(
            prompt = systemPrompt,
            onDismissRequest = settingViewModel::closeSystemPromptDialog,
            onConfirmRequest = settingViewModel::updateSystemPrompt,
        )
    }
}

@Composable
private fun PlatformNameEditorDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit,
) {
    var platformName by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        title = { Text(stringResource(R.string.platform_name)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = platformName,
                onValueChange = { platformName = it },
                label = { Text(stringResource(R.string.platform_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                supportingText = { Text(stringResource(R.string.platform_name_supporting)) },
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = platformName.isNotBlank(),
                onClick = { onConfirmRequest(platformName.trim()) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun APIUrlEditorDialog(
    initialValue: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit,
) {
    var apiUrl by remember(initialValue) { mutableStateOf(initialValue) }
    val cleanUrl = apiUrl.trim()
    val isValid = cleanUrl.isNotBlank() && cleanUrl.isValidUrl()

    AlertDialog(
        title = { Text(stringResource(R.string.api_url)) },
        text = {
            Column {
                Text(stringResource(R.string.api_url_cautions))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    value = apiUrl,
                    onValueChange = { apiUrl = it },
                    singleLine = true,
                    isError = cleanUrl.isNotBlank() && !cleanUrl.isValidUrl(),
                    label = { Text(stringResource(R.string.api_url)) },
                    supportingText = {
                        if (cleanUrl.isNotBlank() && !cleanUrl.isValidUrl()) {
                            Text(stringResource(R.string.invalid_api_url))
                        }
                    },
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { onConfirmRequest(cleanUrl) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun APIKeyEditorDialog(
    allowEmpty: Boolean,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit,
) {
    var token by remember { mutableStateOf("") }

    AlertDialog(
        title = { Text(stringResource(R.string.api_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.api_key)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
                if (allowEmpty) {
                    Text(
                        text = stringResource(R.string.custom_api_key_optional),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = allowEmpty || token.isNotBlank(),
                onClick = { onConfirmRequest(token) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditorDialog(
    initModel: String,
    settingViewModel: PlatformSettingViewModel,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit,
) {
    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val availableModels by settingViewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingModels by settingViewModel.isLoadingModels.collectAsStateWithLifecycle()

    val isOpenRouter = platform?.compatibleType == ClientType.OPEN_ROUTER
    val isGoogleAIStudio = platform?.compatibleType == ClientType.GOOGLE_AI_STUDIO

    var selectedModel by remember(initModel, platform?.uid) {
        mutableStateOf(initModel)
    }
    var isFreeOnly by remember(platform?.uid) {
        mutableStateOf(platform?.isFree ?: true)
    }
    var expanded by remember(platform?.uid) { mutableStateOf(false) }
    var modelSearchQuery by remember(platform?.uid, isFreeOnly) { mutableStateOf("") }

    val filteredModels = remember(availableModels, modelSearchQuery) {
        val query = modelSearchQuery.trim()
        if (query.isBlank()) {
            availableModels
        } else {
            availableModels.filter { modelInfo ->
                modelInfo.id.contains(query, ignoreCase = true) ||
                    modelInfo.name?.contains(query, ignoreCase = true) == true
            }
        }
    }

    LaunchedEffect(isOpenRouter, isFreeOnly, platform?.token) {
        if (isOpenRouter && !platform?.token.isNullOrBlank()) {
            settingViewModel.loadModels(isFreeOnly = isFreeOnly)
        }
    }

    AlertDialog(
        title = {
            Text(
                when {
                    isOpenRouter -> stringResource(R.string.select_openrouter_model)
                    isGoogleAIStudio -> stringResource(R.string.google_ai_studio_model_title)
                    else -> stringResource(R.string.api_model)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isOpenRouter) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = isFreeOnly,
                            onClick = {
                                isFreeOnly = true
                                expanded = false
                                modelSearchQuery = ""
                            },
                            label = { Text(stringResource(R.string.filter_free)) },
                        )
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = !isFreeOnly,
                            onClick = {
                                isFreeOnly = false
                                expanded = false
                                modelSearchQuery = ""
                            },
                            label = { Text(stringResource(R.string.filter_paid)) },
                        )
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = modelSearchQuery,
                        onValueChange = { modelSearchQuery = it },
                        label = { Text(stringResource(R.string.search_openrouter_models)) },
                        placeholder = { Text(stringResource(R.string.model_name_or_id)) },
                        singleLine = true,
                    )

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = {
                            if (!isLoadingModels) expanded = !expanded
                        },
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.model_name)) },
                            placeholder = { Text(stringResource(R.string.select_openrouter_model)) },
                            trailingIcon = {
                                if (isLoadingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                }
                            },
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            when {
                                isLoadingModels -> {
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        },
                                        onClick = {},
                                    )
                                }

                                filteredModels.isEmpty() -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (modelSearchQuery.isBlank()) {
                                                    stringResource(R.string.no_models_available)
                                                } else {
                                                    stringResource(R.string.no_matching_models)
                                                }
                                            )
                                        },
                                        onClick = { expanded = false },
                                    )
                                }

                                else -> {
                                    filteredModels
                                        .take(MAX_VISIBLE_OPENROUTER_MODELS)
                                        .forEach { modelInfo ->
                                            val pricing = modelInfo.pricing
                                            val isFree = pricing?.isFree == true
                                            val priceLabel = if (isFree) {
                                                stringResource(R.string.free)
                                            } else {
                                                pricing?.averagePricePer1K?.let { price ->
                                                    val formatted = String.format(
                                                        Locale.US,
                                                        "%.6f",
                                                        price,
                                                    )
                                                    stringResource(
                                                        R.string.price_per_1k_tokens,
                                                        formatted,
                                                    )
                                                } ?: stringResource(R.string.price_unavailable)
                                            }

                                            DropdownMenuItem(
                                                text = {
                                                    Column(Modifier.fillMaxWidth()) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                        ) {
                                                            Text(
                                                                text = modelInfo.name ?: modelInfo.id,
                                                                modifier = Modifier.weight(1f),
                                                                style = MaterialTheme.typography.bodyLarge,
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                text = priceLabel,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        if (modelInfo.name != null) {
                                                            Text(
                                                                text = modelInfo.id,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        if (modelInfo.supportsTools) {
                                                            Text(
                                                                text = stringResource(R.string.supports_tools),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedModel = modelInfo.id
                                                    expanded = false
                                                },
                                            )
                                        }
                                }
                            }
                        }
                    }

                    if (filteredModels.size > MAX_VISIBLE_OPENROUTER_MODELS) {
                        Text(
                            text = stringResource(R.string.too_many_models_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(
                        text = stringResource(R.string.local_model_search_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        label = {
                            Text(
                                if (isGoogleAIStudio) {
                                    stringResource(R.string.gemini_model_id)
                                } else {
                                    stringResource(R.string.model_id)
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                when (platform?.compatibleType) {
                                    ClientType.GOOGLE_AI_STUDIO -> "gemini-2.5-flash"
                                    ClientType.CUSTOM -> "provider/model-name"
                                    ClientType.OPENAI -> "gpt-4o"
                                    ClientType.ANTHROPIC -> "claude-3-5-sonnet"
                                    ClientType.QWEN -> "qwen-max"
                                    ClientType.KIMI -> "moonshot-v1-8k"
                                    ClientType.MINIMAX -> "abab6.5s-chat"
                                    ClientType.DEEPSEEK -> "deepseek-chat"
                                    ClientType.OPEN_ROUTER,
                                    null -> "Model ID"
                                }
                            )
                        },
                        supportingText = {
                            Text(
                                when (platform?.compatibleType) {
                                    ClientType.GOOGLE_AI_STUDIO ->
                                        stringResource(R.string.google_model_id_supporting)
                                    ClientType.CUSTOM ->
                                        stringResource(R.string.custom_model_id_supporting)
                                    else ->
                                        stringResource(R.string.generic_model_id_supporting)
                                }
                            )
                        },
                        singleLine = true,
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = selectedModel.trim().isNotEmpty(),
                onClick = { onConfirmRequest(selectedModel.trim()) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TemperatureEditorDialog(
    temperature: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Float?) -> Unit,
) {
    var textFieldTemperature by remember(temperature) {
        mutableStateOf(temperature?.let { "%.1f".format(it) } ?: "")
    }
    var sliderTemperature by remember(temperature) {
        mutableFloatStateOf(temperature ?: 1F)
    }
    var isUnset by remember(temperature) {
        mutableStateOf(temperature == null)
    }

    AlertDialog(
        title = { Text(stringResource(R.string.temperature_setting)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.temperature_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTemperature,
                    onValueChange = { value ->
                        textFieldTemperature = value
                        if (value.isBlank()) {
                            isUnset = true
                        } else {
                            value.toFloatOrNull()?.let {
                                sliderTemperature = it.coerceIn(0F, 2F)
                                isUnset = false
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.temperature)) },
                    placeholder = { Text(stringResource(R.string.not_set)) },
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTemperature,
                    valueRange = 0F..2F,
                    steps = 19,
                    enabled = !isUnset,
                    onValueChange = {
                        val rounded = (it * 10).roundToInt() / 10F
                        sliderTemperature = rounded
                        textFieldTemperature = "%.1f".format(rounded)
                        isUnset = false
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            textFieldTemperature = ""
                            isUnset = true
                        },
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmRequest(if (isUnset) null else sliderTemperature)
                },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TopPEditorDialog(
    topP: Float?,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Float?) -> Unit,
) {
    var textFieldTopP by remember(topP) {
        mutableStateOf(topP?.let { "%.2f".format(it) } ?: "")
    }
    var sliderTopP by remember(topP) {
        mutableFloatStateOf(topP ?: 1F)
    }
    var isUnset by remember(topP) {
        mutableStateOf(topP == null)
    }

    AlertDialog(
        title = { Text(stringResource(R.string.top_p_setting)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.top_p_setting_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldTopP,
                    onValueChange = { value ->
                        textFieldTopP = value
                        if (value.isBlank()) {
                            isUnset = true
                        } else {
                            value.toFloatOrNull()?.let {
                                val rounded =
                                    (it.coerceIn(0.1F, 1F) * 100).roundToInt() / 100F
                                sliderTopP = rounded
                                isUnset = false
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.top_p)) },
                    placeholder = { Text(stringResource(R.string.not_set)) },
                )
                Slider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = sliderTopP,
                    valueRange = 0.1F..1F,
                    steps = 89,
                    enabled = !isUnset,
                    onValueChange = {
                        val rounded = (it * 100).roundToInt() / 100F
                        sliderTopP = rounded
                        textFieldTopP = "%.2f".format(rounded)
                        isUnset = false
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            textFieldTopP = ""
                            isUnset = true
                        },
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onConfirmRequest(if (isUnset) null else sliderTopP) },
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun SystemPromptEditorDialog(
    prompt: String,
    onDismissRequest: () -> Unit,
    onConfirmRequest: (String) -> Unit,
) {
    var textFieldPrompt by remember(prompt) { mutableStateOf(prompt) }

    AlertDialog(
        title = { Text(stringResource(R.string.system_prompt_setting)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.system_prompt_description))
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    value = textFieldPrompt,
                    onValueChange = { textFieldPrompt = it },
                    label = { Text(stringResource(R.string.system_prompt)) },
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onConfirmRequest(textFieldPrompt) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
fun DeletePlatformDialog(
    dialogState: PlatformSettingViewModel.DialogState,
    settingViewModel: PlatformSettingViewModel,
) {
    if (dialogState.isDeleteDialogOpen) {
        DeletePlatformConfirmationDialog(
            onDismissRequest = settingViewModel::closeDeleteDialog,
            onConfirmRequest = settingViewModel::deletePlatform,
        )
    }
}

@Composable
private fun DeletePlatformConfirmationDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit,
) {
    AlertDialog(
        title = { Text(stringResource(R.string.delete_platform)) },
        text = { Text(stringResource(R.string.delete_platform_confirmation)) },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onConfirmRequest) {
                Text(stringResource(R.string.delete_platform))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private const val MAX_VISIBLE_OPENROUTER_MODELS = 100
