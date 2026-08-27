package io.dsh.jb.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 6/10/11: per-effort cordis patching (pure). */
class CordisEffortTest {

    private val base = """
- id: sdk-jsonrpc-server
  name: '@deepseek-ai/dsh-sdk-jsonrpc-server'
- id: llm-deepseek
  name: '@deepseek-ai/dsh-llm-deepseek'
  config:
    thinking: enabled
    reasoningEffort: max
- id: compaction-basic
  name: '@deepseek-ai/dsh-compaction-basic'
  config:
    thresholdRatio: 0.8
""".trimIndent()

    @Test
    fun `off disables thinking and sets off effort`() {
        val out = CordisEffort.apply(base, EffortLevel.OFF)
        assertTrue(out.contains("thinking: disabled"))
        assertTrue(out.contains("reasoningEffort: off"))
    }

    @Test
    fun `low high and max enable thinking with matching effort`() {
        for (effort in listOf(EffortLevel.LOW, EffortLevel.HIGH, EffortLevel.MAX)) {
            val out = CordisEffort.apply(base, effort)
            assertTrue("$effort", out.contains("thinking: enabled"))
            assertTrue("$effort", out.contains("reasoningEffort: ${effort.wire}"))
        }
    }

    @Test
    fun `unrelated sections are untouched`() {
        val out = CordisEffort.apply(base, EffortLevel.LOW)
        assertTrue(out.contains("sdk-jsonrpc-server"))
        assertTrue(out.contains("compaction-basic"))
        assertTrue(out.contains("thresholdRatio: 0.8"))
        assertEquals(1, Regex("(?m)^\\s*thinking:").findAll(out).count())
        assertFalse(out.contains("thinking: enabled\n    reasoningEffort: max") || out == base)
    }
}
