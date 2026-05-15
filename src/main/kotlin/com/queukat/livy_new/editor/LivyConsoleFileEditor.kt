package com.queukat.livy_new.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.queukat.livy_new.LivyExecutionTargets
import com.queukat.livy_new.LivyWorkSurfaceRequests
import com.queukat.livy_new.LivyWorkSurfaceRequest
import com.queukat.livy_new.workFileMode
import com.queukat.livy_new.editor.ui.LivyConsolePanel
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class LivyConsoleFileEditor(
    private val project: Project,
    private val myFile: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val mainPanel = JPanel(BorderLayout())
    private val consolePanel: LivyConsolePanel

    init {
        consolePanel = LivyConsolePanel(project, myFile)
        mainPanel.add(consolePanel, BorderLayout.CENTER)
        LivyWorkSurfaceRequests.consume(myFile)?.let { request ->
            consolePanel.applyWorkSurfaceRequest(request)
        }
    }

    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = consolePanel.preferredFocusComponent
    override fun getName(): String {
        val target = LivyExecutionTargets.resolve(myFile)
        return "Livy Work File (${target.profileName} - ${target.workFileMode().displayName})"
    }
    override fun getFile(): VirtualFile = myFile

    override fun setState(state: FileEditorState) {
        // The editor state is derived from the bound virtual file and does not need restoration.
    }
    override fun getState(level: FileEditorStateLevel): FileEditorState = SimpleEditorState()
    override fun isModified(): Boolean = FileDocumentManager.getInstance().isFileModified(myFile)
    override fun isValid(): Boolean = true
    override fun selectNotify() {
        // No selection-specific resources are acquired by this editor.
    }
    override fun deselectNotify() {
        // No selection-specific resources are retained by this editor.
    }
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        // This editor does not publish property changes.
    }
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        // This editor does not publish property changes.
    }
    override fun getCurrentLocation(): FileEditorLocation? = null
    fun applyWorkSurfaceRequest(request: LivyWorkSurfaceRequest) {
        consolePanel.applyWorkSurfaceRequest(request)
    }
    override fun dispose() {
        consolePanel.disposePanel()
    }
}

class SimpleEditorState : FileEditorState {
    override fun canBeMergedWith(other: FileEditorState, level: FileEditorStateLevel): Boolean = false
}
