package com.vibe.app.presentation.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vibe.app.feature.agent.AgentStepItem

/**
 * Agent activity is intentionally kept out of the user-facing transcript.
 *
 * Thinking/reasoning, plans, tool calls, file-operation results and similar
 * implementation traces still exist in session state and diagnostics, but they
 * are not chat messages and therefore are not rendered as expandable cards.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun AgentStepBubble(
    step: AgentStepItem,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
) = Unit
