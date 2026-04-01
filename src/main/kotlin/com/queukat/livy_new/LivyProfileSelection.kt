package com.queukat.livy_new

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

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

    val labels = profiles
        .map { profile -> "${settings.profileLabel(profile)} - ${profile.livyServerUrl}" }
        .toTypedArray()
    val defaultIndex = profiles.indexOfFirst { it.id == settings.activeProfileId }.takeIf { it >= 0 } ?: 0
    val choice = Messages.showChooseDialog(
        project,
        dialogMessage,
        dialogTitle,
        null,
        labels,
        labels[defaultIndex]
    )
    return profiles.getOrNull(choice)
}
