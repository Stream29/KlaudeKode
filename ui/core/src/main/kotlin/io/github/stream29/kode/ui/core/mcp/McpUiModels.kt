package io.github.stream29.kode.ui.core.mcp

public enum class McpTestStatus {
    Success,
    Error,
}

public enum class McpHealthStatus(
    public val label: String,
) {
    Unknown(label = "Unknown"),
    Checking(label = "Checking"),
    Healthy(label = "Healthy"),
    Unhealthy(label = "Unhealthy"),
}

public data class McpToolParameterSummary(
    val name: String,
    val type: String,
    val description: String,
)

public data class McpToolSummary(
    val name: String,
    val description: String,
    val requiredParameters: List<McpToolParameterSummary>,
    val optionalParameters: List<McpToolParameterSummary>,
)

public data class McpTestResult(
    val status: McpTestStatus,
    val message: String,
    val tools: List<McpToolSummary>,
)

public data class McpHealthResult(
    val status: McpHealthStatus,
    val message: String,
)
