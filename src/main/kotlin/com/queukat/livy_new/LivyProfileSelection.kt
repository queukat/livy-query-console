package com.queukat.livy_new

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JPanel

internal data class LivyProfileSelectionOptions(
    val labels: List<String>,
    val defaultIndex: Int
)

fun chooseLivyProfile(
    settings: LivyPluginSettings.PluginState,
    project: Project,
    dialogTitle: String,
    dialogMessage: String
): LivyPluginSettings.ConnectionProfileState? {
    val profiles = settings.profileChoices()
    if (profiles.isEmpty()) {
        Messages.showErrorDialog(project, "No Livy connection profiles are configured.", "Livy")
        return null
    }
    if (profiles.size == 1) {
        return profiles.first()
    }

    val options = buildLivyProfileSelectionOptions(settings, profiles)
    val dialog = LivyProfileSelectionDialog(project, dialogTitle, dialogMessage, options.labels, options.defaultIndex)
    return if (dialog.showAndGet()) profiles.getOrNull(dialog.selectedIndex()) else null
}

internal fun buildLivyProfileSelectionOptions(
    settings: LivyPluginSettings.PluginState,
    profiles: List<LivyPluginSettings.ConnectionProfileState> = settings.profileChoices()
): LivyProfileSelectionOptions {
    val labels = profiles.map { profile -> "${settings.profileLabel(profile)} - ${profile.livyServerUrl}" }
    val defaultIndex = profiles.indexOfFirst { it.id == settings.activeProfileId }.takeIf { it >= 0 } ?: 0
    return LivyProfileSelectionOptions(labels = labels, defaultIndex = defaultIndex)
}

private class LivyProfileSelectionDialog(
    project: Project,
    dialogTitle: String,
    private val dialogMessage: String,
    labels: List<String>,
    defaultIndex: Int
) : DialogWrapper(project) {

    private val profileComboBox = JComboBox(labels.toTypedArray()).apply {
        selectedIndex = defaultIndex
    }

    init {
        title = dialogTitle
        setOKButtonText("Use Profile")
        init()
    }

    fun selectedIndex(): Int = profileComboBox.selectedIndex

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout(0, 8)).apply {
            add(JBLabel(dialogMessage), BorderLayout.NORTH)
            add(profileComboBox, BorderLayout.CENTER)
        }
    }
}
