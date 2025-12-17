package com.queukat.livy_new.bottompanel

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.queukat.livy_new.LivyBackground
import com.queukat.livy_new.LivyClientProvider
import com.queukat.livy_new.LivyPluginSettings
import com.queukat.livy_new.Session
import com.queukat.livy_new.SessionLogsDialog
import com.queukat.livy_new.ShowStatementsDialog
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

class LivySessionsPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tabbedPane = JBTabbedPane()
    private val sessionsPanel = JPanel(BorderLayout())

    private var visibleColumnIds: List<String> = SessionColumns.normalize(
        LivyPluginSettings.getInstance().pluginState.sessionTableColumns
    )

    private var sessionsTableModel: DefaultTableModel = createModelForColumns(visibleColumnIds)

    private val sessionsTable = JBTable(sessionsTableModel).apply {
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        setAutoCreateRowSorter(true) // сортировка, поэтому selection -> convertRowIndexToModel
    }

    // Чтобы actions работали даже если колонка ID скрыта
    private val rowSessions: MutableList<Session> = mutableListOf()

    private val refreshButton = JButton("Refresh Sessions")
    private val chooseColumnsButton = JButton("Columns…")
    private val showStatementsButton = JButton("Show Statements")
    private val viewLogsButton = JButton("View Logs")

    init {
        add(tabbedPane, BorderLayout.CENTER)

        sessionsPanel.add(JBScrollPane(sessionsTable), BorderLayout.CENTER)

        val southPanel = JPanel().apply {
            add(refreshButton)
            add(chooseColumnsButton)
            add(showStatementsButton)
            add(viewLogsButton)
        }
        sessionsPanel.add(southPanel, BorderLayout.SOUTH)

        tabbedPane.addTab("Sessions", sessionsPanel)

        refreshButton.addActionListener { refreshSessions() }
        chooseColumnsButton.addActionListener { chooseColumns() }
        showStatementsButton.addActionListener { showStatementsForSelectedSession() }
        viewLogsButton.addActionListener { showLogsForSelectedSession() }

        refreshSessions()
    }

    fun refreshSessions() {
        LivyBackground.run(
            project = project,
            title = "Refreshing Livy sessions",
            action = { _ ->
                LivyClientProvider.getInstance().fromSettings().getSessions()
            },
            onSuccessUi = { sessions ->
                rowSessions.clear()
                rowSessions.addAll(sessions)

                // очистка таблицы
                sessionsTableModel.rowCount = 0

                // добавляем строки под текущие видимые колонки
                for (s in sessions) {
                    sessionsTableModel.addRow(SessionColumns.valuesFor(s, visibleColumnIds))
                }
            },
            onErrorUi = { e ->
                Messages.showErrorDialog(
                    project,
                    "Failed to refresh sessions: ${e.message}",
                    "Livy Error"
                )
            }
        )
    }

    private fun chooseColumns() {
        val dialog = ChooseSessionColumnsDialog(SessionColumns.ALL, visibleColumnIds)
        if (!dialog.showAndGet()) return

        val newIds = SessionColumns.normalize(dialog.chosenColumnIds)

        // Persist
        LivyPluginSettings.getInstance().pluginState.sessionTableColumns = newIds.toMutableList()

        // Apply
        visibleColumnIds = newIds
        sessionsTableModel = createModelForColumns(visibleColumnIds)
        sessionsTable.model = sessionsTableModel

        // Refill existing cached sessions into new model (без запроса к Livy)
        sessionsTableModel.rowCount = 0
        for (s in rowSessions) {
            sessionsTableModel.addRow(SessionColumns.valuesFor(s, visibleColumnIds))
        }
    }

    private fun selectedSession(): Session? {
        val viewRow = sessionsTable.selectedRow
        if (viewRow < 0) return null
        val modelRow = sessionsTable.convertRowIndexToModel(viewRow)
        if (modelRow < 0 || modelRow >= rowSessions.size) return null
        return rowSessions[modelRow]
    }

    private fun showStatementsForSelectedSession() {
        val session = selectedSession()
        if (session == null) {
            Messages.showInfoMessage(project, "Please select a session in the table first.", "No Session Selected")
            return
        }
        session.id?.let { ShowStatementsDialog(LivyClientProvider.getInstance().fromSettings(), it, project).show() }
    }

    private fun showLogsForSelectedSession() {
        val session = selectedSession()
        if (session == null) {
            Messages.showInfoMessage(project, "Please select a session in the table first.", "No Session Selected")
            return
        }
        session.id?.let { SessionLogsDialog(LivyClientProvider.getInstance().fromSettings(), it, project).show() }
    }

    private fun createModelForColumns(columnIds: List<String>): DefaultTableModel {
        val headers = SessionColumns.titlesFor(columnIds)
        return object : DefaultTableModel(headers, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
    }
}
