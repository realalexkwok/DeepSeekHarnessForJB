package io.dsh.jb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Roadmap item 2: the empty tool-window shell.
 * Roadmap item 5 replaces this placeholder with the real chat transcript UI.
 */
class DshToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        panel.add(
            JBLabel("DSH Community — chat transcript arrives with roadmap item 5"),
            BorderLayout.CENTER,
        )
        toolWindow.component.add(panel)
    }
}
