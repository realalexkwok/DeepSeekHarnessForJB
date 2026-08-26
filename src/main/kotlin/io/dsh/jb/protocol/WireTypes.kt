package io.dsh.jb.protocol

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Kotlin mirrors of the `@deepseek-ai/dsh-sdk-protocol` wire types.
 * See `packages/sdk/protocol/README.md` in the harness checkout.
 *
 * Optional fields must be OMITTED (not null) on the wire: the DSH server
 * rejects `"maxTokens": null` in `initialize`, so request params serialize
 * with `NON_NULL` inclusion.
 */

/** `initialize` params — the process-wide SDK handshake. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class InitializeParams(
    val cwd: String,
    val provider: String,
    val model: String,
    val maxTokens: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ServerInfo(val name: String, val version: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InitializeResult(val serverInfo: ServerInfo)

/** `session/prompt` params — one user turn on one SDK session. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionPromptParams(
    val sessionId: String,
    val contentBlocks: List<JsonNode>,
)

/** Durable enqueue receipt for one prompt. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionPromptResult(val messageId: String)

/** `session.event` notification payload. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionEventNotification(
    val sessionId: String,
    val event: SessionEventEnvelope,
)

/** `session.status` notification payload — whole-agent `idle` | `running`. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionStatusNotification(
    val sessionId: String,
    val status: String,
) {
    val isRunning: Boolean get() = status == "running"
    val isIdle: Boolean get() = status == "idle"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubagentStartedNotification(
    val parentSessionId: String,
    val childSessionId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubagentFinishedNotification(
    val provider: String,
    val agentId: String,
    val parentSessionId: String,
    val childSessionId: String,
    val status: String,
    val stopReason: String? = null,
    val lastAssistantMessage: List<JsonNode>? = null,
)

/**
 * One session-log event envelope, mirroring `dsh-session`'s `SessionEvent`.
 * `data` stays a raw node here; roadmap item 4 adds typed views over it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SessionEventEnvelope(
    val type: String,
    val seq: Long,
    val time: Long,
    val data: JsonNode,
    val ignorable: Boolean? = null,
)

/** Build one OpenAI-style text content block for a prompt. */
fun textContentBlock(text: String, mapper: ObjectMapper): JsonNode =
    mapper.createObjectNode().put("type", "text").put("text", text)
