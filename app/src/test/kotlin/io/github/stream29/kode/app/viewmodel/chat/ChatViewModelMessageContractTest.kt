package io.github.stream29.kode.app.viewmodel.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.agent.model.AgentScript
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.agent.model.SessionMessage
import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.agent.tool.ToolNames
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ChatViewModelMessageContractTest {
    @Test
    fun deriveWaitingForInputReturnsTrueForTrailingPendingScriptMessage() {
        val messages = listOf<SessionMessage>(
            userMessage(content = "hello"),
            scriptMessage(status = AgentScriptStatus.PENDING_INPUT),
        )

        assertTrue(
            deriveWaitingForInput(
                messages = messages,
            )
        )
    }

    @Test
    fun deriveWaitingForInputReturnsFalseForTrailingCompletedScriptMessage() {
        val messages = listOf<SessionMessage>(
            userMessage(content = "hello"),
            scriptMessage(status = AgentScriptStatus.COMPLETED),
        )

        assertFalse(
            deriveWaitingForInput(
                messages = messages,
            )
        )
    }

    @Test
    fun runningWithoutTrailingPendingScriptIsEditableButNotSubmittable() {
        val messages = listOf<SessionMessage>(
            userMessage(content = "hello"),
            scriptMessage(status = AgentScriptStatus.COMPLETED),
        )
        val isWaitingForInput = deriveWaitingForInput(
            messages = messages,
        )
        val beforeEdit = ChatUiState(
            isRunning = true,
            isWaitingForInput = isWaitingForInput,
            taskInput = "draft before edit",
        )
        val afterEdit = beforeEdit.copy(taskInput = "draft after edit")

        assertTrue(afterEdit.taskInput == "draft after edit")
        assertFalse(
            canSubmitInput(
                isRunning = afterEdit.isRunning,
                isWaitingForInput = afterEdit.isWaitingForInput,
            )
        )
    }

    private fun userMessage(content: String): UserMessage {
        return UserMessage(
            id = "user-message-id",
            content = content,
            timestamp = Clock.System.now(),
            koogMessages = listOf(
                Message.User(
                    content = content,
                    metaInfo = RequestMetaInfo.Empty,
                )
            ),
            metadata = null,
        )
    }

    private fun scriptMessage(status: AgentScriptStatus): AgentScript {
        return AgentScript(
            id = "script-message-id",
            scriptId = "script-id",
            status = status,
            scriptReturnValue = null,
            scriptStdout = "",
            error = null,
            outputList = emptyList(),
            timestamp = Clock.System.now(),
            koogMessages = listOf(
                Message.Tool.Call(
                    id = "script-id",
                    tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                    content = "{\"script\":\"suspendForUserInput()\"}",
                    metaInfo = ResponseMetaInfo.Empty,
                )
            ),
            metadata = null,
        )
    }

    private fun canSubmitInput(
        isRunning: Boolean,
        isWaitingForInput: Boolean,
    ): Boolean {
        return !isRunning || isWaitingForInput
    }
}
