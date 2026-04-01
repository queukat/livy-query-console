package com.queukat.livy_new

import com.google.gson.GsonBuilder
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea

class LivyStatementDetailsDialog(
    private val project: Project,
    private val sessionId: Int,
    private val statement: Statement,
    private val executionTarget: LivyExecutionTarget? = null,
    private val sourceOrigin: LivySourceOrigin? = null
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    private val gsonPretty = GsonBuilder().setPrettyPrinting().create()

    init {
        title = "Statement #${statement.id ?: "?"} Details"
        setResizable(true)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val tabs = JTabbedPane().apply {
            preferredSize = Dimension(980, 620)
            addTab("Summary", scrollableTextArea(buildSummaryText()))
            addTab("Code", scrollableTextArea(statement.code.orEmpty().ifBlank { "(No code returned by Livy)" }))
            addTab("Raw Output", scrollableTextArea(gsonPretty.toJson(statement.output)))

            val plainText = statement.output?.data?.get("text/plain") as? String
            if (!plainText.isNullOrBlank()) {
                addTab("Plain Text", scrollableTextArea(plainText))
            }

            val traceback = statement.output?.traceback
            if (!traceback.isNullOrEmpty()) {
                addTab("Traceback", scrollableTextArea(traceback.joinToString("\n")))
            }
        }

        return panel {
            executionTarget?.profileName?.takeIf { it.isNotBlank() }?.let { name ->
                row { label("Profile: $name") }
            }
            executionTarget?.baseUrl?.takeIf { it.isNotBlank() }?.let { url ->
                row { label("Server: $url") }
            }
            sourceOrigin?.let { origin ->
                row { label("Source: ${origin.presentableLabel()}") }
            }
            row { label("Session: #$sessionId") }
            row { label("Statement: #${statement.id ?: "?"}") }
            row {
                cell(tabs).resizableColumn().align(Align.FILL)
            }.resizableRow()
        }
    }

    override fun createActions(): Array<Action> {
        val actions = mutableListOf<Action>()
        val code = statement.code.orEmpty()
        val rawOutput = gsonPretty.toJson(statement.output)

        if (code.isNotBlank()) {
            actions += object : DialogWrapperAction("Copy Code") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    CopyPasteManager.getInstance().setContents(StringSelection(code))
                }
            }
        }

        if (rawOutput.isNotBlank()) {
            actions += object : DialogWrapperAction("Copy Raw Output") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    CopyPasteManager.getInstance().setContents(StringSelection(rawOutput))
                }
            }
        }

        if (executionTarget != null && code.isNotBlank()) {
            actions += object : DialogWrapperAction("Open in Work File") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    openLivyConsole(project, executionTarget, code)
                }
            }
        }

        if (sourceOrigin != null) {
            actions += object : DialogWrapperAction("Open Source") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    sourceOrigin.navigate(project)
                }
            }
        }

        actions += okAction
        return actions.toTypedArray()
    }

    private fun buildSummaryText(): String = buildString {
        appendLine("State: ${statement.state.orEmpty().ifBlank { "unknown" }}")
        appendLine("Output Status: ${statement.output?.status.orEmpty().ifBlank { "n/a" }}")
        appendLine("Execution Count: ${statement.output?.execution_count?.toString().orEmpty().ifBlank { "n/a" }}")
        appendLine("Progress: ${statement.progress?.toString().orEmpty().ifBlank { "n/a" }}")
        appendLine("Started: ${formatEpochMillis(statement.started)}")
        appendLine("Completed: ${formatEpochMillis(statement.completed)}")
        appendLine("Error Name: ${statement.output?.ename.orEmpty().ifBlank { "n/a" }}")
        appendLine("Error Value: ${statement.output?.evalue.orEmpty().ifBlank { "n/a" }}")
    }

    private fun scrollableTextArea(text: String): JScrollPane =
        JScrollPane(
            JTextArea(text).apply {
                isEditable = false
                lineWrap = false
                wrapStyleWord = false
                caretPosition = 0
            }
        )

    private fun formatEpochMillis(value: Long?): String {
        if (value == null || value <= 0L) return "n/a"
        return Instant.ofEpochMilli(value)
            .atZone(ZoneId.systemDefault())
            .format(TIMESTAMP_FORMATTER)
    }

    companion object {
        private val TIMESTAMP_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
