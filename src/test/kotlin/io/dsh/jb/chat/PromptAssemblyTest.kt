package io.dsh.jb.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 6/11: prompt section assembly (pure). */
class PromptAssemblyTest {

    @Test
    fun `plain execute prompt has no sections and no instruction`() {
        val text = PromptAssembly.assemble(
            "do the thing",
            PromptContext(ComposerAction.EXECUTE, includeCurrentFile = false, includeAgents = false),
        )
        assertEquals("do the thing", text)
    }

    @Test
    fun `ask appends the read-only instruction`() {
        val text = PromptAssembly.assemble(
            "what does this do?",
            PromptContext(ComposerAction.ASK, includeCurrentFile = false, includeAgents = false),
        )
        assertTrue(text.startsWith("what does this do?"))
        assertTrue(text.contains("read-only request"))
    }

    @Test
    fun `plan appends the plan-first instruction`() {
        val text = PromptAssembly.assemble(
            "refactor it",
            PromptContext(ComposerAction.PLAN, includeCurrentFile = false, includeAgents = false),
        )
        assertTrue(text.contains("present a plan for approval"))
    }

    @Test
    fun `sections are ordered selection file agents then user text`() {
        val text = PromptAssembly.assemble(
            "explain this",
            PromptContext(
                ComposerAction.EXECUTE,
                includeCurrentFile = true,
                includeAgents = true,
                currentFilePath = "/p/A.kt",
                currentFileContent = "fun a() {}",
                selection = "fun a",
                agentsContent = "# rules",
            ),
        )
        val sel = text.indexOf("[Selected code]")
        val file = text.indexOf("[File: /p/A.kt]")
        val agents = text.indexOf("[AGENTS.md]")
        val user = text.indexOf("explain this")
        assertTrue(sel >= 0 && file > sel && agents > file && user > agents)
    }

    @Test
    fun `unchecked context gates the sections`() {
        val text = PromptAssembly.assemble(
            "hi",
            PromptContext(
                ComposerAction.EXECUTE,
                includeCurrentFile = false,
                includeAgents = false,
                currentFilePath = "/p/A.kt",
                currentFileContent = "fun a() {}",
                selection = "fun a",
                agentsContent = "# rules",
            ),
        )
        assertEquals("hi", text)
        assertFalse(text.contains("[File"))
        assertFalse(text.contains("[AGENTS.md]"))
        assertFalse(text.contains("[Selected code]"))
    }
}
