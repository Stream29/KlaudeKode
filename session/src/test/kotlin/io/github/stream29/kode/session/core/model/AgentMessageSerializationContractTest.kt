package io.github.stream29.kode.session.core.model

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import io.github.stream29.kode.agent.model.AgentMessage
import io.github.stream29.kode.agent.model.AgentScript
import io.github.stream29.kode.agent.model.AgentScriptStatus
import io.github.stream29.kode.agent.tool.ToolNames
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class AgentMessageSerializationContractTest {
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun rejectsLegacyToolExchangeType() {
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
              "timestamp": "2026-03-06T12:00:00Z"
            }
        """.trimIndent()

        val error = assertFailsWith<SerializationException> {
            json.decodeFromString(AgentMessage.serializer(), payload)
        }
        assertTrue(error.message.orEmpty().contains("Unsupported AgentMessage type"))
    }

    @Test
    fun rejectsLegacySuspendType() {
        val payload = """
            {
              "type": "suspend",
              "id": "legacy-suspend",
              "toolName": "executeKotlinScript",
              "toolCallId": "call-2",
              "arguments": {"script": "suspendForUserInput()"},
              "timestamp": "2026-03-06T12:01:00Z"
            }
        """.trimIndent()

        val error = assertFailsWith<SerializationException> {
            json.decodeFromString(AgentMessage.serializer(), payload)
        }
        assertTrue(error.message.orEmpty().contains("Unsupported AgentMessage type"))
    }

    @Test
    fun rejectsLegacyResumeType() {
        val payload = """
            {
              "type": "resume",
              "id": "legacy-resume",
              "toolName": "executeKotlinScript",
              "toolCallId": "call-3",
              "result": "ok",
              "isError": false,
              "errorMessage": null,
              "timestamp": "2026-03-06T12:02:00Z"
            }
        """.trimIndent()

        val error = assertFailsWith<SerializationException> {
            json.decodeFromString(AgentMessage.serializer(), payload)
        }
        assertTrue(error.message.orEmpty().contains("Unsupported AgentMessage type"))
    }

    @Test
    fun rejectsPayloadWithoutTypeDiscriminator() {
        val payload = """
            {
              "id": "script-1",
              "scriptId": "call-9",
              "status": "PENDING_INPUT",
              "scriptReturnValue": null,
              "scriptStdout": "{\"script\":\"suspendForUserInput()\"}",
              "error": null,
              "outputList": [],
              "timestamp": "2026-03-06T12:03:00Z",
              "koogMessages": []
            }
        """.trimIndent()

        val error = assertFailsWith<SerializationException> {
            json.decodeFromString(AgentMessage.serializer(), payload)
        }
        assertTrue(error.message.orEmpty().contains("<missing>"))
    }

    @Test
    fun pendingScriptRoundTripIncludesTypeAndKeepsAwaitForUserInputSemantic() {
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
        assertTrue(encoded.contains("\"type\":\"script\""))
        val decoded = assertIs<AgentScript>(json.decodeFromString(AgentMessage.serializer(), encoded))

        assertEquals(AgentScriptStatus.PENDING_INPUT, decoded.status)
        assertTrue(decoded.awaitForUserInput)
    }
}
