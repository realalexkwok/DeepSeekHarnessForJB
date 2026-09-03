package io.dsh.jb.chat

import io.dsh.jb.events.AbortedEnd
import io.dsh.jb.events.AbortedFinish
import io.dsh.jb.events.ApprovalAskedEvent
import io.dsh.jb.events.ApprovalDecidedEvent
import io.dsh.jb.events.AssistantChunkEvent
import io.dsh.jb.events.AssistantMessageEvent
import io.dsh.jb.events.BlockedEnd
import io.dsh.jb.events.CommandDoneEvent
import io.dsh.jb.events.CommandRunEvent
import io.dsh.jb.events.CompletedEnd
import io.dsh.jb.events.ContentBlock
import io.dsh.jb.events.DisposedCancel
import io.dsh.jb.events.ErrorEnd
import io.dsh.jb.events.ErrorFinish
import io.dsh.jb.events.EventMapper
import io.dsh.jb.events.FinishChunk
import io.dsh.jb.events.HookCancel
import io.dsh.jb.events.ImageBlock
import io.dsh.jb.events.InterruptedEnd
import io.dsh.jb.events.LegacyCancel
import io.dsh.jb.events.MalformedEventData
import io.dsh.jb.events.MaxTokensEnd
import io.dsh.jb.events.ParentCancel
import io.dsh.jb.events.PlanModeEvent
import io.dsh.jb.chat.ReasoningRow
import io.dsh.jb.events.RawCancelCause
import io.dsh.jb.events.RawContentBlock
import io.dsh.jb.events.RawTurnEndReason
import io.dsh.jb.events.ReasoningBlock
import io.dsh.jb.events.ReasoningDeltaChunk
import io.dsh.jb.events.SessionEventView
import io.dsh.jb.events.StepEndEvent
import io.dsh.jb.events.StepStartEvent
import io.dsh.jb.events.TextBlock
import io.dsh.jb.events.TextDeltaChunk
import io.dsh.jb.events.TodoItem
import io.dsh.jb.events.TodoWriteEvent
import io.dsh.jb.events.ToolCallBlock
import io.dsh.jb.events.ToolCallEvent
import io.dsh.jb.events.ToolResultBlock
import io.dsh.jb.events.ToolResultEvent
import io.dsh.jb.events.ToolSource
import io.dsh.jb.events.TurnEndCancelCause
import io.dsh.jb.events.TurnEndEvent
import io.dsh.jb.events.TurnEndReason
import io.dsh.jb.events.TurnStartEvent
import io.dsh.jb.events.UnknownEventData
import io.dsh.jb.events.UsageChunk
import io.dsh.jb.events.UserCancel
import io.dsh.jb.events.UserMessageEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.dsh.jb.diff.FsDiffParser
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionStatusNotification
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pure-JVM fold of the session-event stream into a [TranscriptState] (roadmap
 * item 5). No IntelliJ dependencies: the Swing view subscribes via
 * [addListener] and hops to the EDT itself.
 *
 * Folding rules:
 * - user/message replaces the oldest optimistic echo (FIFO) with the
 *   canonical row, or appends when no echo is pending.
 * - assistant/chunk deltas accumulate into one streaming assistant row;
 *   assistant/message finalizes it (DONE, usage, interruption) without
 *   replacing streamed text; a finish with an error/aborted reason appends a
 *   failure notice. A turn boundary force-closes a dangling streaming row.
 * - tool/call opens a running card; tool/result settles it (or creates
 *   a settled card when the call was missed).
 * - Unknown and malformed payloads are tolerated and ignored.
 */
class ChatTranscriptModel(private val eventMapper: EventMapper = EventMapper()) {

    private val mapper = jacksonObjectMapper()
    private val lock = Any()
    private val rows = ArrayList<TranscriptRow>()
    private var todos: List<TodoItem> = emptyList()
    private var planMode = false
    private var running = false

    @Volatile
    private var state = TranscriptState.EMPTY

    private val listeners = CopyOnWriteArrayList<(TranscriptState) -> Unit>()

    private var streamingAssistantId: String? = null
    private var noKeyGuidanceShown = false
    private var echoSeq = 0L
    private var streamSeq = 0L
    private var noticeSeq = 0L
    private val pendingEchos = ArrayDeque<String>()
    /** Item 23 (host round 4): the reasoning text accumulated before the next
     * tool/call — the harness has no per-tool reasoning field, so the model
     * snapshots the assistant stream. */
    private var lastThinking = ""

    /** Current immutable snapshot. */
    fun state(): TranscriptState = state

    /** Subscribes to state changes; the listener also receives the current state immediately. */
    fun addListener(listener: (TranscriptState) -> Unit) {
        listeners += listener
        listener(state())
    }

    /** Drops all transcript state (used by tests; the tool window is project-lifetime). */
    fun reset() = synchronized(lock) {
        rows.clear()
        todos = emptyList()
        planMode = false
        running = false
        streamingAssistantId = null
        noKeyGuidanceShown = false
        pendingEchos.clear()
        lastThinking = ""
        publishLocked()
    }

    /**
     * Optimistic local echo of a user prompt; returns the synthetic row id.
     * The canonical user/message event replaces it (FIFO across echoes).
     */
    fun echoPrompt(text: String): String = synchronized(lock) {
        val id = "user-pending-${++echoSeq}"
        pendingEchos.addLast(id)
        rows += UserRow(id = id, content = text, pending = true)
        publishLocked()
        id
    }

    /** Item 24 (replanned): renders one pending permission ask as a card. */
    fun addPermission(approvalId: String, toolName: String, reason: String?): String = synchronized(lock) {
        val rowId = "permission-" + approvalId
        rows.removeAll { it is PermissionRow && it.id == rowId }
        rows += PermissionRow(rowId, approvalId, toolName, reason)
        publishLocked()
        rowId
    }

    /** Item 24 (replanned): removes the card once the user decided (the
     * approval/decided notice remains the audit trail). */
    fun resolvePermission(rowId: String) = synchronized(lock) {
        rows.removeAll { it is PermissionRow && it.id == rowId }
        publishLocked()
    }

    /** Item 24 (host round): renders one generic ask_user_question as a card. */
    fun addQuestion(questionId: String, header: String?, question: String, options: List<String>, multiple: Boolean): String = synchronized(lock) {
        val rowId = "question-" + questionId
        rows.removeAll { it is QuestionRow && it.id == rowId }
        rows += QuestionRow(rowId, questionId, header, question, options, multiple)
        publishLocked()
        rowId
    }

    fun resolveQuestion(rowId: String) = synchronized(lock) {
        rows.removeAll { it is QuestionRow && it.id == rowId }
        publishLocked()
    }

    /** A local note (e.g. a send failure) rendered as a plain notice row. */
    fun notice(text: String): String = synchronized(lock) {
        val id = "notice-${++noticeSeq}"
        rows += NoticeRow(id = id, kind = NoticeKind.NOTICE, text = text)
        publishLocked()
        id
    }

    fun onStatus(status: SessionStatusNotification) = synchronized(lock) {
        running = status.isRunning
        publishLocked()
    }

    fun onEvent(notification: SessionEventNotification) = synchronized(lock) {
        applyLocked(eventMapper.parse(notification))
        publishLocked()
    }

    private fun applyLocked(view: SessionEventView) {
        when (val data = view.data) {
            is UserMessageEvent -> {
                val content = renderContentBlocks(data.content)
                val pendingId = pendingEchos.removeFirstOrNull()
                val idx = if (pendingId != null) rows.indexOfFirst { it.id == pendingId } else -1
                val canonical = UserRow(id = "user-" + data.id, content = content, pending = false)
                if (idx >= 0) rows[idx] = canonical else rows += canonical
            }
            is AssistantChunkEvent -> {
                when (val chunk = data.chunk) {
                    is TextDeltaChunk -> {
                        val r = streamingRow(data.turn, data.step)
                        replaceRow(r.id) { it.copy(text = it.text + chunk.text) }
                    }
                    is ReasoningDeltaChunk -> {
                        val r = streamingRow(data.turn, data.step)
                        replaceRow(r.id) { it.copy(thinking = it.thinking + chunk.text) }
                        lastThinking += chunk.text
                    }
                    is UsageChunk -> {
                        val r = streamingRow(data.turn, data.step)
                        replaceRow(r.id) { it.copy(usage = chunk.usage) }
                    }
                    is FinishChunk -> {
                        closeStreaming()
                        val failure = when (val reason = chunk.reason) {
                            is ErrorFinish -> reason.failure
                            is AbortedFinish -> reason.failure
                            else -> null
                        }
                        if (failure != null) {
                            rows += NoticeRow(
                                id = "notice-${++noticeSeq}",
                                kind = NoticeKind.NOTICE,
                                text = "Assistant stream failed: ${failure.message} (${failure.code})",
                            )
                            maybeAppendNoKeyGuidance(failure.message)
                        }
                    }
                    else -> Unit // block-start / block-end / tool-call-delta / raw: no row effect
                }
            }
            is AssistantMessageEvent -> {
                val existing = streamingAssistantId?.let { id -> rows.firstOrNull { it.id == id } as? AssistantRow }
                val row = AssistantRow(
                    id = existing?.id ?: "assistant-stream-${++streamSeq}",
                    turn = data.turn,
                    step = data.step,
                    text = if (existing != null && existing.text.isNotBlank()) existing.text else renderAssistantText(data.message.content),
                    thinking = if (existing != null && existing.thinking.isNotBlank()) existing.thinking else renderReasoning(data.message.content),
                    status = AssistantStatus.DONE,
                    usage = data.usage ?: existing?.usage,
                    interrupted = data.interrupted == true,
                    messageId = data.message.id,
                )
                if (existing != null) replaceRow(existing.id) { row } else rows += row
                streamingAssistantId = null
            }
            is ToolCallEvent -> {
                rows += ToolCardRow(
                    id = "tool-" + data.callId,
                    callId = data.callId,
                    name = data.name,
                    arguments = data.arguments,
                    turn = data.turn,
                    step = data.step,
                    reasoning = lastThinking,
                )
                // Item 23 (host round 6): the reasoning becomes a STANDALONE
                // card right after the tool card it belongs to.
                if (lastThinking.isNotBlank()) {
                    rows += ReasoningRow("reasoning-" + data.callId, lastThinking)
                }
                lastThinking = ""
            }
            is ToolResultEvent -> {
                val callId = (data.message.source as? ToolSource)?.callId
                    ?: (data.message.content.firstOrNull() as? ToolResultBlock)?.toolCallId
                    ?: "unknown"
                val idx = rows.indexOfFirst { it is ToolCardRow && it.callId == callId }
                val existing = if (idx >= 0) rows[idx] as ToolCardRow else null
                val settled = ToolCardRow(
                    id = existing?.id ?: "tool-" + callId,
                    callId = callId,
                    name = existing?.name ?: "tool",
                    arguments = existing?.arguments ?: "",
                    turn = existing?.turn ?: data.turn,
                    step = existing?.step ?: data.step,
                    status = ToolCardStatus.DONE,
                    resultText = renderContentBlocks(data.message.content),
                    isError = data.error != null ||
                        (data.message.content.firstOrNull() as? ToolResultBlock)?.isError == true,
                    errorName = data.error?.name,
                    errorCode = data.error?.code,
                    meta = data.meta,
                    reasoning = existing?.reasoning ?: "",
                )
                // Item 23 (host round 7): MULTI-CHANGE SPLIT — a result with
                // N>1 diffs becomes N single-change cards (write looks like
                // edit: one header link + stat per card, no link lists).
                val parsed = FsDiffParser.parse(data.meta)
                if (parsed != null && parsed.size > 1) {
                    val cards = parsed.mapIndexed { i, c ->
                        val oneMeta = mapper.createObjectNode().apply {
                            val arr = putArray("diffs")
                            val o = arr.addObject()
                            o.put("path", c.path)
                            if (c.oldText != null) o.put("oldText", c.oldText) else o.putNull("oldText")
                            o.put("newText", c.newText)
                        }
                        settled.copy(id = "tool-" + callId + "-" + i, meta = oneMeta)
                    }
                    if (idx >= 0) {
                        rows[idx] = cards.first()
                        rows.addAll(idx + 1, cards.drop(1))
                    } else {
                        rows += cards
                    }
                } else if (idx >= 0) rows[idx] = settled else rows += settled
            }
            is TurnStartEvent -> {
                closeStreaming()
                rows += NoticeRow("notice-${++noticeSeq}", NoticeKind.TURN_START, "Turn ${data.turn} started")
            }
            is TurnEndEvent -> {
                rows += NoticeRow(
                    "notice-${++noticeSeq}",
                    NoticeKind.TURN_END,
                    "Turn ${data.turn} ended — ${renderTurnEndReason(data.reason)}",
                )
                (data.reason as? ErrorEnd)?.error?.let { maybeAppendNoKeyGuidance(it.message) }
            }
            is StepStartEvent -> rows += NoticeRow(
                "notice-${++noticeSeq}",
                NoticeKind.STEP,
                "Step ${data.step} · turn ${data.turn}",
            )
            is StepEndEvent -> Unit
            is PlanModeEvent -> {
                planMode = data.active
                rows += NoticeRow(
                    "notice-${++noticeSeq}",
                    NoticeKind.PLAN_MODE,
                    if (data.active) "Plan mode enabled" else "Plan mode disabled",
                )
            }
            is ApprovalAskedEvent -> rows += NoticeRow(
                "notice-${++noticeSeq}",
                NoticeKind.APPROVAL_ASKED,
                "Approval requested: ${data.toolName}${data.reason?.let { " — $it" } ?: ""}",
            )
            is ApprovalDecidedEvent -> rows += NoticeRow(
                "notice-${++noticeSeq}",
                NoticeKind.APPROVAL_DECIDED,
                "Approval ${data.id}: ${data.outcome}",
            )
            is TodoWriteEvent -> todos = data.todos
            is CommandRunEvent -> rows += NoticeRow(
                "notice-${++noticeSeq}",
                NoticeKind.NOTICE,
                "Command /${data.name} running",
            )
            is CommandDoneEvent -> rows += NoticeRow(
                "notice-${++noticeSeq}",
                NoticeKind.NOTICE,
                "Command ${data.commandId} ${data.kind}${data.text?.let { ": $it" } ?: ""}",
            )
            is UnknownEventData, is MalformedEventData -> Unit // tolerated and ignored
        }
    }

    private fun streamingRow(turn: Long, step: Long): AssistantRow {
        val existing = streamingAssistantId?.let { id -> rows.firstOrNull { it.id == id } as? AssistantRow }
        if (existing != null) return existing
        val row = AssistantRow(id = "assistant-stream-${++streamSeq}", turn = turn, step = step)
        rows += row
        streamingAssistantId = row.id
        return row
    }

    private fun closeStreaming() {
        val id = streamingAssistantId ?: return
        replaceRow(id) { it.copy(status = AssistantStatus.DONE) }
        streamingAssistantId = null
    }

    /** One-shot actionable guidance after a no-API-key failure (roadmap item 10 ask-flow). */
    private fun maybeAppendNoKeyGuidance(message: String) {
        if (noKeyGuidanceShown || !isNoKeyFailure(message)) return
        noKeyGuidanceShown = true
        rows += NoticeRow(
            id = "notice-${++noticeSeq}",
            kind = NoticeKind.API_KEY_MISSING,
            text = "Enter your API key in Settings → Tools → DeepSeek Harness, " +
                "or put DEEPSEEK_API_KEY in the checkout's .env",
        )
    }

    private fun replaceRow(id: String, transform: (AssistantRow) -> AssistantRow) {
        val idx = rows.indexOfFirst { it.id == id }
        if (idx >= 0) rows[idx] = transform(rows[idx] as AssistantRow)
    }

    private fun publishLocked() {
        val snapshot = TranscriptState(rows.toList(), todos, planMode, running)
        state = snapshot
        listeners.forEach { it(snapshot) }
    }
}

/** Whether a failure message means the DeepSeek API key is missing. */
fun isNoKeyFailure(message: String): Boolean =
    message.lowercase().contains("no api key")

/** Renders message content blocks to plain text (item 5 renders no markdown). */
fun renderContentBlocks(blocks: List<ContentBlock>): String =
    blocks.joinToString("\n\n") { block ->
        when (block) {
            is TextBlock -> block.text
            is ReasoningBlock -> block.text
            is ImageBlock -> "[image]"
            is ToolCallBlock -> "[tool-call ${block.name}]"
            is ToolResultBlock -> renderContentBlocks(block.content).ifBlank { "[tool-result]" }
            is RawContentBlock -> "[unknown block]"
        }
    }

/** Visible text of an assembled assistant message: text blocks only (reasoning is disclosed separately). */
fun renderAssistantText(blocks: List<ContentBlock>): String =
    blocks.filterIsInstance<TextBlock>().joinToString("\n\n") { it.text }

/** Reasoning text of an assembled assistant message. */
fun renderReasoning(blocks: List<ContentBlock>): String =
    blocks.filterIsInstance<ReasoningBlock>().joinToString("\n\n") { it.text }

/** Human-readable form of a turn-end reason for notice rows. */
fun renderTurnEndReason(reason: TurnEndReason): String = when (reason) {
    is CompletedEnd -> "completed"
    is AbortedEnd -> "aborted (${renderCancelCause(reason.reason)})"
    is BlockedEnd -> "blocked"
    is ErrorEnd -> "error: ${reason.error.message}"
    is MaxTokensEnd -> "max tokens"
    is InterruptedEnd -> "interrupted"
    is RawTurnEndReason -> "unknown"
}

/** Human-readable form of a cancellation cause. */
fun renderCancelCause(cause: TurnEndCancelCause): String = when (cause) {
    is UserCancel -> "user"
    is ParentCancel -> "parent"
    is HookCancel -> "hook: ${cause.reason}"
    is DisposedCancel -> "disposed"
    is LegacyCancel -> "legacy"
    is RawCancelCause -> "unknown"
}
