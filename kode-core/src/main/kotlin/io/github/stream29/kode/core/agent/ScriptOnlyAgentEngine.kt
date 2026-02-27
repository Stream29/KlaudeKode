package io.github.stream29.kode.core.agent

import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.params.LLMParams
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.core.port.ToolCallPreHookResult
import io.github.stream29.kode.core.port.ToolSideEffectPort
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.tool.ToolNames
import io.github.stream29.kode.tools.scripting.DefaultScriptContext
import io.github.stream29.kode.tools.scripting.KotlinScriptTool
import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.session.core.todo.generateTodoGuidelineInjection

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
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.functions
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.isAccessible

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
    private val scriptContextFactory: () -> ScriptContext = { DefaultScriptContext() },
    private val runtimeSideEffectPort: RuntimeSideEffectPort = RuntimeSideEffectAdapter(
        messageHandler = messageHandler,
        eventListener = eventListener,
        logger = logger,
    ),
    private val toolSideEffectPort: ToolSideEffectPort = ToolSideEffectAdapter(
        hookManager = hookManager,
    ),
    private val sessionSideEffectPort: SessionSideEffectPort = SessionSideEffectAdapter(
        sessionManager = sessionManager,
        sessionBridge = sessionBridge,
    ),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val defaultScriptContext: ScriptContext = scriptContextFactory()
    private val scriptToolDescriptor = KotlinScriptTool(scriptContext = defaultScriptContext).descriptor
    private val defaultSystemPrompt: String = buildDefaultSystemPrompt(defaultScriptContext.systemPromptInjection)

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
        val todoChanged: Boolean,
        val latestTodos: List<TodoNode>,
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
        val messages = sessionSideEffectPort.prepareMessagesForAgent(sessionId = sessionId, agentId = agentId)
        val systemPrompt = sessionSideEffectPort.resolveSystemPrompt(
            sessionId = sessionId,
            agentId = agentId,
            fallback = defaultSystemPrompt,
        )
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

    private suspend fun resolveCurrentTodos(sessionId: String): List<TodoNode> {
        val agentId = runtimeContext.agentId ?: return emptyList()
        return sessionManager.getAgentTodo(sessionId, agentId)
    }

    private suspend fun buildSystemPromptWithTodoInjection(
        sessionId: String,
    ): String {
        val baseSystemPrompt = sessionSideEffectPort.resolveSystemPrompt(
            sessionId = sessionId,
            agentId = runtimeContext.agentId,
            fallback = defaultSystemPrompt,
        )
        val todoGuideline = generateTodoGuidelineInjection()
        return """
            $baseSystemPrompt
            
            $todoGuideline
        """.trimIndent()
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

            val currentTodos = resolveCurrentTodos(sessionId = sessionId)
            val currentSystemPrompt = buildSystemPromptWithTodoInjection(
                sessionId = sessionId,
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
        initialTodos: List<TodoNode>,
    ): Message.Tool.Result {
        val resolvedCall = resolveToolCall(toolCall)

        if (resolvedCall.rawToolName != ToolNames.EXECUTE_KOTLIN_SCRIPT) {
            throw IllegalStateException(
                "Script-only violation: tool '${resolvedCall.rawToolName}' is not allowed; " +
                        "only '${ToolNames.EXECUTE_KOTLIN_SCRIPT}' is accepted"
            )
        }

        val preHook = toolSideEffectPort.applyToolCallBeforeHooks(sessionId, resolvedCall.toolName, resolvedCall.toolArgs)
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

        runtimeSideEffectPort.log("🔧 Calling tool: ${resolvedCall.toolName}")
        runtimeSideEffectPort.log("   Args: ${finalArgs.take(100)}")
        runtimeSideEffectPort.onToolCallStarting(
            sessionId = sessionId,
            toolName = resolvedCall.toolName,
            arguments = finalArgs,
        )

        val execution = executeScriptTool(toolArgs = finalArgs, initialTodos = initialTodos)

        val processedResult = toolSideEffectPort.applyToolCallAfterHooks(
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
            val agentId = runtimeContext.agentId
            if (agentId != null) {
                sessionManager.updateAgentTodo(
                    sessionId = sessionId,
                    agentId = agentId,
                    todos = execution.latestTodos
                )
            }
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
        sessionSideEffectPort.saveToolExchange(
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

    private suspend fun executeScriptTool(
        toolArgs: String,
        initialTodos: List<TodoNode>,
    ): ToolExecutionOutcome {
        val scriptContext = if (initialTodos.isEmpty()) {
            scriptContextFactory()
        } else {
            DefaultScriptContext(initialTodos = initialTodos)
        }
        val tool = KotlinScriptTool(scriptContext = scriptContext)
        val todoStateFlow = (scriptContext as? DefaultScriptContext)?.getTodoStateFlow()
        val todoSnapshot = todoStateFlow?.value?.toList()
        
        return try {
            val argsJson = runCatching { json.parseToJsonElement(toolArgs).jsonObject }
                .getOrElse { parseError ->
                    val message = "Invalid tool args for '${ToolNames.EXECUTE_KOTLIN_SCRIPT}': ${parseError.message}"
                    return ToolExecutionOutcome(
                        content = message,
                        isError = true,
                        errorMessage = message,
                        awaitForUserInput = false,
                        todoChanged = false,
                        latestTodos = initialTodos,
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
                        todoChanged = false,
                        latestTodos = initialTodos,
                        outputList = scriptContext.consumeOutputList(),
                    )
                }
            val result = tool.execute(args)
            val currentTodos = todoStateFlow?.value ?: initialTodos
            val todoChanged = todoStateFlow != null && todoSnapshot != todoStateFlow.value
            
            println("[DEBUG_LOG] todoSnapshot: $todoSnapshot")
            println("[DEBUG_LOG] currentTodos: $currentTodos")
            println("[DEBUG_LOG] todoChanged: $todoChanged")
            
            ToolExecutionOutcome(
                content = tool.encodeResultToString(result),
                isError = false,
                errorMessage = null,
                awaitForUserInput = scriptContext.consumeAwaitForUserInputSignal(),
                todoChanged = todoChanged,
                latestTodos = currentTodos,
                outputList = scriptContext.consumeOutputList(),
            )
        } catch (error: Exception) {
            val message = "Error executing tool ${ToolNames.EXECUTE_KOTLIN_SCRIPT}: ${error.message}"
            val currentTodos = todoStateFlow?.value ?: initialTodos
            val todoChanged = todoStateFlow != null && todoSnapshot != todoStateFlow.value
            
            ToolExecutionOutcome(
                content = message,
                isError = true,
                errorMessage = message,
                awaitForUserInput = false,
                todoChanged = todoChanged,
                latestTodos = currentTodos,
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

    private class RuntimeSideEffectAdapter(
        private val messageHandler: MessageHandler,
        private val eventListener: AgentEventListener?,
        private val logger: (String) -> Unit,
    ) : RuntimeSideEffectPort, RuntimeInputPort {
        override fun isSafeStopRequested(sessionId: String): Boolean {
            return messageHandler.isSafeStopRequested(sessionId)
        }

        override fun onSafeStopReached(sessionId: String) {
            messageHandler.onSafeStopReached(sessionId)
        }

        override fun onToolCallStarting(sessionId: String, toolName: String, arguments: String) {
            eventListener?.onEvent(
                AgentEvent.ToolCallStarting(
                    toolName = toolName,
                    arguments = arguments,
                ),
                sessionId,
            )
        }

        override fun onToolCallCompleted(sessionId: String, toolName: String, result: String) {
            eventListener?.onEvent(
                AgentEvent.ToolCallCompleted(
                    toolName = toolName,
                    result = result,
                ),
                sessionId,
            )
        }

        override fun onToolCallFailed(sessionId: String, message: String) {
            eventListener?.onEvent(
                AgentEvent.Error(
                    message = message,
                    exception = null,
                ),
                sessionId,
            )
        }

        override fun log(message: String) {
            logger(message)
        }

        override suspend fun requestInput(sessionId: String): String {
            return messageHandler.requestInput(sessionId)
        }
    }

    private class ToolSideEffectAdapter(
        private val hookManager: HookManager,
    ) : ToolSideEffectPort {
        override fun applyToolCallBeforeHooks(sessionId: String, toolName: String, toolArgs: String): ToolCallPreHookResult {
            val result = hookManager.applyToolCallBeforeHooks(
                sessionId = sessionId,
                toolName = toolName,
                toolArgs = toolArgs,
            )
            return ToolCallPreHookResult(
                allowed = result.allowed,
                reason = result.reason,
                toolArgs = result.toolArgs,
            )
        }

        override fun applyToolCallAfterHooks(sessionId: String, toolName: String, toolArgs: String, result: String): String {
            return hookManager.applyToolCallAfterHooks(
                sessionId = sessionId,
                toolName = toolName,
                toolArgs = toolArgs,
                result = result,
            )
        }
    }

    private class SessionSideEffectAdapter(
        private val sessionManager: SessionManager,
        private val sessionBridge: KoogSessionBridge,
    ) : SessionSideEffectPort, SessionRunLifecyclePort {
        override suspend fun prepareMessagesForAgent(sessionId: String, agentId: String?): List<Message> {
            return sessionBridge.prepareMessagesForAgent(sessionId = sessionId, agentId = agentId)
        }

        override suspend fun resolveSystemPrompt(sessionId: String, agentId: String?, fallback: String): String {
            return sessionManager.getAgentConfig(sessionId = sessionId, agentId = agentId).systemPrompt ?: fallback
        }

        override suspend fun suspendForUserInput(sessionId: String) {
            sessionManager.suspendForUserInput(sessionId)
        }

        override suspend fun saveToolExchange(
            sessionId: String,
            toolName: String,
            toolCallId: String,
            arguments: JsonElement,
            result: JsonElement,
            isError: Boolean,
            errorMessage: String?,
            outputList: List<String>,
            agentId: String?,
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
                agentId = agentId,
            )
        }

        override suspend fun resumeRun(sessionId: String, job: Job) {
            sessionManager.resumeRun(sessionId = sessionId, ownerJob = job)
        }

        override suspend fun addUserMessage(sessionId: String, content: String, agentId: String?) {
            sessionManager.addUserMessage(
                sessionId = sessionId,
                content = content,
                agentId = agentId,
            )
        }
    }

    private interface RuntimeInputPort {
        suspend fun requestInput(sessionId: String): String
    }

    private interface SessionRunLifecyclePort {
        suspend fun resumeRun(sessionId: String, job: Job)

        suspend fun addUserMessage(sessionId: String, content: String, agentId: String?)
    }

    companion object {
        private val BASE_SYSTEM_PROMPT: String = """
            You are a coding agent named `Kode`.

            Tool usage rules:
            - Keep each script focused, deterministic, and minimal.
            - Handle errors explicitly in script output.
            - println(...) is not visible to the user. It's output is visible for yourself.

        """.trimIndent()

        fun buildDefaultSystemPrompt(systemPromptInjection: String): String {
            val normalizedInjection = systemPromptInjection.trim()
            if (normalizedInjection.isBlank()) {
                return BASE_SYSTEM_PROMPT
            }
            return """
                $BASE_SYSTEM_PROMPT

                $normalizedInjection
            """.trimIndent()
        }

        val DEFAULT_SYSTEM_PROMPT: String = buildDefaultSystemPrompt(DefaultScriptContext.DEFAULT_SYSTEM_PROMPT_INJECTION)
    }
}
