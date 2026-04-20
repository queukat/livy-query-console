package com.queukat.livy_new.bottompanel

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.UIUtil
import com.queukat.livy_new.LivyBackground
import com.queukat.livy_new.LivyClientProvider
import com.queukat.livy_new.LivyExecutionTarget
import com.queukat.livy_new.LivyManagedSessions
import com.queukat.livy_new.LivyPluginSettings
import com.queukat.livy_new.Session
import com.queukat.livy_new.SessionLogsDialog
import com.queukat.livy_new.ShowStatementsDialog
import com.queukat.livy_new.maybePromptForBrowserAuthentication
import com.queukat.livy_new.normalizeSessionsAutoRefreshIntervalSeconds
import com.queukat.livy_new.profileChoices
import com.queukat.livy_new.profileLabel
import com.queukat.livy_new.setActiveProfile
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.ListSelectionModel
import javax.swing.SpinnerNumberModel
import javax.swing.Timer
import javax.swing.table.DefaultTableModel

class LivySessionsPanel(
    private val project: Project,
    private val autoRefresh: Boolean = true,
    private val sessionLoader: (LivyExecutionTarget) -> List<Session> = { target ->
        LivyClientProvider.getInstance().get(target).getAllSessions()
    }
) : JPanel(BorderLayout()) {

    private val tabbedPane = JBTabbedPane()
    private val sessionsPanel = JPanel(BorderLayout())
    private val profileComboModel = DefaultComboBoxModel<LivyPluginSettings.ConnectionProfileState>()
    private val profileCombo = JComboBox(profileComboModel)
    private val contextLabel = JBLabel("No sessions loaded yet. Click Refresh Sessions.").apply {
        foreground = UIUtil.getContextHelpForeground()
    }

    private var visibleColumnIds: List<String> = SessionColumns.normalize(
        LivyPluginSettings.getInstance().pluginState.sessionTableColumns
    )
    private var loadedTarget: LivyExecutionTarget? = null
    private var selectedProfileId: String? = null
    private var updatingProfileSelection: Boolean = false
    private var updatingAutoRefreshControls: Boolean = false
    private var refreshInProgress: Boolean = false

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
    private val autoRefreshCheckBox = JCheckBox("Auto-refresh")
    private val autoRefreshSpinner = JSpinner(
        SpinnerNumberModel(
            normalizedAutoRefreshIntervalFromState(),
            LivyPluginSettings.MIN_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS,
            LivyPluginSettings.MAX_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS,
            5
        )
    )
    private val terminateManagedButton = JButton("Terminate Managed Session…").apply {
        toolTipText = "Delete only plugin-managed sessions from the currently loaded Livy server."
    }
    private val autoRefreshTimer = Timer(autoRefreshDelayMs()) {
        refreshSessions(showModalErrors = false)
    }.apply {
        isRepeats = true
    }

    init {
        add(tabbedPane, BorderLayout.CENTER)

        profileCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).also { component ->
                if (component is JLabel && value is LivyPluginSettings.ConnectionProfileState) {
                    val settings = LivyPluginSettings.getInstance().pluginState
                    component.text = "${settings.profileLabel(value)} - ${value.livyServerUrl}"
                }
            }
        }
        profileCombo.addActionListener {
            if (!updatingProfileSelection) {
                val selectedProfile = selectedProfile()
                if (selectedProfile != null) {
                    selectedProfileId = selectedProfile.id
                    LivyPluginSettings.getInstance().pluginState.setActiveProfile(selectedProfile.id)
                    updateContextLabel(rowSessions.size)
                }
            }
        }
        reloadProfileChoices()
        configureAutoRefreshControls()

        val headerPanel = JPanel(BorderLayout()).apply {
            add(JPanel().apply {
                add(JLabel("Profile:"))
                add(profileCombo)
                add(autoRefreshCheckBox)
                add(JLabel("Every"))
                add(autoRefreshSpinner)
                add(JLabel("sec"))
            }, BorderLayout.NORTH)
            add(contextLabel, BorderLayout.SOUTH)
        }

        sessionsPanel.add(headerPanel, BorderLayout.NORTH)
        sessionsPanel.add(JBScrollPane(sessionsTable), BorderLayout.CENTER)

        val southPanel = JPanel().apply {
            add(refreshButton)
            add(chooseColumnsButton)
            add(showStatementsButton)
            add(viewLogsButton)
            add(terminateManagedButton)
        }
        sessionsPanel.add(southPanel, BorderLayout.SOUTH)

        tabbedPane.addTab("Sessions", sessionsPanel)

        refreshButton.addActionListener { refreshSessions() }
        chooseColumnsButton.addActionListener { chooseColumns() }
        showStatementsButton.addActionListener { showStatementsForSelectedSession() }
        viewLogsButton.addActionListener { showLogsForSelectedSession() }
        terminateManagedButton.addActionListener { terminateSelectedManagedSession() }
        autoRefreshCheckBox.addActionListener {
            if (!updatingAutoRefreshControls) {
                persistAutoRefreshControls()
                updateAutoRefreshTimer()
                updateContextLabel(rowSessions.size)
            }
        }
        autoRefreshSpinner.addChangeListener {
            if (!updatingAutoRefreshControls) {
                val normalized = normalizedSpinnerInterval()
                if ((autoRefreshSpinner.value as? Int) != normalized) {
                    updatingAutoRefreshControls = true
                    try {
                        autoRefreshSpinner.value = normalized
                    } finally {
                        updatingAutoRefreshControls = false
                    }
                }
                persistAutoRefreshControls()
                updateAutoRefreshTimer()
                updateContextLabel(rowSessions.size)
            }
        }
        sessionsTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateActionButtons()
            }
        }
        updateActionButtons()

        if (autoRefresh) {
            refreshSessions()
            updateAutoRefreshTimer()
        }
    }

    override fun addNotify() {
        super.addNotify()
        updateAutoRefreshTimer()
    }

    override fun removeNotify() {
        autoRefreshTimer.stop()
        super.removeNotify()
    }

    fun refreshSessions() {
        refreshSessions(showModalErrors = true)
    }

    private fun refreshSessions(showModalErrors: Boolean) {
        if (refreshInProgress) return
        reloadProfileChoices(selectedProfileId)
        val selectedProfile = selectedProfile() ?: run {
            contextLabel.text = "No connection profile is available."
            if (showModalErrors) {
                Messages.showErrorDialog(project, "No Livy connection profile is configured.", "Livy Error")
            }
            return
        }
        val target = LivyExecutionTarget.capture(LivyPluginSettings.getInstance().pluginState, selectedProfile.id)
        refreshInProgress = true
        contextLabel.text = "Loading sessions from ${target.profileName} (${target.baseUrl})..."
        updateActionButtons()

        LivyBackground.run(
            project = project,
            title = "Refreshing Livy sessions",
            action = { _ ->
                LoadedSessions(target = target, sessions = sessionLoader(target))
            },
            onSuccessUi = { loaded ->
                replaceSessions(loaded.sessions, loaded.target)
            },
            onErrorUi = { e ->
                refreshInProgress = false
                contextLabel.text = "Failed to load sessions from ${target.profileName} (${target.baseUrl})."
                updateActionButtons()
                if (
                    maybePromptForBrowserAuthentication(
                        failure = e,
                        profile = target.settingsSnapshot,
                        project = project
                    ) {
                        refreshSessions(showModalErrors = showModalErrors)
                    }
                ) {
                    return@run
                }
                if (showModalErrors) {
                    Messages.showErrorDialog(
                        project,
                        "Failed to refresh sessions: ${e.message}",
                        "Livy Error"
                    )
                }
            }
        )
    }

    internal fun replaceSessions(sessions: List<Session>, target: LivyExecutionTarget? = loadedTarget) {
        refreshInProgress = false
        loadedTarget = target
        rowSessions.clear()
        rowSessions.addAll(sessions)

        sessionsTableModel.rowCount = 0
        for (s in sessions) {
            sessionsTableModel.addRow(SessionColumns.valuesFor(s, visibleColumnIds))
        }
        updateContextLabel(sessions.size)
        updateActionButtons()
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
        updateContextLabel(rowSessions.size)
        updateActionButtons()
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
        val sessionId = session.id
        if (sessionId == null) {
            Messages.showInfoMessage(project, "Selected session has no id.", "Invalid Session")
            return
        }
        val target = loadedTarget ?: run {
            Messages.showInfoMessage(project, "Refresh sessions first to bind the table to a Livy server.", "No Server Context")
            return
        }
        ShowStatementsDialog(
            client = LivyClientProvider.getInstance().get(target),
            sessionId = sessionId,
            project = project,
            executionTarget = target
        ).show()
    }

    private fun showLogsForSelectedSession() {
        val session = selectedSession()
        if (session == null) {
            Messages.showInfoMessage(project, "Please select a session in the table first.", "No Session Selected")
            return
        }
        val sessionId = session.id
        if (sessionId == null) {
            Messages.showInfoMessage(project, "Selected session has no id.", "Invalid Session")
            return
        }
        val target = loadedTarget ?: run {
            Messages.showInfoMessage(project, "Refresh sessions first to bind the table to a Livy server.", "No Server Context")
            return
        }
        SessionLogsDialog(
            client = LivyClientProvider.getInstance().get(target),
            sessionId = sessionId,
            project = project,
            serverUrl = target.baseUrl,
            profileName = target.profileName
        ).show()
    }

    private fun terminateSelectedManagedSession() {
        val session = selectedSession() ?: run {
            Messages.showInfoMessage(project, "Please select a session in the table first.", "No Session Selected")
            return
        }
        val sessionId = session.id ?: run {
            Messages.showInfoMessage(project, "Selected session has no id.", "Invalid Session")
            return
        }
        val target = loadedTarget ?: run {
            Messages.showInfoMessage(project, "Refresh sessions first to bind the table to a Livy server.", "No Server Context")
            return
        }
        val settings = LivyPluginSettings.getInstance().pluginState
        if (!LivyManagedSessions.isManagedSession(settings, target.baseUrl, sessionId)) {
            Messages.showInfoMessage(
                project,
                "Only plugin-managed sessions can be terminated from this list.",
                "Terminate Managed Session"
            )
            return
        }

        val confirmed = Messages.showYesNoDialog(
            project,
            "Terminate plugin-managed session #$sessionId from ${target.profileName} (${target.baseUrl})?",
            "Terminate Managed Session",
            null
        )
        if (confirmed != Messages.YES) return

        LivyBackground.run(
            project = project,
            title = "Terminating Livy session",
            action = { _ ->
                LivyClientProvider.getInstance().get(target).deleteSession(sessionId)
            },
            onSuccessUi = {
                LivyManagedSessions.forget(settings, sessionId, target.baseUrl)
                replaceSessions(rowSessions.filter { it.id != sessionId }, target)
            },
            onErrorUi = { error ->
                if (
                    maybePromptForBrowserAuthentication(
                        failure = error,
                        profile = target.settingsSnapshot,
                        project = project
                    ) {
                        terminateSelectedManagedSession()
                    }
                ) {
                    return@run
                }
                Messages.showErrorDialog(
                    project,
                    "Failed to terminate session #$sessionId: ${error.message}",
                    "Livy Error"
                )
            }
        )
    }

    private fun createModelForColumns(columnIds: List<String>): DefaultTableModel {
        val headers = SessionColumns.titlesFor(columnIds)
        return object : DefaultTableModel(headers, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
    }

    private fun reloadProfileChoices(preferredProfileId: String? = selectedProfileId) {
        val settings = LivyPluginSettings.getInstance().pluginState
        val profiles = settings.profileChoices()
        updatingProfileSelection = true
        try {
            profileComboModel.removeAllElements()
            profiles.forEach(profileComboModel::addElement)
            val targetId = preferredProfileId
                ?.takeIf { id -> profiles.any { it.id == id } }
                ?: settings.activeProfileId
                    .takeIf { id -> profiles.any { it.id == id } }
                ?: profiles.firstOrNull()?.id
            selectedProfileId = targetId
            val selectedProfile = profiles.firstOrNull { it.id == targetId }
            profileCombo.selectedItem = selectedProfile
        } finally {
            updatingProfileSelection = false
        }
    }

    private fun selectedProfile(): LivyPluginSettings.ConnectionProfileState? =
        profileCombo.selectedItem as? LivyPluginSettings.ConnectionProfileState

    private fun updateActionButtons() {
        val session = selectedSession()
        val target = loadedTarget
        val sessionId = session?.id
        val hasBoundSession = sessionId != null && target != null
        refreshButton.isEnabled = !refreshInProgress
        showStatementsButton.isEnabled = !refreshInProgress && hasBoundSession
        viewLogsButton.isEnabled = !refreshInProgress && hasBoundSession
        terminateManagedButton.isEnabled =
            !refreshInProgress &&
                sessionId != null &&
                target != null &&
                LivyManagedSessions.isManagedSession(
                    LivyPluginSettings.getInstance().pluginState,
                    target.baseUrl,
                    sessionId
                )
    }

    private fun updateContextLabel(sessionCount: Int) {
        val selectedProfile = selectedProfile()
        val loaded = loadedTarget
        val autoRefreshText = autoRefreshSummary()
        contextLabel.text = when {
            loaded == null && selectedProfile == null -> "No connection profile is available. $autoRefreshText"
            loaded == null -> "Selected profile for next refresh: ${selectedProfile?.displayName}. No sessions loaded yet. $autoRefreshText"
            sessionCount == 0 && selectedProfile?.id == loaded.profileId ->
                "Showing ${loaded.profileName} (${loaded.baseUrl}). No sessions returned. $autoRefreshText"
            sessionCount == 0 ->
                "Loaded ${loaded.profileName} (${loaded.baseUrl}). Selected profile for next refresh: ${selectedProfile?.displayName}. $autoRefreshText"
            selectedProfile?.id == loaded.profileId ->
                "Showing $sessionCount session(s) from ${loaded.profileName} (${loaded.baseUrl}). $autoRefreshText"
            else ->
                "Showing $sessionCount session(s) from ${loaded.profileName} (${loaded.baseUrl}). Selected profile for next refresh: ${selectedProfile?.displayName}. $autoRefreshText"
        }
    }

    private fun configureAutoRefreshControls() {
        val state = LivyPluginSettings.getInstance().pluginState
        updatingAutoRefreshControls = true
        try {
            autoRefreshCheckBox.isEnabled = autoRefresh
            autoRefreshCheckBox.isSelected = autoRefresh && state.sessionsAutoRefreshEnabled
            autoRefreshSpinner.value = normalizeSessionsAutoRefreshIntervalSeconds(state.sessionsAutoRefreshIntervalSeconds)
            autoRefreshSpinner.isEnabled = autoRefresh && autoRefreshCheckBox.isSelected
            autoRefreshSpinner.toolTipText = "Refresh interval in seconds (${LivyPluginSettings.MIN_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS}-${LivyPluginSettings.MAX_SESSIONS_AUTO_REFRESH_INTERVAL_SECONDS})."
        } finally {
            updatingAutoRefreshControls = false
        }
    }

    private fun persistAutoRefreshControls() {
        val state = LivyPluginSettings.getInstance().pluginState
        val interval = normalizedSpinnerInterval()
        state.sessionsAutoRefreshEnabled = autoRefresh && autoRefreshCheckBox.isSelected
        state.sessionsAutoRefreshIntervalSeconds = interval
        autoRefreshSpinner.isEnabled = autoRefresh && autoRefreshCheckBox.isSelected
    }

    private fun updateAutoRefreshTimer() {
        autoRefreshTimer.stop()
        autoRefreshTimer.delay = autoRefreshDelayMs()
        autoRefreshTimer.initialDelay = autoRefreshDelayMs()
        if (autoRefresh && autoRefreshCheckBox.isSelected) {
            autoRefreshTimer.start()
        }
    }

    private fun normalizedAutoRefreshIntervalFromState(): Int =
        normalizeSessionsAutoRefreshIntervalSeconds(
            LivyPluginSettings.getInstance().pluginState.sessionsAutoRefreshIntervalSeconds
        )

    private fun normalizedSpinnerInterval(): Int =
        normalizeSessionsAutoRefreshIntervalSeconds((autoRefreshSpinner.value as? Int) ?: normalizedAutoRefreshIntervalFromState())

    private fun autoRefreshDelayMs(): Int = normalizedSpinnerInterval() * 1000

    private fun autoRefreshSummary(): String =
        if (autoRefresh && autoRefreshCheckBox.isSelected) {
            "Auto-refresh every ${normalizedSpinnerInterval()}s."
        } else {
            "Auto-refresh off."
        }

    private data class LoadedSessions(
        val target: LivyExecutionTarget,
        val sessions: List<Session>
    )
}
