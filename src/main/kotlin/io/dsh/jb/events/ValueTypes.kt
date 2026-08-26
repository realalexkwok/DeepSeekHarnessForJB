package io.dsh.jb.events

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Shared value types of the DSH session-event vocabulary (roadmap item 4),
 * mirroring `packages/llm/llm/src/{types,message}.ts` and
 * `packages/core/session/src/types.ts` in the harness checkout.
 *
 * The discriminated unions (ContentBlock, FinishReason, StreamChunk,
 * MessageSource, TurnEndReason, TurnEndCancelCause) are Kotlin sealed
 * hierarchies mapped with Jackson polymorphism: the discriminator property
 * stays `visible`, and an unrecognized discriminator value falls back to the
 * per-union `Raw*` variant, which captures the COMPLETE original JSON —
 * discriminator included — via `@JsonAnySetter` (the merge-extensible
 * vocabulary rule: never fail on drift, keep the raw payload).
 *
 * Field-less union members (e.g. `StopFinish`, `CompletedEnd`) are plain
 * marker classes with identity equality — dispatch on them with `is`, not
 * `==` (Jackson constructs a fresh instance per payload; a Kotlin `object`
 * would break the singleton invariant).
 */

/** Token accounting for one model call (cache fields optional; counts are disjoint). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val reasoningTokens: Long? = null,
)

/** Serializable provider or transport failure facts. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class LlmFailure(
    val message: String,
    val code: String,
    val status: Int? = null,
    val providerRetryAfterMs: Long? = null,
    val requestId: String? = null,
)

/** Common capture helper for unrecognized union members: keeps the whole original object. */
open class RawJsonCapture {
    /** The complete original JSON object for this unrecognized member (type id included). */
    @get:JsonIgnore
    var raw: JsonNode = NullNode.instance
        protected set

    @JsonAnySetter
    fun capture(name: String, value: JsonNode) {
        if (raw is NullNode) raw = JsonNodeFactory.instance.objectNode()
        (raw as ObjectNode).set<JsonNode>(name, value)
    }
}

// ---------------------------------------------------------------------------
// Content blocks
// ---------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true,
    defaultImpl = RawContentBlock::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = TextBlock::class, name = "text"),
    JsonSubTypes.Type(value = ReasoningBlock::class, name = "reasoning"),
    JsonSubTypes.Type(value = ImageBlock::class, name = "image"),
    JsonSubTypes.Type(value = ToolCallBlock::class, name = "tool-call"),
    JsonSubTypes.Type(value = ToolResultBlock::class, name = "tool-result"),
)
sealed interface ContentBlock

/** Plain text visible to the end user. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TextBlock(val text: String) : ContentBlock

/** Reasoning / thinking content, distinct from visible text. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ReasoningBlock(val text: String) : ContentBlock

/** Durable raster image reference; `attachment` is kept raw (opaque to the plugin for now). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageBlock(val attachment: JsonNode) : ContentBlock

/** A tool invocation requested by the model. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolCallBlock(val id: String, val name: String, val arguments: String) : ContentBlock

/** The result of a tool invocation, sent back to the model. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolResultBlock(
    val toolCallId: String,
    val content: List<ContentBlock>,
    val isError: Boolean? = null,
) : ContentBlock

/** Unrecognized content-block type: the complete original JSON, `type` included. */
class RawContentBlock : RawJsonCapture(), ContentBlock

// ---------------------------------------------------------------------------
// Finish reasons
// ---------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "kind",
    visible = true,
    defaultImpl = RawFinishReason::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StopFinish::class, name = "stop"),
    JsonSubTypes.Type(value = ToolCallsFinish::class, name = "tool-calls"),
    JsonSubTypes.Type(value = MaxTokensFinish::class, name = "max-tokens"),
    JsonSubTypes.Type(value = AbortedFinish::class, name = "aborted"),
    JsonSubTypes.Type(value = ErrorFinish::class, name = "error"),
)
sealed interface FinishReason

/** The model stopped normally. */
class StopFinish : FinishReason

/** The model requested tool calls. */
class ToolCallsFinish : FinishReason

/** The model hit the output-token ceiling. */
class MaxTokensFinish : FinishReason

/** The stream was aborted. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AbortedFinish(val failure: LlmFailure) : FinishReason

/** The stream failed. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorFinish(val failure: LlmFailure) : FinishReason

/** Unrecognized finish-reason kind: the complete original JSON, `kind` included. */
class RawFinishReason : RawJsonCapture(), FinishReason

// ---------------------------------------------------------------------------
// Stream chunks
// ---------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
    visible = true,
    defaultImpl = RawStreamChunk::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = BlockStartChunk::class, name = "block-start"),
    JsonSubTypes.Type(value = TextDeltaChunk::class, name = "text-delta"),
    JsonSubTypes.Type(value = ReasoningDeltaChunk::class, name = "reasoning-delta"),
    JsonSubTypes.Type(value = ToolCallDeltaChunk::class, name = "tool-call-delta"),
    JsonSubTypes.Type(value = BlockEndChunk::class, name = "block-end"),
    JsonSubTypes.Type(value = UsageChunk::class, name = "usage"),
    JsonSubTypes.Type(value = FinishChunk::class, name = "finish"),
)
sealed interface StreamChunk

/** Opens a content block in the stream. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BlockStartChunk(val index: Long, val blockType: String) : StreamChunk

/** A delta of visible text. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TextDeltaChunk(val index: Long, val text: String) : StreamChunk

/** A delta of reasoning / thinking content. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ReasoningDeltaChunk(val index: Long, val text: String) : StreamChunk

/** A delta of one tool-call block; `name` rides the first frame only. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolCallDeltaChunk(
    val index: Long,
    val id: String,
    val name: String? = null,
    val argumentsDelta: String,
) : StreamChunk

/** Closes a content block; carries the assembled block. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class BlockEndChunk(val index: Long, val block: ContentBlock) : StreamChunk

/** Token accounting for the model call (emitted before the terminal finish). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UsageChunk(val usage: TokenUsage) : StreamChunk

/** The terminal chunk: why the stream stopped, plus optional replay metadata. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class FinishChunk(val reason: FinishReason, val replayState: JsonNode? = null) : StreamChunk

/** Unrecognized chunk type: the complete original JSON, `type` included. */
class RawStreamChunk : RawJsonCapture(), StreamChunk

// ---------------------------------------------------------------------------
// Message sources
// ---------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "kind",
    visible = true,
    defaultImpl = RawMessageSource::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = UserSource::class, name = "user"),
    JsonSubTypes.Type(value = PluginSource::class, name = "plugin"),
    JsonSubTypes.Type(value = ModelSource::class, name = "model"),
    JsonSubTypes.Type(value = ToolSource::class, name = "tool"),
)
sealed interface MessageSource

/** A direct human prompt. */
class UserSource : MessageSource

/**
 * Producer-supplied context. `form` is the semantic kind
 * (`instructions` | `catalog` | `snapshot` | `notice` | `relay` |
 * `recall`), kept as a string (the vocabulary grows one value at a time).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PluginSource(
    val plugin: String,
    val form: String? = null,
    val sections: List<ContextSnapshotSection>? = null,
    val summary: String? = null,
) : MessageSource

/** One named contribution of a `snapshot`-form context message, in assembly order. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ContextSnapshotSection(val name: String, val text: String)

/** An assistant message produced by a routed model. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ModelSource(
    val provider: String,
    val model: String,
    val replayState: JsonNode? = null,
) : MessageSource

/** A user-role message carrying one tool result. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolSource(val callId: String) : MessageSource

/** Unrecognized message-source kind: the complete original JSON, `kind` included. */
class RawMessageSource : RawJsonCapture(), MessageSource

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

/** Assembled assistant message for one step (role is fixed to `assistant` on the wire). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AssistantMessage(
    val id: String,
    val content: List<ContentBlock>,
    val source: MessageSource,
) {
    val role: String get() = "assistant"
}

/** Tool-result message whose model-facing block retains call correlation (role `user`). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ToolResultMessage(
    val id: String,
    val content: List<ContentBlock>,
    val source: MessageSource,
) {
    val role: String get() = "user"
}

// ---------------------------------------------------------------------------
// Todos
// ---------------------------------------------------------------------------

/** The three-state todo lifecycle (closed vocabulary). */
enum class TodoStatus(val wire: String) {
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    ;

    companion object {
        fun fromWire(value: String): TodoStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** One entry in an agent's todo list — the unit of the `todo/write` snapshot. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class TodoItem(val content: String, val status: String) {
    val statusValue: TodoStatus? get() = TodoStatus.fromWire(status)
}

// ---------------------------------------------------------------------------
// Turn end reasons and cancellation causes
// ---------------------------------------------------------------------------

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "kind",
    visible = true,
    defaultImpl = RawCancelCause::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = UserCancel::class, name = "user"),
    JsonSubTypes.Type(value = ParentCancel::class, name = "parent"),
    JsonSubTypes.Type(value = HookCancel::class, name = "hook"),
    JsonSubTypes.Type(value = DisposedCancel::class, name = "disposed"),
    JsonSubTypes.Type(value = LegacyCancel::class, name = "legacy"),
)
sealed interface TurnEndCancelCause

/** A user cancelled the live turn. */
class UserCancel : TurnEndCancelCause

/** A parent agent cancelled the live turn. */
class ParentCancel : TurnEndCancelCause

/** A hook cancelled the live turn. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HookCancel(val reason: String) : TurnEndCancelCause

/** The agent was disposed. */
class DisposedCancel : TurnEndCancelCause

/** A coarse cancellation record from an older log format. */
class LegacyCancel : TurnEndCancelCause

/** Unrecognized cancellation-cause kind: the complete original JSON, `kind` included. */
class RawCancelCause : RawJsonCapture(), TurnEndCancelCause

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "kind",
    visible = true,
    defaultImpl = RawTurnEndReason::class,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = CompletedEnd::class, name = "completed"),
    JsonSubTypes.Type(value = AbortedEnd::class, name = "aborted"),
    JsonSubTypes.Type(value = BlockedEnd::class, name = "blocked"),
    JsonSubTypes.Type(value = ErrorEnd::class, name = "error"),
    JsonSubTypes.Type(value = MaxTokensEnd::class, name = "max-tokens"),
    JsonSubTypes.Type(value = InterruptedEnd::class, name = "interrupted"),
)
sealed interface TurnEndReason

/** The turn completed successfully. */
class CompletedEnd : TurnEndReason

/** A cancellation request interrupted the live turn. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AbortedEnd(val reason: TurnEndCancelCause) : TurnEndReason

/** The turn was blocked. */
class BlockedEnd : TurnEndReason

/** The turn failed; `error` is always a structured failure. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ErrorEnd(val error: LlmFailure) : TurnEndReason

/** At least one step reached its output-token ceiling. */
class MaxTokensEnd : TurnEndReason

/** A persistence backend closed a crash-orphaned turn on reload. */
class InterruptedEnd : TurnEndReason

/** Unrecognized turn-end reason: the complete original JSON, `kind` included. */
class RawTurnEndReason : RawJsonCapture(), TurnEndReason
