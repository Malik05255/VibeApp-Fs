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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.almi.ai.R
import com.almi.ai.data.preferences.CustomAiConfig
import com.almi.ai.ui.components.GlassIconTile
import com.almi.ai.ui.components.GlassSurface
import com.almi.ai.ui.components.LuxeBackdrop
import com.almi.ai.ui.components.StatusPill

@Composable
fun CustomApiSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val config by viewModel.customAiConfig.collectAsState()
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

    val pending = CustomAiConfig(
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
                        Text(stringResource(R.string.custom_api_title), style = MaterialTheme.typography.titleMedium)
                        Text("Custom provider", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(if (pending.isUsable) stringResource(R.string.ai_hub_active) else stringResource(R.string.ai_hub_inactive), positive = pending.isUsable)
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
                    Text(stringResource(R.string.custom_api_heading), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.custom_api_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                GlassSurface(modifier = Modifier.fillMaxWidth(), emphasized = true) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassIconTile(Icons.Outlined.Key, active = true)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.custom_api_connection), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.custom_api_secure_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Field(providerName, { providerName = it; saved = false }, stringResource(R.string.custom_api_provider))
                        Field(baseUrl, { baseUrl = it; saved = false }, "Base URL")
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it; saved = false },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API key") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions.Default,
                            singleLine = true,
                            shape = RoundedCornerShape(17.dp),
                        )
                    }
                }

                CapabilityEditor(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    title = stringResource(R.string.custom_api_analysis),
                    subtitle = stringResource(R.string.custom_api_analysis_body),
                    endpoint = analysisEndpoint,
                    model = analysisModel,
                    onEndpoint = { analysisEndpoint = it; saved = false },
                    onModel = { analysisModel = it; saved = false },
                )
                CapabilityEditor(
                    icon = Icons.Outlined.Image,
                    title = stringResource(R.string.custom_api_images),
                    subtitle = stringResource(R.string.custom_api_images_body),
                    endpoint = imageEndpoint,
                    model = imageModel,
                    onEndpoint = { imageEndpoint = it; saved = false },
                    onModel = { imageModel = it; saved = false },
                )
                CapabilityEditor(
                    icon = Icons.Outlined.VideoCameraBack,
                    title = stringResource(R.string.custom_api_video),
                    subtitle = stringResource(R.string.custom_api_video_body),
                    endpoint = videoEndpoint,
                    model = videoModel,
                    onEndpoint = { videoEndpoint = it; saved = false },
                    onModel = { videoModel = it; saved = false },
                )

                Button(
                    onClick = {
                        viewModel.saveAndActivateCustom(pending)
                        saved = true
                    },
                    enabled = pending.isUsable,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.custom_api_save), fontWeight = FontWeight.Bold)
                }

                if (saved) {
                    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), emphasized = true) {
                        Row(
                            modifier = Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Text(stringResource(R.string.custom_api_saved), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CapabilityEditor(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    endpoint: String,
    model: String,
    onEndpoint: (String) -> Unit,
    onModel: (String) -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconTile(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Field(endpoint, onEndpoint, stringResource(R.string.custom_api_endpoint))
            Field(model, onModel, stringResource(R.string.custom_api_model))
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(17.dp),
    )
}
