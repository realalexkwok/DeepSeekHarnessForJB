package io.dsh.jb.diff

/**
 * Item 23: pure unified-patch rendering for the inline card diff. The
 * harness meta carries WHOLE before/after texts (FsDiffParser.FileChange),
 * so the patch is produced by a local Myers line diff — no platform
 * dependencies, fully unit-testable.
 */

enum class DiffOp { EQUAL, INSERT, DELETE }

data class DiffLine(val op: DiffOp, val text: String)

/** Item 23: unified-diff line cap above which the card shows the over-cap
 * placeholder instead of inline editors (Kilo DIFF_MAX_LINES). */
const val DIFF_MAX_LINES = 2000

/** Myers LCS line diff between old and new text (null old = creation). */
fun diffLines(oldText: String?, newText: String): List<DiffLine> {
    val a = oldText?.lines().orEmpty()
    val b = newText.lines()
    if (a.isEmpty()) return b.map { DiffLine(DiffOp.INSERT, it) }
    if (b.isEmpty()) return a.map { DiffLine(DiffOp.DELETE, it) }
    val n = a.size
    val m = b.size
    val max = n + m
    val v = IntArray(2 * max + 3)
    val trace = ArrayList<IntArray>()
    outer@ for (d in 0..max) {
        trace += v.copyOf()
        for (k in -d..d step 2) {
            val idx = k + max + 1
            var x = if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) v[idx + 1] else v[idx - 1] + 1
            var y = x - k
            while (x < n && y < m && a[x] == b[y]) { x++; y++ }
            v[idx] = x
            if (x >= n && y >= m) break@outer
        }
    }
    // Backtrack through the recorded v snapshots.
    val out = ArrayList<DiffLine>()
    var x = n
    var y = m
    for (d in trace.size - 1 downTo 0) {
        val vd = trace[d]
        val k = x - y
        val idx = k + max + 1
        val prevK = if (k == -d || (k != d && vd[idx - 1] < vd[idx + 1])) k + 1 else k - 1
        val prevX = vd[prevK + max + 1]
        val prevY = prevX - prevK
        while (x > prevX && y > prevY) {
            out += DiffLine(DiffOp.EQUAL, a[x - 1])
            x--; y--
        }
        if (d == 0) break
        if (x == prevX) {
            out += DiffLine(DiffOp.INSERT, b[y - 1])
            y--
        } else {
            out += DiffLine(DiffOp.DELETE, a[x - 1])
            x--
        }
    }
    return out.reversed()
}

/** Hunk grouping with the standard 2*context merge window. */
fun groupHunks(lines: List<DiffLine>, context: Int = 3): List<List<DiffLine>> {
    val changeIdx = lines.indices.filter { lines[it].op != DiffOp.EQUAL }
    if (changeIdx.isEmpty()) return emptyList()
    val clusters = ArrayList<MutableList<Int>>()
    for (idx in changeIdx) {
        val last = clusters.lastOrNull()
        if (last != null && idx - last.last() <= 2 * context + 1) {
            last += idx
        } else {
            clusters += mutableListOf(idx)
        }
    }
    val hunks = ArrayList<List<DiffLine>>()
    for (cluster in clusters) {
        val from = (cluster.first() - context).coerceAtLeast(0)
        val to = (cluster.last() + context).coerceAtMost(lines.size - 1)
        hunks += lines.subList(from, to + 1)
    }
    return hunks
}

/** One file's unified patch text. */
fun renderUnifiedPatch(change: FileChange): String {
    val sb = StringBuilder()
    // Strip a leading slash: absolute meta paths rendered "a//Users/..." (2026-09-02).
    val rel = change.path.removePrefix("/")
    val oldHeader = if (change.oldText == null) "/dev/null" else "a/" + rel
    val newHeader = if (change.newText.isEmpty() && change.oldText != null) "/dev/null" else "b/" + rel
    sb.append("--- ").append(oldHeader).append('\n')
    sb.append("+++ ").append(newHeader).append('\n')
    val lines = diffLines(change.oldText, change.newText)
    val hunks = groupHunks(lines)
    for (hunk in hunks) {
        val oldCount = hunk.count { it.op != DiffOp.INSERT }
        val newCount = hunk.count { it.op != DiffOp.DELETE }
        var oldStart = 0
        var newStart = 0
        for (l in lines) {
            if (l === hunk.first()) break
            if (l.op != DiffOp.INSERT) oldStart++
            if (l.op != DiffOp.DELETE) newStart++
        }
        sb.append("@@ -").append(oldStart + 1).append(',').append(oldCount)
            .append(" +").append(newStart + 1).append(',').append(newCount).append(" @@\n")
        for (l in hunk) {
            when (l.op) {
                DiffOp.EQUAL -> sb.append(' ')
                DiffOp.INSERT -> sb.append('+')
                DiffOp.DELETE -> sb.append('-')
            }
            sb.append(l.text).append('\n')
        }
    }
    return sb.toString().trimEnd('\n')
}

/** Total unified-diff line count across files (for the 2000-line cap). */
fun patchLineCount(changes: List<FileChange>): Int =
    changes.sumOf { diffLines(it.oldText, it.newText).size }
