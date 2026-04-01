package com.queukat.livy_new

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SessionManagerTest {

    @Test
    fun reuses_only_matching_managed_session_for_bound_server() {
        val executionSettings = baseExecutionSettings("http://livy-a")
        val registryState = LivyPluginSettings.PluginState()
        val fingerprint = LivySessionSpecFactory.fromSettings(executionSettings, "http://livy-a").fingerprint
        LivyManagedSessions.remember(registryState, sessionId = 11, serverUrl = "http://livy-a", fingerprint = fingerprint, createdAtMs = 1)
        LivyManagedSessions.remember(registryState, sessionId = 12, serverUrl = "http://livy-b", fingerprint = fingerprint, createdAtMs = 2)

        val client = FakeLivySessionClient(
            baseUrl = "http://livy-a",
            activeSessions = listOf(
                Session(id = 11, state = "idle"),
                Session(id = 99, state = "idle")
            )
        )

        val session = SessionManager(client, executionSettings, registryState).getSession()

        assertEquals(11, session.id)
        assertTrue(client.createdConfigs.isEmpty())
    }

    @Test
    fun creates_new_session_when_only_other_server_has_matching_entry() {
        val executionSettings = baseExecutionSettings("http://livy-a")
        val registryState = LivyPluginSettings.PluginState()
        val fingerprint = LivySessionSpecFactory.fromSettings(executionSettings, "http://livy-a").fingerprint
        LivyManagedSessions.remember(registryState, sessionId = 12, serverUrl = "http://livy-b", fingerprint = fingerprint, createdAtMs = 1)

        val client = FakeLivySessionClient(
            baseUrl = "http://livy-a",
            activeSessions = listOf(Session(id = 12, state = "idle")),
            createdSessions = ArrayDeque(listOf(Session(id = 20, state = "idle")))
        )

        val session = SessionManager(client, executionSettings, registryState).getSession()

        assertEquals(20, session.id)
        assertEquals(1, client.createdConfigs.size)
        assertEquals(setOf(20), LivyManagedSessions.managedSessionIdsForServer(registryState, "http://livy-a"))
        assertEquals(setOf(12), LivyManagedSessions.managedSessionIdsForServer(registryState, "http://livy-b"))
    }

    @Test
    fun kills_oldest_idle_managed_session_only_for_bound_server_when_limit_is_reached() {
        val executionSettings = baseExecutionSettings(
            serverUrl = "http://livy-a",
            sessionManagementStrategy = "always_create",
            maxSessions = 1,
            killOldestIfFull = true
        )
        val registryState = LivyPluginSettings.PluginState()
        LivyManagedSessions.remember(registryState, sessionId = 1, serverUrl = "http://livy-a", fingerprint = "fp-a", createdAtMs = 1)
        LivyManagedSessions.remember(registryState, sessionId = 2, serverUrl = "http://livy-b", fingerprint = "fp-b", createdAtMs = 2)

        val client = FakeLivySessionClient(
            baseUrl = "http://livy-a",
            activeSessions = listOf(Session(id = 1, state = "idle")),
            createdSessions = ArrayDeque(listOf(Session(id = 3, state = "idle")))
        )

        val session = SessionManager(client, executionSettings, registryState).getSession()

        assertEquals(3, session.id)
        assertEquals(listOf(1), client.deletedSessionIds)
        assertEquals(setOf(3), LivyManagedSessions.managedSessionIdsForServer(registryState, "http://livy-a"))
        assertEquals(setOf(2), LivyManagedSessions.managedSessionIdsForServer(registryState, "http://livy-b"))
    }

    @Test
    fun forgets_new_session_if_creation_reaches_terminal_state() {
        val executionSettings = baseExecutionSettings(
            serverUrl = "http://livy-a",
            sessionManagementStrategy = "always_create"
        )
        val registryState = LivyPluginSettings.PluginState()
        val client = FakeLivySessionClient(
            baseUrl = "http://livy-a",
            createdSessions = ArrayDeque(listOf(Session(id = 4, state = "starting"))),
            sessionPolls = mutableMapOf(
                4 to ArrayDeque(listOf(Session(id = 4, state = "error")))
            )
        )

        val error = assertFailsWith<RuntimeException> {
            SessionManager(client, executionSettings, registryState).getSession()
        }

        assertTrue(error.message?.contains("final state is error") == true)
        assertEquals(emptySet(), LivyManagedSessions.managedSessionIdsForServer(registryState, "http://livy-a"))
    }

    private fun baseExecutionSettings(
        serverUrl: String,
        sessionManagementStrategy: String = "reuse",
        maxSessions: Int = 2,
        killOldestIfFull: Boolean = false
    ): LivyPluginSettings.ConnectionProfileState =
        LivyPluginSettings.ConnectionProfileState().apply {
            livyServerUrl = serverUrl
            displayName = "Test Profile"
            kind = "sql"
            this.sessionManagementStrategy = sessionManagementStrategy
            this.maxSessions = maxSessions
            this.killOldestIfFull = killOldestIfFull
        }

    private class FakeLivySessionClient(
        private val baseUrl: String,
        activeSessions: List<Session> = emptyList(),
        private val createdSessions: ArrayDeque<Session> = ArrayDeque(),
        private val sessionPolls: MutableMap<Int, ArrayDeque<Session>> = mutableMapOf()
    ) : LivySessionClient {

        val createdConfigs = mutableListOf<SessionConfig>()
        val deletedSessionIds = mutableListOf<Int>()

        private val sessionsById = activeSessions
            .mapNotNull { session -> session.id?.let { it to session } }
            .toMap()
            .toMutableMap()

        override fun createSession(sessionConfig: SessionConfig): Session {
            createdConfigs += sessionConfig
            val session = if (createdSessions.isEmpty()) {
                error("No queued created session for test.")
            } else {
                createdSessions.removeFirst()
            }
            session.id?.let { sessionsById[it] = session }
            return session
        }

        override fun getSession(sessionId: Int): Session {
            val queue = sessionPolls[sessionId]
            val session = if (queue != null && queue.isNotEmpty()) {
                queue.removeFirst()
            } else {
                sessionsById[sessionId] ?: error("No session $sessionId registered for test.")
            }
            session.id?.let { sessionsById[it] = session }
            return session
        }

        override fun deleteSession(sessionId: Int) {
            deletedSessionIds += sessionId
            sessionsById.remove(sessionId)
        }

        override fun getAllSessions(): List<Session> = sessionsById.values.sortedBy { it.id }

        override fun getBaseUrl(): String = baseUrl
    }
}
