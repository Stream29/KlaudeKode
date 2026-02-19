package io.github.stream29.kode.tools.scripting

import ai.koog.agents.core.tools.Tool
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds
import kotlin.time.times

public class KotlinScriptTool(
    public val scriptContext: ScriptContext
) : Tool<KotlinScriptParams, KotlinScriptResult>(
    name = kotlinScriptToolName,
    description = "Execute Kotlin scripts with the embedded Kotlin scripting engine",
    argsSerializer = KotlinScriptParams.serializer(),
    resultSerializer = KotlinScriptResult.serializer(),
) {
    override suspend fun execute(args: KotlinScriptParams): KotlinScriptResult {
        return withTimeout(args.timeoutSeconds * 1.seconds) {
            scriptContext.evalInThreadCancellable(args.script)
        }
    }
}
