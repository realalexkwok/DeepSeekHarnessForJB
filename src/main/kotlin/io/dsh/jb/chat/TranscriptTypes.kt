package io.dsh.jb.chat

import com.fasterxml.jackson.databind.JsonNode
import io.dsh.jb.events.TodoItem
import io.dsh.jb.events.TokenUsage

/**
 * Immutable transcript row vocabulary (roadmap item 5). The rows are what the
 * Swing view renders; the model (ChatTranscriptModel) folds session events into
 * them. Row ids are stable across a row's lifetime (e.g. a streaming assistant
 * row keeps its id through finalization), so the view can update in place.
 */
sealed class TranscriptRow {
    abstract val id: String
}

/** One user-role message. [pending] marks an optimistic local echo awaiting the canonical event. */
data class UserRow(
    override val id: String,
    val content: String,
    val pending: Boolean,
) : TranscriptRow()

enum class AssistantStatus { STREAMING, DONE }

/** One assistant message, streamed live from chunks and finalized by assistant/message. */
data class AssistantRow(
    override val id: String,
    val turn: Long? = null,
    val step: Long? = null,
    val text: String = "",
    val thinking: String = "",
    val status: AssistantStatus = AssistantStatus.STREAMING,
    val usage: TokenUsage? = null,
    val interrupted: Boolean = false,
    val messageId: String? = null,
) : TranscriptRow()

enum class ToolCardStatus { RUNNING, DONE }

/** One tool invocation card: opened by tool/call, settled by tool/result. */
data class ToolCardRow(
    override val id: String,
    val callId: String,
    val name: String,
    val arguments: String,
    val turn: Long? = null,
    val step: Long? = null,
    val status: ToolCardStatus = ToolCardStatus.RUNNING,
    val resultText: String = "",
    val isError: Boolean = false,
    val errorName: String? = null,
    val errorCode: String? = null,
    val meta: JsonNode? = null,
) : TranscriptRow()

enum class NoticeKind {
    TURN_START, TURN_END, STEP, PLAN_MODE, APPROVAL_ASKED, APPROVAL_DECIDED, NOTICE,
}

/** A single-line audit entry (turn/step boundaries, plan mode, approvals, local errors). */
data class NoticeRow(
    override val id: String,
    val kind: NoticeKind,
    val text: String,
) : TranscriptRow()

/** Complete transcript state published after every fold step (immutable snapshot). */
data class TranscriptState(
    val rows: List<TranscriptRow>,
    val todos: List<TodoItem>,
    val planMode: Boolean,
    val running: Boolean,
) {
    companion object {
        val EMPTY = TranscriptState(rows = emptyList(), todos = emptyList(), planMode = false, running = false)
    }
}
