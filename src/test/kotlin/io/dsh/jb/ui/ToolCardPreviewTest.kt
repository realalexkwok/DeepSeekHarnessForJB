package io.dsh.jb.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap item 21: pure tool-card preview extraction. */
class ToolCardPreviewTest {

    @Test
    fun resultIsPreferredOverArgs() {
        assertEquals("file written", toolPreviewLine("file written", "{\"path\": \"a\"}"))
    }

    @Test
    fun argsAreTheFallbackWhenResultIsBlank() {
        assertEquals("{\"path\": \"a\"}", toolPreviewLine("  \n  ", "{\"path\": \"a\"}"))
    }

    @Test
    fun firstNonBlankLineWins() {
        assertEquals("second", toolPreviewLine("\n   \nsecond\nthird", ""))
    }

    @Test
    fun longLinesTruncateWithEllipsis() {
        val long = "x".repeat(200)
        val out = toolPreviewLine(long, "", maxChars = 160)
        assertEquals(160 + 1, out.length)
        assertEquals(long.take(160) + "…", out)
    }

    @Test
    fun blankInputsGiveBlankPreview() {
        assertEquals("", toolPreviewLine("   ", ""))
    }
}
