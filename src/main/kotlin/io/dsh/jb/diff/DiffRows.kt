package io.dsh.jb.diff

/** Item 23 (host round 2026-09-02): one gutter row — old/new line numbers for
 * a unified-patch line (null = blank on that side). Port of Kilo's
 * DiffLineNumbers.Row semantics. */
data class DiffRow(val old: Int?, val new: Int?)

private val HUNK_HEADER = Regex("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@")

/** Item 23 (host round 3): gutter rows derived DIRECTLY from the Myers diff
 * lines (the editor no longer shows patch markers, so hunk parsing is gone). */
fun gutterRows(lines: List<DiffLine>): List<DiffRow> {
    var old = 1
    var new = 1
    return lines.map { l ->
        when (l.op) {
            DiffOp.EQUAL -> DiffRow(old++, new++)
            DiffOp.INSERT -> DiffRow(null, new++)
            DiffOp.DELETE -> DiffRow(old++, null)
        }
    }
}

/** Pure parser: per-line old/new numbers driven by the hunk headers. */
fun diffRows(patch: String): List<DiffRow> {
    val rows = mutableListOf<Pair<String, DiffRow>>()
    var old = 0
    var new = 0
    var hunk = false
    patch.lineSequence().forEach { line ->
        if (line.startsWith("@@")) {
            HUNK_HEADER.find(line)?.let { match ->
                old = match.groupValues[1].toInt()
                new = match.groupValues[2].toInt()
            }
            hunk = true
            return@forEach
        }
        if (!hunk) return@forEach
        when {
            line.startsWith("+") -> rows.add(line to DiffRow(null, new++))
            line.startsWith("-") -> rows.add(line to DiffRow(old++, null))
            line.startsWith("\\") -> rows.add(line to DiffRow(null, null))
            else -> rows.add(line to DiffRow(old++, new++))
        }
    }
    // Trim blank edges like the patch body trim (Kilo's trimBlankEdges).
    val start = rows.indexOfFirst { it.first.isNotEmpty() }
    if (start < 0) return emptyList()
    val end = rows.indexOfLast { it.first.isNotEmpty() }
    return rows.subList(start, end + 1).map { it.second }
}
