package com.vibe.app.presentation.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibe.app.R
import com.vibe.app.feature.agent.AgentStepItem
import com.vibe.app.feature.agent.AgentStepType
import com.vibe.app.feature.agent.AgentToolStatus
import com.vibe.app.feature.agent.ToolCallInfo

private const val LIVE_THINKING_VISIBLE_CHARS = 8_000
private const val LATEST_LINE_SCAN_CHARS = 2_000

/**
 * Compact activity cards used while the application-building agent is working.
 * Details stay one tap away without taking over the chat screen.
 *
 * Live reasoning can be very long (especially DeepSeek reasoning models), so
 * the UI intentionally renders only a bounded tail while the model is still
 * streaming. The complete persisted content remains available after the step
 * finishes. This avoids large temporary allocations on every streamed token.
 */
@Composable
fun AgentStepBubble(
    step: AgentStepItem,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when (step.type) {
        AgentStepType.TOOL_CALL -> ToolCallStep(step = step, modifier = modifier)
        AgentStepType.THINKING -> ThinkingStep(step = step, isLive = isLive, modifier = modifier)
        AgentStepType.OUTPUT -> Unit // Output steps are rendered by OpponentChatBubble
        AgentStepType.PLAN -> {
            step.plan?.let { plan ->
                PlanBubble(plan = plan, isLive = isLive, modifier = modifier)
            }
        }
    }
}

@Composable
private fun ToolCallStep(
    step: AgentStepItem,
    modifier: Modifier = Modifier,
) {
    if (step.toolCalls.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }
    val canExpand = step.toolCalls.size > 1
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "tool_expand_rotation",
    )

    val isAnyCalling = step.toolStatus == AgentToolStatus.CALLING
    val latestCall = step.toolCalls.last()
    val latestLabel = formatToolCallLabel(latestCall)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
            .then(
                if (canExpand) Modifier.clickable { isExpanded = !isExpanded }
                else Modifier
            )
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83D\uDD27",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = latestLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isAnyCalling) {
                Spacer(modifier = Modifier.width(6.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            if (canExpand) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.collapse else R.string.expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                )
            }
        }

        if (isExpanded && canExpand) {
            Column(modifier = Modifier.padding(top = 5.dp)) {
                step.toolCalls.forEach { call ->
                    val callIcon = when (call.toolStatus) {
                        AgentToolStatus.CALLING -> "\uD83D\uDD27"
                        AgentToolStatus.OK -> "\u2705"
                        AgentToolStatus.ERROR -> "\u274C"
                    }
                    val callLabel = formatToolCallLabel(call)
                    Row(
                        modifier = Modifier.padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = callIcon,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = callLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatToolCallLabel(call: ToolCallInfo): String {
    val displayName = resolveToolDisplayName(call.toolName)
    return when (call.toolStatus) {
        AgentToolStatus.CALLING -> stringResource(R.string.tool_calling, displayName)
        AgentToolStatus.OK -> stringResource(R.string.tool_result_ok, displayName)
        AgentToolStatus.ERROR -> stringResource(R.string.tool_result_error, displayName)
    }
}

@Composable
private fun ThinkingStep(
    step: AgentStepItem,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (step.content.isBlank()) return

    var isExpanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "thinking_expand_rotation",
    )

    // Do not call lines() here. DeepSeek can stream thousands of reasoning
    // chunks, and lines() allocates a complete list on every recomposition.
    val latestLine = remember(step.content) {
        latestNonBlankLine(step.content)
    }
    val expandedText = remember(step.content, isLive) {
        if (isLive) boundedLiveText(step.content, LIVE_THINKING_VISIBLE_CHARS) else step.content
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83D\uDCAD",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isExpanded) {
                    stringResource(R.string.thinking_in_progress)
                } else {
                    latestLine
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isLive) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "\u25CF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (isExpanded) R.string.collapse else R.string.expand
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
            )
        }

        if (isExpanded) {
            Text(
                text = if (isLive) "$expandedText \u25CF" else expandedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

private fun latestNonBlankLine(text: String): String {
    if (text.isEmpty()) return ""

    val scanStart = (text.length - LATEST_LINE_SCAN_CHARS).coerceAtLeast(0)
    var end = text.length
    while (end > scanStart && text[end - 1].isWhitespace()) {
        end--
    }
    if (end <= scanStart) return ""

    var start = end
    while (start > scanStart) {
        val ch = text[start - 1]
        if (ch == '\n' || ch == '\r') break
        start--
    }
    return text.substring(start, end).trim()
}

private fun boundedLiveText(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    return "…\n" + text.substring(text.length - maxChars)
}
