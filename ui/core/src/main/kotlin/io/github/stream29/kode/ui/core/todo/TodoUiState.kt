package io.github.stream29.kode.ui.core.todo

public data class TodoUiNode(
    val name: String,
    val completed: Boolean,
    val subItems: List<TodoUiNode>,
    val path: String,
    val expanded: Boolean,
    val level: Int,
)

public data class TodoUiState(
    val rootNodes: List<TodoUiNode>,
    val allExpanded: Boolean,
)
