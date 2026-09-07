package com.malik.lmai.feature.agent.loop

import com.malik.lmai.feature.agent.AgentModelRequest

@Suppress("DEPRECATION")
internal fun AgentModelRequest.estimateContextTokensForDiagnostics(): Int {
    val systemPromptTokens = instructions
        ?.takeIf { it.isNotBlank() }
        ?.let(ConversationContextManager::estimateTokens)
        ?: 0
    val conversationTokens = ConversationContextManager.estimateTokens(fullConversation)
    val toolTokens = tools.sumOf { tool ->
        ConversationContextManager.estimateTokens(tool.name) +
            ConversationContextManager.estimateTokens(tool.description) +
            ConversationContextManager.estimateTokens(tool.inputSchema.toString())
    }

    return systemPromptTokens + conversationTokens + toolTokens
}
