package io.github.stream29.kode.core.agent

import ai.koog.agents.testing.tools.getMockExecutor
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.core.testsupport.FakeSessionRepository
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.tools.scripting.KotlinScriptResult
import io.github.stream29.kode.tools.scripting.evalInThreadCancellable
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SubAgentScriptContextTest {
    @Test
    fun subAgentContextDoesNotExposeUserCommunicationApi() {
        val subContext = SubAgentScriptContext()
        val publicMethodNames = SubAgentScriptContext::class.java.methods
            .map { method -> method.name }
            .toSet()

        assertFalse(publicMethodNames.contains("sayToUser"))
        assertFalse(publicMethodNames.contains("suspendForUserInput"))
        assertFalse(subContext.systemPromptInjection.contains("### `sayToUser(text: String)`"))
        assertFalse(subContext.systemPromptInjection.contains("### `suspendForUserInput()`"))
        assertFalse(subContext.systemPromptInjection.contains("sayToUser"))
        assertFalse(subContext.systemPromptInjection.contains("await_user_input"))
        assertFalse(subContext.systemPromptInjection.contains("createSubAgent"))
        assertFalse(subContext.systemPromptInjection.contains("spawn_subagent"))
        assertContains(subContext.systemPromptInjection, "Direct user communication is unavailable in this role.")
    }

    @Test
    fun subAgentContextRejectsSayToUserCallAtScriptCompileBoundary() {
        runBlocking {
            val result = SubAgentScriptContext().evalInThreadCancellable(
                script = """
                    sayToUser("forbidden")
                    "done"
                """.trimIndent(),
            )

            val failure = assertIs<KotlinScriptResult.Failure>(result)
            assertContains(failure.message, "Unresolved reference 'sayToUser'")
        }
    }

    @Test
    fun promptInjectionAggregationOrderIsStableAcrossRoles() {
        val mainInjection = MainAgentScriptContext.DEFAULT_SYSTEM_PROMPT_INJECTION
        val subInjection = SubAgentScriptContext.DEFAULT_SYSTEM_PROMPT_INJECTION
        val customMainContext = MainAgentScriptContext(
            userCommunicationScriptContext = object : UserCommunicationScriptContext by UserCommunicationScriptContextImpl() {
                override val defaultImports: List<String> = listOf("demo.A", "demo.B")
                override val systemPromptInjection: String = "### User Module"
            },
            todoListScriptContext = object : TodoListScriptContext by TodoListScriptContextImpl() {
                override val defaultImports: List<String> = listOf("demo.B", "demo.C")
                override val systemPromptInjection: String = "### Todo Module"
            },
        )
        val customSubContext = SubAgentScriptContext(
            todoListScriptContext = object : TodoListScriptContext by TodoListScriptContextImpl() {
                override val defaultImports: List<String> = listOf("demo.Todo")
                override val systemPromptInjection: String = "### Todo Module"
            },
        )

        val mainUserSectionIndex = mainInjection.indexOf("### `sayToUser(text: String)`")
        val mainTodoSectionIndex = mainInjection.indexOf("### Todo List API")

        assertTrue(mainUserSectionIndex >= 0)
        assertTrue(mainTodoSectionIndex >= 0)
        assertTrue(mainUserSectionIndex < mainTodoSectionIndex)

        assertFalse(subInjection.contains("### `sayToUser(text: String)`"))
        assertFalse(subInjection.contains("### `suspendForUserInput()`"))
        assertTrue(subInjection.contains("### Todo List API"))

        assertEquals(
            expected = listOf("demo.A", "demo.B", "demo.C"),
            actual = customMainContext.defaultImports,
        )
        assertEquals(
            expected = listOf("demo.Todo"),
            actual = customSubContext.defaultImports,
        )
        assertTrue(
            customMainContext.systemPromptInjection.indexOf("### User Module") <
                customMainContext.systemPromptInjection.indexOf("### Todo Module"),
        )
    }

    @Test
    fun subAgentRequiresRoleIsolatedRuntimeFlags() {
        val sessionManager = SessionManager(repository = FakeSessionRepository())
        val sessionBridge = KoogSessionBridge(sessionManager = sessionManager)

        val interactionError = assertFailsWith<IllegalStateException> {
            SubAgent(
                promptExecutor = getMockExecutor {
                    mockLLMAnswer("noop").asDefaultResponse
                },
                sessionManager = sessionManager,
                sessionBridge = sessionBridge,
                messageHandler = FakeMessageHandler(),
                hookManager = HookManager.empty(),
                eventListener = null,
                logger = {},
                runtimeContext = AgentRuntimeContext(
                    agentId = "sub-agent",
                    canInteractWithUser = true,
                    canCreateSubagents = false,
                ),
            )
        }
        assertContains(interactionError.message.orEmpty(), "disable direct user interaction")

        val subagentError = assertFailsWith<IllegalStateException> {
            SubAgent(
                promptExecutor = getMockExecutor {
                    mockLLMAnswer("noop").asDefaultResponse
                },
                sessionManager = sessionManager,
                sessionBridge = sessionBridge,
                messageHandler = FakeMessageHandler(),
                hookManager = HookManager.empty(),
                eventListener = null,
                logger = {},
                runtimeContext = AgentRuntimeContext(
                    agentId = "sub-agent",
                    canInteractWithUser = false,
                    canCreateSubagents = true,
                ),
            )
        }
        assertContains(subagentError.message.orEmpty(), "disable subagent creation")
    }
}
