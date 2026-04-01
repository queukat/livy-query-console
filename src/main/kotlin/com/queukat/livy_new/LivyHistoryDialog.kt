package com.queukat.livy_new

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class LivyHistoryDialog(
    private val project: Project,
    private val executionTarget: LivyExecutionTarget,
    private val onInsertSnippet: (String) -> Unit,
    private val onReplaceSnippet: (String) -> Unit,
    private val onRunSnippet: (String) -> Unit
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    private val tableModel = object : DefaultTableModel(arrayOf("When", "Status", "Session", "Stmt", "Preview"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(tableModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        setAutoCreateRowSorter(true)
        preferredScrollableViewportSize = Dimension(860, 240)
    }
    private val previewArea = JTextArea().apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
    }
    private var entries: List<LivyPluginSettings.ConsoleHistoryEntryState> = emptyList()

    init {
        title = "Recent Snippets - ${executionTarget.profileName}"
        setResizable(true)
        table.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updatePreview()
            }
        }
        init()
        reloadEntries()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row { label("Local plain-text history for ${executionTarget.profileName} (${executionTarget.baseUrl})") }
            row { label("Only recent snippets are stored locally. No remote session restore or secure storage is implied.") }
            row {
                scrollCell(JScrollPane(table)).resizableColumn().align(Align.FILL)
            }
            row {
                scrollCell(JScrollPane(previewArea).apply {
                    preferredSize = Dimension(860, 300)
                }).resizableColumn().align(Align.FILL)
            }.resizableRow()
        }
    }

    override fun createActions(): Array<Action> = arrayOf(
        object : DialogWrapperAction("Insert") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                selectedEntry()?.let { onInsertSnippet(it.snippet) } ?: showNoSelectionMessage()
            }
        },
        object : DialogWrapperAction("Replace") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                selectedEntry()?.let { onReplaceSnippet(it.snippet) } ?: showNoSelectionMessage()
            }
        },
        object : DialogWrapperAction("Run") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                selectedEntry()?.let { onRunSnippet(it.snippet) } ?: showNoSelectionMessage()
            }
        },
        object : DialogWrapperAction("Copy") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                val entry = selectedEntry() ?: run {
                    showNoSelectionMessage()
                    return
                }
                CopyPasteManager.getInstance().setContents(StringSelection(entry.snippet))
            }
        },
        object : DialogWrapperAction("Clear Profile History") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                val confirmed = Messages.showYesNoDialog(
                    project,
                    "Clear saved local history and the last draft for profile \"${executionTarget.profileName}\"?",
                    "Clear Livy History",
                    null
                )
                if (confirmed != Messages.YES) return
                LivyPluginSettings.getInstance().pluginState.clearLocalHistoryAndDrafts(executionTarget.profileId)
                reloadEntries()
            }
        },
        okAction
    )

    private fun reloadEntries() {
        entries = LivyPluginSettings.getInstance().pluginState.historyEntriesForProfile(executionTarget.profileId)
        tableModel.rowCount = 0
        entries.forEach { entry ->
            tableModel.addRow(
                arrayOf(
                    TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(entry.createdAtMs).atZone(ZoneId.systemDefault())),
                    entry.status,
                    entry.sessionId.takeIf { it >= 0 }?.toString().orEmpty(),
                    entry.statementId.takeIf { it >= 0 }?.toString().orEmpty(),
                    snippetPreview(entry.snippet)
                )
            )
        }
        if (entries.isNotEmpty()) {
            table.setRowSelectionInterval(0, 0)
        } else {
            previewArea.text = "No local snippet history saved for this profile yet."
        }
    }

    private fun selectedEntry(): LivyPluginSettings.ConsoleHistoryEntryState? {
        val viewRow = table.selectedRow
        if (viewRow < 0) return null
        val modelRow = table.convertRowIndexToModel(viewRow)
        return entries.getOrNull(modelRow)
    }

    private fun updatePreview() {
        val entry = selectedEntry() ?: run {
            previewArea.text = "Select a history item to inspect its full snippet."
            return
        }
        previewArea.text = buildString {
            appendLine("Saved: ${TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(entry.createdAtMs).atZone(ZoneId.systemDefault()))}")
            appendLine("Profile: ${entry.profileName}")
            appendLine("Server: ${entry.baseUrl}")
            appendLine("Kind: ${entry.languageOrKind.ifBlank { "n/a" }}")
            appendLine("Status: ${entry.status}")
            appendLine("Session: ${entry.sessionId.takeIf { it >= 0 }?.toString() ?: "n/a"}")
            appendLine("Statement: ${entry.statementId.takeIf { it >= 0 }?.toString() ?: "n/a"}")
            if (entry.snippetTruncated) {
                appendLine("Note: stored snippet was truncated to the local history limit.")
            }
            appendLine()
            append(entry.snippet)
        }
        previewArea.caretPosition = 0
    }

    private fun showNoSelectionMessage() {
        Messages.showInfoMessage(project, "Select a history item first.", "Livy History")
    }

    private fun snippetPreview(snippet: String): String =
        snippet
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .replace('\t', ' ')
            .let { if (it.length > 70) it.take(67) + "..." else it }

    companion object {
        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
