package io.dsh.jb.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import io.dsh.jb.settings.DshSettingsConfigurable

/**
 * Roadmap item 2 shell; roadmap item 5 mounts the real chat transcript here.
 *
 * Item 22 follow-up (host feedback 2026-09-01, Kilo parity): the chrome lives
 * in the tool-window TITLE BAR like Kilo's KiloToolWindowSetupService —
 * New Session → History icon buttons via [ToolWindow.setTitleActions], and
 * Settings inside the vertical-… (Options) gear menu via
 * [ToolWindow.setAdditionalGearActions].
 */
class DshToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DshChatPanel(project)
        Disposer.register(project, panel)
        toolWindow.component.add(panel)
        toolWindow.setTitleActions(
            listOf(
                object : DumbAwareAction("New Session", "Start a new DSH session", AllIcons.General.Add) {
                    override fun actionPerformed(e: AnActionEvent) { panel.startNewSession() }
                },
                object : DumbAwareAction("History", "Show session history", AllIcons.Vcs.History) {
                    override fun actionPerformed(e: AnActionEvent) { panel.showHistoryView() }
                },
            ),
        )
        toolWindow.setAdditionalGearActions(
            DefaultActionGroup(
                object : DumbAwareAction("Settings", "DeepSeek Harness settings", AllIcons.General.GearPlain) {
                    override fun actionPerformed(e: AnActionEvent) {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(project, DshSettingsConfigurable::class.java)
                    }
                },
            ),
        )
    }
}
