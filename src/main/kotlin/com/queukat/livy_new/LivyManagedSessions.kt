package com.queukat.livy_new

object LivyManagedSessions {

    fun normalizeServerUrl(serverUrl: String): String = serverUrl.trim().trimEnd('/')

    fun remember(
        state: LivyPluginSettings.PluginState,
        sessionId: Int,
        serverUrl: String,
        fingerprint: String,
        createdAtMs: Long = System.currentTimeMillis()
    ) {
        val normalizedServerUrl = normalizeServerUrl(serverUrl)
        state.managedSessions.removeAll { it.sessionId == sessionId && it.serverUrl == normalizedServerUrl }
        state.managedSessions.add(
            LivyPluginSettings.ManagedSessionState().apply {
                this.sessionId = sessionId
                this.serverUrl = normalizedServerUrl
                this.fingerprint = fingerprint
                this.createdAtMs = createdAtMs
            }
        )
    }

    fun forget(
        state: LivyPluginSettings.PluginState,
        sessionId: Int,
        serverUrl: String? = null
    ) {
        val normalizedServerUrl = serverUrl?.let(::normalizeServerUrl)
        state.managedSessions.removeAll { entry ->
            entry.sessionId == sessionId &&
                (normalizedServerUrl == null || entry.serverUrl == normalizedServerUrl)
        }
    }

    fun pruneMissingForServer(
        state: LivyPluginSettings.PluginState,
        serverUrl: String,
        activeSessionIds: Set<Int>
    ) {
        val normalizedServerUrl = normalizeServerUrl(serverUrl)
        state.managedSessions.removeAll { entry ->
            entry.serverUrl == normalizedServerUrl && entry.sessionId !in activeSessionIds
        }
    }

    fun managedEntriesForServer(
        state: LivyPluginSettings.PluginState,
        serverUrl: String
    ): List<LivyPluginSettings.ManagedSessionState> {
        val normalizedServerUrl = normalizeServerUrl(serverUrl)
        return state.managedSessions
            .filter { it.sessionId >= 0 && it.serverUrl == normalizedServerUrl }
            .sortedWith(compareBy<LivyPluginSettings.ManagedSessionState>({ it.createdAtMs }, { it.sessionId }))
    }

    fun managedSessionIdsForServer(
        state: LivyPluginSettings.PluginState,
        serverUrl: String
    ): Set<Int> = managedEntriesForServer(state, serverUrl).map { it.sessionId }.toSet()

    fun isManagedSession(
        state: LivyPluginSettings.PluginState,
        serverUrl: String,
        sessionId: Int
    ): Boolean = sessionId in managedSessionIdsForServer(state, serverUrl)

    fun matchingSessionIds(
        state: LivyPluginSettings.PluginState,
        serverUrl: String,
        fingerprint: String
    ): Set<Int> = managedEntriesForServer(state, serverUrl)
        .filter { it.fingerprint == fingerprint }
        .map { it.sessionId }
        .toSet()
}
