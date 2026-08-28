package io.dsh.jb.ui

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import io.dsh.jb.diff.FileChange
import java.awt.BorderLayout
import java.io.File
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Roadmap item 7: previews the fs tools' result-time diffs with the platform
 * Diff framework and applies/rejects them workspace-confined.
 */
class DshDiffDialog(
    private val project: Project,
    private val workspace: File,
    private val changes: List<FileChange>,
) : DialogWrapper(project) {

    private val fileCombo = JComboBox(changes.map { it.path }.toTypedArray())
    private val applied = BooleanArray(changes.size)
    private var diffPanel: DiffRequestPanel? = null

    init {
        title = "DSH — ${changes.size} file change${if (changes.size == 1) "" else "s"}"
        setOKButtonText("Apply All")
        setCancelButtonText("Reject All")
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        val top = JPanel(BorderLayout(8, 0))
        top.add(JLabel("File:"), BorderLayout.WEST)
        top.add(fileCombo, BorderLayout.CENTER)
        val applyCurrent = JButton("Apply")
        top.add(applyCurrent, BorderLayout.EAST)
        panel.add(top, BorderLayout.NORTH)
        diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)
        panel.add(diffPanel!!.component, BorderLayout.CENTER)
        fileCombo.addActionListener { showCurrent() }
        applyCurrent.addActionListener {
            val index = fileCombo.selectedIndex
            if (index >= 0 && applyChange(changes[index])) {
                applied[index] = true
                applyCurrent.text = if (applied.all { it }) "All applied" else "Apply"
            }
        }
        showCurrent()
        return panel
    }

    private fun showCurrent() {
        val index = fileCombo.selectedIndex
        if (index < 0) return
        val change = changes[index]
        val request = SimpleDiffRequest(
            change.path,
            DiffContentFactory.getInstance().create(project, change.oldText ?: ""),
            DiffContentFactory.getInstance().create(project, change.newText),
            if (change.oldText == null) "(new file)" else "before",
            "after",
        )
        diffPanel?.setRequest(request)
    }

    override fun doOKAction() {
        for (i in changes.indices) {
            if (!applied[i]) applyChange(changes[i])
        }
        super.doOKAction()
    }

    /** Writes one change inside a write action; refuses out-of-workspace paths. */
    private fun applyChange(change: FileChange): Boolean {
        val base = workspace.canonicalFile
        val target = File(base, change.path).canonicalFile
        val basePrefix = base.path + File.separator
        if (target != base && !target.path.startsWith(basePrefix)) {
            Messages.showErrorDialog(
                project,
                "Refusing to write outside the workspace: ${change.path}",
                "DSH",
            )
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
            true
        } catch (e: Exception) {
            Messages.showErrorDialog(project, "Failed to apply ${change.path}: ${e.message}", "DSH")
            false
        }
    }
}
