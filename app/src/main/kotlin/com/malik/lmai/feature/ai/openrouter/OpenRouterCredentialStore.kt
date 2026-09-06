package com.malik.lmai.feature.ai.openrouter

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenRouterCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun saveApiKey(apiKey: String) = saveEncrypted(API_KEY, API_KEY_IV, apiKey.trim())

    fun getApiKey(): String? = readEncrypted(API_KEY, API_KEY_IV)

    fun savePendingOAuth(verifier: String, callbackUrl: String, createdAtMillis: Long) {
        saveEncrypted(OAUTH_VERIFIER, OAUTH_VERIFIER_IV, verifier)
        preferences.edit()
            .putString(OAUTH_CALLBACK, callbackUrl)
            .putLong(OAUTH_CREATED_AT, createdAtMillis)
            .apply()
    }

    fun pendingOAuth(): PendingOAuth? {
        val verifier = readEncrypted(OAUTH_VERIFIER, OAUTH_VERIFIER_IV) ?: return null
        val callback = preferences.getString(OAUTH_CALLBACK, null) ?: return null
        val createdAt = preferences.getLong(OAUTH_CREATED_AT, 0L)
        if (createdAt <= 0L) return null
        return PendingOAuth(verifier, callback, createdAt)
    }

    fun clearPendingOAuth() {
        preferences.edit()
            .remove(OAUTH_VERIFIER)
            .remove(OAUTH_VERIFIER_IV)
            .remove(OAUTH_CALLBACK)
            .remove(OAUTH_CREATED_AT)
            .apply()
    }

    fun clearApiKey() {
        preferences.edit().remove(API_KEY).remove(API_KEY_IV).apply()
    }

    private fun saveEncrypted(valueKey: String, ivKey: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(valueKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun readEncrypted(valueKey: String, ivKey: String): String? = runCatching {
        val encrypted = preferences.getString(valueKey, null) ?: return null
        val iv = preferences.getString(ivKey, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    data class PendingOAuth(
        val verifier: String,
        val callbackUrl: String,
        val createdAtMillis: Long,
    )

    companion object {
        const val PLATFORM_TOKEN_SENTINEL = "oauth://openrouter"
        private const val PREFERENCES = "lmai_openrouter_credentials"
        private const val API_KEY = "api_key"
        private const val API_KEY_IV = "api_key_iv"
        private const val OAUTH_VERIFIER = "oauth_verifier"
        private const val OAUTH_VERIFIER_IV = "oauth_verifier_iv"
        private const val OAUTH_CALLBACK = "oauth_callback"
        private const val OAUTH_CREATED_AT = "oauth_created_at"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "lmai_openrouter_oauth_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
