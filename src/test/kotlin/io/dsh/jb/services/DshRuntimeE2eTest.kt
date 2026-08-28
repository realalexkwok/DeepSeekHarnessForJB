package io.dsh.jb.services

import com.sun.net.httpserver.HttpServer
import io.dsh.jb.chat.AssistantRow
import io.dsh.jb.chat.AssistantStatus
import io.dsh.jb.chat.ChatTranscriptModel
import io.dsh.jb.chat.NoticeKind
import io.dsh.jb.chat.NoticeRow
import io.dsh.jb.chat.UserRow
import io.dsh.jb.diff.FsDiffParser
import io.dsh.jb.events.AssistantChunkEvent
import io.dsh.jb.events.AssistantMessageEvent
import io.dsh.jb.events.EventMapper
import io.dsh.jb.events.MalformedEventData
import io.dsh.jb.events.StepStartEvent
import io.dsh.jb.events.TurnEndEvent
import io.dsh.jb.events.ToolResultEvent
import io.dsh.jb.events.TurnStartEvent
import io.dsh.jb.events.UserMessageEvent
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionStatusNotification
import io.dsh.jb.runtime.DshRuntimeClient
import io.dsh.jb.runtime.DshRuntimeConfig
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Headless end-to-end test: the real DSH runtime from the checkout, driven by
 * [DshRuntimeClient], against a JDK-hosted mock LLM — no API key needed.
 * Mirrors the harness repo's own keyless smoke
 * (`apps/cli/tests/profiles/sdk/keyless-smoke.e2e.ts`, dsh-v0.1.2-alpha.1+).
 */
class DshRuntimeE2eTest {

    private val checkout = System.getenv("DSH_CHECKOUT") ?: "/home/superguo/Projects/deepseek-harness"

    @Test
    fun `initialize prompt and shutdown against checkout runtime with mock llm`() = runBlocking {
        // dsh-v0.1.2-alpha.1+: the SDK runtime is the main CLI's built-in `sdk`
        // profile (source checkout runs via the checkout's tsx loader).
        val bin = File(checkout, "apps/cli/src/bin.ts")
        assumeTrue("DSH checkout runtime not available at $checkout", bin.isFile)

        val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
        val modelServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        modelServer.createContext("/") { exchange ->
            // The DeepSeek adapter streams; answer with the same SSE shape the
            // harness's own keyless smoke uses. The mock is STATEFUL (roadmap item 7):
            // request 1 emits a read tool call (the fs observation policy requires a
            // prior read), request 2 emits a write call, later requests emit text.
            // Connection: close prevents keep-alive sockets from hanging
            // HttpServer.stop(0) (JDK quirk).
            val n = requestCount.incrementAndGet()
            val headers = exchange.responseHeaders
            headers.add("Content-Type", "text/event-stream")
            headers.add("Connection", "close")
            exchange.sendResponseHeaders(200, 0)
            val out = exchange.responseBody
            fun sse(data: String) {
                out.write("data: $data\n\n".toByteArray(Charsets.UTF_8))
            }
            when (n) {
                1 -> {
                    sse("""{"choices":[{"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read","arguments":""}}]}}]}""")
                    sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"file_path\":\"hello.txt\"}"}}]}}]}""")
                    sse("""{"choices":[{"delta":{"content":""},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}""")
                }
                2 -> {
                    sse("""{"choices":[{"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_2","type":"function","function":{"name":"write","arguments":""}}]}}]}""")
                    sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"file_path\":\"hello.txt\",\"content\":\"new content\"}"}}]}}]}""")
                    sse("""{"choices":[{"delta":{"content":""},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":5,"completion_tokens":3}}""")
                }
                else -> {
                    sse("""{"choices":[{"delta":{"role":"assistant","content":null}}]}""")
                    sse("""{"choices":[{"delta":{"content":"done"}}]}""")
                    sse("""{"choices":[{"delta":{},"finish_reason":"length"}],"usage":{"prompt_tokens":3,"completion_tokens":1}}""")
                }
            }
            out.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
            out.close()
            exchange.close()
        }
        modelServer.start()
        val port = (modelServer.address as InetSocketAddress).port
        val root = createTempDirectory("dsh-jb-e2e").toFile()
        val stderrLines = java.util.Collections.synchronizedList(mutableListOf<String>())

        val client = DshRuntimeClient(
            DshRuntimeConfig(
                mode = "node",
                checkoutPath = checkout,
                apiKey = "keyless-e2e-no-call",
                baseUrl = "http://127.0.0.1:$port",
                provider = "deepseek-official",
                model = "deepseek-v4-pro",
                reasoningEffort = "max",
                harnessHome = File(root, ".dsh").path,
                permissionMode = "danger-full-access",
                cwd = root.path,
                sessionId = "e2e-main",
            ),
            stderrSink = { stderrLines += it },
        )
        try {
            val events = CopyOnWriteArrayList<SessionEventNotification>()
            val statuses = CopyOnWriteArrayList<SessionStatusNotification>()
            client.addEventListener(events::add)
            client.addStatusListener(statuses::add)

            val init = client.start()
            assertEquals("deepseek-harness-sdk-runtime", init.serverInfo.name)

            // Roadmap item 7: seed the workspace file the mock tool calls operate on.
            val hello = File(root, "hello.txt")
            hello.writeText("old content\n")

            val prompt = client.prompt("update hello.txt")
            assertTrue(prompt.messageId.isNotBlank())

            // Wait for the whole-agent idle status plus the committed assistant message.
            withTimeout(90_000) {
                while (true) {
                    if (statuses.any { it.isIdle } &&
                        events.any { it.event.type == "assistant/message" }
                    ) break
                    delay(100)
                }
            }
            assertTrue("expected a turn/end event", events.any { it.event.type == "turn/end" })

            // Roadmap item 4: the typed event model must parse the REAL runtime stream.
            val eventMapper = EventMapper()
            val views = events.map { eventMapper.parse(it) }
            assertTrue("expected typed assistant chunks", views.any { it.data is AssistantChunkEvent })
            assertTrue(
                "expected a typed assistant message with content",
                views.any { (it.data as? AssistantMessageEvent)?.message?.content?.isNotEmpty() == true },
            )
            assertTrue(
                "expected typed turn boundaries",
                views.any { it.data is TurnStartEvent } && views.any { it.data is TurnEndEvent },
            )
            assertTrue("expected typed step start", views.any { it.data is StepStartEvent })
            assertTrue("expected typed user message", views.any { it.data is UserMessageEvent })
            val malformed = views.filter { it.data is MalformedEventData }
            assertTrue(
                "no event payload may fail to bind; errors: " +
                    malformed.map { (it.data as MalformedEventData).error },
                malformed.isEmpty(),
            )
            val assistantView = views.first { it.data is AssistantMessageEvent }
            assertNotNull("assistant/message should carry surface metadata", assistantView.surfaceOp)

            // Roadmap item 5: the transcript model must fold the REAL runtime stream.
            val transcript = ChatTranscriptModel()
            events.forEach { transcript.onEvent(it) }
            statuses.forEach { transcript.onStatus(it) }
            val ts = transcript.state()
            assertTrue("expected canonical user row", ts.rows.any { it is UserRow && !it.pending })
            val assistants = ts.rows.filterIsInstance<AssistantRow>()
            assertTrue("expected completed assistant row", assistants.isNotEmpty())
            assertTrue("assistant rows should be done", assistants.all { it.status == AssistantStatus.DONE })
            assertTrue("assistant text should carry the mock output", assistants.any { it.text.contains("done") })
            assertTrue("expected turn notices", ts.rows.any { it is NoticeRow && it.kind == NoticeKind.TURN_START })
            assertTrue("agent should be idle at the end", !ts.running)

            // Roadmap item 7: the REAL fs tool result must carry a parsable diff meta.
            val diffs = views
                .filter { it.data is ToolResultEvent }
                .mapNotNull { FsDiffParser.parse((it.data as ToolResultEvent).meta) }
                .flatten()
            assertTrue(
                "expected a diff for hello.txt, got: $diffs",
                diffs.any { it.path == "hello.txt" && it.oldText == "old content" && it.newText == "new content" },
            )
            assertEquals("new content", hello.readText().trim())

            client.shutdown()
        } catch (t: Throwable) {
            System.err.println("--- DSH runtime stderr (last 40 lines) ---")
            stderrLines.toList().takeLast(40).forEach(System.err::println)
            throw t
        } finally {
            client.close()
            modelServer.stop(0)
            root.deleteRecursively()
        }
    }
}
