package io.github.stream29.kode.tools.scripting

import kotlinx.serialization.Serializable

@Serializable
public data class KotlinScriptParams(
    val script: String,
    val timeoutSeconds: Int = 30,
)

@Serializable
public sealed interface KotlinScriptResult {
    @Serializable
    public data class Success(
        val returnValue: String,
        val stdout: String,
    ) : KotlinScriptResult

    @Serializable
    public data class Failure(
        val message: String,
        val stdout: String,
    ) : KotlinScriptResult
}