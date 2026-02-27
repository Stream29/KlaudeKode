package io.github.stream29.kode.session.core.todo

import io.github.stream29.kode.session.core.model.TodoNode

public fun generateTodoPromptInjection(todos: List<TodoNode>): String {
    if (todos.isEmpty()) {
        return ""
    }

    val todoManager = TodoManager(initialNodes = todos)
    val lines = buildList {
        add("## Current Todos")
        add("")
        add("Use this todo state as the latest source of truth.")
        add("")

        todoManager.listAllNodes().forEach { node ->
            val path = todoManager.getPath(id = node.id) ?: node.text
            val statusMarker = if (node.completed) "x" else " "
            add(
                "- [$statusMarker] Path: `${escapeInlineMarkdown(path)}`; " +
                    "Text: `${escapeInlineMarkdown(node.text)}`; " +
                    "Completed: ${node.completed}",
            )
        }
    }

    return lines.joinToString(separator = "\n")
}

private fun escapeInlineMarkdown(value: String): String {
    return value
        .replace("`", "\\`")
        .replace("\n", " ")
}
