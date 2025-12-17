package com.queukat.livy_new

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(name = "LivyPluginSettings", storages = [Storage("LivyPluginSettings.xml")])
class LivyPluginSettings : PersistentStateComponent<LivyPluginSettings.PluginState> {

    var pluginState: PluginState = PluginState()

    override fun getState(): PluginState = pluginState

    override fun loadState(state: PluginState) {
        XmlSerializerUtil.copyBean(state, pluginState)
        // на всякий: если у старых конфигов нет поля — выставим дефолт
        if (pluginState.sessionTableColumns.isEmpty()) {
            pluginState.sessionTableColumns = mutableListOf(
                "id", "appId", "owner", "kind", "state", "log"
            )
        }
    }

    class PluginState {
        var livyServerUrl: String = "http://localhost:8998"

        var maxSessions: Int = 4
        var sessionManagementStrategy: String = "reuse"

        var kind: String = ""
        var proxyUser: String = ""
        var jars: String = ""
        var pyFiles: String = ""
        var files: String = ""
        var driverMemory: String = "1g"
        var driverCores: Int = 1
        var executorMemory: String = "1g"
        var executorCores: Int = 1
        var numExecutors: Int = 2
        var archives: String = ""
        var queue: String = ""
        var name: String = ""
        var conf: String = ""
        var heartbeatTimeoutInSecond: Int = 60
        var ttl: String = ""
        var killOldestIfFull: Boolean = false

        /**
         * Columns shown in Sessions tool window table (by column id).
         * User can change via "Columns…" button.
         */
        var sessionTableColumns: MutableList<String> = mutableListOf(
            "id", "appId", "owner", "kind", "state", "log"
        )
    }

    companion object {
        fun getInstance(): LivyPluginSettings {
            return ApplicationManager.getApplication().getService(LivyPluginSettings::class.java)
        }
    }
}
