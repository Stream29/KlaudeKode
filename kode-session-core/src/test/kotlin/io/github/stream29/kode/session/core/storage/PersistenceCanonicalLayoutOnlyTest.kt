package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.toSessionManagerDependencies
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceCanonicalLayoutOnlyTest {
    @Test
    fun `legacy layout is not loaded without canonical index`() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-canonical-only-layout-test")
            try {
                val sessionId = createSeedSession(
                    tempDir = tempDir,
                    title = "canonical only",
                    input = "seed",
                )
                val sessionDir = tempDir.resolve("sessions").resolve(sessionId)
                moveFileToLegacyName(
                    currentPath = tempDir.resolve("session-index.csv"),
                    legacyPath = tempDir.resolve("session-meta.csv"),
                )
                moveFileToLegacyName(
                    currentPath = sessionDir.resolve("metadata.json"),
                    legacyPath = sessionDir.resolve("meta.json"),
                )

                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                assertTrue(storage.listSessions().isEmpty())
                assertNull(storage.getSession(sessionId))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun `legacy todo file is ignored in canonical mode`() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-canonical-only-todo-test")
            try {
                val sessionId = createSeedSession(
                    tempDir = tempDir,
                    title = "canonical todo",
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
                )

                val loaded = storage.readAgentTodo(
                    sessionId = sessionId,
                    agentId = agentId,
                )
                assertTrue(loaded.isEmpty())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    private suspend fun createSeedSession(tempDir: Path, title: String, input: String): String {
        val storage = FileSessionStorage(dataDir = tempDir.toFile())
        val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
        val session = manager.createConversationSession(title = title, systemPrompt = "test", workDir = null)
        manager.prepareConversationContinuation(
            sessionId = session.id,
            input = input,
            agentId = null,
        )
        return session.id
    }

    private fun writeLegacyMainAgentTodo(tempDir: Path, sessionId: String) {
        val legacyTodoFile = tempDir.resolve("sessions")
            .resolve(sessionId)
            .resolve("agents")
            .resolve(encodedMainAgentId(sessionId))
            .resolve("todo.json")
            .toFile()
        legacyTodoFile.parentFile?.mkdirs()
        legacyTodoFile.writeText(
            """
            [
              {
                "name": "stale legacy todo",
                "isCompleted": true,
                "subtasks": []
              }
            ]
            """.trimIndent()
        )
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
}
