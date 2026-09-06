package com.malik.lmai.feature.github

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
class GitHubCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(token.trim().toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(TOKEN, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun getToken(): String? = runCatching {
        val encrypted = preferences.getString(TOKEN, null) ?: return null
        val iv = preferences.getString(IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }.getOrNull()

    fun saveSelectedRepository(fullName: String) {
        preferences.edit().putString(SELECTED_REPOSITORY, fullName).apply()
    }

    fun getSelectedRepository(): String? = preferences.getString(SELECTED_REPOSITORY, null)

    fun clearSelectedRepository() {
        preferences.edit().remove(SELECTED_REPOSITORY).apply()
    }

    fun savePendingOAuth(state: String, verifier: String) {
        preferences.edit()
            .putString(OAUTH_STATE, state)
            .putString(OAUTH_VERIFIER, verifier)
            .apply()
    }

    fun getPendingOAuthState(): String? = preferences.getString(OAUTH_STATE, null)

    fun getPendingOAuthVerifier(): String? = preferences.getString(OAUTH_VERIFIER, null)

    fun clearPendingOAuth() {
        preferences.edit()
            .remove(OAUTH_STATE)
            .remove(OAUTH_VERIFIER)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(TOKEN)
            .remove(IV)
            .remove(SELECTED_REPOSITORY)
            .remove(OAUTH_STATE)
            .remove(OAUTH_VERIFIER)
            .apply()
    }

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

    companion object {
        private const val PREFERENCES = "github_credentials"
        private const val TOKEN = "token"
        private const val IV = "token_iv"
        private const val SELECTED_REPOSITORY = "selected_repository"
        private const val OAUTH_STATE = "oauth_state"
        private const val OAUTH_VERIFIER = "oauth_verifier"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "lmai_github_token"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
