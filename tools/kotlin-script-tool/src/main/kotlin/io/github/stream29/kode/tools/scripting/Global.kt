package io.github.stream29.kode.tools.scripting

import java.util.concurrent.locks.ReentrantLock
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

@PublishedApi internal val host: BasicJvmScriptingHost = BasicJvmScriptingHost()
@PublishedApi internal val scriptEvaluationMutex: ReentrantLock = ReentrantLock()

public const val kotlinScriptToolName: String = "executeKotlinScript"
