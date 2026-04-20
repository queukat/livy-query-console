package com.queukat.livy_new

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter

class SessionLogsDialog(
    private val client: LivyClient,
    private val sessionId: Int,
    private val project: Project,
    private val serverUrl: String? = null,
    private val profileName: String? = null
) : DialogWrapper(project, true, IdeModalityType.MODELESS) {

    private val textArea = JTextArea().apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
    }

    private var fullLogsText: String = ""
    private val statsLabel = JLabel("No logs loaded yet.")

    private val searchField = JTextField(20)
    private val findNextButton = JButton("Find Next")
    private val findPrevButton = JButton("Find Prev")

    init {
        title = serverUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { "Logs for session #$sessionId" }
            ?: "Logs for session #$sessionId"
        setResizable(true)
        init()
        loadLogsAsync()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(textArea).apply {
            preferredSize = Dimension(900, 500)
            putClientProperty("JScrollBar.fastWheelScrolling", true)
        }

        val searchPanel = JPanel(BorderLayout()).apply {
            val buttonsPanel = JPanel().apply {
                add(findNextButton)
                add(findPrevButton)
            }
            add(JLabel("Find: "), BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
            add(buttonsPanel, BorderLayout.EAST)
        }

        findNextButton.addActionListener { findNext() }
        findPrevButton.addActionListener { findPrev() }

        return panel {
            profileName?.takeIf { it.isNotBlank() }?.let { name ->
                row { label("Profile: $name") }
            }
            serverUrl?.takeIf { it.isNotBlank() }?.let { url ->
                row { label("Server: $url") }
            }
            row { cell(statsLabel).align(Align.FILL) }
            row { cell(searchPanel).align(Align.FILL) }
            row {
                scrollCell(scrollPane).resizableColumn().align(Align.FILL)
            }.resizableRow()
        }
    }

    override fun createActions(): Array<Action> {
        return arrayOf(
            object : DialogWrapperAction("Refresh") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    loadLogsAsync()
                }
            },
            object : DialogWrapperAction("Copy All") {
                override fun doAction(e: java.awt.event.ActionEvent?) {
                    CopyPasteManager.getInstance().setContents(StringSelection(fullLogsText))
                }
            },
            okAction
        )
    }

    private fun loadLogsAsync() {
        val authProfile = resolveProfileForAuthPrompt()
        LivyBackground.run(
            project = project,
            title = "Loading Livy logs",
            action = { _ -> client.getSessionLogs(sessionId, 0, 5000) },
            onSuccessUi = { logs ->
                fullLogsText = logs.joinToString("\n")
                textArea.text = fullLogsText
                textArea.caretPosition = 0
                 statsLabel.text = "Loaded ${logs.size} log line(s) for session #$sessionId."
                clearHighlights()
            },
            onErrorUi = { e ->
                if (
                    authProfile != null &&
                        maybePromptForBrowserAuthentication(
                            failure = e,
                            profile = authProfile,
                            project = project
                        ) {
                            loadLogsAsync()
                        }
                ) {
                    return@run
                }
                textArea.text = "Failed to load logs: ${e.message}"
                fullLogsText = textArea.text
                statsLabel.text = "Failed to load logs for session #$sessionId."
            }
        )
    }

    private fun resolveProfileForAuthPrompt(): LivyPluginSettings.ConnectionProfileState? {
        val normalizedServerUrl = serverUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(LivyManagedSessions::normalizeServerUrl)
            ?: return null
        return LivyPluginSettings.getInstance()
            .pluginState
            .profiles
            .firstOrNull { LivyManagedSessions.normalizeServerUrl(it.livyServerUrl) == normalizedServerUrl }
            ?.snapshot()
    }

    private fun findNext() {
        val query = searchField.text
        if (query.isBlank()) return

        clearHighlights()
        val currentPos = textArea.caretPosition

        val idx = fullLogsText.indexOf(query, currentPos, ignoreCase = true)
        if (idx >= 0) {
            highlightMatch(idx, query.length)
        } else {
            val wrapIdx = fullLogsText.indexOf(query, 0, ignoreCase = true)
            if (wrapIdx >= 0) highlightMatch(wrapIdx, query.length)
            else JOptionPane.showMessageDialog(contentPanel, "Not found", "Search", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    private fun findPrev() {
        val query = searchField.text
        if (query.isBlank()) return

        clearHighlights()
        val currentPos = textArea.caretPosition
        val subText = if (currentPos > 0) fullLogsText.substring(0, currentPos) else fullLogsText

        val idx = subText.lastIndexOf(query, ignoreCase = true)
        if (idx >= 0) {
            highlightMatch(idx, query.length)
        } else {
            val wrapIdx = fullLogsText.lastIndexOf(query, ignoreCase = true)
            if (wrapIdx >= 0) highlightMatch(wrapIdx, query.length)
            else JOptionPane.showMessageDialog(contentPanel, "Not found", "Search", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    private fun highlightMatch(start: Int, length: Int) {
        val end = start + length
        val painter = DefaultHighlighter.DefaultHighlightPainter(JBColor.YELLOW)
        try {
            textArea.highlighter.addHighlight(start, end, painter)
        } catch (_: BadLocationException) { /* ignore */ }
        textArea.caretPosition = start
    }

    private fun clearHighlights() {
        textArea.highlighter.removeAllHighlights()
    }
}
