package com.malik.lmai.presentation.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.malik.lmai.feature.agent.AgentStepItem

/**
 * Agent/tool/thinking steps are execution details, not chat content.
 *
 * Keep this composable as a no-op so the backend can continue recording and
 * executing tool activity without exposing reasoning, file-operation status,
 * plans, or tool traces to the user-facing conversation.
 */
@Composable
fun AgentStepBubble(
    step: AgentStepItem,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Intentionally hidden from the chat transcript.
    @Suppress("UNUSED_VARIABLE")
    val ignored = Triple(step, isLive, modifier)
}
