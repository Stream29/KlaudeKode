package io.github.stream29.kode.core.agent

import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.message.Message
import io.github.stream29.kode.core.port.RuntimeSideEffectPort
import io.github.stream29.kode.core.port.SessionSideEffectPort
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.toSessionManagerDependencies
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MainAgentImplSideEffectPortContractTest {
    @Test
    fun constructorRejectsRuntimePortWithoutSessionPort() {
        assertFailsWith<IllegalStateException> {
            createMainAgent(
                runtimePort = NoopRuntimeSideEffectPort,
                sessionPort = null,
            )
        }
    }

    @Test
    fun constructorRejectsSessionPortWithoutRuntimePort() {
        assertFailsWith<IllegalStateException> {
            createMainAgent(
                runtimePort = null,
                sessionPort = NoopSessionSideEffectPort,
            )
        }
    }

    @Test
    fun constructorAcceptsCustomPortsWhenProvidedAsPair() {
        val agent = createMainAgent(
            runtimePort = NoopRuntimeSideEffectPort,
            sessionPort = NoopSessionSideEffectPort,
        )

        assertNotNull(agent)
    }

    private fun createMainAgent(
        runtimePort: RuntimeSideEffectPort?,
        sessionPort: SessionSideEffectPort?,
    ): MainAgent {
        val sessionManager = SessionManager(dependencies = FakeSessionRepository().toSessionManagerDependencies())
        return MainAgentImpl(
            promptExecutor = getMockExecutor {
                mockLLMAnswer("noop").asDefaultResponse
            },
            sessionManager = sessionManager,
            messageHandler = FakeMessageHandler(),
            eventListener = null,
            logger = {},
            runtimeContext = AgentRuntimeContext(
                agentId = null,
                parentAgentId = null,
                canInteractWithUser = true,
                canCreateSubagents = false,
            ),
            runtimeSideEffectPort = runtimePort,
            sessionSideEffectPort = sessionPort,
        )
    }

    private object NoopRuntimeSideEffectPort : RuntimeSideEffectPort {
        override fun isSafeStopRequested(sessionId: String): Boolean = false

        override fun onSafeStopReached(sessionId: String) = Unit

        override fun onToolCallStarting(sessionId: String, toolName: String, arguments: String) = Unit

        override fun onToolCallCompleted(sessionId: String, toolName: String, result: String) = Unit

        override fun onToolCallFailed(sessionId: String, message: String) = Unit

        override fun log(message: String) = Unit
    }

    private object NoopSessionSideEffectPort : SessionSideEffectPort {
        override suspend fun prepareMessagesForAgent(sessionId: String, agentId: String?): List<Message> = emptyList()

        override suspend fun resolveSystemPrompt(sessionId: String, agentId: String?, fallback: String): String = fallback

        override suspend fun suspendForUserInput(sessionId: String) = Unit

        override suspend fun saveToolExchange(
            sessionId: String,
            toolName: String,
            toolCallId: String,
            arguments: JsonElement,
            result: JsonElement,
            isError: Boolean,
            errorMessage: String?,
            outputList: List<String>,
            awaitForUserInput: Boolean,
            agentId: String?,
        ) = Unit
    }
}
