package io.dsh.jb.events

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.dsh.jb.protocol.SessionEventEnvelope
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SurfaceOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap item 4: typed mapping of the session-event vocabulary. Fixtures
 * mirror the payload shapes in the harness checkout's persistence catalog
 * (`docs/persistence-catalog.md` and the `types.ts` sources it cites).
 */
class EventModelTest {

    private val mapper = jacksonObjectMapper()
    private val events = EventMapper(mapper)

    /** Parses one envelope; `prefix` injects extra envelope fields before `data`. */
    private fun view(type: String, dataJson: String, prefix: String = ""): SessionEventView {
        val envelope = mapper.readValue(
            """{"type":"$type","seq":7,"time":1720000000000,$prefix"data":$dataJson}""",
            SessionEventEnvelope::class.java,
        )
        return events.parse(envelope)
    }

    // --- assistant/chunk: all seven StreamChunk variants -----------------

    @Test
    fun `maps text delta chunk`() {
        val d = view("assistant/chunk", """{"turn":1,"step":2,"chunk":{"type":"text-delta","index":0,"text":"done"}}""").data as AssistantChunkEvent
        assertEquals(1L, d.turn)
        assertEquals(2L, d.step)
        val c = d.chunk as TextDeltaChunk
        assertEquals(0L, c.index)
        assertEquals("done", c.text)
    }

    @Test
    fun `maps reasoning delta chunk`() {
        val c = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"reasoning-delta","index":1,"text":"think"}}""").data
        assertEquals(ReasoningDeltaChunk(1L, "think"), (c as AssistantChunkEvent).chunk)
    }

    @Test
    fun `maps tool call delta chunk with optional name`() {
        val c = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"tool-call-delta","index":2,"id":"c1","name":"read","argumentsDelta":"{\"path\":"}}""").data
        assertEquals(ToolCallDeltaChunk(2L, "c1", "read", """{"path":"""), (c as AssistantChunkEvent).chunk)
    }

    @Test
    fun `maps block start chunk`() {
        val c = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"block-start","index":0,"blockType":"text"}}""").data
        assertEquals(BlockStartChunk(0L, "text"), (c as AssistantChunkEvent).chunk)
    }

    @Test
    fun `maps block end chunk with assembled block`() {
        val c = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"block-end","index":1,"block":{"type":"tool-call","id":"c1","name":"read","arguments":"{}"}}}""").data
        assertEquals(BlockEndChunk(1L, ToolCallBlock("c1", "read", "{}")), (c as AssistantChunkEvent).chunk)
    }

    @Test
    fun `maps usage chunk with cache and reasoning counts`() {
        val c = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"usage","usage":{"inputTokens":3,"outputTokens":1,"cacheReadTokens":2,"cacheWriteTokens":0,"reasoningTokens":4}}}""").data
        assertEquals(UsageChunk(TokenUsage(3L, 1L, 2L, 0L, 4L)), (c as AssistantChunkEvent).chunk)
    }

    @Test
    fun `maps finish chunk with stop reason`() {
        val c = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"finish","reason":{"kind":"stop"}}}""").data
        val finish = (c as AssistantChunkEvent).chunk as FinishChunk
        assertTrue(finish.reason is StopFinish)
    }

    @Test
    fun `maps finish chunk with aborted failure facts`() {
        val c = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"finish","reason":{"kind":"aborted","failure":{"message":"timeout","code":"TIMEOUT","status":408,"providerRetryAfterMs":500,"requestId":"r1"}}}}""").data
        val finish = (c as AssistantChunkEvent).chunk as FinishChunk
        assertEquals(AbortedFinish(LlmFailure("timeout", "TIMEOUT", 408, 500L, "r1")), finish.reason)
    }

    // --- assistant/message, tool/call, tool/result -----------------------

    @Test
    fun `maps assistant message with content, source, usage and interruption`() {
        val d = view("assistant/message", """{"turn":1,"step":1,"message":{"id":"msg-1","role":"assistant","content":[{"type":"text","text":"done"},{"type":"tool-call","id":"call-1","name":"read","arguments":"{\"path\":\"a\"}"}],"source":{"kind":"model","provider":"deepseek-official","model":"deepseek-chat"}},"usage":{"inputTokens":3,"outputTokens":1},"interrupted":true}""").data as AssistantMessageEvent
        assertEquals(1L, d.turn)
        assertEquals("assistant", d.message.role)
        assertEquals(TextBlock("done"), d.message.content[0])
        assertEquals(ToolCallBlock("call-1", "read", """{"path":"a"}"""), d.message.content[1])
        assertEquals(ModelSource("deepseek-official", "deepseek-chat"), d.message.source)
        assertEquals(TokenUsage(3L, 1L), d.usage)
        assertEquals(true, d.interrupted)
    }

    @Test
    fun `maps tool call with raw unparsed arguments`() {
        val d = view("tool/call", """{"turn":1,"step":2,"callId":"call-9","name":"bash","arguments":"{\"command\":\"ls\"}"}""").data as ToolCallEvent
        assertEquals(ToolCallEvent(1L, 2L, "call-9", "bash", """{"command":"ls"}"""), d)
    }

    @Test
    fun `maps tool result with error identity and opaque meta`() {
        val d = view("tool/result", """{"turn":1,"step":2,"message":{"id":"msg-2","role":"user","content":[{"type":"tool-result","toolCallId":"call-1","content":[{"type":"text","text":"42"}],"isError":false}],"source":{"kind":"tool","callId":"call-1"}},"error":{"name":"FsError","code":"EACCES"},"meta":{"diff":"@@ -1 +1 @@"}}""").data as ToolResultEvent
        assertEquals("user", d.message.role)
        assertEquals(ToolSource("call-1"), d.message.source)
        val block = d.message.content.single() as ToolResultBlock
        assertEquals("call-1", block.toolCallId)
        assertEquals(listOf(TextBlock("42")), block.content)
        assertEquals(false, block.isError)
        assertEquals(ToolError("FsError", "EACCES"), d.error)
        assertEquals("@@ -1 +1 @@", d.meta?.path("diff")?.asText())
    }

    // --- turn/step boundaries -------------------------------------------

    @Test
    fun `maps turn and step boundaries`() {
        assertEquals(TurnStartEvent(3L), view("turn/start", """{"turn":3}""").data)
        assertEquals(StepStartEvent(3L, 1L), view("step/start", """{"turn":3,"step":1}""").data)
        assertEquals(StepEndEvent(3L, 1L), view("step/end", """{"turn":3,"step":1}""").data)
    }

    @Test
    fun `maps turn end completed`() {
        val d = view("turn/end", """{"turn":1,"reason":{"kind":"completed"}}""").data as TurnEndEvent
        assertEquals(1L, d.turn)
        assertTrue(d.reason is CompletedEnd)
    }

    @Test
    fun `maps turn end aborted by hook`() {
        val d = view("turn/end", """{"turn":2,"reason":{"kind":"aborted","reason":{"kind":"hook","reason":"denied by policy"}}}""").data as TurnEndEvent
        val cause = (d.reason as AbortedEnd).reason
        assertEquals(HookCancel("denied by policy"), cause)
    }

    @Test
    fun `maps turn end error with structured failure`() {
        val d = view("turn/end", """{"turn":3,"reason":{"kind":"error","error":{"message":"boom","code":"UNKNOWN"}}}""").data as TurnEndEvent
        assertEquals(ErrorEnd(LlmFailure("boom", "UNKNOWN")), d.reason)
    }

    // --- plan mode, approvals, todos --------------------------------------

    @Test
    fun `maps plan mode`() {
        assertEquals(PlanModeEvent(true), view("plan/mode", """{"active":true}""").data)
    }

    @Test
    fun `maps approval asked with optional call id and reason`() {
        val d = view("approval/asked", """{"id":"a1","toolName":"bash","callId":"call-1","reason":"runs a command"}""").data as ApprovalAskedEvent
        assertEquals("a1", d.id)
        assertEquals("bash", d.toolName)
        assertEquals("call-1", d.callId)
        assertEquals("runs a command", d.reason)
    }

    @Test
    fun `maps approval decided with closed outcome vocabulary`() {
        val d = view("approval/decided", """{"id":"a1","outcome":"allowed-once"}""").data as ApprovalDecidedEvent
        assertEquals("allowed-once", d.outcome)
        assertEquals(ApprovalOutcome.ALLOWED_ONCE, d.outcomeValue)
    }

    @Test
    fun `maps todo write snapshot with three statuses`() {
        val d = view("todo/write", """{"todos":[{"content":"a","status":"pending"},{"content":"b","status":"in_progress"},{"content":"c","status":"completed"}]}""").data as TodoWriteEvent
        assertEquals(3, d.todos.size)
        assertEquals(TodoStatus.PENDING, d.todos[0].statusValue)
        assertEquals(TodoStatus.IN_PROGRESS, d.todos[1].statusValue)
        assertEquals(TodoStatus.COMPLETED, d.todos[2].statusValue)
    }

    // --- user message and commands -----------------------------------------

    @Test
    fun `maps user message from a human`() {
        val d = view("user/message", """{"id":"m1","role":"user","content":[{"type":"text","text":"hi"}],"source":{"kind":"user"}}""").data as UserMessageEvent
        assertEquals("user", d.role)
        assertEquals(listOf(TextBlock("hi")), d.content)
        assertTrue(d.source is UserSource)
    }

    @Test
    fun `maps user message from a plugin with context form`() {
        val d = view("user/message", """{"id":"m2","role":"user","content":[{"type":"text","text":"notice"}],"source":{"kind":"plugin","plugin":"dsh-agent-instructions","form":"notice","summary":"inbox change"}}""").data as UserMessageEvent
        assertEquals(PluginSource("dsh-agent-instructions", "notice", null, "inbox change"), d.source)
    }

    @Test
    fun `maps command run with user source`() {
        val d = view("command/run", """{"commandId":"c1","name":"plan","args":" the thing","source":{"kind":"user"}}""").data as CommandRunEvent
        assertEquals("c1", d.commandId)
        assertEquals("plan", d.name)
        assertEquals(" the thing", d.args)
        assertTrue(d.source is UserCommandSource)
    }

    @Test
    fun `maps command done with outcome kind and source event seq`() {
        val d = view("command/done", """{"commandId":"c1","kind":"success","text":"ok","sourceEventSeq":9}""").data as CommandDoneEvent
        assertEquals("c1", d.commandId)
        assertEquals("ok", d.text)
        assertEquals(9L, d.sourceEventSeq)
        assertEquals(CommandOutcome.SUCCESS, d.kindValue)
    }

    // --- unknown types and discriminators stay raw -------------------------

    @Test
    fun `unknown event type stays raw json and honors ignorable`() {
        val v = view("sandbox/mode", """{"mode":"workspace-write"}""", prefix = """"ignorable":true,""")
        val d = v.data as UnknownEventData
        assertEquals("workspace-write", d.raw.path("mode").asText())
        assertEquals(true, v.ignorable)
        assertTrue(v.isUnknown)
        assertFalse(v.isKnown)
        assertNull(v.eventType)
    }

    @Test
    fun `unknown chunk type keeps complete raw json including the type id`() {
        val d = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"future/chunk","index":3,"extra":{"a":1}}}""").data as AssistantChunkEvent
        val c = d.chunk as RawStreamChunk
        assertEquals("future/chunk", c.raw.path("type").asText())
        assertEquals(3L, c.raw.path("index").asLong())
        assertEquals(1, c.raw.path("extra").path("a").asInt())
    }

    @Test
    fun `unknown content block type keeps raw json inside block end`() {
        val d = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"block-end","index":0,"block":{"type":"future-block","x":"y"}}}""").data as AssistantChunkEvent
        val block = (d.chunk as BlockEndChunk).block as RawContentBlock
        assertEquals("future-block", block.raw.path("type").asText())
        assertEquals("y", block.raw.path("x").asText())
    }

    @Test
    fun `unknown finish reason kind keeps raw json`() {
        val d = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"finish","reason":{"kind":"future-end","z":1}}}""").data as AssistantChunkEvent
        val reason = (d.chunk as FinishChunk).reason as RawFinishReason
        assertEquals("future-end", reason.raw.path("kind").asText())
    }

    @Test
    fun `unknown turn end kind keeps raw json`() {
        val d = view("turn/end", """{"turn":1,"reason":{"kind":"future-end","z":1}}""").data as TurnEndEvent
        val reason = d.reason as RawTurnEndReason
        assertEquals("future-end", reason.raw.path("kind").asText())
    }

    @Test
    fun `unknown cancellation cause keeps raw json`() {
        val d = view("turn/end", """{"turn":4,"reason":{"kind":"aborted","reason":{"kind":"future-cancel","w":2}}}""").data as TurnEndEvent
        val cause = (d.reason as AbortedEnd).reason as RawCancelCause
        assertEquals("future-cancel", cause.raw.path("kind").asText())
    }

    @Test
    fun `unknown message source kind keeps raw json`() {
        val d = view("user/message", """{"id":"m3","role":"user","content":[{"type":"text","text":"x"}],"source":{"kind":"future-src","q":1}}""").data as UserMessageEvent
        val source = d.source as RawMessageSource
        assertEquals("future-src", source.raw.path("kind").asText())
    }

    // --- malformed payloads degrade instead of throwing ---------------------

    @Test
    fun `malformed payload degrades to MalformedEventData`() {
        val v = view("assistant/chunk", """{"turn":1,"step":1,"chunk":42}""")
        val d = v.data as MalformedEventData
        assertTrue(d.error.isNotBlank())
        assertTrue(d.raw.path("chunk").isInt)
        assertTrue(v.isMalformed)
        assertFalse(v.isKnown)
    }

    // --- envelope surface metadata and notification convenience -------------

    @Test
    fun `surface op append and replace and raw parse from notifications`() {
        val append = mapper.readValue(
            """{"sessionId":"s","event":{"type":"assistant/message","seq":3,"time":1,"data":{"turn":1,"step":1,"message":{"id":"m","role":"assistant","content":[{"type":"text","text":"x"}],"source":{"kind":"model","provider":"p","model":"m"}}},"sourceEventSeqs":[1,2],"surfaceOp":"append"}}""",
            SessionEventNotification::class.java,
        )
        val v1 = events.parse(append)
        assertEquals(SurfaceOp.Append, v1.surfaceOp)
        assertEquals(listOf(1L, 2L), v1.sourceEventSeqs)
        assertTrue(v1.data is AssistantMessageEvent)

        val replace = mapper.readValue(
            """{"sessionId":"s","event":{"type":"assistant/message","seq":4,"time":2,"data":{"turn":1,"step":1,"message":{"id":"m2","role":"assistant","content":[],"source":{"kind":"model","provider":"p","model":"m"}}},"surfaceOp":{"op":"replace","start":1,"end":2}}}""",
            SessionEventNotification::class.java,
        )
        assertEquals(SurfaceOp.Replace(1L, 2L), events.parse(replace).surfaceOp)

        val raw = mapper.readValue(
            """{"sessionId":"s","event":{"type":"assistant/message","seq":5,"time":3,"data":{"turn":1,"step":1,"message":{"id":"m3","role":"assistant","content":[],"source":{"kind":"model","provider":"p","model":"m"}}},"surfaceOp":"future-op"}}""",
            SessionEventNotification::class.java,
        )
        val v3 = events.parse(raw)
        assertTrue(v3.surfaceOp is SurfaceOp.Raw)
        assertEquals("future-op", (v3.surfaceOp as SurfaceOp.Raw).raw.asText())
    }

    @Test
    fun `session event type vocabulary round trips`() {
        assertEquals(SessionEventType.ASSISTANT_CHUNK, SessionEventType.fromWire("assistant/chunk"))
        assertEquals(SessionEventType.COMMAND_DONE, SessionEventType.fromWire("command/done"))
        assertNull(SessionEventType.fromWire("plugin/custom"))
        val v = view("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"x"}}""")
        assertEquals(SessionEventType.ASSISTANT_CHUNK, v.eventType)
        assertNotNull(v.eventType)
    }
}
