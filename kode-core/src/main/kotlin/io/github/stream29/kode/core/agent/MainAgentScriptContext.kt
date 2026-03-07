package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.tools.scripting.ScriptContext

public interface AgentScriptContext : ScriptContext, TodoListScriptContext {
    public fun consumeOutputList(): List<String>
    public fun consumeAwaitForUserInputSignal(): Boolean
}

public interface MainAgentScriptContextModules :
    TodoListScriptContext,
    UserCommunicationScriptContext

public interface SubAgentScriptContextModules : TodoListScriptContext

private interface ScriptContextComposition : ScriptContext

private class ScriptContextCompositionImpl(
    receiverTypeName: String,
    modulesInStableOrder: List<ScriptContext>,
    roleConstraints: List<String> = emptyList(),
) : ScriptContextComposition {
    private val modules: List<ScriptContext> = modulesInStableOrder.toList()

    override val defaultImports: List<String> = modules
        .flatMap { module -> module.defaultImports }
        .distinct()

    override val systemPromptInjection: String = buildString {
        appendLine("## Script receiver API (implicit receiver = $receiverTypeName):")
        appendLine()
        appendLine("You can call methods on `$receiverTypeName` in your script without `this` reference.")
        appendLine("Getting the receiver instance by referencing `this` is also acceptable.")
        if (roleConstraints.isNotEmpty()) {
            appendLine()
            appendLine("Role constraints:")
            roleConstraints.forEach { constraint ->
                appendLine("- $constraint")
            }
        }
        val moduleInjections = modules
            .map { module -> module.systemPromptInjection.trim() }
            .filter { injection -> injection.isNotBlank() }
        if (moduleInjections.isNotEmpty()) {
            appendLine()
            append(moduleInjections.joinToString(separator = "\n\n"))
        }
    }.trim()
}

public class MainAgentScriptContext(
    initialTodos: List<TodoNode> = emptyList(),
    activeTodoFlow: kotlinx.coroutines.flow.MutableStateFlow<List<TodoNode>>? = null,
    private val todoListScriptContext: TodoListScriptContext = TodoListScriptContextImpl(
        initialTodos = initialTodos,
        activeFlow = activeTodoFlow
    ),
    private val userCommunicationScriptContext: UserCommunicationScriptContext = UserCommunicationScriptContextImpl(),
) : AgentScriptContext,
    MainAgentScriptContextModules,
    TodoListScriptContext by todoListScriptContext,
    UserCommunicationScriptContext by userCommunicationScriptContext {

    private val composition: ScriptContextComposition = ScriptContextCompositionImpl(
        receiverTypeName = "MainAgentScriptContext",
        modulesInStableOrder = listOf(userCommunicationScriptContext, todoListScriptContext),
    )

    override val defaultImports: List<String> = composition.defaultImports

    override val systemPromptInjection: String = composition.systemPromptInjection

    public companion object {
        public fun buildSystemPromptInjection(
            userCommunicationModule: UserCommunicationScriptContext,
            todoListModule: TodoListScriptContext,
        ): String = ScriptContextCompositionImpl(
            receiverTypeName = "MainAgentScriptContext",
            modulesInStableOrder = listOf(userCommunicationModule, todoListModule),
        ).systemPromptInjection

        public val DEFAULT_SYSTEM_PROMPT_INJECTION: String = buildSystemPromptInjection(
            userCommunicationModule = UserCommunicationScriptContextImpl(),
            todoListModule = TodoListScriptContextImpl(),
        )
    }
}

public class SubAgentScriptContext(
    initialTodos: List<TodoNode> = emptyList(),
    activeTodoFlow: kotlinx.coroutines.flow.MutableStateFlow<List<TodoNode>>? = null,
    private val todoListScriptContext: TodoListScriptContext = TodoListScriptContextImpl(
        initialTodos = initialTodos,
        activeFlow = activeTodoFlow,
    ),
) : AgentScriptContext,
    SubAgentScriptContextModules,
    TodoListScriptContext by todoListScriptContext {

    private val composition: ScriptContextComposition = ScriptContextCompositionImpl(
        receiverTypeName = "SubAgentScriptContext",
        modulesInStableOrder = listOf(todoListScriptContext),
        roleConstraints = listOf(
            "Direct user communication is unavailable in this role.",
            "Pausing for direct user input is unavailable in this role.",
            "Spawning or managing child agents is unavailable in this role.",
        ),
    )

    override val defaultImports: List<String> = composition.defaultImports

    override val systemPromptInjection: String = composition.systemPromptInjection

    override fun consumeOutputList(): List<String> {
        return emptyList()
    }

    override fun consumeAwaitForUserInputSignal(): Boolean {
        return false
    }

    public companion object {
        public val DEFAULT_SYSTEM_PROMPT_INJECTION: String = ScriptContextCompositionImpl(
            receiverTypeName = "SubAgentScriptContext",
            modulesInStableOrder = listOf(TodoListScriptContextImpl()),
            roleConstraints = listOf(
                "Direct user communication is unavailable in this role.",
                "Pausing for direct user input is unavailable in this role.",
                "Spawning or managing child agents is unavailable in this role.",
            ),
        ).systemPromptInjection
    }
}
