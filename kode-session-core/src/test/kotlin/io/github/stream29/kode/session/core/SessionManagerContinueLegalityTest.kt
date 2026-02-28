package io.github.stream29.kode.session.core

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.session.core.model.AgentScriptStatus
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.model.UserMessage
import io.github.stream29.kode.session.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SessionManagerContinueLegalityTest {
    @Test
    fun prepareConversationContinuationAcceptsEmptyInputWithoutAppendingUserMessage() {
        runBlocking {
            val repository = FakeSessionRepository()
            val sessionManager = createSessionManager(repository = repository)
            val session = createConversationSession(sessionManager = sessionManager, title = "empty continue")
            val before = assertNotNull(sessionManager.getSession(session.id))
            val beforeUserCount = before.messages.count { it is UserMessage }
            val persistCallsBefore = repository.persistSessionCalls

            sessionManager.prepareConversationContinuation(
                sessionId = session.id,
                input = "",
                agentId = null,
            )

            val after = assertNotNull(sessionManager.getSession(session.id))
            assertEquals(before.messages.size, after.messages.size)
            val afterUserCount = after.messages.count { it is UserMessage }
            assertEquals(beforeUserCount, afterUserCount)
            assertEquals(persistCallsBefore, repository.persistSessionCalls)
            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertEquals(SessionRunState.Suspended, runtime.metadata.value.state)
        }
    }

    @Test
    fun prepareConversationContinuationAcceptsNonEmptyInputAndAppendsUserMessage() {
        runBlocking {
            val repository = FakeSessionRepository()
            val sessionManager = createSessionManager(repository = repository)
            val session = createConversationSession(sessionManager = sessionManager, title = "non-empty continue")
            val before = assertNotNull(sessionManager.getSession(session.id))
            val input = "continue with message"
            val persistCallsBefore = repository.persistSessionCalls

            sessionManager.prepareConversationContinuation(
                sessionId = session.id,
                input = input,
                agentId = null,
            )

            val after = assertNotNull(sessionManager.getSession(session.id))
            assertEquals(before.messages.size + 1, after.messages.size)
            val trailingUser = assertIs<UserMessage>(after.messages.last())
            assertEquals(input, trailingUser.content)
            assertEquals(persistCallsBefore + 1, repository.persistSessionCalls)
            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertEquals(SessionRunState.Suspended, runtime.metadata.value.state)
        }
    }

    @Test
    fun prepareConversationContinuationRejectsTrailingPendingScript() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = sessionManager.createConversationSession(
                title = "continue legality",
                systemPrompt = "test",
                preferredModel = null,
                preferredModelId = "test-model",
                workDir = null,
            )

            sessionManager.addAgentScriptMessage(
                sessionId = session.id,
                scriptId = "pending-script-id",
                status = AgentScriptStatus.PENDING_INPUT,
                scriptReturnValue = null,
                scriptStdout = "",
                error = null,
                outputList = emptyList(),
                koogMessages = listOf(
                    Message.Tool.Call(
                        id = "pending-script-id",
                        tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                        content = "{\"script\":\"suspendForUserInput()\"}",
                        metaInfo = ResponseMetaInfo.Empty,
                    )
                ),
                metadata = null,
                agentId = null,
            )

            val throwable = runCatching {
                sessionManager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "",
                    agentId = null,
                )
            }.exceptionOrNull()

            val error = assertNotNull(throwable)
            assertIs<IllegalStateException>(error)
            val message = error.message.orEmpty()
            assertTrue(message.contains("blocks continue; resolve pending-input state first"))
        }
    }

    @Test
    fun stopRunRollsBackTrailingPendingScriptAndAllowsContinueLegalityGate() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "stop convergence")
            appendPendingScript(sessionManager = sessionManager, sessionId = session.id)

            val rolledBack = sessionManager.stopRun(session.id)

            assertTrue(rolledBack)
            assertNull(
                sessionManager.getTrailingPendingScript(
                    sessionId = session.id,
                    agentId = null,
                )
            )
            sessionManager.prepareConversationContinuation(
                sessionId = session.id,
                input = "",
                agentId = null,
            )
            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertEquals(SessionRunState.Suspended, runtime.metadata.value.state)
        }
    }

    @Test
    fun stopRunRollsBackTrailingPendingScriptExactlyOnce() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "stop exactly once")
            appendPendingScript(sessionManager = sessionManager, sessionId = session.id)

            val beforeFirstStop = assertNotNull(sessionManager.getSession(session.id)).messages.size
            val firstRollback = sessionManager.stopRun(session.id)
            val afterFirstStop = assertNotNull(sessionManager.getSession(session.id)).messages.size
            val secondRollback = sessionManager.stopRun(session.id)
            val afterSecondStop = assertNotNull(sessionManager.getSession(session.id)).messages.size

            assertTrue(firstRollback)
            assertEquals(beforeFirstStop - 1, afterFirstStop)
            assertTrue(secondRollback.not())
            assertEquals(afterFirstStop, afterSecondStop)
            assertNull(sessionManager.getTrailingPendingScript(sessionId = session.id, agentId = null))
        }
    }

    @Test
    fun continueAfterStopHasDeterministicLegalOutcomeWithoutHalfState() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "continue after stop")
            appendPendingScript(sessionManager = sessionManager, sessionId = session.id)

            val rolledBack = sessionManager.stopRun(session.id)
            assertTrue(rolledBack)
            assertNull(sessionManager.getTrailingPendingScript(sessionId = session.id, agentId = null))

            val followUp = "continue-after-stop-input"
            sessionManager.prepareConversationContinuation(
                sessionId = session.id,
                input = followUp,
                agentId = null,
            )

            val snapshot = assertNotNull(sessionManager.getSession(session.id))
            val trailingUser = snapshot.messages.lastOrNull()
            assertIs<UserMessage>(trailingUser)
            assertEquals(followUp, trailingUser.content)
            assertNull(sessionManager.getTrailingPendingScript(sessionId = session.id, agentId = null))

            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertEquals(SessionRunState.Suspended, runtime.metadata.value.state)
        }
    }

    @Test
    fun pendingScriptContinueRemainsBlockedUntilStopRunRollbackThenAllowsContinue() {
        runBlocking {
            val sessionManager = createSessionManager()
            val session = createConversationSession(sessionManager = sessionManager, title = "two-phase stop gate")
            appendPendingScript(sessionManager = sessionManager, sessionId = session.id)

            val blockedError = runCatching {
                sessionManager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "",
                    agentId = null,
                )
            }.exceptionOrNull()

            val error = assertNotNull(blockedError)
            assertIs<IllegalStateException>(error)
            assertTrue(error.message.orEmpty().contains("blocks continue; resolve pending-input state first"))
            assertNotNull(sessionManager.getTrailingPendingScript(sessionId = session.id, agentId = null))

            val rolledBack = sessionManager.stopRun(session.id)
            assertTrue(rolledBack)
            assertNull(sessionManager.getTrailingPendingScript(sessionId = session.id, agentId = null))

            sessionManager.prepareConversationContinuation(
                sessionId = session.id,
                input = "",
                agentId = null,
            )

            val runtime = assertNotNull(sessionManager.getSessionState(session.id))
            assertEquals(SessionRunState.Suspended, runtime.metadata.value.state)
        }
    }

    private suspend fun createConversationSession(
        sessionManager: SessionManager,
        title: String,
    ) = sessionManager.createConversationSession(
        title = title,
        systemPrompt = "test",
        preferredModel = null,
        preferredModelId = "test-model",
        workDir = null,
    )

    private fun createSessionManager(repository: FakeSessionRepository = FakeSessionRepository()): SessionManager {
        return SessionManager(
            repository = repository,
        )
    }

    private suspend fun appendPendingScript(
        sessionManager: SessionManager,
        sessionId: String,
    ) {
        sessionManager.addAgentScriptMessage(
            sessionId = sessionId,
            scriptId = "pending-script-id",
            status = AgentScriptStatus.PENDING_INPUT,
            scriptReturnValue = null,
            scriptStdout = "",
            error = null,
            outputList = emptyList(),
            koogMessages = listOf(
                Message.Tool.Call(
                    id = "pending-script-id",
                    tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                    content = "{\"script\":\"suspendForUserInput()\"}",
                    metaInfo = ResponseMetaInfo.Empty,
                )
            ),
            metadata = null,
            agentId = null,
        )
    }
}
