package io.github.stream29.kode.ui.core.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding

private val mermaidFenceRegex: Regex = Regex(
    pattern = """(?is)```[ \t]*mermaid[^\n]*\r?\n(.*?)```""",
)

@Composable
internal fun MarkdownMessageContent(
    markdown: String,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier,
) {
    val segments = remember(markdown) {
        splitMarkdownSegments(markdown)
    }
    val markdownColors = markdownColor(text = textColor)
    val baseText = MaterialTheme.typography.bodyMedium
    val headingText = baseText.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    val markdownTextTypography = markdownTypography(
        h1 = headingText,
        h2 = headingText,
        h3 = headingText,
        h4 = headingText,
        h5 = headingText,
        h6 = headingText,
        text = baseText,
        paragraph = baseText,
        ordered = baseText,
        bullet = baseText,
        list = baseText,
    )
    val compactPadding = markdownPadding(
        block = 0.dp,
        list = 0.dp,
        listItemTop = 0.dp,
        listItemBottom = 0.dp,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is MarkdownSegment.MarkdownBlock -> {
                    if (segment.content.isNotBlank()) {
                        Markdown(
                            content = segment.content,
                            colors = markdownColors,
                            typography = markdownTextTypography,
                            padding = compactPadding,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is MarkdownSegment.MermaidBlock -> MermaidRenderLauncher(
                    segmentId = "mermaid-$index",
                    code = segment.code,
                    textColor = textColor,
                    containerColor = containerColor,
                )
            }
        }
    }
}

@Composable
private fun MermaidRenderLauncher(
    segmentId: String,
    code: String,
    textColor: Color,
    containerColor: Color,
) {
    var showPreview by rememberSaveable(segmentId) { mutableStateOf(false) }
    val previewText = remember(code) { buildMermaidCodePreview(code) }

    DisableSelection {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Mermaid diagram",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                TextButton(onClick = { showPreview = true }) {
                    Text("Render diagram")
                }
            }
        }
    }

    if (showPreview) {
        MermaidPreviewDialog(
            code = code,
            textColor = textColor,
            containerColor = containerColor,
            onDismiss = { showPreview = false },
        )
    }
}

@Composable
private fun MermaidPreviewDialog(
    code: String,
    textColor: Color,
    containerColor: Color,
    onDismiss: () -> Unit,
) {
    var closeRequested by remember { mutableStateOf(false) }
    LaunchedEffect(closeRequested) {
        if (closeRequested) {
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        DisableSelection {
            Surface(
                modifier = Modifier.widthIn(min = 640.dp, max = 1080.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Mermaid preview",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    MermaidBlock(
                        code = code,
                        textColor = textColor,
                        containerColor = containerColor,
                        dialogMode = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { closeRequested = true }) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

private fun buildMermaidCodePreview(code: String): String {
    val firstLine = code.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?: "(empty mermaid code)"
    return if (firstLine.length > 120) {
        firstLine.take(120) + "..."
    } else {
        firstLine
    }
}

@Composable
private fun MermaidBlock(
    code: String,
    textColor: Color,
    containerColor: Color,
    dialogMode: Boolean = false,
) {
    var measuredHeightPx by remember(code, dialogMode) {
        mutableIntStateOf(
            if (dialogMode) {
                DEFAULT_MERMAID_DIALOG_VIEWPORT_PX
            } else {
                DEFAULT_MERMAID_HEIGHT_PX
            },
        )
    }
    var renderReady by remember(code) { mutableStateOf(false) }
    var failed by remember(code) { mutableStateOf(false) }
    val palette = remember(textColor, containerColor) {
        MermaidPalette.from(
            textColor = textColor,
            containerColor = containerColor,
        )
    }
    val html = remember(code, palette, dialogMode) {
        buildMermaidHtml(
            code = code,
            palette = palette,
        )
    }
    val host = remember(code, palette.surfaceBackgroundColor) {
        MermaidWebViewHost(
            surfaceBackgroundColor = palette.surfaceBackgroundColor,
            onHeightMeasured = { measured ->
                measuredHeightPx = measured.coerceIn(MIN_MERMAID_HEIGHT_PX, MAX_MERMAID_HEIGHT_PX)
            },
            onRenderReady = {
                renderReady = true
            },
            onRenderFailure = {
                failed = true
            },
        )
    }

    DisposableEffect(host) {
        onDispose {
            host.dispose()
        }
    }

    LaunchedEffect(host, html) {
        renderReady = false
        failed = false
        host.load(html)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        if (failed) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
            return@ElevatedCard
        }

        if (dialogMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { host.zoomOut() },
                    enabled = renderReady,
                ) {
                    Text("-")
                }
                TextButton(
                    onClick = { host.resetViewport() },
                    enabled = renderReady,
                ) {
                    Text("Reset")
                }
                TextButton(
                    onClick = { host.zoomIn() },
                    enabled = renderReady,
                ) {
                    Text("+")
                }
            }
        }

        val density = LocalDensity.current
        val viewportPx = if (dialogMode) {
            measuredHeightPx.coerceIn(
                minimumValue = DEFAULT_MERMAID_DIALOG_VIEWPORT_PX,
                maximumValue = MAX_MERMAID_DIALOG_VIEWPORT_PX,
            )
        } else {
            measuredHeightPx
        }
        val height = with(density) { viewportPx.toDp() }

        if (!renderReady) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .padding(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
            return@ElevatedCard
        }

        SwingPanel(
            factory = { host.panel },
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            background = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

private sealed interface MarkdownSegment {
    data class MarkdownBlock(val content: String) : MarkdownSegment
    data class MermaidBlock(val code: String) : MarkdownSegment
}

private fun splitMarkdownSegments(markdown: String): List<MarkdownSegment> {
    if (markdown.isBlank()) {
        return emptyList()
    }

    val segments = mutableListOf<MarkdownSegment>()
    var cursor = 0

    mermaidFenceRegex.findAll(markdown).forEach { match ->
        if (match.range.first > cursor) {
            val before = markdown.substring(cursor, match.range.first)
            if (before.isNotBlank()) {
                segments += MarkdownSegment.MarkdownBlock(content = before)
            }
        }

        val code = match.groups[1]?.value
            ?.trim('\n', '\r')
            .orEmpty()
        if (code.isNotBlank()) {
            segments += MarkdownSegment.MermaidBlock(code = code)
        }
        cursor = match.range.last + 1
    }

    if (cursor < markdown.length) {
        val tail = markdown.substring(cursor)
        if (tail.isNotBlank()) {
            segments += MarkdownSegment.MarkdownBlock(content = tail)
        }
    }

    if (segments.isEmpty()) {
        return listOf(MarkdownSegment.MarkdownBlock(content = markdown))
    }
    return segments
}
