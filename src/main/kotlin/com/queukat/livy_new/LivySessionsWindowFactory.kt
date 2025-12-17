package com.queukat.livy_new

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.queukat.livy_new.bottompanel.LivySessionsPanel

/**
 * Creates the "Livy Sessions" tool window with the [LivySessionsPanel].
 */
class LivySessionsWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LivySessionsPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, "Livy Sessions", false)
        toolWindow.contentManager.addContent(content)
    }
}
