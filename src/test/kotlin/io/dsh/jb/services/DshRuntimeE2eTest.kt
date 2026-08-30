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
import io.dsh.jb.runtime.BundledRuntimeResolver
import io.dsh.jb.runtime.DshRuntimeClient
import io.dsh.jb.runtime.DshRuntimeConfig
import io.dsh.jb.util.FsTree
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
import org.junit.Test

/**
 * Headless end-to-end test: the real DSH runtime from the checkout, driven by
 * [DshRuntimeClient], against a JDK-hosted mock LLM — no API key needed.
 * Mirrors the harness repo's own keyless smoke
 * (`apps/cli/tests/profiles/sdk/keyless-smoke.e2e.ts`, dsh-v0.1.2-alpha.1+).
 */
class DshRuntimeE2eTest {

    /**
     * The checkout the e2e may EXECUTE against: a dedicated dev clone at the
     * pinned tag. The ambient checkout (/home/superguo/Projects/deepseek-harness)
     * is the live harness that runs the agent sessions and must NEVER be spawned
     * or mutated by tests (session rules + the 2026-08-29 worker outage).
     */
    private val ambientCheckout = "/home/superguo/Projects/deepseek-harness"
    private val devCheckout = "/home/superguo/Projects/dsh-for-jb-plugin-dev"

    private val checkout: String by lazy {
        val resolved = System.getenv("DSH_CHECKOUT")?.takeIf { it.isNotBlank() } ?: devCheckout
        check(
            File(resolved).canonicalPath != File(ambientCheckout).canonicalPath,
        ) {
            "DSH_CHECKOUT must not point at the ambient checkout $ambientCheckout — " +
                "use the dev clone $devCheckout (the ambient checkout runs this agent and must stay pristine)"
        }
        resolved
    }

    @Test
    fun `initialize prompt and shutdown against checkout runtime with mock llm`() = runBlocking {
        // dsh-v0.1.2-alpha.1+: the SDK runtime is the main CLI's built-in `sdk`
        // profile (source checkout runs via the checkout's tsx loader).
        val bin = File(checkout, "apps/cli/src/bin.ts")
        if (!bin.isFile) {
            throw IllegalStateException("DSH dev checkout has no CLI source at $bin (expected the dsh-v0.1.2-alpha.1 layout)")
        }

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
            // Symlink-safe: DSH_HOME (root/.dsh) contains profile-fallback
            // symlinks into the checkout; deleteRecursively() would follow them.
            FsTree.deleteNoFollow(root)
        }
    }

    /**
     * Roadmap item 8: the full plan gate. The bridge's /commands relay enters
     * plan mode; the mock model then calls exit_plan_mode; the runtime-side
     * answerer forwards the review to the IDE bridge, which auto-approves; the
     * plan lands in the tool result and the model proceeds.
     */
    @Test
    fun `plan mode review gate end to end through the bridge`() = runBlocking {
        val bin = File(checkout, "apps/cli/src/bin.ts")
        if (!bin.isFile) {
            throw IllegalStateException("DSH dev checkout has no CLI source at $bin (expected the dsh-v0.1.2-alpha.1 layout)")
        }

        val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
        val modelServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        modelServer.createContext("/") { exchange ->
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
                    // First turn: plain text — creates the SDK agent (agents are
                    // lazy per session/prompt) so the /plan command relay can
                    // reach it.
                    sse("""{"choices":[{"delta":{"role":"assistant","content":null}}]}""")
                    sse("""{"choices":[{"delta":{"content":"ready"}}]}""")
                    sse("""{"choices":[{"delta":{},"finish_reason":"length"}],"usage":{"prompt_tokens":3,"completion_tokens":1}}""")
                }
                2 -> {
                    sse("""{"choices":[{"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_plan","type":"function","function":{"name":"exit_plan_mode","arguments":""}}]}}]}""")
                    sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"plan\":\"# Test plan\\n\\nDo the things.\"}"}}]}}]}""")
                    sse("""{"choices":[{"delta":{"content":""},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}""")
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
        val root = createTempDirectory("dsh-jb-e2e-plan").toFile()
        val stderrLines = java.util.Collections.synchronizedList(mutableListOf<String>())

        // The IDE bridge: record every question and auto-approve plan reviews.
        val forwarded = java.util.Collections.synchronizedList(mutableListOf<io.dsh.jb.bridge.PlanQuestion>())
        val bridge = io.dsh.jb.bridge.BridgeServer(onQuestions = { questions ->
            questions.map { q ->
                forwarded += q
                io.dsh.jb.bridge.PlanAnswer(q.id, listOf(q.approveLabel ?: q.options.first()), null)
            }
        })
        bridge.start()

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
                bridgeUrl = bridge.url,
                bridgeToken = bridge.token,
                sessionId = "e2e-plan",
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

            // Create the SDK agent with a trivial first turn: agents are created
            // lazily by the first session/prompt, and the /plan relay needs one.
            client.prompt("hello")
            withTimeout(90_000) {
                while (events.none { it.event.type == "assistant/message" } || statuses.none { it.isIdle }) {
                    delay(100)
                }
            }

            // Enter plan mode through the bridge command relay.
            bridge.enqueueCommand("/plan")
            withTimeout(60_000) {
                while (events.none { it.event.type == "plan/mode" && it.event.data.path("active").asBoolean() }) {
                    delay(100)
                }
            }

            val prompt = client.prompt("plan the thing")
            assertTrue(prompt.messageId.isNotBlank())

            // Wait for the SPECIFIC continuation message. A count>=2 condition
            // races: it breaks on the tool-call message (#2) before the approved
            // continuation text (#3) lands, and statuses.any{isIdle} is already
            // true since turn 1 ended (observed 2026-08-29 plan-mode e2e failure).
            withTimeout(90_000) {
                while (!(events.any {
                        it.event.type == "assistant/message" &&
                            it.event.data.toString().contains("done")
                    } && statuses.lastOrNull()?.isIdle == true)
                ) {
                    delay(100)
                }
            }
            assertTrue("expected a turn/end event", events.any { it.event.type == "turn/end" })
            assertTrue(
                "expected the plan review to reach the IDE bridge; stderr tail: " +
                    stderrLines.toList().takeLast(10),
                forwarded.any { it.id == "plan-review" && it.detail?.contains("Test plan") == true },
            )
            assertTrue(
                "expected plan mode to turn off after approval",
                events.any { it.event.type == "plan/mode" && !it.event.data.path("active").asBoolean() },
            )
            assertTrue(
                "expected the approved continuation text",
                events.any { it.event.type == "assistant/message" && it.event.data.toString().contains("done") },
            )
            client.shutdown()
        } catch (t: Throwable) {
            System.err.println("--- DSH runtime stderr (last 40 lines) ---")
            stderrLines.toList().takeLast(40).forEach(System.err::println)
            throw t
        } finally {
            client.close()
            bridge.close()
            modelServer.stop(0)
            // Symlink-safe: DSH_HOME (root/.dsh) contains profile-fallback
            // symlinks into the checkout; deleteRecursively() would follow them.
            FsTree.deleteNoFollow(root)
        }
    }

    /**
     * Roadmap item 9: under workspace-write, an out-of-workspace write escalates
     * to an approval; the runtime-side answerer forwards it to the IDE bridge,
     * the bridge decides REJECTED, the tool call fails, and nothing touches disk.
     */
    @Test
    fun `permission approval flows through the bridge and rejects`() = runBlocking {
        val bin = File(checkout, "apps/cli/lib/bin.js")
        if (!bin.isFile) {
            throw IllegalStateException("DSH dev checkout has no built CLI at $bin (expected the dsh-v0.1.2-alpha.1 layout)")
        }
        val probePath = "/etc/dsh-jb-e2e-probe-" + System.currentTimeMillis() + ".txt"
        val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
        val modelServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        modelServer.createContext("/") { exchange ->
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
                    // Plain out-of-workspace write: the fence denies it and hints
                    // the sandbox_permissions retry.
                    sse("""{"choices":[{"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_w","type":"function","function":{"name":"write","arguments":""}}]}}]}""")
                    sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"file_path\":\"$probePath\",\"content\":\"probe\"}"}}]}}]}""")
                    sse("""{"choices":[{"delta":{"content":""},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}""")
                }
                2 -> {
                    // The model retries with sandbox_permissions + justification;
                    // that escalation is what reaches the approval channel.
                    sse("""{"choices":[{"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_e","type":"function","function":{"name":"write","arguments":""}}]}}]}""")
                    sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"file_path\":\"$probePath\",\"content\":\"probe\",\"sandbox_permissions\":\"danger-full-access\",\"justification\":\"The e2e test requires writing outside the workspace\"}"}}]}}]}""")
                    sse("""{"choices":[{"delta":{"content":""},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}""")
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
        val root = createTempDirectory("dsh-jb-e2e-perm").toFile()
        val stderrLines = java.util.Collections.synchronizedList(mutableListOf<String>())

        val forwardedApprovals = java.util.Collections.synchronizedList(mutableListOf<io.dsh.jb.bridge.BridgeApproval>())
        val bridge = io.dsh.jb.bridge.BridgeServer(
            onQuestions = { qs -> qs.map { q -> io.dsh.jb.bridge.PlanAnswer(q.id, listOf(q.options.firstOrNull().orEmpty()), null) } },
            onApproval = { approval ->
                forwardedApprovals += approval
                "rejected"
            },
        )
        bridge.start()

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
                permissionMode = "workspace-write",
                cwd = root.path,
                bridgeUrl = bridge.url,
                bridgeToken = bridge.token,
                sessionId = "e2e-perm",
            ),
            stderrSink = { stderrLines += it },
        )
        try {
            val events = CopyOnWriteArrayList<SessionEventNotification>()
            val statuses = CopyOnWriteArrayList<SessionStatusNotification>()
            client.addEventListener(events::add)
            client.addStatusListener(statuses::add)

            client.start()
            client.prompt("write outside the workspace")

            withTimeout(90_000) {
                while (!(events.any { it.event.type == "approval/decided" } && statuses.lastOrNull()?.isIdle == true)) {
                    delay(100)
                }
            }
            assertTrue("expected approval/asked", events.any { it.event.type == "approval/asked" })
            val decided = events.filter { it.event.type == "approval/decided" }
            assertTrue(
                "expected a rejected outcome; got: " + decided.map { it.event.data.toString() },
                decided.any { it.event.data.path("outcome").asText() == "rejected" },
            )
            assertTrue(
                "approval should reach the IDE bridge",
                forwardedApprovals.isNotEmpty(),
            )
            assertTrue(
                "bridge should name a tool",
                forwardedApprovals.any { it.toolName.isNotBlank() },
            )
            assertTrue("probe file must not exist after rejection", !File(probePath).exists())
            client.shutdown()
        } catch (t: Throwable) {
            System.err.println("--- DSH runtime stderr (last 40 lines) ---")
            stderrLines.toList().takeLast(40).forEach(System.err::println)
            throw t
        } finally {
            client.close()
            bridge.close()
            modelServer.stop(0)
            // Symlink-safe: DSH_HOME (root/.dsh) contains profile-fallback
            // symlinks into the checkout; deleteRecursively() would follow them.
            FsTree.deleteNoFollow(root)
            File(probePath).delete()
        }
    }

    /**
     * Roadmap item 9 fix round: the built-in sdk profile does not compose the
     * model-facing ask_user_question tool, so the jb-bridge plugin registers it.
     * This proves the full loop: the mock model calls the tool, the question
     * reaches the IDE bridge, the answer lands in the tool result, and the turn
     * continues with the model's next message.
     */
    @Test
    fun `ask_user_question tool forwards to the bridge`() = runBlocking {
        val bin = File(checkout, "apps/cli/lib/bin.js")
        if (!bin.isFile) {
            throw IllegalStateException("DSH dev checkout has no built CLI at $bin (expected the dsh-v0.1.2-alpha.1 layout)")
        }
        val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
        val modelServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        modelServer.createContext("/") { exchange ->
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
                    sse("""{"choices":[{"delta":{"role":"assistant","tool_calls":[{"index":0,"id":"call_q","type":"function","function":{"name":"ask_user_question","arguments":""}}]}}]}""")
                    sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"questions\":[{\"id\":\"q1\",\"question\":\"Write the file?\",\"header\":\"Confirm\",\"options\":[{\"label\":\"Yes, write it\"},{\"label\":\"No\"}]}]}"}}]}}]}""")
                    sse("""{"choices":[{"delta":{"content":""},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}""")
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
        val root = createTempDirectory("dsh-jb-e2e-ask").toFile()
        val stderrLines = java.util.Collections.synchronizedList(mutableListOf<String>())

        val forwarded = java.util.Collections.synchronizedList(mutableListOf<io.dsh.jb.bridge.PlanQuestion>())
        val bridge = io.dsh.jb.bridge.BridgeServer(
            onQuestions = { qs ->
                forwarded += qs
                qs.map { q -> io.dsh.jb.bridge.PlanAnswer(q.id, listOf(q.options.firstOrNull().orEmpty()), null) }
            },
        )
        bridge.start()

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
                permissionMode = "workspace-write",
                cwd = root.path,
                bridgeUrl = bridge.url,
                bridgeToken = bridge.token,
                sessionId = "e2e-ask",
            ),
            stderrSink = { stderrLines += it },
        )
        try {
            val events = CopyOnWriteArrayList<SessionEventNotification>()
            val statuses = CopyOnWriteArrayList<SessionStatusNotification>()
            client.addEventListener(events::add)
            client.addStatusListener(statuses::add)

            client.start()
            client.prompt("confirm before writing")

            withTimeout(90_000) {
                while (!(events.any { it.event.type == "assistant/message" && it.event.data.toString().contains("done") } &&
                        statuses.lastOrNull()?.isIdle == true)
                ) {
                    delay(100)
                }
            }
            assertTrue(
                "the ask_user_question tool must reach the IDE bridge; stderr tail: " +
                    stderrLines.toList().takeLast(10),
                forwarded.any { it.id == "q1" && it.question.contains("Write the file") },
            )
            assertTrue(
                "expected a tool result carrying the answers",
                events.any { it.event.type == "tool/result" && it.event.data.toString().contains("q1") },
            )
            client.shutdown()
        } catch (t: Throwable) {
            System.err.println("--- DSH runtime stderr (last 40 lines) ---")
            stderrLines.toList().takeLast(40).forEach(System.err::println)
            throw t
        } finally {
            client.close()
            bridge.close()
            modelServer.stop(0)
            // Symlink-safe: DSH_HOME (root/.dsh) contains profile-fallback
            // symlinks into the checkout; deleteRecursively() would follow them.
            FsTree.deleteNoFollow(root)
        }
    }

    /**
     * Roadmap item 12: the EMBEDDED packaged runtime serves the sdk profile.
     * Skipped when no runtime-dist was staged into the test resources.
     */
    @Test
    fun `bundled runtime exe initializes against the mock llm`() = runBlocking {
        val cache = createTempDirectory("dsh-jb-bundled-cache").toFile()
        System.setProperty("dsh.jb.runtimeCache", cache.path)
        try {
            val exe = BundledRuntimeResolver.resolve()
            org.junit.Assume.assumeTrue(
                "embedded runtime resource not staged (runtime-dist not populated)",
                exe != null,
            )
            val exeFile = exe ?: return@runBlocking
            val requestCount = java.util.concurrent.atomic.AtomicInteger(0)
            val modelServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            modelServer.createContext("/") { exchange ->
                requestCount.incrementAndGet()
                val headers = exchange.responseHeaders
                headers.add("Content-Type", "text/event-stream")
                headers.add("Connection", "close")
                exchange.sendResponseHeaders(200, 0)
                val out = exchange.responseBody
                fun sse(data: String) {
                    out.write("data: $data\n\n".toByteArray(Charsets.UTF_8))
                }
                sse("""{"choices":[{"delta":{"role":"assistant","content":null}}]}""")
                sse("""{"choices":[{"delta":{"content":"done"}}]}""")
                sse("""{"choices":[{"delta":{},"finish_reason":"length"}],"usage":{"prompt_tokens":3,"completion_tokens":1}}""")
                out.write("data: [DONE]\n\n".toByteArray(Charsets.UTF_8))
                out.close()
                exchange.close()
            }
            modelServer.start()
            val port = (modelServer.address as InetSocketAddress).port
            val root = createTempDirectory("dsh-jb-e2e-bundled").toFile()
            val stderrLines = java.util.Collections.synchronizedList(mutableListOf<String>())

            val client = DshRuntimeClient(
                DshRuntimeConfig(
                    mode = "bundled",
                    bundledExe = "",
                    apiKey = "keyless-e2e-no-call",
                    baseUrl = "http://127.0.0.1:$port",
                    provider = "deepseek-official",
                    model = "deepseek-v4-pro",
                    reasoningEffort = "max",
                    harnessHome = File(root, ".dsh").path,
                    permissionMode = "danger-full-access",
                    cwd = root.path,
                    sessionId = "e2e-bundled",
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
                assertTrue(
                    "expected the ripgrep sidecar beside the exe",
                    exeFile.parentFile!!.listFiles()!!.any { it.name.endsWith("-rg") },
                )
                client.prompt("hello")
                withTimeout(90_000) {
                    while (!(events.any {
                            it.event.type == "assistant/message" && it.event.data.toString().contains("done")
                        } && statuses.lastOrNull()?.isIdle == true)
                    ) {
                        delay(100)
                    }
                }
                client.shutdown()
            } catch (t: Throwable) {
                System.err.println("--- DSH runtime stderr (last 40 lines) ---")
                stderrLines.toList().takeLast(40).forEach(System.err::println)
                throw t
            } finally {
                client.close()
                modelServer.stop(0)
                // Symlink-safe: DSH_HOME (root/.dsh) contains profile-fallback
                // symlinks into the checkout; deleteRecursively() would follow them.
                FsTree.deleteNoFollow(root)
            }
        } finally {
            System.clearProperty("dsh.jb.runtimeCache")
            FsTree.deleteNoFollow(cache)
        }
    }
}
