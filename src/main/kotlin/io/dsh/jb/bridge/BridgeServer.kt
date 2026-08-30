package io.dsh.jb.bridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Localhost bridge the runtime-side `jb-bridge` plugin calls:
 * - `POST /answer` — forwards a `plan-review` question set to [onQuestions] and
 *   blocks until the IDE answers (or [answerTimeoutMs], then 504 so the runtime
 *   fails closed);
 * - `GET /commands` — drains commands the IDE enqueued via [enqueueCommand].
 *
 * Pure JVM (JDK HttpServer); unit-testable headlessly. One instance per runtime.
 */
class BridgeServer(
    private val onQuestions: (List<PlanQuestion>) -> List<PlanAnswer>,
    private val answerTimeoutMs: Long = 120_000,
) : AutoCloseable {

    val token: String = UUID.randomUUID().toString()
    private val mapper = jacksonObjectMapper()
    private val commands = ConcurrentLinkedQueue<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private var executor: ExecutorService? = null

    @Volatile
    private var closed = false

    val url: String
        get() = "http://127.0.0.1:${server.address.port}"

    fun start() {
        val pool = Executors.newCachedThreadPool()
        executor = pool
        server.executor = pool
        server.createContext("/answer") { exchange ->
            if (closed || !authorized(exchange)) {
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
                return@createContext
            }
            val body = try {
                exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            } catch (_: Exception) {
                ""
            }
            val root = try {
                mapper.readTree(body)
            } catch (_: Exception) {
                mapper.nullNode()
            }
            val questions = root.path("questions").map(BridgeProtocol::questionFromJson)
            if (questions.isEmpty()) {
                exchange.sendResponseHeaders(400, -1)
                exchange.close()
                return@createContext
            }
            // Block until the UI answers; enforce the timeout fail-closed.
            val future = pool.submit<List<PlanAnswer>> { onQuestions(questions) }
            val answers = try {
                future.get(answerTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                future.cancel(true)
                exchange.sendResponseHeaders(504, -1)
                exchange.close()
                return@createContext
            }
            val payload = BridgeProtocol.answersToJson(answers, mapper).toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
            exchange.close()
        }
        server.createContext("/commands") { exchange ->
            if (closed || !authorized(exchange)) {
                exchange.sendResponseHeaders(401, -1)
                exchange.close()
                return@createContext
            }
            val queued = drainCommands()
            val root = mapper.createObjectNode()
            val array = root.putArray("commands")
            for (line in queued) {
                array.addObject().put("line", line)
            }
            val payload = mapper.writeValueAsBytes(root)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
            exchange.close()
        }
        server.start()
    }

    fun enqueueCommand(line: String) {
        commands.add(line)
    }

    fun drainCommands(): List<String> {
        val out = mutableListOf<String>()
        while (true) out.add(commands.poll() ?: break)
        return out
    }

    private fun authorized(exchange: com.sun.net.httpserver.HttpExchange): Boolean {
        val header = exchange.requestHeaders.getFirst("Authorization") ?: return false
        return header.trim() == "Bearer $token"
    }

    override fun close() {
        closed = true
        executor?.shutdownNow()
        server.stop(0)
    }
}
