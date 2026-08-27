package io.dsh.jb.runtime

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 10 fix: node candidate resolution (pure). */
class NodeResolverTest {

    @Test
    fun `candidates start with bare node then well known absolute paths`() {
        val list = NodeResolver.candidates(home = "/tmp/fakehome")
        assertEquals("node", list[0])
        assertTrue(list.contains("/opt/homebrew/bin/node"))
        assertTrue(list.contains("/usr/local/bin/node"))
        assertTrue(list.contains("/opt/local/bin/node"))
        assertTrue(list.contains("/usr/bin/node"))
        assertTrue(list.contains("/tmp/fakehome/.volta/bin/node"))
        assertTrue(list.contains("/tmp/fakehome/.asdf/shims/node"))
    }

    @Test
    fun `nvm and fnm version directories are scanned`() {
        val home = createTempDirectory("dsh-jb-home").toFile()
        try {
            val nvmBin = File(home, ".nvm/versions/node/v22.0.0/bin").apply { mkdirs() }
            val fnmBin = File(home, ".local/share/fnm/node-versions/v23.0.0/installation/bin").apply { mkdirs() }
            val list = NodeResolver.candidates(home = home.path)
            assertTrue(list.contains(File(nvmBin, "node").path))
            assertTrue(list.contains(File(fnmBin, "node").path))
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun `resolve returns the first candidate whose probe succeeds`() {
        val info = NodeResolver.resolve(probe = { if (it == "/opt/homebrew/bin/node") "v22.23.2" else null })
        assertEquals("/opt/homebrew/bin/node", info?.path)
        assertEquals("v22.23.2", info?.version)
    }

    @Test
    fun `resolve returns null when no candidate probes`() {
        assertNull(NodeResolver.resolve(probe = { null }))
    }
}
