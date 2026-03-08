package io.github.stream29.kode.ui.core.components.message

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

internal const val DEFAULT_MERMAID_HEIGHT_PX: Int = 180
internal const val MIN_MERMAID_HEIGHT_PX: Int = 96
internal const val MAX_MERMAID_HEIGHT_PX: Int = 1200
internal const val DEFAULT_MERMAID_DIALOG_VIEWPORT_PX: Int = 680
internal const val MAX_MERMAID_DIALOG_VIEWPORT_PX: Int = 960
private const val MERMAID_HEIGHT_PADDING_PX: Int = 18

internal data class MermaidPalette(
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

internal class MermaidWebViewHost(
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
            refreshFrame(1200.0),
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
                "(function(){return !!document.querySelector('#root svg, #root pre.fallback');})()",
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

internal fun buildMermaidHtml(
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
