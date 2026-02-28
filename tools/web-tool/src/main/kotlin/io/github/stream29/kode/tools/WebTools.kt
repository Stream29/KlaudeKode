package io.github.stream29.kode.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.stream29.kode.ui.core.MessageHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Web tools for searching and fetching web content.
 * Based on kimi-cli's web search and fetch tools.
 */
@Suppress("unused")
@LLMDescription("Tools for searching the web and fetching web page content")
public class WebTools public constructor(
    private val messageHandler: MessageHandler,
    private val logger: (String) -> Unit = { println(it) }
) : ToolSet {

    private val client = HttpClient(CIO) {
        followRedirects = true
    }

    public companion object {
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val MAX_CONTENT_CHARS = 50_000
        private const val MAIN_CONTENT_SELECTOR =
            "article, main, [role='main'], .content, .post-content, .entry-content"
        private const val STRIPPED_ELEMENTS_SELECTOR = "script, style, nav, footer, header, aside, .advertisement, .ads"
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val EXTRA_BLANK_LINES_REGEX = Regex("\\n\\s*\\n\\s*\\n+")
    }

    @Tool
    @LLMDescription(
        "Fetch the content of a web page from a URL. " +
                "Extracts the main text content from HTML pages. " +
                "Use this to read documentation, articles, or any web content."
    )
    public suspend fun fetchURL(
        @LLMDescription("The URL to fetch content from")
        url: String,
    ): FetchResult = withContext(Dispatchers.IO) {
        logger("🌐 Fetching URL: $url")
        messageHandler.addMessageToUser("🌐 Fetching: $url")

        try {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, DEFAULT_USER_AGENT)
            }

            if (response.status.value >= 400) {
                return@withContext fetchFailure(
                    url = url,
                    title = null,
                    error = "HTTP ${response.status.value} error",
                )
            }

            val contentType = response.contentType()?.toString()?.lowercase().orEmpty()
            val body = response.bodyAsText()

            if (isTextLikeContentType(contentType)) {
                return@withContext fetchSuccess(url = url, title = null, content = body)
            }

            val htmlContent = extractContentFromHtml(url = url, body = body)
            val title = htmlContent.title
            val content = htmlContent.content

            if (content.isBlank()) {
                return@withContext fetchFailure(
                    url = url,
                    title = title,
                    error = "Could not extract meaningful content from the page",
                )
            }

            logger("✅ Successfully fetched: ${title ?: url}")
            fetchSuccess(url = url, title = title, content = content)

        } catch (e: Exception) {
            logger("❌ Failed to fetch URL: ${e.message}")
            fetchFailure(
                url = url,
                title = null,
                error = "Failed to fetch URL: ${e.message}",
            )
        }
    }

    @Tool
    @LLMDescription(
        "Search the web for information. " +
                "Note: This is a placeholder implementation. In production, integrate with a search API " +
                "like Google Custom Search, Bing API, or Moonshot's search service. " +
                "Returns helpful guidance for manual searching."
    )
    public fun searchWeb(
        @LLMDescription("The search query")
        query: String,
        @LLMDescription("Number of results to request (1-10, default 5)")
        limit: Int = 5,
    ): SearchResult {
        logger("🔍 Web search (manual guidance): $query")
        messageHandler.addMessageToUser("🔍 Search requested: $query")

        val actualLimit = limit.coerceIn(1, 10)
        val searchEngines = createManualSearchUrls(query).take(actualLimit)

        return SearchResult(
            success = true,
            query = query,
            results = emptyList(),
            message = """
                |Web search is not configured with an API key.
                |
                |To search for "$query", visit:
                |${searchEngines.joinToString("\n") { "- $it" }}
                |
                |You can use the fetchURL tool to retrieve content from search results.
                |
                |To enable automated search, configure a search API in the application settings.
            """.trimMargin()
        )
    }

    private fun isTextLikeContentType(contentType: String): Boolean {
        return contentType.contains("text/plain") || contentType.contains("text/markdown")
    }

    private fun extractContentFromHtml(url: String, body: String): ExtractedHtmlContent {
        val doc: Document = Jsoup.parse(body, url)
        doc.select(STRIPPED_ELEMENTS_SELECTOR).remove()

        val title = doc.title().takeIf { it.isNotBlank() }
        val extractedMainContent = doc.select(MAIN_CONTENT_SELECTOR)
            .firstOrNull()
            ?.text()
        val content = cleanWebContent(extractedMainContent.takeIf { !it.isNullOrBlank() } ?: doc.body().text())

        return ExtractedHtmlContent(title = title, content = content)
    }

    private fun cleanWebContent(content: String): String {
        return content
            .replace(WHITESPACE_REGEX, " ")
            .replace(EXTRA_BLANK_LINES_REGEX, "\n\n")
            .trim()
            .take(MAX_CONTENT_CHARS)
    }

    private fun fetchSuccess(url: String, title: String?, content: String): FetchResult {
        return FetchResult(
            success = true,
            url = url,
            title = title,
            content = content,
            error = null,
        )
    }

    private fun fetchFailure(url: String, title: String?, error: String): FetchResult {
        return FetchResult(
            success = false,
            url = url,
            title = title,
            content = "",
            error = error,
        )
    }

    private fun createManualSearchUrls(query: String): List<String> {
        val encodedQuery = query.encodeURLQueryComponent()
        return listOf(
            "https://www.google.com/search?q=$encodedQuery",
            "https://duckduckgo.com/?q=$encodedQuery",
            "https://www.bing.com/search?q=$encodedQuery",
        )
    }

    private data class ExtractedHtmlContent(
        val title: String?,
        val content: String,
    )
}

/**
 * Result of fetching a URL
 */
@Serializable
public data class FetchResult(
    val success: Boolean,
    val url: String,
    val title: String?,
    val content: String,
    val error: String?,
) {
    override fun toString(): String = buildString {
        if (success) {
            title?.let { appendLine("Title: $it") }
            appendLine("URL: $url")
            appendLine()
            append(content)
        } else {
            appendLine("Error: $error")
            appendLine("URL: $url")
        }
    }
}

/**
 * Web search result item
 */
@Serializable
public data class WebSearchResultItem(
    val title: String,
    val url: String,
    val snippet: String,
)

/**
 * Result of a web search
 */
@Serializable
public data class SearchResult(
    val success: Boolean,
    val query: String,
    val results: List<WebSearchResultItem>,
    val message: String,
) {
    override fun toString(): String = buildString {
        appendLine("Search query: $query")
        appendLine()
        appendLine(message)
        if (results.isNotEmpty()) {
            appendLine()
            appendLine("Results:")
            results.forEachIndexed { index, result ->
                appendLine("${index + 1}. ${result.title}")
                appendLine("   URL: ${result.url}")
                appendLine("   ${result.snippet}")
                appendLine()
            }
        }
    }
}
