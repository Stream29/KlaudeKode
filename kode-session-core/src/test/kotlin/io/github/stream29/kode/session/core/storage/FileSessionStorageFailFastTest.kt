package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.UserMessage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class FileSessionStorageFailFastTest {
    @Test
    fun unsupportedSchemaMismatchDoesNotResetByDefault() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-schema-reset-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(repository = storage)
                val session = manager.createConversationSession(
                    title = "schema reset",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "hello",
                    agentId = null,
                )

                val sessionsDir = tempDir.resolve("sessions")
                assertTrue(sessionsDir.toFile().isDirectory)
                assertTrue(sessionsDir.toFile().listFiles().orEmpty().isNotEmpty())
                val metadataFile = tempDir.resolve("session-index.csv").toFile()
                val metadataBefore = metadataFile.readText()

                val schemaFile = tempDir.resolve("session-schema.version").toFile()
                schemaFile.writeText("999")

                val error = assertFailsWith<IllegalStateException> {
                    FileSessionStorage(dataDir = tempDir.toFile())
                }
                assertTrue(error.message.orEmpty().contains("Unsupported session storage schema version"))
                assertEquals("999", schemaFile.readText().trim())
                assertEquals(metadataBefore, metadataFile.readText())
                assertTrue(sessionsDir.toFile().isDirectory)
                assertTrue(sessionsDir.toFile().listFiles().orEmpty().isNotEmpty())
            } finally {
                tempDir.toFile().deleteRecursively()
            }
        }
    }

    @Test
    fun schemaMismatchResetsPersistedStorageOnlyWhenExplicitlyAllowed() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-schema-reset-allowed-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(repository = storage)
                val session = manager.createConversationSession(
                    title = "schema reset allowed",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )
                manager.prepareConversationContinuation(
                    sessionId = session.id,
                    input = "hello",
                    agentId = null,
                )

                val sessionsDir = tempDir.resolve("sessions")
                assertTrue(sessionsDir.toFile().isDirectory)
                assertTrue(sessionsDir.toFile().listFiles().orEmpty().isNotEmpty())

                val schemaFile = tempDir.resolve("session-schema.version").toFile()
                schemaFile.writeText("999")

                val reloaded = FileSessionStorage(
                    dataDir = tempDir.toFile(),
                    allowDestructiveResetOnSchemaMismatch = true,
                )
                assertTrue(reloaded.listSessions().isEmpty())
                assertTrue(sessionsDir.toFile().isDirectory)
                assertTrue(sessionsDir.toFile().listFiles().orEmpty().isEmpty())
                assertEquals("6", schemaFile.readText().trim())
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
                val manager = SessionManager(repository = storage)
                manager.createConversationSession(
                    title = "corrupt metadata",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )

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
                val manager = SessionManager(repository = storage)
                val session = manager.createConversationSession(
                    title = "corrupt message",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )
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
    fun normalPersistLoadKeepsMessageConsistency() {
        runBlocking {
            val tempDir = Files.createTempDirectory("file-session-storage-normal-load-test")
            try {
                val storage = FileSessionStorage(dataDir = tempDir.toFile())
                val manager = SessionManager(repository = storage)
                val session = manager.createConversationSession(
                    title = "normal load",
                    systemPrompt = "test",
                    preferredModel = null,
                    preferredModelId = "test-model",
                    workDir = null,
                )
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
}
