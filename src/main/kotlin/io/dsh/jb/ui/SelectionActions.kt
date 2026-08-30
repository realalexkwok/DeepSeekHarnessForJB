package io.dsh.jb.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import io.dsh.jb.chat.ComposerAction

/**
 * Roadmap item 11 remainder: editor-popup context actions on a text selection.
 * Each activates the DSH Community tool window and preloads the composer; the
 * prompt assembly still injects the live selection + file at send time.
 */
abstract class SelectionAction(
    private val composerAction: ComposerAction,
    private val promptText: String,
) : DumbAwareAction() {

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabled = editor?.selectionModel?.selectedText?.isNotBlank() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        if (editor.selectionModel.selectedText.isNullOrBlank()) return
        ToolWindowManager.getInstance(project).getToolWindow("DSH Community")?.activate(null)
        project.getService(ComposerRequests::class.java).submit(composerAction, promptText)
    }
}

class AskSelectionAction : SelectionAction(ComposerAction.ASK, "Ask about the selected code: ")
class ExplainSelectionAction : SelectionAction(ComposerAction.ASK, "Explain the selected code.")
class FixSelectionAction : SelectionAction(ComposerAction.FIX, "Find and fix problems in the selected code.")
