package io.github.stream29.koogagent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider

/**
 * Create a coding agent using Koog framework.
 *
 * Uses built-in file tools from Koog (ReadFileTool, EditFileTool, ListDirectoryTool)
 * and Anthropic's Claude Sonnet 4.5 model.
 */
fun createCodingAgent(apiKey: String): AIAgent<String, String> {
    return AIAgent<String, String>(
        promptExecutor = simpleAnthropicExecutor(apiKey),
        llmModel = AnthropicModels.Sonnet_4_5,

        toolRegistry = ToolRegistry {
            // File operations (using Koog's built-in tools)
            tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
            tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
        },

        systemPrompt = """
            You are a highly skilled programming assistant powered by Koog framework.

            Your capabilities:
            - Read files and understand code structure
            - Edit files with precise modifications
            - List directory contents
            - Work with multiple programming languages

            Guidelines:
            - Always read files before editing them
            - Make focused, minimal changes
            - Explain your changes clearly
            - Ask for clarification if the task is ambiguous

            Be precise, efficient, and helpful in your programming assistance.
        """.trimIndent(),

        strategy = singleRunStrategy(),
        maxIterations = 50,
        temperature = 0.3 // Low temperature for consistent code generation
    ) {
        // Log tool calls for visibility
        handleEvents {
            onToolCallStarting { ctx ->
                println("🔧 Calling tool: ${ctx.tool.name}")
                println("   Args: ${ctx.toolArgs.toString().take(100)}")
            }
        }
    }
}
