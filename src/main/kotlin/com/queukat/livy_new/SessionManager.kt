package com.queukat.livy_new

import java.util.UUID

/**
 * Manages Livy sessions: can reuse or always create a new session,
 * respecting a maximum session limit. Also waits for new sessions to reach "idle".
 */
class SessionManager(private val client: LivyClient, private val maxSessions: Int) {

    private val activeSessions = mutableListOf<Session>()

    init {
        refreshSessions()
    }

    fun refreshSessions() {
        activeSessions.clear()
        activeSessions.addAll(client.getSessions())
    }

    fun getSession(): Session {
        refreshSessions()
        val settings = LivyPluginSettings.getInstance().pluginState
        val strategy = settings.sessionManagementStrategy

        return when (strategy) {
            "always_create" -> createNewSessionOrThrow()
            "reuse" -> {
                val availableSession = activeSessions.find { it.state == "idle" }
                availableSession ?: createNewSessionOrThrow()
            }
            else -> createNewSessionOrThrow()
        }
    }

    private fun createNewSessionOrThrow(): Session {
        refreshSessions()
        if (activeSessions.size >= maxSessions) {
            val settings = LivyPluginSettings.getInstance().pluginState
            if (settings.killOldestIfFull) {
                killOldestIdleSessionOrThrow()
            } else {
                throw RuntimeException("No available sessions. All are busy or max limit ($maxSessions) reached.")
            }
        }
        return createNewSession()
    }

    fun createNewSession(): Session {
        refreshSessions()

        val settings = LivyPluginSettings.getInstance().pluginState

        val sessionConfig = SessionConfig(
            kind = settings.kind.ifBlank { "spark" },
            proxyUser = settings.proxyUser.takeIf { it.isNotBlank() },
            jars = settings.jars.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            pyFiles = settings.pyFiles.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            files = settings.files.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            driverMemory = settings.driverMemory.ifBlank { "1g" },
            driverCores = settings.driverCores.takeIf { it > 0 } ?: 1,
            executorMemory = settings.executorMemory.ifBlank { "1g" },
            executorCores = settings.executorCores.takeIf { it > 0 } ?: 1,
            numExecutors = settings.numExecutors.takeIf { it > 0 } ?: 2,
            archives = settings.archives.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() },
            queue = settings.queue.takeIf { it.isNotBlank() },
            name = settings.name.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            conf = parseConf(settings.conf),
            heartbeatTimeoutInSecond = settings.heartbeatTimeoutInSecond.takeIf { it > 0 } ?: 60,
            ttl = settings.ttl.takeIf { it.isNotBlank() }
        )

        val session = client.createSession(sessionConfig)
        activeSessions.add(session)
        waitForSessionIdle(session)
        return session
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

    private fun killOldestIdleSessionOrThrow() {
        refreshSessions()
        val idleSessions = activeSessions.filter { it.state == "idle" && it.id != null }
        val oldestIdle = idleSessions.minByOrNull { it.id ?: Int.MAX_VALUE }

        if (oldestIdle?.id != null) {
            client.deleteSession(oldestIdle.id)
            activeSessions.removeIf { it.id == oldestIdle.id }
        } else {
            throw RuntimeException("All existing sessions are busy. No idle session to kill, but max limit is reached.")
        }
    }

    private fun waitForSessionIdle(session: Session) {
        val id = session.id ?: throw RuntimeException("Session id is null (unexpected response).")
        val deadlineMs = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes

        var current = session
        while (System.currentTimeMillis() < deadlineMs) {
            val state = current.state
            if (state == "idle") return
            if (state in listOf("error", "dead", "killed")) break

            Thread.sleep(1000)
            current = client.getSession(id)
        }

        throw RuntimeException("Failed to create a session: final state is ${current.state}")
    }
}
