package com.queukat.livy_new.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
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
    }

    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = consolePanel.preferredFocusComponent
    override fun getName(): String = "Livy Console"
    override fun getFile(): VirtualFile = myFile

    override fun setState(state: FileEditorState) {}
    override fun getState(level: FileEditorStateLevel): FileEditorState = SimpleEditorState()
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun selectNotify() {}
    override fun deselectNotify() {}
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun dispose() {}
}

class SimpleEditorState : FileEditorState {
    override fun canBeMergedWith(other: FileEditorState, level: FileEditorStateLevel): Boolean = false
}
