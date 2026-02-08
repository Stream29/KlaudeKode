package io.github.stream29.kode.ui.core

public data class ToolApprovalRequest(
    val id: String,
    val toolName: String,
    val arguments: String,
    val description: String
)

public enum class ToolApprovalDecision {
    Approve,
    ApproveForSession,
    Reject,
}

public interface ApprovalHandler {
    public suspend fun requestApproval(request: ToolApprovalRequest): ToolApprovalDecision

    public suspend fun requestApproval(
        request: ToolApprovalRequest,
        sessionId: String
    ): ToolApprovalDecision {
        return requestApproval(request)
    }
}
