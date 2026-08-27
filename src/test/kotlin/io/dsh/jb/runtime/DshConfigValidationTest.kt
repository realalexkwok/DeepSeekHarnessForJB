package io.dsh.jb.runtime

import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Roadmap item 10 ask-flow: pre-flight config validation. Pure JVM.
 */
class DshConfigValidationTest {

    @Test
    fun `node carrier with blank checkout path is reported`() {
        val cfg = DshRuntimeConfig(mode = "node", sessionId = "jb-1")
        assertEquals("DeepSeek Harness checkout path is not configured", cfg.validateForStart())
    }

    @Test
    fun `node carrier with missing directory is reported`() {
        val cfg = DshRuntimeConfig(
            mode = "node",
            checkoutPath = "/no/such/dir-xyz",
            nodeExecutable = "/usr/bin/node",
            sessionId = "jb-2",
        )
        assertEquals("DSH_CHECKOUT is not a directory: /no/such/dir-xyz", cfg.validateForStart())
    }

    @Test
    fun `node carrier with a real directory passes`() {
        val dir = createTempDirectory("dsh-jb-cfg").toFile()
        try {
            val cfg = DshRuntimeConfig(
                mode = "node",
                checkoutPath = dir.path,
                nodeExecutable = "/usr/bin/node",
                sessionId = "jb-3",
            )
            assertNull(cfg.validateForStart())
        } finally {
            dir.delete()
        }
    }

    @Test
    fun `bundled carrier with blank exe is reported`() {
        val cfg = DshRuntimeConfig(mode = "bundled", sessionId = "jb-4")
        assertEquals("Bundled runtime executable path is not configured", cfg.validateForStart())
    }

    @Test
    fun `bundled carrier with missing file is reported`() {
        val cfg = DshRuntimeConfig(mode = "bundled", bundledExe = "/no/such/exe-xyz", sessionId = "jb-5")
        assertEquals("Bundled runtime executable not found: /no/such/exe-xyz", cfg.validateForStart())
    }

    @Test
    fun `bundled carrier with an existing file passes`() {
        val f = createTempFile("dsh-jb-exe", "").toFile()
        try {
            val cfg = DshRuntimeConfig(mode = "bundled", bundledExe = f.path, sessionId = "jb-6")
            assertNull(cfg.validateForStart())
        } finally {
            f.delete()
        }
    }

    @Test
    fun `node carrier without resolved node is reported`() {
        val dir = createTempDirectory("dsh-jb-cfg-node").toFile()
        try {
            val cfg = DshRuntimeConfig(mode = "node", checkoutPath = dir.path, sessionId = "jb-7")
            assertEquals(
                "Node.js was not found — checked PATH plus common install locations " +
                    "(see Settings → Tools → DeepSeek Harness)",
                cfg.validateForStart(),
            )
        } finally {
            dir.delete()
        }
    }

    @Test
    fun `node carrier with a resolved node passes`() {
        val dir = createTempDirectory("dsh-jb-cfg-node2").toFile()
        try {
            val cfg = DshRuntimeConfig(
                mode = "node",
                checkoutPath = dir.path,
                nodeExecutable = "/opt/homebrew/bin/node",
                sessionId = "jb-8",
            )
            assertNull(cfg.validateForStart())
        } finally {
            dir.delete()
        }
    }
}
