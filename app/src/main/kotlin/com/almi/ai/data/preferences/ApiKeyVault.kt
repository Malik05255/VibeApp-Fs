package com.almi.ai.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Device-local API key vault backed by Android Keystore AES/GCM.
 * Provider secrets are never written to SharedPreferences as plaintext by this class.
 */
@Singleton
class ApiKeyVault @Inject constructor(
    @ApplicationContext context: Context,
    private val legacyPreferences: AlmiPreferences,
) {
    private val preferences = context.getSharedPreferences(VAULT_PREFERENCES, Context.MODE_PRIVATE)
    private val _keys = MutableStateFlow(readKeys())
    val keys: StateFlow<List<ApiKeyRecord>> = _keys.asStateFlow()

    init {
        migrateLegacyOpenRouterKeys()
    }

    fun addOpenRouterKey(secret: String, label: String = "OpenRouter"): ApiKeyRecord? {
        val normalized = secret.trim()
        if (normalized.isBlank()) return null

        val existing = _keys.value.firstOrNull { it.provider == ApiKeyProvider.OPENROUTER && it.secret == normalized }
        if (existing != null) return existing

        val record = ApiKeyRecord(
            id = UUID.randomUUID().toString(),
            provider = ApiKeyProvider.OPENROUTER,
            label = label.trim().ifBlank { "OpenRouter" },
            secret = normalized,
            enabled = true,
            createdAt = System.currentTimeMillis(),
        )
        persist(_keys.value + record)
        return record
    }

    fun remove(id: String) {
        persist(_keys.value.filterNot { it.id == id })
    }

    fun setEnabled(id: String, enabled: Boolean) {
        persist(_keys.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun activeOpenRouterKeys(): List<ApiKeyRecord> =
        _keys.value
            .filter { it.enabled && it.provider == ApiKeyProvider.OPENROUTER && it.secret.isNotBlank() }
            .sortedBy { it.createdAt }

    private fun migrateLegacyOpenRouterKeys() {
        val legacyCandidates = listOf(
            legacyPreferences.currentFreeOpenRouterApiKey(),
            legacyPreferences.currentCustomAiConfig().apiKey,
        ).map(String::trim).filter(String::isNotBlank).distinct()

        if (legacyCandidates.isEmpty()) return
        var current = _keys.value
        var changed = false
        legacyCandidates.forEachIndexed { index, key ->
            if (current.none { it.provider == ApiKeyProvider.OPENROUTER && it.secret == key }) {
                current = current + ApiKeyRecord(
                    id = UUID.randomUUID().toString(),
                    provider = ApiKeyProvider.OPENROUTER,
                    label = if (index == 0) "OpenRouter" else "OpenRouter migrated",
                    secret = key,
                    enabled = true,
                    createdAt = System.currentTimeMillis() + index,
                )
                changed = true
            }
        }
        if (changed) persist(current)
    }

    private fun persist(records: List<ApiKeyRecord>) {
        val payload = JSONArray().apply {
            records.forEach { record ->
                put(
                    JSONObject()
                        .put("id", record.id)
                        .put("provider", record.provider.name)
                        .put("label", record.label)
                        .put("secret", record.secret)
                        .put("enabled", record.enabled)
                        .put("createdAt", record.createdAt)
                )
            }
        }.toString()

        preferences.edit().putString(KEY_ENCRYPTED_KEYS, encrypt(payload)).apply()
        _keys.value = records
    }

    private fun readKeys(): List<ApiKeyRecord> {
        val encrypted = preferences.getString(KEY_ENCRYPTED_KEYS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(decrypt(encrypted))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val secret = item.optString("secret").trim()
                    if (secret.isBlank()) continue
                    add(
                        ApiKeyRecord(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            provider = runCatching {
                                ApiKeyProvider.valueOf(item.optString("provider"))
                            }.getOrDefault(ApiKeyProvider.OPENROUTER),
                            label = item.optString("label").ifBlank { "OpenRouter" },
                            secret = secret,
                            enabled = item.optBoolean("enabled", true),
                            createdAt = item.optLong("createdAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split('.', limit = 2)
        require(parts.size == 2) { "invalid_vault_payload" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val VAULT_PREFERENCES = "almi_ai_secure_key_vault"
        private const val KEY_ENCRYPTED_KEYS = "encrypted_api_keys_v1"
        private const val KEY_ALIAS = "almi_ai_api_vault_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

data class ApiKeyRecord(
    val id: String,
    val provider: ApiKeyProvider,
    val label: String,
    val secret: String,
    val enabled: Boolean,
    val createdAt: Long,
) {
    val masked: String
        get() = when {
            secret.length <= 8 -> "••••••••"
            else -> "${secret.take(6)}••••${secret.takeLast(4)}"
        }
}

enum class ApiKeyProvider {
    OPENROUTER,
}
