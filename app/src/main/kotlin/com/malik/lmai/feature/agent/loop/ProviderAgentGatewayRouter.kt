package com.malik.lmai.feature.agent.loop

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.ai.FreeAiFailoverCoordinator
import com.malik.lmai.feature.ai.FreeAiRouter
import com.malik.lmai.feature.ai.HMediaPipeAgentGateway
import com.malik.lmai.feature.ai.ProviderHealthTracker
import com.malik.lmai.feature.ai.openrouter.OpenRouterCredentialStore
import com.malik.lmai.feature.assistant.MohammedAssistantContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Single routing gateway for محمد / مساعد H الرقمي.
 *
 * Explicit user-managed APIs keep priority. Built-in cloud routes provide maximum
 * capability online, while the independent local Qwen runtime provides offline
 * conversation and code-review continuity without Gemini Nano/AICore.
 */
@Singleton
class ProviderAgentGatewayRouter @Inject constructor(
    private val qwenGateway: QwenChatCompletionsAgentGateway,
    private val openAiResponsesGateway: OpenAiResponsesAgentGateway,
    private val hMediaPipeAgentGateway: HMediaPipeAgentGateway,
    private val failoverCoordinator: FreeAiFailoverCoordinator,
    private val freeAiRouter: FreeAiRouter,
    private val providerHealthTracker: ProviderHealthTracker,
    private val openRouterCredentialStore: OpenRouterCredentialStore,
    private val mohammedAssistantContext: MohammedAssistantContext,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest,
    ): Flow<AgentModelEvent> = flow {
        val preparedRequest = runCatching {
            mohammedAssistantContext.prepare(request)
        }.getOrDefault(request)

        // The coordinator exposes project tools to every session. Adapt at the
        // provider boundary so greetings and ordinary questions remain real chat
        // instead of being forced into a tool call and the generic completion text.
        val userFacingRequest = ChatTurnPolicy.adapt(preparedRequest)

        val startPlatform = try {
            failoverCoordinator.resolveStartPlatform(userFacingRequest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                AgentModelEvent.Failed(
                    message = e.message
                        ?.takeIf { it.isNotBlank() }
                        ?: "لا يوجد مسار متاح لمحمد حاليًا."
                )
            )
            return@flow
        }

        var activeRequest = userFacingRequest.withPlatform(startPlatform)
        val attemptedPlatformUids = linkedSetOf<String>()

        while (true) {
            val platform = activeRequest.platform

            if (!attemptedPlatformUids.add(platform.uid)) {
                emit(
                    AgentModelEvent.Failed(
                        message = "توقف التحويل التلقائي لمحمد لمنع تكرار نفس المسار."
                    )
                )
                return@flow
            }

            var failureMessage: String? = null
            var completed = false
            var visibleOrActionOutputEmitted = false
            var firstOutputRecorded = false
            var rateLimited = false
            val attemptStartedAtNs = System.nanoTime()

            fun elapsedMs(): Long =
                ((System.nanoTime() - attemptStartedAtNs) / 1_000_000L)
                    .coerceAtLeast(0L)

            fun recordFirstVisibleOutput() {
                if (firstOutputRecorded) return
                firstOutputRecorded = true
                providerHealthTracker.recordFirstOutput(
                    platformUid = platform.uid,
                    latencyMs = elapsedMs(),
                )
            }

            fun noteFailure(message: String) {
                failureMessage = message
                if (message.isRateLimitFailure()) {
                    rateLimited = true
                }
            }

            try {
                val providerFlow: Flow<AgentModelEvent>? = when {
                    isLocalHPlatform(platform) ->
                        hMediaPipeAgentGateway.streamTurn(activeRequest)

                    // Never send an old/forged internal-local route through the generic
                    // OpenAI-compatible network gateway. Only the exact H local URI is
                    // trusted to invoke on-device inference.
                    isAnyInternalLocalPlatform(platform) -> null

                    // OpenRouter's Responses endpoint accepts local/base64 image input.
                    // The free router then selects a model that supports the modalities
                    // actually present in this request instead of silently dropping them.
                    isOpenRouterPlatform(platform) && activeRequest.hasImageAttachments() ->
                        openAiResponsesGateway.streamTurn(activeRequest)

                    isOpenAiCompatible(platform.compatibleType) ->
                        qwenGateway.streamTurn(activeRequest)

                    else -> null
                }

                if (providerFlow == null) {
                    noteFailure(unsupportedProviderMessage(platform.compatibleType))
                } else {
                    providerFlow.collect { event ->
                        when (event) {
                            is AgentModelEvent.Failed -> noteFailure(event.message)

                            is AgentModelEvent.Completed -> {
                                // Providers that do not stream put the first visible reply
                                // in Completed.finalText. Count that as TTFT so future turns
                                // can learn to avoid a route that waits 10-20 seconds.
                                if (
                                    !firstOutputRecorded &&
                                    !event.finalText.isNullOrBlank()
                                ) {
                                    recordFirstVisibleOutput()
                                }
                                completed = true
                                emit(event)
                            }

                            is AgentModelEvent.OutputDelta -> {
                                if (event.delta.isNotEmpty()) {
                                    recordFirstVisibleOutput()
                                    visibleOrActionOutputEmitted = true
                                }
                                emit(event)
                            }

                            is AgentModelEvent.ToolCallReady -> {
                                // A tool call is an externally meaningful action. Once one
                                // has been emitted, silently replaying the same turn against a
                                // second model risks duplicate project mutation.
                                visibleOrActionOutputEmitted = true
                                emit(event)
                            }

                            is AgentModelEvent.ThinkingDelta -> {
                                // Hidden reasoning is not a user-visible response and is not
                                // an external action. If the provider then rate-limits or dies,
                                // allow immediate failover instead of surfacing a 429 after the
                                // user waited for reasoning they never saw.
                                emit(event)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                noteFailure(
                    e.message
                        ?.takeIf { it.isNotBlank() }
                        ?: e::class.java.simpleName
                            .takeIf { it.isNotBlank() }
                        ?: "تعذر تشغيل مسار محمد الحالي."
                )
            }

            val elapsedMs = elapsedMs()

            if (completed) {
                providerHealthTracker.recordSuccess(platform.uid, elapsedMs)
                return@flow
            }

            if (rateLimited) {
                providerHealthTracker.recordRateLimit(platform.uid)
            } else {
                providerHealthTracker.recordFailure(platform.uid)
            }

            val terminalFailure = failureMessage
                ?: "انتهى المسار بدون إكمال الرد."

            if (visibleOrActionOutputEmitted) {
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

    /** Resolve OpenRouter OAuth only at runtime so the secret never enters Room. */
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

    private fun AgentModelRequest.hasImageAttachments(): Boolean =
        fullConversation.any { it.attachments.isNotEmpty() } ||
            conversation.any { it.attachments.isNotEmpty() }

    private fun isOpenRouterPlatform(platform: PlatformV2): Boolean =
        platform.compatibleType == ClientType.OPEN_ROUTER ||
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.OPENROUTER

    private fun isLocalHPlatform(platform: PlatformV2): Boolean =
        isAnyInternalLocalPlatform(platform) &&
            freeAiRouter.isFreeCandidate(platform, FreeAiRouter.Provider.LOCAL)

    private fun isAnyInternalLocalPlatform(platform: PlatformV2): Boolean =
        freeAiRouter.isInternalFree(platform) &&
            freeAiRouter.detectProvider(platform) == FreeAiRouter.Provider.LOCAL

    private fun isOpenAiCompatible(type: ClientType): Boolean =
        type == ClientType.OPEN_ROUTER ||
            type == ClientType.GOOGLE_AI_STUDIO ||
            type == ClientType.CUSTOM

    private fun String.isRateLimitFailure(): Boolean {
        val normalized = lowercase()
        return "http 429" in normalized ||
            "http_429" in normalized ||
            "status 429" in normalized ||
            "status=429" in normalized ||
            "status: 429" in normalized ||
            "\"code\":429" in normalized ||
            "\"code\": 429" in normalized ||
            "rate limit" in normalized ||
            "rate_limit" in normalized ||
            "too many requests" in normalized ||
            "quota exceeded" in normalized ||
            "resource_exhausted" in normalized
    }

    private fun unsupportedProviderMessage(type: ClientType): String =
        "إعداد المزوّد غير مدعوم حاليًا: ${type.name}."
}
