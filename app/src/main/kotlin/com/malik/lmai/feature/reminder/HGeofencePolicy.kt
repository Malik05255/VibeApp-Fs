package com.malik.lmai.feature.reminder

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest

/** Pure mapping used by H's location reminder scheduler. */
object HGeofencePolicy {
    fun transitionTypes(mode: HLocationTriggerMode): Int = when (mode) {
        HLocationTriggerMode.ARRIVE,
        HLocationTriggerMode.NEARBY -> Geofence.GEOFENCE_TRANSITION_ENTER
        HLocationTriggerMode.DEPART -> Geofence.GEOFENCE_TRANSITION_EXIT
        HLocationTriggerMode.DWELL -> Geofence.GEOFENCE_TRANSITION_DWELL
    }

    fun initialTrigger(mode: HLocationTriggerMode): Int = when (mode) {
        HLocationTriggerMode.ARRIVE,
        HLocationTriggerMode.NEARBY -> GeofencingRequest.INITIAL_TRIGGER_ENTER
        HLocationTriggerMode.DEPART -> GeofencingRequest.INITIAL_TRIGGER_EXIT
        HLocationTriggerMode.DWELL -> 0
    }
}
