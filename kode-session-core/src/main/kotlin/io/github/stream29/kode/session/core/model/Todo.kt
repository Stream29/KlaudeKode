package io.github.stream29.kode.session.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class TodoNode(
    val name: String,
    val isCompleted: Boolean,
    val subtasks: List<TodoNode> = emptyList(),
)
