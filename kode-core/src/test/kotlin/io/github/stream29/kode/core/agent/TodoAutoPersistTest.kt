package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.github.stream29.kode.core.session.KoogSessionBridge
import io.github.stream29.kode.core.testsupport.FakeMessageHandler
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.agent.model.TodoItem
import io.github.stream29.kode.session.core.storage.FileSessionStorage
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class TodoAutoPersistTest {
    @Test
    fun todoAddScriptAutoPersistsTodoInMetadata() {
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
                        resetTodoItems(listOf(
                            TodoItem(
                                name = "todo from add script",
                                completed = false
                            )
                        ))
                        suspendForUserInput()
                    """.trimIndent(),
                )

                val persistedTodos = readPersistedTodos(
                    dataDir = tempDir,
                    sessionId = session.id,
                )
                assertFalse(
                    resolveCanonicalMainAgentTodoFile(
                        dataDir = tempDir,
                        sessionId = session.id,
                    ).exists()
                )
                assertFalse(
                    resolveLegacyMainAgentTodoFile(
                        dataDir = tempDir,
                        sessionId = session.id,
                    ).exists()
                )

                assertEquals(1, persistedTodos.size)
                val todo = persistedTodos.single()
                assertEquals("todo from add script", todo.name)
                assertEquals(0, todo.subItems.size)
                assertFalse(todo.completed)

                val reloadedSessionManager = reloadSessionManager(dataDir = tempDir)
                val reloadedTodos = reloadedSessionManager.getAgentTodo(session.id, "main-${session.id}")
                assertEquals(persistedTodos, reloadedTodos)
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun todoUpdateScriptAutoPersistsTodoInMetadata() {
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
                        resetTodoItems(listOf(
                            TodoItem(
                                name = "todo before update",
                                completed = false
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
                        editTodoItem("todo before update") {
                            it.copy(
                                name = "todo after update",
                                completed = true
                            )
                        }
                        suspendForUserInput()
                    """.trimIndent(),
                )

                val persistedTodos = readPersistedTodos(
                    dataDir = tempDir,
                    sessionId = session.id,
                )
                assertFalse(
                    resolveCanonicalMainAgentTodoFile(
                        dataDir = tempDir,
                        sessionId = session.id,
                    ).exists()
                )
                assertFalse(
                    resolveLegacyMainAgentTodoFile(
                        dataDir = tempDir,
                        sessionId = session.id,
                    ).exists()
                )
                val updatedTodo = persistedTodos.single { node -> node.name == "todo after update" }

                assertEquals(1, persistedTodos.size)
                assertEquals("todo after update", updatedTodo.name)
                assertTrue(updatedTodo.completed)

                val reloadedSessionManager = reloadSessionManager(dataDir = tempDir)
                val reloadedTodos = reloadedSessionManager.getAgentTodo(session.id, "main-${session.id}")
                assertEquals(persistedTodos, reloadedTodos)
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun todoAddScriptAutoPersistsWhenAgentRuntimeContextHasNoAgentId() {
        runBlocking {
            val tempDir = Files.createTempDirectory("todo-auto-persist-main-fallback-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val sessionManager = SessionManager(repository = storage)
                val session = sessionManager.createConversationSession(
                    title = "todo add fallback",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )

                runSingleScript(
                    sessionManager = sessionManager,
                    sessionId = session.id,
                    script = """
                        resetTodoItems(listOf(
                            TodoItem(
                                name = "todo from fallback runtime context",
                                completed = false
                            )
                        ))
                        suspendForUserInput()
                    """.trimIndent(),
                    runtimeContext = AgentRuntimeContext(),
                )

                val persistedTodos = readPersistedTodos(
                    dataDir = tempDir,
                    sessionId = session.id,
                )

                assertEquals(1, persistedTodos.size)
                assertEquals("todo from fallback runtime context", persistedTodos.single().name)
                assertFalse(persistedTodos.single().completed)

                val reloadedSessionManager = reloadSessionManager(dataDir = tempDir)
                val reloadedTodos = reloadedSessionManager.getAgentTodo(session.id, "main-${session.id}")
                assertEquals(persistedTodos, reloadedTodos)
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    private suspend fun runSingleScript(
        sessionManager: SessionManager,
        sessionId: String,
        script: String,
        runtimeContext: AgentRuntimeContext = AgentRuntimeContext(),
    ) {
        val messageHandler = SafeStopAfterFirstInputMessageHandler()
        val uniqueScript = "// cache-bust:${UUID.randomUUID()}\n$script"
        val engine = ScriptOnlyAgentEngine(
            promptExecutor = getMockExecutor {
                mockLLMToolCall(
                    tool = ExecuteKotlinScriptTool,
                    args = ExecuteKotlinScriptArgs(script = uniqueScript),
                    toolCallId = "call-id",
                ) onCondition { true }
            },
            sessionManager = sessionManager,
            sessionBridge = KoogSessionBridge(sessionManager = sessionManager),
            messageHandler = messageHandler,
            eventListener = null,
            logger = {},
            runtimeContext = runtimeContext,
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

    private fun readPersistedTodos(dataDir: Path, sessionId: String): List<TodoItem> {
        val metadataFile = resolveCanonicalMainAgentMetadataFile(
            dataDir = dataDir,
            sessionId = sessionId,
        )
        assertTrue(metadataFile.isFile)
        val metadata = TODO_JSON.parseToJsonElement(metadataFile.readText()).jsonObject
        val todoElement = metadata["todo"] ?: fail("Missing todo in agent metadata")
        return TODO_JSON.decodeFromJsonElement(
            deserializer = ListSerializer(TodoItem.serializer()),
            element = todoElement,
        )
    }

    private fun resolveCanonicalMainAgentMetadataFile(dataDir: Path, sessionId: String): File {
        return dataDir.resolve("sessions")
            .resolve(sessionId)
            .resolve("agents")
            .resolve("mainAgent")
            .resolve("metadata.json")
            .toFile()
    }

    private fun resolveCanonicalMainAgentTodoFile(dataDir: Path, sessionId: String): File {
        return dataDir.resolve("sessions")
            .resolve(sessionId)
            .resolve("agents")
            .resolve("mainAgent")
            .resolve("todo.json")
            .toFile()
    }

    private fun resolveLegacyMainAgentTodoFile(dataDir: Path, sessionId: String): File {
        val encodedAgentId = encodedMainAgentId(sessionId = sessionId)
        return dataDir.resolve("sessions")
            .resolve(sessionId)
            .resolve("agents")
            .resolve(encodedAgentId)
            .resolve("todo.json")
            .toFile()
    }

    private fun encodedMainAgentId(sessionId: String): String {
        val mainAgentId = "main-$sessionId"
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mainAgentId.toByteArray(Charsets.UTF_8))
    }

    private fun reloadSessionManager(dataDir: Path): SessionManager {
        val storage = FileSessionStorage(dataDir = dataDir.toFile())
        return SessionManager(repository = storage)
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
