package com.malik.lmai.feature.assistant

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HCloudStandbyRoutePolicyTest {
    @Test
    fun `standby project endpoint accepts only clean https supabase origin`() {
        assertEquals(
            "https://abcdefghijklmnop.supabase.co",
            HCloudRouteStore.normalizeProjectEndpoint("https://abcdefghijklmnop.supabase.co/"),
        )
        assertNull(HCloudRouteStore.normalizeProjectEndpoint("http://abcdefghijklmnop.supabase.co"))
        assertNull(HCloudRouteStore.normalizeProjectEndpoint("https://evil.example.com"))
        assertNull(HCloudRouteStore.normalizeProjectEndpoint("https://abcdefghijklmnop.supabase.co/functions/v1"))
        assertNull(HCloudRouteStore.normalizeProjectEndpoint("https://user:pass@abcdefghijklmnop.supabase.co"))
        assertNull(HCloudRouteStore.normalizeProjectEndpoint("https://abcdefghijklmnop.supabase.co?token=secret"))
    }

    @Test
    fun `function route keeps operation name while switching project`() {
        assertEquals(
            "https://standbyproject.supabase.co/functions/v1/h-app-media",
            HCloudLinkClient.endpointForFunction(
                "https://standbyproject.supabase.co/functions/v1",
                "https://primaryproject.supabase.co/functions/v1/h-app-media",
            ),
        )
        assertEquals(
            "https://standbyproject.supabase.co",
            HCloudLinkClient.projectEndpointFromFunctionBase(
                "https://standbyproject.supabase.co/functions/v1",
            ),
        )
    }

    @Test
    fun `only availability class failures can trigger standby preflight`() {
        listOf(0, 404, 408, 500, 502, 503, 504).forEach {
            assertTrue("$it should be availability failure", HCloudLinkClient.isAvailabilityFailure(it))
        }
        listOf(200, 400, 401, 403, 409, 422, 429).forEach {
            assertFalse("$it must not trigger failover", HCloudLinkClient.isAvailabilityFailure(it))
        }
    }

    @Test
    fun `promotion response contract requires fenced request only active state`() {
        val valid = buildJsonObject {
            put("ok", true)
            put("promoted", true)
            put("active", true)
            put("mode", "request_only")
            put("requestId", "1234567890abcdef")
            put("replicaWritesFenced", true)
            put("schedulerActive", false)
            put("autonomousOutboundActive", false)
        }
        assertTrue(HCloudLinkClient.jsonBoolean(valid, "promoted"))
        assertEquals("request_only", HCloudLinkClient.jsonString(valid, "mode"))
        assertFalse(HCloudLinkClient.jsonBoolean(valid, "schedulerActive"))
    }
}