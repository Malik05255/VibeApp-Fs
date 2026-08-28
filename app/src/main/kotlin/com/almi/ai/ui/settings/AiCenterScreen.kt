package com.almi.ai.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AiMode
import com.almi.ai.data.preferences.CustomAiConfig
import com.almi.ai.data.preferences.OpenRouterConfig
import com.almi.ai.data.repository.DiscoveredProvider
import com.almi.ai.data.repository.ModelCapability
import com.almi.ai.data.repository.OpenRouterCatalog
import com.almi.ai.data.repository.OpenRouterModelInfo
import com.almi.ai.ui.components.AiOrb3D
import com.almi.ai.ui.components.ConnectionPill
import com.almi.ai.ui.components.DimensionCard
import com.almi.ai.ui.components.Glossy3DIcon

private enum class AiPage { HOME, OPENROUTER, CUSTOM, FREE }
private enum class OpenRouterConnectMode { AUTO, MANUAL }
private enum class CustomMediaType { IMAGE, VIDEO }

@Composable
fun AiCenterScreen(
    viewModel: SettingsViewModel,
    language: String,
) {
    val aiMode by viewModel.aiMode.collectAsState()
    var page by rememberSaveable { mutableStateOf(AiPage.HOME) }

    BackHandler(enabled = page != AiPage.HOME) { page = AiPage.HOME }

    when (page) {
        AiPage.HOME -> AiHome(aiMode, language, onOpen = { page = it })
        AiPage.OPENROUTER -> OpenRouterPane(viewModel, language, onBack = { page = AiPage.HOME })
        AiPage.CUSTOM -> CustomPane(viewModel, language, onBack = { page = AiPage.HOME })
        AiPage.FREE -> FreePane(viewModel, language, onBack = { page = AiPage.HOME })
    }
}

@Composable
private fun AiHome(
    mode: AiMode,
    language: String,
    onOpen: (AiPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ALMI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    if (language == "ar") "الذكاء الاصطناعي" else "Artificial Intelligence",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionPill(
                when (mode) {
                    AiMode.OPENROUTER -> "OpenRouter"
                    AiMode.CUSTOM -> if (language == "ar") "مخصص" else "Custom"
                    AiMode.FREE_AUTO -> if (language == "ar") "مجاني" else "Free"
                }
            )
        }

        DimensionCard(emphasized = true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                AiOrb3D(label = "AI")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (language == "ar") "موديل الذكاء الاصطناعي الحالي" else "Current AI model",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        when (mode) {
                            AiMode.OPENROUTER -> "OpenRouter"
                            AiMode.CUSTOM -> if (language == "ar") "API مخصص" else "Custom API"
                            AiMode.FREE_AUTO -> if (language == "ar") "مجاني تلقائي" else "Automatic free"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        GatewayRow(
            title = "OpenRouter",
            subtitle = if (language == "ar") "ربط مباشر أو API يدوي" else "Direct connect or manual API",
            active = mode == AiMode.OPENROUTER,
            icon = Icons.Outlined.Route,
            onClick = { onOpen(AiPage.OPENROUTER) },
        )
        GatewayRow(
            title = if (language == "ar") "API مخصص" else "Custom API",
            subtitle = if (language == "ar") "إنشاء الصور والفيديو" else "Image and video generation",
            active = mode == AiMode.CUSTOM,
            icon = Icons.Outlined.Key,
            onClick = { onOpen(AiPage.CUSTOM) },
        )
        GatewayRow(
            title = if (language == "ar") "ذكاء اصطناعي مجاني" else "Free AI",
            subtitle = if (language == "ar") "بدون مفتاح شخصي" else "No personal API key",
            active = mode == AiMode.FREE_AUTO,
            icon = Icons.Outlined.AutoAwesome,
            onClick = { onOpen(AiPage.FREE) },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GatewayRow(
    title: String,
    subtitle: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DimensionCard(onClick = onClick, emphasized = active) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Glossy3DIcon(icon, active = active)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) {
                ConnectionPill("ON")
            } else {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun OpenRouterPane(
    viewModel: SettingsViewModel,
    language: String,
    onBack: () -> Unit,
) {
    val state by viewModel.openRouterState.collectAsState()
    val config by viewModel.openRouterConfig.collectAsState()
    val keys by viewModel.apiKeys.collectAsState()
    val oauth by viewModel.oauthState.collectAsState()
    var connectMode by rememberSaveable { mutableStateOf(OpenRouterConnectMode.AUTO) }
    var manualKey by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var capability by rememberSaveable { mutableStateOf(ModelCapability.IMAGE) }

    val directCatalog = state.catalog.filtered(true)
    val catalog = if (connectMode == OpenRouterConnectMode.AUTO) directCatalog else state.catalog.filtered(config.freeOnly)
    val models = modelsFor(catalog, capability)
        .filter { query.isBlank() || it.name.contains(query, true) || it.id.contains(query, true) }
    val connected = state.keyStatus?.connected == true || oauth.connected

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubHeader(
            onBack = onBack,
            title = "OpenRouter",
            subtitle = if (connected) {
                if (language == "ar") "متصل وجاهز" else "Connected and ready"
            } else {
                if (language == "ar") "اختر طريقة الاتصال" else "Choose a connection method"
            },
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton(
                selected = connectMode == OpenRouterConnectMode.AUTO,
                label = if (language == "ar") "ربط مباشر" else "Direct connect",
                onClick = {
                    connectMode = OpenRouterConnectMode.AUTO
                    viewModel.setOpenRouterFreeOnly(true)
                },
                modifier = Modifier.weight(1f),
            )
            ModeButton(
                selected = connectMode == OpenRouterConnectMode.MANUAL,
                label = if (language == "ar") "API يدوي" else "Manual API",
                onClick = { connectMode = OpenRouterConnectMode.MANUAL },
                modifier = Modifier.weight(1f),
            )
        }

        if (connectMode == OpenRouterConnectMode.AUTO) {
            DirectOpenRouterCard(
                connected = connected,
                isConnecting = oauth.isConnecting,
                catalog = directCatalog,
                remainingUsd = state.keyStatus?.remainingUsd,
                language = language,
                error = oauth.error,
                onConnect = viewModel::connectOpenRouterAutomatically,
                onRefresh = viewModel::refreshOpenRouter,
            )
        } else {
            ManualOpenRouterCard(
                keyValue = manualKey,
                onKeyChange = { manualKey = it },
                freeOnly = config.freeOnly,
                onFreeOnlyChange = viewModel::setOpenRouterFreeOnly,
                connected = state.keyStatus?.connected == true,
                enabledKeyCount = keys.count { it.enabled },
                language = language,
                onSave = {
                    viewModel.addManualOpenRouterKey(manualKey, config.freeOnly)
                    manualKey = ""
                },
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (language == "ar") "اختيار الموديل" else "Choose model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = viewModel::refreshOpenRouter) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = capability == ModelCapability.IMAGE,
                onClick = { capability = ModelCapability.IMAGE },
                label = { Text(if (language == "ar") "صور" else "Images") },
            )
            FilterChip(
                selected = capability == ModelCapability.VIDEO,
                onClick = { capability = ModelCapability.VIDEO },
                label = { Text(if (language == "ar") "فيديو" else "Video") },
            )
            FilterChip(
                selected = capability == ModelCapability.TEXT,
                onClick = { capability = ModelCapability.TEXT },
                label = { Text(if (language == "ar") "نص" else "Text") },
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(if (language == "ar") "بحث عن موديل" else "Search models") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().height(86.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            models.take(30).forEach { model ->
                ModelRow(
                    model = model,
                    capability = capability,
                    selected = selectedModel(config, capability),
                    language = language,
                ) {
                    viewModel.selectOpenRouterModel(capability, model.id)
                    viewModel.activateOpenRouter()
                }
            }
            if (models.isEmpty()) {
                DimensionCard {
                    Text(
                        if (language == "ar") "لا توجد نماذج مطابقة متاحة الآن." else "No matching models are available right now.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DirectOpenRouterCard(
    connected: Boolean,
    isConnecting: Boolean,
    catalog: OpenRouterCatalog,
    remainingUsd: Double?,
    language: String,
    error: String?,
    onConnect: () -> Unit,
    onRefresh: () -> Unit,
) {
    DimensionCard(emphasized = connected) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Glossy3DIcon(Icons.Outlined.Route, active = connected)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (language == "ar") "الربط المباشر" else "Direct connection",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (connected) {
                            if (language == "ar") "OpenRouter متصل داخل ALMI" else "OpenRouter is connected inside ALMI"
                        } else {
                            if (language == "ar") "يربط حسابك ويحفظ المفتاح تلقائيًا داخل الجهاز" else "Connects your account and stores the key on-device automatically"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConnectionPill(
                    text = if (connected) {
                        if (language == "ar") "متصل" else "Connected"
                    } else {
                        if (language == "ar") "غير متصل" else "Offline"
                    },
                    connected = connected,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(
                    value = catalog.imageModels.size.toString(),
                    label = if (language == "ar") "صور مجانية" else "Free images",
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    value = catalog.videoModels.size.toString(),
                    label = if (language == "ar") "فيديو مجاني" else "Free video",
                    modifier = Modifier.weight(1f),
                )
                MiniStat(
                    value = catalog.textModels.size.toString(),
                    label = if (language == "ar") "نص مجاني" else "Free text",
                    modifier = Modifier.weight(1f),
                )
            }

            remainingUsd?.let {
                Text(
                    "${if (language == "ar") "الرصيد المتبقي" else "Remaining balance"}: $${"%.3f".format(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!error.isNullOrBlank()) {
                Text(
                    if (language == "ar") "تعذر إكمال الربط. حاول مرة أخرى." else "Connection could not be completed. Try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = if (connected) onRefresh else onConnect,
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                } else {
                    Icon(
                        if (connected) Icons.Outlined.Refresh else Icons.Outlined.Route,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    when {
                        isConnecting -> if (language == "ar") "جارٍ إكمال الربط…" else "Connecting…"
                        connected -> if (language == "ar") "تحديث النماذج" else "Refresh models"
                        else -> if (language == "ar") "اتصال مباشر بـ OpenRouter" else "Connect directly to OpenRouter"
                    }
                )
            }

            Text(
                if (language == "ar") "في الربط المباشر يعرض ALMI النماذج المجانية فقط وينتقل تلقائيًا إلى البديل عند فشل الموديل المختار." else "Direct mode shows free models only and automatically falls back if the selected model fails.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MiniStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    DimensionCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ManualOpenRouterCard(
    keyValue: String,
    onKeyChange: (String) -> Unit,
    freeOnly: Boolean,
    onFreeOnlyChange: (Boolean) -> Unit,
    connected: Boolean,
    enabledKeyCount: Int,
    language: String,
    onSave: () -> Unit,
) {
    DimensionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Glossy3DIcon(Icons.Outlined.Key, active = connected)
                Column(Modifier.weight(1f)) {
                    Text(if (language == "ar") "مفتاحك الخاص" else "Your API key", fontWeight = FontWeight.Bold)
                    Text(
                        if (language == "ar") "يمكنك استخدام المجاني أو المدفوع" else "Use free or paid models",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ConnectionPill(
                    if (connected) {
                        if (language == "ar") "متصل" else "Connected"
                    } else {
                        if (language == "ar") "غير متصل" else "Offline"
                    },
                    connected,
                )
            }

            OutlinedTextField(
                value = keyValue,
                onValueChange = onKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenRouter API key") },
                placeholder = { Text("sk-or-…") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(if (language == "ar") "النماذج المجانية فقط" else "Free models only", fontWeight = FontWeight.SemiBold)
                    if (enabledKeyCount > 0) {
                        Text(
                            "${if (language == "ar") "مفاتيح محفوظة" else "Saved keys"}: $enabledKeyCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = freeOnly, onCheckedChange = onFreeOnlyChange)
            }

            Button(
                onClick = onSave,
                enabled = keyValue.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(17.dp),
            ) {
                Text(if (language == "ar") "حفظ وربط" else "Save & connect")
            }
        }
    }
}

private fun modelsFor(catalog: OpenRouterCatalog, capability: ModelCapability): List<OpenRouterModelInfo> = when (capability) {
    ModelCapability.TEXT -> catalog.textModels
    ModelCapability.IMAGE -> catalog.imageModels
    ModelCapability.VIDEO -> catalog.videoModels
}

private fun selectedModel(config: OpenRouterConfig, capability: ModelCapability): String = when (capability) {
    ModelCapability.TEXT -> config.analysisModel
    ModelCapability.IMAGE -> config.imageModel
    ModelCapability.VIDEO -> config.videoModel
}

@Composable
private fun ModelRow(
    model: OpenRouterModelInfo,
    capability: ModelCapability,
    selected: String,
    language: String,
    onClick: () -> Unit,
) {
    val isSelected = selected == model.id
    DimensionCard(onClick = onClick, emphasized = isSelected) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isSelected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(model.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    model.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (model.isFree) {
                    if (language == "ar") "مجاني" else "Free"
                } else {
                    priceLabel(model, capability)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (model.isFree) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun priceLabel(model: OpenRouterModelInfo, capability: ModelCapability): String = when (capability) {
    ModelCapability.IMAGE -> model.imageUsdPerUnit?.let { "$${"%.4f".format(it)}/img" } ?: "Paid"
    ModelCapability.VIDEO -> model.videoUsdPerSecond?.let { "$${"%.4f".format(it)}/s" } ?: "Paid"
    ModelCapability.TEXT -> model.inputUsdPerMillion?.let { "$${"%.2f".format(it)}/1M" } ?: "Paid"
}

@Composable
private fun CustomPane(
    viewModel: SettingsViewModel,
    language: String,
    onBack: () -> Unit,
) {
    val saved by viewModel.customAiConfig.collectAsState()
    var config by remember(saved) { mutableStateOf(saved) }
    var mediaType by rememberSaveable { mutableStateOf(CustomMediaType.IMAGE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubHeader(
            onBack = onBack,
            title = if (language == "ar") "API مخصص" else "Custom API",
            subtitle = if (language == "ar") "للصور والفيديو فقط" else "Image and video only",
        )

        DimensionCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Glossy3DIcon(Icons.Outlined.Key, active = true)
                    Column(Modifier.weight(1f)) {
                        Text(if (language == "ar") "بيانات المزود" else "Provider connection", fontWeight = FontWeight.Bold)
                        Text(
                            if (language == "ar") "أدخل بيانات API المتوافقة" else "Enter compatible API details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CompactField(
                    config.providerName,
                    { config = config.copy(providerName = it) },
                    if (language == "ar") "اسم المزود" else "Provider",
                )
                CompactField(config.baseUrl, { config = config.copy(baseUrl = it) }, "Base URL")
                CompactField(config.apiKey, { config = config.copy(apiKey = it) }, "API key", secret = true)
            }
        }

        Text(
            if (language == "ar") "نوع الإنشاء" else "Generation type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaChoice(
                selected = mediaType == CustomMediaType.IMAGE,
                label = if (language == "ar") "الصور ▾" else "Images ▾",
                onClick = { mediaType = CustomMediaType.IMAGE },
                modifier = Modifier.weight(1f),
            )
            MediaChoice(
                selected = mediaType == CustomMediaType.VIDEO,
                label = if (language == "ar") "الفيديو ▾" else "Video ▾",
                onClick = { mediaType = CustomMediaType.VIDEO },
                modifier = Modifier.weight(1f),
            )
        }

        when (mediaType) {
            CustomMediaType.IMAGE -> MediaConfigCard(
                title = if (language == "ar") "إنشاء الصور" else "Image generation",
                endpoint = config.imageEndpoint,
                model = config.imageModel,
                onEndpoint = { config = config.copy(imageEndpoint = it) },
                onModel = { config = config.copy(imageModel = it) },
                language = language,
            )
            CustomMediaType.VIDEO -> MediaConfigCard(
                title = if (language == "ar") "إنشاء الفيديو" else "Video generation",
                endpoint = config.videoEndpoint,
                model = config.videoModel,
                onEndpoint = { config = config.copy(videoEndpoint = it) },
                onModel = { config = config.copy(videoModel = it) },
                language = language,
            )
        }

        Button(
            onClick = {
                viewModel.saveAndActivateCustom(
                    config.copy(
                        analysisEndpoint = "",
                        analysisModel = "",
                    )
                )
            },
            enabled = config.baseUrl.isNotBlank() && config.apiKey.isNotBlank() &&
                config.imageEndpoint.isNotBlank() && config.imageModel.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(17.dp),
        ) {
            Text(if (language == "ar") "حفظ وتفعيل API المخصص" else "Save & activate custom API")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MediaChoice(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text(label)
        }
    }
}

@Composable
private fun MediaConfigCard(
    title: String,
    endpoint: String,
    model: String,
    onEndpoint: (String) -> Unit,
    onModel: (String) -> Unit,
    language: String,
) {
    DimensionCard(emphasized = true) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            CompactField(endpoint, onEndpoint, "Endpoint")
            CompactField(model, onModel, "Model ID")
            Text(
                if (language == "ar") "يمكن أن يكون Endpoint مسارًا مثل /images أو رابطًا كاملًا." else "Endpoint can be a path such as /images or a full URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FreePane(
    viewModel: SettingsViewModel,
    language: String,
    onBack: () -> Unit,
) {
    val mode by viewModel.aiMode.collectAsState()
    val state by viewModel.providerDiscoveryState.collectAsState()
    val enabled = mode == AiMode.FREE_AUTO
    val active = state.result.providers.firstOrNull { it.id == state.activeProviderId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SubHeader(
            onBack,
            if (language == "ar") "ذكاء اصطناعي مجاني" else "Free AI",
            if (language == "ar") "بدون مفتاح شخصي" else "No personal API key",
        )

        DimensionCard(emphasized = enabled) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AiOrb3D(Modifier.size(96.dp), "AI")
                Column(Modifier.weight(1f)) {
                    Text(if (language == "ar") "تشغيل تلقائي" else "Automatic", fontWeight = FontWeight.Bold)
                    Text(
                        active?.name ?: if (language == "ar") "لا يوجد مزود متصل" else "No provider connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = viewModel::setFreeMode)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(if (language == "ar") "المزودات بدون مفتاح" else "No-key providers", fontWeight = FontWeight.Bold)
            IconButton(onClick = viewModel::discoverFreeProviders) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
            }
        }

        if (state.isChecking) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            state.result.providers.forEach { provider ->
                ProviderRow(provider, provider.id == state.activeProviderId, language)
            }
            if (state.result.providers.isEmpty()) {
                Text(
                    if (language == "ar") "لا يوجد مزود متاح حاليًا" else "No provider available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProviderRow(
    provider: DiscoveredProvider,
    active: Boolean,
    language: String,
) {
    DimensionCard(emphasized = active) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Glossy3DIcon(Icons.Outlined.AutoAwesome, active = active)
            Column(Modifier.weight(1f)) {
                Text(provider.name, fontWeight = FontWeight.Bold)
                val caps = buildList {
                    if (provider.supportsText) add(if (language == "ar") "نص" else "Text")
                    if (provider.supportsImage) add(if (language == "ar") "صور" else "Images")
                    if (provider.supportsVideo) add(if (language == "ar") "فيديو" else "Video")
                }.joinToString(" • ")
                Text(caps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ConnectionPill(
                if (provider.connected) {
                    if (language == "ar") "متصل" else "Connected"
                } else {
                    if (language == "ar") "غير متاح" else "Unavailable"
                },
                provider.connected,
            )
        }
    }
}

@Composable
private fun SubHeader(
    onBack: () -> Unit,
    title: String,
    subtitle: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Button(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(14.dp)) {
            Text(label)
        }
    }
}

@Composable
private fun CompactField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
    )
}