package io.dsh.jb.protocol

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/** A JSON-RPC 2.0 error reported by the peer. */
class JsonRpcException(
    val code: Int,
    override val message: String,
    val data: JsonNode? = null,
) : Exception("JSON-RPC error $code: $message")

/**
 * Newline-delimited line transport. Production uses [StdioLineTransport] over the
 * runtime's stdio; tests exercise [JsonRpcCodec] directly and need no transport.
 */
interface LineTransport {
    /** Send one complete frame line; flushes before returning. */
    fun send(line: String)

    /** Block until the next line; null means EOF. */
    fun receive(): String?

    /** Release the underlying resources; a blocked [receive] must return (null). */
    fun close()
}

/** Newline-delimited transport over owned stdio streams. */
class StdioLineTransport(
    private val reader: BufferedReader,
    private val writer: BufferedWriter,
) : LineTransport {
    override fun send(line: String) {
        synchronized(writer) {
            writer.write(line)
            writer.write('\n'.code)
            writer.flush()
        }
    }

    override fun receive(): String? = try {
        reader.readLine()
    } catch (_: Exception) {
        null
    }

    override fun close() {
        try {
            writer.close()
        } catch (_: Exception) {
            // Stream may already be gone.
        }
    }
}

/**
 * Pure, synchronous JSON-RPC 2.0 frame engine: framing, request/response
 * correlation, error mapping, and dispatch — no I/O, no threads, so unit tests
 * drive it directly. Mirrors `@deepseek-ai/dsh-sdk-protocol`: one compact JSON
 * frame per line; requests carry an id, notifications do not; malformed lines
 * are ignored.
 */
class JsonRpcCodec(private val mapper: ObjectMapper) {

    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonNode>>()

    /** Receives (method, params); no response expected. */
    @Volatile
    var onNotification: (String, JsonNode) -> Unit = { _, _ -> }

    /** Receives (method, params, id) and must RETURN the response frame line (or null to not answer). */
    @Volatile
    var onRequest: (String, JsonNode, Long) -> String? = { _, _, id -> encodeError(id, -32601, "method not found") }

    /** Registers a pending call and returns (id, wire line) for the request. */
    fun newRequest(method: String, params: JsonNode?): Pair<Long, String> {
        val id = nextId.getAndIncrement()
        pending[id] = CompletableFuture()
        val frame = mapper.createObjectNode()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
        if (params != null) frame.set<JsonNode>("params", params)
        return id to mapper.writeValueAsString(frame)
    }

    fun futureFor(id: Long): CompletableFuture<JsonNode>? = pending[id]

    fun cancel(id: Long) {
        pending.remove(id)
    }

    fun failAll(err: JsonRpcException) {
        val futures = pending.values.toList()
        pending.clear()
        futures.forEach { it.completeExceptionally(err) }
    }

    fun encodeNotification(method: String, params: JsonNode?): String {
        val frame = mapper.createObjectNode().put("jsonrpc", "2.0").put("method", method)
        if (params != null) frame.set<JsonNode>("params", params)
        return mapper.writeValueAsString(frame)
    }

    fun encodeResponse(id: Long, result: JsonNode?): String {
        val frame = mapper.createObjectNode()
            .put("jsonrpc", "2.0")
            .put("id", id)
        if (result != null) frame.set<JsonNode>("result", result) else frame.putNull("result")
        return mapper.writeValueAsString(frame)
    }

    fun encodeError(id: Long, code: Int, message: String, data: JsonNode? = null): String {
        val error = mapper.createObjectNode().put("code", code).put("message", message)
        if (data != null) error.set<JsonNode>("data", data)
        val frame = mapper.createObjectNode().put("jsonrpc", "2.0").put("id", id)
        frame.set<JsonNode>("error", error)
        return mapper.writeValueAsString(frame)
    }

    /**
     * Processes one inbound frame line; returns an outbound response line when
     * the frame was an inbound request the [onRequest] handler answered, or
     * null. Malformed lines are ignored (return null).
     */
    fun handleLine(line: String): String? {
        if (line.isBlank()) return null
        val frame = try {
            mapper.readTree(line)
        } catch (_: Exception) {
            return null // malformed JSON lines are ignored
        }
        val idNode = frame.get("id")
        if (idNode != null) {
            if (frame.has("method")) {
                // Inbound request: delegate and return the handler's answer.
                return onRequest(frame.get("method").asText(), frame.get("params") ?: mapper.nullNode(), idNode.asLong())
            }
            val future = pending.remove(idNode.asLong()) ?: return null // unknown/stale id
            val error = frame.get("error")
            if (error != null && !error.isNull) {
                future.completeExceptionally(
                    JsonRpcException(
                        error.path("code").asInt(-32603),
                        error.path("message").asText("JSON-RPC error"),
                        error.get("data"),
                    ),
                )
            } else {
                future.complete(frame.get("result") ?: mapper.nullNode())
            }
            return null
        }
        if (frame.has("method")) {
            onNotification(frame.get("method").asText(), frame.get("params") ?: mapper.nullNode())
        }
        return null
    }
}

/**
 * Async stdio wrapper around [JsonRpcCodec]: one plain daemon reader thread,
 * [LineTransport] for I/O, suspend `request` for callers. [stop] closes the
 * transport, which unblocks the reader deterministically.
 */
class JsonRpcPeer(
    private val transport: LineTransport,
    mapper: ObjectMapper,
) {
    val codec = JsonRpcCodec(mapper)

    @Volatile
    private var closed = false

    private var readerThread: Thread? = null

    fun onNotification(handler: (method: String, params: JsonNode) -> Unit) {
        codec.onNotification = handler
    }

    /** Handler for inbound requests; receives (method, params, id) and returns the response line. */
    fun onRequest(handler: (method: String, params: JsonNode, id: Long) -> String?) {
        codec.onRequest = handler
    }

    fun start() {
        check(readerThread == null) { "peer already started" }
        check(!closed) { "peer already stopped" }
        readerThread = thread(isDaemon = true, name = "dsh-jsonrpc-reader") { readLoop() }
    }

    /** Closes the transport and fails every pending call; idempotent. */
    fun stop() {
        if (closed) return
        closed = true
        try {
            transport.close()
        } catch (_: Exception) {
            // Best effort.
        }
        readerThread?.join(1000)
        codec.failAll(JsonRpcException(-32000, "transport closed"))
    }

    suspend fun request(method: String, params: JsonNode?): JsonNode =
        suspendCancellableCoroutine { cont ->
            val (id, line) = codec.newRequest(method, params)
            codec.futureFor(id)?.whenComplete { value, error ->
                if (cont.isActive) {
                    if (error != null) cont.resumeWithException(error) else cont.resume(value)
                }
            }
            cont.invokeOnCancellation { codec.cancel(id) }
            try {
                transport.send(line)
            } catch (e: Exception) {
                codec.cancel(id)
                if (cont.isActive) cont.resumeWithException(e)
            }
        }

    fun notify(method: String, params: JsonNode?) {
        transport.send(codec.encodeNotification(method, params))
    }

    fun respond(id: Long, result: JsonNode?) {
        transport.send(codec.encodeResponse(id, result))
    }

    fun respondError(id: Long, code: Int, message: String, data: JsonNode? = null) {
        transport.send(codec.encodeError(id, code, message, data))
    }

    private fun readLoop() {
        while (!closed) {
            val line = transport.receive() ?: break // EOF or IO failure: transport closed
            val out = codec.handleLine(line)
            if (out != null) transport.send(out)
        }
        codec.failAll(JsonRpcException(-32000, "transport closed"))
    }
}
