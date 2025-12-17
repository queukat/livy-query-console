package com.queukat.livy_new.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class LivyConsoleFileEditorProvider : FileEditorProvider {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        // не сравниваем по instance — сравниваем по имени fileType
        return file.fileType.name == LivyConsoleFileType.DISPLAY_NAME
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return LivyConsoleFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "LivyConsoleFileEditor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
