package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.config.api.LlmModelConfig
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.ApprovalHandler
import io.github.stream29.kode.ui.core.MessageHandler

public class SessionAwareAgentFactoryProvider(
    private val sessionManager: SessionManager,
    private val hookManager: HookManager,
) {
    public fun create(
        auths: List<LlmAuthConfig>,
        models: List<LlmModelConfig>,
        messageHandler: MessageHandler,
        approvalHandler: ApprovalHandler?,
        disabledTools: Set<String>,
        mcpToolRegistry: ToolRegistry?,
        eventListener: AgentEventListener?,
        logger: (String) -> Unit,
    ): SessionAwareAgentFactory {
        return SessionAwareAgentFactory(
            auths = auths,
            models = models,
            messageHandler = messageHandler,
            approvalHandler = approvalHandler,
            disabledTools = disabledTools,
            mcpToolRegistry = mcpToolRegistry,
            eventListener = eventListener,
            hookManager = hookManager,
            logger = logger,
            sessionManager = sessionManager,
        )
    }
}
