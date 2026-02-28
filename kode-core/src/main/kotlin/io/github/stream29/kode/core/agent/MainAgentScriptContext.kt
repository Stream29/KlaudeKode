package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.tools.scripting.ScriptContext

public class MainAgentScriptContext(
    initialTodos: List<TodoNode> = emptyList(),
    activeTodoFlow: kotlinx.coroutines.flow.MutableStateFlow<List<TodoNode>>? = null,
    private val todoListScriptContext: TodoListScriptContext = TodoListScriptContextImpl(
        initialTodos = initialTodos,
        activeFlow = activeTodoFlow
    ),
    private val userCommunicationScriptContext: UserCommunicationScriptContext = UserCommunicationScriptContextImpl(),
) : ScriptContext,
    TodoListScriptContext by todoListScriptContext,
    UserCommunicationScriptContext by userCommunicationScriptContext {

    override val defaultImports: List<String> = todoListScriptContext.defaultImports + userCommunicationScriptContext.defaultImports

    override val systemPromptInjection: String = buildSystemPromptInjection(
        userCommunicationScriptContext.systemPromptInjection,
        todoListScriptContext.systemPromptInjection
    )

    public companion object {
        public fun buildSystemPromptInjection(
            userCommInjection: String,
            todoInjection: String
        ): String = """
            ## Script receiver API (implicit receiver = MainAgentScriptContext):

            You can call methods on `MainAgentScriptContext` in your script without `this` reference.
            Getting the receiver instance by referencing `this` is also acceptable.
        """.trimIndent() + "\n\n" + userCommInjection + "\n" + todoInjection

        public val DEFAULT_SYSTEM_PROMPT_INJECTION: String = buildSystemPromptInjection(
            UserCommunicationScriptContextImpl().systemPromptInjection,
            TodoListScriptContextImpl().systemPromptInjection
        )
    }
}
