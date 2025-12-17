package com.queukat.livy_new

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import javax.swing.DefaultListSelectionModel
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class ShowStatementsDialog(
    private val client: LivyClient,
    private val sessionId: Int,
    private val project: Project
) : DialogWrapper(true) {

    private val tableModel = DefaultTableModel(arrayOf("ID", "Code", "State", "Output Status"), 0)
    private val table = JBTable(tableModel).apply {
        selectionModel = DefaultListSelectionModel().apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
        }
        fillsViewportHeight = true
        preferredScrollableViewportSize = Dimension(800, 300)
    }

    init {
        title = "Statements for session $sessionId"
        isModal = true
        init()
        setOKButtonText("Close")
        refreshStatementsAsync()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row { label("Session #$sessionId statements (descending order):") }
            row {
                scrollCell(JScrollPane(table)).resizableColumn().align(Align.FILL)
            }
        }
    }

    private fun refreshStatementsAsync() {
        LivyBackground.run(
            project = project,
            title = "Loading Livy statements",
            action = { _ -> client.listStatements(sessionId, from = 0, size = 50, orderDesc = true) },
            onSuccessUi = { stmts ->
                tableModel.rowCount = 0
                for (stmt in stmts) {
                    tableModel.addRow(
                        arrayOf(
                            stmt.id ?: -1,
                            shorten(stmt.code.orEmpty()),
                            stmt.state.orEmpty(),
                            stmt.output?.status ?: "no output"
                        )
                    )
                }
            }
        )
    }

    private fun shorten(code: String, maxLen: Int = 60): String {
        return if (code.length > maxLen) code.substring(0, maxLen - 3) + "..." else code
    }
}
