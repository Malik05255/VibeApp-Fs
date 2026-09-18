package com.malik.lmai.presentation.ui.reminder

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malik.lmai.R
import com.malik.lmai.feature.reminder.HGeoPoint
import com.malik.lmai.feature.reminder.HLocationScopeConfig
import com.malik.lmai.feature.reminder.HReminderLocation
import java.text.DateFormat
import java.util.Date

/**
 * User-facing controls for H's place-resolution scope.
 * The radius here filters search candidates only; it never registers a 100 km Android geofence.
 */
@Composable
fun HLocationScopeSettingsCard(
    viewModel: HLocationScopeViewModel = hiltViewModel(),
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val anchorDefaultLabel = stringResource(R.string.h_location_anchor_default_label)
    var editingAnchor by remember { mutableStateOf(false) }
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val action = pendingLocationAction
        pendingLocationAction = null
        action?.invoke()
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun runWithLocationPermission(action: () -> Unit) {
        if (hasLocationPermission()) {
            action()
        } else {
            pendingLocationAction = action
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.h_location_scope_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.h_location_scope_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val base = config.baseAnchor
            Text(
                if (base == null) stringResource(R.string.h_location_anchor_unset)
                else stringResource(R.string.h_location_anchor_value, base.label ?: formatPoint(base)),
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { runWithLocationPermission(viewModel::useCurrentAsBase) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (base == null) stringResource(R.string.h_location_set_current) else stringResource(R.string.h_location_update_current))
                }
                if (base != null) {
                    OutlinedButton(onClick = { editingAnchor = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.h_location_edit_map))
                    }
                }
            }

            Text(stringResource(R.string.h_location_search_radius, formatRadius(config.radiusKm)), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HLocationScopeConfig.RADIUS_PRESETS_KM.forEach { radius ->
                    FilterChip(
                        selected = config.radiusKm == radius,
                        onClick = { viewModel.setRadiusKm(radius) },
                        label = { Text(stringResource(R.string.h_location_radius_km, radius.toInt())) },
                    )
                }
            }

            HorizontalDivider()

            val travel = config.travelAnchor
            if (travel == null) {
                Text(
                    stringResource(R.string.h_location_travel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { runWithLocationPermission { viewModel.startTravelFromCurrent(24) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.h_location_travel_start_24h))
                }
            } else {
                val travelLabel = travel.label ?: formatPoint(travel)
                val travelText = config.travelExpiresAtMs?.let {
                    stringResource(
                        R.string.h_location_travel_active_until,
                        travelLabel,
                        DateFormat.getDateTimeInstance().format(Date(it)),
                    )
                } ?: stringResource(R.string.h_location_travel_active, travelLabel)
                Text(
                    travelText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                OutlinedButton(onClick = viewModel::stopTravel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.h_location_travel_stop))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.h_location_explicit_override_title), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.h_location_explicit_override_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = config.explicitDistantOverride,
                    onCheckedChange = viewModel::setExplicitOverride,
                )
            }

            if (base != null) {
                TextButton(onClick = viewModel::clearBaseAnchor, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.h_location_clear_anchor))
                }
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (editingAnchor) {
        val base = config.baseAnchor
        if (base == null) {
            editingAnchor = false
        } else {
            var draft by remember(base) {
                mutableStateOf(
                    HReminderLocation(
                        placeNameAr = base.label ?: anchorDefaultLabel,
                        latitude = base.latitude,
                        longitude = base.longitude,
                    )
                )
            }
            ModalBottomSheet(onDismissRequest = { editingAnchor = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.h_location_edit_anchor_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    HReminderMapEditor(
                        location = draft,
                        editable = true,
                        onLocationChange = { draft = it },
                    )
                    Button(
                        onClick = {
                            viewModel.setBaseAnchor(
                                HGeoPoint(
                                    latitude = draft.latitude,
                                    longitude = draft.longitude,
                                    label = draft.placeNameAr.ifBlank { anchorDefaultLabel },
                                )
                            )
                            editingAnchor = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    ) {
                        Text(stringResource(R.string.h_location_save_anchor))
                    }
                }
            }
        }
    }
}

private fun formatPoint(point: HGeoPoint): String =
    "%.4f, %.4f".format(point.latitude, point.longitude)

@Composable
private fun formatRadius(radiusKm: Double): String =
    if (radiusKm % 1.0 == 0.0) {
        stringResource(R.string.h_location_radius_km, radiusKm.toInt())
    } else {
        stringResource(R.string.h_location_radius_km_decimal, radiusKm)
    }
