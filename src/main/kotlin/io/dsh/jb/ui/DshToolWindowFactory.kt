package io.dsh.jb.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Roadmap item 2 shell; roadmap item 5 mounts the real chat transcript here.
 */
class DshToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DshChatPanel(project)
        Disposer.register(project, panel)
        toolWindow.component.add(panel)
    }
}
