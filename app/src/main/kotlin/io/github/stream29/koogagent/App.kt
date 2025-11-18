package io.github.stream29.koogagent

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    println("🤖 Koog Code Agent")
    println()

    if (args.isEmpty()) {
        println("Usage: koog-code-agent <task>")
        println()
        println("Examples:")
        println("  koog-code-agent \"Read App.kt and explain what it does\"")
        println("  koog-code-agent \"Add a hello function to Utils.kt\"")
        println("  koog-code-agent \"List all Kotlin files in this project\"")
        println()
        println("Environment:")
        println("  ANTHROPIC_API_KEY must be set")
        return@runBlocking
    }

    val apiKey = System.getenv("ANTHROPIC_API_KEY")
        ?: error("❌ ANTHROPIC_API_KEY environment variable not set")

    val task = args.joinToString(" ")

    println("📋 Task: $task")
    println("📂 Working directory: ${System.getProperty("user.dir")}")
    println()

    val agent = createCodingAgent(apiKey)

    try {
        println("🚀 Starting agent...")
        println()

        val result = agent.run(task)

        println()
        println("✅ Result:")
        println(result)
    } catch (e: Exception) {
        println()
        println("❌ Error:")
        println(e.message)
        if (System.getenv("DEBUG") != null) {
            e.printStackTrace()
        }
    }
}
