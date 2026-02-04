package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.hooks.ToolCallHookResult
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.ApprovalHandler
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.ui.core.ToolApprovalDecision
import io.github.stream29.kode.ui.core.ToolApprovalRequest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

public class ConversationAgent(
    private val promptExecutor: PromptExecutor,
    private val toolRegistry: ToolRegistry,
    private val sessionManager: SessionManager,
    private val sessionBridge: KoogSessionBridge,
    private val messageHandler: MessageHandler,
    private val hookManager: HookManager,
    private val approvalHandler: ApprovalHandler?,
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    private fun buildJsonString(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    public suspend fun chat(sessionId: String, userInput: String, model: LLModel): String {
        val session = sessionManager.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val processedInput = hookManager.applyUserPromptHooks(sessionId, userInput)
        sessionManager.addUserMessage(sessionId, processedInput)

        val messages = sessionBridge.prepareMessagesForAgent(sessionId)
        val systemPrompt = session.configuration.systemPrompt ?: DEFAULT_SYSTEM_PROMPT

        val finalResponse = executeWithTools(sessionId, systemPrompt, messages, processedInput, model)

        sessionManager.addAssistantMessage(sessionId, finalResponse, null)
        sessionBridge.checkpoint(sessionId, "After execution")

        return finalResponse
    }

    public suspend fun continueSession(sessionId: String, model: LLModel): String {
        val session = sessionManager.getSession(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val messages = sessionBridge.prepareMessagesForAgent(sessionId)
        val systemPrompt = session.configuration.systemPrompt ?: DEFAULT_SYSTEM_PROMPT

        val finalResponse = executeWithTools(
            sessionId = sessionId,
            systemPrompt = systemPrompt,
            historyMessages = messages,
            currentInput = "",
            model = model
        )

        sessionManager.addAssistantMessage(sessionId, finalResponse, null)
        sessionBridge.checkpoint(sessionId, "After execution")

        return finalResponse
    }

    private suspend fun executeWithTools(
        sessionId: String,
        systemPrompt: String,
        historyMessages: List<Message>,
        currentInput: String,
        model: LLModel
    ): String {
        val allMessages = historyMessages.toMutableList()
        var iteration = 0

        while (true) {
            iteration++

            val messagesForPrompt = mutableListOf<Message>()
            messagesForPrompt.add(Message.System(systemPrompt, RequestMetaInfo.create(Clock.System)))
            messagesForPrompt.addAll(allMessages)
            
            val currentPrompt = ai.koog.prompt.dsl.Prompt(
                id = "conversation_${sessionId}_$iteration",
                messages = messagesForPrompt
            )

            val response = promptExecutor.execute(currentPrompt, model, toolRegistry.tools.map { it.descriptor }).first()

            when (response) {
                is Message.Assistant -> {
                    if (!containsToolCall(response.content)) {
                        val processed = hookManager.applyAssistantResponseHooks(sessionId, response.content)
                        emitAssistantMessageChunks(processed)
                        return processed
                    }
                    allMessages.add(response)
                }
                is Message.Tool.Call -> {
                    allMessages.add(response)
                    val toolResult = executeToolCall(sessionId, response)
                    allMessages.add(toolResult)
                }
                else -> {
                    return response.content
                }
            }
        }
    }

    private suspend fun executeToolCall(
        sessionId: String,
        toolCall: Message.Tool.Call
    ): Message.Tool.Result {
        val toolName = toolCall.tool
        val toolArgs = toolCall.content
        val preHook = hookManager.applyToolCallBeforeHooks(sessionId, toolName, toolArgs)
        if (!preHook.allowed) {
            val reason = preHook.reason ?: "Tool call blocked by hook"
            val blockedArgs = preHook.toolArgs
            sessionBridge.saveToolCall(
                sessionId = sessionId,
                toolName = toolName,
                toolCallId = toolCall.id ?: "",
                arguments = json.parseToJsonElement(blockedArgs)
            )
            sessionBridge.saveToolResult(
                sessionId = sessionId,
                toolCallId = toolCall.id ?: "",
                toolName = toolName,
                result = json.parseToJsonElement(buildJsonString(reason)),
                isError = true,
                errorMessage = reason
            )
            return Message.Tool.Result(
                id = toolCall.id,
                tool = toolName,
                content = reason,
                metaInfo = RequestMetaInfo.create(Clock.System)
            )
        }

        val finalArgs = preHook.toolArgs

        logger("🔧 Calling tool: $toolName")
        logger("   Args: ${finalArgs.take(100)}")

        eventListener?.onEvent(
            AgentEvent.ToolCallStarting(
                toolName = toolName,
                arguments = finalArgs
            )
        )

        sessionBridge.saveToolCall(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCall.id ?: "",
            arguments = json.parseToJsonElement(finalArgs)
        )

        val decision = approvalHandler?.requestApproval(
            ToolApprovalRequest(
                id = toolCall.id ?: "",
                toolName = toolName,
                arguments = finalArgs,
                description = "Tool call: $toolName"
            )
        ) ?: ToolApprovalDecision.Approve

        val result = if (decision == ToolApprovalDecision.Reject) {
            "Tool call rejected by user: $toolName"
        } else {
            try {
                val tool = toolRegistry.tools.find { it.name == toolName }
                if (tool == null) {
                    "Error: Tool '$toolName' not found"
                } else {
                    val argsJson = json.parseToJsonElement(finalArgs).jsonObject
                    @Suppress("UNCHECKED_CAST")
                    val decodedArgs = (tool as ai.koog.agents.core.tools.Tool<Any?, Any?>).decodeArgs(argsJson)
                    @Suppress("UNCHECKED_CAST")
                    val toolResult = tool.execute(decodedArgs)
                    toolResult.toString()
                }
            } catch (e: Exception) {
                "Error executing tool $toolName: ${e.message}"
            }
        }

        val processedResult = hookManager.applyToolCallAfterHooks(
            sessionId = sessionId,
            toolName = toolName,
            toolArgs = finalArgs,
            result = result
        )

        sessionBridge.saveToolResult(
            sessionId = sessionId,
            toolCallId = toolCall.id ?: "",
            toolName = toolName,
            result = json.parseToJsonElement(buildJsonString(processedResult)),
            isError = false,
            errorMessage = null
        )

        eventListener?.onEvent(
            AgentEvent.ToolCallCompleted(
                toolName = toolName,
                result = processedResult
            )
        )

        return Message.Tool.Result(
            id = toolCall.id,
            tool = toolName,
            content = processedResult,
            metaInfo = RequestMetaInfo.create(Clock.System)
        )
    }

    private fun containsToolCall(content: String): Boolean {
        return content.contains("function") || content.contains("tool_call")
    }

    private fun emitAssistantMessageChunks(content: String) {
        val chunkSize = 60
        if (content.isBlank()) {
            eventListener?.onEvent(AgentEvent.AssistantMessageChunk(content = "", isFinal = true))
            return
        }

        val chunks = content.chunked(chunkSize)
        chunks.forEachIndexed { index, chunk ->
            val isFinal = index == chunks.lastIndex
            eventListener?.onEvent(
                AgentEvent.AssistantMessageChunk(
                    content = chunk,
                    isFinal = isFinal
                )
            )
        }
    }

    public companion object {
        public val DEFAULT_SYSTEM_PROMPT: String = """
            You are a highly skilled AI coding assistant powered by Koog framework with kimi-cli capabilities.

            ## Your Capabilities

            ### File Operations
            - Read files and understand code structure
            - Edit files with precise modifications
            - List directory contents
            - Search files with glob patterns
            - Search file contents with grep

            ### Terminal/Shell
            - Execute bash commands
            - Run build commands
            - Check git status, run tests, etc.

            ### Web Access
            - Fetch content from URLs
            - Extract text from web pages

            ### Task Management
            - Create todo lists to track progress
            - Mark todos as pending, in_progress, or done

            ### Communication
            - Ask the user for clarification when needed
            - Provide updates on long-running tasks

            ## Guidelines

            1. Plan First: For complex tasks, organize your approach
            2. Read Before Edit: Always read files before modifying them
            3. Search Effectively: Use tools to find relevant code
            4. Make Focused Changes: Keep edits minimal and precise
            5. Use Shell Wisely: Run tests, builds, and validation commands
            6. Track Progress: Use todos for multi-step tasks
            7. Explain Clearly: Describe what you are doing and why
            8. Ask When Unclear: Request clarification for ambiguous requirements

            Be precise, efficient, and helpful in your programming assistance.
        """.trimIndent()
    }
}
