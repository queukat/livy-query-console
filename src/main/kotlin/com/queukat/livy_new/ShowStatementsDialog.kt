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
import javax.swing.Action
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class ShowStatementsDialog(
    private val client: LivyClient,
    private val sessionId: Int,
    private val project: Project,
    private val executionTarget: LivyExecutionTarget? = null
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    private val tableModel = object : DefaultTableModel(arrayOf("ID", "Code", "State", "Output Status"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(tableModel).apply {
        selectionModel = DefaultListSelectionModel().apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }
        fillsViewportHeight = true
        preferredScrollableViewportSize = Dimension(800, 300)
        setAutoCreateRowSorter(true)
    }
    private var statements: List<Statement> = emptyList()

    init {
        title = "Statements for session $sessionId"
        setOKButtonText("Close")
        setResizable(true)
        init()
        refreshStatementsAsync()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            executionTarget?.profileName?.takeIf { it.isNotBlank() }?.let { name ->
                row { label("Profile: $name") }
            }
            executionTarget?.baseUrl?.takeIf { it.isNotBlank() }?.let { url ->
                row { label("Server: $url") }
            }
            row { label("Session #$sessionId statements (descending order):") }
            row {
                scrollCell(JScrollPane(table)).resizableColumn().align(Align.FILL)
            }
        }
    }

    override fun createActions(): Array<Action> = arrayOf(
        object : DialogWrapperAction("Refresh") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                refreshStatementsAsync()
            }
        },
        object : DialogWrapperAction("Inspect Selected") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                inspectSelectedStatement()
            }
        },
        object : DialogWrapperAction("Copy Code") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                val statement = selectedStatement() ?: run {
                    showNoSelectionMessage()
                    return
                }
                val code = statement.code.orEmpty()
                if (code.isBlank()) {
                    Messages.showInfoMessage(project, "The selected statement has no code payload.", "Livy Statements")
                    return
                }
                CopyPasteManager.getInstance().setContents(StringSelection(code))
            }
        },
        object : DialogWrapperAction("Open in Work File") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                val target = executionTarget ?: run {
                    Messages.showInfoMessage(project, "This statements view is not bound to a Livy work-file profile context.", "Livy Statements")
                    return
                }
                val statement = selectedStatement() ?: run {
                    showNoSelectionMessage()
                    return
                }
                val code = statement.code.orEmpty()
                if (code.isBlank()) {
                    Messages.showInfoMessage(project, "The selected statement has no code payload.", "Livy Statements")
                    return
                }
                openLivyConsole(project, target, code)
            }
        },
        okAction
    )

    private fun refreshStatementsAsync() {
        LivyBackground.run(
            project = project,
            title = "Loading Livy statements",
            action = { _ -> client.listStatements(sessionId, from = 0, size = 50, orderDesc = true) },
            onSuccessUi = { loadedStatements ->
                statements = loadedStatements
                tableModel.rowCount = 0
                loadedStatements.forEach { stmt ->
                    tableModel.addRow(
                        arrayOf<Any>(
                            stmt.id ?: -1,
                            shorten(stmt.code.orEmpty()),
                            stmt.state.orEmpty(),
                            stmt.output?.status ?: "no output"
                        )
                    )
                }
            },
            onErrorUi = { e ->
                val target = executionTarget
                if (
                    target != null &&
                        maybePromptForBrowserAuthentication(
                            failure = e,
                            profile = target.settingsSnapshot,
                            project = project
                        ) {
                            refreshStatementsAsync()
                        }
                ) {
                    return@run
                }
                Messages.showErrorDialog(
                    project,
                    "Failed to load statements: ${e.message}",
                    "Livy Error"
                )
            }
        )
    }

    private fun inspectSelectedStatement() {
        val selected = selectedStatement() ?: run {
            showNoSelectionMessage()
            return
        }
        val statementId = selected.id ?: run {
            Messages.showInfoMessage(project, "The selected statement has no id.", "Livy Statements")
            return
        }
        LivyBackground.run(
            project = project,
            title = "Loading statement details",
            action = { _ -> client.getStatement(sessionId, statementId) },
            onSuccessUi = { statement ->
                LivyStatementDetailsDialog(
                    project = project,
                    sessionId = sessionId,
                    statement = statement,
                    executionTarget = executionTarget
                ).show()
            },
            onErrorUi = { error ->
                val target = executionTarget
                if (
                    target != null &&
                        maybePromptForBrowserAuthentication(
                            failure = error,
                            profile = target.settingsSnapshot,
                            project = project
                        ) {
                            inspectSelectedStatement()
                        }
                ) {
                    return@run
                }
                Messages.showErrorDialog(project, "Failed to load statement details: ${error.message}", "Livy Error")
            }
        )
    }

    private fun selectedStatement(): Statement? {
        val viewRow = table.selectedRow
        if (viewRow < 0) return null
        val modelRow = table.convertRowIndexToModel(viewRow)
        return statements.getOrNull(modelRow)
    }

    private fun showNoSelectionMessage() {
        Messages.showInfoMessage(project, "Select a statement first.", "Livy Statements")
    }

    private fun shorten(code: String, maxLen: Int = 60): String =
        if (code.length > maxLen) code.substring(0, maxLen - 3) + "..." else code
}
