package com.vibe.app.feature.agent.loop

import com.vibe.app.data.database.entity.PlatformV2
import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import com.vibe.app.feature.ai.FreeAiFailoverCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Routes Agent requests to the model protocol implementation.
 *
 * Automatic mode keeps the retry/failover chain inside the same agent turn.
 * Intermediate provider failures are swallowed only when the failed provider
 * produced no material stream output. This prevents mixing partial answers or
 * tool calls from two different providers in one turn.
 */
@Singleton
class ProviderAgentGatewayRouter @Inject constructor(
    private val qwenGateway: QwenChatCompletionsAgentGateway,
    private val failoverCoordinator: FreeAiFailoverCoordinator,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest,
    ): Flow<AgentModelEvent> = flow {
        val startPlatform = try {
            failoverCoordinator.resolveStartPlatform(request.platform)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            request.platform
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

            if (isOpenAiCompatible(platform.compatibleType)) {
                try {
                    qwenGateway
                        .streamTurn(activeRequest)
                        .collect { event ->
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
            } else {
                failureMessage = unsupportedProviderMessage(platform.compatibleType)
            }

            if (completed) {
                return@flow
            }

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
                failoverCoordinator.handleFailure(platform.uid)
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

    private fun AgentModelRequest.withPlatform(platform: PlatformV2): AgentModelRequest =
        if (this.platform.uid == platform.uid && this.platform == platform) {
            this
        } else {
            copy(
                platform = platform,
                diagnosticContext = diagnosticContext?.copy(
                    platformUid = platform.uid,
                ),
            )
        }

    private fun isOpenAiCompatible(type: ClientType): Boolean =
        type == ClientType.OPEN_ROUTER ||
            type == ClientType.GOOGLE_AI_STUDIO ||
            type == ClientType.CUSTOM

    private fun unsupportedProviderMessage(type: ClientType): String =
        buildString {
            append("Unsupported provider in the current configuration: ")
            append(type.name)
            append(". Supported providers are OPEN_ROUTER, GOOGLE_AI_STUDIO, and CUSTOM.")
        }
}
