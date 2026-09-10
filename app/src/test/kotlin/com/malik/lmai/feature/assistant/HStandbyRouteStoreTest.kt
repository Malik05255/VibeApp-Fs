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
            HStandbyRouteStore.normalizeEndpoint("https://EXAMPLEPROJECT.supabase.co/path/ignored"),
        )
    }

    @Test
    fun rejectsCredentialsQueriesFragmentsAndNonSupabaseHosts() {
        assertNull(HStandbyRouteStore.normalizeEndpoint("http://exampleproject.supabase.co"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://user@exampleproject.supabase.co"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://exampleproject.supabase.co?token=x"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://exampleproject.supabase.co#fragment"))
        assertNull(HStandbyRouteStore.normalizeEndpoint("https://example.com"))
    }
}
