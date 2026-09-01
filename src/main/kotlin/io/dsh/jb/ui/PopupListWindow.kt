package io.dsh.jb.ui

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.FlowLayout
import java.awt.Window
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** One popup row: bold title + gray detail, an optional tick, optional group header. */
data class PopupRow(
    val bold: String,
    val plain: String = "",
    val tick: Boolean = false,
    val header: Boolean = false,
) {
    val searchText: String get() = bold + " " + plain
}

/**
 * Item 18: Kilo-style list popup for the composer tabs — a real (focusable)
 * JWindow above the anchor with a search field and a list. Rows carry a bold
 * title + gray detail; the tick lives in its OWN fixed column so content stays
 * aligned; group headers render bold without a tick. Enter/click picks, Escape
 * or focus loss closes.
 */
class PopupListWindow(
    owner: Window,
    var onPick: (PopupRow) -> Unit,
) : JWindow(owner) {

    private val search = JBTextField()
    private val list = JBList<PopupRow>()
    private var items: List<PopupRow> = emptyList()
    // Item 19: typography snapshot refreshed per show (popups are short-lived).
    private var style: DshEditorStyle = DshEditorStyle.current()

    init {
        isAlwaysOnTop = true
        setFocusableWindowState(true)
        val root = JPanel(BorderLayout(4, 4))
        root.add(search, BorderLayout.NORTH)
        root.add(JBScrollPane(list).apply { preferredSize = Dimension(320, 220) }, BorderLayout.CENTER)
        contentPane.add(root)

        list.setCellRenderer { _, value, _, selected, _ ->
            val row = value ?: return@setCellRenderer JBLabel()
            val panel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
                isOpaque = true
                background = if (selected) list.selectionBackground else list.background
            }
            if (row.header) {
                panel.add(JBLabel("<html><b>${escapeHtml(row.bold)}</b></html>").apply {
                    font = style.smallFont.deriveFont(Font.BOLD)
                    foreground = if (selected) list.selectionForeground else com.intellij.ui.JBColor.GRAY
                })
                if (row.plain.isNotEmpty()) {
                    panel.add(JBLabel("<html><font color='#808080'>${escapeHtml(row.plain)}</font></html>").apply {
                        font = style.smallFont
                        foreground = if (selected) list.selectionForeground else com.intellij.ui.JBColor.GRAY
                    })
                }
            } else {
                // Fixed-width tick column so row content lines up without the glyph.
                val tick = JBLabel(if (row.tick) "✓" else "").apply {
                    preferredSize = Dimension(20, preferredSize.height)
                }
                panel.add(tick)
                panel.add(JBLabel("<html><b>${escapeHtml(row.bold)}</b></html>").apply {
                    font = style.boldFont
                    foreground = if (selected) list.selectionForeground else list.foreground
                })
                if (row.plain.isNotEmpty()) {
                    panel.add(JBLabel("<html><font color='#808080'>${escapeHtml(row.plain)}</font></html>").apply {
                        font = style.smallFont
                        foreground = if (selected) list.selectionForeground else com.intellij.ui.JBColor.GRAY
                    })
                }
            }
            panel
        }

        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val index = list.locationToIndex(e.point)
                val row = if (index >= 0) list.model.getElementAt(index) else null
                if (row != null) {
                    hide()
                    onPick(row)
                }
            }
        })
        search.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        val row = list.selectedValue
                        if (row != null) {
                            hide()
                            onPick(row)
                        }
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        hide()
                        e.consume()
                    }
                    KeyEvent.VK_DOWN -> {
                        if (list.model.size > 0) {
                            list.selectedIndex = (list.selectedIndex + 1).coerceAtMost(list.model.size - 1)
                            list.ensureIndexIsVisible(list.selectedIndex)
                        }
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        if (list.model.size > 0) {
                            list.selectedIndex = (list.selectedIndex - 1).coerceAtLeast(0)
                            list.ensureIndexIsVisible(list.selectedIndex)
                        }
                        e.consume()
                    }
                }
            }
        })
        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = filter()
            override fun removeUpdate(e: DocumentEvent) = filter()
            override fun changedUpdate(e: DocumentEvent) {}
        })
        addWindowFocusListener(object : WindowAdapter() {
            override fun windowLostFocus(e: WindowEvent) {
                hide()
            }
        })
    }

    /** Shows the window above [anchor] with the given rows and focuses the search field. */
    fun show(anchor: JComponent, rows: List<PopupRow>) {
        style = DshEditorStyle.current()
        search.font = style.regularFont
        items = rows
        search.text = ""
        list.setListData(items.toTypedArray())
        if (items.isNotEmpty()) list.selectedIndex = 0
        pack()
        val loc = anchor.locationOnScreen
        setLocation(loc.x, loc.y - height - 6)
        isVisible = true
        search.requestFocusInWindow()
    }

    private fun filter() {
        val q = search.text.lowercase()
        val filtered = items.filter { it.searchText.lowercase().contains(q) }
        list.setListData(filtered.toTypedArray())
        if (filtered.isNotEmpty()) list.selectedIndex = 0
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
