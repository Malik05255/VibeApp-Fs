package com.malik.lmai.presentation.ui.auth

import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleIdTokenProviderTest {

    @Test
    fun `token with more than five minutes remaining is fresh`() {
        val now = 2_000_000_000L
        val token = jwt("{\"exp\":${now + 601}}")

        assertTrue(GoogleIdTokenFreshness.isFresh(token, nowEpochSeconds = now))
    }

    @Test
    fun `token at refresh window is treated as stale`() {
        val now = 2_000_000_000L
        val token = jwt("{\"exp\":${now + 300}}")

        assertFalse(GoogleIdTokenFreshness.isFresh(token, nowEpochSeconds = now))
    }

    @Test
    fun `expired malformed or expiry-less tokens are stale`() {
        val now = 2_000_000_000L

        assertFalse(
            GoogleIdTokenFreshness.isFresh(
                jwt("{\"exp\":${now - 1}}"),
                nowEpochSeconds = now,
            )
        )
        assertFalse(GoogleIdTokenFreshness.isFresh("not-a-jwt", nowEpochSeconds = now))
        assertFalse(
            GoogleIdTokenFreshness.isFresh(
                jwt("{\"sub\":\"owner\"}"),
                nowEpochSeconds = now,
            )
        )
    }

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("{\"alg\":\"RS256\"}".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature"
    }
}
