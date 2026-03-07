package io.github.stream29.kode.session.core.storage

import java.io.File

internal class RootSessionFileRepository(
    private val dataDir: File,
) {
    val sessionsDirectory: File = File(dataDir, SESSIONS_DIRECTORY_NAME)
    val sessionIndexFile: File = File(dataDir, SESSION_INDEX_FILE_NAME)
    val legacySessionIndexFile: File = File(dataDir, LEGACY_SESSION_INDEX_FILE_NAME)
    val schemaVersionFile: File = File(dataDir, SESSION_SCHEMA_VERSION_FILE_NAME)

    fun session(sessionId: String): SessionFileRepository {
        return SessionFileRepository(
            sessionDirectory = File(sessionsDirectory, sessionId),
        )
    }

    fun resolveSessionIndexFileForRead(): File? {
        if (sessionIndexFile.isFile) {
            return sessionIndexFile
        }
        if (legacySessionIndexFile.isFile) {
            return legacySessionIndexFile
        }
        return null
    }

    fun allSessionIndexFiles(): List<File> {
        return listOf(sessionIndexFile, legacySessionIndexFile)
    }
}

internal class SessionFileRepository(
    val sessionDirectory: File,
) {
    val metadataFile: File = File(sessionDirectory, SESSION_METADATA_FILE_NAME)
    val legacyMetadataFile: File = File(sessionDirectory, LEGACY_SESSION_METADATA_FILE_NAME)
    val agentsDirectory: File = File(sessionDirectory, AGENTS_DIRECTORY_NAME)
    val mainAgentDirectory: File = File(agentsDirectory, MAIN_AGENT_DIRECTORY_NAME)
    val subAgentsDirectory: File = File(agentsDirectory, SUB_AGENTS_DIRECTORY_NAME)

    fun mainAgent(legacyEncodedMainAgentId: String): AgentFileRepository {
        return AgentFileRepository(
            canonicalAgentDirectory = mainAgentDirectory,
            legacyAgentDirectories = listOf(File(agentsDirectory, legacyEncodedMainAgentId)),
        )
    }

    fun subAgent(encodedSubAgentId: String): AgentFileRepository {
        return AgentFileRepository(
            canonicalAgentDirectory = File(subAgentsDirectory, encodedSubAgentId),
            legacyAgentDirectories = listOf(File(agentsDirectory, encodedSubAgentId)),
        )
    }

    fun listAgentDirectoriesForRead(): List<File> {
        val canonicalAgentDirectories = mutableListOf<File>()
        if (mainAgentDirectory.isDirectory) {
            canonicalAgentDirectories += mainAgentDirectory
        }
        if (subAgentsDirectory.isDirectory) {
            canonicalAgentDirectories += subAgentsDirectory.listFiles()
                ?.filter { file -> file.isDirectory }
                .orEmpty()
        }

        val legacyAgentDirectories = agentsDirectory.listFiles()
            ?.filter { file ->
                file.isDirectory &&
                    file.name != MAIN_AGENT_DIRECTORY_NAME &&
                    file.name != SUB_AGENTS_DIRECTORY_NAME
            }
            .orEmpty()

        return (canonicalAgentDirectories + legacyAgentDirectories)
            .distinctBy { directory -> directory.absolutePath }
    }

    fun resolveMetadataFileForRead(): File? {
        if (metadataFile.isFile) {
            return metadataFile
        }
        if (legacyMetadataFile.isFile) {
            return legacyMetadataFile
        }
        return null
    }
}

internal class AgentFileRepository(
    val canonicalAgentDirectory: File,
    private val legacyAgentDirectories: List<File>,
) {
    val metadataFile: File = File(canonicalAgentDirectory, AGENT_METADATA_FILE_NAME)
    val legacyMetadataFile: File = File(canonicalAgentDirectory, LEGACY_AGENT_METADATA_FILE_NAME)
    val todoFile: File = File(canonicalAgentDirectory, AGENT_TODO_FILE_NAME)
    val messagesDirectory: File = File(canonicalAgentDirectory, MESSAGES_DIRECTORY_NAME)

    private val readAgentDirectories: List<File> = buildList {
        add(canonicalAgentDirectory)
        addAll(legacyAgentDirectories)
    }.distinctBy { directory -> directory.absolutePath }

    fun resolveMetadataFileForRead(): File? {
        readAgentDirectories.forEach { directory ->
            val currentMetadata = File(directory, AGENT_METADATA_FILE_NAME)
            if (currentMetadata.isFile) {
                return currentMetadata
            }
            val legacyMetadata = File(directory, LEGACY_AGENT_METADATA_FILE_NAME)
            if (legacyMetadata.isFile) {
                return legacyMetadata
            }
        }
        return null
    }

    fun resolveTodoFileForRead(): File? {
        readAgentDirectories.forEach { directory ->
            val todo = File(directory, AGENT_TODO_FILE_NAME)
            if (todo.isFile) {
                return todo
            }
        }
        return null
    }

    fun messageFileForRead(seq: Long): File? {
        readAgentDirectories.forEach { directory ->
            val messagesDir = File(directory, MESSAGES_DIRECTORY_NAME)
            val currentFile = File(messagesDir, "message_${seq}.json")
            if (currentFile.isFile) {
                return currentFile
            }
            val legacyFile = File(messagesDir, "${seq}.json")
            if (legacyFile.isFile) {
                return legacyFile
            }
        }
        return null
    }

    fun messageFileForWrite(seq: Long): File {
        return File(messagesDirectory, "message_${seq}.json")
    }

    fun legacyMessageFile(seq: Long): File {
        return File(messagesDirectory, "${seq}.json")
    }
}

internal const val SESSION_SCHEMA_VERSION_FILE_NAME: String = "session-schema.version"
internal const val SESSION_INDEX_FILE_NAME: String = "session-index.csv"
internal const val LEGACY_SESSION_INDEX_FILE_NAME: String = "session-meta.csv"
internal const val SESSIONS_DIRECTORY_NAME: String = "sessions"
internal const val SESSION_METADATA_FILE_NAME: String = "metadata.json"
internal const val LEGACY_SESSION_METADATA_FILE_NAME: String = "meta.json"
internal const val AGENTS_DIRECTORY_NAME: String = "agents"
internal const val MAIN_AGENT_DIRECTORY_NAME: String = "mainAgent"
internal const val SUB_AGENTS_DIRECTORY_NAME: String = "subAgents"
internal const val AGENT_METADATA_FILE_NAME: String = "metadata.json"
internal const val LEGACY_AGENT_METADATA_FILE_NAME: String = "meta.json"
internal const val AGENT_TODO_FILE_NAME: String = "todo.json"
internal const val MESSAGES_DIRECTORY_NAME: String = "messages"
