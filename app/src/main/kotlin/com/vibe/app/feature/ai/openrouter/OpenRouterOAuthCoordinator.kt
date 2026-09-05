package com.vibe.app.feature.ai.openrouter

import android.net.Uri
import android.util.Base64
import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.repository.SettingRepository
import com.vibe.app.feature.ai.AiProviderOrigin
import com.vibe.app.feature.ai.FreeAiRouter
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterOAuthCoordinator @Inject constructor(
    private val api: OpenRouterOAuthApi,
    private val credentialStore: OpenRouterCredentialStore,
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
) {
    fun begin(callbackUrl: String): String {
        require(callbackUrl.isNotBlank()) { "OpenRouter OAuth callback URL is not configured" }
        val verifier = randomVerifier()
        val challenge = codeChallenge(verifier)
        credentialStore.savePendingOAuth(verifier, callbackUrl, System.currentTimeMillis())
        return api.buildAuthorizationUrl(callbackUrl, challenge)
    }

    suspend fun complete(uri: Uri): Result<Unit> = runCatching {
        val pending = credentialStore.pendingOAuth() ?: error("No OpenRouter OAuth session is pending")
        try {
            check(System.currentTimeMillis() - pending.createdAtMillis <= SESSION_TTL_MILLIS) {
                "OpenRouter OAuth session expired"
            }
            check(matchesCallback(uri, pending.callbackUrl)) { "Unexpected OpenRouter OAuth callback" }
            val oauthError = uri.getQueryParameter("error")
            if (!oauthError.isNullOrBlank()) {
                error("OpenRouter authorization was rejected: $oauthError")
            }
            val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
                ?: error("OpenRouter authorization code is missing")
            val apiKey = api.exchangeCode(code, pending.verifier)
            credentialStore.saveApiKey(apiKey)
            upsertHiddenFreeRoute()
        } finally {
            credentialStore.clearPendingOAuth()
        }
    }

    fun isConnected(): Boolean = !credentialStore.getApiKey().isNullOrBlank()

    suspend fun disconnect() {
        credentialStore.clearApiKey()
        settingRepository.fetchPlatformV2s()
            .firstOrNull { platform ->
                freeAiRouter.isInternalFree(platform) &&
                    freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
            }
            ?.let { settingRepository.deletePlatformV2(it) }
    }

    private suspend fun upsertHiddenFreeRoute() {
        val platforms = settingRepository.fetchPlatformV2s()
        val existing = platforms.firstOrNull { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER
        }
        val route = existing?.copy(
            name = DISPLAY_NAME,
            compatibleType = ClientType.OPEN_ROUTER,
            apiUrl = API_URL,
            token = OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL,
            model = FREE_MODEL,
            provider = AiProviderOrigin.internalProviderCode("openrouter"),
            isFree = true,
            stream = true,
        ) ?: PlatformV2(
            name = DISPLAY_NAME,
            compatibleType = ClientType.OPEN_ROUTER,
            enabled = false,
            apiUrl = API_URL,
            token = OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL,
            model = FREE_MODEL,
            provider = AiProviderOrigin.internalProviderCode("openrouter"),
            isFree = true,
            temperature = 0.7f,
            topP = 0.95f,
            stream = true,
            reasoning = false,
            timeout = 90,
        )
        if (existing == null) settingRepository.addPlatformV2(route)
        else settingRepository.updatePlatformV2(route)
    }

    private fun matchesCallback(uri: Uri, callbackUrl: String): Boolean {
        val expected = Uri.parse(callbackUrl)
        return uri.scheme == expected.scheme && uri.host == expected.host && uri.path == expected.path
    }

    private fun randomVerifier(): String {
        val bytes = ByteArray(64).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val API_URL = "https://openrouter.ai/api/v1"
        const val FREE_MODEL = "openrouter/free"
        const val DISPLAY_NAME = "OpenRouter Free"
        private const val SESSION_TTL_MILLIS = 10 * 60 * 1000L
    }
}
