package io.github.stream29.kode.ui.core.todo

public data class TodoUiNode(
    val name: String,
    val isCompleted: Boolean,
    val subtasks: List<TodoUiNode>,
    val path: String,
    val expanded: Boolean,
    val level: Int,
)

public data class TodoUiState(
    val rootNodes: List<TodoUiNode>,
    val allExpanded: Boolean,
)
