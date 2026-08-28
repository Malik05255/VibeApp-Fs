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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.VideoCameraBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.almi.ai.R
import com.almi.ai.data.preferences.CustomAiConfig

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_api_title), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text(
                stringResource(R.string.custom_api_heading),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.custom_api_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.custom_api_connection), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(16.dp),
                    )
                    Text(
                        stringResource(R.string.custom_api_secure_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            CapabilityEditor(
                icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) },
                title = stringResource(R.string.custom_api_analysis),
                subtitle = stringResource(R.string.custom_api_analysis_body),
                endpoint = analysisEndpoint,
                model = analysisModel,
                onEndpoint = { analysisEndpoint = it; saved = false },
                onModel = { analysisModel = it; saved = false },
            )
            CapabilityEditor(
                icon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                title = stringResource(R.string.custom_api_images),
                subtitle = stringResource(R.string.custom_api_images_body),
                endpoint = imageEndpoint,
                model = imageModel,
                onEndpoint = { imageEndpoint = it; saved = false },
                onModel = { imageModel = it; saved = false },
            )
            CapabilityEditor(
                icon = { Icon(Icons.Outlined.VideoCameraBack, contentDescription = null) },
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(stringResource(R.string.custom_api_saved), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CapabilityEditor(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    endpoint: String,
    model: String,
    onEndpoint: (String) -> Unit,
    onModel: (String) -> Unit,
) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    androidx.compose.foundation.layout.Box(Modifier.padding(9.dp)) { icon() }
                }
                Column(Modifier.weight(1f)) {
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
        shape = RoundedCornerShape(16.dp),
    )
}
