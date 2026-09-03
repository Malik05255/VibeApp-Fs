package com.vibe.app.presentation.ui.setting

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.model.GoogleAiStudioModelCatalog
import com.vibe.app.presentation.ui.components.ModelCatalogSelector
import com.vibe.app.presentation.ui.components.PlatformTopAppBar
import com.vibe.app.util.getClientTypeDisplayName
import com.vibe.app.util.pinnedExitUntilCollapsedScrollBehavior
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformSettingScreen(
    modifier: Modifier = Modifier,
    settingViewModel: PlatformSettingViewModel = hiltViewModel(),
    onNavigationClick: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val scrollBehavior = pinnedExitUntilCollapsedScrollBehavior(
        canScroll = { scrollState.canScrollForward || scrollState.canScrollBackward }
    )

    val platform by settingViewModel.platformState.collectAsStateWithLifecycle()
    val dialogState by settingViewModel.dialogState.collectAsStateWithLifecycle()
    val isDeleted by settingViewModel.isDeleted.collectAsStateWithLifecycle()
    val openRouterModels by settingViewModel.availableModels.collectAsStateWithLifecycle()
    val isLoadingOpenRouterModels by settingViewModel.isLoadingModels.collectAsStateWithLifecycle()

    var isFreeFilter by remember(platform?.uid) {
        mutableStateOf(platform?.isFree ?: true)
    }
    var temperatureValue by remember(platform?.uid) {
        mutableFloatStateOf(platform?.temperature ?: 1f)
    }
    var topPValue by remember(platform?.uid) {
        mutableFloatStateOf(platform?.topP ?: 1f)
    }

    val context = LocalContext.current
    val switchedHint = stringResource(R.string.switched_platform_hint)

    LaunchedEffect(platform?.compatibleType, isFreeFilter, platform?.token) {
        if (
            platform?.compatibleType == ClientType.OPEN_ROUTER &&
            !platform?.token.isNullOrBlank()
        ) {
            settingViewModel.loadModels(isFreeOnly = isFreeFilter)
        }
    }

    LaunchedEffect(Unit) {
        settingViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(
                context,
                switchedHint.format(name),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(isDeleted) {
        if (isDeleted) onNavigationClick()
    }

    platform?.let { platformData ->
        val isGoogleAIStudio = platformData.compatibleType == ClientType.GOOGLE_AI_STUDIO
        val isOpenRouter = platformData.compatibleType == ClientType.OPEN_ROUTER
        val isCatalogProvider = isGoogleAIStudio || isOpenRouter
        val isReasoningDisabled =
            platformData.compatibleType == ClientType.OPENAI && platformData.reasoning

        val models = if (isGoogleAIStudio) {
            GoogleAiStudioModelCatalog.models(isFreeOnly = isFreeFilter)
        } else {
            openRouterModels
        }

        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                PlatformTopAppBar(
                    title = platformData.name,
                    onBackClick = onNavigationClick,
                    onDeleteClick = settingViewModel::openDeleteDialog,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProviderHeroCard(
                    name = platformData.name,
                    provider = getClientTypeDisplayName(platformData.compatibleType),
                    enabled = platformData.enabled,
                    onEnabledChange = { settingViewModel.toggleEnabled() },
                )

                SectionTitle(stringResource(R.string.connection_settings))

                SettingsPanel {
                    ConfigRow(
                        title = stringResource(R.string.platform_name),
                        value = platformData.name,
                        icon = Icons.Outlined.Person,
                        enabled = platformData.enabled,
                        onClick = settingViewModel::openPlatformNameDialog,
                    )
                    PanelDivider()
                    ConfigRow(
                        title = stringResource(R.string.api_url),
                        value = platformData.apiUrl,
                        icon = Icons.Outlined.Link,
                        enabled = platformData.enabled,
                        onClick = settingViewModel::openApiUrlDialog,
                    )
                    PanelDivider()
                    ConfigRow(
                        title = if (isGoogleAIStudio) {
                            stringResource(R.string.google_ai_studio_api_key)
                        } else {
                            stringResource(R.string.api_key)
                        },
                        value = if (platformData.token.isNullOrBlank()) {
                            stringResource(R.string.not_set)
                        } else {
                            "•••••${platformData.token!!.takeLast(4)}"
                        },
                        icon = Icons.Outlined.VpnKey,
                        enabled = platformData.enabled,
                        onClick = settingViewModel::openApiTokenDialog,
                    )
                }

                SectionTitle(stringResource(R.string.model_workspace))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            IconTile(Icons.Outlined.Tune)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.choose_model),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (isGoogleAIStudio) {
                                        stringResource(R.string.google_ai_studio_model_description)
                                    } else {
                                        stringResource(R.string.model_selector_hint)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        if (isCatalogProvider) {
                            ModelCatalogSelector(
                                providerType = platformData.compatibleType,
                                selectedModel = platformData.model,
                                isFreePlan = isFreeFilter,
                                models = models,
                                isLoading = isOpenRouter && isLoadingOpenRouterModels,
                                enabled = platformData.enabled,
                                onPlanTypeChange = { isFree ->
                                    isFreeFilter = isFree
                                    settingViewModel.updatePlatform(
                                        platformData.copy(isFree = isFree)
                                    )
                                    if (isOpenRouter && !platformData.token.isNullOrBlank()) {
                                        settingViewModel.loadModels(isFreeOnly = isFree)
                                    }
                                },
                                onModelSelected = { modelInfo ->
                                    settingViewModel.updateApiModel(modelInfo.id)
                                },
                            )
                        } else {
                            ConfigRow(
                                title = stringResource(R.string.api_model),
                                value = platformData.model.ifBlank {
                                    stringResource(R.string.not_set)
                                },
                                icon = Icons.Outlined.Tune,
                                enabled = platformData.enabled,
                                onClick = settingViewModel::openApiModelDialog,
                                outerPadding = false,
                            )
                        }
                    }
                }

                SectionTitle(stringResource(R.string.generation_controls))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column {
                        TuningSlider(
                            title = stringResource(R.string.temperature),
                            description = stringResource(R.string.temperature_description),
                            value = temperatureValue,
                            valueText = String.format(Locale.US, "%.1f", temperatureValue),
                            valueRange = 0f..2f,
                            icon = Icons.Outlined.Thermostat,
                            enabled = platformData.enabled && !isReasoningDisabled,
                            onValueChange = { temperatureValue = it },
                            onValueChangeFinished = {
                                settingViewModel.updatePlatform(
                                    platformData.copy(temperature = temperatureValue)
                                )
                            },
                        )

                        PanelDivider()

                        TuningSlider(
                            title = stringResource(R.string.top_p),
                            description = stringResource(R.string.top_p_description),
                            value = topPValue,
                            valueText = String.format(Locale.US, "%.2f", topPValue),
                            valueRange = 0f..1f,
                            icon = Icons.Outlined.Tune,
                            enabled = platformData.enabled && !isReasoningDisabled,
                            onValueChange = { topPValue = it },
                            onValueChangeFinished = {
                                settingViewModel.updatePlatform(
                                    platformData.copy(topP = topPValue)
                                )
                            },
                        )
                    }
                }

                PlatformNameDialog(
                    dialogState = dialogState,
                    initialValue = platformData.name,
                    settingViewModel = settingViewModel,
                )
                APIUrlDialog(
                    dialogState = dialogState,
                    initialValue = platformData.apiUrl,
                    settingViewModel = settingViewModel,
                )
                APIKeyDialog(
                    dialogState = dialogState,
                    settingViewModel = settingViewModel,
                )
                ModelDialog(
                    dialogState = dialogState,
                    model = platformData.model,
                    settingViewModel = settingViewModel,
                )
                DeletePlatformDialog(
                    dialogState = dialogState,
                    settingViewModel = settingViewModel,
                )
            }
        }
    }
}

@Composable
private fun ProviderHeroCard(
    name: String,
    provider: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(13.dp).size(26.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = provider,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun ConfigRow(
    title: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    outerPadding: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (outerPadding) 18.dp else 0.dp,
                vertical = 15.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        IconTile(icon = icon, enabled = enabled)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
    }
}

@Composable
private fun IconTile(
    icon: ImageVector,
    enabled: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.padding(9.dp).size(20.dp),
        )
    }
}

@Composable
private fun PanelDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun TuningSlider(
    title: String,
    description: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(icon = icon, enabled = enabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = valueText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            enabled = enabled,
        )
    }
}
