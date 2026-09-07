package com.malik.lmai.presentation.ui.setup

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.ai.FreeAiProviderPreset
import com.malik.lmai.presentation.ui.components.ModelCatalogSelector
import com.malik.lmai.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_API_KEY
import com.malik.lmai.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_BASICS
import com.malik.lmai.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_STEP_MODEL
import com.malik.lmai.presentation.ui.setup.SetupViewModelV2.Companion.WIZARD_TOTAL_STEPS

@Composable
fun SetupPlatformWizardScreen(
    modifier: Modifier = Modifier,
    setupViewModel: SetupViewModelV2 = hiltViewModel(),
    onComplete: () -> Unit,
    onBackAction: () -> Unit,
) {
    val wizardStep by setupViewModel.wizardStep.collectAsStateWithLifecycle()
    val selectedClientType by setupViewModel.selectedClientType.collectAsStateWithLifecycle()
    val providerPreset by setupViewModel.providerPreset.collectAsStateWithLifecycle()
    val platformName by setupViewModel.platformName.collectAsStateWithLifecycle()
    val apiUrl by setupViewModel.apiUrl.collectAsStateWithLifecycle()
    val apiKey by setupViewModel.apiKey.collectAsStateWithLifecycle()
    val model by setupViewModel.model.collectAsStateWithLifecycle()
    val isFreePlan by setupViewModel.isFreePlan.collectAsStateWithLifecycle()
    val modelsFetchStatus by setupViewModel.modelsFetchStatus.collectAsStateWithLifecycle()
    val saveStatus by setupViewModel.saveStatus.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val switchedHint = stringResource(R.string.switched_platform_hint)
    val savePlatformFailedText = stringResource(R.string.save_platform_failed)

    LaunchedEffect(Unit) {
        setupViewModel.switchedPlatformEvent.collect { name ->
            Toast.makeText(context, switchedHint.format(name), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(saveStatus) {
        when (val status = saveStatus) {
            SaveStatus.Success -> {
                setupViewModel.clearSaveStatus()
                onComplete()
            }
            is SaveStatus.Error -> {
                Toast.makeText(
                    context,
                    status.message.ifBlank { savePlatformFailedText },
                    Toast.LENGTH_LONG,
                ).show()
                setupViewModel.clearSaveStatus()
            }
            else -> Unit
        }
    }

    val isSaving = saveStatus is SaveStatus.Saving
    val canProceed by remember(
        wizardStep,
        selectedClientType,
        providerPreset,
        platformName,
        apiUrl,
        apiKey,
        model,
    ) {
        derivedStateOf {
            when (wizardStep) {
                WIZARD_STEP_BASICS -> platformName.isNotBlank() && apiUrl.isNotBlank()
                WIZARD_STEP_API_KEY ->
                    (selectedClientType == ClientType.CUSTOM && providerPreset == null) ||
                        apiKey.isNotBlank()
                WIZARD_STEP_MODEL -> model.isNotBlank()
                else -> false
            }
        }
    }

    fun goBack() {
        if (wizardStep > WIZARD_STEP_BASICS) {
            setupViewModel.previousWizardStep()
        } else {
            setupViewModel.resetWizard()
            onBackAction()
        }
    }

    BackHandler { goBack() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SetupAppBar(backAction = ::goBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding(),
        ) {
            WizardHeader(
                currentStep = wizardStep,
                totalSteps = WIZARD_TOTAL_STEPS,
            )

            when (wizardStep) {
                WIZARD_STEP_BASICS -> BasicsStep(
                    clientType = selectedClientType,
                    platformName = platformName,
                    onPlatformNameChange = setupViewModel::updatePlatformName,
                    apiUrl = apiUrl,
                    onApiUrlChange = setupViewModel::updateApiUrl,
                    modifier = Modifier.weight(1f),
                )
                WIZARD_STEP_API_KEY -> ApiKeyStep(
                    clientType = selectedClientType,
                    providerPreset = providerPreset,
                    apiKey = apiKey,
                    onApiKeyChange = setupViewModel::updateApiKey,
                    modifier = Modifier.weight(1f),
                )
                WIZARD_STEP_MODEL -> ModelStep(
                    clientType = selectedClientType,
                    model = model,
                    onModelChange = setupViewModel::updateModel,
                    isFreePlan = isFreePlan,
                    onPlanTypeChange = setupViewModel::updatePlanType,
                    modelsFetchStatus = modelsFetchStatus,
                    modifier = Modifier.weight(1f),
                )
            }

            WizardNavigationButtons(
                currentStep = wizardStep,
                canProceed = canProceed,
                isSaving = isSaving,
                onBack = ::goBack,
                onNext = {
                    if (wizardStep < WIZARD_TOTAL_STEPS - 1) {
                        setupViewModel.nextWizardStep()
                    } else {
                        setupViewModel.savePlatform()
                    }
                },
                isLastStep = wizardStep == WIZARD_TOTAL_STEPS - 1,
            )
        }
    }
}

@Composable
private fun WizardHeader(
    currentStep: Int,
    totalSteps: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = when (currentStep) {
                    WIZARD_STEP_BASICS -> stringResource(R.string.step_basics)
                    WIZARD_STEP_API_KEY -> stringResource(R.string.step_api_key)
                    else -> stringResource(R.string.step_model)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.step_x_of_y, currentStep + 1, totalSteps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LinearProgressIndicator(
            progress = { (currentStep + 1).toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WizardCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        content()
    }
}

@Composable
private fun WizardTitle(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WizardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    supporting: String,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        supportingText = { Text(supporting) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun BasicsStep(
    clientType: ClientType?,
    platformName: String,
    onPlatformNameChange: (String) -> Unit,
    apiUrl: String,
    onApiUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        WizardCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                WizardTitle(
                    title = stringResource(R.string.step_basics),
                    description = stringResource(R.string.platform_basics_description),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WizardTextField(
                    value = platformName,
                    onValueChange = onPlatformNameChange,
                    label = stringResource(R.string.platform_name),
                    placeholder = stringResource(R.string.platform_name_hint),
                    supporting = stringResource(R.string.platform_name_supporting),
                )
                WizardTextField(
                    value = apiUrl,
                    onValueChange = onApiUrlChange,
                    label = stringResource(R.string.api_url),
                    placeholder = stringResource(R.string.api_url_hint),
                    supporting = if (clientType == ClientType.GOOGLE_AI_STUDIO) {
                        stringResource(R.string.google_ai_studio_api_url_supporting)
                    } else {
                        stringResource(R.string.api_url_cautions)
                    },
                )
            }
        }
    }
}

@Composable
private fun ApiKeyStep(
    clientType: ClientType?,
    providerPreset: FreeAiProviderPreset?,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val customKeyOptional = clientType == ClientType.CUSTOM && providerPreset == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        WizardCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                WizardTitle(
                    title = stringResource(R.string.step_api_key),
                    description = when {
                        clientType == ClientType.GOOGLE_AI_STUDIO ->
                            stringResource(R.string.google_ai_studio_api_key_description)
                        customKeyOptional ->
                            stringResource(R.string.custom_api_key_optional_description)
                        else ->
                            stringResource(R.string.api_key_description)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                WizardTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = when {
                        clientType == ClientType.GOOGLE_AI_STUDIO ->
                            stringResource(R.string.google_ai_studio_api_key)
                        customKeyOptional ->
                            stringResource(R.string.custom_api_key_optional_label)
                        else ->
                            stringResource(R.string.api_key)
                    },
                    placeholder = stringResource(R.string.api_key_hint),
                    supporting = when {
                        clientType == ClientType.GOOGLE_AI_STUDIO ->
                            stringResource(R.string.google_ai_studio_api_key_supporting)
                        customKeyOptional ->
                            stringResource(R.string.custom_api_key_optional_supporting)
                        else ->
                            stringResource(R.string.api_key_supporting)
                    },
                    password = true,
                )

                val helpUrl = providerPreset?.apiKeyHelpUrl
                    ?: clientType?.let(::getApiHelpUrl)
                helpUrl?.let { url ->
                    Text(
                        text = stringResource(R.string.need_help),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { uriHandler.openUri(url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStep(
    clientType: ClientType?,
    model: String,
    onModelChange: (String) -> Unit,
    isFreePlan: Boolean,
    onPlanTypeChange: (Boolean) -> Unit,
    modelsFetchStatus: ModelsFetchStatus,
    modifier: Modifier = Modifier,
) {
    val isCatalogProvider = clientType == ClientType.OPEN_ROUTER || clientType == ClientType.GOOGLE_AI_STUDIO
    val models = (modelsFetchStatus as? ModelsFetchStatus.Success)?.models.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        WizardCard {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                WizardTitle(
                    title = stringResource(R.string.step_model),
                    description = if (clientType == ClientType.GOOGLE_AI_STUDIO) {
                        stringResource(R.string.google_ai_studio_model_description)
                    } else {
                        stringResource(R.string.model_description)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (isCatalogProvider && clientType != null) {
                    ModelCatalogSelector(
                        providerType = clientType,
                        selectedModel = model,
                        isFreePlan = isFreePlan,
                        models = models,
                        isLoading = modelsFetchStatus is ModelsFetchStatus.Loading,
                        onPlanTypeChange = onPlanTypeChange,
                        onModelSelected = { onModelChange(it.id) },
                    )
                    if (modelsFetchStatus is ModelsFetchStatus.Error) {
                        Text(
                            text = modelsFetchStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    WizardTextField(
                        value = model,
                        onValueChange = onModelChange,
                        label = stringResource(R.string.model),
                        placeholder = stringResource(R.string.model_name),
                        supporting = stringResource(R.string.model_supporting),
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardNavigationButtons(
    currentStep: Int,
    canProceed: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    isLastStep: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.weight(1f),
            enabled = !isSaving,
            shape = RoundedCornerShape(15.dp),
        ) {
            Text(
                if (currentStep == WIZARD_STEP_BASICS) {
                    stringResource(R.string.cancel)
                } else {
                    stringResource(R.string.back)
                }
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
            enabled = canProceed && !isSaving,
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            if (isLastStep && isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(
                    if (isLastStep) {
                        stringResource(R.string.finish)
                    } else {
                        stringResource(R.string.next)
                    }
                )
            }
        }
    }
}

private fun getApiHelpUrl(clientType: ClientType): String? = when (clientType) {
    ClientType.GOOGLE_AI_STUDIO -> "https://aistudio.google.com/apikey"
    ClientType.OPEN_ROUTER -> "https://openrouter.ai/keys"
    ClientType.CUSTOM -> null
    else -> null
}
