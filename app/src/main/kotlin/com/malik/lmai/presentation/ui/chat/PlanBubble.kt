package com.malik.lmai.presentation.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.malik.lmai.R
import com.malik.lmai.feature.agent.AgentPlan
import com.malik.lmai.feature.agent.PlanStepStatus

@Composable
fun PlanBubble(
    plan: AgentPlan,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val completedCount = plan.steps.count { it.status == PlanStepStatus.COMPLETED }
    val totalCount = plan.steps.size
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "plan_expand_rotation",
    )
    val activeStep = plan.steps.firstOrNull { it.status == PlanStepStatus.IN_PROGRESS }
        ?: plan.steps.firstOrNull { it.status == PlanStepStatus.PENDING }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .clickable { isExpanded = !isExpanded }
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "\uD83D\uDCCB",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Plan ($completedCount/$totalCount)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (isLive && completedCount < totalCount) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
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

        Spacer(modifier = Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        if (!isExpanded) {
            val previewText = activeStep?.let { "${it.id}. ${it.description}" } ?: plan.summary
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodySmall,
                color = if (activeStep?.status == PlanStepStatus.IN_PROGRESS) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
            return@Column
        }

        Text(
            text = plan.summary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 7.dp),
        )

        Column(modifier = Modifier.padding(top = 5.dp)) {
            plan.steps.forEach { step ->
                val (icon, textColor) = when (step.status) {
                    PlanStepStatus.COMPLETED -> "\u2705" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    PlanStepStatus.IN_PROGRESS -> "\uD83D\uDD04" to MaterialTheme.colorScheme.primary
                    PlanStepStatus.FAILED -> "\u274C" to MaterialTheme.colorScheme.error
                    PlanStepStatus.SKIPPED -> "\u23ED\uFE0F" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    PlanStepStatus.PENDING -> "\u2B1C" to MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                }

                Row(
                    modifier = Modifier.padding(vertical = 1.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = icon,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(19.dp),
                    )
                    Text(
                        text = "${step.id}. ${step.description}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                    )
                }
            }
        }
    }
}
