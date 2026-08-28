package io.dsh.jb.chat

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.dsh.jb.events.CompletedEnd
import io.dsh.jb.events.MaxTokensEnd
import io.dsh.jb.events.RawTurnEndReason
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionStatusNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap item 5: fixture-driven tests of the transcript fold. Each event is a
 * realistic session-log payload (same shapes as EventModelTest); the model is
 * pure JVM and runs without the IntelliJ platform sandbox.
 */
class ChatTranscriptModelTest {

    private val mapper = jacksonObjectMapper()
    private var seq = 0L

    private fun event(type: String, dataJson: String): SessionEventNotification =
        mapper.readValue(
            """{"sessionId":"s","event":{"type":"$type","seq":${++seq},"time":1,"data":$dataJson}}""",
            SessionEventNotification::class.java,
        )

    private fun status(status: String): SessionStatusNotification =
        mapper.readValue("""{"sessionId":"s","status":"$status"}""", SessionStatusNotification::class.java)

    @Test
    fun `streams text and thinking deltas into one assistant row`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"Hel"}}"""))
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"lo"}}"""))
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"reasoning-delta","index":1,"text":"think"}}"""))
        val rows = m.state().rows
        assertEquals(1, rows.size)
        val row = rows.single() as AssistantRow
        assertEquals("Hello", row.text)
        assertEquals("think", row.thinking)
        assertEquals(AssistantStatus.STREAMING, row.status)
        assertEquals(1L, row.turn)
    }

    @Test
    fun `assistant message finalizes the streaming row without clobbering text`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"Hello"}}"""))
        m.onEvent(event("assistant/message", """{"turn":1,"step":1,"message":{"id":"m1","role":"assistant","content":[{"type":"text","text":"Hello"}],"source":{"kind":"model","provider":"p","model":"m"}},"usage":{"inputTokens":3,"outputTokens":1},"interrupted":true}"""))
        val row = m.state().rows.single() as AssistantRow
        assertEquals(AssistantStatus.DONE, row.status)
        assertEquals("Hello", row.text)
        assertEquals("m1", row.messageId)
        assertEquals(3L, row.usage?.inputTokens)
        assertTrue(row.interrupted)
    }

    @Test
    fun `assistant message without prior chunks creates a done row`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("assistant/message", """{"turn":2,"step":1,"message":{"id":"m2","role":"assistant","content":[{"type":"text","text":"hi"},{"type":"reasoning","text":"why"}],"source":{"kind":"model","provider":"p","model":"m"}}}"""))
        val row = m.state().rows.single() as AssistantRow
        assertEquals(AssistantStatus.DONE, row.status)
        assertEquals("hi", row.text)
        assertEquals("why", row.thinking)
    }

    @Test
    fun `optimistic echoes are replaced fifo by canonical user messages`() {
        val m = ChatTranscriptModel()
        m.echoPrompt("first")
        m.echoPrompt("second")
        m.onEvent(event("user/message", """{"id":"u1","role":"user","content":[{"type":"text","text":"canonical first"}],"source":{"kind":"user"}}"""))
        val rows = m.state().rows
        assertEquals(2, rows.size)
        assertTrue(rows[0] is UserRow && !(rows[0] as UserRow).pending && rows[0].id == "user-u1")
        assertTrue(rows[1] is UserRow && (rows[1] as UserRow).pending)
        m.onEvent(event("user/message", """{"id":"u2","role":"user","content":[{"type":"text","text":"canonical second"}],"source":{"kind":"user"}}"""))
        val after = m.state().rows
        assertEquals(2, after.size)
        assertTrue(after.all { it is UserRow && !it.pending })
        assertEquals("canonical first", (after[0] as UserRow).content)
        assertEquals("canonical second", (after[1] as UserRow).content)
    }

    @Test
    fun `tool card pairs call and result with error and meta`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("tool/call", """{"turn":1,"step":1,"callId":"c1","name":"bash","arguments":"{\"command\":\"ls\"}"}"""))
        val running = m.state().rows.single() as ToolCardRow
        assertEquals(ToolCardStatus.RUNNING, running.status)
        assertEquals("bash", running.name)
        m.onEvent(event("tool/result", """{"turn":1,"step":1,"message":{"id":"r1","role":"user","content":[{"type":"tool-result","toolCallId":"c1","content":[{"type":"text","text":"out"}],"isError":true}],"source":{"kind":"tool","callId":"c1"}},"error":{"name":"ExitError","code":"1"},"meta":{"exitCode":1}}"""))
        val done = m.state().rows.single() as ToolCardRow
        assertEquals(ToolCardStatus.DONE, done.status)
        assertEquals("out", done.resultText)
        assertTrue(done.isError)
        assertEquals("ExitError", done.errorName)
        assertEquals("1", done.errorCode)
        assertNotNull(done.meta)
        assertEquals(1, done.meta?.path("exitCode")?.asInt())
    }

    @Test
    fun `tool result without preceding call creates a settled card`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("tool/result", """{"turn":1,"step":1,"message":{"id":"r2","role":"user","content":[{"type":"tool-result","toolCallId":"c9","content":[{"type":"text","text":"late"}],"isError":false}],"source":{"kind":"tool","callId":"c9"}}}"""))
        val card = m.state().rows.single() as ToolCardRow
        assertEquals(ToolCardStatus.DONE, card.status)
        assertEquals("c9", card.callId)
        assertEquals("late", card.resultText)
    }

    @Test
    fun `turn and step boundaries and plan mode produce notices`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("turn/start", """{"turn":1}"""))
        m.onEvent(event("step/start", """{"turn":1,"step":1}"""))
        m.onEvent(event("plan/mode", """{"active":true}"""))
        m.onEvent(event("turn/end", """{"turn":1,"reason":{"kind":"completed"}}"""))
        val state = m.state()
        assertTrue(state.planMode)
        val kinds = state.rows.filterIsInstance<NoticeRow>().map { it.kind }
        assertEquals(listOf(NoticeKind.TURN_START, NoticeKind.STEP, NoticeKind.PLAN_MODE, NoticeKind.TURN_END), kinds)
        assertTrue((state.rows.last() as NoticeRow).text.contains("completed"))
    }

    @Test
    fun `approval audit rows render asked and decided`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("approval/asked", """{"id":"a1","toolName":"bash","reason":"runs a command"}"""))
        m.onEvent(event("approval/decided", """{"id":"a1","outcome":"allowed-once"}"""))
        val notices = m.state().rows.filterIsInstance<NoticeRow>()
        assertEquals(2, notices.size)
        assertEquals(NoticeKind.APPROVAL_ASKED, notices[0].kind)
        assertEquals(NoticeKind.APPROVAL_DECIDED, notices[1].kind)
        assertTrue(notices[1].text.contains("allowed-once"))
    }

    @Test
    fun `todo snapshot replaces and is published`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("todo/write", """{"todos":[{"content":"a","status":"pending"}]}"""))
        assertEquals(1, m.state().todos.size)
        m.onEvent(event("todo/write", """{"todos":[{"content":"a","status":"completed"},{"content":"b","status":"in_progress"}]}"""))
        assertEquals(2, m.state().todos.size)
        assertEquals("completed", m.state().todos[0].status)
    }

    @Test
    fun `finish with error appends a failure notice`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"x"}}"""))
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"finish","reason":{"kind":"error","failure":{"message":"boom","code":"PROVIDER"}}}}"""))
        val state = m.state()
        val row = state.rows.first() as AssistantRow
        assertEquals(AssistantStatus.DONE, row.status)
        assertTrue(state.rows.any { it is NoticeRow && it.text.contains("boom") })
    }

    @Test
    fun `unknown and malformed events are tolerated`() {
        val m = ChatTranscriptModel()
        m.echoPrompt("keep me")
        m.onEvent(event("sandbox/mode", """{"mode":"workspace-write"}"""))
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":42}"""))
        val rows = m.state().rows
        assertEquals(1, rows.size)
        assertTrue(rows.single() is UserRow)
    }

    @Test
    fun `status notifications flip the running flag`() {
        val m = ChatTranscriptModel()
        assertFalse(m.state().running)
        m.onStatus(status("running"))
        assertTrue(m.state().running)
        m.onStatus(status("idle"))
        assertFalse(m.state().running)
    }

    @Test
    fun `turn start force closes a dangling streaming row`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"text-delta","index":0,"text":"x"}}"""))
        m.onEvent(event("turn/start", """{"turn":2}"""))
        val assistant = m.state().rows.filterIsInstance<AssistantRow>().single()
        assertEquals(AssistantStatus.DONE, assistant.status)
    }

    @Test
    fun `listeners receive the current snapshot on subscribe and on changes`() {
        val m = ChatTranscriptModel()
        val seen = mutableListOf<TranscriptState>()
        m.addListener { seen += it }
        assertEquals(1, seen.size)
        m.echoPrompt("hi")
        assertEquals(2, seen.size)
        assertEquals(1, seen.last().rows.size)
    }

    @Test
    fun `notice api appends a local notice row`() {
        val m = ChatTranscriptModel()
        m.notice("Send failed: boom")
        val row = m.state().rows.single() as NoticeRow
        assertEquals(NoticeKind.NOTICE, row.kind)
        assertEquals("Send failed: boom", row.text)
    }

    @Test
    fun `render helpers cover turn end reasons`() {
        assertEquals("completed", renderTurnEndReason(CompletedEnd()))
        assertEquals("max tokens", renderTurnEndReason(MaxTokensEnd()))
        assertEquals("unknown", renderTurnEndReason(RawTurnEndReason()))
    }

    @Test
    fun `no api key finish appends guidance exactly once`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"finish","reason":{"kind":"error","failure":{"message":"llm-deepseek: no API key for provider route","code":"NO_KEY"}}}}"""))
        val guidance = m.state().rows.filterIsInstance<NoticeRow>().filter { it.kind == NoticeKind.API_KEY_MISSING }
        assertEquals(1, guidance.size)
        assertTrue(guidance[0].text.contains("Settings"))
        m.onEvent(event("assistant/chunk", """{"turn":2,"step":1,"chunk":{"type":"finish","reason":{"kind":"error","failure":{"message":"no API key for provider route","code":"NO_KEY"}}}}"""))
        assertEquals(1, m.state().rows.filterIsInstance<NoticeRow>().count { it.kind == NoticeKind.API_KEY_MISSING })
    }

    @Test
    fun `no api key turn end appends guidance`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("turn/end", """{"turn":1,"reason":{"kind":"error","error":{"message":"llm-deepseek: no API key for provider route","code":"NO_KEY"}}}"""))
        assertTrue(m.state().rows.any { it is NoticeRow && it.kind == NoticeKind.API_KEY_MISSING })
    }

    @Test
    fun `other failures do not append key guidance`() {
        val m = ChatTranscriptModel()
        m.onEvent(event("assistant/chunk", """{"turn":1,"step":1,"chunk":{"type":"finish","reason":{"kind":"error","failure":{"message":"timeout","code":"TIMEOUT"}}}}"""))
        assertTrue(m.state().rows.none { it is NoticeRow && it.kind == NoticeKind.API_KEY_MISSING })
    }
}
