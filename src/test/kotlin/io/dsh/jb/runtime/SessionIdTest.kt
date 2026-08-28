package io.dsh.jb.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-runtime session ids (id-collision fix, 2026-08-28). */
class SessionIdTest {

    @Test
    fun `session id embeds hash effort model and nonce`() {
        val id = buildSessionId("ef1095d3", RuntimeKey("deepseek-v4-flash", EffortLevel.MAX), "abc12345")
        assertEquals("jb-ef1095d3-max-deepseek-v4-flash-abc12345", id)
    }

    @Test
    fun `nonce is eight hex characters`() {
        val nonce = newSessionNonce()
        assertEquals(8, nonce.length)
        assertTrue(nonce.all { it.isDigit() || it in 'a'..'f' })
    }

    @Test
    fun `nonces differ across calls`() {
        assertNotEquals(newSessionNonce(), newSessionNonce())
    }
}
