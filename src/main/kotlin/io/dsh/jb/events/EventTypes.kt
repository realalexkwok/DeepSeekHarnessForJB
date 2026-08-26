package io.dsh.jb.events

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode

/**
 * The event-payload union (`EventData`): one typed Kotlin view per
 * session-event type the chat UI (roadmap item 5) consumes, mirroring
 * `SessionEventMap` in `packages/core/session/src/types.ts` plus the
 * plugin-merged declarations (commands, approvals, plan mode) in the harness
 * checkout. Unknown event types stay generic JSON ([UnknownEventData]); a
 * known type whose payload fails to bind keeps the raw node plus the binding
 * error ([MalformedEventData]) — the UI never crashes on vocabulary drift.
 */
sealed class EventData

/** `assistant/chunk` — raw stream chunk with turn/step coordinates. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistantChunkEvent(
    val turn: Long,
    val step: Long,
    val chunk: StreamChunk,
) : EventData()

/** `assistant/message` — assembled assistant message for one step. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistantMessageEvent(
    val turn: Long,
    val step: Long,
    val message: AssistantMessage,
    val usage: TokenUsage? = null,
    val interrupted: Boolean? = null,
) : EventData()

/** `tool/call` — the model requested one tool invocation. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolCallEvent(
    val turn: Long,
    val step: Long,
    val callId: String,
    val name: String,
    val arguments: String,
) : EventData()

/** `tool/result` — a completed tool call's model-facing result. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolResultEvent(
    val turn: Long,
    val step: Long,
    val message: ToolResultMessage,
    val error: ToolError? = null,
    val meta: JsonNode? = null,
) : EventData()

/** Optional internal failure identity of a tool result. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolError(val name: String, val code: String)

/** `turn/start` — opens a turn before queued input is claimed. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TurnStartEvent(val turn: Long) : EventData()

/** `turn/end` — closes a turn with the reason that ended it. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TurnEndEvent(val turn: Long, val reason: TurnEndReason) : EventData()

/** `step/start` — opens a step (one model call plus its tool executions). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StepStartEvent(val turn: Long, val step: Long) : EventData()

/** `step/end` — closes a step. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StepEndEvent(val turn: Long, val step: Long) : EventData()

/** `plan/mode` — whether plan mode is in force from this point on. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PlanModeEvent(val active: Boolean) : EventData()

/** `approval/asked` — an approval question was put to the answerer chain. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApprovalAskedEvent(
    val id: String,
    val toolName: String,
    val callId: String? = null,
    val reason: String? = null,
) : EventData()

/** Closed approval-outcome vocabulary (`allowed-once` | `rejected` | `cancelled` | `unavailable`). */
enum class ApprovalOutcome(val wire: String) {
    ALLOWED_ONCE("allowed-once"),
    REJECTED("rejected"),
    CANCELLED("cancelled"),
    UNAVAILABLE("unavailable"),
    ;

    companion object {
        fun fromWire(value: String): ApprovalOutcome? = entries.firstOrNull { it.wire == value }
    }
}

/** `approval/decided` — the outcome of a prior `approval/asked` (same id). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ApprovalDecidedEvent(val id: String, val outcome: String) : EventData() {
    val outcomeValue: ApprovalOutcome? get() = ApprovalOutcome.fromWire(outcome)
}

/** `todo/write` — whole-list snapshot; latest write wins. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TodoWriteEvent(val todos: List<TodoItem>) : EventData()

/** `user/message` — a user-role message on the model-visible surface. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserMessageEvent(
    val id: String,
    val content: List<ContentBlock>,
    val source: MessageSource,
) : EventData() {
    val role: String get() = "user"
}

// ---------------------------------------------------------------------------
// Commands
// ---------------------------------------------------------------------------

/** Who issued a command line (merge-extensible; `user` is the only core variant). */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "kind",
    visible = true,
    defaultImpl = RawCommandSource::class,
)
@JsonSubTypes(JsonSubTypes.Type(value = UserCommandSource::class, name = "user"))
sealed interface CommandSource

/** A human-facing UI dispatched a human-typed line. */
class UserCommandSource : CommandSource

/** Unrecognized command-source kind: the complete original JSON, `kind` included. */
class RawCommandSource : RawJsonCapture(), CommandSource

/** `command/run` — a resolved slash command entered its handler. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandRunEvent(
    val commandId: String,
    val name: String,
    val args: String? = null,
    val source: CommandSource,
) : EventData()

/** Closed command-outcome kinds (`success` | `error`). */
enum class CommandOutcome(val wire: String) {
    SUCCESS("success"),
    ERROR("error"),
    ;

    companion object {
        fun fromWire(value: String): CommandOutcome? = entries.firstOrNull { it.wire == value }
    }
}

/** `command/done` — the paired command settled. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandDoneEvent(
    val commandId: String,
    val kind: String,
    val text: String? = null,
    val sourceEventSeq: Long? = null,
) : EventData() {
    val kindValue: CommandOutcome? get() = CommandOutcome.fromWire(kind)
}

// ---------------------------------------------------------------------------
// Fallbacks and the event-type vocabulary
// ---------------------------------------------------------------------------

/** An event type this plugin version does not model: the original `data` JSON kept verbatim. */
data class UnknownEventData(val raw: JsonNode) : EventData()

/** A known event type whose payload failed to bind: the original JSON kept, error recorded. */
data class MalformedEventData(val raw: JsonNode, val error: String) : EventData()

/** The session-event types this plugin models; anything else stays [UnknownEventData]. */
enum class SessionEventType(val wire: String) {
    ASSISTANT_CHUNK("assistant/chunk"),
    ASSISTANT_MESSAGE("assistant/message"),
    TOOL_CALL("tool/call"),
    TOOL_RESULT("tool/result"),
    TURN_START("turn/start"),
    TURN_END("turn/end"),
    STEP_START("step/start"),
    STEP_END("step/end"),
    PLAN_MODE("plan/mode"),
    APPROVAL_ASKED("approval/asked"),
    APPROVAL_DECIDED("approval/decided"),
    TODO_WRITE("todo/write"),
    USER_MESSAGE("user/message"),
    COMMAND_RUN("command/run"),
    COMMAND_DONE("command/done"),
    ;

    companion object {
        fun fromWire(value: String): SessionEventType? = entries.firstOrNull { it.wire == value }
    }
}
