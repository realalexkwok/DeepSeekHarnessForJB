package io.dsh.jb.ui

/** Item 21: collapsed tool-card preview length, in chars. */
internal const val TOOL_PREVIEW_CHARS = 160

/**
 * Item 21: pure preview-line extraction for a collapsed tool card — the first
 * non-blank line of the result, falling back to the pretty-printed arguments,
 * truncated with an ellipsis. Top-level and dependency-free for the pure-JVM
 * suite.
 */
internal fun toolPreviewLine(result: String, args: String, maxChars: Int = TOOL_PREVIEW_CHARS): String {
    val source = result.ifBlank { args }
    val line = source.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val cap = maxChars.coerceAtLeast(1)
    return if (line.length > cap) line.take(cap) + "…" else line
}
