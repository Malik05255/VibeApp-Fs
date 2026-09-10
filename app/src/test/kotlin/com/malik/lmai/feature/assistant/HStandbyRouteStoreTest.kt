package com.malik.lmai.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HStandbyRouteStoreTest {
    @Test
    fun acceptsOnlyPlainHttpsSupabaseProjectBaseUrls() {
        assertEquals(
            "https://exampleproject.supabase.co",
            HStandbyRouteStore.normalizeEndpoint("https://exampleproject.supabase.co"),
        )
        assertEquals(
            "https://exampleproject.supabase.co",
            HStandbyRouteStore.normalizeEndpoint("https://EXAMPLEPROJECT.supabase.co/"),
        )
    }

    @Test
    fun rejectsPathsCredentialsQueriesFragmentsPortsAndNonSupabaseHosts() {
        assertNull(HStandbyRouteStore.normalizeEndpoint("http://exampleproject.supabase.co"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://user@exampleproject.supabase.co"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://exampleproject.supabase.co/functions/v1/h-app-sync"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://exampleproject.supabase.co?token=x"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://exampleproject.supabase.co#fragment"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://exampleproject.supabase.co:8443"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://example.com"))
    }

    @Test
    fun acceptsOnlyExactHttpsWorkersDevFailoverWitnessUrls() {
        assertEquals(
            "https://h-witness.example.workers.dev/h-app-failover-route",
            HStandbyRouteStore.normalizeFailoverControlUrl(
                "https://H-WITNESS.EXAMPLE.workers.dev/h-app-failover-route",
            ),
        )
    }

    @Test
    fun rejectsUntrustedOrCredentialBearingFailoverWitnessUrls() {
        assertNull(HStandbyRouteStore.normalizeFailoverControlUrl("http://h.example.workers.dev/h-app-failover-route"))
        assertNull(HStandbyRouteStore.normalizeFailoverControlUrl("https://user@h.example.workers.dev/h-app-failover-route"))
        assertNull(HStandbyRouteStore.normalizeFailoverControlUrl("https://h.example.workers.dev/other"))
        assertNull(HStandbyRouteStore.normalizeFailoverControlUrl("https://h.example.workers.dev/h-app-failover-route?token=x"))
        assertNull(HStandbyRouteStore.normalizeFailoverControlUrl("https://h.example.workers.dev/h-app-failover-route#x"))
        assertNull(HStandbyRouteStore.normalizeFailoverControlUrl("https://example.com/h-app-failover-route"))
    }
}
