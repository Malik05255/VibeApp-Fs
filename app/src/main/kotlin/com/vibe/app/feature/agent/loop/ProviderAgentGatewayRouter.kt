package com.vibe.app.feature.agent.loop

import com.vibe.app.data.model.ClientType
import com.vibe.app.feature.agent.AgentModelEvent
import com.vibe.app.feature.agent.AgentModelGateway
import com.vibe.app.feature.agent.AgentModelRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Routes Agent requests to the model protocol implementation.
 *
 * Current supported providers:
 *
 * - OpenRouter
 * - Google AI Studio
 * - Custom OpenAI-compatible API
 *
 * All three use the OpenAI-compatible Chat Completions protocol,
 * therefore they intentionally share [QwenChatCompletionsAgentGateway].
 *
 * The gateway name is historical; its current responsibility is effectively
 * "OpenAI-compatible Chat Completions Agent Gateway".
 */
@Singleton
class ProviderAgentGatewayRouter @Inject constructor(
    private val qwenGateway: QwenChatCompletionsAgentGateway,
) : AgentModelGateway {

    override suspend fun streamTurn(
        request: AgentModelRequest,
    ): Flow<AgentModelEvent> {

        return when (
            request.platform.compatibleType
        ) {

            /*
             * =====================================================
             * SUPPORTED PROVIDERS
             * =====================================================
             *
             * These providers all use:
             *
             * POST /chat/completions
             *
             * with OpenAI-compatible:
             *
             * messages
             * tools
             * tool_choice
             * streaming SSE
             */
            ClientType.OPEN_ROUTER,
            ClientType.GOOGLE_AI_STUDIO,
            ClientType.CUSTOM -> {

                qwenGateway.streamTurn(
                    request
                )
            }

            /*
             * =====================================================
             * LEGACY PROVIDERS
             * =====================================================
             *
             * These enum values remain for:
             *
             * Room database compatibility
             * old saved platforms
             * source compatibility
             *
             * They are intentionally NOT exposed by the current
             * setup UI.
             *
             * Do NOT silently route them through qwenGateway.
             *
             * Example:
             *
             * Anthropic Messages API != OpenAI Chat Completions.
             *
             * Silently sending an Anthropic configuration to the
             * compatible gateway creates confusing HTTP/schema errors.
             */
            ClientType.OPENAI,
            ClientType.ANTHROPIC,
            ClientType.QWEN,
            ClientType.KIMI,
            ClientType.MINIMAX,
            ClientType.DEEPSEEK -> {

                flowOf<AgentModelEvent>(
                    AgentModelEvent.Failed(
                        message =
                            buildString {

                                append(
                                    "Unsupported provider in the current configuration: "
                                )

                                append(
                                    request.platform.compatibleType.name
                                )

                                append(
                                    ". Supported providers are "
                                )

                                append(
                                    "OPEN_ROUTER, GOOGLE_AI_STUDIO, and CUSTOM."
                                )
                            }
                    )
                )
            }
        }
    }
}
