package io.github.stream29.kode.session.core.storage

import app.softwork.serialization.csv.CSVFormat
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.SessionMetadataCsvRow
import io.github.stream29.kode.session.core.model.SessionRunState
import io.github.stream29.kode.session.core.model.SessionStatus
import io.github.stream29.kode.session.core.model.TodoNode
import io.github.stream29.kode.session.core.model.UserMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class PersistenceMigrationCompatibilityTest {
    @Test
    fun `migrates N-1 schema sample without destructive reset`() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-migrate-n-1-test")
            try {
                val sessionId = createSeedSession(
                    tempDir = tempDir,
                    title = "migration N-1",
                    input = "n-1 sample user input",
                )
                val schemaFile = tempDir.resolve("session-schema.version").toFile()
                schemaFile.writeText("5")

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                assertSessionLoaded(
                    storage = reloaded,
                    sessionId = sessionId,
                    expectedLastUserInput = "n-1 sample user input",
                )
                assertEquals("6", schemaFile.readText().trim())
                assertTrue(tempDir.resolve("sessions").toFile().listFiles().orEmpty().isNotEmpty())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `migrates N-2 schema sample without destructive reset`() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-migrate-n-2-test")
            try {
                val sessionId = createSeedSession(
                    tempDir = tempDir,
                    title = "migration N-2",
                    input = "n-2 sample user input",
                )

                val schemaFile = tempDir.resolve("session-schema.version").toFile()
                schemaFile.delete()

                val sessionIndexFile = tempDir.resolve("session-index.csv").toFile()
                val metadataFile = tempDir.resolve("session-meta.csv").toFile()
                val currentRows = CSVFormat.decodeFromString(
                    deserializer = ListSerializer(SessionMetadataCsvRow.serializer()),
                    string = sessionIndexFile.readText().trim(),
                )
                val legacyRows = currentRows.map { row ->
                    LegacyV4MetadataCsvRow(
                        id = row.id,
                        title = row.title,
                        createdAtIso = row.createdAtIso,
                        updatedAtIso = row.updatedAtIso,
                        state = row.state,
                        status = row.status,
                        parentSessionId = row.parentSessionId,
                        forkedFromMessageId = row.forkedFromMessageId,
                        version = row.version,
                        tags = row.tags,
                        childSessionIds = row.childSessionIds,
                    )
                }
                metadataFile.writeText(
                    CSVFormat.encodeToString(
                        serializer = ListSerializer(LegacyV4MetadataCsvRow.serializer()),
                        value = legacyRows,
                    )
                )
                sessionIndexFile.delete()
                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                assertSessionLoaded(
                    storage = reloaded,
                    sessionId = sessionId,
                    expectedLastUserInput = "n-2 sample user input",
                )

                val migratedRows = CSVFormat.decodeFromString(
                    deserializer = ListSerializer(SessionMetadataCsvRow.serializer()),
                    string = sessionIndexFile.readText().trim(),
                )
                assertEquals(currentRows.map { row -> row.id }, migratedRows.map { row -> row.id })
                assertEquals("6", schemaFile.readText().trim())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `reads legacy layout and rewrites to current layout on persist`() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-read-legacy-write-current-test")
            try {
                val sessionId = createSeedSession(
                    tempDir = tempDir,
                    title = "legacy read current write",
                    input = "legacy user input",
                )
                rewriteToLegacyLayout(
                    tempDir = tempDir,
                    sessionId = sessionId,
                )
                writeLegacyMainAgentTodo(
                    tempDir = tempDir,
                    sessionId = sessionId,
                    todos = listOf(
                        TodoNode(
                            name = "legacy todo item",
                            isCompleted = false,
                        )
                    ),
                )

                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                assertSessionLoaded(
                    storage = storage,
                    sessionId = sessionId,
                    expectedLastUserInput = "legacy user input",
                )
                val loadedLegacyTodo = assertNotNull(
                    storage.readAgentTodo(
                        sessionId = sessionId,
                        agentId = mainAgentId(sessionId = sessionId),
                    )
                )
                assertEquals("legacy todo item", loadedLegacyTodo.single().name)

                val manager = SessionManager(repository = storage)
                manager.prepareConversationContinuation(
                    sessionId = sessionId,
                    input = "rewrite to current layout",
                    agentId = null,
                )

                val sessionDir = tempDir.resolve("sessions").resolve(sessionId)
                val mainAgentDir = sessionDir
                    .resolve("agents")
                    .resolve(MAIN_AGENT_DIRECTORY_NAME)
                assertTrue(tempDir.resolve("session-index.csv").toFile().isFile)
                assertTrue(sessionDir.resolve("metadata.json").toFile().isFile)
                assertTrue(mainAgentDir.resolve("metadata.json").toFile().isFile)
                assertTrue(mainAgentDir.resolve("messages").resolve("message_1.json").toFile().isFile)
                assertFalse(mainAgentDir.resolve("todo.json").toFile().exists())
                val canonicalMetadata = mainAgentDir.resolve("metadata.json").toFile().readText()
                assertTrue(canonicalMetadata.contains("\"todoStoredInMetadata\": true"))
                assertTrue(canonicalMetadata.contains("legacy todo item"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `uses metadata todo as canonical even when legacy todo file exists`() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-todo-canonical-test")
            try {
                val sessionId = createSeedSession(
                    tempDir = tempDir,
                    title = "todo canonical",
                    input = "seed",
                )
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val agentId = mainAgentId(sessionId = sessionId)

                storage.writeAgentTodo(
                    sessionId = sessionId,
                    agentId = agentId,
                    todos = emptyList(),
                )
                writeLegacyMainAgentTodo(
                    tempDir = tempDir,
                    sessionId = sessionId,
                    todos = listOf(
                        TodoNode(
                            name = "stale legacy todo",
                            isCompleted = false,
                        )
                    ),
                )

                val loaded = assertNotNull(
                    storage.readAgentTodo(
                        sessionId = sessionId,
                        agentId = agentId,
                    )
                )
                assertTrue(loaded.isEmpty())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    private suspend fun createSeedSession(tempDir: Path, title: String, input: String): String {
        val storage = FileSessionStorage(dataDir = tempDir.toFile())
        val manager = SessionManager(repository = storage)
        val session = manager.createConversationSession(
            title = title,
            systemPrompt = "test",
            preferredModel = null,
            preferredModelId = "test-model",
            workDir = null,
        )
        manager.prepareConversationContinuation(
            sessionId = session.id,
            input = input,
            agentId = null,
        )
        return session.id
    }

    private fun writeLegacyMainAgentTodo(tempDir: Path, sessionId: String, todos: List<TodoNode>) {
        val legacyTodoFile = tempDir.resolve("sessions")
            .resolve(sessionId)
            .resolve("agents")
            .resolve(encodedMainAgentId(sessionId))
            .resolve("todo.json")
            .toFile()
        legacyTodoFile.parentFile?.mkdirs()
        val serialized = todos.joinToString(prefix = "[", postfix = "]") { todo ->
            "{\"name\":\"${todo.name}\",\"completed\":${todo.isCompleted},\"subItems\":[]}"
        }
        legacyTodoFile.writeText(serialized)
    }

    private fun rewriteToLegacyLayout(tempDir: Path, sessionId: String) {
        moveFileToLegacyName(
            currentPath = tempDir.resolve("session-index.csv"),
            legacyPath = tempDir.resolve("session-meta.csv"),
        )

        val sessionDir = tempDir.resolve("sessions").resolve(sessionId)
        moveFileToLegacyName(
            currentPath = sessionDir.resolve("metadata.json"),
            legacyPath = sessionDir.resolve("meta.json"),
        )

        val canonicalMainAgentDir = sessionDir
            .resolve("agents")
            .resolve(MAIN_AGENT_DIRECTORY_NAME)
        val legacyMainAgentDir = sessionDir
            .resolve("agents")
            .resolve(encodedMainAgentId(sessionId))
        moveFileToLegacyName(
            currentPath = canonicalMainAgentDir.resolve("metadata.json"),
            legacyPath = legacyMainAgentDir.resolve("meta.json"),
        )

        val canonicalMessagesDir = canonicalMainAgentDir.resolve("messages").toFile()
        val legacyMessagesDir = legacyMainAgentDir.resolve("messages").toFile()
        legacyMessagesDir.mkdirs()
        canonicalMessagesDir.listFiles()
            ?.filter { file -> file.isFile && file.name.startsWith("message_") }
            ?.forEach { file ->
                val legacyName = file.name.removePrefix("message_")
                val legacyFile = File(legacyMessagesDir, legacyName)
                file.copyTo(target = legacyFile, overwrite = true)
                file.delete()
            }
        canonicalMainAgentDir.toFile().deleteRecursively()
    }

    private fun moveFileToLegacyName(currentPath: Path, legacyPath: Path) {
        val currentFile = currentPath.toFile()
        if (!currentFile.isFile) {
            return
        }
        val legacyFile = legacyPath.toFile()
        legacyFile.parentFile?.mkdirs()
        currentFile.copyTo(target = legacyFile, overwrite = true)
        currentFile.delete()
    }

    private fun encodedMainAgentId(sessionId: String): String {
        val mainAgentId = mainAgentId(sessionId = sessionId)
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mainAgentId.toByteArray(Charsets.UTF_8))
    }

    private fun mainAgentId(sessionId: String): String {
        return "main-$sessionId"
    }

    private suspend fun assertSessionLoaded(
        storage: FileSessionStorage,
        sessionId: String,
        expectedLastUserInput: String,
    ) {
        val snapshot = assertNotNull(storage.getSession(sessionId))
        assertEquals(sessionId, snapshot.id)
        val trailingUserMessage = assertIs<UserMessage>(snapshot.messages.last())
        assertEquals(expectedLastUserInput, trailingUserMessage.content)
    }

    @Serializable
    private data class LegacyV4MetadataCsvRow(
        val id: String,
        val title: String,
        val createdAtIso: String,
        val updatedAtIso: String,
        val state: SessionRunState,
        val status: SessionStatus,
        val parentSessionId: String,
        val forkedFromMessageId: String,
        val version: Long,
        val tags: String,
        val childSessionIds: String,
    )
}
