package com.queukat.livy_new

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.ListSelectionModel

class ChooseSessionDialog(
    private val client: LivyClient,
    sessions: List<Session>
) : DialogWrapper(true) {

    private var selectedSession: Session? = null
    private var alwaysCreateNewSession: Boolean = false
    private var sessionLimit: Int = 2
    private var runOnce: Boolean = false

    private val listModel = DefaultListModel<Session>().apply {
        sessions.forEach { addElement(it) }
    }

    private val sessionList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        isEnabled = true
        preferredSize = Dimension(250, 120)
    }

    init {
        title = "Choose Session"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label("Choose existing session (if any):")
            }
            row {
                cell(sessionList)
                    .resizableColumn()
                    .align(Align.FILL)
            }

            separator()

            row {
                checkBox("Always create new session")
                    .bindSelected(
                        getter = { alwaysCreateNewSession },
                        setter = { alwaysCreateNewSession = it }
                    )
                    .comment("If checked, a new session will always be created instead of reusing one.")
            }

            row("Session limit:") {
                spinner(1..10, 1)
                    .bindIntValue(
                        getter = { sessionLimit },
                        setter = { sessionLimit = it }
                    )
                    .comment("UI-only limit for this dialog (does not override global plugin setting).")
            }

            row {
                checkBox("Run once (create session, execute code, kill session)")
                    .bindSelected(
                        getter = { runOnce },
                        setter = {
                            runOnce = it
                            sessionList.isEnabled = !it
                        }
                    )
                    .comment("If checked, your code will run in a temporary session that will be closed automatically.")
            }
        }
    }

    override fun doOKAction() {
        selectedSession = if (runOnce) {
            null
        } else {
            val chosen = sessionList.selectedValue
            if (alwaysCreateNewSession || chosen == null) {
                createNewSession()
            } else {
                chosen
            }
        }
        super.doOKAction()
    }

    private fun createNewSession(): Session? {
        val settings = LivyPluginSettings.getInstance().pluginState

        val sessionConfig = SessionConfig(
            kind = settings.kind.ifBlank { "spark" },
            proxyUser = settings.proxyUser.takeIf { it.isNotBlank() },
            jars = settings.jars.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            pyFiles = settings.pyFiles.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            files = settings.files.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            driverMemory = settings.driverMemory.takeIf { it.isNotBlank() },
            driverCores = settings.driverCores,
            executorMemory = settings.executorMemory.takeIf { it.isNotBlank() },
            executorCores = settings.executorCores,
            numExecutors = settings.numExecutors,
            archives = settings.archives.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            queue = settings.queue.takeIf { it.isNotBlank() },
            name = settings.name.takeIf { it.isNotBlank() },
            conf = parseConf(settings.conf),
            heartbeatTimeoutInSecond = settings.heartbeatTimeoutInSecond,
            ttl = settings.ttl.takeIf { it.isNotBlank() }
        )

        return try {
            client.createSession(sessionConfig)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                contentPanel,
                "Failed to create new session: ${e.message}",
                "Livy Error"
            )
            null
        }
    }

    private fun parseConf(confString: String): Map<String, String>? {
        if (confString.isBlank()) return null

        val pairs = confString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { kv ->
                val parts = kv.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }

        return pairs.takeIf { it.isNotEmpty() }?.toMap()
    }

    fun getSelectedSession(): Session? = selectedSession
    fun getAlwaysCreateNewSession(): Boolean = alwaysCreateNewSession
    fun getSessionLimit(): Int = sessionLimit
    fun getRunOnce(): Boolean = runOnce
}
