package com.vibe.app.feature.ai

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.repository.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarantees that Free AI always has a zero-key on-device baseline.
 *
 * The local route is hidden infrastructure. It is never shown in the user's API
 * provider picker and never shares credentials/quota with external providers.
 */
@Singleton
class FreeAiBootstrapper @Inject constructor(
    private val settingRepository: SettingRepository,
    private val freeAiRouter: FreeAiRouter,
) {

    suspend fun ensureReady(): List<PlatformV2> {
        var platforms = settingRepository.fetchPlatformV2s()

        val hasLocal = platforms.any { platform ->
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL
        }

        if (!hasLocal) {
            settingRepository.addPlatformV2(
                PlatformV2(
                    name = LOCAL_DISPLAY_NAME,
                    compatibleType = ClientType.CUSTOM,
                    enabled = false,
                    apiUrl = LOCAL_API_URL,
                    token = null,
                    model = LOCAL_MODEL_ID,
                    provider = AiProviderOrigin.internalProviderCode("local"),
                    isFree = true,
                    temperature = 0.7f,
                    topP = 0.95f,
                    stream = false,
                    reasoning = false,
                    timeout = 60,
                )
            )
            platforms = settingRepository.fetchPlatformV2s()
        }

        val externalActive = platforms.any { platform ->
            platform.enabled && freeAiRouter.isExternal(platform)
        }

        if (externalActive) {
            if (settingRepository.getFreeAiEnabled()) {
                settingRepository.updateFreeAiEnabled(false)
            }

            platforms
                .filter { it.enabled && freeAiRouter.isInternalFree(it) }
                .forEach { internal ->
                    settingRepository.updatePlatformV2(internal.copy(enabled = false))
                }

            return settingRepository.fetchPlatformV2s()
        }

        if (!settingRepository.getFreeAiEnabled()) {
            settingRepository.updateFreeAiEnabled(true)
        }

        // Prefer any valid hidden cloud route configured by lm_AI; if none is
        // available, Local Gemini Nano is always the baseline candidate.
        val target = freeAiRouter.selectBest(platforms)
            ?: return settingRepository.fetchPlatformV2s()

        platforms
            .filter(freeAiRouter::isInternalFree)
            .forEach { internal ->
                val shouldEnable = internal.uid == target.uid
                if (internal.enabled != shouldEnable) {
                    settingRepository.updatePlatformV2(
                        internal.copy(enabled = shouldEnable)
                    )
                }
            }

        return settingRepository.fetchPlatformV2s()
    }

    companion object {
        const val LOCAL_DISPLAY_NAME = "Local Gemini Nano"
        const val LOCAL_MODEL_ID = "gemini-nano"
        const val LOCAL_API_URL = "local://android-aicore"
    }
}
