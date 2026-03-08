package io.github.stream29.kode.core.agent

import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.ui.core.AgentEventListener
import io.github.stream29.kode.ui.core.MessageHandler

public fun interface SessionExecutionRuntimeFactory {
    public fun create(
        messageHandler: MessageHandler,
        eventListener: AgentEventListener?,
        logger: (String) -> Unit,
    ): SessionExecutionRuntime
}

public class DefaultSessionExecutionRuntimeFactory(
    private val sessionManager: SessionManager,
    private val modelCatalogPort: SessionExecutionModelCatalogPort,
) : SessionExecutionRuntimeFactory {
    override fun create(
        messageHandler: MessageHandler,
        eventListener: AgentEventListener?,
        logger: (String) -> Unit,
    ): SessionExecutionRuntime {
        return SessionExecutionRuntime(
            modelCatalogPort = modelCatalogPort,
            messageHandler = messageHandler,
            eventListener = eventListener,
            logger = logger,
            sessionManager = sessionManager,
        )
    }
}
