package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.tool.ToolNames
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext = AgentRuntimeContext(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    private data class ResolvedToolCall(
        val call: Message.Tool.Call,
        val rawToolName: String,
        val toolName: String,
        val toolArgs: String,
        val toolCallId: String,
        val parsedArgs: JsonElement,
    )

    private fun requireInteractiveContext(message: String) {
        if (!runtimeContext.canInteractWithUser) {
            throw IllegalStateException(message)
        }
    }

    private suspend fun <T> withManagedSessionRun(sessionId: String, block: suspend () -> T): T {
        sessionManager.beginRun(sessionId, currentJob())
        return try {
            block()
        } finally {
            sessionManager.completeRun(sessionId)
        }
    }

    private suspend fun resolvePromptContext(sessionId: String, agentId: String?): Pair<String, List<Message>> {
        val messages = sessionBridge.prepareMessagesForAgent(sessionId, agentId)
        val systemPrompt = sessionManager.getAgentConfig(sessionId, agentId).systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        return systemPrompt to messages
    }

    private suspend fun executeAndPersistResponse(
        sessionId: String,
        agentId: String?,
        model: LLModel,
        modelParams: LLMParams?,
    ): String? {
        val (systemPrompt, messages) = resolvePromptContext(sessionId = sessionId, agentId = agentId)
        try {
            executeWithTools(
                sessionId = sessionId,
                systemPrompt = systemPrompt,
                historyMessages = messages,
                model = model,
                modelParams = modelParams,
            )
        } catch (_: SafeStopSignal) {
            return null
        }
        return ""
    }

    public suspend fun chat(
        sessionId: String,
        userInput: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String {
        requireInteractiveContext("Subagent cannot accept direct user chat")
        return withManagedSessionRun(sessionId) {
            val processedInput = hookManager.applyUserPromptHooks(sessionId, userInput)
            sessionManager.addUserMessage(sessionId, processedInput, runtimeContext.agentId)
            executeAndPersistResponse(
                sessionId = sessionId,
                agentId = runtimeContext.agentId,
                model = model,
                modelParams = modelParams,
            )
        }.orEmpty()
    }

    public suspend fun continueSession(
        sessionId: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String {
        requireInteractiveContext("Subagent cannot continue user session directly")
        return withManagedSessionRun(sessionId) {
            executeAndPersistResponse(
                sessionId = sessionId,
                agentId = runtimeContext.agentId,
                model = model,
                modelParams = modelParams,
            )
        }.orEmpty()
    }

    public suspend fun runSubAgent(
        sessionId: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String {
        val agentId = requireNotNull(runtimeContext.agentId) { "Subagent context requires agentId" }
        return try {
            executeAndPersistResponse(
                sessionId = sessionId,
                agentId = agentId,
                model = model,
                modelParams = modelParams,
            ).orEmpty()
        } catch (signal: SubAgentReturnSignal) {
            signal.result
        }
    }

    private fun buildPrompt(
        sessionId: String,
        iteration: Int,
        systemPrompt: String,
        messages: List<Message>,
        modelParams: LLMParams?,
    ): ai.koog.prompt.dsl.Prompt {
        val normalizedParams = withCodexInstructionParams(
            params = ModelParamsFactory.enforceRequiredToolChoice(modelParams),
            systemPrompt = systemPrompt,
        )
        val messagesForPrompt = buildList {
            add(Message.System(systemPrompt, RequestMetaInfo.create(Clock.System.toDeprecatedClock())))
            addAll(messages)
        }
        return ai.koog.prompt.dsl.Prompt(
            id = "conversation_${sessionId}_$iteration",
            messages = messagesForPrompt,
            params = normalizedParams,
        )
    }

    private fun withCodexInstructionParams(
        params: LLMParams,
        systemPrompt: String,
    ): LLMParams {
        if (params !is OpenAIResponsesParams) {
            return params
        }
        val normalizedInstructions = systemPrompt.trim().ifBlank {
            "You are a helpful assistant"
        }
        val mergedAdditionalProperties = (params.additionalProperties ?: emptyMap()) +
                mapOf("instructions" to JsonPrimitive(normalizedInstructions))
        return params.copy(additionalProperties = mergedAdditionalProperties)
    }

    private suspend fun executeWithTools(
        sessionId: String,
        systemPrompt: String,
        historyMessages: List<Message>,
        model: LLModel,
        modelParams: LLMParams?,
    ) {
        var allMessages = historyMessages.toMutableList()
        var iteration = 0

        while (true) {
            if (messageHandler.isSafeStopRequested(sessionId)) {
                messageHandler.onSafeStopReached(sessionId)
                throw SafeStopSignal()
            }

            iteration++

            val refreshedMessages = sessionBridge.prepareMessagesForAgent(sessionId, runtimeContext.agentId)
            if (refreshedMessages.size >= allMessages.size) {
                allMessages = refreshedMessages.toMutableList()
            }

            val currentPrompt = buildPrompt(
                sessionId = sessionId,
                iteration = iteration,
                systemPrompt = systemPrompt,
                messages = allMessages,
                modelParams = modelParams,
            )

            val responses = promptExecutor.execute(currentPrompt, model, toolRegistry.tools.map { it.descriptor })
            validateToolOnlyResponses(responses)

            responses.forEach { response ->
                when (response) {
                    is Message.Reasoning -> {
                        allMessages.add(response)
                    }

                    is Message.Assistant -> Unit

                    is Message.Tool.Call -> {
                        if (messageHandler.isSafeStopRequested(sessionId)) {
                            messageHandler.onSafeStopReached(sessionId)
                            throw SafeStopSignal()
                        }
                        allMessages.add(response)
                        val toolResult = executeToolCall(sessionId, response)
                        allMessages.add(toolResult)
                        if (messageHandler.isSafeStopRequested(sessionId)) {
                            messageHandler.onSafeStopReached(sessionId)
                            throw SafeStopSignal()
                        }
                    }
                }
            }
        }
    }

    private fun validateToolOnlyResponses(responses: List<Message.Response>) {
        if (responses.isEmpty()) {
            throw IllegalStateException("Tool-only mode violation: model returned empty response batch")
        }

        val nonEmptyAssistantText = responses
            .filterIsInstance<Message.Assistant>()
            .firstOrNull { message -> message.content.isNotBlank() }
            ?.content

        if (nonEmptyAssistantText != null) {
            throw IllegalStateException(
                "Tool-only mode violation: assistant text is not allowed: ${nonEmptyAssistantText.take(200)}"
            )
        }

        val hasToolCall = responses.any { response -> response is Message.Tool.Call }
        if (!hasToolCall) {
            val responseTypes = responses.joinToString(", ") { response ->
                when (response) {
                    is Message.Assistant -> "assistant"
                    is Message.Tool.Call -> "tool_call"
                    is Message.Reasoning -> "reasoning"
                }
            }
            throw IllegalStateException(
                "Tool-only mode violation: model returned no tool call (responses=$responseTypes)"
            )
        }
    }

    private suspend fun executeToolCall(
        sessionId: String,
        toolCall: Message.Tool.Call
    ): Message.Tool.Result {
        val resolvedCall = resolveToolCall(toolCall)

        handleCreateAgentRestriction(
            sessionId = sessionId,
            resolvedCall = resolvedCall,
        )?.let { return it }

        handleReturnAgentResult(
            sessionId = sessionId,
            resolvedCall = resolvedCall,
        )

        handleAwaitUserInputToolCall(
            sessionId = sessionId,
            resolvedCall = resolvedCall,
        )?.let { return it }

        val preHook = hookManager.applyToolCallBeforeHooks(sessionId, resolvedCall.toolName, resolvedCall.toolArgs)
        if (!preHook.allowed) {
            val reason = preHook.reason ?: "Tool call blocked by hook"
            return rejectToolCall(
                sessionId = sessionId,
                resolvedCall = resolvedCall,
                reason = reason,
                resultToolName = resolvedCall.toolName,
                arguments = parseToolArgs(preHook.toolArgs),
            )
        }

        val finalArgs = preHook.toolArgs

        logger("🔧 Calling tool: ${resolvedCall.toolName}")
        logger("   Args: ${finalArgs.take(100)}")

        eventListener?.onEvent(
            AgentEvent.ToolCallStarting(
                toolName = resolvedCall.toolName,
                arguments = finalArgs
            ),
            sessionId
        )

        val result = executeRegisteredTool(
            toolName = resolvedCall.toolName,
            toolArgs = finalArgs,
        )

        val processedResult = hookManager.applyToolCallAfterHooks(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolArgs = finalArgs,
            result = result
        )

        saveToolExchange(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolCallId = resolvedCall.toolCallId,
            arguments = parseToolArgs(finalArgs),
            result = JsonPrimitive(processedResult),
            isError = false,
            errorMessage = null,
        )

        eventListener?.onEvent(
            AgentEvent.ToolCallCompleted(
                toolName = resolvedCall.toolName,
                result = processedResult
            ),
            sessionId
        )

        return buildToolResult(
            toolCall = resolvedCall.call,
            rawToolName = resolvedCall.rawToolName,
            content = processedResult,
        )
    }

    private fun resolveToolCall(toolCall: Message.Tool.Call): ResolvedToolCall {
        val rawToolName = toolCall.tool
        val toolArgs = toolCall.content
        return ResolvedToolCall(
            call = toolCall,
            rawToolName = rawToolName,
            toolName = rawToolName,
            toolArgs = toolArgs,
            toolCallId = toolCall.id.orEmpty(),
            parsedArgs = parseToolArgs(toolArgs),
        )
    }

    private suspend fun rejectToolCall(
        sessionId: String,
        resolvedCall: ResolvedToolCall,
        reason: String,
        resultToolName: String,
        arguments: JsonElement,
    ): Message.Tool.Result {
        saveToolExchange(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolCallId = resolvedCall.toolCallId,
            arguments = arguments,
            result = JsonPrimitive(reason),
            isError = true,
            errorMessage = reason,
        )
        return buildToolResult(
            toolCall = resolvedCall.call,
            rawToolName = resultToolName,
            content = reason,
        )
    }

    private suspend fun handleCreateAgentRestriction(
        sessionId: String,
        resolvedCall: ResolvedToolCall,
    ): Message.Tool.Result? {
        if (runtimeContext.canCreateSubagents || !isCreateAgentTool(resolvedCall.rawToolName)) {
            return null
        }

        val reason = "Subagent is not allowed to create subagents."
        return rejectToolCall(
            sessionId = sessionId,
            resolvedCall = resolvedCall,
            reason = reason,
            resultToolName = resolvedCall.rawToolName,
            arguments = resolvedCall.parsedArgs,
        )
    }

    private suspend fun handleReturnAgentResult(
        sessionId: String,
        resolvedCall: ResolvedToolCall,
    ) {
        if (runtimeContext.agentId == null || !isReturnAgentResultTool(resolvedCall.rawToolName)) {
            return
        }

        val result = extractReturnAgentResult(resolvedCall.toolArgs)
        saveToolExchange(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolCallId = resolvedCall.toolCallId,
            arguments = resolvedCall.parsedArgs,
            result = JsonPrimitive(result),
            isError = false,
            errorMessage = null,
        )
        throw SubAgentReturnSignal(result)
    }

    private suspend fun handleAwaitUserInputToolCall(
        sessionId: String,
        resolvedCall: ResolvedToolCall,
    ): Message.Tool.Result? {
        if (!isAwaitUserInputTool(resolvedCall.rawToolName)) {
            return null
        }

        if (!runtimeContext.canInteractWithUser) {
            val reason = "Subagent cannot call ${ToolNames.WAIT_FOR_USER_INPUT}."
            return rejectToolCall(
                sessionId = sessionId,
                resolvedCall = resolvedCall,
                reason = reason,
                resultToolName = resolvedCall.rawToolName,
                arguments = resolvedCall.parsedArgs,
            )
        }

        saveSuspend(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolCallId = resolvedCall.toolCallId,
            arguments = resolvedCall.parsedArgs,
        )
        sessionManager.suspendForUserInput(sessionId)
        val input = messageHandler.requestInput(sessionId)
        sessionManager.resumeRun(sessionId, currentJob())
        saveResume(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolCallId = resolvedCall.toolCallId,
            result = JsonPrimitive(input),
            isError = false,
            errorMessage = null,
        )
        return buildToolResult(
            toolCall = resolvedCall.call,
            rawToolName = resolvedCall.rawToolName,
            content = input,
        )
    }

    private suspend fun saveToolExchange(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
    ) {
        sessionBridge.saveToolExchange(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            result = result,
            isError = isError,
            errorMessage = errorMessage,
            agentId = runtimeContext.agentId,
        )
    }

    private suspend fun saveSuspend(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        arguments: JsonElement,
    ) {
        sessionBridge.saveSuspend(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            agentId = runtimeContext.agentId,
        )
    }

    private suspend fun saveResume(
        sessionId: String,
        toolName: String,
        toolCallId: String,
        result: JsonElement,
        isError: Boolean,
        errorMessage: String?,
    ) {
        sessionBridge.saveResume(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
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
        toolName: String,
        toolArgs: String,
    ): String {
        return try {
            val tool = toolRegistry.tools.find { it.name == toolName }
            if (tool == null) {
                "Error: Tool '$toolName' not found"
            } else {
                val argsJson = json.parseToJsonElement(toolArgs).jsonObject
                executeToolSafely(tool = tool, argsJson = argsJson)
            }
        } catch (e: Exception) {
            "Error executing tool $toolName: ${e.message}"
        }
    }

    @OptIn(InternalAgentToolsApi::class)
    private suspend fun executeToolSafely(
        tool: ai.koog.agents.core.tools.Tool<*, *>,
        argsJson: kotlinx.serialization.json.JsonObject,
    ): String {
        val decodedArgs = tool.decodeArgs(argsJson)
        val toolResult = tool.executeUnsafe(decodedArgs)
        return tool.encodeResultToStringUnsafe(toolResult)
    }

    private fun isCreateAgentTool(toolName: String): Boolean {
        return toolName == ToolNames.FORK_SUBAGENT || toolName == ToolNames.SPAWN_SUBAGENT
    }

    private fun isReturnAgentResultTool(toolName: String): Boolean {
        return toolName == ToolNames.RETURN_AGENT_RESULT
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

    private fun parseToolArgs(toolArgs: String): JsonElement {
        return runCatching {
            json.parseToJsonElement(toolArgs)
        }.getOrElse {
            JsonPrimitive(toolArgs)
        }
    }

    private fun isAwaitUserInputTool(rawToolName: String): Boolean {
        return rawToolName == ToolNames.WAIT_FOR_USER_INPUT
    }

    private suspend fun currentJob(): Job {
        val job = currentCoroutineContext()[Job]
        return requireNotNull(job) { "ConversationAgent requires coroutine Job context" }
    }

    private class SubAgentReturnSignal(val result: String) : RuntimeException()

    private class SafeStopSignal : RuntimeException()

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
