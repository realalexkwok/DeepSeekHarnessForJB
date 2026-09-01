package io.dsh.jb.ui

/** Item 20: composer growth cap, in lines. */
internal const val MAX_COMPOSER_LINES = 10

/**
 * Item 20: pure composer height clamp — the measured wrapped content height
 * is clamped to [1 line, maxLines lines]. Top-level and dependency-free so
 * the pure-JVM suite can cover it without loading IntelliJ classes.
 */
internal fun clampComposerHeight(content: Int, lineHeight: Int, maxLines: Int): Int {
    val line = lineHeight.coerceAtLeast(1)
    return content.coerceIn(line, line * maxLines.coerceAtLeast(1))
}
