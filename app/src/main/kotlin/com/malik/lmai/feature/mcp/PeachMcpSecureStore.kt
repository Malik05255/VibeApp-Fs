package com.malik.lmai.feature.mcp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeachMcpSecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class OAuthMetadata(
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val registrationEndpoint: String,
        val issuer: String,
        val scope: String,
    )

    data class PendingOAuth(
        val state: String,
        val verifier: String,
        val redirectUri: String,
        val createdAtMillis: Long,
    )

    data class TokenSet(
        val accessToken: String,
        val refreshToken: String?,
        val tokenType: String,
        val scope: String?,
        val expiresAtMillis: Long,
    )

    fun saveOAuthMetadata(value: OAuthMetadata) {
        put(KEY_AUTH_ENDPOINT, value.authorizationEndpoint)
        put(KEY_TOKEN_ENDPOINT, value.tokenEndpoint)
        put(KEY_REGISTRATION_ENDPOINT, value.registrationEndpoint)
        put(KEY_ISSUER, value.issuer)
        put(KEY_SCOPE, value.scope)
    }

    fun oauthMetadata(): OAuthMetadata? {
        val auth = get(KEY_AUTH_ENDPOINT) ?: return null
        val token = get(KEY_TOKEN_ENDPOINT) ?: return null
        val registration = get(KEY_REGISTRATION_ENDPOINT) ?: return null
        val issuer = get(KEY_ISSUER) ?: return null
        return OAuthMetadata(auth, token, registration, issuer, get(KEY_SCOPE).orEmpty())
    }

    fun saveClientId(clientId: String) = put(KEY_CLIENT_ID, clientId)
    fun clientId(): String? = get(KEY_CLIENT_ID)

    fun savePendingOAuth(value: PendingOAuth) {
        put(KEY_PENDING_STATE, value.state)
        put(KEY_PENDING_VERIFIER, value.verifier)
        put(KEY_PENDING_REDIRECT, value.redirectUri)
        preferences.edit().putLong(KEY_PENDING_CREATED_AT, value.createdAtMillis).apply()
    }

    fun pendingOAuth(): PendingOAuth? {
        val state = get(KEY_PENDING_STATE) ?: return null
        val verifier = get(KEY_PENDING_VERIFIER) ?: return null
        val redirect = get(KEY_PENDING_REDIRECT) ?: return null
        return PendingOAuth(
            state = state,
            verifier = verifier,
            redirectUri = redirect,
            createdAtMillis = preferences.getLong(KEY_PENDING_CREATED_AT, 0L),
        )
    }

    fun clearPendingOAuth() {
        listOf(KEY_PENDING_STATE, KEY_PENDING_VERIFIER, KEY_PENDING_REDIRECT).forEach(::remove)
        preferences.edit().remove(KEY_PENDING_CREATED_AT).apply()
    }

    fun saveTokens(value: TokenSet) {
        put(KEY_ACCESS_TOKEN, value.accessToken)
        value.refreshToken?.let { put(KEY_REFRESH_TOKEN, it) } ?: remove(KEY_REFRESH_TOKEN)
        put(KEY_TOKEN_TYPE, value.tokenType)
        value.scope?.let { put(KEY_TOKEN_SCOPE, it) } ?: remove(KEY_TOKEN_SCOPE)
        preferences.edit().putLong(KEY_EXPIRES_AT, value.expiresAtMillis).apply()
    }

    fun tokens(): TokenSet? {
        val access = get(KEY_ACCESS_TOKEN) ?: return null
        return TokenSet(
            accessToken = access,
            refreshToken = get(KEY_REFRESH_TOKEN),
            tokenType = get(KEY_TOKEN_TYPE) ?: "Bearer",
            scope = get(KEY_TOKEN_SCOPE),
            expiresAtMillis = preferences.getLong(KEY_EXPIRES_AT, Long.MAX_VALUE),
        )
    }

    fun clearConnection() {
        preferences.edit().clear().apply()
    }

    private fun put(key: String, value: String) {
        preferences.edit().putString(key, encrypt(value)).apply()
    }

    private fun get(key: String): String? = preferences.getString(key, null)?.let { encoded ->
        runCatching { decrypt(encoded) }.getOrNull()
    }

    private fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = ByteBuffer.allocate(4 + cipher.iv.size + ciphertext.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val buffer = ByteBuffer.wrap(packed)
        val ivSize = buffer.int
        require(ivSize in 12..32 && ivSize <= buffer.remaining()) { "Invalid encrypted value" }
        val iv = ByteArray(ivSize).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "h_peach_mcp_secure_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "h_peach_mcp_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_AUTH_ENDPOINT = "auth_endpoint"
        private const val KEY_TOKEN_ENDPOINT = "token_endpoint"
        private const val KEY_REGISTRATION_ENDPOINT = "registration_endpoint"
        private const val KEY_ISSUER = "issuer"
        private const val KEY_SCOPE = "scope"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_PENDING_STATE = "pending_state"
        private const val KEY_PENDING_VERIFIER = "pending_verifier"
        private const val KEY_PENDING_REDIRECT = "pending_redirect"
        private const val KEY_PENDING_CREATED_AT = "pending_created_at"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TYPE = "token_type"
        private const val KEY_TOKEN_SCOPE = "token_scope"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}
