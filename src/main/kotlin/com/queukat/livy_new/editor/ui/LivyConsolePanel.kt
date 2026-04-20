package com.queukat.livy_new.editor.ui

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
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
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.table.DefaultTableModel

class LivyConsolePanel(
    private val project: Project,
    private val file: VirtualFile
) : JPanel(BorderLayout()) {

    private val gsonPretty: Gson = GsonBuilder().setPrettyPrinting().create()
    private val executionTarget = LivyExecutionTargets.resolve(file)
    private val workFileMode = executionTarget.workFileMode()
    private val editorFileType = workFileMode.resolveEditorFileType()

    private val progressBar = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
    }

    private val contextLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private val infoLabel = JBLabel(buildInfoText()).apply {
        foreground = UIUtil.getContextHelpForeground()
        toolTipText = buildHeaderTooltip()
    }

    private val modeLabel = JBLabel().apply {
        foreground = UIUtil.getContextHelpForeground()
        toolTipText = buildHeaderTooltip()
    }

    private val codeDocument = resolveCodeDocument()

    private val codeField = EditorTextField(
        codeDocument,
        project,
        editorFileType,
        /* isViewer = */ false,
        /* oneLineMode = */ false
    ).apply {
        preferredSize = Dimension(600, 180)
    }

    val preferredFocusComponent: JComponent get() = codeField

    private val resultsTabbedPane = JBTabbedPane()
    private var statementCounter = 0
    private val panelDisposable = Disposer.newDisposable("LivyConsolePanel")

    @Volatile
    private var isRunning = false
    @Volatile
    private var currentStatement: LivyStatementRef? = null
    @Volatile
    private var lastUsedSession: LivySessionRef? = null
    @Volatile
    private var currentIndicator: ProgressIndicator? = null
    @Volatile
    private var disposed = false

    private val toolbar: ActionToolbar
    private val draftListener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            rememberCurrentDraft()
        }
    }

    init {
        contextLabel.toolTipText = buildHeaderTooltip()
        updateContextLabel()

        val group = DefaultActionGroup().apply {
            add(RunCurrentAction())
            add(RunFileAction())
            add(CancelAction())
            add(ShowLogsAction())
            add(HistoryAction())
            add(OpenSourceAction())
        }
        toolbar = ActionManager.getInstance().createActionToolbar("LivyConsoleToolbar", group, true).apply {
            targetComponent = this@LivyConsolePanel
        }

        val helpPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(contextLabel)
            add(modeLabel)
            add(infoLabel)
        }

        val top = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.NORTH)
            add(helpPanel, BorderLayout.SOUTH)
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
        codeDocument.addDocumentListener(draftListener, panelDisposable)
    }

    private inner class RunCurrentAction : DumbAwareAction("Run Current", "Run current selection or statement via Livy", AllIcons.Actions.Execute) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !isRunning && currentRunnableText().isNotBlank()
        }
        override fun actionPerformed(e: AnActionEvent) = executeCode()
    }

    private inner class RunFileAction : DumbAwareAction("Run File", "Run the full work file via Livy", AllIcons.Actions.Execute) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = !isRunning && codeField.text.isNotBlank()
        }
        override fun actionPerformed(e: AnActionEvent) = executeCode(explicitCode = codeField.text)
    }

    private inner class CancelAction : DumbAwareAction("Cancel", "Cancel running statement", AllIcons.Actions.Suspend) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = isRunning && currentStatement != null
        }
        override fun actionPerformed(e: AnActionEvent) = cancelStatement()
    }

    private inner class ShowLogsAction : DumbAwareAction("Show Logs", "Show logs for last used session", AllIcons.Toolwindows.ToolWindowMessages) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = lastUsedSession != null
        }
        override fun actionPerformed(e: AnActionEvent) = showLogs()
    }

    private inner class HistoryAction : DumbAwareAction("History", "Show recent local snippets for this profile", AllIcons.Actions.SearchWithHistory) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            val settings = LivyPluginSettings.getInstance().pluginState
            e.presentation.isEnabled = settings.historyEntriesForProfile(executionTarget.profileId).isNotEmpty()
        }
        override fun actionPerformed(e: AnActionEvent) = showHistory()
    }

    private inner class OpenSourceAction : DumbAwareAction("Open Source", "Jump back to the last source routed into this work file", AllIcons.Actions.EditSource) {
        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = currentSourceOrigin() != null
        }
        override fun actionPerformed(e: AnActionEvent) = openSourceOrigin()
    }

    private fun executeCode() {
        executeCode(explicitCode = null)
    }

    private fun executeCode(explicitCode: String?) {
        val code = explicitCode ?: currentRunnableText()
        val sourceOrigin = currentSourceOrigin()

        if (code.isBlank()) {
            Messages.showInfoMessage(project, "No code to run.", "Livy")
            return
        }

        rememberCurrentDraft()

        setRunning(true)
        currentStatement = null

        var executionResult: ExecutionResult? = null

        object : Task.Backgroundable(project, "Running code via Livy", true) {
            override fun run(indicator: ProgressIndicator) {
                currentIndicator = indicator
                try {
                    val client = LivyClientProvider.getInstance().get(executionTarget)
                    val sessionManager = SessionManager(client, executionTarget.settingsSnapshot)

                    val session = sessionManager.getSession(indicator)
                    val sessionId = session.id ?: throw RuntimeException("Livy returned a session without id.")
                    lastUsedSession = LivySessionRef(executionTarget.baseUrl, sessionId)

                    checkCanceled(indicator)
                    val statement = client.runStatement(sessionId, code)
                    val statementId = statement.id ?: throw RuntimeException("Livy returned a statement without id.")
                    currentStatement = LivyStatementRef(executionTarget.baseUrl, sessionId, statementId)

                    val finalStatement = waitForStatementAvailable(
                        client = client,
                        sessionId = sessionId,
                        statementId = statementId,
                        indicator = indicator,
                        timeoutMs = STATEMENT_WAIT_TIMEOUT_MS
                    )
                    executionResult = ExecutionResult(sessionId, finalStatement, sourceOrigin)
                } finally {
                    currentIndicator = null
                }
            }

            override fun onSuccess() {
                val result = executionResult ?: return
                if (disposed) return
                LivyPluginSettings.getInstance().pluginState.recordConsoleHistory(
                    target = executionTarget,
                    snippet = code,
                    status = historyStatusFor(result.statement),
                    sessionId = result.sessionId,
                    statementId = result.statement.id
                )
                statementCounter++
                addResultTab(result.sessionId, result.statement, "Result #$statementCounter", result.sourceOrigin)
                updateContextLabel()
            }

            override fun onThrowable(error: Throwable) {
                if (error is ProcessCanceledException || disposed) return
                if (
                    maybePromptForBrowserAuthentication(
                        failure = error,
                        profile = executionTarget.settingsSnapshot,
                        project = project
                    ) {
                        executeCode(explicitCode = code)
                    }
                ) {
                    return
                }
                LivyPluginSettings.getInstance().pluginState.recordConsoleHistory(
                    target = executionTarget,
                    snippet = code,
                    status = "failed_to_start"
                )
                Messages.showErrorDialog(project, "Failed to execute code: ${error.message}", "Livy Error")
            }

            override fun onFinished() {
                currentIndicator = null
                currentStatement = null
                if (!disposed) {
                    setRunning(false)
                    updateContextLabel()
                } else {
                    isRunning = false
                    progressBar.isVisible = false
                }
            }
        }.queue()
    }

    private fun cancelStatement() {
        val statementRef = currentStatement
        currentIndicator?.cancel()
        if (statementRef == null) return

        LivyBackground.run(
            project = project,
            title = "Cancelling Livy statement",
            action = { _ ->
                LivyClientProvider.getInstance()
                    .get(executionTarget)
                    .cancelStatement(statementRef.sessionId, statementRef.statementId)
            },
            onSuccessUi = {
                if (!disposed) {
                    Messages.showInfoMessage(
                        project,
                        "Cancel requested for statement #${statementRef.statementId} on ${executionTarget.profileName} (${statementRef.baseUrl}).",
                        "Livy"
                    )
                }
            },
            onErrorUi = { error ->
                if (
                    maybePromptForBrowserAuthentication(
                        failure = error,
                        profile = executionTarget.settingsSnapshot,
                        project = project
                    ) {
                        cancelStatement()
                    }
                ) {
                    return@run
                }
                if (error !is ProcessCanceledException && !disposed) {
                    Messages.showErrorDialog(project, "Failed to cancel: ${error.message}", "Livy Error")
                }
            }
        )
    }

    private fun showLogs() {
        val sessionRef = lastUsedSession ?: run {
            Messages.showInfoMessage(project, "No session in use yet. Run something first!", "Livy")
            return
        }
        SessionLogsDialog(
            client = LivyClientProvider.getInstance().get(executionTarget),
            sessionId = sessionRef.sessionId,
            project = project,
            serverUrl = sessionRef.baseUrl,
            profileName = executionTarget.profileName
        ).show()
    }

    private fun showHistory() {
        LivyHistoryDialog(
            project = project,
            executionTarget = executionTarget,
            onInsertSnippet = { snippet -> insertSnippet(snippet) },
            onReplaceSnippet = { snippet -> replaceEditorText(snippet) },
            onRunSnippet = { snippet ->
                replaceEditorText(snippet)
                executeCode(explicitCode = snippet)
            }
        ).show()
    }

    private fun openSourceOrigin() {
        val origin = currentSourceOrigin() ?: run {
            Messages.showInfoMessage(project, "This work file has no source origin yet.", "Livy")
            return
        }
        if (!origin.navigate(project)) {
            Messages.showErrorDialog(project, "Failed to open source: ${origin.sourcePath}", "Livy")
        }
    }

    private fun createOutputPanel(sessionId: Int, statement: Statement, sourceOrigin: LivySourceOrigin?): JPanel {
        val out = statement.output
        val status = out?.status.orEmpty()

        val rawJson = gsonPretty.toJson(out)
        val rawTextArea = JTextArea(
            buildString {
                appendLine("Profile: ${executionTarget.profileName}")
                appendLine("Server URL: ${executionTarget.baseUrl}")
                appendLine("Session ID: $sessionId")
                appendLine("Statement ID: ${statement.id ?: "?"}")
                appendLine("State: ${statement.state ?: "?"}")
                sourceOrigin?.let { appendLine("Source: ${it.presentableLabel()}") }
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

        val summaryLabel = JBLabel(
            buildString {
                append("Session #$sessionId | Statement #${statement.id ?: "?"} | State: ${statement.state.orEmpty().ifBlank { "unknown" }} | Output: ${status.ifBlank { "n/a" }}")
                sourceOrigin?.let { append(" | Source: ${it.presentableLabel()}") }
            }
        ).apply {
            foreground = UIUtil.getContextHelpForeground()
        }
        val actionsPanel = JPanel().apply {
            val code = statement.code.orEmpty()
            add(JButton("Reuse Code").apply {
                isEnabled = code.isNotBlank()
                addActionListener { replaceEditorText(code) }
            })
            add(JButton("Inspect").apply {
                addActionListener {
                    LivyStatementDetailsDialog(
                        project = project,
                        sessionId = sessionId,
                        statement = statement,
                        executionTarget = executionTarget,
                        sourceOrigin = sourceOrigin
                    ).show()
                }
            })
            add(JButton("Source").apply {
                isEnabled = sourceOrigin != null
                addActionListener {
                    if (sourceOrigin != null && !sourceOrigin.navigate(project)) {
                        Messages.showErrorDialog(project, "Failed to open source: ${sourceOrigin.sourcePath}", "Livy")
                    }
                }
            })
            add(JButton("Copy Raw").apply {
                addActionListener {
                    CopyPasteManager.getInstance().setContents(StringSelection(rawTextArea.text))
                }
            })
        }

        return JPanel(BorderLayout()).apply {
            add(JPanel(BorderLayout()).apply {
                add(summaryLabel, BorderLayout.CENTER)
                add(actionsPanel, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(tabs, BorderLayout.CENTER)
        }
    }

    internal fun addPreviewResult(
        sessionId: Int,
        statement: Statement,
        title: String = "Preview"
    ) {
        if (disposed) return
        addResultTab(sessionId, statement, title, currentSourceOrigin())
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

    private fun waitForStatementAvailable(
        client: LivyClient,
        sessionId: Int,
        statementId: Int,
        indicator: ProgressIndicator,
        timeoutMs: Long
    ): Statement {
        val deadlineMs = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadlineMs) {
            checkCanceled(indicator)
            val st = client.getStatement(sessionId, statementId)
            val state = st.state ?: throw RuntimeException("Statement #$statementId has null state.")
            if (state in listOf("available", "error", "cancelled")) {
                return st
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
        throw RuntimeException("Timed out waiting for statement #$statementId to finish.")
    }

    private fun currentRunnableText(): String {
        val editor = codeField.editor
        val selection = editor?.selectionModel?.selectedText?.takeIf { it.isNotBlank() }
        if (selection != null) return selection
        if (workFileMode.statementAwareRun) {
            val caretOffset = editor?.caretModel?.offset ?: codeDocument.textLength
            val currentStatement = resolveSqlStatementAtCaret(codeDocument.text, caretOffset)?.text.orEmpty()
            if (currentStatement.isNotBlank()) return currentStatement
        }
        return codeField.text
    }

    fun applyWorkSurfaceRequest(request: LivyWorkSurfaceRequest) {
        request.origin?.let { LivySourceOrigins.attach(file, it) }
        when (request.contentMode) {
            LivyWorkSurfaceContentMode.NONE -> {}
            LivyWorkSurfaceContentMode.REPLACE -> replaceEditorText(request.snippet)
            LivyWorkSurfaceContentMode.INSERT -> appendSnippetBlock(request.snippet)
        }
        updateContextLabel()
        if (request.autorun && request.snippet.isNotBlank()) {
            executeCode(explicitCode = request.snippet)
        }
    }

    private fun initialConsoleText(): String {
        val fileText = readFileText(file)
        val settings = LivyPluginSettings.getInstance().pluginState
        return resolveInitialConsoleText(
            fileText = fileText,
            savedDraft = settings.draftTextForProfile(
                executionTarget.profileId,
                executionTarget.settingsSnapshot.kind
            ),
            localHistoryEnabled = settings.localHistoryEnabled
        )
    }

    private fun buildInfoText(): String {
        return if (workFileMode.statementAwareRun) {
            "Run Current: selection -> SQL statement -> file."
        } else {
            "Run Current: selection -> file."
        }
    }

    private fun buildHeaderTooltip(): String = buildString {
        append("<html>")
        append("This work file stays bound to the captured profile snapshot.<br>")
        append("Source-aware actions can route selection, line, or file into this surface.<br>")
        if (LivyPluginSettings.getInstance().pluginState.localHistoryEnabled) {
            append("Local history restores the last draft for this profile and execution kind.")
        } else {
            append("Local history is disabled in settings.")
        }
        append("</html>")
    }

    private fun rememberCurrentDraft() {
        LivyPluginSettings.getInstance().pluginState.rememberConsoleDraft(
            profileId = executionTarget.profileId,
            languageOrKind = executionTarget.settingsSnapshot.kind,
            text = codeDocument.text
        )
        FileDocumentManager.getInstance().getFile(codeDocument)?.let { _ ->
            FileDocumentManager.getInstance().saveDocument(codeDocument)
        }
    }

    private fun replaceEditorText(text: String) {
        WriteAction.run<RuntimeException> {
            codeDocument.setText(text)
        }
        codeField.editor?.caretModel?.moveToOffset(codeDocument.textLength)
    }

    private fun insertSnippet(snippet: String) {
        val editor = codeField.editor
        val offset = editor?.caretModel?.offset?.coerceIn(0, codeDocument.textLength) ?: codeDocument.textLength
        WriteAction.run<RuntimeException> {
            codeDocument.insertString(offset, snippet)
        }
        editor?.caretModel?.moveToOffset((offset + snippet.length).coerceAtMost(codeDocument.textLength))
    }

    private fun appendSnippetBlock(snippet: String) {
        if (snippet.isBlank()) return
        val prefix = when {
            codeDocument.text.isBlank() -> ""
            codeDocument.text.endsWith("\n\n") -> ""
            codeDocument.text.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        replaceEditorText(codeDocument.text + prefix + snippet)
    }

    private fun resolveCodeDocument() =
        FileDocumentManager.getInstance().getDocument(file)?.also { document ->
            val initialText = initialConsoleText()
            if (document.text.isBlank() && initialText.isNotBlank()) {
                WriteAction.run<RuntimeException> {
                    document.setText(initialText)
                }
            }
        } ?: EditorFactory.getInstance().createDocument(initialConsoleText())

    private fun currentSourceOrigin(): LivySourceOrigin? = LivySourceOrigins.resolve(file)

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
            val rowCells = if (row.size >= headerCells.size) {
                row.take(headerCells.size)
            } else {
                row + List(headerCells.size - row.size) { "" }
            }
            model.addRow(rowCells.toTypedArray())
        }

        return JTable(model).apply { font = Font("Monospaced", Font.PLAIN, 12) }
    }

    private fun parseLineAsCells(line: String): List<String> {
        val rawParts = line.trim().split('|')
        return rawParts.drop(1).dropLast(1).map { it.trim() }
    }

    private fun readFileText(vf: VirtualFile): String =
        FileDocumentManager.getInstance().getDocument(vf)?.text
            ?: try { String(vf.contentsToByteArray(), Charsets.UTF_8) } catch (_: Exception) { "" }

    private fun setRunning(running: Boolean) {
        isRunning = running
        progressBar.isVisible = running
        ActivityTracker.getInstance().inc()
    }

    private fun updateContextLabel() {
        val sessionText = lastUsedSession?.let { "Last session: #${it.sessionId}" } ?: "No session yet"
        val statementText = currentStatement?.let { "Running statement: #${it.statementId}" } ?: "Idle"
        val sourceText = currentSourceOrigin()?.let { "Source: ${it.presentableLabel()}" } ?: "Source: manual work file"
        val highlighting = if (editorFileType == FileTypes.PLAIN_TEXT) {
            "Plain Text"
        } else {
            editorFileType.displayName
        }
        contextLabel.text = "Profile: ${executionTarget.profileName} | ${workFileMode.displayName} | ${executionTarget.baseUrl}"
        modeLabel.text = "$sourceText | $sessionText | $statementText | Editor: $highlighting"
    }

    private fun addResultTab(sessionId: Int, statement: Statement, title: String, sourceOrigin: LivySourceOrigin?) {
        val outputPanel = createOutputPanel(sessionId, statement, sourceOrigin)
        resultsTabbedPane.addTab(title, outputPanel)
        resultsTabbedPane.selectedIndex = resultsTabbedPane.tabCount - 1
    }

    fun disposePanel() {
        if (disposed) return
        disposed = true
        Disposer.dispose(panelDisposable)
        rememberCurrentDraft()
        currentIndicator?.cancel()
        currentIndicator = null
        currentStatement = null
        isRunning = false
        progressBar.isVisible = false
    }

    private fun checkCanceled(indicator: ProgressIndicator) {
        if (disposed || indicator.isCanceled) {
            throw ProcessCanceledException()
        }
    }

    private data class ExecutionResult(
        val sessionId: Int,
        val statement: Statement,
        val sourceOrigin: LivySourceOrigin?
    )

    companion object {
        private const val POLL_INTERVAL_MS = 1_000L
        private const val STATEMENT_WAIT_TIMEOUT_MS = 10 * 60 * 1000L
    }
}
