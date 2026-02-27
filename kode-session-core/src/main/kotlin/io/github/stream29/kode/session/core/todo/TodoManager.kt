package io.github.stream29.kode.session.core.todo

import io.github.stream29.kode.session.core.model.TodoNode

public class TodoManager(
    initialNodes: List<TodoNode> = emptyList(),
) {
    private var nodes: List<TodoNode> = initialNodes.toList()

    public fun updateNodes(newNodes: List<TodoNode>) {
        nodes = newNodes.toList()
    }

    public fun listAllNodes(): List<TodoNode> {
        return nodes
    }
}
