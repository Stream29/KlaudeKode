package io.github.stream29.kode.session.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class TodoNode(
    val name: String,
    @SerialName("completed")
    @JsonNames("isCompleted")
    val isCompleted: Boolean,
    @SerialName("subItems")
    @JsonNames("subtasks")
    val subtasks: List<TodoNode> = emptyList(),
) {
    override fun toString(): String = Json.encodeToString(this)
}

public typealias TodoItem = TodoNode
