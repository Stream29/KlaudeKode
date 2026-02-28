package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.core.hooks.HookManager
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.session.core.storage.FileSessionStorage
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class TodoAutoPersistTest {
    @Test
    fun todoAddScriptAutoPersistsTodoJson() {
        runBlocking {
            val tempDir = Files.createTempDirectory("todo-auto-persist-add-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val sessionManager = SessionManager(repository = storage)
                val session = sessionManager.createConversationSession(
                    title = "todo add auto persist",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )

                runSingleScript(
                    sessionManager = sessionManager,
                    sessionId = session.id,
                    script = """
                        updateTodoList(listOf(
                            io.github.stream29.kode.session.core.model.TodoNode(
                                name = "todo from add script",
                                isCompleted = false,
                                subtasks = emptyList()
                            )
                        ))
                        suspendForUserInput()
                    """.trimIndent(),
                )

                val msgs = sessionManager.getAgentMessages(session.id, "main-${session.id}")
                println("[DEBUG_LOG] messages: ${msgs.joinToString("\n")}")

                val persistedTodos = readPersistedTodos(
                    dataDir = tempDir,
                    sessionId = session.id,
                )

                assertEquals(1, persistedTodos.size)
                val todo = persistedTodos.single()
                assertEquals("todo from add script", todo.name)
                assertEquals(0, todo.subtasks.size)
                assertFalse(todo.isCompleted)
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun todoUpdateScriptAutoPersistsTodoJson() {
        runBlocking {
            val tempDir = Files.createTempDirectory("todo-auto-persist-update-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val sessionManager = SessionManager(repository = storage)
                val session = sessionManager.createConversationSession(
                    title = "todo update auto persist",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )

                runSingleScript(
                    sessionManager = sessionManager,
                    sessionId = session.id,
                    script = """
                        updateTodoList(listOf(
                            io.github.stream29.kode.session.core.model.TodoNode(
                                name = "todo before update",
                                isCompleted = false,
                                subtasks = emptyList()
                            )
                        ))
                        suspendForUserInput()
                    """.trimIndent(),
                )

                val initialTodos = readPersistedTodos(
                    dataDir = tempDir,
                    sessionId = session.id,
                )
                assertEquals(1, initialTodos.size)

                runSingleScript(
                    sessionManager = sessionManager,
                    sessionId = session.id,
                    script = """
                        updateTodoList(listOf(
                            io.github.stream29.kode.session.core.model.TodoNode(
                                name = "todo after update",
                                isCompleted = true,
                                subtasks = emptyList()
                            )
                        ))
                        suspendForUserInput()
                    """.trimIndent(),
                )

                val persistedTodos = readPersistedTodos(
                    dataDir = tempDir,
                    sessionId = session.id,
                )
                val updatedTodo = persistedTodos.single { node -> node.name == "todo after update" }

                assertEquals(1, persistedTodos.size)
                assertEquals("todo after update", updatedTodo.name)
                assertTrue(updatedTodo.isCompleted)
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    private suspend fun runSingleScript(
        sessionManager: SessionManager,
        sessionId: String,
        script: String,
    ) {
        val messageHandler = SafeStopAfterFirstInputMessageHandler()
        val engine = ScriptOnlyAgentEngine(
            promptExecutor = getMockExecutor {
                mockLLMToolCall(
                    tool = ExecuteKotlinScriptTool,
                    args = ExecuteKotlinScriptArgs(script = script),
                    toolCallId = "call-id",
                ) onCondition { true }
            },
            sessionManager = sessionManager,
            sessionBridge = KoogSessionBridge(sessionManager = sessionManager),
            messageHandler = messageHandler,
            hookManager = HookManager.empty(),
            eventListener = null,
            logger = {},
            runtimeContext = AgentRuntimeContext(agentId = "main-$sessionId"),
        )

        val result = withTimeout(timeMillis = 2_000L) {
            engine.run(
                sessionId = sessionId,
                model = TEST_MODEL,
                modelParams = null,
            )
        }

        assertNull(result)
        assertEquals(1, messageHandler.requestInputCount)
    }

    private fun readPersistedTodos(dataDir: Path, sessionId: String): List<TodoNode> {
        val todoFile = resolveTodoFile(
            dataDir = dataDir,
            sessionId = sessionId,
        )
        assertTrue(todoFile.isFile)
        return TODO_JSON.decodeFromString(
            deserializer = ListSerializer(TodoNode.serializer()),
            string = todoFile.readText(),
        )
    }

    private fun resolveTodoFile(dataDir: Path, sessionId: String): File {
        val mainAgentId = "main-$sessionId"
        val encodedAgentId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mainAgentId.toByteArray(Charsets.UTF_8))
        return dataDir.resolve("sessions")
            .resolve(sessionId)
            .resolve("agents")
            .resolve(encodedAgentId)
            .resolve("todo.json")
            .toFile()
    }

    private class SafeStopAfterFirstInputMessageHandler : FakeMessageHandler() {
        private var safeStopRequested: Boolean = false

        override suspend fun requestInput(): String {
            safeStopRequested = true
            return super.requestInput()
        }

        override fun isSafeStopRequested(sessionId: String): Boolean {
            return safeStopRequested
        }
    }

    @Serializable
    private data class ExecuteKotlinScriptArgs(
        val script: String,
    )

    private companion object {
        private object ExecuteKotlinScriptTool : Tool<ExecuteKotlinScriptArgs, String>(
            argsSerializer = serializer<ExecuteKotlinScriptArgs>(),
            resultSerializer = serializer<String>(),
            name = ToolNames.EXECUTE_KOTLIN_SCRIPT,
            description = "Test-only executeKotlinScript tool call",
        ) {
            override suspend fun execute(args: ExecuteKotlinScriptArgs): String {
                return ""
            }
        }

        private val TODO_JSON: Json = Json {
            ignoreUnknownKeys = true
        }

        private val TEST_MODEL: LLModel = LLModel(
            provider = LLMProvider.OpenAI,
            id = "test-model",
            capabilities = listOf(LLMCapability.Tools, LLMCapability.ToolChoice),
            contextLength = 8_192,
            maxOutputTokens = 1_024,
        )
    }
}
