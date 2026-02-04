@file:Suppress("DEPRECATION")

package io.github.stream29.kode.app.model

import io.github.stream29.kode.session.core.model.MessageRole
import kotlinx.datetime.Instant

/**
 * UI model for a message in the conversation.
 */
public data class MessageItem(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant,
    val isError: Boolean = false,
    val isToolCall: Boolean = false,
    val toolName: String? = null
)

/**
 * Converts SessionMessage to MessageItem for UI display.
 */
public fun io.github.stream29.kode.session.core.model.SessionMessage.toMessageItem(): MessageItem {
    return MessageItem(
        id = this.id,
        role = this.role,
        content = this.content,
        timestamp = this.timestamp,
        isError = this.contentType == io.github.stream29.kode.session.core.model.ContentType.ERROR,
        isToolCall = this.role == MessageRole.TOOL_CALL || this.role == MessageRole.TOOL_RESULT,
        toolName = this.structuredData?.let { 
            try {
                kotlinx.serialization.json.Json.decodeFromJsonElement(
                    io.github.stream29.kode.session.core.model.ToolCallData.serializer(),
                    it
                ).toolName
            } catch (e: Exception) {
                null
            }
        }
    )
}
