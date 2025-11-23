package io.github.stream29.kode.app

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.params.LLMParams
import ai.koog.rag.base.files.JVMFileSystemProvider
import io.github.stream29.kode.app.viewmodel.MainViewModel

/**
 * Create a coding agent using Koog framework.
 *
 * Uses built-in file tools from Koog (ReadFileTool, EditFileTool, ListDirectoryTool)
 * and Anthropic's Claude Sonnet 4.5 model.
 */
public fun createCodingAgent(
    apiKey: String, 
    appState: MainViewModel,
    logger: (String) -> Unit = { println(it) }
): AIAgent<String, String> {
    return AIAgent<String, String>(
        promptExecutor = simpleAnthropicExecutor(apiKey),
        llmModel = AnthropicModels.Sonnet_4_5,

        toolRegistry = ToolRegistry {
            // File operations (using Koog's built-in tools)
            tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
            tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
            tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
            
            // Communication tools
            tools(CommunicationTools(appState))
        },

        systemPrompt = """
            You are a highly skilled programming assistant powered by Koog framework.

            Your capabilities:
            - Read files and understand code structure
            - Edit files with precise modifications
            - List directory contents
            - Communicate with the user (ask questions, provide updates)
            - Work with multiple programming languages

            Guidelines:
            - Always read files before editing them
            - Make focused, minimal changes
            - Explain your changes clearly
            - Ask for clarification if the task is ambiguous using the waitForUserInput tool
            - Use sayToUser to provide intermediate updates if a task takes long

            Be precise, efficient, and helpful in your programming assistance.
        """.trimIndent(),

        strategy = strategy("coding_agent_strategy") {
            val forceTool by node<String, String> { input ->
                llm.writeSession {
                    prompt = prompt.withUpdatedParams {
                        toolChoice = LLMParams.ToolChoice.Required
                    }
                }
                input
            }
            val parallelStrategySubgraph = singleRunStrategy()
            nodeStart then forceTool then parallelStrategySubgraph then nodeFinish
        },
        maxIterations = 5000,
        temperature = 0.3 // Low temperature for consistent code generation
    ) {
        // Log tool calls for visibility
        handleEvents {
            onToolCallStarting { ctx ->
                logger("🔧 Calling tool: ${ctx.tool.name}")
                logger("   Args: ${ctx.toolArgs.toString().take(100)}")
            }
        }
    }
}