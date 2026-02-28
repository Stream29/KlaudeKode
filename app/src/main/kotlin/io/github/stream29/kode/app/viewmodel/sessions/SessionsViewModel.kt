package io.github.stream29.kode.app.viewmodel.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.config.api.*
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.session.core.model.SessionSummary
import io.github.stream29.kode.session.core.storage.SessionFilter
import io.github.stream29.kode.session.core.storage.SessionStatusFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

public class SessionsViewModel(
    private val sessionManager: SessionManager,
    private val configManager: ConfigManager,
    private val onSwitchToSession: (String) -> Unit,
    private val onSystemMessage: (String) -> Unit,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SessionsUiState())
    public val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        loadSessionList()
    }

    public fun loadSessionList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val filter = buildSessionFilter()
                val sessions = sessionManager.listSessions(filter)
                _uiState.update { it.copy(sessionSummaries = sessions) }
            } catch (e: Exception) {
                onSystemMessage("Failed to load sessions: ${e.message}")
            }
        }
    }

    private fun buildSessionFilter(): SessionFilter {
        return SessionFilter(
            status = uiState.value.sessionStatusFilter,
            searchQuery = uiState.value.sessionSearchQuery.takeIf { it.isNotBlank() }
        )
    }

    public fun updateSessionSearchQuery(query: String) {
        _uiState.update { it.copy(sessionSearchQuery = query) }
        loadSessionList()
    }

    public fun updateSessionStatusFilter(filter: SessionStatusFilter) {
        _uiState.update { it.copy(sessionStatusFilter = filter) }
        loadSessionList()
    }

    public fun switchToSession(sessionId: String) {
        onSwitchToSession(sessionId)
    }

    public fun deleteSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.deleteSession(sessionId, hardDelete = true)
                loadSessionList()
                onSystemMessage("Session deleted")
            } catch (e: Exception) {
                onSystemMessage("Failed to delete session: ${e.message}")
            }
        }
    }

    public fun forkSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newSession = sessionManager.forkSession(
                    parentSessionId = sessionId,
                    atMessageId = null,
                    newTitle = "Fork of ${sessionId.take(8)}"
                )
                loadSessionList()
                onSwitchToSession(newSession.id)
                onSystemMessage("Session forked: ${newSession.id.take(8)}...")
            } catch (e: Exception) {
                onSystemMessage("Failed to fork session: ${e.message}")
            }
        }
    }

    public fun archiveSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.archiveSession(sessionId)
                loadSessionList()
                onSystemMessage("Session archived")
            } catch (e: Exception) {
                onSystemMessage("Failed to archive session: ${e.message}")
            }
        }
    }

    public fun restoreSession(sessionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.restoreSession(sessionId)
                loadSessionList()
                onSystemMessage("Session restored")
            } catch (e: Exception) {
                onSystemMessage("Failed to restore session: ${e.message}")
            }
        }
    }

    public fun clearAllSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sessions = sessionManager.listSessions(null)
                sessions.forEach { 
                    sessionManager.deleteSession(it.id, hardDelete = true)
                }
                loadSessionList()
                onSystemMessage("All sessions cleared")
            } catch (e: Exception) {
                onSystemMessage("Failed to clear sessions: ${e.message}")
            }
        }
    }

    public fun exportSession(sessionId: String, targetFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sessionManager.exportSession(sessionId, targetFile)
                onSystemMessage("Session exported to ${targetFile.absolutePath}")
            } catch (e: Exception) {
                onSystemMessage("Failed to export session: ${e.message}")
            }
        }
    }

    public fun importSession(sourceFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = sessionManager.importSession(sourceFile, null)
                loadSessionList()
                onSystemMessage("Session imported: ${snapshot.id.take(8)}")
            } catch (e: Exception) {
                onSystemMessage("Failed to import session: ${e.message}")
            }
        }
    }

    public fun importSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dialog = FileDialog(null as Frame?, "Import Session", FileDialog.LOAD)
                dialog.isVisible = true
                val fileName = dialog.file ?: return@launch
                val directory = dialog.directory ?: return@launch
                importSession(File(directory, fileName))
            } catch (e: Exception) {
                onSystemMessage("Failed to choose import file: ${e.message}")
            }
        }
    }
}

public data class SessionsUiState(
    val sessionSummaries: List<SessionSummary> = emptyList(),
    val sessionSearchQuery: String = "",
    val sessionStatusFilter: SessionStatusFilter = SessionStatusFilter.ACTIVE,
)
