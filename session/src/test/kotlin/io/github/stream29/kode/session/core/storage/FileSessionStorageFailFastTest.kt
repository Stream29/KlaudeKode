package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.toSessionManagerDependencies
import io.github.stream29.kode.agent.model.UserMessage
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class FileSessionStorageFailFastTest {
    @Test
    fun obsoleteMarkerFileIsIgnoredAndDoesNotAffectLoading() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-schema-file-ignored-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                val session = manager.createConversationSession(title = "obsolete marker ignored", systemPrompt = "test", workDir = null)
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "hello",
                    agentId = null,
                )

                val schemaFile = tempDir.resolve("session-schema.version").toFile()
                schemaFile.writeText("999")

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                val loaded = assertNotNull(reloaded.getSession(session.id))
                val trailingUser = assertIs<UserMessage>(loaded.messages.last())
                assertEquals("hello", trailingUser.content)
                assertEquals("999", schemaFile.readText().trim())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun corruptedMetadataCsvFailsFast() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-corrupt-metadata-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                manager.createConversationSession(title = "corrupt metadata", systemPrompt = "test", workDir = null)

                val metadataFile = tempDir.resolve("session-index.csv").toFile()
                metadataFile.writeText("\"")

                val error = assertFailsWith<IllegalStateException> {
                    storage.listSessions()
                }
                assertTrue(error.message.orEmpty().contains("Failed to decode session metadata csv"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun corruptedMessageJsonFailsFast() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-corrupt-message-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                val session = manager.createConversationSession(title = "corrupt message", systemPrompt = "test", workDir = null)
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "keep this message",
                    agentId = null,
                )

                val messageFile = tempDir.resolve("sessions")
                    .resolve(session.id)
                    .resolve("agents")
                    .resolve(MAIN_AGENT_DIRECTORY_NAME)
                    .resolve("messages")
                    .resolve("message_0.json")
                    .toFile()
                assertTrue(messageFile.isFile)
                messageFile.writeText("{")

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                val error = assertFailsWith<IllegalStateException> {
                    reloaded.loadSession(session.id)
                }
                assertTrue(error.message.orEmpty().contains("Failed to decode session message"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun legacyMessageTypeFailsFast() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-legacy-message-type-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                val session = manager.createConversationSession(title = "legacy message type", systemPrompt = "test", workDir = null)
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "seed",
                    agentId = null,
                )

                val messageFile = tempDir.resolve("sessions")
                    .resolve(session.id)
                    .resolve("agents")
                    .resolve(MAIN_AGENT_DIRECTORY_NAME)
                    .resolve("messages")
                    .resolve("message_0.json")
                    .toFile()
                assertTrue(messageFile.isFile)
                messageFile.writeText(
                    """
                    {
                      "type": "tool_exchange",
                      "id": "legacy-tool-exchange",
                      "toolName": "executeKotlinScript",
                      "toolCallId": "call-1",
                      "arguments": {"script": "sayToUser(\"hello\")"},
                      "result": "done",
                      "isError": false,
                      "errorMessage": null,
                      "timestamp": "2026-03-06T12:00:00Z"
                    }
                    """.trimIndent()
                )

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                val error = assertFailsWith<IllegalStateException> {
                    reloaded.loadSession(session.id)
                }
                assertTrue(error.message.orEmpty().contains("Failed to decode session message"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun mismatchedAgentIdInMetadataFailsFast() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-agent-id-mismatch-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                val session = manager.createConversationSession(title = "agent id mismatch", systemPrompt = "test", workDir = null)
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "seed",
                    agentId = null,
                )

                val metadataFile = mainAgentMetadataFile(tempDir = tempDir, sessionId = session.id)
                assertTrue(metadataFile.isFile)
                val original = metadataFile.readText()
                val mutated = original.replaceFirst(
                    oldValue = "\"agentId\": \"main-${session.id}\"",
                    newValue = "\"agentId\": \"main-${session.id}-tampered\"",
                )
                assertNotEquals(original, mutated)
                metadataFile.writeText(mutated)

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                val error = assertFailsWith<IllegalStateException> {
                    reloaded.loadSession(session.id)
                }
                assertTrue(error.message.orEmpty().contains("Canonical agent metadata required"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun mismatchedAgentKindInMetadataFailsFast() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-agent-kind-mismatch-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                val session = manager.createConversationSession(title = "agent kind mismatch", systemPrompt = "test", workDir = null)
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "seed",
                    agentId = null,
                )

                val metadataFile = mainAgentMetadataFile(tempDir = tempDir, sessionId = session.id)
                assertTrue(metadataFile.isFile)
                val original = metadataFile.readText()
                val mutated = original.replaceFirst(
                    oldValue = "\"kind\": \"MAIN\"",
                    newValue = "\"kind\": \"SUBAGENT\"",
                )
                assertNotEquals(original, mutated)
                metadataFile.writeText(mutated)

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                val error = assertFailsWith<IllegalStateException> {
                    reloaded.loadSession(session.id)
                }
                assertTrue(error.message.orEmpty().contains("Canonical agent metadata required"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun missingCanonicalTodoFieldFailsFast() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-missing-canonical-todo-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                val session = manager.createConversationSession(title = "missing canonical todo", systemPrompt = "test", workDir = null)
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "seed",
                    agentId = null,
                )

                val metadataFile = mainAgentMetadataFile(tempDir = tempDir, sessionId = session.id)
                assertTrue(metadataFile.isFile)
                val original = metadataFile.readText()
                val mutated = original.replace(
                    oldValue = "\"todo\": []",
                    newValue = "\"todoLegacy\": []",
                )
                assertNotEquals(original, mutated)
                metadataFile.writeText(mutated)

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                val error = assertFailsWith<IllegalStateException> {
                    reloaded.loadSession(session.id)
                }
                assertTrue(error.message.orEmpty().contains("Failed to decode agent meta file"))
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun normalPersistLoadKeepsMessageConsistency() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-normal-load-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(dependencies = storage.toSessionManagerDependencies())
                val session = manager.createConversationSession(title = "normal load", systemPrompt = "test", workDir = null)
                val input = "persist and load user input"
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = input,
                    agentId = null,
                )

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
                val loaded = assertNotNull(reloaded.getSession(session.id))
                assertEquals(session.id, loaded.id)
                val trailingUser = assertIs<UserMessage>(loaded.messages.last())
                assertEquals(input, trailingUser.content)
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    private fun mainAgentMetadataFile(tempDir: Path, sessionId: String): File {
        return tempDir.resolve("sessions")
            .resolve(sessionId)
            .resolve("agents")
            .resolve(MAIN_AGENT_DIRECTORY_NAME)
            .resolve("metadata.json")
            .toFile()
    }
}
