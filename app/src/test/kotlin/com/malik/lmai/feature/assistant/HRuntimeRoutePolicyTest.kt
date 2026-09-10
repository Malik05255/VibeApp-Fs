package com.malik.lmai.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HRuntimeRoutePolicyTest {

    @Test
    fun acceptsOnlyBareHttpsSupabaseProjectEndpoints() {
        assertEquals(
            "https://abcdefgh.supabase.co",
            HRuntimeRoutePolicy.normalizeSupabaseBaseUrl("https://abcdefgh.supabase.co/"),
        )
        assertNull(HRuntimeRoutePolicy.normalizeSupabaseBaseUrl("http://abcdefgh.supabase.co"))
        assertNull(HRuntimeRoutePolicy.normalizeSupabaseBaseUrl("https://user@abcdefgh.supabase.co"))
        assertNull(HRuntimeRoutePolicy.normalizeSupabaseBaseUrl("https://abcdefgh.supabase.co/functions/v1/x"))
        assertNull(HRuntimeRoutePolicy.normalizeSupabaseBaseUrl("https://example.com"))
    }

    @Test
    fun fallbackIsOnlyConsideredForTransportOrServerFailureBeforeDispatch() {
        assertTrue(HRuntimeRoutePolicy.primaryPreflightCanFallBack(0))
        assertTrue(HRuntimeRoutePolicy.primaryPreflightCanFallBack(500))
        assertTrue(HRuntimeRoutePolicy.primaryPreflightCanFallBack(503))
        assertFalse(HRuntimeRoutePolicy.primaryPreflightCanFallBack(401))
        assertFalse(HRuntimeRoutePolicy.primaryPreflightCanFallBack(403))
        assertFalse(HRuntimeRoutePolicy.primaryPreflightCanFallBack(409))
    }

    @Test
    fun functionUrlCannotEscapeFunctionsNamespace() {
        assertEquals(
            "https://abcdefgh.supabase.co/functions/v1/h-app-sync",
            HRuntimeRoutePolicy.functionUrl("https://abcdefgh.supabase.co", "h-app-sync"),
        )
        assertNull(HRuntimeRoutePolicy.functionUrl("https://abcdefgh.supabase.co", "../secret"))
        assertNull(HRuntimeRoutePolicy.functionUrl("https://example.com", "h-app-sync"))
    }
}
