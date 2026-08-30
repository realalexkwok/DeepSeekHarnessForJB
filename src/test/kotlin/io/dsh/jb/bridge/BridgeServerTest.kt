package io.dsh.jb.bridge

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 8: the localhost bridge protocol (pure JVM, headless). */
class BridgeServerTest {

    private val mapper = jacksonObjectMapper()
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    @Test
    fun `answer endpoint forwards plan-review questions and returns the decision`() {
        val seen = AtomicReference<List<PlanQuestion>?>(null)
        val bridge = BridgeServer(onQuestions = { questions ->
            seen.set(questions)
            questions.map { q ->
                val approve = q.approveLabel ?: q.options.first()
                PlanAnswer(q.id, listOf(approve), null)
            }
        }, answerTimeoutMs = 30_000)
        bridge.start()
        try {
            val body = mapper.createObjectNode().apply {
                val qs = putArray("questions")
                val q = qs.addObject()
                q.put("id", "plan-review")
                q.put("question", "Review this plan")
                q.put("detail", "# Plan\nDo the thing.")
                val options = q.putArray("options")
                options.addObject().put("label", "Approve")
                options.addObject().put("label", "Keep planning")
                q.putObject("intent").put("kind", "plan-review").put("approve", "Approve")
            }
            val response = http.send(
                HttpRequest.newBuilder(URI.create("${bridge.url}/answer"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${bridge.token}")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())
            val answers = mapper.readTree(response.body()).path("answers")
            assertEquals(1, answers.size())
            assertEquals("plan-review", answers[0].path("id").asText())
            assertEquals("Approve", answers[0].path("selected")[0].asText())
            val questions = seen.get()
            assertNotNull(questions)
            assertEquals("# Plan\nDo the thing.", questions!![0].detail)
            assertEquals("Approve", questions[0].approveLabel)
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `commands endpoint drains the enqueued queue once`() {
        val bridge = BridgeServer(onQuestions = { emptyList() })
        bridge.start()
        try {
            bridge.enqueueCommand("/plan")
            bridge.enqueueCommand("/plan off")
            val response = http.send(
                HttpRequest.newBuilder(URI.create("${bridge.url}/commands"))
                    .header("Authorization", "Bearer ${bridge.token}")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, response.statusCode())
            val lines = mapper.readTree(response.body()).path("commands").map { it.path("line").asText() }
            assertEquals(listOf("/plan", "/plan off"), lines)
            // Second drain is empty.
            val second = http.send(
                HttpRequest.newBuilder(URI.create("${bridge.url}/commands"))
                    .header("Authorization", "Bearer ${bridge.token}")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(0, mapper.readTree(second.body()).path("commands").size())
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `requests without the bearer token are rejected`() {
        val bridge = BridgeServer(onQuestions = { emptyList() })
        bridge.start()
        try {
            val response = http.send(
                HttpRequest.newBuilder(URI.create("${bridge.url}/commands")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(401, response.statusCode())
            val wrong = http.send(
                HttpRequest.newBuilder(URI.create("${bridge.url}/commands"))
                    .header("Authorization", "Bearer nope")
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(401, wrong.statusCode())
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `unanswered questions time out fail-closed`() {
        // The handler blocks past the server timeout (a user who never answers);
        // the server must respond 504 so the runtime answerer fails closed.
        val bridge = BridgeServer(
            onQuestions = {
                Thread.sleep(60_000)
                emptyList()
            },
            answerTimeoutMs = 2_000,
        )
        bridge.start()
        try {
            val body = mapper.createObjectNode().apply {
                putArray("questions").addObject().apply {
                    put("id", "plan-review")
                    put("question", "q")
                    putArray("options").addObject().put("label", "Approve")
                }
            }
            val response = http.send(
                HttpRequest.newBuilder(URI.create("${bridge.url}/answer"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${bridge.token}")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(504, response.statusCode())
        } finally {
            bridge.close()
        }
    }

    @Test
    fun `drainCommands empties the queue`() {
        val bridge = BridgeServer(onQuestions = { emptyList() })
        bridge.enqueueCommand("a")
        bridge.enqueueCommand("b")
        assertEquals(listOf("a", "b"), bridge.drainCommands())
        assertTrue(bridge.drainCommands().isEmpty())
        bridge.close()
    }
}
