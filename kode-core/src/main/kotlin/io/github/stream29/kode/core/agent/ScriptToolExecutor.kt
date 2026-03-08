package io.github.stream29.kode.core.agent

import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.agent.tool.ToolNames
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.tools.scripting.KotlinScriptTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal data class ToolExecutionOutcome(
    val content: String,
    val isError: Boolean,
    val errorMessage: String?,
    val awaitForUserInput: Boolean,
    val todoChanged: Boolean,
    val latestTodos: List<TodoItem>,
    val outputList: List<String>,
)

internal class ScriptToolExecutor(
    private val json: Json,
    private val sessionManager: SessionManager,
    private val scriptContextFactory: (List<TodoItem>, MutableStateFlow<List<TodoItem>>?) -> AgentScriptContext,
    private val resolveTodoAgentId: (String) -> String,
) {
    suspend fun execute(
        sessionId: String,
        toolArgs: String,
        initialTodos: List<TodoItem>,
    ): ToolExecutionOutcome {
        val agentId = resolveTodoAgentId(sessionId)
        val activeFlow = sessionManager.getAgentTodoStateFlow(sessionId, agentId)
        val scriptContext = scriptContextFactory(initialTodos, activeFlow)
        val tool = buildScriptTool(scriptContext)
        val todoStateFlow = scriptContext.todoStateFlow
        val todoSnapshot = todoStateFlow.value.toList()

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
            val currentTodos = todoStateFlow.value
            val todoChanged = todoSnapshot != todoStateFlow.value

            ToolExecutionOutcome(
                content = tool.encodeResultToString(result),
                isError = false,
                errorMessage = null,
                awaitForUserInput = scriptContext.consumeAwaitForUserInputSignal(),
                todoChanged = todoChanged,
                latestTodos = currentTodos,
                outputList = scriptContext.consumeOutputList(),
            )
        } catch (error: CancellationException) {
            val isContextActive = currentCoroutineContext()[Job]?.isActive == true
            if (!isContextActive) {
                throw error
            }

            val message = "Error executing tool ${ToolNames.EXECUTE_KOTLIN_SCRIPT}: ${error.message}"
            val currentTodos = todoStateFlow.value
            val todoChanged = todoSnapshot != todoStateFlow.value

            ToolExecutionOutcome(
                content = message,
                isError = true,
                errorMessage = message,
                awaitForUserInput = false,
                todoChanged = todoChanged,
                latestTodos = currentTodos,
                outputList = scriptContext.consumeOutputList(),
            )
        } catch (error: Exception) {
            val message = "Error executing tool ${ToolNames.EXECUTE_KOTLIN_SCRIPT}: ${error.message}"
            val currentTodos = todoStateFlow.value
            val todoChanged = todoSnapshot != todoStateFlow.value

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
}

internal fun buildScriptTool(scriptContext: AgentScriptContext): KotlinScriptTool {
    return when (scriptContext) {
        is MainAgentScriptContext -> KotlinScriptTool(scriptContext)
        is SubAgentScriptContext -> KotlinScriptTool(scriptContext)
        else -> error("Unsupported AgentScriptContext type: ${scriptContext::class.qualifiedName}")
    }
}
