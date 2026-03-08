package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.agent.tool.ToolNames
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.toDeprecatedClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

internal class ScriptOnlyAgentEngine(
    private val promptExecutor: PromptExecutor,
    private val sessionManager: SessionManager,
    private val messageHandler: MessageHandler,
    private val eventListener: AgentEventListener?,
    private val logger: (String) -> Unit,
    private val runtimeContext: AgentRuntimeContext,
    private val scriptContextFactory: (List<TodoItem>, MutableStateFlow<List<TodoItem>>?) -> AgentScriptContext =
        { initialTodos, activeTodoFlow ->
            MainAgentScriptContext(initialTodos = initialTodos, activeTodoFlow = activeTodoFlow)
        },
    private val runtimeSideEffectPort: RuntimeSideEffectPort = RuntimeSideEffectAdapter(
        messageHandler = messageHandler,
        eventListener = eventListener,
        logger = logger,
    ),
    private val sessionSideEffectPort: SessionSideEffectPort = SessionSideEffectAdapter(
        sessionManager = sessionManager,
        sessionQueryPort = SessionManagerSessionQueryPort(sessionManager),
    ),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val defaultScriptContext: AgentScriptContext = scriptContextFactory(emptyList(), null)
    private val scriptToolExecutor = ScriptToolExecutor(
        json = json,
        sessionManager = sessionManager,
        scriptContextFactory = scriptContextFactory,
        resolveTodoAgentId = ::resolveTodoAgentId,
    )
    private val scriptToolDescriptor = buildScriptTool(defaultScriptContext).descriptor
    private val defaultSystemPrompt: String = buildDefaultSystemPrompt(BASE_SYSTEM_PROMPT, defaultScriptContext.systemPromptInjection)

    private data class ResolvedToolCall(
        val call: Message.Tool.Call,
        val rawToolName: String,
        val toolName: String,
        val toolArgs: String,
        val toolCallId: String,
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

    private suspend fun resolveSystemPrompt(sessionId: String, agentId: String?): String {
        val baseSystemPrompt = sessionSideEffectPort.resolveSystemPrompt(
            sessionId = sessionId,
            agentId = agentId,
            fallback = BASE_SYSTEM_PROMPT,
        )
        return buildDefaultSystemPrompt(baseSystemPrompt, defaultScriptContext.systemPromptInjection)
    }

    private suspend fun resolvePromptContext(sessionId: String, agentId: String?): Pair<String, List<Message>> {
        val messages = sessionSideEffectPort.prepareMessagesForAgent(sessionId = sessionId, agentId = agentId)
        val systemPrompt = resolveSystemPrompt(sessionId, agentId)
        return systemPrompt to messages
    }

    private suspend fun resolveCurrentTodos(sessionId: String): List<TodoItem> {
        val agentId = resolveTodoAgentId(sessionId = sessionId)
        return sessionManager.getAgentTodo(sessionId, agentId)
    }

    private fun resolveTodoAgentId(sessionId: String): String {
        return runtimeContext.agentId ?: "main-$sessionId"
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
            if (runtimeSideEffectPort.isSafeStopRequested(sessionId)) {
                runtimeSideEffectPort.onSafeStopReached(sessionId)
                throw SafeStopSignal()
            }

            iteration++

            val refreshedMessages = sessionSideEffectPort.prepareMessagesForAgent(sessionId, runtimeContext.agentId)
            if (refreshedMessages.size >= allMessages.size) {
                allMessages = refreshedMessages.toMutableList()
            }

            resolveCurrentTodos(sessionId = sessionId)
            val currentSystemPrompt = resolveSystemPrompt(
                sessionId = sessionId,
                agentId = runtimeContext.agentId,
            )

            val currentPrompt = buildPrompt(
                sessionId = sessionId,
                iteration = iteration,
                systemPrompt = currentSystemPrompt,
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
                        if (runtimeSideEffectPort.isSafeStopRequested(sessionId)) {
                            runtimeSideEffectPort.onSafeStopReached(sessionId)
                            throw SafeStopSignal()
                        }
                        allMessages.add(response)
                        val toolResult = executeToolCall(
                            sessionId = sessionId,
                            toolCall = response,
                            initialTodos = resolveCurrentTodos(sessionId = sessionId),
                        )
                        allMessages.add(toolResult)
                        if (runtimeSideEffectPort.isSafeStopRequested(sessionId)) {
                            runtimeSideEffectPort.onSafeStopReached(sessionId)
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
        toolCall: Message.Tool.Call,
        initialTodos: List<TodoItem>,
    ): Message.Tool.Result {
        val resolvedCall = resolveToolCall(toolCall)

        if (resolvedCall.rawToolName != ToolNames.EXECUTE_KOTLIN_SCRIPT) {
            throw IllegalStateException(
                "Script-only violation: tool '${resolvedCall.rawToolName}' is not allowed; " +
                        "only '${ToolNames.EXECUTE_KOTLIN_SCRIPT}' is accepted"
            )
        }

        val finalArgs = resolvedCall.toolArgs

        runtimeSideEffectPort.log("🔧 Calling tool: ${resolvedCall.toolName}")
        runtimeSideEffectPort.log("   Args: ${finalArgs.take(100)}")
        runtimeSideEffectPort.onToolCallStarting(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            arguments = finalArgs,
        )

        val execution = try {
            scriptToolExecutor.execute(
                sessionId = sessionId,
                toolArgs = finalArgs,
                initialTodos = initialTodos,
            )
        } catch (error: CancellationException) {
            val isContextActive = currentCoroutineContext()[Job]?.isActive == true
            if (!isContextActive) {
                persistInterruptedToolExchange(
                    sessionId = sessionId,
                    resolvedCall = resolvedCall,
                    finalArgs = finalArgs,
                )
            }
            throw error
        }

        val processedResult = execution.content

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
            awaitForUserInput = execution.awaitForUserInput,
        )

        if (!isError) {
            runtimeSideEffectPort.onToolCallCompleted(
                sessionId = sessionId,
                toolName = resolvedCall.toolName,
                result = processedResult,
            )
        } else {
            runtimeSideEffectPort.onToolCallFailed(
                sessionId = sessionId,
                message = errorMessage ?: processedResult,
            )
        }
        if (execution.todoChanged) {
            val agentId = resolveTodoAgentId(sessionId = sessionId)
            sessionManager.updateAgentTodo(
                sessionId = sessionId,
                agentId = agentId,
                todos = execution.latestTodos
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

    private suspend fun persistInterruptedToolExchange(
        sessionId: String,
        resolvedCall: ResolvedToolCall,
        finalArgs: String,
    ) {
        runCatching {
            withContext(NonCancellable) {
                saveToolExchange(
                    sessionId = sessionId,
                    toolName = resolvedCall.toolName,
                    toolCallId = resolvedCall.toolCallId,
                    arguments = parseToolArgs(finalArgs),
                    result = JsonPrimitive(INTERRUPTED_OPERATION_MESSAGE),
                    isError = true,
                    errorMessage = INTERRUPTED_OPERATION_MESSAGE,
                    outputList = emptyList(),
                    awaitForUserInput = false,
                )
                runtimeSideEffectPort.onToolCallFailed(
                    sessionId = sessionId,
                    message = INTERRUPTED_OPERATION_MESSAGE,
                )
            }
        }.onFailure { persistError ->
            runtimeSideEffectPort.log(
                "Failed to persist interrupted script tool exchange: ${persistError.message}",
            )
        }
    }

    private suspend fun awaitForUserInput(sessionId: String) {
        sessionSideEffectPort.suspendForUserInput(sessionId)
        val userInput = requestUserInput(sessionId)
        resumeSessionRun(sessionId)
        if (userInput.isNotBlank()) {
            appendUserMessage(
                sessionId = sessionId,
                content = userInput,
            )
        }
    }

    private suspend fun requestUserInput(sessionId: String): String {
        val runtimeInputPort = runtimeSideEffectPort as? RuntimeInputPort
        return runtimeInputPort?.requestInput(sessionId)
            ?: messageHandler.requestInput(sessionId)
    }

    private suspend fun resumeSessionRun(sessionId: String) {
        val runLifecyclePort = sessionSideEffectPort as? SessionRunLifecyclePort
        if (runLifecyclePort != null) {
            runLifecyclePort.resumeRun(sessionId = sessionId, job = currentJob())
            return
        }
        sessionManager.resumeRun(sessionId, currentJob())
    }

    private suspend fun appendUserMessage(sessionId: String, content: String) {
        val runLifecyclePort = sessionSideEffectPort as? SessionRunLifecyclePort
        if (runLifecyclePort != null) {
            runLifecyclePort.addUserMessage(
                sessionId = sessionId,
                content = content,
                agentId = runtimeContext.agentId,
            )
            return
        }
        sessionManager.addUserMessage(
            sessionId = sessionId,
            content = content,
            agentId = runtimeContext.agentId,
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
        awaitForUserInput: Boolean,
    ) {
        sessionSideEffectPort.saveToolExchange(
            sessionId = sessionId,
            toolName = toolName,
            toolCallId = toolCallId,
            arguments = arguments,
            result = result,
            isError = isError,
            errorMessage = errorMessage,
            outputList = outputList,
            awaitForUserInput = awaitForUserInput,
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

    private interface RuntimeInputPort {
        suspend fun requestInput(sessionId: String): String
    }

    private interface SessionRunLifecyclePort {
        suspend fun resumeRun(sessionId: String, job: Job)

        suspend fun addUserMessage(sessionId: String, content: String, agentId: String?)
    }

    companion object {
        private const val INTERRUPTED_OPERATION_MESSAGE: String = "This operation was interrupted by user."

        private val BASE_SYSTEM_PROMPT: String = """
            You are a coding agent named `Kode`.

            Tool usage rules:
            - Keep each script focused, deterministic, and minimal.
            - Handle errors explicitly in script output.
            - println(...) is not visible to the user. It's output is visible for yourself.

        """.trimIndent()

        fun buildDefaultSystemPrompt(baseSystemPrompt: String, systemPromptInjection: String): String {
            val normalizedInjection = systemPromptInjection.trim()
            if (normalizedInjection.isBlank()) {
                return baseSystemPrompt
            }
            return """
                $baseSystemPrompt

                $normalizedInjection
            """.trimIndent()
        }

        val DEFAULT_SYSTEM_PROMPT: String =
            buildDefaultSystemPrompt(BASE_SYSTEM_PROMPT, MainAgentScriptContext.DEFAULT_SYSTEM_PROMPT_INJECTION)
    }
}
