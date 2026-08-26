package io.dsh.jb.protocol

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Synchronous tests of the pure frame engine [JsonRpcCodec] — no threads, no
 * transport. The async stdio wiring ([JsonRpcPeer] + [StdioLineTransport]) is
 * exercised by the headless e2e against the real DSH runtime.
 */
class JsonRpcCodecTest {

    private fun mapper() = jacksonObjectMapper()

    @Test
    fun `request response completes pending future`() {
        val client = JsonRpcCodec(mapper())
        val (id, line) = client.newRequest("initialize", mapper().createObjectNode().put("key", "value"))
        val future = client.futureFor(id)!!

        val server = JsonRpcCodec(mapper())
        var receivedMethod = ""
        var receivedParams: com.fasterxml.jackson.databind.JsonNode? = null
        server.onRequest = { method, params, reqId ->
            receivedMethod = method
            receivedParams = params
            server.encodeResponse(reqId, mapper().createObjectNode().put("ok", true))
        }

        val responseLine = server.handleLine(line)
        assertNotNull(responseLine)
        client.handleLine(responseLine!!)
        assertEquals("initialize", receivedMethod)
        assertEquals("value", receivedParams?.path("key")?.asText())
        assertEquals(true, future.get(5, TimeUnit.SECONDS).path("ok").asBoolean())
    }

    @Test
    fun `error response completes exceptionally with code and message`() {
        val client = JsonRpcCodec(mapper())
        val (id, line) = client.newRequest("initialize", null)
        val future = client.futureFor(id)!!

        val server = JsonRpcCodec(mapper())
        server.onRequest = { _, _, reqId -> server.encodeError(reqId, -32000, "boom") }
        client.handleLine(server.handleLine(line)!!)

        try {
            future.get(5, TimeUnit.SECONDS)
            fail("expected ExecutionException")
        } catch (e: ExecutionException) {
            val cause = e.cause
            assertTrue(cause is JsonRpcException)
            assertEquals(-32000, (cause as JsonRpcException).code)
            assertEquals("boom", cause.message)
        }
    }

    @Test
    fun `stale response id is ignored and does not corrupt pending state`() {
        val client = JsonRpcCodec(mapper())
        val (id, line) = client.newRequest("initialize", null)
        val future = client.futureFor(id)!!

        // A response for an unknown id changes nothing.
        assertNull(client.handleLine(mapper().createObjectNode().put("jsonrpc", "2.0").put("id", 999).put("result", "x").toString()))
        assertTrue(!future.isDone)

        val server = JsonRpcCodec(mapper())
        server.onRequest = { _, _, reqId -> server.encodeResponse(reqId, mapper().createObjectNode().put("ok", 1)) }
        client.handleLine(server.handleLine(line)!!)
        assertEquals(1, future.get(5, TimeUnit.SECONDS).path("ok").asInt())
    }

    @Test
    fun `default inbound request answers method not found`() {
        val server = JsonRpcCodec(mapper())
        val response = server.handleLine(
            mapper().createObjectNode().put("jsonrpc", "2.0").put("id", 7).put("method", "nope").toString(),
        )
        assertNotNull(response)
        val frame = mapper().readTree(response)
        assertEquals(-32601, frame.path("error").path("code").asInt())
        assertEquals(7, frame.path("id").asLong())
    }

    @Test
    fun `notifications dispatch to handler`() {
        val codec = JsonRpcCodec(mapper())
        val received = mutableListOf<String>()
        codec.onNotification = { method, _ -> received += method }
        codec.handleLine(codec.encodeNotification("session.status", mapper().createObjectNode().put("status", "idle")))
        codec.handleLine(codec.encodeNotification("session.event", mapper().createObjectNode().put("type", "turn/end")))
        assertEquals(listOf("session.status", "session.event"), received)
    }

    @Test
    fun `malformed and blank lines are ignored`() {
        val codec = JsonRpcCodec(mapper())
        assertNull(codec.handleLine("this is not json"))
        assertNull(codec.handleLine("   "))
    }

    @Test
    fun `failAll fails every pending future`() {
        val codec = JsonRpcCodec(mapper())
        val (id1, _) = codec.newRequest("initialize", null)
        val (id2, _) = codec.newRequest("session/prompt", null)
        val f1 = codec.futureFor(id1)!!
        val f2 = codec.futureFor(id2)!!
        codec.failAll(JsonRpcException(-32000, "transport closed"))
        for (f in listOf(f1, f2)) {
            try {
                f.get(5, TimeUnit.SECONDS)
                fail("expected ExecutionException")
            } catch (e: ExecutionException) {
                assertTrue(e.cause is JsonRpcException)
            }
        }
    }

    @Test
    fun `request params round trip verbatim on the wire`() {
        val codec = JsonRpcCodec(mapper())
        val params = mapper().createObjectNode().put("text", "héllo").put("n", 42)
        val (id, line) = codec.newRequest("session/prompt", params)
        val frame = mapper().readTree(line)
        assertEquals("2.0", frame.path("jsonrpc").asText())
        assertEquals(id, frame.path("id").asLong())
        assertEquals("session/prompt", frame.path("method").asText())
        assertEquals("héllo", frame.path("params").path("text").asText())
        assertEquals(42, frame.path("params").path("n").asInt())
    }
}
