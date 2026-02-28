package io.github.stream29.kode.app.viewmodel.mcp

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.stream29.kode.config.api.*
import io.github.stream29.kode.config.core.ConfigManager
import io.github.stream29.kode.ui.bridge.mcp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File

public class McpViewModel(
    private val configManager: ConfigManager,
    private val onSystemMessage: (String) -> Unit,
    private val onNotifyConfigChanged: () -> Unit,
    private val workingDirProvider: () -> File,
) : ViewModel() {

    private val _uiState = MutableStateFlow(McpUiState())
    public val uiState: StateFlow<McpUiState> = _uiState.asStateFlow()

    init {
        loadMcpSettings()
    }

    private fun loadMcpSettings() {
        viewModelScope.launch {
            val config = configManager.load()
            _uiState.update { it.copy(
                mcpServers = config.mcp.servers,
                mcpToolTimeoutMs = config.mcp.client.toolCallTimeoutMs,
                mcpHealthResults = config.mcp.servers.keys.associateWith { 
                    McpHealthResult(McpHealthStatus.Unknown, "") 
                }
            ) }
        }
    }

    public fun updateMcpToolTimeoutMs(timeoutMs: Int) {
        _uiState.update { it.copy(mcpToolTimeoutMs = timeoutMs) }
        saveMcpSettings()
    }

    public fun addMcpServer(name: String, config: McpServerConfig) {
        _uiState.update { current ->
            current.copy(
                mcpServers = current.mcpServers + (name to config),
                mcpHealthResults = current.mcpHealthResults + (name to McpHealthResult(McpHealthStatus.Unknown, "")),
                mcpTestResults = current.mcpTestResults - name,
                mcpTestsInFlight = current.mcpTestsInFlight - name
            )
        }
        saveMcpSettings()
    }

    public fun removeMcpServer(name: String) {
        _uiState.update { current ->
            current.copy(
                mcpServers = current.mcpServers - name,
                mcpHealthResults = current.mcpHealthResults - name,
                mcpTestResults = current.mcpTestResults - name,
                mcpTestsInFlight = current.mcpTestsInFlight - name
            )
        }
        saveMcpSettings()
    }

    public fun clearMcpTestResult(name: String) {
        _uiState.update { it.copy(mcpTestResults = it.mcpTestResults - name) }
    }

    public fun testMcpServer(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = uiState.value.mcpServers[name] ?: return@launch
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(
                    mcpTestsInFlight = it.mcpTestsInFlight + name,
                    mcpTestResults = it.mcpTestResults - name,
                    mcpHealthResults = it.mcpHealthResults + (name to McpHealthResult(McpHealthStatus.Checking, "Checking..."))
                ) }
            }
            val result = try {
                runMcpTest(name = name, server = server)
            } catch (e: Exception) {
                buildMcpTestError("MCP test failed (${name}): ${e.message}")
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(
                    mcpTestsInFlight = it.mcpTestsInFlight - name,
                    mcpTestResults = it.mcpTestResults + (name to result),
                    mcpHealthResults = it.mcpHealthResults + (name to buildMcpHealthFromTest(result))
                ) }
            }
        }
    }

    public fun authMcpServer(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val server = uiState.value.mcpServers[name] ?: return@launch
            try {
                val url = server.url
                if (server.transportType() == McpTransportType.Http && !url.isNullOrBlank()) {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(java.net.URI.create(url))
                        onSystemMessage("Opened browser for MCP auth: $name")
                    } else {
                        onSystemMessage("Desktop not supported for MCP auth: $name")
                    }
                } else {
                    onSystemMessage("MCP auth not supported for stdio server: $name")
                }
            } catch (e: Exception) {
                onSystemMessage("MCP auth failed (${name}): ${e.message}")
            }
        }
    }

    private fun saveMcpSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = configManager.load()
                val updated = current.copy(
                    mcp = current.mcp.copy(
                        client = current.mcp.client.copy(toolCallTimeoutMs = uiState.value.mcpToolTimeoutMs),
                        servers = uiState.value.mcpServers
                    )
                )
                configManager.save(updated)
                onNotifyConfigChanged()
            } catch (e: Exception) {
                onSystemMessage("Failed to save MCP settings: ${e.message}")
            }
        }
    }

    private suspend fun runMcpTest(name: String, server: McpServerConfig): McpTestResult {
        return when (server.transportType()) {
            McpTransportType.Stdio -> {
                val command = server.command
                if (command.isNullOrBlank()) {
                    buildMcpTestError("MCP test (${name}): missing command")
                } else {
                    var process: Process? = null
                    try {
                        process = startMcpTestProcess(server = server, command = command)
                        val transport = McpToolRegistryProvider.defaultStdioTransport(process)
                        val registry = McpToolRegistryProvider.fromTransport(
                            transport = transport,
                            name = name,
                            version = "1.0.0",
                        )
                        buildMcpTestSuccess(registry = registry)
                    } finally {
                        process?.destroy()
                    }
                }
            }
            McpTransportType.Http, McpTransportType.Sse -> {
                buildMcpTestError("HTTP/SSE transport test not implemented in McpViewModel yet")
            }
            else -> buildMcpTestError("Unsupported transport type")
        }
    }

    private fun startMcpTestProcess(server: McpServerConfig, command: String): Process {
        val args = server.args
        val pb = ProcessBuilder(listOf(command) + args)
            .directory(workingDirProvider())
            .redirectErrorStream(true)
        val env = pb.environment()
        server.env?.forEach { (k, v) -> env[k] = v }
        return pb.start()
    }

    private fun buildMcpTestSuccess(registry: ToolRegistry): McpTestResult {
        val tools = registry.tools
            .map { tool ->
                val descriptor = tool.descriptor
                McpToolSummary(
                    name = tool.name,
                    description = descriptor.description,
                    requiredParameters = descriptor.requiredParameters.map { buildToolParameterSummary(it) },
                    optionalParameters = descriptor.optionalParameters.map { buildToolParameterSummary(it) },
                )
            }
            .sortedBy { it.name.lowercase() }
        
        val message = if (tools.isEmpty()) "No tools returned" else "Found ${tools.size} tools"
        return McpTestResult(
            status = McpTestStatus.Success,
            message = message,
            tools = tools,
        )
    }

    private fun buildMcpTestError(message: String): McpTestResult {
        return McpTestResult(
            status = McpTestStatus.Error,
            message = message,
            tools = emptyList(),
        )
    }

    private fun buildMcpHealthFromTest(result: McpTestResult): McpHealthResult {
        return result.status.toHealthResult(message = result.message)
    }

    private fun McpTestStatus.toHealthResult(message: String): McpHealthResult {
        val healthStatus = when (this) {
            McpTestStatus.Success -> McpHealthStatus.Healthy
            McpTestStatus.Error -> McpHealthStatus.Unhealthy
        }
        return McpHealthResult(
            status = healthStatus,
            message = message,
        )
    }

    private fun buildToolParameterSummary(param: ToolParameterDescriptor): McpToolParameterSummary {
        return McpToolParameterSummary(
            name = param.name,
            type = formatToolParameterType(param.type),
            description = param.description,
        )
    }

    private fun formatToolParameterType(type: ToolParameterType): String {
        return when (type) {
            ToolParameterType.String -> "string"
            ToolParameterType.Null -> "null"
            ToolParameterType.Integer -> "int"
            ToolParameterType.Float -> "float"
            ToolParameterType.Boolean -> "boolean"
            is ToolParameterType.Enum -> "enum(${type.entries.joinToString(", ")})"
            is ToolParameterType.List -> "list<${formatToolParameterType(type.itemsType)}>"
            is ToolParameterType.AnyOf -> {
                val entries = type.types.joinToString(", ") { entry ->
                    "${entry.name}:${formatToolParameterType(entry.type)}"
                }
                "anyOf($entries)"
            }
            is ToolParameterType.Object -> {
                val props = type.properties.joinToString(", ") { it.name }
                "object{$props}"
            }
        }
    }
}

public data class McpUiState(
    val mcpServers: Map<String, McpServerConfig> = emptyMap(),
    val mcpToolTimeoutMs: Int = 30000,
    val mcpHealthResults: Map<String, McpHealthResult> = emptyMap(),
    val mcpTestResults: Map<String, McpTestResult> = emptyMap(),
    val mcpTestsInFlight: Set<String> = emptySet(),
)
