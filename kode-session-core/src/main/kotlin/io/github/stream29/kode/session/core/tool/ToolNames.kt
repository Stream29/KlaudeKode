package io.github.stream29.kode.session.core.tool

public object ToolNames {
    public const val EXECUTE_KOTLIN_SCRIPT: String = "executeKotlinScript"
    public const val WAIT_FOR_USER_INPUT: String = "waitForUserInput"
    public const val SAY_TO_USER: String = "sayToUser"
    public const val USER_INTERRUPT: String = "userInterrupt"

    public const val FORK_SUBAGENT: String = "forkSubagent"
    public const val SPAWN_SUBAGENT: String = "spawnSubagent"
    public const val POLL_AGENT_RESULT: String = "pollAgentResult"
    public const val AWAIT_AGENT_RESULT: String = "awaitAgentResult"
    public const val KILL_AGENT: String = "killAgent"
    public const val LIST_ACTIVE_AGENTS: String = "listActiveAgents"
    public const val SAY_TO_AGENT: String = "sayToAgent"
    public const val RETURN_AGENT_RESULT: String = "returnAgentResult"

    public const val RECEIVE_AGENT_MESSAGE: String = "receiveAgentMessage"
    public const val FORK: String = "fork"
}
