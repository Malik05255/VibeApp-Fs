package com.malik.lmai.feature.reminder

import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class HGeofencePolicyTest {
    @Test
    fun depart_uses_exit_for_transition_and_initial_state() {
        assertEquals(Geofence.GEOFENCE_TRANSITION_EXIT, HGeofencePolicy.transitionTypes(HLocationTriggerMode.DEPART))
        assertEquals(GeofencingRequest.INITIAL_TRIGGER_EXIT, HGeofencePolicy.initialTrigger(HLocationTriggerMode.DEPART))
    }

    @Test
    fun arrive_and_nearby_use_enter() {
        listOf(HLocationTriggerMode.ARRIVE, HLocationTriggerMode.NEARBY).forEach { mode ->
            assertEquals(Geofence.GEOFENCE_TRANSITION_ENTER, HGeofencePolicy.transitionTypes(mode))
            assertEquals(GeofencingRequest.INITIAL_TRIGGER_ENTER, HGeofencePolicy.initialTrigger(mode))
        }
    }

    @Test
    fun dwell_registers_only_dwell_without_synthetic_initial_transition() {
        assertEquals(Geofence.GEOFENCE_TRANSITION_DWELL, HGeofencePolicy.transitionTypes(HLocationTriggerMode.DWELL))
        assertEquals(0, HGeofencePolicy.initialTrigger(HLocationTriggerMode.DWELL))
    }
}
