package com.malik.lmai.feature.agent.loop

import com.malik.lmai.data.database.entity.MessageV2
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopEvent
import com.malik.lmai.feature.agent.AgentLoopRequest
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolDefinition

/**
 * تحويل رسالة قاعدة البيانات إلى عنصر محادثة للـ Agent.
 */
private fun MessageV2.toAgentConversationItem(
    role: AgentMessageRole
): AgentConversationItem {
    return AgentConversationItem(
        role = role,
        text = content,
        attachments = files,
    )
}

/**
 * تحويل AgentLoopRequest إلى AgentModelRequest.
 *
 * AgentLoopRequest في المشروع لا يحتوي conversation/fullConversation
 * بشكل مباشر، لذلك نبنيهما من userMessages و assistantMessages.
 */
fun AgentLoopRequest.toModelRequest(
    tools: List<AgentToolDefinition>
): AgentModelRequest {

    val fullConversation = buildList {
        userMessages.forEachIndexed { index, userMessage ->

            add(
                userMessage.toAgentConversationItem(
                    role = AgentMessageRole.USER
                )
            )

            assistantMessages
                .getOrNull(index)
                ?.forEach { assistantMessage ->

                    if (assistantMessage.content.isNotBlank()) {
                        add(
                            assistantMessage.toAgentConversationItem(
                                role = AgentMessageRole.ASSISTANT
                            )
                        )
                    }
                }
        }
    }

    return AgentModelRequest(
        platform = platform,
        diagnosticContext = diagnosticContext,

        // لا توجد previousResponseId في AgentLoopRequest،
        // لذلك نرسل كامل السياق في كل دورة.
        conversation = fullConversation,
        fullConversation = fullConversation,

        instructions = systemPrompt,

        tools = tools,

        policy = policy,

        previousResponseId = null,
    )
}

/**
 * تحويل حدث النموذج إلى حدث حلقة الوكيل.
 *
 * iteration يجب أن يأتي من الـ Coordinator،
 * لأن AgentModelEvent لا يحتوي iteration.
 */
fun AgentModelEvent.toLoopEvent(
    iteration: Int
): AgentLoopEvent {

    return when (this) {

        is AgentModelEvent.ThinkingDelta -> {
            AgentLoopEvent.ThinkingDelta(
                iteration = iteration,
                delta = delta,
            )
        }

        is AgentModelEvent.OutputDelta -> {
            AgentLoopEvent.OutputDelta(
                iteration = iteration,
                delta = delta,
            )
        }

        is AgentModelEvent.ToolCallReady -> {
            AgentLoopEvent.ToolCallDiscovered(
                iteration = iteration,
                call = call,
            )
        }

        is AgentModelEvent.Completed -> {
            AgentLoopEvent.LoopCompleted(
                finalText = finalText.orEmpty(),
            )
        }

        is AgentModelEvent.Failed -> {
            AgentLoopEvent.LoopFailed(
                message = message,
                iteration = iteration,
            )
        }
    }
}
