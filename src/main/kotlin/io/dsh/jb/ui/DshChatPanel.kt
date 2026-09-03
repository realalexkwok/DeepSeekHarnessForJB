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
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import io.dsh.jb.chat.AssistantRow
import io.dsh.jb.chat.AssistantStatus
import io.dsh.jb.chat.ChatTranscriptModel
import io.dsh.jb.chat.ComposerAction
import io.dsh.jb.chat.NoticeKind
import io.dsh.jb.chat.NoticeRow
import io.dsh.jb.chat.PromptAssembly
import io.dsh.jb.chat.PermissionRow
import io.dsh.jb.chat.PromptContext
import io.dsh.jb.chat.QuestionRow
import io.dsh.jb.chat.ReasoningRow
import io.dsh.jb.bridge.BridgeApproval
import io.dsh.jb.bridge.PlanAnswer
import io.dsh.jb.bridge.PlanQuestion
import io.dsh.jb.permission.PermissionLevel
import io.dsh.jb.settings.PermissionSettings
import io.dsh.jb.chat.ToolCardRow
import io.dsh.jb.chat.ToolCardStatus
import io.dsh.jb.chat.TranscriptRow
import io.dsh.jb.chat.TranscriptState
import io.dsh.jb.protocol.SessionEventNotification
import io.dsh.jb.protocol.SessionStatusNotification
import io.dsh.jb.chat.UserRow
import io.dsh.jb.diff.DIFF_MAX_LINES
import io.dsh.jb.diff.DiffLine
import io.dsh.jb.diff.DiffOp
import io.dsh.jb.diff.FileChange
import io.dsh.jb.diff.FsDiffParser
import io.dsh.jb.diff.DiffRow
import io.dsh.jb.diff.diffLines
import io.dsh.jb.diff.gutterRows
import io.dsh.jb.diff.patchLineCount
import io.dsh.jb.events.TodoItem
import io.dsh.jb.events.TodoStatus
import io.dsh.jb.runtime.DshConfigException
import io.dsh.jb.history.HistoryStore
import io.dsh.jb.runtime.EffortLevel
import io.dsh.jb.runtime.RuntimeKey
import io.dsh.jb.services.DshRuntimeService
import io.dsh.jb.settings.DshApiKey
import io.dsh.jb.settings.DshSettingsConfigurable
import io.dsh.jb.settings.DshSettingsState
import io.dsh.jb.settings.ModelCatalog
import io.dsh.jb.settings.ModelInfo
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.TextAnnotationGutterProvider
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.EditorTextField
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.awt.RelativePoint
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Point
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.event.HyperlinkEvent
import javax.swing.Icon
import javax.swing.JCheckBox
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
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.text.DefaultEditorKit
import com.intellij.ui.components.TextComponentEmptyText
import javax.swing.text.JTextComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Roadmap item 5 chat transcript, with the roadmap item 6/10/11 composer pulled
 * forward (2026-08-27). The composer's bottom strip holds three TAB BUTTONS that
 * open POPUP MENUS (user feedback 2026-08-27): Context → checkable items Current
 * file + AGENTS.md; Context action → Ask / Code / Plan / Debug; Model →
 * Model + Effort radio submenus + Settings…. The submit icon sits right of the
 * buttons. Prompts route to the (model, effort)-keyed runtime pool.
 */
class DshChatPanel(private val project: Project) : JPanel(BorderLayout()), Disposable, DshStyleTarget {

    // Item 19: the typography snapshot every surface reads; declared FIRST so
    // field initializers (contextPreview, transcriptText) can use it.
    private var style: DshEditorStyle = DshEditorStyle.current()

    private val model = ChatTranscriptModel()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logger = Logger.getInstance(DshChatPanel::class.java)
    private val mapper = jacksonObjectMapper()

    // Item 22: per-project session history (Kilo model — project-scoped log +
    // metadata index). Disabled when the project has no base path.
    private val history: HistoryStore? = project.basePath
        ?.takeIf { it.isNotBlank() }
        ?.let { HistoryStore(File(it)) }
    private var historyId: String = UUID.randomUUID().toString()
    // Item 22 follow-up (host feedback 2026-09-01): the New Session / History /
    // Settings chrome lives in the tool-window TITLE BAR (DshToolWindowFactory
    // setTitleActions + setAdditionalGearActions), not in this header row.
    // Item 22 follow-up: Kilo-style IN-WINDOW history view (Kilo HistoryPanel
    // pattern — the CENTER swaps between transcript and history; Back restores).
    private val historyView = JPanel(BorderLayout())
    private val historyBackButton = iconButton(AllIcons.Actions.Back, "Back")
    private val historySearch = JBTextField()
    private val historyListPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val historyListScroll = JBScrollPane(historyListPanel).apply { border = null }
    private var historyViewVisible = false
    private val recentPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val recentScroll = JBScrollPane(recentPanel).apply { border = null }
    private var emptyStateVisible = false
    /** Item 22 (host feedback 2026-09-01): after New session the chat stays
     * CLEAN — the Recent list appears only on a freshly opened panel. */
    private var suppressEmptyState = false

    private val statusLabel = JBLabel("DSH agent: starting…")
    private val planBadge = JBLabel("PLAN MODE").apply {
        foreground = JBColor(0xb26b00, 0xdfb14a)
        isVisible = false
    }
    private val stopButton = JButton("Stop").apply {
        isVisible = false
        addActionListener { stopSession() }
    }
    private val rowsPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val scroll = JBScrollPane(rowsPanel).apply {
        border = null
        horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    }
    private val todoToggle = JButton("Todos (0) ▾")
    private val todoList = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    // Item 23 (host round 4): bulk decision row above the Todos section.
    private val bulkActionsRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 4)).apply {
        isOpaque = false
        isVisible = false
    }
    private val acceptAllBtn = JButton("Accept All", AllIcons.Actions.Commit).apply {
        toolTipText = "Keep every pending change (they are already applied)"
    }
    private val rejectAllBtn = JButton("Reject All", AllIcons.Actions.Cancel).apply {
        toolTipText = "Undo every pending change"
    }

    // Item 15: Kilo-style context chips above the composer, with a collapsible
    // preview (collapsed by default; X removes the context).
    private val contextChips = JPanel(WrapLayout()).apply { isOpaque = false }
    private val contextPreview = transcriptText().apply {
        border = BorderFactory.createEmptyBorder(4, 12, 4, 12)
        foreground = JBColor.GRAY
    }
    private val contextPreviewScroll = JBScrollPane(contextPreview).apply {
        preferredSize = Dimension(0, 90)
        isVisible = false
    }
    /** The chip whose preview is open, so every chip previews and removal dismisses it. */
    private var activeChip: String? = null

    // Item 5/17b: Kilo-style visibility-gated update queue — incoming events are
    // batched into ONE 150 ms EDT flush with a pre-batch scroll snapshot, so the
    // transcript never re-renders per chunk (Kilo SessionUpdateQueue pattern).
    private val pendingEvents = java.util.Collections.synchronizedList(mutableListOf<SessionEventNotification>())
    private val pendingStatuses = java.util.Collections.synchronizedList(mutableListOf<SessionStatusNotification>())
    private val flushTimer = javax.swing.Timer(150) { flushTranscript() }.apply { isRepeats = true }

    private val contextTab = tabButton("Context")
    private val actionTab = tabButton("Ask ▾")
    private val modelTab = tabButton("Model")
    private var currentAction: ComposerAction = ComposerAction.ASK
    private val contextFileItem = JCheckBoxMenuItem("Current file", true)
    private val contextAgentsItem = JCheckBoxMenuItem("AGENTS.md", true)
    private val askItem = JRadioButtonMenuItem("Ask")
    private val executeItem = JRadioButtonMenuItem("Code")
    private val planItem = JRadioButtonMenuItem("Plan")
    private val fixItem = JRadioButtonMenuItem("Debug")
    private val modelSubmenu = JMenu("Model")
    private val effortMenu = JMenu("Effort")
    private val customItem = JMenuItem("Custom…")
    private val settingsItem = JMenuItem("Settings…")
    private val effortItems = EffortLevel.entries.associateWith { JRadioButtonMenuItem(it.display) }
    // Item 24 (replanned): Kilo's shield auto-approve toggle (Lock/Unlock
    // platform icons stand in for Kilo's custom shield SVGs).
    private val autoApproveBtn = JButton().apply {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
    }
    private val permissionResponders = ConcurrentHashMap<String, (String) -> Unit>()
    private val questionResponders = ConcurrentHashMap<String, (PlanAnswer) -> Unit>()
    private val sendIcon = JButton(AllIcons.Actions.Execute).apply {
        toolTipText = "Send prompt"
        isContentAreaFilled = false
        isBorderPainted = false
    }
    private val input = JBTextArea().apply {
        // Item 20: rows is only the pre-measurement fallback — the dynamic
        // height (updateComposerHeight) takes over on the first layout pass.
        rows = 1
        lineWrap = true
        wrapStyleWord = true
        border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
        // Item 20 follow-up (host feedback 2026-09-01): the borderless editor
        // needs a placeholder — keep it visible while focused (Kilo's
        // setShowPlaceholderWhenFocused behavior).
        emptyText.setText(COMPOSER_HINT)
        TextComponentEmptyText.setupPlaceholderVisibility(this)
    }
    // Item 20: no fixed height — updateComposerHeight measures the content
    // and clamps it to [1 line, MAX_COMPOSER_LINES lines]. A field so the
    // member function can resize it. Follow-up: the platform text-area outline
    // (DarculaScrollPaneBorder) is stripped — ComposerShell owns the only frame.
    private val inputScroll = JBScrollPane(input).apply {
        border = null
        viewportBorder = null
    }

    // Item 14: @-mention file picker — a NON-FOCUSABLE JWindow above the
    // composer (JPopupMenu steals keyboard focus once visible, which froze the
    // input after the first letter — manual-test issue 2026-08-30).
    private val mentionList = JBList<String>()
    private var mentionWindow: JWindow? = null
    private var mentionStart = -1
    /** Set while inserting a chosen file so the inserted `@path` does not re-open the picker. */
    private var mentionJustInserted = false

    private val rowWidgets = LinkedHashMap<String, RowWidget>()
    private var wasAtBottom = true
    private var lastMax = 0
    private var lastRowsWidth = -1
    private var modelIds: List<String> = emptyList()
    private var populatingModels = false
    private var settingsOpenedForKey = false
    private val loggedFailureIds = HashSet<String>()

    @Volatile
    private var startedOk = false

    private sealed class RowWidget {
        abstract fun component(): JComponent
        /** Item 19: typed parts so [applyStyle] can restyle static rows in place. */
        class Static(val parts: StaticParts) : RowWidget() {
            override fun component() = parts.panel
        }
        class Assistant(val parts: AssistantParts) : RowWidget() {
            override fun component() = parts.panel
        }
        class Tool(val parts: ToolParts) : RowWidget() {
            override fun component() = parts.panel
        }
        class Reasoning(val parts: ReasoningParts) : RowWidget() {
            override fun component() = parts.panel
        }
        class Permission(val parts: PermissionParts) : RowWidget() {
            override fun component() = parts.panel
        }
        class Question(val parts: QuestionParts) : RowWidget() {
            override fun component() = parts.panel
        }
    }

    /** Item 24 (host round): inline question card parts. */
    private class QuestionParts(
        val panel: JComponent,
        val textArea: JBTextArea,
    )

    /** Item 24 (replanned): inline permission card parts. */
    private class PermissionParts(
        val panel: JComponent,
        val textArea: JBTextArea,
    ) {
        var rowId: String = ""
    }

    /** Item 23 (host round 6): the standalone "Reasoning" card under a tool card. */
    private class ReasoningParts(
        val panel: JComponent,
        val headerBlock: JComponent,
        val arrow: JBLabel,
        val bodyArea: JBTextArea,
        val body: JComponent,
    ) {
        var collapsed = true
    }

    private class StaticParts(
        val panel: JComponent,
        val header: JBLabel?,
        val text: JTextComponent,
        val small: Boolean = false,
        val hint: Int = Font.PLAIN,
    )

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
        val headerBlock: JComponent,
        val header: JPanel,
        val nameLabel: JBLabel,
        val expandBtn: JBLabel,
        val preview: JPanel,
        val body: JComponent,
        val argsArea: JBTextArea,
        val resultArea: JBTextArea,
        val bodyCenter: JPanel,
        val diffSection: JPanel,
        val errorLabel: JBLabel,
        val metaToggle: JButton,
        val metaArea: JBTextArea,
        val metaWrap: JPanel,
    ) {
        /** Parsed result-time diffs, or null when the card shows the plain result. */
        var diffs: List<FileChange>? = null
        /** Item 21: cards are collapsed by default and NEVER auto-expand. */
        var collapsed = true
        /** Previous tool name — a name change re-collapses the card. */
        var lastName: String? = null
        var name: String = ""
        var stateText: String = ""
        var argumentsJson: String = ""
        var resultText: String = ""
        /** Item 23 (host round): per-file decisions survive section rebuilds. */
        val decisions: MutableMap<String, DiffDecision> = HashMap()
    }

    /** Item 23 (host round): Accept = keep (already applied); Reject = undone. */
    private enum class DiffDecision { ACCEPTED, REJECTED }

    init {
        // Item 19: re-snapshot typography on GLOBAL scheme changes. Evidence
        // (platform bytecode 2025.1.3, 2026-09-01): Settings → Editor → Font
        // goes through EditorColorsManagerImpl.setGlobalScheme →
        // syncPublisher(TOPIC), which may deliver OFF the EDT — so the handler
        // must hop to the EDT (Kilo wraps it the same way). NOTE: Ctrl/Cmd+wheel
        // zoom is a PER-EDITOR local override (MyColorSchemeDelegate.myFontSize,
        // PropertyChange "fontSize" only) — it never touches the global scheme,
        // so the chat follows Settings-driven font changes, not per-editor wheel
        // zoom (Kilo has the identical limitation on this platform).
        val styleBus = ApplicationManager.getApplication().messageBus.connect(this)
        styleBus.subscribe(EditorColorsManager.TOPIC, object : EditorColorsListener {
            override fun globalSchemeChange(scheme: EditorColorsScheme?) {
                ApplicationManager.getApplication().invokeLater {
                    logger.info("style: globalSchemeChange transcriptFont=" + DshEditorStyle.current().transcriptFont.size)
                    applyStyle(DshEditorStyle.current())
                }
            }
        })
        styleBus.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            ApplicationManager.getApplication().invokeLater { applyStyle(DshEditorStyle.current()) }
        })
        val header = JPanel(BorderLayout())
        header.border = JBUI.Borders.empty(8, 12, 4, 12)
        header.add(statusLabel, BorderLayout.WEST)
        val headerRight = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }
        headerRight.add(stopButton)
        headerRight.add(planBadge)
        header.add(headerRight, BorderLayout.EAST)
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
        // Item 23 (host round 4): Accept All / Reject All sit above the Todos.
        bulkActionsRow.add(acceptAllBtn)
        bulkActionsRow.add(rejectAllBtn)
        acceptAllBtn.addActionListener { bulkDecide(true) }
        rejectAllBtn.addActionListener { bulkDecide(false) }
        val todoArea = JPanel(BorderLayout())
        todoArea.add(bulkActionsRow, BorderLayout.NORTH)
        todoArea.add(todoWrap, BorderLayout.CENTER)
        bottom.add(todoArea, BorderLayout.NORTH)
        // Item 15: context chips + preview sit between todos and the composer.
        val contextWrap = JPanel(BorderLayout())
        contextWrap.add(contextChips, BorderLayout.NORTH)
        contextWrap.add(contextPreviewScroll, BorderLayout.CENTER)
        bottom.add(contextWrap, BorderLayout.CENTER)
        contextFileItem.addActionListener { refreshContextChips() }
        contextAgentsItem.addActionListener { refreshContextChips() }
        val composerBlock = JPanel(BorderLayout())
        // Item 20 follow-up: editor + tab row live in ONE Kilo-style shell;
        // the focus ring (ComposerShell) surrounds the whole block.
        val composerShell = ComposerShell { input.hasFocus() }
        composerShell.add(inputScroll, BorderLayout.CENTER)
        val tabRow = JPanel(BorderLayout(8, 0))
        tabRow.border = JBUI.Borders.empty(4, 12, 6, 12)
        tabRow.add(buildComposerTabs(), BorderLayout.CENTER)
        val rightWrap = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }
        rightWrap.add(autoApproveBtn)
        rightWrap.add(sendIcon)
        tabRow.add(rightWrap, BorderLayout.EAST)
        composerShell.add(tabRow, BorderLayout.SOUTH)
        composerBlock.add(composerShell, BorderLayout.CENTER)
        bottom.add(composerBlock, BorderLayout.SOUTH)
        input.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) { composerShell.repaint() }
            override fun focusLost(e: FocusEvent) { composerShell.repaint() }
        })
        add(bottom, BorderLayout.SOUTH)

        add(scroll, BorderLayout.CENTER)

        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "dsh-send")
        input.actionMap.put("dsh-send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) { submit() }
        })
        // Item 20: explicit Shift+Enter → newline (Enter keeps sending).
        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "dsh-newline")
        input.actionMap.put("dsh-newline", DefaultEditorKit.InsertBreakAction())
        sendIcon.addActionListener { submit() }
        // Icon state is set HERE, not in the field initializer: inside the
        // initializer's apply block the field is still null (2026-09-03 NPE).
        syncAutoApproveIcon()
        autoApproveBtn.addActionListener {
            PermissionSettings.setAutoApprove(!PermissionSettings.getAutoApprove())
            syncAutoApproveIcon()
        }
        // Item 14: @-mention picker wiring (window created lazily once the
        // panel has a window ancestor).
        mentionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 1) return
                // Resolve the clicked entry directly: the list selection can lag
                // behind the click event (manual-test issue 2026-08-30).
                val index = mentionList.locationToIndex(e.point)
                val entry = if (index >= 0) mentionList.model.getElementAt(index) else null
                if (entry != null) acceptMentionEntry(entry) else acceptMention()
            }
        })
        // Keyboard control lives on the INPUT, never on the list: moving focus
        // into the popup makes Swing close it (bare-@ popup vanished instantly,
        // Enter never reached the list — manual-test issues 2026-08-30).
        input.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (mentionWindow?.isVisible != true) return
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> {
                        logger.info("mention: enter entry='${mentionList.selectedValue}' window=${mentionWindow?.isVisible}")
                        acceptMention()
                        e.consume()
                    }
                    KeyEvent.VK_ESCAPE -> {
                        hideMentions()
                        e.consume()
                    }
                    KeyEvent.VK_DOWN -> {
                        moveMentionSelection(1)
                        e.consume()
                    }
                    KeyEvent.VK_UP -> {
                        moveMentionSelection(-1)
                        e.consume()
                    }
                }
            }
        })
        input.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                updateMentions()
                updateComposerHeight()
            }
            override fun removeUpdate(e: DocumentEvent) {
                updateMentions()
                updateComposerHeight()
            }
            override fun changedUpdate(e: DocumentEvent) { updateComposerHeight() }
        })
        // The scrollbar MODEL fires on EVERY value change — including mouse-wheel
        // scrolls, which AdjustmentListener never sees (it only fires on thumb
        // drags). Missing wheel events kept wasAtBottom=true after the user
        // scrolled up, so streaming re-pinned the viewport to the bottom
        // (manual-test failure 2026-08-30).
        scroll.verticalScrollBar.model.addChangeListener { trackScroll() }
        // Disengage auto-follow on SCROLL INTENT, not on accumulated position:
        // a scroll-up gesture arrives as many small wheel frames, and each pin
        // snaps the view back before the offset can pass any tolerance — so the
        // position-based check can never trip and the pins win frame by frame
        // (2026-08-30 re-test 3: scrollbar oscillation). Any upward wheel frame
        // disengages immediately; scrolling back down to the bottom re-engages
        // through trackScroll.
        scroll.addMouseWheelListener { e ->
            when {
                e.wheelRotation < 0 -> wasAtBottom = false
                e.wheelRotation > 0 -> SwingUtilities.invokeLater { trackScroll() }
            }
        }
        // Thumb drags are also explicit user intent: disengage the moment a
        // drag starts; the release position is re-evaluated by trackScroll.
        scroll.verticalScrollBar.addAdjustmentListener { e ->
            if (e.valueIsAdjusting) wasAtBottom = false
        }
        rowsPanel.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                // Only reflow when the WIDTH changed: firing on every resize
                // (including the layout's own pass) created a feedback storm
                // that moved the viewport (2026-08-31 scroll trace).
                val w = rowsPanel.width
                if (w != lastRowsWidth) {
                    lastRowsWidth = w
                    reflowAll()
                    // Item 20: the composer re-wraps when the panel width changes.
                    updateComposerHeight()
                }
            }
        })

        val service = DshRuntimeService.getInstance(project)
        service.addEventListener(::enqueueEvent)
        service.addStatusListener(::enqueueStatus)
        // Item 24 (replanned): approvals render as INLINE cards; the card's
        // buttons resolve the per-ask latch back to the bridge.
        service.addApprovalListener { approval, respond ->
            ApplicationManager.getApplication().invokeLater { showPermissionCard(approval, respond) }
        }
        // Item 24 (host round): generic ask_user_question cards (plan reviews
        // stay modal).
        service.addQuestionListener { question, respond ->
            ApplicationManager.getApplication().invokeLater { showQuestionCard(question, respond) }
        }
        flushTimer.start()
        // Roadmap item 11 remainder: editor context actions preload the composer.
        project.getService(ComposerRequests::class.java).addListener { action, text ->
            ApplicationManager.getApplication().invokeLater {
                actionSelected(action)
                input.text = text
                input.requestFocusInWindow()
            }
        }

        val s = DshSettingsState.getInstance().snapshot()
        effortItems[EffortLevel.fromWire(s.effort)]?.isSelected = true
        populateModels(ModelCatalog.KNOWN)
        scope.launch {
            val catalog = ModelCatalog.fetch(s.baseUrl, DshApiKey.get())
            ApplicationManager.getApplication().invokeLater { populateModels(catalog) }
        }
        scope.launch { startRuntime("") }
        refreshContextChips()
        // Item 15: the construction-time refresh runs before the tool window
        // lays this panel out and before the editor context is visible — so
        // re-refresh when the panel becomes displayable and on every focus
        // regain (manual-test 2026-08-31: the initial chip stayed hidden until
        // the first keystroke).
        addHierarchyListener(object : HierarchyListener {
            override fun hierarchyChanged(e: HierarchyEvent) {
                if (e.changeFlags.toLong() and HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() != 0L && isDisplayable) {
                    ApplicationManager.getApplication().invokeLater { refreshContextChips() }
                }
            }
        })
        addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                ApplicationManager.getApplication().invokeLater { refreshContextChips() }
            }
        })
        applyStyle(DshEditorStyle.current())
        // Item 22 follow-up: assemble the in-window history view.
        val historyTop = JPanel(BorderLayout(8, 0))
        historyTop.border = JBUI.Borders.empty(4, 12, 4, 12)
        historyTop.add(historyBackButton, BorderLayout.WEST)
        historyTop.add(historySearch, BorderLayout.CENTER)
        historyView.add(historyTop, BorderLayout.NORTH)
        historyView.add(historyListScroll, BorderLayout.CENTER)
        historyBackButton.addActionListener { backFromHistory() }
        historySearch.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { refreshHistoryList() }
            override fun removeUpdate(e: DocumentEvent) { refreshHistoryList() }
            override fun changedUpdate(e: DocumentEvent) {}
        })
        refreshHistoryUi()
    }

    private fun tabButton(label: String): JButton = JButton(label).apply {
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        border = JBUI.Borders.empty(2, 8)
    }

    /** Three bottom tab buttons, each opening a Kilo-style searchable list popup (item 18). */
    private fun buildComposerTabs(): JComponent {
        contextTab.addActionListener { showPopup(contextTab, contextRows()) { row -> onContextPick(row) } }
        actionTab.addActionListener { showPopup(actionTab, actionRows()) { row -> onActionPick(row) } }
        modelTab.addActionListener { showPopup(modelTab, modelRows()) { row -> onModelPick(row) } }
        val strip = JPanel(BorderLayout(8, 0))
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        buttons.add(contextTab)
        buttons.add(actionTab)
        buttons.add(modelTab)
        strip.add(buttons, BorderLayout.CENTER)
        return strip
    }

    /** Kilo-style: the selected row carries a bare tick in its OWN column, so
     * row content stays aligned with or without the glyph. The tick column lives
     * in the popup renderer; rows here only carry the boolean. */
    private fun row(bold: String, plain: String, tick: Boolean) = PopupRow(bold, plain, tick)

    private fun contextRows(): List<PopupRow> = listOf(
        row("Current file", "attach the open editor file", contextFileItem.isSelected),
        row("AGENTS.md", "workspace rules", contextAgentsItem.isSelected),
    )

    private fun onContextPick(row: PopupRow) {
        when (row.bold) {
            "Current file" -> {
                contextFileItem.isSelected = !contextFileItem.isSelected
                refreshContextChips()
            }
            "AGENTS.md" -> {
                contextAgentsItem.isSelected = !contextAgentsItem.isSelected
                refreshContextChips()
            }
        }
    }

    private fun actionRows(): List<PopupRow> = listOf(
        row("Ask", "answer without modifying", currentAction == ComposerAction.ASK),
        row("Code", "act on the request", currentAction == ComposerAction.EXECUTE),
        row("Plan", "plan before applying", currentAction == ComposerAction.PLAN),
        row("Debug", "diagnose and repair", currentAction == ComposerAction.FIX),
    )

    private fun onActionPick(row: PopupRow) {
        val action = ComposerAction.entries.firstOrNull { it.display == row.bold } ?: return
        actionSelected(action)
    }

    /** Group labels Model / Effort / Settings… are all bold. Model and Effort
     * are plain headers (not pickable); Settings… is its OWN group label — not
     * a row under Effort (host feedback 2026-09-01) — and still opens the
     * settings page when picked. */
    private fun modelRows(): List<PopupRow> {
        val rows = mutableListOf<PopupRow>()
        rows += PopupRow("Model", "", header = true)
        for (id in modelIds) {
            rows += row(ModelCatalog.displayNameFor(id), "", id == currentModelId())
        }
        rows += PopupRow("Effort", "", header = true)
        for (level in EffortLevel.entries) {
            rows += row(level.display, "", level.wire == DshSettingsState.getInstance().snapshot().effort)
        }
        rows += PopupRow("Settings…", "opens the settings page", header = true)
        return rows
    }

    private fun onModelPick(row: PopupRow) {
        if (row.bold == "Settings…") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, DshSettingsConfigurable::class.java)
            return
        }
        if (row.header) return
        val effort = EffortLevel.entries.firstOrNull { it.display == row.bold }
        if (effort != null) {
            effortSelected(effort)
            return
        }
        val id = modelIds.firstOrNull { ModelCatalog.displayNameFor(it) == row.bold }
        if (id != null) modelSelected(id)
    }

    /** Item 18: a FRESH popup window per show — reuse leaked stale rows across
    * tabs (all tabs showed the Context list, manual-test 2026-09-01). */
    private var popup: PopupListWindow? = null

    private fun showPopup(anchor: JComponent, rows: List<PopupRow>, handler: (PopupRow) -> Unit) {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        popup?.dispose()
        val win = PopupListWindow(owner, handler)
        popup = win
        logger.info("popup: firstRow='${rows.firstOrNull()}' size=${rows.size}")
        win.show(anchor, rows)
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
        refreshContextChips()
        // Typed slash commands route through the bridge command relay — they
        // must NOT reach the model as prompt text (2026-08-30 manual-test bug:
        // a literal "/plan" prompt ran a real 10-minute agentic turn).
        if (text == "/plan" || text == "/plan off") {
            sendCommand(text)
            return
        }
        // Item 8: the Plan action enters real plan mode via the bridge command
        // relay (no-op when no runtime/bridge is live; the advisory instruction
        // from PromptAssembly still applies either way).
        if (currentAction == ComposerAction.PLAN) {
            DshRuntimeService.getInstance(project).enqueueCommand("/plan")
        }
        // Item 22: one history entry per conversation; the first prompt line
        // becomes the entry title.
        rotateHistoryIfNeeded()
        history?.appendPrompt(historyId, text)
        val assembled = PromptAssembly.assemble(text, gatherContext())
        model.echoPrompt(assembled)
        scope.launch {
            val service = DshRuntimeService.getInstance(project)
            try {
                if (!service.isRunning()) startRuntime("")
                service.prompt(assembled)
            } catch (e: Exception) {
                logger.warn("prompt failed", e)
                ApplicationManager.getApplication().invokeLater {
                    model.notice("Send failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    /** `/plan` and `/plan off`: relay to the harness and mirror the composer mode. */
    private fun sendCommand(command: String) {
        val entering = command == "/plan"
        actionSelected(if (entering) ComposerAction.PLAN else ComposerAction.ASK)
        scope.launch {
            val service = DshRuntimeService.getInstance(project)
            try {
                if (!service.isRunning()) startRuntime("")
                service.enqueueCommand(command)
                ApplicationManager.getApplication().invokeLater {
                    model.notice(if (entering) "Entering plan mode — the harness will plan before applying changes" else "Leaving plan mode")
                }
            } catch (e: Exception) {
                logger.warn("command failed", e)
                ApplicationManager.getApplication().invokeLater {
                    model.notice("Command failed: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Kilo-style submit: the send button doubles as Stop while the agent runs
     * (see kilocode's StopSessionAction + isStopEnabled toggle).
     */
    private fun submit() {
        if (model.state().running) stopSession() else send()
    }

    /**
     * Stops the running turn with a Kilo-style process-tree kill
     * (SIGTERM → grace → SIGKILL); the next send restarts the runtime.
     */
    private fun stopSession() {
        scope.launch {
            DshRuntimeService.getInstance(project).interrupt()
            ApplicationManager.getApplication().invokeLater {
                model.onStatus(io.dsh.jb.protocol.SessionStatusNotification("", "idle"))
                model.notice("Session stopped — send a new prompt to restart")
                statusLabel.text = "DSH agent: idle"
                render(model.state())
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
        // Item 14: resolve @<path> tokens to workspace-confined file content.
        val base = File(project.basePath ?: ".").canonicalFile
        val basePrefix = base.path + File.separator
        val mentionedFiles = PromptAssembly.parseMentions(input.text)
            .take(MAX_MENTIONS)
            .mapNotNull { mentionPath ->
                val target = File(base, mentionPath).canonicalFile
                if (target.isFile && target.path.startsWith(basePrefix)) {
                    runCatching {
                        io.dsh.jb.chat.MentionedFile(mentionPath, target.readText().take(MAX_MENTION_BYTES))
                    }.getOrNull()
                } else {
                    null
                }
            }
        return PromptContext(
            action = action,
            includeCurrentFile = contextFileItem.isSelected,
            includeAgents = contextAgentsItem.isSelected,
            currentFilePath = file?.path,
            currentFileContent = editor?.document?.text,
            selection = selection,
            agentsContent = agentsText,
            mentionedFiles = mentionedFiles,
        )
    }

    /** Item 14: refresh the @-mention picker for the trailing `@token`. */
    private fun updateMentions() {
        // Chips refresh on EVERY document change, before any picker logic: the
        // early returns below skipped them, so mention chips never appeared
        // (manual-test 2026-08-31).
        refreshContextChips()
        if (mentionJustInserted) {
            // The just-inserted `@path` must not re-open the picker.
            mentionJustInserted = false
            hideMentions()
            return
        }
        val text = input.text
        // Caret-independent: during the FIRST typed character's document event
        // the caret has not advanced yet, so searching behind it misses the `@`
        // (bare-@ never opened — manual-test issue 2026-08-30).
        val idx = text.lastIndexOf('@')
        if (idx < 0) {
            hideMentions()
            return
        }
        val span = text.substring(idx + 1)
        if (span.contains(' ') || span.contains('\n')) {
            hideMentions()
            return
        }
        mentionStart = idx
        val entries = collectMentionPaths(span)
        mentionList.setListData(entries.toTypedArray())
        // Debug trace (manual-test 2026-08-30): what the picker decided.
        logger.info("mention: span='$span' entries=${entries.size} idx=$idx windowVisible=${mentionWindow?.isVisible == true}")
        if (entries.isNotEmpty()) {
            mentionList.selectedIndex = 0
            showMentions()
        } else {
            hideMentions()
        }
    }

    /** Shows the picker window above the composer WITHOUT taking keyboard focus. */
    private fun showMentions() {
        val owner = SwingUtilities.getWindowAncestor(this) ?: return
        val win = mentionWindow ?: JWindow(owner).also { w ->
            w.contentPane.add(JBScrollPane(mentionList).apply { preferredSize = Dimension(320, 220) })
            w.setFocusableWindowState(false)
            w.isAlwaysOnTop = true
            mentionWindow = w
        }
        win.pack()
        val loc = input.locationOnScreen
        win.setLocation(loc.x + 12, loc.y - win.height - 6)
        win.isVisible = true
    }

    /** Arrow navigation while the popup is open; wraps within the list bounds. */
    private fun moveMentionSelection(delta: Int) {
        val size = mentionList.model.size
        if (size == 0) return
        val current = mentionList.selectedIndex.coerceAtLeast(0)
        mentionList.selectedIndex = (current + delta + size) % size
        mentionList.ensureIndexIsVisible(mentionList.selectedIndex)
    }

    /**
     * Item 14, Kilo look & feel (kilocode KiloPromptCompletionProvider): a FLAT
     * completion of workspace files shown by their FULL project-relative path;
     * the typed prefix filters (name OR path), name matches rank first. Hidden
     * dirs and build outputs excluded, capped.
     */
    private fun collectMentionPaths(query: String): List<String> {
        val base = File(project.basePath ?: ".").canonicalFile
        val basePrefix = base.path + File.separator
        val q = query.lowercase()
        val hits = mutableListOf<String>()
        runCatching {
            base.walkTopDown()
                .onEnter { dir ->
                    val n = dir.name
                    !(n.startsWith(".") || n == "node_modules" || n == "build" || n == "out" ||
                        n == "dist" || n == ".gradle")
                }
                .maxDepth(10)
                .filter { it.isFile }
                .forEach { f ->
                    val relative = f.path.removePrefix(basePrefix)
                    if (q.isEmpty() || relative.lowercase().contains(q)) {
                        if (hits.size < MAX_MENTION_CANDIDATES) hits += relative
                    }
                }
        }
        if (q.isNotEmpty()) {
            hits.sortBy { if (it.substringAfterLast('/').lowercase().contains(q)) 0 else 1 }
        }
        return hits
    }

    /**
     * Kilo semantics (click and Enter both apply the entry): insert the FULL
     * workspace-relative path with a trailing space, exactly like Kilo's
     * `replace(ctx, "@${file.path} ", …)` insert handler.
     */
    private fun acceptMentionEntry(entry: String) {
        val start = mentionStart
        hideMentions()
        if (start < 0) return
        val text = input.text
        val caret = input.caretPosition.coerceIn(start + 1, text.length)
        val before = text.substring(0, start)
        val after = text.substring(caret)
        val inserted = "@" + entry + " "
        mentionJustInserted = true
        input.text = before + inserted + after
        input.caretPosition = before.length + inserted.length
        input.requestFocusInWindow()
        logger.info("mention: inserted $inserted")
    }

    private fun acceptMention() {
        val selected = mentionList.selectedValue ?: run {
            hideMentions()
            return
        }
        acceptMentionEntry(selected)
    }

    private fun hideMentions() {
        mentionStart = -1
        mentionWindow?.isVisible = false
    }
    /** Item 15: rebuild the chips for the active context sources. */
    private fun refreshContextChips() {
        contextChips.removeAll()
        val titles = mutableListOf<String>()
        val editor: Editor? = FileEditorManager.getInstance(project).selectedTextEditor
        val file = editor?.let { FileDocumentManager.getInstance().getFile(it.document) }
        if (contextFileItem.isSelected && file != null) {
            val title = "Current file: " + file.name
            addChip(title, { editor?.document?.text ?: "" }) {
                contextFileItem.isSelected = false
                refreshContextChips()
            }
            titles += title
        }
        val agents = project.basePath
            ?.let { File(it, "AGENTS.md") }
            ?.takeIf { it.isFile }
            ?.let { runCatching { it.readText() }.getOrNull() }
        if (contextAgentsItem.isSelected && agents != null) {
            addChip("AGENTS.md", { agents }) {
                contextAgentsItem.isSelected = false
                refreshContextChips()
            }
            titles += "AGENTS.md"
        }
        val base = File(project.basePath ?: ".").canonicalFile
        val basePrefix = base.path + File.separator
        for (mention in PromptAssembly.parseMentions(input.text)) {
            val f = File(base, mention).canonicalFile
            if (f.isFile && f.path.startsWith(basePrefix)) {
                val title = "@" + mention
                addChip(title, { runCatching { f.readText() }.getOrDefault("") }) {
                    val token = java.util.regex.Pattern.quote(mention)
                    input.text = input.text.replace("@" + token + " ", " ").replace("@" + token, "")
                    refreshContextChips()
                }
                titles += title
            }
        }
        // Dismiss the preview when its chip is gone (X or token removal) —
        // manual-test 2026-08-31: the preview outlived a closed chip.
        if (activeChip != null && activeChip !in titles) {
            activeChip = null
            contextPreviewScroll.isVisible = false
        }
        // Revalidate the chip panel AND its parent: BorderLayout must see the
        // WRAPPED height (plain FlowLayout under-reports it and clips wrapped
        // rows — manual-test 2026-08-31).
        contextChips.revalidate()
        (contextChips.parent as? JComponent)?.revalidate()
        contextChips.repaint()
    }

    /** One chip: label (click to preview) + X (remove). */
    private fun addChip(label: String, content: () -> String, remove: () -> Unit) {
        val chip = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
            border = BorderFactory.createLineBorder(JBColor.GRAY, 1, true)
            isOpaque = false
        }
        val labelC = JBLabel(label).apply {
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) = togglePreview(label, content)
            })
        }
        val x = JButton("×").apply {
            border = BorderFactory.createEmptyBorder(0, 2, 0, 2)
            isContentAreaFilled = false
            isBorderPainted = false
            addActionListener { remove() }
        }
        chip.add(labelC)
        chip.add(x)
        contextChips.add(chip)
    }

    /** Per-chip preview: the ACTIVE chip drives the panel; re-click dismisses. */
    private fun togglePreview(title: String, content: () -> String) {
        if (activeChip == title && contextPreviewScroll.isVisible) {
            activeChip = null
            contextPreviewScroll.isVisible = false
        } else {
            activeChip = title
            contextPreview.text = title + "\n" + content().take(PREVIEW_BYTES)
            contextPreviewScroll.isVisible = true
        }
        revalidate()
        repaint()
    }


    /** Batched EDT flush: applies all pending events to the model, then renders once. */
    private fun flushTranscript() {
        if (!isShowing) return
        val events: List<SessionEventNotification> = synchronized(pendingEvents) {
            pendingEvents.toList().also { pendingEvents.clear() }
        }
        val statuses: List<SessionStatusNotification> = synchronized(pendingStatuses) {
            pendingStatuses.toList().also { pendingStatuses.clear() }
        }
        if (events.isEmpty() && statuses.isEmpty()) return
        // Snapshot the follow state BEFORE the batch applies (Kilo's rule).
        val follow = wasAtBottom
        events.forEach(model::onEvent)
        statuses.forEach(model::onStatus)
        // Item 22: record the batch as ONE multi-line history write.
        if (events.isNotEmpty()) {
            runCatching {
                history?.appendEvents(historyId, events.map { "event" to mapper.writeValueAsString(it) })
            }
        }
        render(model.state(), follow)
    }

    /** Item 23 (host round 4): the bulk row shows while any card has pending changes. */
    private fun refreshBulkActions() {
        bulkActionsRow.isVisible = pendingDecisions().isNotEmpty()
        bulkActionsRow.revalidate()
        bulkActionsRow.repaint()
    }

    private fun pendingDecisions(): List<Pair<ToolParts, FileChange>> =
        rowWidgets.values
            .filterIsInstance<RowWidget.Tool>()
            .flatMap { w ->
                w.parts.diffs.orEmpty()
                    .filter { w.parts.decisions[it.path] == null }
                    .map { w.parts to it }
            }

    /** Item 23 (host round 4): Accept All dismisses every pending pair; Reject
     * All reverts every pending change on disk, then dismisses them too. */
    private fun bulkDecide(accept: Boolean) {
        var n = 0
        for (widget in rowWidgets.values) {
            if (widget !is RowWidget.Tool) continue
            val parts = widget.parts
            val changes = parts.diffs ?: continue
            for (change in changes) {
                if (parts.decisions[change.path] != null) continue
                if (accept) {
                    parts.decisions[change.path] = DiffDecision.ACCEPTED
                } else if (revertChange(change)) {
                    parts.decisions[change.path] = DiffDecision.REJECTED
                } else {
                    continue
                }
                n++
            }
            parts.rebuildDiffSection()
        }
        model.notice(if (accept) "Accepted $n change(s)" else "Rejected $n change(s) — undone")
        render(model.state())
        refreshBulkActions()
    }

    private fun enqueueEvent(e: SessionEventNotification) {
        pendingEvents += e
    }

    private fun enqueueStatus(s: SessionStatusNotification) {
        pendingStatuses += s
    }

    /**
     * Item 22: starts a FRESH conversation — stops any running turn (the next
     * send lazily restarts the runtime), clears the transcript + composer,
     * and rotates to a new history entry. Synchronous so no async notice can
     * leak into the fresh transcript (Kilo NewSessionAction pattern).
     */
    internal fun startNewSession() {
        if (model.state().running) {
            scope.launch { DshRuntimeService.getInstance(project).interrupt() }
            model.onStatus(SessionStatusNotification("", "idle"))
        }
        historyId = UUID.randomUUID().toString()
        model.reset()
        rowsPanel.removeAll()
        rowWidgets.clear()
        input.text = ""
        suppressEmptyState = true
        render(model.state(), followOverride = false)
        refreshHistoryUi()
        if (historyViewVisible) refreshHistoryList()
        statusLabel.text = "DSH agent: idle"
        logger.info("history: new session id=$historyId")
    }

    /** Item 22: one history entry per conversation — rotate only when the
     * transcript was empty at send time and the current entry has content. */
    private fun rotateHistoryIfNeeded() {
        if (model.state().rows.isEmpty() && history?.hasContent(historyId) == true) {
            historyId = UUID.randomUUID().toString()
        }
    }

    /** Item 22: Kilo-style empty-state Recent sessions list + CENTER swap. */
    private fun refreshHistoryUi() {
        if (historyViewVisible) return
        val entries = history?.entries() ?: emptyList()
        val show = !suppressEmptyState && model.state().rows.isEmpty() && entries.isNotEmpty()
        if (show) {
            recentPanel.removeAll()
            for (e in entries.take(RECENT_SESSIONS_LIMIT)) {
                recentPanel.add(recentRow(e))
            }
            recentPanel.revalidate()
            recentPanel.repaint()
            if (!emptyStateVisible) {
                remove(scroll)
                add(recentScroll, BorderLayout.CENTER)
                emptyStateVisible = true
                revalidate()
                repaint()
            }
        } else if (emptyStateVisible) {
            remove(recentScroll)
            add(scroll, BorderLayout.CENTER)
            emptyStateVisible = false
            revalidate()
            repaint()
        }
    }

    private fun recentRow(e: HistoryStore.Entry): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(6, 12)
        val title = JBLabel(e.title.ifBlank { "Untitled session" }).apply { font = style.boldFont }
        val sub = JBLabel(timeAgo(e.updated)).apply {
            foreground = JBColor.GRAY
            font = style.smallFont
        }
        panel.add(title, BorderLayout.NORTH)
        panel.add(sub, BorderLayout.SOUTH)
        panel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(ev: MouseEvent) {
                if (ev.clickCount == 1) replaySession(e.id)
            }
        })
        return panel
    }

    private fun timeAgo(t: Long): String {
        val mins = (System.currentTimeMillis() - t) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            else -> "${mins / (60 * 24)}d ago"
        }
    }

    /** Item 22 follow-up: Kilo-style icon-only chrome button. */
    private fun iconButton(icon: Icon, tip: String): JButton = JButton(icon).apply {
        toolTipText = tip
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusPainted = false
        border = JBUI.Borders.empty(2, 2)
    }

    /** Item 22 follow-up: toggles the in-window history view (Kilo HistoryPanel). */
    internal fun showHistoryView() {
        if (historyViewVisible) {
            backFromHistory()
            return
        }
        remove(scroll)
        remove(recentScroll)
        emptyStateVisible = false
        refreshHistoryList()
        add(historyView, BorderLayout.CENTER)
        historyViewVisible = true
        revalidate()
        repaint()
    }

    /** Item 22 follow-up: closes the history view and restores the chat. */
    private fun backFromHistory() {
        if (!historyViewVisible) return
        remove(historyView)
        historyViewVisible = false
        emptyStateVisible = false
        add(scroll, BorderLayout.CENTER)
        refreshHistoryUi()
        revalidate()
        repaint()
    }

    /** Item 22 follow-up: rebuilds the history list (search-filtered). */
    private fun refreshHistoryList() {
        val entries = history?.entries() ?: emptyList()
        val q = historySearch.text.trim().lowercase()
        val filtered = if (q.isEmpty()) entries else entries.filter { it.title.lowercase().contains(q) }
        historyListPanel.removeAll()
        if (entries.isEmpty()) {
            historyListPanel.add(JBLabel("No session history yet").apply {
                border = JBUI.Borders.empty(8, 12)
                foreground = JBColor.GRAY
                font = style.smallFont
            })
        } else {
            filtered.forEach { historyListPanel.add(historyRow(it)) }
        }
        historyListPanel.revalidate()
        historyListPanel.repaint()
    }

    /** Item 22 follow-up: history-view row with Kilo-style Rename + Delete cells. */
    private fun historyRow(e: HistoryStore.Entry): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(6, 12)
        val titleLabel = JBLabel(e.title.ifBlank { "Untitled session" }).apply { font = style.boldFont }
        val sub = JBLabel("${timeAgo(e.updated)} · click to open").apply {
            foreground = JBColor.GRAY
            font = style.smallFont
        }
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        val renameBtn = iconButton(AllIcons.Actions.Edit, "Rename")
        val deleteBtn = iconButton(AllIcons.Actions.GC, "Delete")
        actions.add(renameBtn)
        actions.add(deleteBtn)
        panel.add(titleLabel, BorderLayout.NORTH)
        panel.add(sub, BorderLayout.SOUTH)
        panel.add(actions, BorderLayout.EAST)
        panel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        panel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(ev: MouseEvent) {
                if (ev.clickCount == 1) replaySession(e.id)
            }
        })
        renameBtn.addActionListener { beginRename(panel, titleLabel, e) }
        deleteBtn.addActionListener {
            val title = e.title.ifBlank { "Untitled session" }
            val ok = Messages.showYesNoDialog(
                project,
                "Delete \"$title\" from local history?",
                "Delete session?",
                Messages.getWarningIcon(),
            )
            if (ok == Messages.YES) {
                history?.delete(e.id)
                refreshHistoryList()
                refreshHistoryUi()
            }
        }
        return panel
    }

    /** Item 22 follow-up: inline row rename (Kilo's inline balloon simplified
     * to an in-row field; Enter/blur commits, Escape cancels). */
    private fun beginRename(panel: JPanel, titleLabel: JBLabel, e: HistoryStore.Entry) {
        val field = JBTextField(e.title)
        var done = false
        fun commit() {
            if (done) return
            done = true
            val t = field.text.trim()
            if (t.isNotBlank()) history?.rename(e.id, t)
            refreshHistoryList()
        }
        field.addActionListener { commit() }
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(ev: FocusEvent) { commit() }
        })
        field.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "dsh-rename-cancel")
        field.actionMap.put("dsh-rename-cancel", object : AbstractAction() {
            override fun actionPerformed(ev: ActionEvent) {
                if (done) return
                done = true
                refreshHistoryList()
            }
        })
        panel.remove(titleLabel)
        panel.add(field, BorderLayout.NORTH)
        panel.revalidate()
        panel.repaint()
        field.requestFocusInWindow()
        field.selectAll()
    }

    /** Item 22: replays a stored session through the same fold + widget pipeline. */
    private fun replaySession(id: String) {
        if (historyViewVisible) backFromHistory()
        historyId = id
        model.reset()
        rowsPanel.removeAll()
        rowWidgets.clear()
        var replayed = 0
        for (line in history?.lines(id) ?: emptyList()) {
            when (line.kind) {
                "prompt" -> {
                    model.echoPrompt(line.payload)
                    replayed++
                }
                "event" -> runCatching {
                    val n = mapper.readValue(line.payload, SessionEventNotification::class.java)
                    model.onEvent(n)
                    replayed++
                }
            }
        }
        logger.info("history: replay id=$id lines=$replayed")
        render(model.state(), followOverride = false)
        refreshHistoryUi()
        SwingUtilities.invokeLater { scroll.verticalScrollBar.value = 0 }
    }

    private fun render(state: TranscriptState, followOverride: Boolean? = null) {
        statusLabel.text = when {
            !startedOk -> "DSH agent: starting…"
            state.running -> "DSH agent: running…"
            else -> "DSH agent: idle"
        }
        planBadge.isVisible = state.planMode
        stopButton.isVisible = state.running
        // The send button toggles to Stop while running (Kilo pattern).
        sendIcon.icon = if (state.running) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
        sendIcon.toolTipText = if (state.running) "Stop" else "Send prompt"
        // Mirror the harness plan mode onto the composer tab so typed /plan and
        // bridge commands stay visually in sync (one-way: mode on → Plan).
        if (state.planMode && currentAction != ComposerAction.PLAN) {
            actionSelected(ComposerAction.PLAN)
        }

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
        // Follow state comes from the pre-batch snapshot (or the live flag for
        // direct renders); reading it after reflowAll() picks up transient
        // scrollbar states and force-pins the viewport (2026-08-30).
        val follow = followOverride ?: wasAtBottom
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
            lastMax = scroll.verticalScrollBar.maximum
            if (follow) {
                // Re-verify AT EXECUTION TIME: if the user scrolled up in the
                // meantime (even if the follow flag missed it), never pin.
                SwingUtilities.invokeLater {
                    val bar = scroll.verticalScrollBar
                    if (bar.value + bar.model.extent >= bar.maximum - 24) {
                        bar.value = bar.maximum
                    }
                }
            }
        }
        refreshHistoryUi()
        refreshBulkActions()
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
        val atBottom = bar.value + bar.model.extent >= bar.maximum - 24
        if (!atBottom) {
            wasAtBottom = false
        } else if (bar.maximum >= lastMax) {
            // Re-engage follow only when the content actually grew: a shrinking
            // layout clamps the value to the max and would fake "at bottom".
            wasAtBottom = true
        }
    }

    private fun createWidget(row: TranscriptRow): RowWidget = when (row) {
        is UserRow -> RowWidget.Static(userPanel(row))
        is AssistantRow -> RowWidget.Assistant(assistantParts(row))
        is ToolCardRow -> RowWidget.Tool(toolParts(row))
        is ReasoningRow -> RowWidget.Reasoning(reasoningParts(row))
        is PermissionRow -> RowWidget.Permission(permissionParts(row))
        is QuestionRow -> RowWidget.Question(questionParts(row))
        is NoticeRow -> RowWidget.Static(noticePanel(row))
    }

    /** Item 24 (replanned): Kilo's inline "Permission required" card. */
    private fun permissionParts(row: PermissionRow): PermissionParts {
        val panel = rowShell()
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        header.add(JBLabel(AllIcons.General.Warning))
        header.add(JBLabel("Permission required").apply { font = style.headerFont })
        val textArea = transcriptText().apply {
            text = "Tool: " + row.toolName + (row.reason?.let { "\n\n" + it } ?: "")
        }
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }
        val rejectBtn = JButton("Reject")
        val allowBtn = JButton("Allow once")
        actions.add(rejectBtn)
        actions.add(allowBtn)
        val rulesPanel = buildPermissionRulesPanel(row.toolName, allowBtn)
        val south = JPanel(BorderLayout())
        south.add(rulesPanel, BorderLayout.NORTH)
        south.add(actions, BorderLayout.SOUTH)
        panel.add(header, BorderLayout.NORTH)
        panel.add(textArea, BorderLayout.CENTER)
        panel.add(south, BorderLayout.SOUTH)
        val parts = PermissionParts(panel, textArea)
        parts.rowId = row.id
        fun decide(outcome: String) {
            model.resolvePermission(row.id)
            permissionResponders.remove(row.id)?.invoke(outcome)
            render(model.state())
        }
        allowBtn.addActionListener { decide("allowed-once") }
        rejectBtn.addActionListener { decide("rejected") }
        return parts
    }

    /** Item 24 (replanned): the card's collapsible Auto-approve Rules section —
     * existing patterns for the tool (level + remove) and quick always-allow /
     * always-deny toggles (deciding relabels the primary button to "Allow"). */
    private fun buildPermissionRulesPanel(tool: String, allowBtn: JButton): JPanel {
        val wrap = JPanel(BorderLayout()).apply { isOpaque = false }
        val toggle = JButton("Auto-approve Rules ▸").apply {
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            font = style.smallFont
        }
        val content = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            isVisible = false
        }
        fun refreshContent() {
            content.removeAll()
            val rules = PermissionSettings.loadRules()
            for (p in rules.patterns.filter { it.tool == tool }) {
                val row = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
                row.add(
                    JBLabel(p.pattern + " → " + p.level.wire).apply {
                        font = style.smallFont
                        foreground = JBColor.GRAY
                    },
                )
                val remove = iconButton(AllIcons.Actions.GC, "Remove rule")
                remove.addActionListener {
                    PermissionSettings.saveRules(rules.withoutPattern(tool, p.pattern))
                    refreshContent()
                }
                row.add(remove)
                content.add(row)
            }
            val quick = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
            val allowAll = JButton("Always allow " + tool).apply {
                isFocusPainted = false
                font = style.smallFont
            }
            val denyAll = JButton("Always deny " + tool).apply {
                isFocusPainted = false
                font = style.smallFont
            }
            allowAll.addActionListener {
                PermissionSettings.saveRules(rules.withToolLevel(tool, PermissionLevel.ALLOW))
                allowBtn.text = "Allow"
                refreshContent()
            }
            denyAll.addActionListener {
                PermissionSettings.saveRules(rules.withToolLevel(tool, PermissionLevel.DENY))
                allowBtn.text = "Allow"
                refreshContent()
            }
            quick.add(allowAll)
            quick.add(denyAll)
            content.add(quick)
            content.revalidate()
            content.repaint()
        }
        refreshContent()
        toggle.addActionListener {
            content.isVisible = !content.isVisible
            toggle.text = if (content.isVisible) "Auto-approve Rules ▾" else "Auto-approve Rules ▸"
            wrap.revalidate()
        }
        wrap.add(toggle, BorderLayout.NORTH)
        wrap.add(content, BorderLayout.CENTER)
        return wrap
    }

    /** Item 24 (replanned): renders one forwarded approval as a card row. */
    private fun showPermissionCard(approval: BridgeApproval, respond: (String) -> Unit) {
        val rowId = model.addPermission(
            approval.callId ?: approval.toolName + "-" + System.currentTimeMillis(),
            approval.toolName,
            approval.reason,
        )
        permissionResponders[rowId] = respond
        render(model.state())
    }

    /** Item 24 (host round): Kilo's inline question card — options as
     * buttons (single-select answers immediately; multi-select + Submit),
     * plus the optional custom-details field. */
    private fun questionParts(row: QuestionRow): QuestionParts {
        val panel = rowShell()
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        header.add(JBLabel(AllIcons.Actions.Help))
        header.add(JBLabel(row.header ?: "Question").apply { font = style.headerFont })
        val textArea = transcriptText().apply { text = row.question }
        val south = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        val custom = JBTextField(24)
        fun submit(selected: List<String>) {
            val ans = PlanAnswer(row.questionId, selected, custom.text.trim().ifBlank { null })
            model.resolveQuestion(row.id)
            questionResponders.remove(row.id)?.invoke(ans)
            render(model.state())
        }
        if (row.multiple) {
            val boxes = row.options.map { JCheckBox(it) }
            boxes.forEach { south.add(it) }
            val submitBtn = JButton("Submit")
            submitBtn.addActionListener {
                submit(boxes.filter { it.isSelected }.map { it.text })
            }
            val customRow = JPanel(BorderLayout(6, 0)).apply { isOpaque = false }
            customRow.add(JBLabel("Additional details (optional):").apply {
                foreground = JBColor.GRAY
                font = style.smallFont
            }, BorderLayout.WEST)
            customRow.add(custom, BorderLayout.CENTER)
            customRow.add(submitBtn, BorderLayout.EAST)
            south.add(customRow)
        } else {
            for (opt in row.options) {
                val b = JButton(opt)
                b.addActionListener { submit(listOf(opt)) }
                south.add(b)
            }
            south.add(JBLabel("Additional details (optional):").apply {
                foreground = JBColor.GRAY
                font = style.smallFont
            })
            south.add(custom)
        }
        panel.add(header, BorderLayout.NORTH)
        panel.add(textArea, BorderLayout.CENTER)
        panel.add(south, BorderLayout.SOUTH)
        return QuestionParts(panel, textArea)
    }

    /** Item 24 (host round): renders one forwarded question as a card row. */
    private fun showQuestionCard(question: PlanQuestion, respond: (PlanAnswer) -> Unit) {
        val rowId = model.addQuestion(question.id, question.header, question.detail ?: question.question, question.options, question.multiple)
        questionResponders[rowId] = respond
        render(model.state())
    }

    private fun syncAutoApproveIcon() {
        autoApproveBtn.icon = if (PermissionSettings.getAutoApprove()) AllIcons.Actions.Checked else AllIcons.Actions.InlaySecuredShield
        autoApproveBtn.toolTipText = if (PermissionSettings.getAutoApprove()) {
            "Auto-approve is enabled — permission prompts are approved automatically"
        } else {
            "Auto-approve is disabled — click to approve permission prompts automatically"
        }
    }

    /** Item 23 (host round 6): expandable "Reasoning" card (Kilo ReasoningView
     * look — hover tint, whole-header click, ">" / "∨" pure-text arrow). */
    private fun reasoningParts(row: ReasoningRow): ReasoningParts {
        val panel = rowShell()
        val headerBlock = JPanel(BorderLayout()).apply { isOpaque = false }
        val title = JBLabel("Reasoning").apply {
            font = style.headerFont
            foreground = JBColor.GRAY
        }
        val arrow = JBLabel(">").apply {
            foreground = JBColor.GRAY
            font = style.smallFont
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        headerBlock.add(title, BorderLayout.CENTER)
        headerBlock.add(arrow, BorderLayout.EAST)
        val bodyArea = transcriptText().apply {
            foreground = JBColor.GRAY
            text = row.text
        }
        val body = JPanel(BorderLayout()).apply { isVisible = false }
        body.add(bodyArea, BorderLayout.CENTER)
        panel.add(headerBlock, BorderLayout.NORTH)
        panel.add(body, BorderLayout.CENTER)
        val parts = ReasoningParts(panel, headerBlock, arrow, bodyArea, body)
        headerBlock.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                headerBlock.isOpaque = true
                headerBlock.background = JBUI.CurrentTheme.ActionButton.hoverBackground()
                headerBlock.repaint()
            }
            override fun mouseExited(e: MouseEvent) {
                headerBlock.isOpaque = false
                headerBlock.repaint()
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 1) return
                parts.collapsed = !parts.collapsed
                parts.arrow.text = if (parts.collapsed) ">" else "∨"
                parts.body.isVisible = !parts.collapsed
                parts.panel.revalidate()
                parts.panel.repaint()
            }
        })
        return parts
    }

    private fun userPanel(row: UserRow): StaticParts {
        val panel = rowShell()
        val name = JBLabel(if (row.pending) "You · sending…" else "You").apply {
            foreground = JBColor.GRAY
            font = style.headerFont
        }
        val text = transcriptText().apply { text = row.content }
        if (row.pending) text.foreground = JBColor.GRAY
        panel.add(name, BorderLayout.NORTH)
        panel.add(text, BorderLayout.CENTER)
        return StaticParts(panel, name, text)
    }

    private fun assistantParts(row: AssistantRow): AssistantParts {
        val panel = rowShell()
        val header = JBLabel().apply { font = style.headerFont }
        val textArea = transcriptText()
        val thinkingToggle = JButton("▸ Thinking")
        val thinkingArea = transcriptText().apply {
            foreground = JBColor.GRAY
            font = style.smallEditorFont
            isVisible = false
        }
        val usage = JBLabel("").apply {
            foreground = JBColor.GRAY
            font = style.smallFont
        }
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
        // Item 21: the header block (arrow + name/state + one-line preview) is
        // the click target; the body (controls + args + result) hides by default.
        val headerBlock = JPanel(BorderLayout()).apply { isOpaque = false }
        // Item 23 (host round): the preview line may hold the clickable
        // basename link (single-file cards) or the change-count hint.
        val preview = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        // Item 23 (host round 3): the header line holds the tool name plus
        // the single-file link (Kilo title + subtitle look); state words are
        // gone (fail = red tool name).
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val nameLabel = JBLabel().apply { font = style.headerFont }
        header.add(nameLabel)
        headerBlock.add(header, BorderLayout.CENTER)
        headerBlock.add(preview, BorderLayout.SOUTH)
        val argsArea = transcriptText(mono = true)
        val resultArea = transcriptText()
        val errorLabel = JBLabel("").apply {
            foreground = JBColor(0xc0392b, 0xe06c75)
            font = style.smallFont
        }
        val metaToggle = JButton("raw meta ▸")
        val metaArea = transcriptText(mono = true).apply { isVisible = false }
        val body = JPanel(BorderLayout())
        val controls = JPanel(BorderLayout(8, 0))
        controls.add(errorLabel, BorderLayout.EAST)
        body.add(controls, BorderLayout.NORTH)
        val content = JPanel(BorderLayout())
        content.add(argsArea, BorderLayout.NORTH)
        // Item 23: the body CENTER swaps between the plain result and the
        // inline diff section (Kilo: edit tools render the diff as the body).
        val bodyCenter = JPanel(BorderLayout())
        bodyCenter.add(resultArea, BorderLayout.CENTER)
        content.add(bodyCenter, BorderLayout.CENTER)
        val diffSection = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyTop(4)
            isVisible = false
        }
        body.add(content, BorderLayout.CENTER)
        val metaWrap = JPanel(BorderLayout())
        metaWrap.add(metaToggle, BorderLayout.NORTH)
        metaWrap.add(metaArea, BorderLayout.CENTER)
        panel.add(headerBlock, BorderLayout.NORTH)
        panel.add(body, BorderLayout.CENTER)
        panel.add(metaWrap, BorderLayout.SOUTH)
        // Item 23 (host round 4A): expansion via the right-end ARROW only —
        // the header block stays inert (file links own their clicks).
        // Item 23 (host feedback 2026-09-02): a REAL bordered button — the
        // bare glyph version was invisible at the far-right edge.
        // Item 23 (host round 4): PURE TEXT arrow — a gray label whose clicks
        // bubble into the whole-header toggle.
        val expandBtn = JBLabel(">").apply {
            toolTipText = "Expand"
            foreground = JBColor.GRAY
            font = style.smallFont
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        headerBlock.add(expandBtn, BorderLayout.EAST)
        val parts = ToolParts(
            panel, headerBlock, header, nameLabel, expandBtn, preview, body,
            argsArea, resultArea, bodyCenter, diffSection, errorLabel, metaToggle, metaArea, metaWrap,
        )
        // Item 23 (host round 2): Kilo PartHeader behavior — hover fills the
        // header background; ANY header click toggles the card EXCEPT on the
        // file links (they open files). The arrow button is visual only here;
        // its clicks bubble to this listener and toggle once.
        // Item 23 (host round 6): hover-preview bubble (scrollable, short
        // delay, dismissed on mouse-out). bash/glob show the response; edit
        // shows the diff body like the expanded card.
        fun previewBubbleContent(): JComponent? = when (parts.name) {
            "bash", "glob", "grep" -> {
                val area = transcriptText(mono = true).apply { text = parts.resultText }
                JBScrollPane(area).apply {
                    preferredSize = Dimension(480, 320)
                    border = null
                }
            }
            "edit", "write" -> {
                val ds = parts.diffs
                if (ds.isNullOrEmpty()) {
                    null
                } else if (ds.size == 1) {
                    val lines = diffLines(ds[0].oldText, ds[0].newText)
                    val body = lines.joinToString("\n") { it.text }
                    val field = EditorTextField(body).apply {
                        isViewer = true
                        setOneLineMode(false)
                        font = style.editorFont
                    }
                    field.addSettingsProvider { ed ->
                        highlightPatch(ed as? EditorEx ?: return@addSettingsProvider, lines)
                    }
                    JBScrollPane(field).apply {
                        preferredSize = Dimension(480, 320)
                        border = null
                    }
                } else {
                    val sb = StringBuilder()
                    for (c in ds) {
                        sb.append(c.path).append('\n')
                        sb.append(diffLines(c.oldText, c.newText).joinToString("\n") { it.text })
                        sb.append("\n\n")
                    }
                    val area = transcriptText(mono = true).apply { text = sb.toString() }
                    JBScrollPane(area).apply {
                        preferredSize = Dimension(480, 320)
                        border = null
                    }
                }
            }
            else -> null
        }
        var hoverTimer: javax.swing.Timer? = null
        var bubble: Balloon? = null
        headerBlock.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                headerBlock.isOpaque = true
                headerBlock.background = JBUI.CurrentTheme.ActionButton.hoverBackground()
                headerBlock.repaint()
                hoverTimer?.stop()
                hoverTimer = javax.swing.Timer(500) {
                    hoverTimer = null
                    bubble?.hide()
                    previewBubbleContent()?.let { content ->
                        bubble = JBPopupFactory.getInstance()
                            .createBalloonBuilder(content)
                            .setBlockClicksThroughBalloon(true)
                            .setRequestFocus(false)
                            .setHideOnClickOutside(true)
                            .createBalloon()
                        bubble?.show(
                            RelativePoint(headerBlock, Point(0, headerBlock.height)),
                            Balloon.Position.below,
                        )
                    }
                }.apply {
                    isRepeats = false
                    start()
                }
            }
            override fun mouseExited(e: MouseEvent) {
                hoverTimer?.stop()
                hoverTimer = null
                bubble?.hide()
                bubble = null
                headerBlock.isOpaque = false
                headerBlock.repaint()
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 1) return
                val src = e.source as? JComponent ?: return
                if (src.getClientProperty("dsh-file-link") == true) return
                parts.collapsed = !parts.collapsed
                syncToolBody(parts)
            }
        })
        metaToggle.addActionListener {
            val show = !metaArea.isVisible
            metaArea.isVisible = show
            metaToggle.text = if (show) "raw meta ▾" else "raw meta ▸"
            panel.revalidate()
        }
        parts.update(row)
        return parts
    }

    /** Item 21/23: mirror the collapsed state into the arrow button and the
     * body/preview visibility. */
    private fun syncToolBody(parts: ToolParts) {
        syncHeader(parts)
        parts.expandBtn.text = if (parts.collapsed) ">" else "∨"
        parts.expandBtn.toolTipText = if (parts.collapsed) "Expand" else "Collapse"
        parts.body.isVisible = !parts.collapsed
        parts.preview.isVisible = parts.collapsed
        parts.panel.revalidate()
        parts.panel.repaint()
    }

    /** Item 23 (host round 3): tool name + single-file link on the header line;
     * 'done'/'failed' words are gone — failures paint the name red. */
    private fun syncHeader(parts: ToolParts) {
        parts.nameLabel.text = "⚙ " + parts.name + if (parts.stateText == "running…") " · running…" else ""
        parts.nameLabel.foreground = if (parts.stateText == "error") JBColor(0xc0392b, 0xe06c75) else null
        parts.header.removeAll()
        parts.header.add(parts.nameLabel)
        val ds = parts.diffs
        if (ds != null && ds.size == 1) {
            // Item 23 (host round 5): the diff title (link + "+N −M") lives on
            // the header line for single-change cards.
            parts.header.add(fileLinkLabel(ds[0].path))
            val hl = diffLines(ds[0].oldText, ds[0].newText)
            val adds = hl.count { it.op == DiffOp.INSERT }
            val dels = hl.count { it.op == DiffOp.DELETE }
            parts.header.add(JBLabel("+" + adds + " −" + dels).apply {
                foreground = JBColor.GRAY
                font = style.smallFont
            })
        } else if (ds == null && parts.name == "read") {
            readFilePath(parts.argumentsJson)?.let { parts.header.add(fileLinkLabel(it)) }
        }
        parts.header.revalidate()
        parts.header.repaint()
    }

    /** Item 23: rebuilds the inline diff section (Kilo look: per-file patch
     * editors with +/- highlighting; over the cap → placeholder + tab link). */
    private fun ToolParts.rebuildDiffSection() {
        bodyCenter.removeAll()
        diffSection.removeAll()
        val changes = diffs
        logger.info("diff: section tool=${name} changes=${changes?.size ?: "none"} collapsed=${collapsed}")
        if (changes == null) {
            argsArea.isVisible = true
            // Item 23 (host round 2): read cards get no raw-meta button.
            metaWrap.isVisible = name != "read"
            val wrapper = JPanel(BorderLayout())
            if (name == "write" || name == "edit") {
                // Item 23 (host feedback 2026-09-02): explain WHY there is no
                // Accept/Reject instead of silently showing the plain result.
                wrapper.add(
                    JBLabel("No diff metadata — the harness reported no file changes, so there is nothing to Accept/Reject.").apply {
                        foreground = JBColor.GRAY
                        font = style.smallFont
                        border = JBUI.Borders.empty(4, 0, 4, 0)
                    },
                    BorderLayout.NORTH,
                )
            }
            wrapper.add(resultArea, BorderLayout.CENTER)
            bodyCenter.add(wrapper, BorderLayout.CENTER)
            resultArea.isVisible = true
            diffSection.isVisible = false
            bodyCenter.revalidate()
            bodyCenter.repaint()
            return
        }
        // Item 23 (host round): expanded DIFF cards show ONLY the diff content —
        // request JSON and the raw-meta toggle are hidden.
        argsArea.isVisible = false
        metaWrap.isVisible = false
        resultArea.isVisible = false
        diffSection.isVisible = true
        if (patchLineCount(changes) > DIFF_MAX_LINES) {
            diffSection.add(overflowPanel(changes))
        } else {
            for (change in changes) {
                runCatching { diffSection.add(diffFilePanel(change)) }
                    .onFailure { logger.warn("diff: section build failed for ${change.path}", it) }
            }
        }
        bodyCenter.add(diffSection, BorderLayout.CENTER)
        diffSection.revalidate()
        diffSection.repaint()
        bodyCenter.revalidate()
        bodyCenter.repaint()
    }

    /** Item 23 (host round): collapsed preview — single-file cards show the
     * clickable basename link; multi-file cards show the count hint. */
    private fun ToolParts.buildPreview(row: ToolCardRow, prettyArgs: String) {
        preview.removeAll()
        val ds = diffs
        if (ds == null) {
            // Item 23 (host round 4): the header line already carries the link
            // for single-file cards and read cards — the preview keeps only
            // the gray snippet/hint.
            preview.add(JBLabel(toolPreviewLine(row.resultText, prettyArgs)).apply {
                foreground = JBColor.GRAY
                font = style.smallFont
            })
        } else if (ds.size == 1) {
            preview.add(JBLabel("click to expand").apply {
                foreground = JBColor.GRAY
                font = style.smallFont
            })
        } else {
            // Item 23 (host round 3): multi-file cards list EVERY file link on
            // the collapsed preview (the aggregate write card can't be split
            // into one card per change without a model change).
            for (p in ds) preview.add(fileLinkLabel(p.path))
            preview.add(JBLabel("(" + ds.size + " files — click to expand)").apply {
                foreground = JBColor.GRAY
                font = style.smallFont
            })
        }
        preview.revalidate()
        preview.repaint()
    }

    /** Item 23 (host round 2): the read tool's file_path argument, or null. */
    private fun readFilePath(argumentsJson: String): String? = try {
        mapper.readTree(argumentsJson).path("file_path").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /** Item 23 (host round 3): underlined basename link — hover balloon with the
     * full path, click opens the file. */
    private fun fileLinkLabel(path: String): JBLabel {
        val label = JBLabel("<html><u>${escapeHtml(path.substringAfterLast('/'))}</u></html>").apply {
            font = style.boldFont
            foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        label.putClientProperty("dsh-file-link", true)
        var balloon: Balloon? = null
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) {
                balloon?.hide()
                balloon = JBPopupFactory.getInstance()
                    .createBalloonBuilder(JBLabel(path))
                    .setHideOnClickOutside(false)
                    .setBlockClicksThroughBalloon(true)
                    .setShowCallout(true)
                    .createBalloon()
                balloon?.show(RelativePoint(label, Point(0, label.height)), Balloon.Position.below)
            }
            override fun mouseExited(e: MouseEvent) {
                balloon?.hide()
                balloon = null
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) openFile(path)
            }
        })
        return label
    }

    /** Item 23: one file's inline patch section — basename link + patch editor
     * + the Accept/Reject/Undo-Reject state machine. */
    private fun ToolParts.diffFilePanel(change: FileChange): JComponent {
        val panel = JPanel(BorderLayout(0, 4))
        panel.border = JBUI.Borders.empty(0, 0, 8, 0)
        val headerRow = JPanel(BorderLayout(8, 0)).apply { isOpaque = false }
        // Item 23 (host round 3): BASENAME link — hover shows the full path
        // in a balloon, click opens the file. No full path text in the body.
        val title = fileLinkLabel(change.path)
        val lines = diffLines(change.oldText, change.newText)
        val adds = lines.count { it.op == DiffOp.INSERT }
        val dels = lines.count { it.op == DiffOp.DELETE }
        val stat = JBLabel("+" + adds + " −" + dels).apply {
            foreground = JBColor.GRAY
            font = style.smallFont
        }
        val titleWrap = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        // Item 23 (host round 5): single-change cards carry the title on the
        // CARD HEADER; the section keeps its own title only for multi-change
        // cards (each file needs its identity).
        if ((diffs?.size ?: 1) > 1) {
            titleWrap.add(title)
            titleWrap.add(stat)
            headerRow.add(titleWrap, BorderLayout.WEST)
        }
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        when (decisions[change.path]) {
            DiffDecision.ACCEPTED -> Unit // buttons dismissed (the change is already applied)
            DiffDecision.REJECTED -> {
                val undo = JButton("Undo Reject", AllIcons.Actions.Rollback).apply {
                    toolTipText = "Re-apply the change"
                }
                actions.add(undo)
                undo.addActionListener {
                    decisions.remove(change.path)
                    if (applyChange(change)) {
                        model.notice("Re-applied ${change.path}")
                        render(model.state())
                    }
                    rebuildDiffSection()
                }
            }
            null -> {
                val acceptBtn = JButton("Accept", AllIcons.Actions.Commit).apply {
                    toolTipText = "Keep the change (the harness already applied it)"
                }
                val rejectBtn = JButton("Reject", AllIcons.Actions.Cancel).apply {
                    toolTipText = "Undo the change"
                }
                actions.add(acceptBtn)
                actions.add(rejectBtn)
                acceptBtn.addActionListener {
                    decisions[change.path] = DiffDecision.ACCEPTED
                    rebuildDiffSection()
                }
                rejectBtn.addActionListener {
                    if (revertChange(change)) {
                        decisions[change.path] = DiffDecision.REJECTED
                        model.notice("Rejected ${change.path} — change undone")
                        render(model.state())
                    }
                    rebuildDiffSection()
                }
            }
        }
        headerRow.add(actions, BorderLayout.EAST)
        // Item 23 (host round 3): Kilo's pure-diff body — no ---/+++/@@
        // header lines, no leading +/- markers; the ops drive the colors and
        // the gutter instead.
        val body = lines.joinToString("\n") { it.text }
        val field = EditorTextField(body).apply {
            isViewer = true
            setOneLineMode(false)
            font = style.editorFont
        }
        // The editor is created lazily — decorate it inside the settings
        // provider so backgrounds + gutter apply whenever it materializes.
        field.addSettingsProvider { ed ->
            highlightPatch(ed as? EditorEx ?: return@addSettingsProvider, lines)
        }
        panel.add(headerRow, BorderLayout.NORTH)
        panel.add(
            JBScrollPane(field).apply {
                preferredSize = Dimension(0, 140)
                border = null
            },
            BorderLayout.CENTER,
        )
        return panel
    }

    /** Item 23: Kilo-style +/- line highlighting (colored backgrounds) on
     * the patch editor, plus the old/new line-number gutter. */
    private fun highlightPatch(editor: EditorEx, lines: List<DiffLine>) {
        val doc = editor.document
        lines.forEachIndexed { idx, line ->
            val attrs = when (line.op) {
                DiffOp.INSERT -> DiffColors.DIFF_INSERTED.defaultAttributes
                DiffOp.DELETE -> DiffColors.DIFF_DELETED.defaultAttributes
                DiffOp.EQUAL -> null
            } ?: return@forEachIndexed
            editor.markupModel.addRangeHighlighter(
                doc.getLineStartOffset(idx),
                doc.getLineEndOffset(idx),
                HighlighterLayer.FIRST,
                attrs,
                HighlighterTargetArea.EXACT_RANGE,
            )
        }
        installDiffGutter(editor, gutterRows(lines))
    }

    /** Item 23 (host round 1): old/new line numbers in the diff gutter. */
    private fun installDiffGutter(editor: EditorEx, rows: List<DiffRow>) {
        editor.settings.isLineNumbersShown = false
        editor.gutter.closeAllAnnotations()
        editor.gutter.registerTextAnnotation(DiffGutterProvider(rows))
    }

    /** Item 23 (host round): Reject undoes the change — oldText back, or the
     * file is DELETED for creations. Workspace-confined like applyChange. */
    private fun revertChange(change: FileChange): Boolean {
        val base = File(project.basePath ?: ".").canonicalFile
        val target = File(base, change.path).canonicalFile
        val basePrefix = base.path + File.separator
        if (target != base && !target.path.startsWith(basePrefix)) {
            model.notice("Reject refused: ${change.path} is outside the workspace")
            render(model.state())
            return false
        }
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                val old = change.oldText
                if (old == null) {
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
                    if (vf != null) vf.delete(project)
                } else {
                    target.parentFile?.mkdirs()
                    var vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
                    if (vf == null) {
                        val parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.parentFile)
                            ?: throw IllegalStateException("cannot resolve parent: ${target.parentFile}")
                        vf = parent.createChildData(project, target.name)
                    }
                    VfsUtil.saveText(vf, old)
                }
            }
            true
        } catch (e: Exception) {
            model.notice("Failed to reject ${change.path}: ${e.message}")
            render(model.state())
            false
        }
    }

    /** Item 23 (host round 3): opens the change's file in the editor. */
    private fun openFile(path: String) {
        val base = File(project.basePath ?: ".").canonicalFile
        val raw = File(path)
        val target = (if (raw.isAbsolute) raw else File(base, path)).canonicalFile
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
        if (vf == null) {
            model.notice("File not found: ${target.path}")
            render(model.state())
            return
        }
        FileEditorManager.getInstance(project).openFile(vf, true)
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Item 23: writes one change workspace-confined (the item-7 semantic —
     * Kilo's CLI writes files, ours apply on the IDE side). */
    private fun applyChange(change: FileChange): Boolean {
        val base = File(project.basePath ?: ".").canonicalFile
        val target = File(base, change.path).canonicalFile
        val basePrefix = base.path + File.separator
        if (target != base && !target.path.startsWith(basePrefix)) {
            model.notice("Apply refused: ${change.path} is outside the workspace")
            render(model.state())
            return false
        }
        return try {
            WriteCommandAction.runWriteCommandAction(project) {
                target.parentFile?.mkdirs()
                var vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target)
                if (vf == null) {
                    val parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target.parentFile)
                        ?: throw IllegalStateException("cannot resolve parent: ${target.parentFile}")
                    vf = parent.createChildData(project, target.name)
                }
                VfsUtil.saveText(vf, change.newText)
            }
            model.notice("Applied ${change.path}")
            render(model.state())
            true
        } catch (e: Exception) {
            model.notice("Failed to apply ${change.path}: ${e.message}")
            render(model.state())
            false
        }
    }

    /** Item 23: Kilo's over-cap placeholder (DIFF_MAX_LINES) with the tab link. */
    private fun overflowPanel(changes: List<FileChange>): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        panel.add(
            JBLabel("Diff too large (${patchLineCount(changes)} lines) — ").apply {
                foreground = JBColor.GRAY
                font = style.smallFont
            },
        )
        val link = HyperlinkLabel("open in a diff tab")
        link.addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) openDiffTab(changes)
        }
        panel.add(link)
        return panel
    }

    /** Item 23: opens the over-cap diff in the PLATFORM multi-file viewer. */
    private fun openDiffTab(changes: List<FileChange>) {
        val producers = changes.map { change ->
            object : DiffRequestProducer {
                override fun getName(): String = change.path
                override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
                    val factory = DiffContentFactory.getInstance()
                    val before = change.oldText?.let { factory.create(project, it) } ?: factory.createEmpty()
                    val after = factory.create(project, change.newText)
                    return SimpleDiffRequest(change.path, before, after, change.path, change.path)
                }
            }
        }
        DiffManager.getInstance().showDiff(
            project,
            SimpleDiffRequestChain.fromProducers(producers),
            DiffDialogHints.DEFAULT,
        )
    }

    private fun noticePanel(row: NoticeRow): StaticParts {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(2, 12)
        // Selectable + wrapping so the full text is visible and copyable (2026-08-28).
        val hint = if (row.kind == NoticeKind.APPROVAL_ASKED || row.kind == NoticeKind.APPROVAL_DECIDED) {
            Font.BOLD
        } else {
            Font.ITALIC
        }
        val label = transcriptText().apply {
            text = row.text
            foreground = JBColor.GRAY
            font = style.smallFont.deriveFont(hint)
        }
        when (row.kind) {
            NoticeKind.PLAN_MODE, NoticeKind.API_KEY_MISSING ->
                label.foreground = JBColor(0xb26b00, 0xdfb14a)
            else -> Unit
        }
        panel.add(label, BorderLayout.CENTER)
        return StaticParts(panel, null, label, small = true, hint = hint)
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
        // Item 21: a tool-name change re-collapses the card (never auto-expand).
        if (lastName != null && lastName != row.name) collapsed = true
        lastName = row.name
        name = row.name
        stateText = when {
            row.status == ToolCardStatus.RUNNING -> "running…"
            row.isError -> "error"
            else -> "done"
        }
        argumentsJson = row.arguments
        resultText = row.resultText
        val prettyArgs = prettyJson(row.arguments)
        argsArea.text = prettyArgs
        resultArea.text = row.resultText
        buildPreview(row, prettyArgs)
        val errorText = row.errorName.orEmpty() + row.errorCode?.let { " ($it)" }.orEmpty()
        errorLabel.text = errorText
        errorLabel.isVisible = row.isError
        val parsed = FsDiffParser.parse(row.meta)
        diffs = parsed ?: fallbackDiff(row.name, row.arguments)
        // Item 23 diagnostics (2026-09-02): why a card has/hasn't got Accept/Reject.
        logger.info("diff: tool=${row.name} meta=${row.meta != null} parsed=${parsed?.size ?: "none"} diffs=${diffs?.size ?: "none"}")
        rebuildDiffSection()
        metaToggle.isVisible = row.meta != null
        if (row.meta != null && metaArea.text.isEmpty()) {
            metaArea.text = prettyJson(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(row.meta))
        }
        syncToolBody(this)
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

    /** Item 19: mono surfaces read the EDITOR font; chat text reads the UI
     * family at the editor size (transcriptFont tracks Ctrl+wheel zoom). */
    private fun transcriptText(mono: Boolean = false): JBTextArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        border = BorderFactory.createEmptyBorder()
        font = if (mono) style.editorFont else style.transcriptFont
    }

    /** Item 19: apply the snapshot to every live surface without rebuilding nodes. */
    override fun applyStyle(newStyle: DshEditorStyle) {
        style = newStyle
        statusLabel.font = newStyle.smallFont
        statusLabel.foreground = JBColor.GRAY
        planBadge.font = newStyle.smallFont
        input.font = newStyle.transcriptFont
        contextPreview.font = newStyle.transcriptFont
        for (widget in rowWidgets.values) {
            when (widget) {
                is RowWidget.Assistant -> {
                    widget.parts.header.font = newStyle.headerFont
                    widget.parts.textArea.font = newStyle.transcriptFont
                    widget.parts.thinkingArea.font = newStyle.smallEditorFont
                    widget.parts.usage.font = newStyle.smallFont
                }
                is RowWidget.Tool -> {
                    widget.parts.nameLabel.font = newStyle.headerFont
                    widget.parts.argsArea.font = newStyle.editorFont
                    widget.parts.resultArea.font = newStyle.transcriptFont
                    widget.parts.metaArea.font = newStyle.editorFont
                    widget.parts.errorLabel.font = newStyle.smallFont
                    widget.parts.rebuildDiffSection()
                }
                is RowWidget.Static -> {
                    widget.parts.header?.font = newStyle.headerFont
                    var textFont = if (widget.parts.small) newStyle.smallFont else newStyle.transcriptFont
                    if (widget.parts.hint != Font.PLAIN) textFont = textFont.deriveFont(widget.parts.hint)
                    widget.parts.text.font = textFont
                }
                is RowWidget.Reasoning -> {
                    widget.parts.bodyArea.font = newStyle.transcriptFont
                }
                is RowWidget.Permission -> {
                    widget.parts.textArea.font = newStyle.transcriptFont
                }
                is RowWidget.Question -> {
                    widget.parts.textArea.font = newStyle.transcriptFont
                }
            }
        }
        reflowAll()
        // Item 20: the composer font changed, so its line height did too.
        updateComposerHeight()
        rowsPanel.revalidate()
        rowsPanel.repaint()
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
                is RowWidget.Reasoning -> sizeToContent(widget.parts.bodyArea, width)
                is RowWidget.Permission -> sizeToContent(widget.parts.textArea, width)
                is RowWidget.Question -> sizeToContent(widget.parts.textArea, width)
                is RowWidget.Static -> Unit
            }
        }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    /**
     * Item 20: measure the composer's wrapped content via the View API (same
     * technique as [sizeToContent]) and clamp the scroll-pane height to
     * [1 line, MAX_COMPOSER_LINES lines]; beyond the cap the area scrolls.
     */
    private fun updateComposerHeight() {
        val metrics = input.getFontMetrics(input.font)
        val lineHeight = metrics.height.coerceAtLeast(1)
        val w = input.width.coerceAtLeast(40)
        val rootView = input.getUI().getRootView(input)
        rootView.setSize(w.toFloat(), Int.MAX_VALUE.toFloat())
        val content = rootView.getPreferredSpan(javax.swing.text.View.Y_AXIS).toInt()
        val target = clampComposerHeight(content, lineHeight, MAX_COMPOSER_LINES)
        val current = inputScroll.preferredSize
        if (current == null || current.height != target + 8) {
            inputScroll.preferredSize = Dimension(0, target + 8)
            inputScroll.revalidate()
        }
    }

    private fun sizeToContent(area: JBTextArea, width: Int) {
        // Measure via the View API — never setSize(MAX_VALUE): the size hack
        // perturbed the layout mid-pass and moved the viewport during content
        // growth (2026-08-31 scroll trace).
        val w = (width - 32).coerceAtLeast(40)
        val rootView = area.getUI().getRootView(area)
        rootView.setSize(w.toFloat(), Int.MAX_VALUE.toFloat())
        val height = rootView.getPreferredSpan(javax.swing.text.View.Y_AXIS).toInt().coerceAtLeast(0)
        area.preferredSize = Dimension(w, height)
    }

    override fun dispose() {
        flushTimer.stop()
        mentionWindow?.dispose()
        mentionWindow = null
        popup?.dispose()
        popup = null
        scope.cancel()
    }

    /**
     * FlowLayout that WRAPS rows and reports the full wrapped height — the
     * chips bar needs it, otherwise wrapped chip rows are clipped (2026-08-31).
     */
    private class WrapLayout : FlowLayout(FlowLayout.LEFT, 4, 0) {
        override fun preferredLayoutSize(target: java.awt.Container): Dimension {
            val width = target.width.coerceAtLeast(1)
            var x = 0
            var y = 0
            var rowHeight = 0
            for (i in 0 until target.componentCount) {
                val pref = target.getComponent(i).preferredSize
                if (x > 0 && x + pref.width > width) {
                    x = 0
                    y += rowHeight + vgap
                    rowHeight = 0
                }
                x += pref.width + hgap
                rowHeight = maxOf(rowHeight, pref.height)
            }
            return Dimension(width, y + rowHeight + vgap)
        }
    }

    companion object {
        /** Item 14 caps: mentions per prompt, bytes per mentioned file, picker candidates. */
        private const val MAX_MENTIONS = 10
        private const val MAX_MENTION_BYTES = 50_000
        private const val MAX_MENTION_CANDIDATES = 300
        /** Item 15: preview truncation for context chips. */
        private const val PREVIEW_BYTES = 2_000
        /** Item 20 follow-up: composer placeholder hint. */
        private const val COMPOSER_HINT = "Type here; use / or @, drop / paste files; ⏎ send, ⇧⏎ newline"
        /** Item 22: Kilo's RecentSessions.LIMIT. */
        private const val RECENT_SESSIONS_LIMIT = 5
    }
}

/** Item 23 (host round 1): two-column old/new line numbers in the diff gutter
 * (Kilo's DiffGutter, reimplemented). */
private const val FIGURE = '\u2007'

private class DiffGutterProvider(private val rows: List<DiffRow>) : TextAnnotationGutterProvider {
    private val oldWidth = width { it.old }
    private val newWidth = width { it.new }

    override fun getLineText(line: Int, editor: Editor): String? {
        val row = rows.getOrNull(line) ?: return null
        return "${col(row.old, oldWidth)}$FIGURE${col(row.new, newWidth)}$FIGURE$FIGURE"
    }

    override fun getToolTip(line: Int, editor: Editor): String? = null

    override fun getStyle(line: Int, editor: Editor): EditorFontType = EditorFontType.PLAIN

    override fun getColor(line: Int, editor: Editor): ColorKey? = null

    override fun getBgColor(line: Int, editor: Editor): Color? = null

    override fun gutterClosed() = Unit

    override fun getPopupActions(line: Int, editor: Editor): List<AnAction>? = null

    override fun useMargin(): Boolean = false

    private fun width(pick: (DiffRow) -> Int?): Int =
        rows.mapNotNull(pick).maxOrNull()?.toString()?.length ?: 1

    private fun col(value: Int?, width: Int): String = value?.toString().orEmpty().padStart(width, FIGURE)
}
