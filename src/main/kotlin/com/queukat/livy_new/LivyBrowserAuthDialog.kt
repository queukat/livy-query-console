package com.queukat.livy_new

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

class LivyBrowserAuthDialog(
    project: Project?,
    private val profile: LivyPluginSettings.ConnectionProfileState
) : DialogWrapper(project, true) {

    private val targetUrl = LivyManagedSessions.normalizeServerUrl(profile.livyServerUrl)
    private val supported = JBCefApp.isSupported()
    private val currentPageLabel = JLabel(
        if (supported) {
            "Current page: $targetUrl"
        } else {
            "Embedded browser is not available in this IDE runtime."
        }
    )
    private var browser: JBCefBrowser? = if (supported) JBCefBrowser(targetUrl) else null
    private var capturedCookies: List<com.intellij.ui.jcef.JBCefCookie> = emptyList()

    init {
        title = "Authenticate ${profile.displayName.ifBlank { "Livy Profile" }}"
        setOKButtonText("Use Current Session")
        setResizable(true)
        installLoadHandler()
        init()
    }

    fun capturedCookies(): List<com.intellij.ui.jcef.JBCefCookie> = capturedCookies

    override fun createCenterPanel(): JComponent {
        val browserComponent = browser?.component

        return panel {
            row {
                label("Finish the browser login flow for ${profile.displayName.ifBlank { targetUrl }}.")
            }
            row {
                label("When the target page is reachable, click \"Use Current Session\" and the plugin will reuse the saved cookies.")
            }
            row {
                cell(currentPageLabel).align(Align.FILL)
            }
            if (browserComponent != null) {
                row {
                    cell(browserComponent).align(Align.FILL)
                }.resizableRow()
            } else {
                row {
                    label("JCEF is not supported here, so browser-based login cannot be opened inside the IDE.")
                }
            }
        }
    }

    override fun createActions(): Array<Action> = arrayOf(
        object : DialogWrapperAction("Reset Browser Cookies") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                val manager = browser?.getJBCefCookieManager() ?: return
                manager.deleteCookies(targetUrl, true)
                browser?.loadURL(targetUrl)
                currentPageLabel.text = "Current page: $targetUrl"
            }
        },
        okAction,
        cancelAction
    )

    override fun doOKAction() {
        if (!supported) {
            Messages.showErrorDialog(contentPanel, "Embedded browser authentication is not supported in this IDE runtime.", "Livy Authentication")
            return
        }
        val cookies = browser
            ?.getJBCefCookieManager()
            ?.getCookies()
            ?.filter { cookieMatchesTarget(it) }
            .orEmpty()
        if (cookies.isEmpty()) {
            Messages.showErrorDialog(
                contentPanel,
                "No browser cookies were captured for $targetUrl yet. Finish the login flow first.",
                "Livy Authentication"
            )
            return
        }
        capturedCookies = cookies
        super.doOKAction()
    }

    override fun dispose() {
        browser?.dispose()
        browser = null
        super.dispose()
    }

    private fun installLoadHandler() {
        val currentBrowser = browser ?: return
        currentBrowser.getJBCefClient().addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(
                    browser: org.cef.browser.CefBrowser,
                    frame: org.cef.browser.CefFrame,
                    httpStatusCode: Int
                ) {
                    SwingUtilities.invokeLater {
                        currentPageLabel.text = "Current page: ${browser.url} (HTTP $httpStatusCode)"
                    }
                }

                override fun onLoadError(
                    browser: org.cef.browser.CefBrowser,
                    frame: org.cef.browser.CefFrame,
                    errorCode: org.cef.handler.CefLoadHandler.ErrorCode,
                    errorText: String,
                    failedUrl: String
                ) {
                    SwingUtilities.invokeLater {
                        currentPageLabel.text = "Failed to load $failedUrl: $errorText"
                    }
                }
            },
            currentBrowser.getCefBrowser()
                )
    }

    private fun cookieMatchesTarget(cookie: com.intellij.ui.jcef.JBCefCookie): Boolean {
        val httpUrl = targetUrl.toHttpUrlOrNull() ?: return true
        val okHttpCookie = cookie.toStoredCookieOrNull()?.toOkHttpCookieOrNull() ?: return false
        return okHttpCookie.matches(httpUrl)
    }
}
