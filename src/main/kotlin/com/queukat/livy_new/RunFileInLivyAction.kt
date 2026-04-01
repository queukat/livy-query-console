package com.queukat.livy_new

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class RunFileInLivyAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val sourceRequest = if (project != null) resolveWholeFileRequest(e) else null
        e.presentation.isEnabledAndVisible = project != null && sourceRequest?.snippet?.isNotBlank() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val sourceRequest = resolveWholeFileRequest(e) ?: return
        val settings = LivyPluginSettings.getInstance().pluginState
        val chosenProfile = chooseLivyProfile(
            settings = settings,
            project = project,
            dialogTitle = "Run File in Livy",
            dialogMessage = "Choose the connection profile for this Livy file run."
        ) ?: return
        settings.setActiveProfile(chosenProfile.id)
        val executionTarget = LivyExecutionTarget.capture(settings, chosenProfile.id)
        openLivyWorkSurface(
            project = project,
            executionTarget = executionTarget,
            request = LivyWorkSurfaceRequest(
                snippet = sourceRequest.snippet,
                origin = sourceRequest.origin,
                contentMode = LivyWorkSurfaceContentMode.NONE,
                autorun = true
            )
        )
    }
}
