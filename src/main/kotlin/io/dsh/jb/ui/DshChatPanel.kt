package io.dsh.jb.ui

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import io.dsh.jb.chat.AssistantRow
import io.dsh.jb.chat.AssistantStatus
import io.dsh.jb.chat.ChatTranscriptModel
import io.dsh.jb.chat.NoticeKind
import io.dsh.jb.chat.NoticeRow
import io.dsh.jb.chat.ToolCardRow
import io.dsh.jb.chat.ToolCardStatus
import io.dsh.jb.chat.TranscriptRow
import io.dsh.jb.chat.TranscriptState
import io.dsh.jb.chat.UserRow
import io.dsh.jb.events.TodoItem
import io.dsh.jb.events.TodoStatus
import io.dsh.jb.services.DshRuntimeService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Roadmap item 5: the DSH chat transcript. The pure-JVM [ChatTranscriptModel]
 * folds the session-event stream; this panel renders rows on the EDT and owns
 * the composer, the todo panel, the plan-mode badge and the running/idle line.
 */
class DshChatPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val model = ChatTranscriptModel()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logger = Logger.getInstance(DshChatPanel::class.java)
    private val mapper = jacksonObjectMapper()

    private val statusLabel = JBLabel("DSH agent: starting…")
    private val planBadge = JBLabel("PLAN MODE").apply {
        foreground = JBColor(0xb26b00, 0xdfb14a)
        isVisible = false
    }
    private val rowsPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val scroll = JBScrollPane(rowsPanel).apply {
        border = null
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }
    private val todoToggle = JButton("Todos (0) ▾")
    private val todoList = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val input = JBTextArea().apply {
        rows = 3
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
    }
    private val sendButton = JButton("Send")

    private val rowWidgets = LinkedHashMap<String, RowWidget>()
    private var wasAtBottom = true

    @Volatile
    private var startedOk = false

    private sealed class RowWidget {
        abstract fun component(): JComponent
        class Static(private val c: JComponent) : RowWidget() {
            override fun component() = c
        }
        class Assistant(val parts: AssistantParts) : RowWidget() {
            override fun component() = parts.panel
        }
        class Tool(val parts: ToolParts) : RowWidget() {
            override fun component() = parts.panel
        }
    }

    private class AssistantParts(
        val panel: JComponent,
        val header: JBLabel,
        val textArea: JBTextArea,
        val thinkingToggle: JButton,
        val thinkingArea: JBTextArea,
        val usage: JBLabel,
    )

    private class ToolParts(
        val panel: JComponent,
        val header: JBLabel,
        val argsArea: JBTextArea,
        val resultArea: JBTextArea,
        val errorLabel: JBLabel,
        val metaToggle: JButton,
        val metaArea: JBTextArea,
    )

    init {
        val header = JPanel(BorderLayout())
        header.border = JBUI.Borders.empty(8, 12, 4, 12)
        header.add(statusLabel, BorderLayout.WEST)
        header.add(planBadge, BorderLayout.EAST)
        add(header, BorderLayout.NORTH)

        val bottom = JPanel(BorderLayout())
        val todoWrap = JPanel(BorderLayout())
        todoWrap.border = JBUI.Borders.emptyTop(4)
        todoWrap.add(todoToggle, BorderLayout.NORTH)
        todoWrap.add(todoList, BorderLayout.CENTER)
        todoToggle.addActionListener {
            todoList.isVisible = !todoList.isVisible
            todoToggle.text = "Todos (${model.state().todos.size}) " + if (todoList.isVisible) "▾" else "▸"
            todoWrap.revalidate()
        }
        bottom.add(todoWrap, BorderLayout.NORTH)
        val composer = JPanel(BorderLayout(8, 0))
        composer.border = JBUI.Borders.empty(8, 12, 12, 12)
        val inputScroll = JBScrollPane(input).apply { preferredSize = Dimension(0, 64) }
        composer.add(inputScroll, BorderLayout.CENTER)
        composer.add(sendButton, BorderLayout.EAST)
        bottom.add(composer, BorderLayout.SOUTH)
        add(bottom, BorderLayout.SOUTH)

        add(scroll, BorderLayout.CENTER)

        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "dsh-send")
        input.actionMap.put("dsh-send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) { send() }
        })
        sendButton.addActionListener { send() }
        scroll.verticalScrollBar.addAdjustmentListener { trackScroll() }
        rowsPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) { reflowAll() }
        })

        model.addListener { state -> ApplicationManager.getApplication().invokeLater { render(state) } }
        val service = DshRuntimeService.getInstance(project)
        service.addEventListener(model::onEvent)
        service.addStatusListener(model::onStatus)
        scope.launch {
            try {
                service.start()
                startedOk = true
                ApplicationManager.getApplication().invokeLater { render(model.state()) }
            } catch (e: Exception) {
                logger.warn("DSH runtime start failed", e)
                ApplicationManager.getApplication().invokeLater {
                    model.notice(
                        "Runtime start failed: ${e.message ?: e.javaClass.simpleName} — " +
                            "set DSH_RUNTIME_MODE=node and DSH_CHECKOUT to a DeepSeek Harness " +
                            "checkout before starting the IDE (a settings page arrives with roadmap item 10)",
                    )
                    statusLabel.text = "DSH agent: start failed"
                }
            }
        }
    }

    private fun send() {
        val text = input.text.trim()
        if (text.isEmpty()) return
        input.text = ""
        model.echoPrompt(text)
        scope.launch {
            try {
                DshRuntimeService.getInstance(project).prompt(text)
            } catch (e: Exception) {
                logger.warn("prompt failed", e)
                ApplicationManager.getApplication().invokeLater {
                    model.notice("Send failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun render(state: TranscriptState) {
        statusLabel.text = when {
            !startedOk -> "DSH agent: starting…"
            state.running -> "DSH agent: running…"
            else -> "DSH agent: idle"
        }
        planBadge.isVisible = state.planMode

        val seen = HashSet<String>()
        var changed = false
        for (row in state.rows) {
            seen += row.id
            val widget = rowWidgets[row.id]
            when {
                widget == null -> {
                    rowWidgets[row.id] = createWidget(row)
                    rowsPanel.add(rowWidgets[row.id]!!.component())
                    changed = true
                }
                widget is RowWidget.Assistant && row is AssistantRow -> {
                    widget.parts.update(row)
                    changed = true
                }
                widget is RowWidget.Tool && row is ToolCardRow -> {
                    widget.parts.update(row)
                    changed = true
                }
            }
        }
        for (id in rowWidgets.keys.filter { it !in seen }) {
            rowsPanel.remove(rowWidgets.remove(id)!!.component())
            changed = true
        }
        renderTodos(state.todos)
        if (changed) {
            reflowAll()
            if (wasAtBottom) {
                SwingUtilities.invokeLater { scroll.verticalScrollBar.value = scroll.verticalScrollBar.maximum }
            }
        }
    }

    private fun trackScroll() {
        val bar = scroll.verticalScrollBar
        wasAtBottom = bar.value + bar.model.extent >= bar.maximum - 24
    }

    private fun createWidget(row: TranscriptRow): RowWidget = when (row) {
        is UserRow -> RowWidget.Static(userPanel(row))
        is AssistantRow -> RowWidget.Assistant(assistantParts(row))
        is ToolCardRow -> RowWidget.Tool(toolParts(row))
        is NoticeRow -> RowWidget.Static(noticePanel(row))
    }

    private fun userPanel(row: UserRow): JComponent {
        val panel = rowShell()
        val name = JBLabel(if (row.pending) "You · sending…" else "You").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(Font.BOLD)
        }
        val text = transcriptText().apply { text = row.content }
        if (row.pending) text.foreground = JBColor.GRAY
        panel.add(name, BorderLayout.NORTH)
        panel.add(text, BorderLayout.CENTER)
        return panel
    }

    private fun assistantParts(row: AssistantRow): AssistantParts {
        val panel = rowShell()
        val header = JBLabel().apply { font = font.deriveFont(Font.BOLD) }
        val textArea = transcriptText()
        val thinkingToggle = JButton("▸ Thinking")
        val thinkingArea = transcriptText().apply {
            foreground = JBColor.GRAY
            isVisible = false
        }
        val usage = JBLabel("").apply { foreground = JBColor.GRAY }
        val body = JPanel(BorderLayout())
        body.add(thinkingToggle, BorderLayout.NORTH)
        body.add(thinkingArea, BorderLayout.NORTH)
        body.add(textArea, BorderLayout.CENTER)
        panel.add(header, BorderLayout.NORTH)
        panel.add(body, BorderLayout.CENTER)
        panel.add(usage, BorderLayout.SOUTH)
        thinkingToggle.addActionListener {
            val show = !thinkingArea.isVisible
            thinkingArea.isVisible = show
            thinkingToggle.text = if (show) "▾ Thinking" else "▸ Thinking"
            panel.revalidate()
        }
        val parts = AssistantParts(panel, header, textArea, thinkingToggle, thinkingArea, usage)
        parts.update(row)
        return parts
    }

    private fun toolParts(row: ToolCardRow): ToolParts {
        val panel = rowShell()
        val header = JBLabel().apply { font = font.deriveFont(Font.BOLD) }
        val argsArea = transcriptText(mono = true)
        val resultArea = transcriptText()
        val errorLabel = JBLabel("").apply { foreground = JBColor(0xc0392b, 0xe06c75) }
        val metaToggle = JButton("raw meta ▸")
        val metaArea = transcriptText(mono = true).apply { isVisible = false }
        val body = JPanel(BorderLayout())
        body.add(argsArea, BorderLayout.NORTH)
        body.add(resultArea, BorderLayout.CENTER)
        body.add(errorLabel, BorderLayout.SOUTH)
        val metaWrap = JPanel(BorderLayout())
        metaWrap.add(metaToggle, BorderLayout.NORTH)
        metaWrap.add(metaArea, BorderLayout.CENTER)
        panel.add(header, BorderLayout.NORTH)
        panel.add(body, BorderLayout.CENTER)
        panel.add(metaWrap, BorderLayout.SOUTH)
        metaToggle.addActionListener {
            val show = !metaArea.isVisible
            metaArea.isVisible = show
            metaToggle.text = if (show) "raw meta ▾" else "raw meta ▸"
            panel.revalidate()
        }
        val parts = ToolParts(panel, header, argsArea, resultArea, errorLabel, metaToggle, metaArea)
        parts.update(row)
        return parts
    }

    private fun noticePanel(row: NoticeRow): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(2, 12)
        val label = JBLabel(row.text).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(Font.ITALIC, font.size2D - 1f)
        }
        when (row.kind) {
            NoticeKind.PLAN_MODE -> label.foreground = JBColor(0xb26b00, 0xdfb14a)
            NoticeKind.APPROVAL_ASKED, NoticeKind.APPROVAL_DECIDED ->
                label.font = label.font.deriveFont(Font.BOLD, label.font.size2D)
            else -> Unit
        }
        panel.add(label, BorderLayout.CENTER)
        return panel
    }

    private fun AssistantParts.update(row: AssistantRow) {
        header.text = when {
            row.status == AssistantStatus.STREAMING -> "DSH · streaming…"
            row.interrupted -> "DSH · interrupted"
            else -> "DSH"
        }
        textArea.text = row.text
        thinkingArea.text = row.thinking
        thinkingToggle.isVisible = row.thinking.isNotBlank()
        if (row.thinking.isNotBlank() && thinkingToggle.text.startsWith("▸")) thinkingToggle.text = "▸ Thinking"
        usage.text = row.usage?.let { "↑ ${it.inputTokens} ↓ ${it.outputTokens} tokens" } ?: ""
        usage.isVisible = row.usage != null
    }

    private fun ToolParts.update(row: ToolCardRow) {
        header.text = when {
            row.status == ToolCardStatus.RUNNING -> "⚙ ${row.name} · running…"
            row.isError -> "⚙ ${row.name} · error"
            else -> "⚙ ${row.name} · done"
        }
        argsArea.text = prettyJson(row.arguments)
        resultArea.text = row.resultText
        val errorText = row.errorName.orEmpty() + row.errorCode?.let { " ($it)" }.orEmpty()
        errorLabel.text = errorText
        errorLabel.isVisible = row.isError
        metaToggle.isVisible = row.meta != null
        if (row.meta != null && metaArea.text.isEmpty()) {
            metaArea.text = prettyJson(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(row.meta))
        }
    }

    private fun renderTodos(todos: List<TodoItem>) {
        todoToggle.text = "Todos (${todos.size}) " + if (todoList.isVisible) "▾" else "▸"
        todoList.removeAll()
        for (todo in todos) {
            val glyph = when (todo.statusValue) {
                TodoStatus.COMPLETED -> "✓"
                TodoStatus.IN_PROGRESS -> "▶"
                TodoStatus.PENDING, null -> "☐"
            }
            todoList.add(JBLabel("$glyph  ${todo.content}").apply { border = JBUI.Borders.empty(1, 12) })
        }
        todoList.revalidate()
        todoList.repaint()
    }

    private fun transcriptText(mono: Boolean = false): JBTextArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
        if (mono) font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    private fun rowShell(): JPanel = JPanel(BorderLayout()).apply {
        border = JBUI.Borders.empty(8, 12)
        maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
    }

    private fun prettyJson(raw: String): String = try {
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(raw))
    } catch (_: Exception) {
        raw
    }

    private fun reflowAll() {
        val width = rowsPanel.width
        if (width <= 0) return
        for (widget in rowWidgets.values) {
            when (widget) {
                is RowWidget.Assistant -> {
                    sizeToContent(widget.parts.textArea, width)
                    sizeToContent(widget.parts.thinkingArea, width)
                }
                is RowWidget.Tool -> {
                    sizeToContent(widget.parts.argsArea, width)
                    sizeToContent(widget.parts.resultArea, width)
                    sizeToContent(widget.parts.metaArea, width)
                }
                is RowWidget.Static -> Unit
            }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun sizeToContent(area: JBTextArea, width: Int) {
        area.size = Dimension((width - 32).coerceAtLeast(40), Int.MAX_VALUE)
        val height = area.preferredSize.height.coerceAtLeast(area.minimumSize.height)
        area.preferredSize = Dimension(area.preferredSize.width, height)
    }

    override fun dispose() {
        scope.cancel()
    }
}
