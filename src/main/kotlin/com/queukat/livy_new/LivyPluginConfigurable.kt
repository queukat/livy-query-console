package com.queukat.livy_new

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class LivyPluginConfigurable : Configurable {

    private var mainPanel: DialogPanel? = null
    private val propertyGraph = PropertyGraph()

    private val livyServerUrlProp: ObservableMutableProperty<String> = propertyGraph.property("")

    private val kindProp = propertyGraph.property("")
    private val proxyUserProp = propertyGraph.property("")
    private val jarsProp = propertyGraph.property("")
    private val pyFilesProp = propertyGraph.property("")
    private val filesProp = propertyGraph.property("")
    private val driverMemoryProp = propertyGraph.property("")
    private val driverCoresProp = propertyGraph.property(1)
    private val executorMemoryProp = propertyGraph.property("")
    private val executorCoresProp = propertyGraph.property(1)
    private val numExecutorsProp = propertyGraph.property(2)
    private val archivesProp = propertyGraph.property("")
    private val queueProp = propertyGraph.property("")
    private val nameProp = propertyGraph.property("")
    private val confProp = propertyGraph.property("")
    private val heartbeatTimeoutProp = propertyGraph.property(60)
    private val ttlProp = propertyGraph.property("")
    private val maxSessionsProp = propertyGraph.property(4)

    private val sessionManagementProp: ObservableMutableProperty<String> = propertyGraph.property("reuse")
    private val killOldestProp = propertyGraph.property(false)

    override fun createComponent(): JComponent {
        val settings = LivyPluginSettings.getInstance().pluginState

        livyServerUrlProp.set(settings.livyServerUrl)
        kindProp.set(settings.kind)
        proxyUserProp.set(settings.proxyUser)
        jarsProp.set(settings.jars)
        pyFilesProp.set(settings.pyFiles)
        filesProp.set(settings.files)
        driverMemoryProp.set(settings.driverMemory)
        driverCoresProp.set(settings.driverCores)
        executorMemoryProp.set(settings.executorMemory)
        executorCoresProp.set(settings.executorCores)
        numExecutorsProp.set(settings.numExecutors)
        archivesProp.set(settings.archives)
        queueProp.set(settings.queue)
        nameProp.set(settings.name)
        confProp.set(settings.conf)
        heartbeatTimeoutProp.set(settings.heartbeatTimeoutInSecond)
        ttlProp.set(settings.ttl)
        maxSessionsProp.set(settings.maxSessions)
        sessionManagementProp.set(settings.sessionManagementStrategy)
        killOldestProp.set(settings.killOldestIfFull)

        mainPanel = panel {
            row { label("Livy Plugin Settings").bold() }
            separator()

            group("Livy Server") {
                row("Server URL:") {
                    textField()
                        .bindText(livyServerUrlProp)
                        .align(AlignX.FILL)
                        .comment("e.g. http://localhost:8998")
                }
                row("Session Kind:") { textField().bindText(kindProp).comment("spark, pyspark, sparkr, sql ...") }
                row("Proxy User:") { textField().bindText(proxyUserProp) }
            }

            group("Resources") {
                row("Jars:") { textField().bindText(jarsProp).comment("Comma-separated JARs") }
                row("Python Files:") { textField().bindText(pyFilesProp).comment("Comma-separated py files") }
                row("Files:") { textField().bindText(filesProp).comment("Comma-separated files") }
                row("Driver Memory:") { textField().bindText(driverMemoryProp).comment("e.g. 1g") }
                row("Driver Cores:") { intTextField(1..64).bindIntText(driverCoresProp) }
                row("Executor Memory:") { textField().bindText(executorMemoryProp).comment("e.g. 2g") }
                row("Executor Cores:") { intTextField(1..64).bindIntText(executorCoresProp) }
                row("Num Executors:") { intTextField(1..128).bindIntText(numExecutorsProp) }
                row("Archives:") { textField().bindText(archivesProp).comment("Comma-separated archives") }
                row("Queue:") { textField().bindText(queueProp) }
                row("Name:") { textField().bindText(nameProp) }
                row("Conf:") {
                    textField().bindText(confProp)
                        .comment("Comma-separated: key=value,key2=value2 (split by first '=')")
                }
            }

            group("Timeouts & Limits") {
                row("Heartbeat Timeout (sec):") { intTextField(1..6000).bindIntText(heartbeatTimeoutProp) }
                row("TTL:") { textField().bindText(ttlProp).comment("e.g. 10m") }
                row("Max Sessions:") { intTextField(1..50).bindIntText(maxSessionsProp) }
            }

            group("Session Strategy") {
                row("Strategy:") {
                    comboBox(listOf("reuse", "always_create"))
                        .bindItem(sessionManagementProp)
                        .comment("Reuse idle sessions or always create (up to limit).")
                }
                row { checkBox("Kill oldest idle if limit reached").bindSelected(killOldestProp) }
            }

            row {
                button("Test Connection") {
                    val url = livyServerUrlProp.get().trimEnd('/')
                    LivyBackground.run(
                        project = null,
                        title = "Testing Livy connection",
                        action = { _ -> LivyClientProvider.getInstance().get(url).testConnection() },
                        onSuccessUi = { ok ->
                            if (ok) Messages.showInfoMessage("Connection successful!", "Livy")
                            else Messages.showErrorDialog("Failed to connect to $url", "Livy Error")
                        }
                    )
                }

                button("Start Test Session") {
                    val url = livyServerUrlProp.get().trimEnd('/')

                    LivyBackground.run(
                        project = null,
                        title = "Creating Livy test session",
                        action = { _ ->
                            val sessionConfig = SessionConfig(
                                kind = kindProp.get().ifBlank { "spark" },
                                proxyUser = proxyUserProp.get().takeIf { it.isNotBlank() },
                                jars = jarsProp.get().takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
                                pyFiles = pyFilesProp.get().takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
                                files = filesProp.get().takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
                                driverMemory = driverMemoryProp.get().takeIf { it.isNotBlank() },
                                driverCores = driverCoresProp.get(),
                                executorMemory = executorMemoryProp.get().takeIf { it.isNotBlank() },
                                executorCores = executorCoresProp.get(),
                                numExecutors = numExecutorsProp.get(),
                                archives = archivesProp.get().takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
                                queue = queueProp.get().takeIf { it.isNotBlank() },
                                name = nameProp.get().takeIf { it.isNotBlank() },
                                conf = parseConf(confProp.get()),
                                heartbeatTimeoutInSecond = heartbeatTimeoutProp.get(),
                                ttl = ttlProp.get().takeIf { it.isNotBlank() }
                            )
                            LivyClientProvider.getInstance().get(url).createSession(sessionConfig)
                        },
                        onSuccessUi = {
                            Messages.showInfoMessage("Test session created!", "Livy")
                        },
                        onErrorUi = { ex ->
                            Messages.showErrorDialog("Failed to create session: ${ex.message}", "Livy Error")
                        }
                    )
                }
            }
        }

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = LivyPluginSettings.getInstance().pluginState
        return (
                livyServerUrlProp.get() != settings.livyServerUrl ||
                        kindProp.get() != settings.kind ||
                        proxyUserProp.get() != settings.proxyUser ||
                        jarsProp.get() != settings.jars ||
                        pyFilesProp.get() != settings.pyFiles ||
                        filesProp.get() != settings.files ||
                        driverMemoryProp.get() != settings.driverMemory ||
                        driverCoresProp.get() != settings.driverCores ||
                        executorMemoryProp.get() != settings.executorMemory ||
                        executorCoresProp.get() != settings.executorCores ||
                        numExecutorsProp.get() != settings.numExecutors ||
                        archivesProp.get() != settings.archives ||
                        queueProp.get() != settings.queue ||
                        nameProp.get() != settings.name ||
                        confProp.get() != settings.conf ||
                        heartbeatTimeoutProp.get() != settings.heartbeatTimeoutInSecond ||
                        ttlProp.get() != settings.ttl ||
                        maxSessionsProp.get() != settings.maxSessions ||
                        (sessionManagementProp.get() ?: "reuse") != settings.sessionManagementStrategy ||
                        killOldestProp.get() != settings.killOldestIfFull
                )
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        mainPanel?.apply()

        val settings = LivyPluginSettings.getInstance().pluginState

        val urlTrimmed = livyServerUrlProp.get().trimEnd('/')
        if (urlTrimmed.isBlank()) throw ConfigurationException("Livy Server URL cannot be empty.")

        settings.livyServerUrl = urlTrimmed
        settings.kind = kindProp.get()
        settings.proxyUser = proxyUserProp.get()
        settings.jars = jarsProp.get()
        settings.pyFiles = pyFilesProp.get()
        settings.files = filesProp.get()
        settings.driverMemory = driverMemoryProp.get()
        settings.driverCores = driverCoresProp.get()
        settings.executorMemory = executorMemoryProp.get()
        settings.executorCores = executorCoresProp.get()
        settings.numExecutors = numExecutorsProp.get()
        settings.archives = archivesProp.get()
        settings.queue = queueProp.get()
        settings.name = nameProp.get()
        settings.conf = confProp.get()
        settings.heartbeatTimeoutInSecond = heartbeatTimeoutProp.get()
        settings.ttl = ttlProp.get()
        settings.maxSessions = maxSessionsProp.get()
        settings.sessionManagementStrategy = sessionManagementProp.get() ?: "reuse"
        settings.killOldestIfFull = killOldestProp.get()
    }

    override fun reset() {
        val settings = LivyPluginSettings.getInstance().pluginState

        livyServerUrlProp.set(settings.livyServerUrl)
        kindProp.set(settings.kind)
        proxyUserProp.set(settings.proxyUser)
        jarsProp.set(settings.jars)
        pyFilesProp.set(settings.pyFiles)
        filesProp.set(settings.files)
        driverMemoryProp.set(settings.driverMemory)
        driverCoresProp.set(settings.driverCores)
        executorMemoryProp.set(settings.executorMemory)
        executorCoresProp.set(settings.executorCores)
        numExecutorsProp.set(settings.numExecutors)
        archivesProp.set(settings.archives)
        queueProp.set(settings.queue)
        nameProp.set(settings.name)
        confProp.set(settings.conf)
        heartbeatTimeoutProp.set(settings.heartbeatTimeoutInSecond)
        ttlProp.set(settings.ttl)
        maxSessionsProp.set(settings.maxSessions)
        sessionManagementProp.set(settings.sessionManagementStrategy)
        killOldestProp.set(settings.killOldestIfFull)

        mainPanel?.reset()
    }

    override fun disposeUIResources() {
        mainPanel = null
    }

    override fun getDisplayName(): String = "Livy"

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
}
