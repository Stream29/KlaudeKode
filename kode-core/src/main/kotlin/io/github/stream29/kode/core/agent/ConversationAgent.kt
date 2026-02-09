package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.ApprovalHandler
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import io.github.stream29.kode.ui.core.ToolApprovalDecision
import io.github.stream29.kode.ui.core.ToolApprovalRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

public data class AgentRuntimeContext(
    val agentId: String? = null,
    val parentAgentId: String? = null,
    val canInteractWithUser: Boolean = true,
    val canCreateSubagents: Boolean = true,
)

public class ConversationAgent(
    private val promptExecutor: PromptExecutor,
    private val toolRegistry: ToolRegistry,
    private val sessionManager: SessionManager,
    private val sessionBridge: KoogSessionBridge,
    private val messageHandler: MessageHandler,
    private val hookManager: HookManager,
    private val approvalHandler: ApprovalHandler?,
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext = AgentRuntimeContext(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    private fun buildJsonString(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    public suspend fun chat(sessionId: String, userInput: String, model: LLModel): String {
        if (!runtimeContext.canInteractWithUser) {
            throw IllegalStateException("Subagent cannot accept direct user chat")
        }

        sessionManager.beginRun(sessionId, currentJob())

        val processedInput = hookManager.applyUserPromptHooks(sessionId, userInput)
        return try {
            sessionManager.addUserMessage(sessionId, processedInput, runtimeContext.agentId)

            val messages = sessionBridge.prepareMessagesForAgent(sessionId, runtimeContext.agentId)
            val systemPrompt = sessionManager.getAgentConfig(sessionId, runtimeContext.agentId).systemPrompt ?: DEFAULT_SYSTEM_PROMPT

            val finalResponse = executeWithTools(sessionId, systemPrompt, messages, model)

            sessionManager.addAssistantMessage(sessionId, finalResponse, null, runtimeContext.agentId)
            sessionBridge.checkpoint(sessionId, "After execution")

            finalResponse
        } finally {
            sessionManager.completeRun(sessionId)
        }
    }

    public suspend fun continueSession(sessionId: String, model: LLModel): String {
        if (!runtimeContext.canInteractWithUser) {
            throw IllegalStateException("Subagent cannot continue user session directly")
        }

        sessionManager.beginRun(sessionId, currentJob())

        return try {
            val messages = sessionBridge.prepareMessagesForAgent(sessionId, runtimeContext.agentId)
            val systemPrompt = sessionManager.getAgentConfig(sessionId, runtimeContext.agentId).systemPrompt ?: DEFAULT_SYSTEM_PROMPT

            val finalResponse = executeWithTools(
                sessionId = sessionId,
                systemPrompt = systemPrompt,
                historyMessages = messages,
                model = model
            )

            sessionManager.addAssistantMessage(sessionId, finalResponse, null, runtimeContext.agentId)
            sessionBridge.checkpoint(sessionId, "After execution")

            finalResponse
        } finally {
            sessionManager.completeRun(sessionId)
        }
    }

    public suspend fun runSubAgent(sessionId: String, model: LLModel): String {
        val agentId = requireNotNull(runtimeContext.agentId) { "Subagent context requires agentId" }
        val messages = sessionBridge.prepareMessagesForAgent(sessionId, agentId)
        val systemPrompt = sessionManager.getAgentConfig(sessionId, agentId).systemPrompt ?: DEFAULT_SYSTEM_PROMPT

        return try {
            val finalResponse = executeWithTools(
                sessionId = sessionId,
                systemPrompt = systemPrompt,
                historyMessages = messages,
                model = model,
            )
            sessionManager.addAssistantMessage(sessionId, finalResponse, null, agentId)
            finalResponse
        } catch (signal: SubAgentReturnSignal) {
            signal.result
        }
    }

    private suspend fun executeWithTools(
        sessionId: String,
        systemPrompt: String,
        historyMessages: List<Message>,
        model: LLModel
    ): String {
        var allMessages = historyMessages.toMutableList()
        var iteration = 0

        while (true) {
            iteration++

            val refreshedMessages = sessionBridge.prepareMessagesForAgent(sessionId, runtimeContext.agentId)
            if (refreshedMessages.size >= allMessages.size) {
                allMessages = refreshedMessages.toMutableList()
            }

            val messagesForPrompt = mutableListOf<Message>()
            messagesForPrompt.add(
                Message.System(systemPrompt, RequestMetaInfo.create(Clock.System.toDeprecatedClock()))
            )
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
                        return processed
                    }
                    sessionManager.addAssistantMessage(
                        sessionId = sessionId,
                        content = response.content,
                        metadata = mapOf("source" to "assistant_tool_plan"),
                        agentId = runtimeContext.agentId,
                    )
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
        val rawToolName = toolCall.tool
        val toolName = resolveToolName(rawToolName)
        val toolArgs = toolCall.content
        val toolCallId = toolCall.id.orEmpty()
        val parsedArgs = parseToolArgs(toolArgs)

        handleCreateAgentRestriction(
            sessionId = sessionId,
            toolCall = toolCall,
            rawToolName = rawToolName,
            toolName = toolName,
            toolCallId = toolCallId,
            parsedArgs = parsedArgs,
        )?.let { return it }

        handleReturnAgentResult(
            sessionId = sessionId,
            rawToolName = rawToolName,
            toolName = toolName,
            toolCallId = toolCallId,
            toolArgs = toolArgs,
            parsedArgs = parsedArgs,
        )

        handleAwaitUserInputToolCall(
            sessionId = sessionId,
            toolCall = toolCall,
            rawToolName = rawToolName,
            toolName = toolName,
            toolCallId = toolCallId,
            parsedArgs = parsedArgs,
        )?.let { return it }

        val preHook = hookManager.applyToolCallBeforeHooks(sessionId, toolName, toolArgs)
        if (!preHook.allowed) {
            val reason = preHook.reason ?: "Tool call blocked by hook"
            val blockedArgs = preHook.toolArgs
            saveToolCall(
                sessionId = sessionId,
                toolName = toolName,
                toolCallId = toolCallId,
                arguments = parseToolArgs(blockedArgs),
            )
            saveToolResult(
                sessionId = sessionId,
                toolCallId = toolCallId,
                toolName = toolName,
                result = json.parseToJsonElement(buildJsonString(reason)),
                isError = true,
                errorMessage = reason,
            )
            return buildToolResult(toolCall = toolCall, rawToolName = toolName, content = reason)
        }

        val finalArgs = preHook.toolArgs

        logger("🔧 Calling tool: $toolName")
        logger("   Args: ${finalArgs.take(100)}")

        eventListener?.onEvent(
            AgentEvent.ToolCallStarting(
                toolName = toolName,
                arguments = finalArgs
            ),
            sessionId
        )

        saveToolCall(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = json.parseToJsonElement(finalArgs),
        )

        val decision = approvalHandler?.requestApproval(
            ToolApprovalRequest(
                id = toolCallId,
                toolName = toolName,
                arguments = finalArgs,
                description = "Tool call: $toolName"
            ),
            sessionId
        ) ?: ToolApprovalDecision.Approve

        val result = if (decision == ToolApprovalDecision.Reject) {
            "Tool call rejected by user: $toolName"
        } else {
            executeRegisteredTool(
                rawToolName = rawToolName,
                toolName = toolName,
                toolArgs = finalArgs,
            )
        }

        val processedResult = hookManager.applyToolCallAfterHooks(
            sessionId = sessionId,
            toolName = toolName,
            toolArgs = finalArgs,
            result = result
        )

        saveToolResult(
            sessionId = sessionId,
            toolCallId = toolCallId,
            toolName = toolName,
            result = json.parseToJsonElement(buildJsonString(processedResult)),
            isError = false,
            errorMessage = null,
        )

        eventListener?.onEvent(
            AgentEvent.ToolCallCompleted(
                toolName = toolName,
                result = processedResult
            ),
            sessionId
        )

        return buildToolResult(toolCall = toolCall, rawToolName = rawToolName, content = processedResult)
    }

    private suspend fun handleCreateAgentRestriction(
        sessionId: String,
        toolCall: Message.Tool.Call,
        rawToolName: String,
        toolName: String,
        toolCallId: String,
        parsedArgs: kotlinx.serialization.json.JsonElement,
    ): Message.Tool.Result? {
        if (runtimeContext.canCreateSubagents || !isCreateAgentTool(rawToolName)) {
            return null
        }

        val reason = "Subagent is not allowed to create subagents."
        saveToolCall(sessionId = sessionId, toolName = toolName, toolCallId = toolCallId, arguments = parsedArgs)
        saveToolResult(
            sessionId = sessionId,
            toolCallId = toolCallId,
            toolName = toolName,
            result = JsonPrimitive(reason),
            isError = true,
            errorMessage = reason,
        )
        return buildToolResult(toolCall = toolCall, rawToolName = rawToolName, content = reason)
    }

    private suspend fun handleReturnAgentResult(
        sessionId: String,
        rawToolName: String,
        toolName: String,
        toolCallId: String,
        toolArgs: String,
        parsedArgs: kotlinx.serialization.json.JsonElement,
    ) {
        if (runtimeContext.agentId == null || !isReturnAgentResultTool(rawToolName)) {
            return
        }

        val result = extractReturnAgentResult(toolArgs)
        saveToolCall(sessionId = sessionId, toolName = toolName, toolCallId = toolCallId, arguments = parsedArgs)
        saveToolResult(
            sessionId = sessionId,
            toolCallId = toolCallId,
            toolName = toolName,
            result = JsonPrimitive(result),
            isError = false,
            errorMessage = null,
        )
        throw SubAgentReturnSignal(result)
    }

    private suspend fun handleAwaitUserInputToolCall(
        sessionId: String,
        toolCall: Message.Tool.Call,
        rawToolName: String,
        toolName: String,
        toolCallId: String,
        parsedArgs: kotlinx.serialization.json.JsonElement,
    ): Message.Tool.Result? {
        if (!isAwaitUserInputTool(rawToolName)) {
            return null
        }

        if (!runtimeContext.canInteractWithUser) {
            val reason = "Subagent cannot call await_user_input."
            saveToolCall(sessionId = sessionId, toolName = toolName, toolCallId = toolCallId, arguments = parsedArgs)
            saveToolResult(
                sessionId = sessionId,
                toolCallId = toolCallId,
                toolName = toolName,
                result = JsonPrimitive(reason),
                isError = true,
                errorMessage = reason,
            )
            return buildToolResult(toolCall = toolCall, rawToolName = rawToolName, content = reason)
        }

        saveToolCall(sessionId = sessionId, toolName = toolName, toolCallId = toolCallId, arguments = parsedArgs)
        sessionManager.suspendForUserInput(sessionId)
        val input = messageHandler.requestInput(sessionId)
        sessionManager.addUserMessage(sessionId, input, runtimeContext.agentId)
        sessionManager.resumeRun(sessionId, currentJob())
        saveToolResult(
            sessionId = sessionId,
            toolCallId = toolCallId,
            toolName = toolName,
            result = JsonPrimitive(input),
            isError = false,
            errorMessage = null,
        )
        return buildToolResult(toolCall = toolCall, rawToolName = rawToolName, content = input)
    }

    private suspend fun saveToolCall(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: kotlinx.serialization.json.JsonElement,
    ) {
        sessionBridge.saveToolCall(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            agentId = runtimeContext.agentId,
        )
    }

    private suspend fun saveToolResult(
        sessionId: String,
        toolCallId: String,
        toolName: String,
        result: kotlinx.serialization.json.JsonElement,
        isError: Boolean,
        errorMessage: String?,
    ) {
        sessionBridge.saveToolResult(
            sessionId = sessionId,
            toolCallId = toolCallId,
            toolName = toolName,
            result = result,
            isError = isError,
            errorMessage = errorMessage,
            agentId = runtimeContext.agentId,
        )
    }

    private fun buildToolResult(
        toolCall: Message.Tool.Call,
        rawToolName: String,
        content: String,
    ): Message.Tool.Result {
        return Message.Tool.Result(
            id = toolCall.id,
            tool = rawToolName,
            content = content,
            metaInfo = RequestMetaInfo.create(Clock.System.toDeprecatedClock()),
        )
    }

    private suspend fun executeRegisteredTool(
        rawToolName: String,
        toolName: String,
        toolArgs: String,
    ): String {
        return try {
            val tool = toolRegistry.tools.find { it.name == toolName || it.name == rawToolName }
            if (tool == null) {
                "Error: Tool '$toolName' not found"
            } else {
                val argsJson = json.parseToJsonElement(toolArgs).jsonObject
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

    private fun resolveToolName(toolName: String): String {
        return when (toolName) {
            "fork_subagent" -> "forkSubagent"
            "spawn_subagent" -> "spawnSubagent"
            "create_agent" -> "forkSubagent"
            "poll_agent_result" -> "pollAgentResult"
            "await_agent_result" -> "awaitAgentResult"
            "kill_agent" -> "killAgent"
            "list_active_agents" -> "listActiveAgents"
            "say_to_agent" -> "sayToAgent"
            "return_agent_result" -> "returnAgentResult"
            else -> toolName
        }
    }

    private fun isCreateAgentTool(toolName: String): Boolean {
        val normalized = resolveToolName(toolName)
        return normalized == "forkSubagent" || normalized == "spawnSubagent"
    }

    private fun isReturnAgentResultTool(toolName: String): Boolean {
        return resolveToolName(toolName) == "returnAgentResult"
    }

    private fun extractReturnAgentResult(toolArgs: String): String {
        return runCatching {
            val element = json.parseToJsonElement(toolArgs)
            val resultNode = element.jsonObject["result"]
            resultNode?.jsonPrimitive?.contentOrNull ?: resultNode?.toString() ?: toolArgs
        }.getOrElse {
            toolArgs
        }
    }

    private fun parseToolArgs(toolArgs: String): kotlinx.serialization.json.JsonElement {
        return runCatching {
            json.parseToJsonElement(toolArgs)
        }.getOrElse {
            JsonPrimitive(toolArgs)
        }
    }

    private fun isAwaitUserInputTool(rawToolName: String): Boolean {
        return AWAIT_USER_INPUT_TOOL_NAMES.any { candidate ->
            candidate.equals(rawToolName, ignoreCase = true)
        }
    }

    private suspend fun currentJob(): Job {
        val job = currentCoroutineContext()[Job]
        return requireNotNull(job) { "ConversationAgent requires coroutine Job context" }
    }

    private class SubAgentReturnSignal(val result: String) : RuntimeException()

    private fun containsToolCall(content: String): Boolean {
        return content.contains("function") || content.contains("tool_call")
    }

    public companion object {
        private val AWAIT_USER_INPUT_TOOL_NAMES: Set<String> = setOf(
            "await_user_input",
            "awaitUserInput",
            "wait_for_user_input",
            "waitForUserInput",
        )

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
