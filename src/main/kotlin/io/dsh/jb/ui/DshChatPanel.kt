package io.dsh.jb.ui

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import io.dsh.jb.chat.AssistantRow
import io.dsh.jb.chat.AssistantStatus
import io.dsh.jb.chat.ChatTranscriptModel
import io.dsh.jb.chat.ComposerAction
import io.dsh.jb.chat.NoticeKind
import io.dsh.jb.chat.NoticeRow
import io.dsh.jb.chat.PromptAssembly
import io.dsh.jb.chat.PromptContext
import io.dsh.jb.chat.ToolCardRow
import io.dsh.jb.chat.ToolCardStatus
import io.dsh.jb.chat.TranscriptRow
import io.dsh.jb.chat.TranscriptState
import io.dsh.jb.chat.UserRow
import io.dsh.jb.diff.FileChange
import io.dsh.jb.diff.FsDiffParser
import io.dsh.jb.events.TodoItem
import io.dsh.jb.events.TodoStatus
import io.dsh.jb.runtime.DshConfigException
import io.dsh.jb.runtime.EffortLevel
import io.dsh.jb.runtime.RuntimeKey
import io.dsh.jb.services.DshRuntimeService
import io.dsh.jb.settings.DshApiKey
import io.dsh.jb.settings.DshSettingsConfigurable
import io.dsh.jb.settings.DshSettingsState
import io.dsh.jb.settings.ModelCatalog
import io.dsh.jb.settings.ModelInfo
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Roadmap item 5 chat transcript, with the roadmap item 6/10/11 composer pulled
 * forward (2026-08-27). The composer's bottom strip holds three TAB BUTTONS that
 * open POPUP MENUS (user feedback 2026-08-27): Context → checkable items Current
 * file + AGENTS.md; Context action → Ask / Execute / Plan / Fix (pending); Model →
 * Model + Effort radio submenus + Settings…. The submit icon sits right of the
 * buttons. Prompts route to the (model, effort)-keyed runtime pool.
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

    private val contextTab = tabButton("Context")
    private val actionTab = tabButton("Ask ▾")
    private val modelTab = tabButton("Model")
    private var currentAction: ComposerAction = ComposerAction.ASK
    private val contextFileItem = JCheckBoxMenuItem("Current file", true)
    private val contextAgentsItem = JCheckBoxMenuItem("AGENTS.md", true)
    private val askItem = JRadioButtonMenuItem("Ask")
    private val executeItem = JRadioButtonMenuItem("Execute")
    private val planItem = JRadioButtonMenuItem("Plan")
    private val fixItem = JRadioButtonMenuItem("Fix (pending)")
    private val modelSubmenu = JMenu("Model")
    private val effortMenu = JMenu("Effort")
    private val customItem = JMenuItem("Custom…")
    private val settingsItem = JMenuItem("Settings…")
    private val effortItems = EffortLevel.entries.associateWith { JRadioButtonMenuItem(it.display) }
    private val sendIcon = JButton(AllIcons.Actions.Execute).apply {
        toolTipText = "Send prompt"
        isContentAreaFilled = false
        isBorderPainted = false
    }
    private val input = JBTextArea().apply {
        rows = 3
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
    }

    private val rowWidgets = LinkedHashMap<String, RowWidget>()
    private var wasAtBottom = true
    private var modelIds: List<String> = emptyList()
    private var populatingModels = false
    private var settingsOpenedForKey = false
    private val loggedFailureIds = HashSet<String>()

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
        val diffButton: JButton,
        val metaToggle: JButton,
        val metaArea: JBTextArea,
    ) {
        /** Parsed result-time diffs, or null when the card shows the plain result. */
        var diffs: List<FileChange>? = null
    }

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
        val composerBlock = JPanel(BorderLayout())
        val inputScroll = JBScrollPane(input).apply { preferredSize = Dimension(0, 64) }
        composerBlock.add(inputScroll, BorderLayout.CENTER)
        val tabRow = JPanel(BorderLayout(8, 0))
        tabRow.border = JBUI.Borders.empty(4, 12, 6, 12)
        tabRow.add(buildComposerTabs(), BorderLayout.CENTER)
        tabRow.add(sendIcon, BorderLayout.EAST)
        composerBlock.add(tabRow, BorderLayout.SOUTH)
        bottom.add(composerBlock, BorderLayout.SOUTH)
        add(bottom, BorderLayout.SOUTH)

        add(scroll, BorderLayout.CENTER)

        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "dsh-send")
        input.actionMap.put("dsh-send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) { send() }
        })
        sendIcon.addActionListener { send() }
        scroll.verticalScrollBar.addAdjustmentListener { trackScroll() }
        rowsPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) { reflowAll() }
        })

        model.addListener { state -> ApplicationManager.getApplication().invokeLater { render(state) } }
        val service = DshRuntimeService.getInstance(project)
        service.addEventListener(model::onEvent)
        service.addStatusListener(model::onStatus)

        val s = DshSettingsState.getInstance().snapshot()
        effortItems[EffortLevel.fromWire(s.effort)]?.isSelected = true
        populateModels(ModelCatalog.KNOWN)
        scope.launch {
            val catalog = ModelCatalog.fetch(s.baseUrl, DshApiKey.get())
            ApplicationManager.getApplication().invokeLater { populateModels(catalog) }
        }
        scope.launch { startRuntime("") }
    }

    private fun tabButton(label: String): JButton = JButton(label).apply {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        border = JBUI.Borders.empty(2, 8)
    }

    /** Three bottom tab buttons, each opening a popup menu on click (user feedback 2026-08-27). */
    private fun buildComposerTabs(): JComponent {
        val contextMenu = JPopupMenu()
        contextMenu.add(contextFileItem)
        contextMenu.add(contextAgentsItem)
        contextTab.addActionListener { contextMenu.show(contextTab, 0, contextTab.height) }

        val actionMenu = JPopupMenu()
        val actionGroup = ButtonGroup()
        for (item in listOf(askItem, executeItem, planItem, fixItem)) {
            actionGroup.add(item)
            actionMenu.add(item)
        }
        fixItem.isEnabled = false
        fixItem.toolTipText = "Pending — semantics vs Execute not settled yet"
        askItem.addActionListener { actionSelected(ComposerAction.ASK) }
        executeItem.addActionListener { actionSelected(ComposerAction.EXECUTE) }
        planItem.addActionListener { actionSelected(ComposerAction.PLAN) }
        actionTab.addActionListener {
            // Re-assert the checks from the stored selection on every open, so the
            // current action is always visibly checked in the popped list.
            askItem.isSelected = currentAction == ComposerAction.ASK
            executeItem.isSelected = currentAction == ComposerAction.EXECUTE
            planItem.isSelected = currentAction == ComposerAction.PLAN
            actionMenu.show(actionTab, 0, actionTab.height)
        }

        val modelMenu = JPopupMenu()
        modelMenu.add(modelSubmenu)
        modelMenu.add(effortMenu)
        modelMenu.addSeparator()
        modelMenu.add(settingsItem)
        val effortGroup = ButtonGroup()
        for ((level, item) in effortItems) {
            effortGroup.add(item)
            effortMenu.add(item)
            item.addActionListener { effortSelected(level) }
        }
        customItem.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, DshSettingsConfigurable::class.java)
        }
        settingsItem.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, DshSettingsConfigurable::class.java)
        }
        modelTab.addActionListener { modelMenu.show(modelTab, 0, modelTab.height) }

        val strip = JPanel(BorderLayout(8, 0))
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        buttons.add(contextTab)
        buttons.add(actionTab)
        buttons.add(modelTab)
        strip.add(buttons, BorderLayout.CENTER)
        return strip
    }

    private fun currentModelId(): String =
        DshSettingsState.getInstance().snapshot().model.trim().ifBlank { ModelCatalog.DEFAULT_MODEL }

    private fun currentKey(): RuntimeKey = RuntimeKey(
        model = currentModelId(),
        effort = EffortLevel.fromWire(DshSettingsState.getInstance().snapshot().effort),
    )

    private fun populateModels(models: List<ModelInfo>) {
        populatingModels = true
        try {
            modelIds = models.map { it.id }.toMutableList()
            val current = currentModelId()
            if (current !in modelIds) modelIds = modelIds + current
            modelSubmenu.removeAll()
            val group = ButtonGroup()
            for (id in modelIds) {
                val item = JRadioButtonMenuItem(ModelCatalog.displayNameFor(id))
                item.isSelected = id == current
                item.addActionListener { if (!populatingModels) modelSelected(id) }
                group.add(item)
                modelSubmenu.add(item)
            }
            modelSubmenu.addSeparator()
            modelSubmenu.add(customItem)
        } finally {
            populatingModels = false
        }
    }

    /** Stores the selected action and mirrors it on the tab button label. */
    private fun actionSelected(action: ComposerAction) {
        currentAction = action
        actionTab.text = "${action.display} ▾"
    }

    private fun modelSelected(id: String) {
        val s = DshSettingsState.getInstance()
        if (id == s.model) return
        s.model = id
        scope.launch { startRuntime("Model changed to ${ModelCatalog.displayNameFor(id)} — restarting runtime") }
    }

    private fun effortSelected(level: EffortLevel) {
        val s = DshSettingsState.getInstance()
        if (level.wire == s.effort) return
        s.effort = level.wire
        scope.launch { startRuntime("Effort changed to ${level.display} — restarting runtime") }
    }

    private suspend fun startRuntime(noticeText: String) {
        try {
            val service = DshRuntimeService.getInstance(project)
            val key = currentKey()
            service.startFor(key)
            startedOk = true
            ApplicationManager.getApplication().invokeLater {
                model.notice(
                    "New session ${service.sessionIdFor(key)} started" +
                        if (noticeText.isNotEmpty()) " — $noticeText" else "",
                )
                render(model.state())
            }
        } catch (e: Exception) {
            logger.warn("DSH runtime start failed", e)
            if (e is DshConfigException) {
                ApplicationManager.getApplication().invokeLater {
                    model.notice("${e.message} — configuring DeepSeek Harness")
                    statusLabel.text = "DSH agent: not configured"
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, DshSettingsConfigurable::class.java)
                }
            } else {
                ApplicationManager.getApplication().invokeLater {
                    model.notice(
                        "Runtime start failed: ${e.message ?: e.javaClass.simpleName} — " +
                            "check Settings → Tools → DeepSeek Harness",
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
        val assembled = PromptAssembly.assemble(text, gatherContext())
        model.echoPrompt(assembled)
        scope.launch {
            try {
                DshRuntimeService.getInstance(project).prompt(assembled)
            } catch (e: Exception) {
                logger.warn("prompt failed", e)
                ApplicationManager.getApplication().invokeLater {
                    model.notice("Send failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    private fun gatherContext(): PromptContext {
        val editor: Editor? = FileEditorManager.getInstance(project).selectedTextEditor
        val file = editor?.let { FileDocumentManager.getInstance().getFile(it.document) }
        val selection = editor?.selectionModel?.selectedText?.takeIf { it.isNotBlank() }
        val agentsText = project.basePath
            ?.let { File(it, "AGENTS.md") }
            ?.takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
        val action = currentAction
        return PromptContext(
            action = action,
            includeCurrentFile = contextFileItem.isSelected,
            includeAgents = contextAgentsItem.isSelected,
            currentFilePath = file?.path,
            currentFileContent = editor?.document?.text,
            selection = selection,
            agentsContent = agentsText,
        )
    }

    private fun render(state: TranscriptState) {
        statusLabel.text = when {
            !startedOk -> "DSH agent: starting…"
            state.running -> "DSH agent: running…"
            else -> "DSH agent: idle"
        }
        planBadge.isVisible = state.planMode

        // One-shot proactive ask when the API key is missing (roadmap item 10).
        if (!settingsOpenedForKey &&
            state.rows.any { it is NoticeRow && it.kind == NoticeKind.API_KEY_MISSING }
        ) {
            settingsOpenedForKey = true
            ShowSettingsUtil.getInstance().showSettingsDialog(project, DshSettingsConfigurable::class.java)
        }

        // Mirror every failure notice's FULL text to idea.log once (2026-08-28),
        // so the log always carries the complete error even when the UI is small.
        for (row in state.rows) {
            if (row is NoticeRow && loggedFailureIds.add(row.id) && isFailureNotice(row)) {
                logger.warn("DSH transcript failure: ${row.text}")
            }
        }

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

    /**
     * Whole-file diff fallback (harness-documented behavior): when a write/edit
     * result carries no contextual hunks (creates, declined basis), show the
     * current file content as a "(new file)" diff. Workspace-confined.
     */
    private fun fallbackDiff(toolName: String, arguments: String): List<FileChange>? {
        val path = FsDiffParser.filePathFromArguments(toolName, arguments) ?: return null
        val base = File(project.basePath ?: ".").canonicalFile
        val target = File(base, path).canonicalFile
        val basePrefix = base.path + File.separator
        if (target != base && !target.path.startsWith(basePrefix)) return null
        val content = runCatching { target.readText() }.getOrNull() ?: return null
        return listOf(FileChange(path, null, content))
    }

    private fun isFailureNotice(row: NoticeRow): Boolean =
        row.kind == NoticeKind.API_KEY_MISSING ||
            (row.kind == NoticeKind.NOTICE && row.text.contains("failed", ignoreCase = true)) ||
            (row.kind == NoticeKind.TURN_END && row.text.contains("error", ignoreCase = true))

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
        val diffButton = JButton("Diff").apply { isVisible = false }
        val metaToggle = JButton("raw meta ▸")
        val metaArea = transcriptText(mono = true).apply { isVisible = false }
        val body = JPanel(BorderLayout())
        val controls = JPanel(BorderLayout(8, 0))
        controls.add(diffButton, BorderLayout.WEST)
        controls.add(errorLabel, BorderLayout.EAST)
        body.add(controls, BorderLayout.NORTH)
        val content = JPanel(BorderLayout())
        content.add(argsArea, BorderLayout.NORTH)
        content.add(resultArea, BorderLayout.CENTER)
        body.add(content, BorderLayout.CENTER)
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
        val parts = ToolParts(panel, header, argsArea, resultArea, errorLabel, diffButton, metaToggle, metaArea)
        diffButton.addActionListener {
            parts.diffs?.let { d ->
                DshDiffDialog(project, File(project.basePath ?: "."), d).show()
            }
        }
        parts.update(row)
        return parts
    }

    private fun noticePanel(row: NoticeRow): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(2, 12)
        // Selectable + wrapping so the full text is visible and copyable (2026-08-28).
        val label = transcriptText().apply {
            text = row.text
            foreground = JBColor.GRAY
            font = font.deriveFont(Font.ITALIC, font.size2D - 1f)
        }
        when (row.kind) {
            NoticeKind.PLAN_MODE, NoticeKind.API_KEY_MISSING ->
                label.foreground = JBColor(0xb26b00, 0xdfb14a)
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
        val parsed = FsDiffParser.parse(row.meta)
        diffs = parsed ?: fallbackDiff(row.name, row.arguments)
        diffButton.isVisible = diffs != null
        diffs?.let { diffButton.text = "Diff (${it.size} file${if (it.size == 1) "" else "s"})" }
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
