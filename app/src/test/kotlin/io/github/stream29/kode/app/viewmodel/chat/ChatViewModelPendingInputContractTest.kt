package io.github.stream29.kode.app.viewmodel.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.app.viewmodel.StopMode
import io.github.stream29.kode.agent.model.AgentScript
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.agent.model.SessionMessage
import io.github.stream29.kode.agent.model.UserMessage
import io.github.stream29.kode.agent.tool.ToolNames
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

class ChatViewModelPendingInputContractTest {
    @Test
    fun deriveWaitingForInputReturnsTrueForSuspendedSessionWithTrailingPendingScript() {
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
    fun deriveWaitingForInputReturnsFalseForSuspendedSessionWithoutTrailingPendingScript() {
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
    fun deriveWaitingForInputReturnsTrueWhenPendingScriptIsTrailingEvenIfRuntimeStillRunning() {
        val messages = listOf<SessionMessage>(
            scriptMessage(status = AgentScriptStatus.PENDING_INPUT),
        )

        assertTrue(
            deriveWaitingForInput(
                messages = messages,
            )
        )
    }

    @Test
    fun deriveWaitingForInputReturnsFalseWhenPendingScriptIsNotTrailing() {
        val messages = listOf<SessionMessage>(
            scriptMessage(status = AgentScriptStatus.PENDING_INPUT),
            userMessage(content = "follow-up"),
        )

        assertFalse(
            deriveWaitingForInput(
                messages = messages,
            )
        )
    }

    @Test
    fun runningWithTrailingPendingScriptIsEditableAndSubmittable() {
        val messages = listOf<SessionMessage>(
            userMessage(content = "hello"),
            scriptMessage(status = AgentScriptStatus.PENDING_INPUT),
        )
        val isWaitingForInput = deriveWaitingForInput(
            messages = messages,
        )
        val beforeEdit = ChatUiState(
            isRunning = true,
            isWaitingForInput = isWaitingForInput,
            taskInput = "response before edit",
        )
        val afterEdit = beforeEdit.copy(taskInput = "response after edit")

        assertTrue(afterEdit.taskInput == "response after edit")
        assertTrue(
            canSubmitInput(
                isRunning = afterEdit.isRunning,
                isWaitingForInput = afterEdit.isWaitingForInput,
            )
        )
    }

    @Test
    fun runningDraftRemainsEditableButSubmitGetsBlockedAfterSoftStopRollsBackPendingInput() {
        val messagesBeforeStop = listOf<SessionMessage>(
            userMessage(content = "hello"),
            scriptMessage(status = AgentScriptStatus.PENDING_INPUT),
        )
        val beforeSoftStop = ChatUiState(
            isRunning = true,
            isWaitingForInput = deriveWaitingForInput(
                messages = messagesBeforeStop,
            ),
            taskInput = "draft before soft-stop",
        )

        assertTrue(
            canSubmitInput(
                isRunning = beforeSoftStop.isRunning,
                isWaitingForInput = beforeSoftStop.isWaitingForInput,
            )
        )

        val messagesAfterSoftStopRollback = listOf<SessionMessage>(
            userMessage(content = "hello"),
        )
        val afterSoftStop = beforeSoftStop.copy(
            isWaitingForInput = deriveWaitingForInput(
                messages = messagesAfterSoftStopRollback,
            ),
            taskInput = "draft after soft-stop",
        )

        assertTrue(afterSoftStop.taskInput == "draft after soft-stop")
        assertFalse(
            canSubmitInput(
                isRunning = afterSoftStop.isRunning,
                isWaitingForInput = afterSoftStop.isWaitingForInput,
            )
        )
    }

    @Test
    fun deriveStopModeMapsRunningSessionToDefaultStopAction() {
        assertTrue(
            deriveStopMode(
                isRunning = true,
                currentStopMode = StopMode.None,
            ) == StopMode.Stop
        )
    }

    @Test
    fun deriveStopModeKeepsSafeRequestedUntilSessionLeavesRunningState() {
        assertTrue(
            deriveStopMode(
                isRunning = true,
                currentStopMode = StopMode.SafeRequested,
            ) == StopMode.SafeRequested
        )
        assertTrue(
            deriveStopMode(
                isRunning = false,
                currentStopMode = StopMode.SafeRequested,
            ) == StopMode.None
        )
    }

    @Test
    fun nextStopModeAfterClickMapsSoftThenHardStopIntent() {
        assertTrue(
            nextStopModeAfterClick(
                currentStopMode = StopMode.Stop,
                forceStop = false,
            ) == StopMode.SafeRequested
        )
        assertTrue(
            nextStopModeAfterClick(
                currentStopMode = StopMode.SafeRequested,
                forceStop = false,
            ) == StopMode.ForceStop
        )
        assertTrue(
            nextStopModeAfterClick(
                currentStopMode = StopMode.SafeRequested,
                forceStop = true,
            ) == StopMode.ForceStop
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
