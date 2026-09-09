package com.malik.lmai.presentation.ui.setting

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R

/**
 * Owner-facing control for H's optional BYOK/paid helper.
 *
 * No provider API key is entered, cached, or persisted by Android. The app creates an
 * owner-authenticated short-lived setup link, then the browser performs key validation and
 * the mandatory second-step live-price approval directly against H Cloud.
 */
@Composable
fun HOwnerPaidAiSettingsCard(
    modifier: Modifier = Modifier,
    viewModel: HOwnerPaidAiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var modelId by rememberSaveable { mutableStateOf("") }
    var dailyLimitText by rememberSaveable { mutableStateOf("5") }
    var hardTasksOnly by rememberSaveable { mutableStateOf(true) }
    var allowFreeFallback by rememberSaveable { mutableStateOf(false) }
    var hydratedFromServer by remember { mutableStateOf(false) }

    LaunchedEffect(state.connected, state.selectedModel) {
        if (!hydratedFromServer && state.connected) {
            state.selectedModel?.let { modelId = it }
            if (state.dailyCallLimit in 1..100) dailyLimitText = state.dailyCallLimit.toString()
            hardTasksOnly = state.hardTasksOnly
            allowFreeFallback = state.allowFreeFallback
            hydratedFromServer = true
        }
    }

    LaunchedEffect(state.pendingSetupUrl) {
        val url = state.pendingSetupUrl ?: return@LaunchedEffect
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.isSuccess
        if (!opened) {
            Toast.makeText(context, R.string.h_owner_paid_open_link_failed, Toast.LENGTH_LONG).show()
        }
        viewModel.consumeSetupUrl()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.h_owner_paid_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.h_owner_paid_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.loading) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }

            if (!state.linked) {
                Text(
                    text = stringResource(R.string.h_owner_paid_link_h_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.connected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (state.enabled) {
                                stringResource(R.string.h_owner_paid_enabled)
                            } else {
                                stringResource(R.string.h_owner_paid_disabled)
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        state.selectedModel?.let {
                            Text(
                                text = stringResource(R.string.h_owner_paid_current_model, it),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.h_owner_paid_usage_today,
                                state.callsUsedToday,
                                state.dailyCallLimit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.h_owner_paid_cost_today, state.costUsdToday),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.priceGuard && state.explicitPriceReview) {
                            Text(
                                text = stringResource(R.string.h_owner_paid_price_guard_active),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = modelId,
                onValueChange = { modelId = it.take(200) },
                enabled = !state.loading && state.linked,
                singleLine = true,
                label = { Text(stringResource(R.string.h_owner_paid_model_id)) },
                supportingText = { Text(stringResource(R.string.h_owner_paid_model_hint)) },
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = dailyLimitText,
                onValueChange = { value ->
                    dailyLimitText = value.filter(Char::isDigit).take(3)
                },
                enabled = !state.loading && state.linked,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.h_owner_paid_daily_limit)) },
                supportingText = { Text(stringResource(R.string.h_owner_paid_daily_limit_hint)) },
            )

            HOwnerPaidSwitchRow(
                title = stringResource(R.string.h_owner_paid_hard_tasks_only),
                description = stringResource(R.string.h_owner_paid_hard_tasks_only_desc),
                checked = hardTasksOnly,
                enabled = !state.loading && state.linked,
                onCheckedChange = { hardTasksOnly = it },
            )

            HOwnerPaidSwitchRow(
                title = stringResource(R.string.h_owner_paid_free_fallback),
                description = stringResource(R.string.h_owner_paid_free_fallback_desc),
                checked = allowFreeFallback,
                enabled = !state.loading && state.linked,
                onCheckedChange = { allowFreeFallback = it },
            )

            Text(
                text = stringResource(R.string.h_owner_paid_safety_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val dailyLimit = dailyLimitText.toIntOrNull() ?: 0
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.loading && state.linked && modelId.isNotBlank() && dailyLimit in 1..100,
                onClick = {
                    viewModel.createSetupLink(
                        selectedModel = modelId,
                        dailyCallLimit = dailyLimit,
                        hardTasksOnly = hardTasksOnly,
                        allowFreeFallback = allowFreeFallback,
                    )
                },
            ) {
                Text(
                    if (state.connected) {
                        stringResource(R.string.h_owner_paid_review_change)
                    } else {
                        stringResource(R.string.h_owner_paid_review_connect)
                    },
                )
            }

            if (state.connected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.enabled) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !state.loading,
                            onClick = viewModel::disable,
                        ) {
                            Text(stringResource(R.string.h_owner_paid_disable))
                        }
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        enabled = !state.loading,
                        onClick = viewModel::disconnect,
                    ) {
                        Text(
                            text = stringResource(R.string.h_owner_paid_disconnect),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            state.error?.let { error ->
                Text(
                    text = ownerPaidErrorText(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HOwnerPaidSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ownerPaidErrorText(error: String): String = when (error) {
    "app_not_linked" -> stringResource(R.string.h_owner_paid_link_h_first)
    "google_sign_in_required", "google_token_refresh_failed" ->
        stringResource(R.string.h_owner_paid_google_required)
    "invalid_owner_paid_setup" -> stringResource(R.string.h_owner_paid_invalid_setup)
    else -> stringResource(R.string.h_owner_paid_generic_error, error)
}
