package io.github.stream29.kode.session.core.storage

import java.io.File

internal class RootSessionFileRepository(
    private val dataDir: File,
) {
    val sessionsDirectory: File = File(dataDir, SESSIONS_DIRECTORY_NAME)
    val sessionIndexFile: File = File(dataDir, SESSION_INDEX_FILE_NAME)

    fun session(sessionId: String): SessionFileRepository {
        return SessionFileRepository(
            sessionDirectory = File(sessionsDirectory, sessionId),
        )
    }

    fun resolveSessionIndexFileForRead(): File? {
        return sessionIndexFile.takeIf { file -> file.isFile }
    }
}

internal class SessionFileRepository(
    val sessionDirectory: File,
) {
    val metadataFile: File = File(sessionDirectory, SESSION_METADATA_FILE_NAME)
    val agentsDirectory: File = File(sessionDirectory, AGENTS_DIRECTORY_NAME)
    val mainAgentDirectory: File = File(agentsDirectory, MAIN_AGENT_DIRECTORY_NAME)
    val subAgentsDirectory: File = File(agentsDirectory, SUB_AGENTS_DIRECTORY_NAME)

    fun mainAgent(): AgentFileRepository {
        return AgentFileRepository(
            canonicalAgentDirectory = mainAgentDirectory,
        )
    }

    fun subAgent(encodedSubAgentId: String): AgentFileRepository {
        return AgentFileRepository(
            canonicalAgentDirectory = File(subAgentsDirectory, encodedSubAgentId),
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

        return canonicalAgentDirectories
            .distinctBy { directory -> directory.absolutePath }
    }

    fun resolveMetadataFileForRead(): File? {
        return metadataFile.takeIf { file -> file.isFile }
    }
}

internal class AgentFileRepository(
    val canonicalAgentDirectory: File,
) {
    val metadataFile: File = File(canonicalAgentDirectory, AGENT_METADATA_FILE_NAME)
    val messagesDirectory: File = File(canonicalAgentDirectory, MESSAGES_DIRECTORY_NAME)

    fun resolveMetadataFileForRead(): File? {
        return metadataFile.takeIf { file -> file.isFile }
    }

    fun messageFileForRead(seq: Long): File? {
        val currentFile = File(messagesDirectory, "message_${seq}.json")
        return currentFile.takeIf { file -> file.isFile }
    }

    fun messageFileForWrite(seq: Long): File {
        return File(messagesDirectory, "message_${seq}.json")
    }
}

internal const val SESSION_INDEX_FILE_NAME: String = "session-index.csv"
internal const val SESSIONS_DIRECTORY_NAME: String = "sessions"
internal const val SESSION_METADATA_FILE_NAME: String = "metadata.json"
internal const val AGENTS_DIRECTORY_NAME: String = "agents"
internal const val MAIN_AGENT_DIRECTORY_NAME: String = "mainAgent"
internal const val SUB_AGENTS_DIRECTORY_NAME: String = "subAgents"
internal const val AGENT_METADATA_FILE_NAME: String = "metadata.json"
internal const val MESSAGES_DIRECTORY_NAME: String = "messages"
