package io.dsh.jb.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import io.dsh.jb.bridge.BridgeApproval
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Tool-approval dialog (roadmap item 9, Kilo PermissionView pattern): shows the
 * tool name and the asker's reason with Allow once / Deny actions. The harness
 * vocabulary only grants `allowed-once` (no always-rules), so v1 has no
 * per-pattern persistence. Dismissing fails closed (the caller maps null to
 * `rejected`).
 */
class PermissionDialog(private val approval: BridgeApproval) : DialogWrapper(true) {

    var outcome: String? = null
        private set

    init {
        title = "Approve tool"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        val body = buildString {
            append("Tool: ").append(approval.toolName)
            approval.reason?.let { append("\\n\\nReason: ").append(it) }
            approval.callId?.let { append("\\n\\nCall: ").append(it) }
        }
        val text = JTextArea(body)
        text.isEditable = false
        text.lineWrap = true
        text.wrapStyleWord = true
        // Item 19: tool/command text reads the editor (mono) font.
        text.font = DshEditorStyle.current().editorFont
        panel.add(JBScrollPane(text), BorderLayout.CENTER)
        return panel
    }

    override fun createActions(): Array<Action> {
        val deny = object : AbstractAction("Deny") {
            override fun actionPerformed(e: ActionEvent?) {
                outcome = "rejected"
                close(OK_EXIT_CODE)
            }
        }
        val allow = object : AbstractAction("Allow once") {
            override fun actionPerformed(e: ActionEvent?) {
                outcome = "allowed-once"
                close(OK_EXIT_CODE)
            }
        }
        return arrayOf(deny, allow)
    }

    companion object {
        /** Shows the dialog on the EDT and blocks until decided; null when dismissed. */
        fun ask(approval: BridgeApproval): String? {
            var decided: String? = null
            SwingUtilities.invokeAndWait {
                val dialog = PermissionDialog(approval)
                if (dialog.showAndGet()) decided = dialog.outcome
            }
            return decided
        }
    }
}
