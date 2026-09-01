package io.dsh.jb.ui

import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Path2D
import javax.swing.JPanel

/**
 * Item 20 follow-up (host feedback 2026-09-01): Kilo-style composer shell —
 * the editor and the tab strip form ONE surface, and the focus ring surrounds
 * the WHOLE block, not the editor alone (Kilo PromptPanel.surface +
 * paintChildren pattern, reimplemented). The platform text-area outline
 * (DarculaScrollPaneBorder) is stripped at the call site so nothing paints
 * editor-only chrome; this shell owns the only frame.
 */
class ComposerShell(
    /** Focus source: the composer input inside this shell. */
    private val focused: () -> Boolean,
) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        // Reserve the ring width so the layout never jumps between states.
        border = JBUI.Borders.empty(FOCUS_INSET, FOCUS_INSET, FOCUS_INSET, FOCUS_INSET)
    }

    override fun paintChildren(g: Graphics) {
        super.paintChildren(g)
        if (!focused()) return
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val line = JBUI.scale(FOCUS_WIDTH).toFloat()
            g2.color = JBUI.CurrentTheme.Focus.focusColor()
            g2.stroke = BasicStroke(line, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
            g2.draw(surface(line / 2f))
        } finally {
            g2.dispose()
        }
    }

    /** Rounded outline of the whole shell — mirrors Kilo's PromptPanel.surface. */
    private fun surface(inset: Float): Path2D.Float {
        val left = inset + insets.left
        val top = inset + insets.top
        val right = width - inset - insets.right
        val bottom = height - inset - insets.bottom
        val radius = (JBUI.scale(JBUI.getInt("Island.arc", CORNER_ARC)) / 2f)
            .coerceAtMost((right - left) / 2f)
            .coerceAtMost(bottom - top)
            .coerceAtLeast(0f)
        return Path2D.Float().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom - radius)
            if (radius > 0f) {
                quadTo(right, bottom, right - radius, bottom)
                lineTo(left + radius, bottom)
                quadTo(left, bottom, left, bottom - radius)
            } else {
                lineTo(right, bottom)
                lineTo(left, bottom)
            }
            closePath()
        }
    }

    companion object {
        private const val FOCUS_WIDTH = 2
        private const val FOCUS_INSET = 2
        private const val CORNER_ARC = 10
    }
}
