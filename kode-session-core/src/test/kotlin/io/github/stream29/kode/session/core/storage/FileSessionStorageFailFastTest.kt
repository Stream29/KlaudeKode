package io.github.stream29.kode.session.core.storage

import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.UserMessage
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileSessionStorageFailFastTest {
    @Test
    fun schemaMismatchResetsPersistedStorage() {
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

                val schemaFile = tempDir.resolve("session-schema.version").toFile()
                schemaFile.writeText("5")

                val reloaded = FileSessionStorage(dataDir = tempDir.toFile())
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

                val metadataFile = tempDir.resolve("session-meta.csv").toFile()
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

                val mainAgentId = "main-${session.id}"
                val encodedAgentId = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mainAgentId.toByteArray(Charsets.UTF_8))
                val messageFile = tempDir.resolve("sessions")
                    .resolve(session.id)
                    .resolve("agents")
                    .resolve(encodedAgentId)
                    .resolve("messages")
                    .resolve("0.json")
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
