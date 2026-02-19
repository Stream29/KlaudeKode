package io.github.stream29.kode.ui.core.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.web.WebView
import javafx.util.Duration
import java.awt.Desktop
import java.net.URI
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

private const val DEFAULT_MERMAID_HEIGHT_PX: Int = 180
private const val MIN_MERMAID_HEIGHT_PX: Int = 96
private const val MAX_MERMAID_HEIGHT_PX: Int = 1200
private const val DEFAULT_MERMAID_DIALOG_VIEWPORT_PX: Int = 680
private const val MAX_MERMAID_DIALOG_VIEWPORT_PX: Int = 960
private const val MERMAID_HEIGHT_PADDING_PX: Int = 18

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
            }
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

private data class MermaidPalette(
    val textColor: String,
    val borderColor: String,
    val codeBackgroundColor: String,
    val surfaceBackgroundColor: String,
    val mermaidTheme: String,
) {
    companion object {
        fun from(textColor: Color, containerColor: Color): MermaidPalette {
            val borderColor = if (containerColor.luminance() < 0.45f) {
                Color(0xFF5F6368)
            } else {
                Color(0xFF9AA0A6)
            }
            val codeBackgroundColor = if (containerColor.luminance() < 0.45f) {
                Color(0xFF2B313A)
            } else {
                Color(0xFFE8EAED)
            }
            return MermaidPalette(
                textColor = textColor.toCssHex(),
                borderColor = borderColor.toCssHex(),
                codeBackgroundColor = codeBackgroundColor.toCssHex(),
                surfaceBackgroundColor = containerColor.toCssHex(),
                mermaidTheme = if (containerColor.luminance() < 0.45f) "dark" else "default",
            )
        }
    }
}

private class MermaidWebViewHost(
    private val surfaceBackgroundColor: String,
    private val onHeightMeasured: (Int) -> Unit,
    private val onRenderReady: () -> Unit,
    private val onRenderFailure: () -> Unit,
) {
    val panel: JFXPanel = JFXPanel()
    private var webView: WebView? = null
    private var latestHtml: String = ""
    private var refreshTimeline: Timeline? = null
    private var renderReadyNotified: Boolean = false

    init {
        panel.background = toAwtColor(surfaceBackgroundColor)
        Platform.runLater {
            runCatching {
                val view = WebView().also { web ->
                    web.isContextMenuEnabled = false
                    web.engine.isJavaScriptEnabled = true
                    web.style = "-fx-background-color: $surfaceBackgroundColor;"
                    web.engine.loadWorker.stateProperty().addListener { _, _, state ->
                        when (state) {
                            Worker.State.SUCCEEDED -> scheduleHeightRefreshes()
                            Worker.State.FAILED -> SwingUtilities.invokeLater { onRenderFailure() }
                            else -> Unit
                        }
                    }
                    web.engine.locationProperty().addListener { _, oldLocation, newLocation ->
                        handleLinkNavigation(
                            oldLocation = oldLocation,
                            newLocation = newLocation,
                        )
                    }
                }
                panel.scene = Scene(view)
                webView = view
            }.onFailure {
                SwingUtilities.invokeLater {
                    onRenderFailure()
                }
            }
        }
    }

    fun load(html: String) {
        latestHtml = html
        renderReadyNotified = false
        Platform.runLater {
            webView?.engine?.loadContent(html)
        }
    }

    fun dispose() {
        Platform.runLater {
            refreshTimeline?.stop()
            refreshTimeline = null
            webView?.engine?.loadContent("<html><body></body></html>")
            webView = null
            panel.scene = null
        }
    }

    fun zoomIn() {
        executeViewportScript("window.kodeMermaid && window.kodeMermaid.zoomIn && window.kodeMermaid.zoomIn();")
    }

    fun zoomOut() {
        executeViewportScript("window.kodeMermaid && window.kodeMermaid.zoomOut && window.kodeMermaid.zoomOut();")
    }

    fun resetViewport() {
        executeViewportScript("window.kodeMermaid && window.kodeMermaid.reset && window.kodeMermaid.reset();")
    }

    private fun executeViewportScript(script: String) {
        Platform.runLater {
            runCatching {
                webView?.engine?.executeScript(script)
            }
        }
    }

    private fun handleLinkNavigation(oldLocation: String?, newLocation: String?) {
        val destination = newLocation?.trim().orEmpty()
        if (destination.isBlank() || destination == oldLocation || destination == "about:blank") {
            return
        }
        if (destination.startsWith("http://") || destination.startsWith("https://")) {
            runCatching {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI(destination))
                }
            }
            webView?.engine?.loadContent(latestHtml)
        }
    }

    private fun scheduleHeightRefreshes() {
        refreshTimeline?.stop()
        refreshHeightOnFxThread()
        refreshTimeline = Timeline(
            refreshFrame(90.0),
            refreshFrame(220.0),
            refreshFrame(420.0),
            refreshFrame(760.0),
            refreshFrame(1200.0)
        ).also { it.play() }
    }

    private fun refreshFrame(delayMillis: Double): KeyFrame {
        return KeyFrame(
            Duration.millis(delayMillis),
            EventHandler { refreshHeightOnFxThread() },
        )
    }

    private fun refreshHeightOnFxThread() {
        val script =
            "Math.max((document.getElementById('root')?.scrollHeight)||0, document.body.scrollHeight||0, document.documentElement.scrollHeight||0)"
        val rawHeight = webView?.engine?.executeScript(script) as? Number
        val measuredHeight = (rawHeight?.toInt() ?: DEFAULT_MERMAID_HEIGHT_PX) + MERMAID_HEIGHT_PADDING_PX
        val ready = runCatching {
            webView?.engine?.executeScript(
                "(function(){return !!document.querySelector('#root svg, #root pre.fallback');})()"
            ) as? Boolean
        }.getOrNull() == true
        SwingUtilities.invokeLater {
            onHeightMeasured(measuredHeight)
            if (ready && !renderReadyNotified) {
                renderReadyNotified = true
                onRenderReady()
            }
        }
    }
}

private fun buildMermaidHtml(
    code: String,
    palette: MermaidPalette,
): String {
    val escapedCode = code.escapeHtml()
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <style>
            *, *::before, *::after { box-sizing: border-box; }
            html, body {
              margin: 0;
              padding: 0;
              color: ${palette.textColor};
              overflow-x: hidden;
              overflow-y: hidden;
              background: ${palette.surfaceBackgroundColor};
              width: 100%;
              height: 100%;
            }
            ::-webkit-scrollbar {
              width: 0;
              height: 0;
              display: none;
            }
            #root {
              width: 100%;
              height: 100%;
              overflow: hidden;
              padding: 8px;
              display: flex;
              align-items: flex-start;
              justify-content: center;
            }
            pre.mermaid, pre.fallback {
              margin: 0;
              border: 1px solid ${palette.borderColor};
              border-radius: 8px;
              background: ${palette.codeBackgroundColor};
              padding: 10px 12px;
              overflow-x: auto;
              white-space: pre;
            }
            svg {
              max-width: 100% !important;
              height: auto !important;
              display: block;
            }
          </style>
          <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
          <script src="https://cdn.jsdelivr.net/npm/svg-pan-zoom@3.6.2/dist/svg-pan-zoom.min.js"></script>
        </head>
        <body>
          <main id="root">
            <pre class="mermaid" id="mermaid-source">$escapedCode</pre>
          </main>
          <script>
            (function() {
              let panZoomInstance = null;

              const installControls = function() {
                window.kodeMermaid = {
                  zoomIn: function() {
                    if (panZoomInstance) {
                      panZoomInstance.zoomIn();
                    }
                  },
                  zoomOut: function() {
                    if (panZoomInstance) {
                      panZoomInstance.zoomOut();
                    }
                  },
                  reset: function() {
                    if (panZoomInstance) {
                      panZoomInstance.resetZoom();
                      panZoomInstance.fit();
                      panZoomInstance.center();
                    }
                  }
                };
              };

              const notifyHeight = function() {
                document.body.style.height = document.body.scrollHeight + "px";
              };

              const setupPanZoom = function() {
                installControls();
                const svg = document.querySelector("#root svg");
                if (!svg || typeof svgPanZoom === "undefined") {
                  return;
                }

                svg.style.touchAction = "none";
                try {
                  panZoomInstance = svgPanZoom(svg, {
                    zoomEnabled: true,
                    panEnabled: true,
                    fit: true,
                    center: true,
                    minZoom: 0.2,
                    maxZoom: 8,
                    controlIconsEnabled: false,
                    mouseWheelZoomEnabled: true,
                    dblClickZoomEnabled: true,
                    preventMouseEventsDefault: true,
                    touchEventsEnabled: true,
                  });
                } catch (error) {
                  panZoomInstance = null;
                }
              };

              const renderFallback = function() {
                const root = document.getElementById("root");
                const source = document.getElementById("mermaid-source");
                if (!root || !source) {
                  installControls();
                  notifyHeight();
                  return;
                }
                const fallback = document.createElement("pre");
                fallback.className = "fallback";
                fallback.textContent = source.textContent || "";
                root.innerHTML = "";
                root.appendChild(fallback);
                installControls();
                notifyHeight();
              };

              if (typeof mermaid === "undefined") {
                renderFallback();
                return;
              }

              try {
                mermaid.initialize({
                  startOnLoad: false,
                  theme: "${palette.mermaidTheme}",
                  suppressErrorRendering: true,
                  securityLevel: "loose"
                });
                mermaid.run({ nodes: [document.getElementById("mermaid-source")] })
                  .then(function() {
                    setupPanZoom();
                    notifyHeight();
                  })
                  .catch(function() { renderFallback(); });
              } catch (error) {
                renderFallback();
              }

              setTimeout(notifyHeight, 60);
              setTimeout(notifyHeight, 180);
              setTimeout(notifyHeight, 360);
              setTimeout(notifyHeight, 760);
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun String.escapeHtml(): String {
    return buildString(length) {
        this@escapeHtml.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(char)
            }
        }
    }
}

private fun toAwtColor(hexColor: String): java.awt.Color {
    val normalized = hexColor.removePrefix("#")
    return when (normalized.length) {
        6 -> {
            val red = normalized.substring(0, 2).toInt(16)
            val green = normalized.substring(2, 4).toInt(16)
            val blue = normalized.substring(4, 6).toInt(16)
            java.awt.Color(red, green, blue)
        }

        else -> java.awt.Color(32, 33, 36)
    }
}

private fun Color.toCssHex(): String {
    val red = (red * 255f).roundToInt().coerceIn(0, 255)
    val green = (green * 255f).roundToInt().coerceIn(0, 255)
    val blue = (blue * 255f).roundToInt().coerceIn(0, 255)
    return "#%02X%02X%02X".format(red, green, blue)
}
