package io.dsh.jb.settings

import io.dsh.jb.runtime.DshRuntimeConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Roadmap item 10 (pulled forward): settings → runtime-config resolution.
 * Pure JVM: DshSettingsSnapshot carries no platform types, so the resolution
 * logic is unit-testable without the IntelliJ platform sandbox.
 */
class DshSettingsResolveTest {

    @Test
    fun `blank settings resolve to node carrier and default model with no key`() {
        val cfg = DshRuntimeConfig.fromSettings(DshSettingsSnapshot(), null, "jb-1", "/proj")
        assertEquals("node", cfg.mode)
        assertEquals("deepseek-v4-flash", cfg.model)
        assertEquals("", cfg.apiKey)
        assertEquals("", cfg.checkoutPath)
        assertEquals("jb-1", cfg.sessionId)
        assertEquals("/proj", cfg.cwd)
    }

    @Test
    fun `fields and key pass through`() {
        val cfg = DshRuntimeConfig.fromSettings(
            DshSettingsSnapshot(
                mode = "bundled",
                bundledExe = "/opt/dsh/dsh-jsonrpc-agent",
                checkoutPath = "/h/dsh",
                baseUrl = "https://proxy.example/v1",
                model = "deepseek-reasoner",
            ),
            apiKey = "sk-secret",
            sessionId = "jb-2",
            cwd = "/p",
        )
        assertEquals("bundled", cfg.mode)
        assertEquals("/opt/dsh/dsh-jsonrpc-agent", cfg.bundledExe)
        assertEquals("/h/dsh", cfg.checkoutPath)
        assertEquals("https://proxy.example/v1", cfg.baseUrl)
        assertEquals("deepseek-reasoner", cfg.model)
        assertEquals("sk-secret", cfg.apiKey)
    }

    @Test
    fun `blank mode and model fall back to defaults`() {
        val cfg = DshRuntimeConfig.fromSettings(
            DshSettingsSnapshot(mode = " ", model = "  "),
            null, "jb-3", "/p",
        )
        assertEquals("node", cfg.mode)
        assertEquals("deepseek-v4-flash", cfg.model)
    }

    @Test
    fun `whitespace-only paths are trimmed to empty`() {
        val cfg = DshRuntimeConfig.fromSettings(
            DshSettingsSnapshot(checkoutPath = "  /x/dsh  "),
            null, "jb-4", "/p",
        )
        assertEquals("/x/dsh", cfg.checkoutPath)
    }
}
