package com.queukat.livy_new.editor.ui

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorTextField
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.util.ui.UIUtil
import com.queukat.livy_new.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*
import javax.swing.table.DefaultTableModel

class LivyConsolePanel(
    private val project: Project,
    private val file: VirtualFile
) : JPanel(BorderLayout()) {

    private val gsonPretty: Gson = GsonBuilder().setPrettyPrinting().create()

    private val progressBar = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
    }

    private val infoLabel = JBLabel("Tip: If some text is selected here, only that selection will be executed.").apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private val codeDocument = EditorFactory.getInstance().createDocument(readFileText(file))

    private val codeField = EditorTextField(
        codeDocument,
        project,
        FileTypes.PLAIN_TEXT,
        /* isViewer = */ false,
        /* oneLineMode = */ false
    ).apply {
        preferredSize = Dimension(600, 180)
    }

    val preferredFocusComponent: JComponent get() = codeField

    private val resultsTabbedPane = JBTabbedPane()
    private var statementCounter = 0

    private var isRunning = false
    private var currentStatementId: Int? = null
    private var lastUsedSessionId: Int? = null

    private val toolbar: ActionToolbar

    init {
        val group = DefaultActionGroup().apply {
            add(RunAction())
            add(CancelAction())
            add(ShowLogsAction())
            addSeparator()
            add(CompleteAction())
        }
        toolbar = ActionManager.getInstance().createActionToolbar("LivyConsoleToolbar", group, true).apply {
            targetComponent = this@LivyConsolePanel
        }

        val top = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH)
            add(infoLabel, BorderLayout.SOUTH)
        }

        val codePanel = JPanel(BorderLayout()).apply {
            add(top, BorderLayout.NORTH)
            add(codeField, BorderLayout.CENTER)
        }

        val splitter = OnePixelSplitter(false, 0.28f).apply {
            firstComponent = codePanel
            secondComponent = resultsTabbedPane
        }

        add(progressBar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
    }

    private inner class RunAction : DumbAwareAction("Run", "Run code via Livy", AllIcons.Actions.Execute) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !isRunning && codeField.text.isNotBlank()
        }
        override fun actionPerformed(e: AnActionEvent) = executeCode()
    }

    private inner class CancelAction : DumbAwareAction("Cancel", "Cancel running statement", AllIcons.Actions.Suspend) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = isRunning && currentStatementId != null && lastUsedSessionId != null
        }
        override fun actionPerformed(e: AnActionEvent) = cancelStatement()
    }

    private inner class ShowLogsAction : DumbAwareAction("Show Logs", "Show logs for last used session", AllIcons.Toolwindows.ToolWindowMessages) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = lastUsedSessionId != null
        }
        override fun actionPerformed(e: AnActionEvent) = showLogs()
    }

    private inner class CompleteAction : DumbAwareAction("Complete", "Get completion suggestions from Livy", AllIcons.Actions.Lightning) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !isRunning && codeField.text.isNotBlank()
        }
        override fun actionPerformed(e: AnActionEvent) = doCodeCompletion()
    }

    private fun executeCode() {
        val ed = codeField.editor
        val selection = ed?.selectionModel?.selectedText?.takeIf { it.isNotBlank() }
        val code = selection ?: codeField.text

        if (code.isBlank()) {
            Messages.showInfoMessage(project, "No code to run.", "Livy")
            return
        }

        setRunning(true)

        Thread {
            try {
                val settings = LivyPluginSettings.getInstance().pluginState
                val client = LivyClientProvider.getInstance().fromSettings()
                val sessionManager = SessionManager(client, settings.maxSessions)

                val session = sessionManager.getSession()
                val sessionId = session.id ?: throw RuntimeException("Livy returned a session without id.")
                lastUsedSessionId = sessionId

                waitForSessionIdle(client, sessionId)

                val statement = client.runStatement(sessionId, code)
                val statementId = statement.id ?: throw RuntimeException("Livy returned a statement without id.")
                currentStatementId = statementId

                val finalStatement = waitForStatementAvailable(client, sessionId, statementId)

                SwingUtilities.invokeLater {
                    statementCounter++
                    val tabTitle = "Result #$statementCounter"
                    val outputPanel = createOutputPanel(sessionId, finalStatement)
                    resultsTabbedPane.addTab(tabTitle, outputPanel)
                    resultsTabbedPane.selectedIndex = resultsTabbedPane.tabCount - 1
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, "Failed to execute code: ${e.message}", "Livy Error")
                }
            } finally {
                SwingUtilities.invokeLater { setRunning(false) }
            }
        }.start()
    }

    private fun cancelStatement() {
        val statementId = currentStatementId
        val sessionId = lastUsedSessionId
        if (statementId == null || sessionId == null) return

        Thread {
            try {
                LivyClientProvider.getInstance().fromSettings().cancelStatement(sessionId, statementId)
                SwingUtilities.invokeLater {
                    Messages.showInfoMessage(project, "Cancel requested for statement #$statementId.", "Livy")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, "Failed to cancel: ${e.message}", "Livy Error")
                }
            }
        }.start()
    }

    private fun showLogs() {
        val sessionId = lastUsedSessionId ?: run {
            Messages.showInfoMessage(project, "No session in use yet. Run something first!", "Livy")
            return
        }
        SessionLogsDialog(LivyClientProvider.getInstance().fromSettings(), sessionId, project).show()
    }

    private fun doCodeCompletion() {
        Messages.showInfoMessage(project, "Completion is not wired here yet (later we’ll move to ProgressManager).", "Livy")
    }

    private fun createOutputPanel(sessionId: Int, statement: Statement): JPanel {
        val out = statement.output
        val status = out?.status.orEmpty()

        val rawJson = gsonPretty.toJson(out)
        val rawTextArea = JTextArea(
            buildString {
                appendLine("Session ID: $sessionId")
                appendLine("Statement ID: ${statement.id ?: "?"}")
                appendLine("State: ${statement.state ?: "?"}")
                appendLine()
                append(rawJson)
            }
        ).apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
        }

        val prettyTextArea = JTextArea(buildPrettyText(out)).apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
        }

        val plainText = (out?.data?.get("text/plain") as? String).orEmpty()
        val table = parseAsciiTableOrNull(plainText)

        val tableComponent: JComponent = if (table != null) {
            table
        } else {
            JTextArea(
                "No ASCII table detected.\n" +
                        "If you want a table, print it (Scala):\n" +
                        "  spark.range(100).show(20, false)\n\n" +
                        "text/plain:\n\n$plainText"
            ).apply {
                isEditable = false
                font = Font("Monospaced", Font.PLAIN, 12)
                lineWrap = true
                wrapStyleWord = true
            }
        }

        val tabs = JBTabbedPane()
        tabs.addTab("Raw", JScrollPane(rawTextArea))
        tabs.addTab("Table", JScrollPane(tableComponent))
        tabs.addTab("Pretty", JScrollPane(prettyTextArea))

        // если ошибка — отдельная вкладка, чтобы было видно сразу
        if (status == "error") {
            val err = JTextArea(
                buildString {
                    appendLine("${out?.ename.orEmpty()}: ${out?.evalue.orEmpty()}")
                    appendLine()
                    val tb = out?.traceback ?: emptyList()
                    if (tb.isEmpty()) {
                        appendLine("(No traceback provided by Livy)")
                    } else {
                        tb.forEach { appendLine(it) }
                    }
                }
            ).apply {
                isEditable = false
                font = Font("Monospaced", Font.PLAIN, 12)
            }
            tabs.addTab("Error", JScrollPane(err))
            tabs.selectedIndex = tabs.tabCount - 1
        }

        return JPanel(BorderLayout()).apply { add(tabs, BorderLayout.CENTER) }
    }

    private fun buildPrettyText(out: StatementOutput?): String {
        if (out == null) return "No output."

        return buildString {
            appendLine("status: ${out.status}")
            appendLine("execution_count: ${out.execution_count}")
            if (out.status == "error") {
                appendLine()
                appendLine("ename: ${out.ename}")
                appendLine("evalue: ${out.evalue}")
                val tb = out.traceback ?: emptyList()
                if (tb.isNotEmpty()) {
                    appendLine()
                    appendLine("traceback:")
                    tb.forEach { appendLine(it) }
                }
            } else {
                val data = out.data ?: emptyMap()
                appendLine()
                appendLine("data:")
                appendLine(gsonPretty.toJson(data))
            }
        }
    }

    private fun waitForSessionIdle(client: LivyClient, sessionId: Int) {
        while (true) {
            val s = client.getSession(sessionId)
            val state = s.state ?: throw RuntimeException("Session #$sessionId has null state.")
            if (state in listOf("idle", "error", "dead", "killed")) {
                if (state != "idle") throw RuntimeException("Session #$sessionId ended up in state: $state")
                return
            }
            Thread.sleep(1000)
        }
    }

    private fun waitForStatementAvailable(client: LivyClient, sessionId: Int, statementId: Int): Statement {
        while (true) {
            val st = client.getStatement(sessionId, statementId)
            val state = st.state ?: throw RuntimeException("Statement #$statementId has null state.")
            if (state in listOf("available", "error", "cancelled")) {
                return st
            }
            Thread.sleep(1000)
        }
    }

    private fun parseAsciiTableOrNull(asciiTable: String): JTable? {
        val lines = asciiTable.lines().map { it.trimEnd() }
        val borderIndices = lines.mapIndexedNotNull { i, line ->
            if (line.startsWith("+") && line.endsWith("+")) i else null
        }
        if (borderIndices.size < 2) return null

        val headerLineIndex = borderIndices[0] + 1
        if (headerLineIndex >= lines.size) return null
        val headerCells = parseLineAsCells(lines[headerLineIndex])
        if (headerCells.isEmpty()) return null

        val dataRows = mutableListOf<List<String>>()
        val dataStartIndex = borderIndices[1] + 1
        var i = dataStartIndex
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("+") && line.endsWith("+")) break
            if (line.startsWith("|")) dataRows.add(parseLineAsCells(line))
            i++
        }

        val model = object : DefaultTableModel() {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        model.setColumnIdentifiers(headerCells.toTypedArray())
        for (row in dataRows) {
            val rowCells = row + List(headerCells.size - row.size) { "" }
            model.addRow(rowCells.toTypedArray())
        }

        return JTable(model).apply { font = Font("Monospaced", Font.PLAIN, 12) }
    }

    private fun parseLineAsCells(line: String): List<String> {
        val rawParts = line.trim().split('|')
        return rawParts.drop(1).dropLast(1).map { it.trim() }
    }

    private fun readFileText(vf: VirtualFile): String =
        try { String(vf.contentsToByteArray(), Charsets.UTF_8) } catch (_: Exception) { "" }

    private fun setRunning(running: Boolean) {
        isRunning = running
        progressBar.isVisible = running
        toolbar.updateActionsImmediately()
    }
}
