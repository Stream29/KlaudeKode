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
    }

    @Tool
    @LLMDescription(
        "Fetch the content of a web page from a URL. " +
        "Extracts the main text content from HTML pages. " +
        "Use this to read documentation, articles, or any web content."
    )
    public suspend fun fetchURL(
        @LLMDescription("The URL to fetch content from")
        url: String
    ): FetchResult = withContext(Dispatchers.IO) {
        logger("🌐 Fetching URL: $url")
        messageHandler.addMessageToUser("🌐 Fetching: $url")

        try {
            val response = client.get(url) {
                header("User-Agent", DEFAULT_USER_AGENT)
            }

            if (response.status.value >= 400) {
                return@withContext FetchResult(
                    success = false,
                    url = url,
                    title = null,
                    content = "",
                    error = "HTTP ${response.status.value} error"
                )
            }

            val contentType = response.contentType()?.toString() ?: ""
            val body = response.bodyAsText()

            // Handle plain text or markdown directly
            if (contentType.contains("text/plain") || contentType.contains("text/markdown")) {
                return@withContext FetchResult(
                    success = true,
                    url = url,
                    title = null,
                    content = body,
                    error = null
                )
            }

            // Parse HTML using Jsoup
            val doc: Document = Jsoup.parse(body, url)

            // Remove unwanted elements
            doc.select("script, style, nav, footer, header, aside, .advertisement, .ads").remove()

            // Extract title
            val title = doc.title().takeIf { it.isNotBlank() }

            // Extract main content
            // Try to find main content areas first
            var content = doc.select("article, main, [role='main'], .content, .post-content, .entry-content")
                .firstOrNull()
                ?.text()

            // Fallback to body text
            if (content.isNullOrBlank()) {
                content = doc.body()?.text()
            }

            // Clean up the content
            content = content?.let { cleanWebContent(it) }

            if (content.isNullOrBlank()) {
                return@withContext FetchResult(
                    success = false,
                    url = url,
                    title = title,
                    content = "",
                    error = "Could not extract meaningful content from the page"
                )
            }

            logger("✅ Successfully fetched: ${title ?: url}")

            FetchResult(
                success = true,
                url = url,
                title = title,
                content = content,
                error = null
            )

        } catch (e: Exception) {
            logger("❌ Failed to fetch URL: ${e.message}")
            FetchResult(
                success = false,
                url = url,
                title = null,
                content = "",
                error = "Failed to fetch URL: ${e.message}"
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
    public suspend fun searchWeb(
        @LLMDescription("The search query")
        query: String,
        @LLMDescription("Number of results to request (1-10, default 5)")
        limit: Int = 5
    ): SearchResult {
        logger("🔍 Web search (manual guidance): $query")
        messageHandler.addMessageToUser("🔍 Search requested: $query")

        // Since we don't have a search API configured, provide helpful guidance
        val searchEngines = listOf(
            "https://www.google.com/search?q=${query.replace(" ", "+")}",
            "https://duckduckgo.com/?q=${query.replace(" ", "+")}",
            "https://www.bing.com/search?q=${query.replace(" ", "+")}"
        )

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

    /**
     * Clean up extracted web content
     */
    private fun cleanWebContent(content: String): String {
        return content
            .replace(Regex("\\s+"), " ")  // Normalize whitespace
            .replace(Regex("\\n\\s*\\n\\s*\\n+"), "\n\n")  // Limit consecutive newlines
            .trim()
            .take(50000)  // Limit content length
    }
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
    val error: String?
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
    val snippet: String
)

/**
 * Result of a web search
 */
@Serializable
public data class SearchResult(
    val success: Boolean,
    val query: String,
    val results: List<WebSearchResultItem>,
    val message: String
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
