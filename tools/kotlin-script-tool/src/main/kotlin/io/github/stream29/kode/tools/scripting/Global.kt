package io.github.stream29.kode.tools.scripting

import java.util.concurrent.locks.ReentrantLock
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

internal val host = BasicJvmScriptingHost()
internal val scriptEvaluationMutex = ReentrantLock()

public const val kotlinScriptToolName: String = "KotlinScriptTool"