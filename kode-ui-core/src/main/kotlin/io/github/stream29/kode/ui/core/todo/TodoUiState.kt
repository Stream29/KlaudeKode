package io.github.stream29.kode.ui.core.todo

import io.github.stream29.kode.session.core.model.TodoNode

public data class TodoUiNode(
    val node: TodoNode,
    val path: String,
    val expanded: Boolean,
    val level: Int,
)

public data class TodoUiState(
    val rootNodes: List<TodoUiNode>,
    val allExpanded: Boolean,
)
