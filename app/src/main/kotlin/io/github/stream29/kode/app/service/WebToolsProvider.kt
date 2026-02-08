package io.github.stream29.kode.app.service

import io.github.stream29.kode.tools.WebTools
import io.github.stream29.kode.ui.core.MessageHandler

public interface WebToolsProvider {
    public fun create(
        messageHandler: MessageHandler,
        logger: (String) -> Unit,
    ): WebTools
}

public class DefaultWebToolsProvider : WebToolsProvider {
    override fun create(
        messageHandler: MessageHandler,
        logger: (String) -> Unit,
    ): WebTools {
        return WebTools(
            messageHandler = messageHandler,
            logger = logger,
        )
    }
}
