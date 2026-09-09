package com.malik.lmai.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HAppMediaPolicyTest {

    @Test
    fun `small text is derived locally and never sent raw`() {
        val decision = HAppMediaPolicy.decide(
            mimeType = "text/plain",
            sizeBytes = 12_000,
        )
        assertTrue(decision.allowed)
        assertTrue(decision.localTextDerivation)
        assertFalse(decision.transientCloudAllowed)
    }

    @Test
    fun `bounded image can use transient cloud`() {
        val decision = HAppMediaPolicy.decide(
            mimeType = "image/jpeg",
            sizeBytes = 700_000,
        )
        assertTrue(decision.allowed)
        assertTrue(decision.transientCloudAllowed)
    }

    @Test
    fun `oversized image is reduced locally first`() {
        val decision = HAppMediaPolicy.decide(
            mimeType = "image/png",
            sizeBytes = HAppMediaPolicy.MAX_RAW_MEDIA_BYTES + 1,
        )
        assertTrue(decision.allowed)
        assertTrue(decision.localImageReductionRecommended)
        assertFalse(decision.transientCloudAllowed)
    }

    @Test
    fun `audio over three minutes fails closed`() {
        val decision = HAppMediaPolicy.decide(
            mimeType = "audio/mpeg",
            sizeBytes = 2_000_000,
            durationMs = HAppMediaPolicy.MAX_RAW_AUDIO_VIDEO_DURATION_MS + 1,
        )
        assertFalse(decision.allowed)
        assertEquals("raw_audio_over_180_seconds", decision.reason)
    }

    @Test
    fun `long video can continue through local frame derivation`() {
        val decision = HAppMediaPolicy.decide(
            mimeType = "video/mp4",
            sizeBytes = 4_000_000,
            durationMs = 12 * 60_000L,
        )
        assertTrue(decision.allowed)
        assertTrue(decision.localVideoFramesRecommended)
        assertFalse(decision.transientCloudAllowed)
    }

    @Test
    fun `unsupported media never falls through`() {
        val decision = HAppMediaPolicy.decide(
            mimeType = "application/zip",
            sizeBytes = 1_000,
        )
        assertFalse(decision.allowed)
        assertEquals(HAppMediaKind.UNSUPPORTED, decision.kind)
    }
}
