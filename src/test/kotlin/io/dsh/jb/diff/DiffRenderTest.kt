package io.dsh.jb.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roadmap item 23: pure Myers diff + unified patch rendering. */
class DiffRenderTest {

    @Test
    fun identicalTextsProduceEqualLinesOnly() {
        val out = diffLines("a\nb", "a\nb")
        assertEquals(listOf(DiffOp.EQUAL, DiffOp.EQUAL), out.map { it.op })
    }

    @Test
    fun replacementProducesDeleteThenInsert() {
        val out = diffLines("a\nb\nc", "a\nx\nc")
        assertEquals(
            listOf(DiffOp.EQUAL, DiffOp.DELETE, DiffOp.INSERT, DiffOp.EQUAL),
            out.map { it.op },
        )
        assertEquals("b", out[1].text)
        assertEquals("x", out[2].text)
    }

    @Test
    fun creationRendersDevNullHeader() {
        val patch = renderUnifiedPatch(FileChange("p.txt", null, "hello"))
        assertTrue(patch.startsWith("--- /dev/null\n+++ b/p.txt"))
        assertTrue(patch.contains("+hello"))
    }

    @Test
    fun hunkHeaderCarriesCountsAndStarts() {
        val patch = renderUnifiedPatch(FileChange("p.txt", "one\ntwo\nthree", "one\nTWO\nthree"))
        assertTrue(patch.contains("@@ -1,3 +1,3 @@"))
        assertTrue(patch.contains("-two"))
        assertTrue(patch.contains("+TWO"))
    }

    @Test
    fun separatedChangesSplitIntoTwoHunks() {
        val old = (1..10).joinToString("\n") { "line$it" }
        val new = "CHANGED1\n" + old.lines().drop(1).joinToString("\n") + "\nCHANGED10X"
        val patch = renderUnifiedPatch(FileChange("p.txt", old, new))
        val hunks = patch.lines().count { it.startsWith("@@") }
        assertEquals(2, hunks)
    }

    @Test
    fun absolutePathHeaderStripsLeadingSlash() {
        val patch = renderUnifiedPatch(FileChange("/Users/x/y.txt", null, "hi"))
        assertTrue(patch.startsWith("--- /dev/null\n+++ b/Users/x/y.txt"))
    }

    @Test
    fun patchLineCountSumsAcrossFiles() {
        val changes = listOf(
            FileChange("a.txt", "x", "y"),
            FileChange("b.txt", null, "one\ntwo"),
        )
        assertEquals(2 + 2, patchLineCount(changes))
    }
}
