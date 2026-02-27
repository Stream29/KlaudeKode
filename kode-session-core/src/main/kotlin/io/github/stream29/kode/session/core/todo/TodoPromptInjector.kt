package io.github.stream29.kode.session.core.todo

import io.github.stream29.kode.session.core.model.TodoNode

public fun generateTodoGuidelineInjection(): String {
    return """
        ## Todo List Guidelines
        
        You have access to a hierarchical todo list to manage complex tasks.
        
        ### When to use:
        - **Required**: For large tasks that can be decomposed into multiple steps.
        - **Optional**: For small, atomic tasks that can be completed in a single round.
        
        ### Best Practices:
        1. Always decompose complex requests into smaller, manageable todo nodes.
        2. Update the status of nodes as you progress.
        3. Use `todoList()` to see current state if you need to recall your progress.
        4. If a branch node needs complete rewriting, use `todoUpdate` with `newChildren`.
    """.trimIndent()
}

private fun escapeInlineMarkdown(value: String): String {
    return value
        .replace("`", "\\`")
        .replace("\n", " ")
}
