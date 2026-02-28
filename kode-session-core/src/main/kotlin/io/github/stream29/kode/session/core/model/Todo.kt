package io.github.stream29.kode.session.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
public data class TodoNode(
    val name: String,
    val isCompleted: Boolean,
    val subtasks: List<TodoNode> = emptyList(),
) {
    override fun toString(): String = Json.encodeToString(this)
}
