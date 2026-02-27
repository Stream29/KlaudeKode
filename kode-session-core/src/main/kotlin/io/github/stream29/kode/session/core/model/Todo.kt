package io.github.stream29.kode.session.core.model

import kotlinx.serialization.Serializable

@Serializable
public data class TodoNode(
    val id: String,
    val text: String,
    val completed: Boolean,
    val parentId: String?,
    val metadata: Map<String, String>?,
)
