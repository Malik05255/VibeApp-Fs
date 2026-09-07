package com.malik.lmai.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.malik.lmai.R
import com.malik.lmai.data.dto.OpenRouterModel
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.data.model.ModelSpeedTier
import com.malik.lmai.data.model.ModelTaskTier
import com.malik.lmai.data.model.speedTier
import com.malik.lmai.data.model.taskTier
import java.util.Locale

private enum class ModelSort {
    RECOMMENDED,
    NAME,
    CONTEXT,
    PRICE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogSelector(
    providerType: ClientType,
    selectedModel: String,
    isFreePlan: Boolean,
    models: List<OpenRouterModel>,
    isLoading: Boolean,
    enabled: Boolean = true,
    onPlanTypeChange: (Boolean) -> Unit,
    onModelSelected: (OpenRouterModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val selected = remember(models, selectedModel) {
        models.firstOrNull { it.id == selectedModel }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlanSegmentedControl(
            isFreePlan = isFreePlan,
            enabled = enabled,
            onPlanTypeChange = onPlanTypeChange,
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(enabled = enabled && !isLoading) { pickerOpen = true },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.model_picker_current_model),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = selectedModel.ifBlank {
                                stringResource(R.string.model_picker_none_selected)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                selected?.let { model ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModelTag(text = model.speedTierLabel())
                        ModelTag(text = model.taskTierLabel())
                        if (model.supportsTools) {
                            ModelTag(text = stringResource(R.string.model_filter_tools))
                        }
                        if (model.supportsReasoning) {
                            ModelTag(text = stringResource(R.string.model_filter_reasoning))
                        }
                    }
                }

                FilledTonalButton(
                    onClick = { pickerOpen = true },
                    enabled = enabled && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.model_picker_browse_models))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (providerType == ClientType.OPEN_ROUTER) {
                    stringResource(R.string.openrouter_live_catalog_note)
                } else {
                    stringResource(R.string.pricing_snapshot_note)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (pickerOpen) {
        ModelPickerScreen(
            providerType = providerType,
            selectedModel = selectedModel,
            models = models,
            isLoading = isLoading,
            onDismiss = { pickerOpen = false },
            onModelSelected = { model ->
                onModelSelected(model)
                pickerOpen = false
            },
        )
    }
}

/**
 * Full-screen picker instead of a draggable bottom sheet.
 *
 * The previous bottom sheet shared nested-scroll gestures with the LazyColumn.
 * At the end of the list, unconsumed scroll was handed to the sheet, making
 * the whole surface move a few pixels and spring back repeatedly. A dedicated
 * full-screen dialog keeps the model list as the only vertical scroll owner,
 * which removes that end-of-list jitter completely.
 */
@Composable
private fun ModelPickerScreen(
    providerType: ClientType,
    selectedModel: String,
    models: List<OpenRouterModel>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onModelSelected: (OpenRouterModel) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var toolsOnly by remember { mutableStateOf(false) }
    var reasoningOnly by remember { mutableStateOf(false) }
    var structuredOnly by remember { mutableStateOf(false) }
    var longContextOnly by remember { mutableStateOf(false) }
    var fastOnly by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(ModelSort.RECOMMENDED) }

    val providers = remember(models, providerType) {
        if (providerType == ClientType.GOOGLE_AI_STUDIO) {
            emptyList()
        } else {
            models.map { it.providerName() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
    }

    val filtered = remember(
        models,
        query,
        toolsOnly,
        reasoningOnly,
        structuredOnly,
        longContextOnly,
        fastOnly,
        selectedProvider,
        sort,
        selectedModel,
    ) {
        val needle = query.trim()
        val base = models.asSequence()
            .filter { model ->
                needle.isBlank() ||
                    model.id.contains(needle, ignoreCase = true) ||
                    model.name?.contains(needle, ignoreCase = true) == true ||
                    model.description?.contains(needle, ignoreCase = true) == true ||
                    model.providerName().contains(needle, ignoreCase = true) ||
                    model.supportedParameters.any {
                        it.contains(needle, ignoreCase = true)
                    }
            }
            .filter { !toolsOnly || it.supportsTools }
            .filter { !reasoningOnly || it.supportsReasoning }
            .filter { !structuredOnly || it.supportsStructuredOutputs }
            .filter { !longContextOnly || (it.contextLength ?: 0) >= 100_000 }
            .filter {
                !fastOnly ||
                    it.speedTier == ModelSpeedTier.VERY_FAST ||
                    it.speedTier == ModelSpeedTier.FAST
            }
            .filter { selectedProvider == null || it.providerName() == selectedProvider }
            .toList()

        when (sort) {
            ModelSort.RECOMMENDED -> base.sortedWith(
                compareByDescending<OpenRouterModel> { it.id == selectedModel }
                    .thenByDescending { it.supportsTools }
                    .thenByDescending { it.supportsReasoning }
                    .thenByDescending { it.contextLength ?: 0 }
                    .thenBy { it.id.lowercase(Locale.ROOT) }
            )

            ModelSort.NAME -> base.sortedBy {
                it.name?.lowercase(Locale.ROOT) ?: it.id.lowercase(Locale.ROOT)
            }

            ModelSort.CONTEXT -> base.sortedByDescending { it.contextLength ?: 0 }
            ModelSort.PRICE -> base.sortedBy {
                it.pricing?.averagePricePerMillion ?: Double.MAX_VALUE
            }
        }
    }

    val hasFilters = toolsOnly || reasoningOnly || structuredOnly ||
        longContextOnly || fastOnly || selectedProvider != null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.model_picker_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.model_picker_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.clear),
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            stringResource(
                                if (providerType == ClientType.GOOGLE_AI_STUDIO) {
                                    R.string.search_google_models
                                } else {
                                    R.string.search_openrouter_live_models
                                }
                            )
                        )
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = toolsOnly,
                        onClick = { toolsOnly = !toolsOnly },
                        label = { Text(stringResource(R.string.model_filter_tools)) },
                    )
                    FilterChip(
                        selected = reasoningOnly,
                        onClick = { reasoningOnly = !reasoningOnly },
                        label = { Text(stringResource(R.string.model_filter_reasoning)) },
                    )
                    FilterChip(
                        selected = structuredOnly,
                        onClick = { structuredOnly = !structuredOnly },
                        label = { Text(stringResource(R.string.model_filter_structured)) },
                    )
                    FilterChip(
                        selected = longContextOnly,
                        onClick = { longContextOnly = !longContextOnly },
                        label = { Text(stringResource(R.string.model_filter_long_context)) },
                    )
                    FilterChip(
                        selected = fastOnly,
                        onClick = { fastOnly = !fastOnly },
                        label = { Text(stringResource(R.string.model_filter_fast)) },
                    )
                }

                if (providers.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedProvider == null,
                            onClick = { selectedProvider = null },
                            label = { Text(stringResource(R.string.model_filter_all_providers)) },
                        )
                        providers.forEach { provider ->
                            FilterChip(
                                selected = selectedProvider == provider,
                                onClick = {
                                    selectedProvider = if (selectedProvider == provider) {
                                        null
                                    } else {
                                        provider
                                    }
                                },
                                label = { Text(provider) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        text = stringResource(R.string.model_picker_results_count, filtered.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    TextButton(
                        onClick = {
                            sort = when (sort) {
                                ModelSort.RECOMMENDED -> ModelSort.NAME
                                ModelSort.NAME -> ModelSort.CONTEXT
                                ModelSort.CONTEXT -> ModelSort.PRICE
                                ModelSort.PRICE -> ModelSort.RECOMMENDED
                            }
                        }
                    ) {
                        Text(sort.label())
                    }

                    if (hasFilters) {
                        TextButton(
                            onClick = {
                                toolsOnly = false
                                reasoningOnly = false
                                structuredOnly = false
                                longContextOnly = false
                                fastOnly = false
                                selectedProvider = null
                            }
                        ) {
                            Text(stringResource(R.string.model_picker_clear_filters))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.no_matching_models),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.model_picker_try_filters),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }
                            items(
                                items = filtered,
                                key = { it.id },
                            ) { model ->
                                ModelResultCard(
                                    model = model,
                                    selected = model.id == selectedModel,
                                    onClick = { onModelSelected(model) },
                                )
                            }
                            item { Spacer(Modifier.height(32.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelResultCard(
    model: OpenRouterModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name?.takeIf { it.isNotBlank() } ?: model.id,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!model.name.isNullOrBlank() && model.name != model.id) {
                        Text(
                            text = model.id,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = model.localizedUseCaseSummary(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = model.localizedCapabilitySummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ModelTag(text = model.priceLabel())
                model.contextLength?.let {
                    ModelTag(
                        text = stringResource(
                            R.string.model_context_label,
                            formatContext(it),
                        )
                    )
                }
                ModelTag(text = model.speedTierLabel())
                if (model.supportsTools) {
                    ModelTag(text = stringResource(R.string.model_filter_tools))
                }
                if (model.supportsReasoning) {
                    ModelTag(text = stringResource(R.string.model_filter_reasoning))
                }
                if (model.supportsStructuredOutputs) {
                    ModelTag(text = stringResource(R.string.model_filter_structured))
                }
            }
        }
    }
}

/**
 * Static tags deliberately use normal surface/content colors instead of a
 * disabled AssistChip. Disabled chips were rendered with a very low alpha,
 * which made price/context/speed information difficult to read.
 */
@Composable
private fun ModelTag(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlanSegmentedControl(
    isFreePlan: Boolean,
    enabled: Boolean,
    onPlanTypeChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Segment(
                text = stringResource(R.string.model_catalog_free),
                selected = isFreePlan,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onPlanTypeChange(true) },
            )
            Segment(
                text = stringResource(R.string.model_catalog_paid),
                selected = !isFreePlan,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onPlanTypeChange(false) },
            )
        }
    }
}

@Composable
private fun Segment(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .padding(3.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun OpenRouterModel.providerName(): String =
    id.substringBefore('/').takeIf { id.contains('/') } ?: "Google"

@Composable
private fun OpenRouterModel.speedTierLabel(): String =
    stringResource(
        when (speedTier) {
            ModelSpeedTier.VERY_FAST -> R.string.model_speed_very_fast
            ModelSpeedTier.FAST -> R.string.model_speed_fast
            ModelSpeedTier.BALANCED -> R.string.model_speed_balanced
            ModelSpeedTier.SLOWER -> R.string.model_speed_slower
        }
    )

@Composable
private fun OpenRouterModel.taskTierLabel(): String =
    stringResource(
        when (taskTier) {
            ModelTaskTier.SIMPLE -> R.string.model_task_simple
            ModelTaskTier.MEDIUM -> R.string.model_task_medium
            ModelTaskTier.COMPLEX -> R.string.model_task_complex
        }
    )

@Composable
private fun OpenRouterModel.localizedUseCaseSummary(): String =
    stringResource(
        when (taskTier) {
            ModelTaskTier.SIMPLE -> R.string.model_summary_simple
            ModelTaskTier.MEDIUM -> R.string.model_summary_medium
            ModelTaskTier.COMPLEX -> R.string.model_summary_complex
        }
    )

@Composable
private fun OpenRouterModel.localizedCapabilitySummary(): String {
    val parts = buildList {
        if (supportsTools) {
            add(stringResource(R.string.model_capability_tools_explained))
        }
        if (supportsReasoning) {
            add(stringResource(R.string.model_capability_reasoning_explained))
        }
        if (supportsStructuredOutputs) {
            add(stringResource(R.string.model_capability_structured_explained))
        }
        contextLength?.let {
            add(
                stringResource(
                    R.string.model_capability_context_explained,
                    formatContext(it),
                )
            )
        }
    }

    return if (parts.isEmpty()) {
        stringResource(R.string.model_capability_standard)
    } else {
        parts.joinToString(separator = " • ")
    }
}

@Composable
private fun OpenRouterModel.priceLabel(): String {
    val p = pricing
    return when {
        p?.isFree == true -> stringResource(R.string.model_price_free)
        p?.promptPricePerMillion != null && p.completionPricePerMillion != null -> {
            stringResource(
                R.string.model_price_compact,
                formatUsd(p.promptPricePerMillion!!),
                formatUsd(p.completionPricePerMillion!!),
            )
        }

        else -> stringResource(R.string.model_price_unavailable)
    }
}

@Composable
private fun ModelSort.label(): String =
    stringResource(
        when (this) {
            ModelSort.RECOMMENDED -> R.string.model_sort_recommended
            ModelSort.NAME -> R.string.model_sort_name
            ModelSort.CONTEXT -> R.string.model_sort_context
            ModelSort.PRICE -> R.string.model_sort_price
        }
    )

private fun formatContext(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.US, "%.0fK", value / 1_000f)
    else -> value.toString()
}

private fun formatUsd(value: Double): String = when {
    value >= 100.0 -> String.format(Locale.US, "%.0f", value)
    value >= 10.0 -> String.format(Locale.US, "%.2f", value)
    value >= 1.0 -> String.format(Locale.US, "%.2f", value)
    else -> String.format(Locale.US, "%.3f", value)
}
