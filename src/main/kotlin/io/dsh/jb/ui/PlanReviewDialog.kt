package io.dsh.jb.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import io.dsh.jb.bridge.PlanAnswer
import io.dsh.jb.bridge.PlanQuestion
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Modal plan-review dialog (roadmap item 8): renders the plan markdown and the
 * harness's own options. Approval REQUIRES the answer's selected label to equal
 * the intent's approve label with no custom text — plan-mode rejects anything
 * else as "keep planning".
 */
class PlanReviewDialog(private val question: PlanQuestion) : DialogWrapper(true) {

    var result: PlanAnswer? = null
        private set

    private val approveLabel: String = question.approveLabel
        ?: question.options.firstOrNull().orEmpty()

    private val keepLabel: String = question.options.firstOrNull { it != approveLabel }.orEmpty()

    init {
        title = question.header ?: "Plan review"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        val text = JTextArea((question.detail ?: question.question).ifBlank { question.question })
        text.isEditable = false
        text.lineWrap = true
        text.wrapStyleWord = true
        panel.add(JBScrollPane(text), BorderLayout.CENTER)
        return panel
    }

    override fun createActions(): Array<Action> {
        val approve = object : AbstractAction(approveLabel.ifBlank { "Approve" }) {
            override fun actionPerformed(e: ActionEvent?) {
                result = PlanAnswer(question.id, listOf(approveLabel), null)
                close(OK_EXIT_CODE)
            }
        }
        val keep = object : AbstractAction(keepLabel.ifBlank { "Keep planning" }) {
            override fun actionPerformed(e: ActionEvent?) {
                result = PlanAnswer(question.id, listOf(keepLabel), null)
                close(OK_EXIT_CODE)
            }
        }
        return arrayOf(keep, approve)
    }

    companion object {
        /** Shows the dialog on the EDT and blocks until answered; null when dismissed. */
        fun ask(question: PlanQuestion): PlanAnswer? {
            var answer: PlanAnswer? = null
            SwingUtilities.invokeAndWait {
                val dialog = PlanReviewDialog(question)
                if (dialog.showAndGet()) answer = dialog.result
            }
            return answer
        }
    }
}
