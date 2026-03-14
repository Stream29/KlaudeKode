package io.github.stream29.kode.agent.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
public data class TodoItem(
    val name: String,
    val completed: Boolean = false,
    val subItems: List<TodoItem> = emptyList(),
) {
    override fun toString(): String = Json.encodeToString(this)
}
