package com.vibe.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AdaptiveDimensions(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val itemSpacing: Dp,
)

@Composable
fun AdaptiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 720.dp,
    content: @Composable BoxScope.(AdaptiveDimensions) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val dimensions = when {
            maxWidth < 360.dp -> AdaptiveDimensions(
                horizontalPadding = 12.dp,
                verticalPadding = 12.dp,
                itemSpacing = 12.dp,
            )
            maxWidth < 600.dp -> AdaptiveDimensions(
                horizontalPadding = 18.dp,
                verticalPadding = 18.dp,
                itemSpacing = 14.dp,
            )
            maxWidth < 840.dp -> AdaptiveDimensions(
                horizontalPadding = 28.dp,
                verticalPadding = 24.dp,
                itemSpacing = 16.dp,
            )
            else -> AdaptiveDimensions(
                horizontalPadding = 36.dp,
                verticalPadding = 28.dp,
                itemSpacing = 18.dp,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = maxContentWidth)
                .fillMaxSize(),
            content = { content(dimensions) },
        )
    }
}
