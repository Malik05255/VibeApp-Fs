package com.malik.lmai.presentation.ui.reminder

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.malik.lmai.R
import com.malik.lmai.feature.reminder.HReminderLocation
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HReminderMapEditor(
    location: HReminderLocation,
    editable: Boolean,
    onLocationChange: (HReminderLocation) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var latitude by remember(location.latitude) { mutableDoubleStateOf(location.latitude) }
    var longitude by remember(location.longitude) { mutableDoubleStateOf(location.longitude) }
    var placeName by remember(location.placeNameAr) { mutableStateOf(location.placeNameAr) }
    var address by remember(location.addressAr) { mutableStateOf(location.addressAr.orEmpty()) }
    var searchText by remember { mutableStateOf("") }
    val marker = remember(latitude, longitude) { MarkerState(LatLng(latitude, longitude)) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 15f)
    }

    LaunchedEffect(latitude, longitude) {
        if (!editable) return@LaunchedEffect
        val resolved = reverseGeocode(context, latitude, longitude)
        if (resolved != null) {
            if (resolved.first.isNotBlank()) placeName = resolved.first
            if (resolved.second.isNotBlank()) address = resolved.second
        }
        onLocationChange(
            location.copy(
                placeNameAr = placeName.ifBlank { location.placeNameAr },
                addressAr = address.ifBlank { null },
                latitude = latitude,
                longitude = longitude,
                placeId = if (latitude == location.latitude && longitude == location.longitude) location.placeId else null,
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (editable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.h_reminder_place)) },
                )
                Button(
                    onClick = {
                        if (searchText.isNotBlank()) {
                            val query = searchText
                            scope.launch {
                                geocode(context, query)?.let { result ->
                                    latitude = result.first
                                    longitude = result.second
                                    placeName = query
                                    camera.position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 15f)
                                }
                            }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = null)
                }
            }
        }

        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            cameraPositionState = camera,
            onMapClick = if (editable) {
                { point ->
                    latitude = point.latitude
                    longitude = point.longitude
                }
            } else null,
        ) {
            Marker(
                state = marker,
                title = placeName,
                snippet = address.ifBlank { null },
            )
        }

        if (editable) {
            Text(
                text = stringResource(R.string.h_reminder_map_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(text = placeName, style = MaterialTheme.typography.titleMedium)
        if (address.isNotBlank()) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = {
                val geo = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(placeName)})")
                val intent = Intent(Intent.ACTION_VIEW, geo).apply { setPackage("com.google.android.apps.maps") }
                runCatching { context.startActivity(intent) }
                    .recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
            },
        ) {
            Icon(Icons.Outlined.Map, contentDescription = null)
            Text(
                text = stringResource(R.string.h_reminder_open_maps),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private suspend fun geocode(context: android.content.Context, query: String): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale("ar")).getFromLocationName(query, 1)
                ?.firstOrNull()
                ?.let { it.latitude to it.longitude }
        }.getOrNull()
    }

private suspend fun reverseGeocode(
    context: android.content.Context,
    latitude: Double,
    longitude: Double,
): Pair<String, String>? = withContext(Dispatchers.IO) {
    runCatching {
        @Suppress("DEPRECATION")
        val item = Geocoder(context, Locale("ar")).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            ?: return@runCatching null
        val place = listOfNotNull(item.featureName, item.locality, item.subLocality)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val full = (0..item.maxAddressLineIndex)
            .mapNotNull(item::getAddressLine)
            .joinToString("، ")
        place to full
    }.getOrNull()
}
