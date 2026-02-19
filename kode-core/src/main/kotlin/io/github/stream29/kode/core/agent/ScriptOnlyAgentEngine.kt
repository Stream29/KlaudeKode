package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.tool.ToolNames
import io.github.stream29.kode.tools.scripting.KotlinScriptTool
import io.github.stream29.kode.tools.scripting.ScriptContext
import io.github.stream29.kode.ui.core.AgentEvent
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.time.Clock

internal class ScriptOnlyAgentEngine(
    private val promptExecutor: PromptExecutor,
    private val sessionManager: SessionManager,
    private val sessionBridge: KoogSessionBridge,
    private val messageHandler: MessageHandler,
    private val hookManager: HookManager,
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scriptToolDescriptor = KotlinScriptTool(scriptContext = ScriptContext()).descriptor

    private data class ResolvedToolCall(
        val call: Message.Tool.Call,
        val rawToolName: String,
        val toolName: String,
        val toolArgs: String,
        val toolCallId: String,
        val parsedArgs: JsonElement,
    )

    private data class ToolExecutionOutcome(
        val content: String,
        val isError: Boolean,
        val errorMessage: String?,
        val awaitForUserInput: Boolean,
        val outputList: List<String>,
    )

    suspend fun run(
        sessionId: String,
        model: LLModel,
        modelParams: LLMParams?,
    ): String? {
        val (systemPrompt, messages) = resolvePromptContext(sessionId = sessionId, agentId = runtimeContext.agentId)
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

    private suspend fun resolvePromptContext(sessionId: String, agentId: String?): Pair<String, List<Message>> {
        val messages = sessionBridge.prepareMessagesForAgent(sessionId, agentId)
        val systemPrompt = sessionManager.getAgentConfig(sessionId, agentId).systemPrompt ?: DEFAULT_SYSTEM_PROMPT
        return systemPrompt to messages
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

            val responses = promptExecutor.execute(currentPrompt, model, listOf(scriptToolDescriptor))
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

        val firstNonScriptCall = responses
            .filterIsInstance<Message.Tool.Call>()
            .firstOrNull { call -> call.tool != ToolNames.EXECUTE_KOTLIN_SCRIPT }
        if (firstNonScriptCall != null) {
            throw IllegalStateException(
                "Script-only violation: tool '${firstNonScriptCall.tool}' is not allowed; " +
                        "only '${ToolNames.EXECUTE_KOTLIN_SCRIPT}' is accepted"
            )
        }
    }

    private suspend fun executeToolCall(
        sessionId: String,
        toolCall: Message.Tool.Call
    ): Message.Tool.Result {
        val resolvedCall = resolveToolCall(toolCall)

        if (resolvedCall.rawToolName != ToolNames.EXECUTE_KOTLIN_SCRIPT) {
            throw IllegalStateException(
                "Script-only violation: tool '${resolvedCall.rawToolName}' is not allowed; " +
                        "only '${ToolNames.EXECUTE_KOTLIN_SCRIPT}' is accepted"
            )
        }

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

        val execution = executeScriptTool(toolArgs = finalArgs)

        val processedResult = hookManager.applyToolCallAfterHooks(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolArgs = finalArgs,
            result = execution.content
        )

        val isError = execution.isError
        val errorMessage = if (isError) {
            execution.errorMessage ?: processedResult
        } else {
            null
        }

        saveToolExchange(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            toolCallId = resolvedCall.toolCallId,
            arguments = parseToolArgs(finalArgs),
            result = JsonPrimitive(processedResult),
            isError = isError,
            errorMessage = errorMessage,
            outputList = execution.outputList,
        )

        if (!isError) {
            eventListener?.onEvent(
                AgentEvent.ToolCallCompleted(
                    toolName = resolvedCall.toolName,
                    result = processedResult
                ),
                sessionId
            )
        } else {
            eventListener?.onEvent(
                AgentEvent.Error(
                    message = errorMessage ?: processedResult,
                    exception = null,
                ),
                sessionId,
            )
        }

        if (execution.awaitForUserInput) {
            awaitForUserInput(sessionId)
        }

        return buildToolResult(
            toolCall = resolvedCall.call,
            rawToolName = resolvedCall.rawToolName,
            content = processedResult,
        )
    }

    private suspend fun awaitForUserInput(sessionId: String) {
        sessionManager.suspendForUserInput(sessionId)
        val userInput = messageHandler.requestInput(sessionId)
        sessionManager.resumeRun(sessionId, currentJob())
        if (userInput.isNotBlank()) {
            sessionManager.addUserMessage(
                sessionId = sessionId,
                content = userInput,
                agentId = runtimeContext.agentId,
            )
        }
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
            outputList = emptyList(),
        )
        return buildToolResult(
            toolCall = resolvedCall.call,
            rawToolName = resultToolName,
            content = reason,
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
        outputList: List<String>,
    ) {
        sessionBridge.saveToolExchange(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            result = result,
            isError = isError,
            errorMessage = errorMessage,
            outputList = outputList,
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

    private suspend fun executeScriptTool(toolArgs: String): ToolExecutionOutcome {
        val scriptContext = ScriptContext()
        val tool = KotlinScriptTool(scriptContext = scriptContext)
        return try {
            val argsJson = runCatching { json.parseToJsonElement(toolArgs).jsonObject }
                .getOrElse { parseError ->
                    val message = "Invalid tool args for '${ToolNames.EXECUTE_KOTLIN_SCRIPT}': ${parseError.message}"
                    return ToolExecutionOutcome(
                        content = message,
                        isError = true,
                        errorMessage = message,
                        awaitForUserInput = false,
                        outputList = scriptContext.consumeOutputList(),
                    )
                }
            val args = runCatching { tool.decodeArgs(argsJson) }
                .getOrElse { decodeError ->
                    val message = "Invalid tool args for '${ToolNames.EXECUTE_KOTLIN_SCRIPT}': ${decodeError.message}"
                    return ToolExecutionOutcome(
                        content = message,
                        isError = true,
                        errorMessage = message,
                        awaitForUserInput = false,
                        outputList = scriptContext.consumeOutputList(),
                    )
                }
            val result = tool.execute(args)
            ToolExecutionOutcome(
                content = tool.encodeResultToString(result),
                isError = false,
                errorMessage = null,
                awaitForUserInput = scriptContext.consumeAwaitForUserInputSignal(),
                outputList = scriptContext.consumeOutputList(),
            )
        } catch (error: Exception) {
            val message = "Error executing tool ${ToolNames.EXECUTE_KOTLIN_SCRIPT}: ${error.message}"
            ToolExecutionOutcome(
                content = message,
                isError = true,
                errorMessage = message,
                awaitForUserInput = false,
                outputList = scriptContext.consumeOutputList(),
            )
        }
    }

    private fun parseToolArgs(toolArgs: String): JsonElement {
        return runCatching {
            json.parseToJsonElement(toolArgs)
        }.getOrElse {
            JsonPrimitive(toolArgs)
        }
    }

    private suspend fun currentJob(): Job {
        val job = currentCoroutineContext()[Job]
        return requireNotNull(job) { "ScriptOnlyAgentEngine requires coroutine Job context" }
    }

    private class SafeStopSignal : RuntimeException()

    companion object {
        val DEFAULT_SYSTEM_PROMPT: String = """
            You are a coding agent named `Kode`.

            Tool usage rules:
            - Keep each script focused, deterministic, and minimal.
            - Handle errors explicitly in script output.
            - println(...) is not visible to the user. It's output is visible for yourself.

            ## Script receiver API (implicit receiver = ScriptContext):

            You can call methods on `ScriptContext` in your script without `this` reference.
            Getting the `ScriptContext` instance by referencing `this` is also acceptable.

            ### `sayToUser(text: String)`
            - Append one user-visible output entry.
            - Each call corresponds to one UI message entry.
            - May written in markdown with mermaid.

            ### `suspendForUserInput()`
            - You must call `suspendForUserInput()` to finish your output. Otherwise, you will be forced to continue.
            - Runtime behavior: the run enters pending-input and resumes after the user provides input.
            - Do not call consumeAwaitForUserInputSignal(); it is runtime-internal.
        """.trimIndent()
    }
}
