package com.queukat.livy_new

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.util.UUID

/**
 * Manages Livy sessions: can reuse or always create a new session,
 * respecting a maximum session limit. Also waits for new sessions to reach "idle".
 */
class SessionManager(private val client: LivyClient, private val maxSessions: Int) {

    private val activeSessions = mutableListOf<Session>()
    private val settingsState: LivyPluginSettings.PluginState
        get() = LivyPluginSettings.getInstance().pluginState
    private val serverUrl: String
        get() = client.getBaseUrl()

    init {
        refreshSessions()
    }

    fun refreshSessions() {
        activeSessions.clear()
        activeSessions.addAll(client.getAllSessions())
        syncManagedSessions()
    }

    fun getSession(indicator: ProgressIndicator? = null): Session {
        refreshSessions()
        val settings = settingsState
        val strategy = settings.sessionManagementStrategy
        val desiredSpec = LivySessionSpecFactory.fromSettings(settings, serverUrl)

        return when (strategy) {
            "always_create" -> createNewSessionOrThrow(desiredSpec, indicator)
            "reuse" -> {
                val matchingIds = LivyManagedSessions.matchingSessionIds(settings, serverUrl, desiredSpec.fingerprint)
                val availableSession = activeSessions.find { it.id != null && it.id in matchingIds && it.state == "idle" }
                availableSession ?: createNewSessionOrThrow(desiredSpec, indicator)
            }
            else -> createNewSessionOrThrow(desiredSpec, indicator)
        }
    }

    private fun createNewSessionOrThrow(
        spec: LivySessionSpec,
        indicator: ProgressIndicator? = null
    ): Session {
        refreshSessions()
        val settings = settingsState
        val managedSessionIds = LivyManagedSessions.managedSessionIdsForServer(settings, serverUrl)
        val managedActiveSessions = activeSessions.filter { session ->
            val sessionId = session.id
            sessionId != null && sessionId in managedSessionIds && session.state !in TERMINAL_STATES
        }

        if (managedActiveSessions.size >= maxSessions) {
            if (settings.killOldestIfFull) {
                killOldestIdleSessionOrThrow()
            } else {
                throw RuntimeException("No available managed sessions. Max managed session limit ($maxSessions) reached.")
            }
        }
        return createNewSession(spec, indicator)
    }

    fun createNewSession(indicator: ProgressIndicator? = null): Session {
        val spec = LivySessionSpecFactory.fromSettings(settingsState, serverUrl, generatedName = "Livy Query Console ${UUID.randomUUID()}")
        return createNewSession(spec, indicator)
    }

    private fun createNewSession(spec: LivySessionSpec, indicator: ProgressIndicator? = null): Session {
        refreshSessions()

        checkCanceled(indicator)

        val session = client.createSession(spec.config)
        val sessionId = session.id ?: throw RuntimeException("Livy returned a session without id.")
        LivyManagedSessions.remember(settingsState, sessionId, serverUrl, spec.fingerprint)
        activeSessions.add(session)
        return waitForSessionIdle(session, indicator)
    }

    private fun killOldestIdleSessionOrThrow() {
        refreshSessions()
        val managedEntriesById = LivyManagedSessions
            .managedEntriesForServer(settingsState, serverUrl)
            .associateBy { it.sessionId }
        val idleSessions = activeSessions.filter { it.state == "idle" && it.id != null && it.id in managedEntriesById.keys }
        val oldestIdle = idleSessions.minWithOrNull(
            compareBy<Session>(
                { managedEntriesById[it.id]?.createdAtMs ?: Long.MAX_VALUE },
                { it.id ?: Int.MAX_VALUE }
            )
        )

        if (oldestIdle?.id != null) {
            client.deleteSession(oldestIdle.id)
            LivyManagedSessions.forget(settingsState, oldestIdle.id, serverUrl)
            activeSessions.removeIf { it.id == oldestIdle.id }
        } else {
            throw RuntimeException("All managed sessions are busy. No idle managed session to kill, but max limit is reached.")
        }
    }

    private fun waitForSessionIdle(session: Session, indicator: ProgressIndicator? = null): Session {
        val id = session.id ?: throw RuntimeException("Session id is null (unexpected response).")
        val deadlineMs = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes

        var current = session
        while (System.currentTimeMillis() < deadlineMs) {
            checkCanceled(indicator)
            val state = current.state
            if (state == "idle") return current
            if (state in TERMINAL_STATES) {
                LivyManagedSessions.forget(settingsState, id, serverUrl)
                break
            }

            Thread.sleep(1000)
            current = client.getSession(id)
        }

        throw RuntimeException("Failed to create a session: final state is ${current.state}")
    }

    private fun syncManagedSessions() {
        val activeIds = activeSessions.mapNotNull { it.id }.toSet()
        LivyManagedSessions.pruneMissingForServer(settingsState, serverUrl, activeIds)
        activeSessions
            .filter { it.id != null && it.state in TERMINAL_STATES }
            .mapNotNull { it.id }
            .forEach { LivyManagedSessions.forget(settingsState, it, serverUrl) }
    }

    private fun checkCanceled(indicator: ProgressIndicator?) {
        if (indicator?.isCanceled == true) {
            throw ProcessCanceledException()
        }
    }

    companion object {
        private val TERMINAL_STATES = setOf("error", "dead", "killed")
    }
}
