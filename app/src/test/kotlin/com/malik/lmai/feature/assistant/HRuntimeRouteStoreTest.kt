package com.malik.lmai.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HRuntimeRouteStoreTest {
    @Test
    fun `normalizes only root https supabase project endpoints`() {
        assertEquals(
            "https://abcdefgh.supabase.co",
            HRuntimeRouteStore.normalizeSupabaseBaseUrl("https://abcdefgh.supabase.co/"),
        )
        assertEquals(
            "https://abc-def123.supabase.co",
            HRuntimeRouteStore.normalizeSupabaseBaseUrl("https://ABC-def123.supabase.co"),
        )
    }

    @Test
    fun `rejects unsafe or non supabase routing endpoints`() {
        assertNull(HRuntimeRouteStore.normalizeSupabaseBaseUrl("http://abcdefgh.supabase.co"))
        assertNull(HRuntimeRouteStore.normalizeSupabaseBaseUrl("https://example.com"))
        assertNull(HRuntimeRouteStore.normalizeSupabaseBaseUrl("https://abcdefgh.supabase.co/functions/v1/h-app-sync"))
        assertNull(HRuntimeRouteStore.normalizeSupabaseBaseUrl("https://abcdefgh.supabase.co/?next=evil"))
        assertNull(HRuntimeRouteStore.normalizeSupabaseBaseUrl("https://user:pass@abcdefgh.supabase.co"))
    }
}
