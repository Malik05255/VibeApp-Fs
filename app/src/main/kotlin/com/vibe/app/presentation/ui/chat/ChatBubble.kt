package com.vibe.app.presentation.ui.chat

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCode
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.vibe.app.R
import java.io.File

@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    text: String,
    files: List<String> = emptyList(),
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = modifier
                .padding(horizontal = 10.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                },
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 7.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        ) {
            Markdown(
                content = text.trimIndent(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                colors = chatMarkdownColors(),
                typography = chatMarkdownTypography(),
                padding = chatMarkdownPadding(),
                components = chatMarkdownComponents(),
            )
        }

        UserFileThumbnailRow(
            modifier = Modifier.padding(top = 8.dp, end = 10.dp),
            files = files,
        )
    }
}

/**
 * User-facing assistant reply.
 *
 * Deliberation, tool traces and expandable AI/debug chrome intentionally do not
 * belong here. The chat should read like a normal conversation: provider label,
 * then the final assistant answer.
 */
@Composable
fun OpponentChatBubble(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    isError: Boolean = false,
    text: String,
    loadingMinHeight: Dp = 0.dp,
    onCopyClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (text.isNotBlank()) {
            Markdown(
                content = text.trimIndent(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                colors = chatMarkdownColors(),
                typography = chatMarkdownTypography(),
                padding = chatMarkdownPadding(),
                components = chatMarkdownComponents(),
            )
        }

        if (isLoading) {
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.8.dp,
                )
            }
        }

        if (!isLoading && text.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                IconButton(onClick = onCopyClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_copy),
                        contentDescription = stringResource(R.string.copy_text),
                        modifier = Modifier.size(16.dp),
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSelectClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_select),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val ignoredLoadingMinHeight = loadingMinHeight
}

@Composable
fun VibeAppIcon(loading: Boolean) {
    Surface(
        modifier = Modifier
            .padding(start = 4.dp)
            .size(34.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(11.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.09f),
            ),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(29.dp),
                    strokeWidth = 1.7.dp,
                )
            }
            Image(
                painter = painterResource(R.drawable.ic_vibe),
                contentDescription = null,
                modifier = Modifier.size(25.dp),
            )
        }
    }
}

@Composable
private fun UserFileThumbnailRow(
    modifier: Modifier = Modifier,
    files: List<String>,
) {
    val validFiles = files.filter { it.isNotBlank() }
    var previewImagePath by remember { mutableStateOf<String?>(null) }
    if (validFiles.isEmpty()) return

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .wrapContentHeight()
                .horizontalScroll(rememberScrollState())
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            validFiles.forEach { filePath ->
                UserFileThumbnail(
                    filePath = filePath,
                    onImageClick = { previewImagePath = filePath },
                )
            }
        }
    }

    previewImagePath?.let { imagePath ->
        FullscreenImagePreview(
            filePath = imagePath,
            onDismissRequest = { previewImagePath = null },
        )
    }
}

@Composable
private fun UserFileThumbnail(
    filePath: String,
    onImageClick: () -> Unit,
) {
    val file = File(filePath)
    val isImage = isImageFile(file.extension)
    val width = if (isImage) 132.dp else 88.dp

    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(width)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .then(if (isImage) Modifier.clickable(onClick = onImageClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (isImage) {
                AsyncImage(
                    model = Uri.fromFile(file),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_file),
                    contentDescription = file.name,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        androidx.compose.material3.Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun chatMarkdownColors() = markdownColor(
    codeBackground = MaterialTheme.colorScheme.surfaceContainerLow,
)

@Composable
fun chatMarkdownTypography(): com.mikepenz.markdown.model.MarkdownTypography {
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )
    val body = MaterialTheme.typography.bodyLarge.copy(
        lineHeight = 24.sp,
        lineHeightStyle = lineHeightStyle,
    )
    return markdownTypography(
        h1 = MaterialTheme.typography.headlineSmall,
        h2 = MaterialTheme.typography.titleLarge,
        h3 = MaterialTheme.typography.titleMedium,
        h4 = MaterialTheme.typography.titleSmall,
        h5 = MaterialTheme.typography.bodyLarge,
        h6 = MaterialTheme.typography.bodyMedium,
        text = body,
        paragraph = body,
        list = body,
        bullet = body,
        ordered = body,
    )
}

@Composable
fun chatMarkdownPadding() = markdownPadding(
    list = 0.dp,
    listItemTop = 0.dp,
    listItemBottom = 0.dp,
)

@Composable
fun chatMarkdownComponents() = markdownComponents(
    orderedList = {
        MarkdownOrderedList(
            content = it.content,
            node = it.node,
            style = it.typography.ordered,
            depth = it.listDepth,
            markerModifier = { Modifier.alignByBaseline() },
            listModifier = { Modifier.alignByBaseline() },
        )
    },
    unorderedList = {
        MarkdownBulletList(
            content = it.content,
            node = it.node,
            style = it.typography.bullet,
            depth = it.listDepth,
            markerModifier = { Modifier.alignByBaseline() },
            listModifier = { Modifier.alignByBaseline() },
        )
    },
    codeFence = {
        MarkdownCodeFence(it.content, it.node, it.typography.code) { code, language, style ->
            MarkdownHighlightedCode(
                code = code,
                language = language ?: "txt",
                style = style,
                showHeader = true,
            )
        }
    },
    codeBlock = {
        MarkdownCodeBlock(it.content, it.node, it.typography.code) { code, language, style ->
            MarkdownHighlightedCode(
                code = code,
                language = language ?: "txt",
                style = style,
                showHeader = true,
            )
        }
    },
    table = {
        MarkdownTable(it.content, it.node, it.typography.table)
    },
)

@Composable
fun FullscreenImagePreview(
    filePath: String,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismissRequest),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = Uri.fromFile(File(filePath)),
                contentDescription = File(filePath).name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun isImageFile(extension: String?): Boolean {
    val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    return extension?.lowercase() in imageExtensions
}
