package io.github.stream29.kode.session.core.model

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.session.core.tool.ToolNames
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class AgentMessageSerializationCompatibilityTest {
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun decodesLegacyToolExchangeAsCompletedAgentScript() {
        val payload = """
            {
              "type": "tool_exchange",
              "id": "legacy-tool-exchange",
              "toolName": "executeKotlinScript",
              "toolCallId": "call-1",
              "arguments": {"script": "sayToUser(\"hello\")"},
              "result": "done",
              "isError": false,
              "errorMessage": null,
              "displayName": null,
              "timestamp": "2026-03-06T12:00:00Z",
              "metadata": {"legacy": "true"}
            }
        """.trimIndent()

        val decoded = json.decodeFromString(AgentMessage.serializer(), payload)
        val script = assertIs<AgentScript>(decoded)

        assertEquals(AgentScriptStatus.COMPLETED, script.status)
        assertEquals("call-1", script.scriptId)
        assertEquals("done", script.scriptReturnValue)
        assertEquals("{\"script\":\"sayToUser(\\\"hello\\\")\"}", script.scriptStdout)
        assertTrue(script.awaitForUserInput.not())
        assertEquals("executeKotlinScript", script.metadata?.get(SCRIPT_TOOL_NAME_METADATA_KEY))
        assertEquals(
            SCRIPT_RESULT_MODE_CALL_RESULT,
            script.metadata?.get(SCRIPT_RESULT_MODE_METADATA_KEY),
        )
        assertEquals(2, script.toKoogMessages().size)
        assertTrue(script.toKoogMessages().all { it is Message.Tool })
    }

    @Test
    fun decodesLegacySuspendAsPendingAgentScript() {
        val payload = """
            {
              "type": "suspend",
              "id": "legacy-suspend",
              "toolName": "executeKotlinScript",
              "toolCallId": "call-2",
              "arguments": {"script": "suspendForUserInput()"},
              "displayName": null,
              "timestamp": "2026-03-06T12:01:00Z",
              "metadata": null
            }
        """.trimIndent()

        val decoded = json.decodeFromString(AgentMessage.serializer(), payload)
        val script = assertIs<AgentScript>(decoded)

        assertEquals(AgentScriptStatus.PENDING_INPUT, script.status)
        assertTrue(script.awaitForUserInput)
        assertEquals(1, script.toKoogMessages().size)
        assertIs<Message.Tool.Call>(script.toKoogMessages().single())
    }

    @Test
    fun decodesLegacyResumeAsResultOnlyAgentScript() {
        val payload = """
            {
              "type": "resume",
              "id": "legacy-resume",
              "toolName": "executeKotlinScript",
              "toolCallId": "call-3",
              "result": "ok",
              "isError": false,
              "errorMessage": null,
              "timestamp": "2026-03-06T12:02:00Z",
              "metadata": {"legacy": "true"}
            }
        """.trimIndent()

        val decoded = json.decodeFromString(AgentMessage.serializer(), payload)
        val script = assertIs<AgentScript>(decoded)

        assertEquals(AgentScriptStatus.COMPLETED, script.status)
        assertEquals(DEFAULT_SCRIPT_TOOL_ARGS, script.scriptStdout)
        assertEquals("ok", script.scriptReturnValue)
        assertEquals(
            SCRIPT_RESULT_MODE_RESULT_ONLY,
            script.metadata?.get(SCRIPT_RESULT_MODE_METADATA_KEY),
        )
        assertEquals(1, script.toKoogMessages().size)
        assertIs<Message.Tool.Result>(script.toKoogMessages().single())
    }

    @Test
    fun pendingScriptRoundTripKeepsAwaitForUserInputSemantic() {
        val script = AgentScript(
            id = "script-1",
            scriptId = "call-9",
            status = AgentScriptStatus.PENDING_INPUT,
            scriptReturnValue = null,
            scriptStdout = "{\"script\":\"suspendForUserInput()\"}",
            error = null,
            outputList = emptyList(),
            timestamp = Clock.System.now(),
            koogMessages = listOf(
                Message.Tool.Call(
                    id = "call-9",
                    tool = ToolNames.EXECUTE_KOTLIN_SCRIPT,
                    content = "{\"script\":\"suspendForUserInput()\"}",
                    metaInfo = ResponseMetaInfo.Empty,
                )
            ),
            metadata = null,
        )

        val encoded = json.encodeToString(AgentMessage.serializer(), script)
        val decoded = assertIs<AgentScript>(json.decodeFromString(AgentMessage.serializer(), encoded))

        assertEquals(AgentScriptStatus.PENDING_INPUT, decoded.status)
        assertTrue(decoded.awaitForUserInput)
    }
}
