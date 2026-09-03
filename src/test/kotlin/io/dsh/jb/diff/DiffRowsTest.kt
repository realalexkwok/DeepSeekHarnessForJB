package io.dsh.jb.diff

import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap item 23 (host round 2026-09-02): gutter row parsing. */
class DiffRowsTest {

    @Test
    fun gutterRowsFollowDiffOpsDirectly() {
        val lines = diffLines("a\nb\nc", "a\nx\nc\nd")
        val rows = gutterRows(lines)
        assertEquals(
            listOf(
                DiffRow(1, 1),
                DiffRow(2, null),
                DiffRow(null, 2),
                DiffRow(3, 3),
                DiffRow(null, 4),
            ),
            rows,
        )
    }

    @Test
    fun contextLinesAdvanceBothSides() {
        val patch = "--- a/p.txt\n+++ b/p.txt\n@@ -2,2 +2,3 @@\n ctx\n+add\n-del\n ctx2"
        val rows = diffRows(patch)
        assertEquals(4, rows.size)
        assertEquals(DiffRow(2, 2), rows[0])
        assertEquals(DiffRow(null, 3), rows[1])
        assertEquals(DiffRow(3, null), rows[2])
        assertEquals(DiffRow(4, 4), rows[3])
    }

    @Test
    fun fileHeadersProduceNoRows() {
        val patch = "--- a/p.txt\n+++ b/p.txt"
        assertEquals(0, diffRows(patch).size)
    }

    @Test
    fun secondHunkRestartsCounters() {
        val patch = "@@ -1,1 +1,1 @@\n a\n@@ -10,1 +12,1 @@\n b"
        val rows = diffRows(patch)
        assertEquals(listOf(DiffRow(1, 1), DiffRow(10, 12)), rows)
    }
}
