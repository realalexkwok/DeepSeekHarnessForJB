package io.dsh.jb.events

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.dsh.jb.protocol.SessionEventEnvelope
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SurfaceOp

/**
 * One typed view over a raw [SessionEventEnvelope]: the envelope metadata plus
 * the payload mapped into the sealed [EventData] hierarchy. Unknown payload
 * shapes degrade to [UnknownEventData] / [MalformedEventData] instead of
 * throwing, so the UI can render or skip anything the harness emits.
 */
data class SessionEventView(
    val type: String,
    val seq: Long,
    val time: Long,
    val data: EventData,
    val ignorable: Boolean? = null,
    val sourceEventSeqs: List<Long>? = null,
    val surfaceOp: SurfaceOp? = null,
) {
    /** The modeled vocabulary entry for [type], or null for an unknown/plugin type. */
    val eventType: SessionEventType? get() = SessionEventType.fromWire(type)

    /** True when the payload mapped into a modeled event type. */
    val isKnown: Boolean get() = data !is UnknownEventData && data !is MalformedEventData

    /** True when [type] is outside the modeled vocabulary; [data] is [UnknownEventData]. */
    val isUnknown: Boolean get() = data is UnknownEventData

    /** True when [type] is modeled but the payload failed to bind; [data] is [MalformedEventData]. */
    val isMalformed: Boolean get() = data is MalformedEventData
}

/**
 * Pure-JVM mapping layer from raw session-event envelopes to typed
 * [SessionEventView]s — no IntelliJ dependencies, so it is unit-testable
 * headlessly (roadmap item 4; the item 5 chat UI consumes these views).
 */
class EventMapper(private val mapper: ObjectMapper = jacksonObjectMapper()) {

    fun parse(notification: SessionEventNotification): SessionEventView = parse(notification.event)

    fun parse(envelope: SessionEventEnvelope): SessionEventView = SessionEventView(
        type = envelope.type,
        seq = envelope.seq,
        time = envelope.time,
        data = parseData(envelope.type, envelope.data),
        ignorable = envelope.ignorable,
        sourceEventSeqs = envelope.sourceEventSeqs,
        surfaceOp = envelope.surfaceOp,
    )

    /**
     * Maps one envelope `data` node by event type. Unknown types stay raw
     * ([UnknownEventData]); a modeled type whose payload fails to bind keeps
     * the raw node plus the binding error ([MalformedEventData]).
     */
    fun parseData(type: String, data: JsonNode): EventData = try {
        when (type) {
            "assistant/chunk" -> mapper.treeToValue(data, AssistantChunkEvent::class.java)
            "assistant/message" -> mapper.treeToValue(data, AssistantMessageEvent::class.java)
            "tool/call" -> mapper.treeToValue(data, ToolCallEvent::class.java)
            "tool/result" -> mapper.treeToValue(data, ToolResultEvent::class.java)
            "turn/start" -> mapper.treeToValue(data, TurnStartEvent::class.java)
            "turn/end" -> mapper.treeToValue(data, TurnEndEvent::class.java)
            "step/start" -> mapper.treeToValue(data, StepStartEvent::class.java)
            "step/end" -> mapper.treeToValue(data, StepEndEvent::class.java)
            "plan/mode" -> mapper.treeToValue(data, PlanModeEvent::class.java)
            "approval/asked" -> mapper.treeToValue(data, ApprovalAskedEvent::class.java)
            "approval/decided" -> mapper.treeToValue(data, ApprovalDecidedEvent::class.java)
            "todo/write" -> mapper.treeToValue(data, TodoWriteEvent::class.java)
            "user/message" -> mapper.treeToValue(data, UserMessageEvent::class.java)
            "command/run" -> mapper.treeToValue(data, CommandRunEvent::class.java)
            "command/done" -> mapper.treeToValue(data, CommandDoneEvent::class.java)
            else -> UnknownEventData(data)
        }
    } catch (e: Exception) {
        MalformedEventData(data, e.message ?: e.javaClass.simpleName)
    }
}
