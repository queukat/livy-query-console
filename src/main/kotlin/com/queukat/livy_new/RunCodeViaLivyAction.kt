package com.queukat.livy_new

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

class RunCodeViaLivyAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        // Keep action available from Tools menu even when no editor is focused.
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        openLivyWorkFile(project, e)
    }

    private fun openLivyWorkFile(project: Project, e: AnActionEvent) {
        val settings = LivyPluginSettings.getInstance().pluginState
        val chosenProfile = chooseLivyProfile(
            settings = settings,
            project = project,
            dialogTitle = "Open Livy Work File",
            dialogMessage = "Choose the connection profile for the Livy work file."
        ) ?: return
        settings.setActiveProfile(chosenProfile.id)
        val executionTarget = LivyExecutionTarget.capture(settings, chosenProfile.id)
        val request = resolveOpenRequest(e)
        openLivyWorkSurface(
            project = project,
            executionTarget = executionTarget,
            request = request ?: LivyWorkSurfaceRequest()
        )
    }

    private fun resolveOpenRequest(e: AnActionEvent): LivyWorkSurfaceRequest? {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (editor != null && file != null) {
            val selection = editor.selectionModel
            val sourceRequest = if (selection.hasSelection()) {
                resolveSelectionOrCurrentLineRequest(editor, file)
            } else {
                resolveWholeFileRequest(editor.document.text, file)
            }
            return LivyWorkSurfaceRequest(
                snippet = sourceRequest.snippet,
                origin = sourceRequest.origin,
                contentMode = LivyWorkSurfaceContentMode.REPLACE
            )
        }

        val sourceRequest = resolveWholeFileRequest(e) ?: return null
        return LivyWorkSurfaceRequest(
            snippet = sourceRequest.snippet,
            origin = sourceRequest.origin,
            contentMode = LivyWorkSurfaceContentMode.REPLACE
        )
    }
}
