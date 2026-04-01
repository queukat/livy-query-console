package com.queukat.livy_new

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

class LivyPluginConfigurable : Configurable {

    private var mainPanel: DialogPanel? = null
    private var workingState: LivyPluginSettings.PluginState = LivyPluginSettings.PluginState()
    private var selectedProfileId: String? = null
    private var updatingProfileSelection: Boolean = false

    private val profileComboModel = DefaultComboBoxModel<LivyPluginSettings.ConnectionProfileState>()
    private lateinit var profileCombo: JComboBox<LivyPluginSettings.ConnectionProfileState>
    private lateinit var profileStatusLabel: JLabel
    private lateinit var localHistoryEnabledCheckbox: JCheckBox
    private lateinit var maxHistoryItemsSpinner: JSpinner

    private lateinit var displayNameField: JTextField
    private lateinit var livyServerUrlField: JTextField
    private lateinit var kindField: JTextField
    private lateinit var proxyUserField: JTextField
    private lateinit var jarsField: JTextField
    private lateinit var pyFilesField: JTextField
    private lateinit var filesField: JTextField
    private lateinit var driverMemoryField: JTextField
    private lateinit var driverCoresSpinner: JSpinner
    private lateinit var executorMemoryField: JTextField
    private lateinit var executorCoresSpinner: JSpinner
    private lateinit var numExecutorsSpinner: JSpinner
    private lateinit var archivesField: JTextField
    private lateinit var queueField: JTextField
    private lateinit var nameField: JTextField
    private lateinit var confField: JTextField
    private lateinit var heartbeatTimeoutSpinner: JSpinner
    private lateinit var ttlField: JTextField
    private lateinit var maxSessionsSpinner: JSpinner
    private lateinit var sessionManagementCombo: JComboBox<String>
    private lateinit var killOldestCheckbox: JCheckBox

    override fun createComponent(): JComponent {
        loadWorkingState()

        mainPanel = panel {
            row { label("Livy Plugin Settings").bold() }
            row {
                label("Current scope: multiple named connection profiles with direct HTTP(S) connectivity only.")
            }
            row {
                label("Not supported yet: auth headers/tokens/cookies, secure credential storage, Kerberos, or OAuth.")
            }
            row {
                label("Existing Livy work files and loaded session views keep the profile snapshot captured when they were opened.")
            }

            group("Local History & Drafts") {
                row {
                    label("Recent snippets and the last draft per profile are stored locally in plain text when enabled.")
                }
                row {
                    label("No auth data or remote session state is stored by this feature.")
                }
                row {
                    checkBox("Enable local history and last-draft restore").applyToComponent {
                        localHistoryEnabledCheckbox = this
                    }
                }
                row("Max saved snippets:") {
                    cell(JSpinner(SpinnerNumberModel(LivyPluginSettings.DEFAULT_MAX_HISTORY_ITEMS, 1, 500, 1)).also {
                        maxHistoryItemsSpinner = it
                    })
                }
                row {
                    button("Clear Saved History") { clearLocalHistoryData() }
                    label("Applies when you click Apply or OK.")
                }
            }

            group("Connection Profiles") {
                row("Profile:") {
                    cell(JComboBox(profileComboModel).also { combo ->
                        profileCombo = combo
                        combo.renderer = object : DefaultListCellRenderer() {
                            override fun getListCellRendererComponent(
                                list: JList<*>?,
                                value: Any?,
                                index: Int,
                                isSelected: Boolean,
                                cellHasFocus: Boolean
                            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).also { component ->
                                if (component is JLabel && value is LivyPluginSettings.ConnectionProfileState) {
                                    component.text = "${workingState.profileLabel(value)} - ${value.livyServerUrl}"
                                }
                            }
                        }
                        combo.addActionListener {
                            if (!updatingProfileSelection) {
                                val nextId = (combo.selectedItem as? LivyPluginSettings.ConnectionProfileState)?.id
                                switchSelectedProfile(nextId)
                            }
                        }
                    }).align(AlignX.FILL)
                    button("Add") { addProfile() }
                    button("Delete") { deleteSelectedProfile() }
                    button("Use For New Work Files") { markActiveProfile() }
                    button("Set Default") { markDefaultProfile() }
                }
                row {
                    label("").applyToComponent { profileStatusLabel = this }
                }
            }

            group("Selected Profile") {
                row("Display Name:") {
                    textField().applyToComponent { displayNameField = this }.align(AlignX.FILL)
                }
                row("Server URL:") {
                    textField().applyToComponent { livyServerUrlField = this }
                        .align(AlignX.FILL)
                        .comment("e.g. http://localhost:8998")
                }
                row("Session Kind:") {
                    textField().applyToComponent { kindField = this }
                        .comment("spark, pyspark, sparkr, sql ...")
                }
                row("Proxy User:") {
                    textField().applyToComponent { proxyUserField = this }.align(AlignX.FILL)
                }
            }

            group("Resources") {
                row("Jars:") {
                    textField().applyToComponent { jarsField = this }
                        .align(AlignX.FILL)
                        .comment("Comma-separated JARs")
                }
                row("Python Files:") {
                    textField().applyToComponent { pyFilesField = this }
                        .align(AlignX.FILL)
                        .comment("Comma-separated py files")
                }
                row("Files:") {
                    textField().applyToComponent { filesField = this }
                        .align(AlignX.FILL)
                        .comment("Comma-separated files")
                }
                row("Driver Memory:") {
                    textField().applyToComponent { driverMemoryField = this }
                        .comment("e.g. 1g")
                }
                row("Driver Cores:") {
                    cell(JSpinner(SpinnerNumberModel(1, 1, 64, 1)).also { driverCoresSpinner = it })
                }
                row("Executor Memory:") {
                    textField().applyToComponent { executorMemoryField = this }
                        .comment("e.g. 2g")
                }
                row("Executor Cores:") {
                    cell(JSpinner(SpinnerNumberModel(1, 1, 64, 1)).also { executorCoresSpinner = it })
                }
                row("Num Executors:") {
                    cell(JSpinner(SpinnerNumberModel(2, 1, 128, 1)).also { numExecutorsSpinner = it })
                }
                row("Archives:") {
                    textField().applyToComponent { archivesField = this }
                        .align(AlignX.FILL)
                        .comment("Comma-separated archives")
                }
                row("Queue:") {
                    textField().applyToComponent { queueField = this }
                }
                row("Name:") {
                    textField().applyToComponent { nameField = this }
                }
                row("Conf:") {
                    textField().applyToComponent { confField = this }
                        .align(AlignX.FILL)
                        .comment("Comma-separated: key=value,key2=value2 (split by first '=')")
                }
            }

            group("Timeouts & Limits") {
                row("Heartbeat Timeout (sec):") {
                    cell(JSpinner(SpinnerNumberModel(60, 1, 6000, 1)).also { heartbeatTimeoutSpinner = it })
                }
                row("TTL:") {
                    textField().applyToComponent { ttlField = this }
                        .comment("e.g. 10m")
                }
                row("Max Sessions:") {
                    cell(JSpinner(SpinnerNumberModel(4, 1, 50, 1)).also { maxSessionsSpinner = it })
                }
            }

            group("Session Strategy") {
                row {
                    label("Only plugin-managed sessions with matching server and configuration are reused or auto-deleted.")
                }
                row("Strategy:") {
                    comboBox(listOf("reuse", "always_create")).applyToComponent { sessionManagementCombo = this }
                        .comment("Reuse compatible managed sessions or always create (up to managed-session limit).")
                }
                row {
                    checkBox("Kill oldest idle managed session if limit reached").applyToComponent {
                        killOldestCheckbox = this
                    }
                }
            }

            row {
                button("Test Connection") { testSelectedProfile(verbose = false) }
                button("Test Connection (Verbose)") { testSelectedProfile(verbose = true) }
                button("Start Test Session") { startTestSessionForSelectedProfile() }
            }
        }

        refreshProfileCombo()
        populateProfileFields(selectedProfile())
        updateProfileStatus()

        return mainPanel!!
    }

    override fun isModified(): Boolean =
        editableSettingsFingerprint(buildCurrentStateFromUi()) != editableSettingsFingerprint(LivyPluginSettings.getInstance().pluginState)

    @Throws(ConfigurationException::class)
    override fun apply() {
        val updatedState = buildCurrentStateFromUi()
        validateProfiles(updatedState)
        LivyPluginSettings.getInstance().loadState(updatedState)
        loadWorkingState()
        refreshProfileCombo()
        populateProfileFields(selectedProfile())
        updateProfileStatus()
    }

    override fun reset() {
        loadWorkingState()
        refreshProfileCombo()
        populateProfileFields(selectedProfile())
        updateProfileStatus()
        mainPanel?.reset()
    }

    override fun disposeUIResources() {
        mainPanel = null
    }

    override fun getDisplayName(): String = "Livy"

    private fun loadWorkingState() {
        workingState = LivyPluginSettings.getInstance().pluginState.snapshot()
        selectedProfileId = workingState.activeProfileId.ifBlank {
            workingState.defaultProfileId.ifBlank {
                workingState.profiles.firstOrNull()?.id.orEmpty()
            }
        }
    }

    private fun addProfile() {
        commitSelectedProfileUi()
        val template = selectedProfile()?.snapshot() ?: createProfile(displayName = workingState.nextProfileDisplayName())
        val newProfile = template.snapshot().apply {
            id = UUID.randomUUID().toString()
            displayName = workingState.nextProfileDisplayName()
        }
        workingState.profiles.add(newProfile)
        selectedProfileId = newProfile.id
        refreshProfileCombo()
        populateProfileFields(newProfile)
        updateProfileStatus()
    }

    private fun deleteSelectedProfile() {
        val profile = selectedProfile() ?: return
        if (workingState.profiles.size <= 1) {
            Messages.showInfoMessage("At least one Livy connection profile must remain.", "Livy")
            return
        }
        val delete = Messages.showYesNoDialog(
            "Delete connection profile \"${profile.displayName}\"?",
            "Delete Livy Profile",
            null
        )
        if (delete != Messages.YES) return

        workingState.removeProfile(profile.id)
        selectedProfileId = workingState.activeProfileId.ifBlank {
            workingState.defaultProfileId.ifBlank {
                workingState.profiles.firstOrNull()?.id.orEmpty()
            }
        }
        refreshProfileCombo()
        populateProfileFields(selectedProfile())
        updateProfileStatus()
    }

    private fun markActiveProfile() {
        commitSelectedProfileUi()
        val profile = selectedProfile() ?: return
        workingState.setActiveProfile(profile.id)
        refreshProfileCombo()
        updateProfileStatus()
    }

    private fun markDefaultProfile() {
        commitSelectedProfileUi()
        val profile = selectedProfile() ?: return
        workingState.setDefaultProfile(profile.id)
        refreshProfileCombo()
        updateProfileStatus()
    }

    private fun switchSelectedProfile(nextProfileId: String?) {
        commitSelectedProfileUi()
        selectedProfileId = nextProfileId
        populateProfileFields(selectedProfile())
        updateProfileStatus()
    }

    private fun refreshProfileCombo() {
        val selectedIdBeforeRefresh = selectedProfileId
        val profiles = workingState.profileChoices()
        updatingProfileSelection = true
        try {
            profileComboModel.removeAllElements()
            profiles.forEach(profileComboModel::addElement)
            val targetId = selectedIdBeforeRefresh
                ?.takeIf { id -> profiles.any { it.id == id } }
                ?: workingState.activeProfileId
                    .takeIf { id -> profiles.any { it.id == id } }
                ?: profiles.firstOrNull()?.id
            selectedProfileId = targetId
            profileCombo.selectedItem = profiles.firstOrNull { it.id == targetId }
        } finally {
            updatingProfileSelection = false
        }
    }

    private fun selectedProfile(): LivyPluginSettings.ConnectionProfileState? =
        workingState.findProfile(selectedProfileId)

    private fun commitSelectedProfileUi() {
        commitGlobalUi()
        val profile = selectedProfile() ?: return
        profile.displayName = displayNameField.text.trim().ifBlank { workingState.nextProfileDisplayName() }
        profile.livyServerUrl = LivyManagedSessions.normalizeServerUrl(
            livyServerUrlField.text.trim().ifBlank { LivyPluginSettings.DEFAULT_SERVER_URL }
        )
        profile.kind = kindField.text
        profile.proxyUser = proxyUserField.text
        profile.jars = jarsField.text
        profile.pyFiles = pyFilesField.text
        profile.files = filesField.text
        profile.driverMemory = driverMemoryField.text
        profile.driverCores = spinnerValue(driverCoresSpinner)
        profile.executorMemory = executorMemoryField.text
        profile.executorCores = spinnerValue(executorCoresSpinner)
        profile.numExecutors = spinnerValue(numExecutorsSpinner)
        profile.archives = archivesField.text
        profile.queue = queueField.text
        profile.name = nameField.text
        profile.conf = confField.text
        profile.heartbeatTimeoutInSecond = spinnerValue(heartbeatTimeoutSpinner)
        profile.ttl = ttlField.text
        profile.maxSessions = spinnerValue(maxSessionsSpinner)
        profile.sessionManagementStrategy = sessionManagementCombo.selectedItem as? String ?: "reuse"
        profile.killOldestIfFull = killOldestCheckbox.isSelected
    }

    private fun commitGlobalUi() {
        if (!::localHistoryEnabledCheckbox.isInitialized || !::maxHistoryItemsSpinner.isInitialized) return
        workingState.localHistoryEnabled = localHistoryEnabledCheckbox.isSelected
        workingState.maxHistoryItems = spinnerValue(maxHistoryItemsSpinner)
    }

    private fun populateProfileFields(profile: LivyPluginSettings.ConnectionProfileState?) {
        val selected = profile ?: createProfile(displayName = "Profile")
        localHistoryEnabledCheckbox.isSelected = workingState.localHistoryEnabled
        maxHistoryItemsSpinner.value = workingState.maxHistoryItems
        displayNameField.text = selected.displayName
        livyServerUrlField.text = selected.livyServerUrl
        kindField.text = selected.kind
        proxyUserField.text = selected.proxyUser
        jarsField.text = selected.jars
        pyFilesField.text = selected.pyFiles
        filesField.text = selected.files
        driverMemoryField.text = selected.driverMemory
        driverCoresSpinner.value = selected.driverCores
        executorMemoryField.text = selected.executorMemory
        executorCoresSpinner.value = selected.executorCores
        numExecutorsSpinner.value = selected.numExecutors
        archivesField.text = selected.archives
        queueField.text = selected.queue
        nameField.text = selected.name
        confField.text = selected.conf
        heartbeatTimeoutSpinner.value = selected.heartbeatTimeoutInSecond
        ttlField.text = selected.ttl
        maxSessionsSpinner.value = selected.maxSessions
        sessionManagementCombo.selectedItem = selected.sessionManagementStrategy
        killOldestCheckbox.isSelected = selected.killOldestIfFull
    }

    private fun updateProfileStatus() {
        val profile = selectedProfile()
        profileStatusLabel.text = when {
            profile == null -> "No profile selected."
            profile.id == workingState.activeProfileId && profile.id == workingState.defaultProfileId ->
                "Selected profile is both Active (preselected for new work-file/sessions flows) and Default fallback."
            profile.id == workingState.activeProfileId ->
                "Selected profile is Active for new work-file/sessions flows."
            profile.id == workingState.defaultProfileId ->
                "Selected profile is the Default fallback."
            else ->
                "Active profile: ${workingState.findProfile(workingState.activeProfileId)?.displayName}. Default profile: ${workingState.findProfile(workingState.defaultProfileId)?.displayName}."
        }
    }

    private fun testSelectedProfile(verbose: Boolean) {
        val profile = buildCurrentProfileFromUi()
        val url = LivyManagedSessions.normalizeServerUrl(profile.livyServerUrl)
        LivyBackground.run(
            project = null,
            title = if (verbose) "Testing Livy connection (verbose)" else "Testing Livy connection",
            action = { _ -> LivyClientProvider.getInstance().get(url).testConnectionDetailed() },
            onSuccessUi = { diagnostics ->
                if (verbose) {
                    ResultDialog(diagnostics.toDiagnosticsText()).show()
                } else if (diagnostics.success) {
                    Messages.showInfoMessage(diagnostics.summary(), "Livy")
                } else {
                    Messages.showErrorDialog(
                        mainPanel,
                        diagnostics.summary() + "\nSupported scope: direct connectivity only; advanced auth flows are not implemented yet.",
                        "Livy Error"
                    )
                }
            },
            onErrorUi = { ex ->
                val text = if (ex is ProcessCanceledException) {
                    "Connection test was canceled."
                } else {
                    "Connection test failed: ${ex.message}"
                }
                Messages.showErrorDialog(mainPanel, text, "Livy Error")
            }
        )
    }

    private fun startTestSessionForSelectedProfile() {
        val profile = buildCurrentProfileFromUi()
        val url = LivyManagedSessions.normalizeServerUrl(profile.livyServerUrl)

        LivyBackground.run(
            project = null,
            title = "Creating Livy test session",
            action = { _ ->
                val spec = LivySessionSpecFactory.fromSettings(profile, url)
                val session = LivyClientProvider.getInstance().get(url).createSession(spec.config)
                val sessionId = session.id ?: throw RuntimeException("Livy returned a session without id.")
                LivyManagedSessions.remember(
                    state = LivyPluginSettings.getInstance().pluginState,
                    sessionId = sessionId,
                    serverUrl = url,
                    fingerprint = spec.fingerprint
                )
                sessionId
            },
            onSuccessUi = { sessionId ->
                Messages.showInfoMessage(
                    "Managed test session #$sessionId created for profile \"${profile.displayName}\".\n" +
                        "It can be reused only when the server and configuration match.",
                    "Livy"
                )
            },
            onErrorUi = { ex ->
                Messages.showErrorDialog("Failed to create session: ${ex.message}", "Livy Error")
            }
        )
    }

    private fun buildCurrentProfileFromUi(): LivyPluginSettings.ConnectionProfileState {
        commitSelectedProfileUi()
        return selectedProfile()?.snapshot() ?: createProfile(displayName = workingState.nextProfileDisplayName())
    }

    private fun buildCurrentStateFromUi(): LivyPluginSettings.PluginState {
        commitSelectedProfileUi()
        return workingState.snapshot().apply {
            syncLegacyFieldsFromActiveProfile()
        }
    }

    private fun clearLocalHistoryData() {
        commitGlobalUi()
        workingState.clearLocalHistoryAndDrafts()
        Messages.showInfoMessage(
            "Saved local snippet history and drafts will be cleared when you apply settings.",
            "Livy"
        )
    }

    private fun validateProfiles(state: LivyPluginSettings.PluginState) {
        if (state.profiles.isEmpty()) {
            throw ConfigurationException("At least one Livy connection profile is required.")
        }
        state.profiles.forEach { profile ->
            if (profile.displayName.isBlank()) {
                throw ConfigurationException("Profile display name cannot be empty.")
            }
            if (profile.livyServerUrl.isBlank()) {
                throw ConfigurationException("Livy Server URL cannot be empty for profile \"${profile.displayName}\".")
            }
        }
    }

    private fun editableSettingsFingerprint(state: LivyPluginSettings.PluginState): String = buildString {
        appendLine("active=${state.activeProfileId}")
        appendLine("default=${state.defaultProfileId}")
        appendLine("localHistoryEnabled=${state.localHistoryEnabled}")
        appendLine("maxHistoryItems=${state.maxHistoryItems}")
        state.profiles.forEach { profile ->
            appendLine("profile=${profile.id}")
            appendLine(profile.displayName)
            appendLine(profile.livyServerUrl)
            appendLine(profile.maxSessions)
            appendLine(profile.sessionManagementStrategy)
            appendLine(profile.kind)
            appendLine(profile.proxyUser)
            appendLine(profile.jars)
            appendLine(profile.pyFiles)
            appendLine(profile.files)
            appendLine(profile.driverMemory)
            appendLine(profile.driverCores)
            appendLine(profile.executorMemory)
            appendLine(profile.executorCores)
            appendLine(profile.numExecutors)
            appendLine(profile.archives)
            appendLine(profile.queue)
            appendLine(profile.name)
            appendLine(profile.conf)
            appendLine(profile.heartbeatTimeoutInSecond)
            appendLine(profile.ttl)
            appendLine(profile.killOldestIfFull)
        }
    }

    private fun spinnerValue(spinner: JSpinner): Int = (spinner.value as Number).toInt()

}
