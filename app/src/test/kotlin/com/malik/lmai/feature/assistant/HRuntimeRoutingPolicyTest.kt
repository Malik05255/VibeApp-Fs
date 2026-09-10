package com.malik.lmai.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HRuntimeRoutingPolicyTest {
    @Test
    fun `normalizes only public Supabase project endpoints`() {
        assertEquals(
            "https://abcdefghijklmnop.supabase.co",
            HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint("https://abcdefghijklmnop.supabase.co/"),
        )
        assertNull(HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint("http://abcdefghijklmnop.supabase.co"))
        assertNull(HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint("https://user:pass@abcdefghijklmnop.supabase.co"))
        assertNull(HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint("https://abcdefghijklmnop.supabase.co/functions/v1/h-app-sync"))
        assertNull(HRuntimeRoutingPolicy.normalizeStandbyBaseEndpoint("https://example.com"))
    }

    @Test
    fun `derives only allowlisted H app functions`() {
        val base = "https://abcdefghijklmnop.supabase.co"
        assertEquals(
            "$base/functions/v1/h-app-sync",
            HRuntimeRoutingPolicy.standbyFunctionUrl(
                base,
                "https://primaryproject.supabase.co/functions/v1/h-app-sync",
            ),
        )
        assertEquals(
            "$base/functions/v1/h-app-media",
            HRuntimeRoutingPolicy.standbyFunctionUrl(
                base,
                "https://primaryproject.supabase.co/functions/v1/h-app-media",
            ),
        )
        assertNull(
            HRuntimeRoutingPolicy.standbyFunctionUrl(
                base,
                "https://primaryproject.supabase.co/functions/v1/h-cloud-manager",
            ),
        )
    }

    @Test
    fun `status URL never carries credentials`() {
        assertEquals(
            "https://abcdefghijklmnop.supabase.co/functions/v1/h-app-runtime-status",
            HRuntimeRoutingPolicy.standbyStatusUrl("https://abcdefghijklmnop.supabase.co"),
        )
    }
}
