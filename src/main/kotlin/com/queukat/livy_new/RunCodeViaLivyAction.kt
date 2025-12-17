package com.queukat.livy_new

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.queukat.livy_new.editor.LivyConsoleFileType

class RunCodeViaLivyAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectedText = editor?.selectionModel?.selectedText.orEmpty()
        openLivyConsole(project, selectedText)
    }

    private fun openLivyConsole(project: Project, initialCode: String) {
        val fileType = FileTypeManager.getInstance()
            .getFileTypeByExtension(LivyConsoleFileType.EXTENSION)

        val vf = LightVirtualFile(
            "Livy Query Console.${LivyConsoleFileType.EXTENSION}",
            fileType,
            initialCode
        )

        FileEditorManager.getInstance(project).openFile(vf, true)
    }
}
