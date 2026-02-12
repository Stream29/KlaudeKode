package io.github.stream29.kode.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import io.github.stream29.kode.config.api.LlmAuthConfig
import io.github.stream29.kode.session.core.SessionManager
import io.github.stream29.kode.tools.CommunicationTools
import io.github.stream29.kode.tools.FileSearchTools
import io.github.stream29.kode.tools.KotlinScriptTool
import io.github.stream29.kode.tools.ShellTool
import io.github.stream29.kode.tools.TaskTool
import io.github.stream29.kode.tools.ThinkTool
import io.github.stream29.kode.tools.TodoTool
import io.github.stream29.kode.tools.WebTools
import io.github.stream29.kode.ui.core.MessageHandler
import java.io.File

internal object MultiLLMExecutorFactory {
    fun create(auths: List<LlmAuthConfig>): MultiLLMPromptExecutor {
        val clients = mutableMapOf<LLMProvider, LLMClient>()
        
        auths.forEach { auth ->
            val provider = providerStringToEnum(auth.provider)
            if (!clients.containsKey(provider)) {
                val client = createClientForAuth(auth)
                clients[provider] = client
            }
        }
        
        return MultiLLMPromptExecutor(clients)
    }
    
    private fun providerStringToEnum(provider: String): LLMProvider {
        return when (provider) {
            "Anthropic" -> LLMProvider.Anthropic
            "OpenAI" -> LLMProvider.OpenAI
            "Moonshot" -> LLMProvider.OpenAI
            "DeepSeek" -> LLMProvider.OpenAI
            "Gemini" -> LLMProvider.Google
            else -> LLMProvider.OpenAI
        }
    }
    
    private fun createClientForAuth(auth: LlmAuthConfig): LLMClient {
        return when (auth) {
            is LlmAuthConfig.Anthropic -> AnthropicLLMClient(apiKey = auth.apiKey)
            is LlmAuthConfig.OpenAI -> {
                val baseUrl = auth.baseUrl
                val settings = if (baseUrl != null) 
                    OpenAIClientSettings(baseUrl = baseUrl) 
                else 
                    OpenAIClientSettings()
                OpenAILLMClient(apiKey = auth.apiKey, settings = settings)
            }
            is LlmAuthConfig.Moonshot -> {
                val settings = OpenAIClientSettings(baseUrl = auth.baseUrl ?: "https://api.moonshot.cn/v1")
                OpenAILLMClient(apiKey = auth.apiKey, settings = settings)
            }
            is LlmAuthConfig.DeepSeek -> {
                val settings = OpenAIClientSettings(baseUrl = auth.baseUrl ?: "https://api.deepseek.com/v1")
                OpenAILLMClient(apiKey = auth.apiKey, settings = settings)
            }
            is LlmAuthConfig.Gemini -> {
                val baseUrl = auth.baseUrl
                val settings = if (baseUrl != null) 
                    GoogleClientSettings(baseUrl = baseUrl) 
                else 
                    GoogleClientSettings()
                GoogleLLMClient(apiKey = auth.apiKey, settings = settings)
            }
            is LlmAuthConfig.OpenAICompatible -> {
                val settings = OpenAIClientSettings(baseUrl = auth.baseUrl)
                OpenAILLMClient(apiKey = auth.apiKey, settings = settings)
            }
        }
    }
}

internal object ToolRegistryFactory {
    fun create(
        workingDir: File,
        messageHandler: MessageHandler,
        logger: (String) -> Unit,
        disabledTools: Set<String>,
        taskAgentFactory: io.github.stream29.kode.tools.AgentFactory?,
        ownerSessionId: String?,
        ownerAgentId: String?,
        sessionManager: SessionManager?,
    ): ToolRegistry {
        return ToolRegistry {
            val disableFile = disabledTools.contains("file")
            val disableFileEdit = disableFile || disabledTools.contains("file-edit")

            if (!disableFile) {
                tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
                tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            }
            if (!disableFileEdit) {
                tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
            }
            if (!disabledTools.contains("communication")) {
                tools(CommunicationTools(messageHandler))
            }
            if (!disabledTools.contains("shell")) {
                tools(ShellTool(messageHandler, workingDir, logger))
                tools(KotlinScriptTool(messageHandler, workingDir, logger))
            }
            if (!disabledTools.contains("web")) {
                tools(WebTools(messageHandler, logger))
            }
            if (!disabledTools.contains("todo")) {
                tools(TodoTool(messageHandler, logger))
            }
            if (!disabledTools.contains("search")) {
                tools(FileSearchTools(messageHandler, workingDir, logger))
            }
            if (!disabledTools.contains("think")) {
                tools(ThinkTool(messageHandler, logger))
            }
            if (!disabledTools.contains("task") && taskAgentFactory != null) {
                tools(
                    TaskTool(
                        messageHandler = messageHandler,
                        agentFactory = taskAgentFactory,
                        logger = logger,
                        sessionManager = sessionManager,
                        ownerSessionId = ownerSessionId,
                        ownerAgentId = ownerAgentId,
                    )
                )
            }
        }
    }
}
