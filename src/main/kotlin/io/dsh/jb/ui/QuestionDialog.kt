package io.dsh.jb.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import io.dsh.jb.bridge.PlanAnswer
import io.dsh.jb.bridge.PlanQuestion
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextArea
import javax.swing.SwingUtilities

/**
 * Generic ask_user_question dialog (roadmap item 9): renders the harness
 * question with its options (radio by default, checkboxes when
 * [PlanQuestion.multiple]) plus an optional custom-text field. Plan reviews
 * keep their own [PlanReviewDialog].
 */
class QuestionDialog(private val question: PlanQuestion) : DialogWrapper(true) {

    var result: PlanAnswer? = null
        private set

    private val radioButtons = question.options.map { JRadioButton(it) }
    private val checkBoxes = question.options.map { JCheckBox(it) }
    private val custom = JBTextField()

    init {
        title = question.header ?: "Question"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        val text = JTextArea((question.detail ?: question.question).ifBlank { question.question })
        text.isEditable = false
        text.lineWrap = true
        text.wrapStyleWord = true
        // Item 19: question bodies read the transcript font.
        text.font = DshEditorStyle.current().transcriptFont
        panel.add(JBScrollPane(text), BorderLayout.CENTER)

        val south = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        if (question.multiple) {
            checkBoxes.forEach { south.add(it) }
        } else {
            val group = ButtonGroup()
            radioButtons.forEach { group.add(it); south.add(it) }
        }
        south.add(JLabel("Additional details (optional):"))
        south.add(custom)
        panel.add(south, BorderLayout.SOUTH)
        return panel
    }

    override fun createActions(): Array<Action> {
        val answer = object : AbstractAction("Answer") {
            override fun actionPerformed(e: ActionEvent?) {
                val selected = if (question.multiple) {
                    checkBoxes.filter { it.isSelected }.map { it.text }
                } else {
                    radioButtons.firstOrNull { it.isSelected }?.text?.let { listOf(it) } ?: emptyList()
                }
                result = PlanAnswer(
                    id = question.id,
                    selected = selected,
                    custom = custom.text.trim().takeIf { it.isNotEmpty() },
                )
                close(OK_EXIT_CODE)
            }
        }
        return arrayOf(answer)
    }

    companion object {
        /** Shows the dialog on the EDT and blocks until answered; null when dismissed. */
        fun ask(question: PlanQuestion): PlanAnswer? {
            var answer: PlanAnswer? = null
            SwingUtilities.invokeAndWait {
                val dialog = QuestionDialog(question)
                if (dialog.showAndGet()) answer = dialog.result
            }
            return answer
        }
    }
}
