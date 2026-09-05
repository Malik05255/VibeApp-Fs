package com.vibe.app.feature.agent.loop

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.ai.FreeAiFailoverCoordinator
import com.vibe.app.feature.ai.FreeAiRouter
import com.vibe.app.feature.ai.ProviderHealthTracker
import com.vibe.app.feature.ai.openrouter.OpenRouterCredentialStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Single hidden routing gateway for lm_AI.
 *
 * Each turn can choose a different internal Free AI route according to task,
 * device capability and recent provider health. External APIs remain explicit
 * user choices and always keep priority while enabled.
 */
@Singleton
class ProviderAgentGatewayRouter @Inject constructor(
    private val qwenGateway: QwenChatCompletionsAgentGateway,
    private val localNanoGateway: LocalNanoAgentGateway,
    private val failoverCoordinator: FreeAiFailoverCoordinator,
    private val freeAiRouter: FreeAiRouter,
    private val providerHealthTracker: ProviderHealthTracker,
    private val openRouterCredentialStore: OpenRouterCredentialStore,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest,
    ): Flow<AgentModelEvent> = flow {
        val startPlatform = try {
            failoverCoordinator.resolveStartPlatform(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                AgentModelEvent.Failed(
                    message = e.message
                        ?.takeIf { it.isNotBlank() }
                        ?: "No active AI provider is available."
                )
            )
            return@flow
        }

        var activeRequest = request.withPlatform(startPlatform)
        val attemptedPlatformUids = linkedSetOf<String>()

        while (true) {
            val platform = activeRequest.platform

            if (!attemptedPlatformUids.add(platform.uid)) {
                emit(
                    AgentModelEvent.Failed(
                        message = "Automatic AI failover stopped because a provider retry loop was detected."
                    )
                )
                return@flow
            }

            var failureMessage: String? = null
            var completed = false
            var materialOutputEmitted = false
            val attemptStartedAtNs = System.nanoTime()

            try {
                val providerFlow: Flow<AgentModelEvent>? = when {
                    isLocalFreePlatform(platform) ->
                        localNanoGateway.streamTurn(activeRequest)

                    isOpenAiCompatible(platform.compatibleType) ->
                        qwenGateway.streamTurn(activeRequest)

                    else -> null
                }

                if (providerFlow == null) {
                    failureMessage = unsupportedProviderMessage(platform.compatibleType)
                } else {
                    providerFlow.collect { event ->
                        when (event) {
                            is AgentModelEvent.Failed -> {
                                failureMessage = event.message
                            }

                            is AgentModelEvent.Completed -> {
                                completed = true
                                emit(event)
                            }

                            else -> {
                                materialOutputEmitted = true
                                emit(event)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                // User stop/coroutine cancellation must never wake another
                // provider behind the user's back.
                throw e
            } catch (e: Exception) {
                failureMessage = e.message
                    ?.takeIf { it.isNotBlank() }
                    ?: e::class.java.simpleName
                        .takeIf { it.isNotBlank() }
                    ?: "Provider request failed."
            }

            val elapsedMs = ((System.nanoTime() - attemptStartedAtNs) / 1_000_000L)
                .coerceAtLeast(0L)

            if (completed) {
                providerHealthTracker.recordSuccess(platform.uid, elapsedMs)
                return@flow
            }

            providerHealthTracker.recordFailure(platform.uid)

            val terminalFailure = failureMessage
                ?: "Provider stream ended without a completion event."

            // Never switch providers after any streamed content/tool call has
            // escaped to the agent loop. Mixing providers mid-response can
            // duplicate text or execute an inconsistent tool plan.
            if (materialOutputEmitted) {
                emit(AgentModelEvent.Failed(message = terminalFailure))
                return@flow
            }

            val failover = try {
                failoverCoordinator.handleFailure(
                    failedPlatformUid = platform.uid,
                    request = activeRequest,
                    attemptedPlatformUids = attemptedPlatformUids,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emit(AgentModelEvent.Failed(message = terminalFailure))
                return@flow
            }

            when (failover) {
                is FreeAiFailoverCoordinator.Result.Switched -> {
                    val target = failover.toPlatform
                    if (target.uid in attemptedPlatformUids) {
                        emit(AgentModelEvent.Failed(message = terminalFailure))
                        return@flow
                    }

                    activeRequest = activeRequest.withPlatform(target)
                }

                FreeAiFailoverCoordinator.Result.ManualMode,
                FreeAiFailoverCoordinator.Result.FreeAiDisabled,
                FreeAiFailoverCoordinator.Result.NoFallbackAvailable -> {
                    emit(AgentModelEvent.Failed(message = terminalFailure))
                    return@flow
                }
            }
        }
    }

    private fun isLocalFreePlatform(platform: PlatformV2): Boolean =
        freeAiRouter.isInternalFree(platform) &&
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL

    /**
     * Hidden OpenRouter Free rows intentionally store only a non-secret sentinel
     * in Room. Resolve the actual per-user OAuth key from Android Keystore at the
     * last possible moment so it is never persisted in the platform database.
     */
    private fun resolveRuntimePlatform(platform: PlatformV2): PlatformV2 {
        val isOAuthOpenRouter =
            freeAiRouter.isInternalFree(platform) &&
                freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER &&
                platform.token == OpenRouterCredentialStore.PLATFORM_TOKEN_SENTINEL

        if (!isOAuthOpenRouter) return platform

        val oauthToken = openRouterCredentialStore.getApiKey()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return platform.copy(token = oauthToken)
    }

    private fun AgentModelRequest.withPlatform(platform: PlatformV2): AgentModelRequest {
        val runtimePlatform = resolveRuntimePlatform(platform)
        return if (this.platform.uid == runtimePlatform.uid && this.platform == runtimePlatform) {
            this
        } else {
            copy(
                platform = runtimePlatform,
                diagnosticContext = diagnosticContext?.copy(
                    platformUid = runtimePlatform.uid,
                ),
            )
        }
    }

    private fun isOpenAiCompatible(type: ClientType): Boolean =
        type == ClientType.OPEN_ROUTER ||
            type == ClientType.GOOGLE_AI_STUDIO ||
            type == ClientType.CUSTOM

    private fun unsupportedProviderMessage(type: ClientType): String =
        buildString {
            append("Unsupported provider in the current configuration: ")
            append(type.name)
            append(". Supported providers are OPEN_ROUTER, GOOGLE_AI_STUDIO, CUSTOM, and local Free AI.")
        }
}
