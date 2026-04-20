package com.queukat.livy_new

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.jcef.JBCefApp
import java.awt.Component

fun authenticateProfileInBrowser(
    profile: LivyPluginSettings.ConnectionProfileState,
    project: Project? = null,
    parentComponent: Component? = null,
    showSuccessMessage: Boolean = true
): LivyAuthStatus? {
    val targetUrl = LivyManagedSessions.normalizeServerUrl(profile.livyServerUrl)
    if (!JBCefApp.isSupported()) {
        if (parentComponent != null) {
            Messages.showErrorDialog(
                parentComponent,
                "This IDE runtime does not support the embedded browser needed for Livy sign-in.",
                "Livy Authentication"
            )
        } else {
            Messages.showErrorDialog(
                project,
                "This IDE runtime does not support the embedded browser needed for Livy sign-in.",
                "Livy Authentication"
            )
        }
        return null
    }

    val dialog = LivyBrowserAuthDialog(project, profile)
    if (!dialog.showAndGet()) return null

    val status = LivyAuthSessionStore.getInstance().saveBrowserSession(
        profileId = profile.id,
        baseUrl = targetUrl,
        cookies = dialog.capturedCookies()
    )
    if (showSuccessMessage) {
        val message = "Browser session saved for ${profile.displayName.ifBlank { targetUrl }}.\n${status.toDisplayText(targetUrl)}"
        if (parentComponent != null) {
            Messages.showInfoMessage(parentComponent, message, "Livy Authentication")
        } else {
            Messages.showInfoMessage(project, message, "Livy Authentication")
        }
    }
    return status
}

fun maybePromptForBrowserAuthentication(
    failure: Throwable,
    profile: LivyPluginSettings.ConnectionProfileState,
    project: Project? = null,
    parentComponent: Component? = null,
    onAuthenticated: (() -> Unit)? = null
): Boolean {
    if (!failureLooksLikeAuthenticationRequired(failure)) return false

    val targetUrl = LivyManagedSessions.normalizeServerUrl(profile.livyServerUrl)
    val message = buildString {
        appendLine("This Livy server appears to require browser authentication before API calls can work.")
        appendLine(targetUrl)
        appendLine()
        append("Open the login window now?")
    }

    val answer = if (parentComponent != null) {
        Messages.showYesNoDialog(parentComponent, message, "Livy Authentication Required", null)
    } else {
        Messages.showYesNoDialog(project, message, "Livy Authentication Required", null)
    }
    if (answer != Messages.YES) return true

    val status = authenticateProfileInBrowser(
        profile = profile,
        project = project,
        parentComponent = parentComponent,
        showSuccessMessage = false
    ) ?: return true

    val successMessage = "Browser session saved.\n${status.toDisplayText(targetUrl)}"
    if (parentComponent != null) {
        Messages.showInfoMessage(parentComponent, successMessage, "Livy Authentication")
    } else {
        Messages.showInfoMessage(project, successMessage, "Livy Authentication")
    }
    onAuthenticated?.invoke()
    return true
}
